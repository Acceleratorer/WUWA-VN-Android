package com.acceleratorer.wuwavn

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.os.Environment
import android.os.IBinder
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import rikka.shizuku.Shizuku

class ShizukuRestoreWriter {
    fun restoreConfigFiles(
        context: Context,
        dryRun: RestoreDryRun,
        logger: DebugLogger,
    ): RestoreResult {
        requireSafeDryRun(dryRun)

        val serviceRef = AtomicReference<IWuwaRestoreService?>()
        val connected = CountDownLatch(1)
        val componentName = ComponentName(context, WuwaRestoreUserService::class.java)
        val args = Shizuku.UserServiceArgs(componentName)
            .daemon(false)
            .debuggable(false)
            .processNameSuffix("restore")
            .tag("restore")
            .version(AppConstants.VERSION_CODE)

        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, service: IBinder) {
                serviceRef.set(IWuwaRestoreService.Stub.asInterface(service))
                connected.countDown()
            }

            override fun onServiceDisconnected(name: ComponentName) {
                serviceRef.set(null)
            }
        }

        try {
            Shizuku.bindUserService(args, connection)
            if (!connected.await(15, TimeUnit.SECONDS)) {
                throw IllegalStateException("Timed out while connecting Shizuku restore service.")
            }

            val service = serviceRef.get()
                ?: throw IllegalStateException("Shizuku restore service did not connect.")

            val restoredFiles = mutableListOf<RestoreWriteInfo>()
            for (file in dryRun.files.sortedBy { it.displayName }) {
                if (file.status != RestoreFileStatus.VERIFIED) {
                    throw IllegalStateException("Restore blocked because ${file.displayName} is ${file.status.label}.")
                }

                val expectedSha256 = file.expectedSha256
                    ?: throw IllegalStateException("Restore blocked because ${file.displayName} has no expected SHA-256.")
                val backupFile = File(dryRun.sessionDirectory, file.displayName)
                val bytes = backupFile.readBytes()
                if (bytes.size > MAX_CONFIG_BYTES) {
                    throw IllegalStateException("${file.displayName} is too large for safe restore.")
                }
                val backupSha256 = Sha256Verifier.sha256(bytes)
                if (!backupSha256.equals(expectedSha256, ignoreCase = true)) {
                    throw IllegalStateException("${file.displayName} changed after dry-run verification.")
                }

                val targetPath = gameAbsolutePath(file.relativePath)
                logger.add("Restore write: writing ${file.displayName}")
                service.writeConfigFile(targetPath, bytes, expectedSha256)

                val restoredBytes = service.readFile(targetPath, MAX_CONFIG_BYTES)
                val restoredSha256 = Sha256Verifier.sha256(restoredBytes)
                if (!restoredSha256.equals(expectedSha256, ignoreCase = true)) {
                    throw IllegalStateException("${file.displayName} target verification failed after restore.")
                }

                restoredFiles.add(
                    RestoreWriteInfo(
                        displayName = file.displayName,
                        relativePath = file.relativePath,
                        sha256 = restoredSha256,
                        sizeBytes = restoredBytes.size.toLong(),
                    ),
                )
                logger.add("Restore write: verified ${file.displayName} (${restoredSha256.take(12)}...)")
            }

            return RestoreResult(restoredFiles)
        } finally {
            try {
                Shizuku.unbindUserService(args, connection, true)
            } catch (ignored: Throwable) {
            }
        }
    }

    private fun gameAbsolutePath(relativePath: String): String =
        Environment.getExternalStorageDirectory().absolutePath +
            "/Android/data/" +
            AppConstants.GLOBAL_GAME_PACKAGE +
            "/files/" +
            relativePath

    private fun requireSafeDryRun(dryRun: RestoreDryRun) {
        if (dryRun.backupType != BackupManager.READ_ONLY_CONFIG_BACKUP_TYPE) {
            throw IllegalStateException("Restore blocked because backup type is not trusted.")
        }
        if (dryRun.gamePackage != AppConstants.GLOBAL_GAME_PACKAGE) {
            throw IllegalStateException("Restore blocked because backup game package is not WUWA Global.")
        }
        if (dryRun.restoreWriteEnabled != false) {
            throw IllegalStateException("Restore blocked because restore_write_enabled=false is missing.")
        }
        if (!dryRun.allFilesVerified()) {
            throw IllegalStateException("Restore blocked because not every file is VERIFIED.")
        }
        if (!dryRun.hasOnlyVerifiedRequiredConfigFiles()) {
            throw IllegalStateException("Restore blocked because backup is not the exact required config set.")
        }
    }

    data class RestoreResult(
        val restoredFiles: List<RestoreWriteInfo>,
    )

    data class RestoreWriteInfo(
        val displayName: String,
        val relativePath: String,
        val sha256: String,
        val sizeBytes: Long,
    )

    private companion object {
        const val MAX_CONFIG_BYTES = 512 * 1024
    }
}
