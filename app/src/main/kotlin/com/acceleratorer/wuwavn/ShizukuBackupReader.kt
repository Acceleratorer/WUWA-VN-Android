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

class ShizukuBackupReader(
    private val backupManager: BackupManager,
) {
    fun backupConfigFiles(
        context: Context,
        backupDirectory: File,
        logger: DebugLogger,
    ): BackupResult {
        val serviceRef = AtomicReference<IWuwaBackupService?>()
        val connected = CountDownLatch(1)
        val componentName = ComponentName(context, WuwaBackupUserService::class.java)
        val args = Shizuku.UserServiceArgs(componentName)
            .daemon(false)
            .debuggable(false)
            .processNameSuffix("backup")
            .tag("backup")
            .version(AppConstants.VERSION_CODE)

        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, service: IBinder) {
                serviceRef.set(IWuwaBackupService.Stub.asInterface(service))
                connected.countDown()
            }

            override fun onServiceDisconnected(name: ComponentName) {
                serviceRef.set(null)
            }
        }

        try {
            Shizuku.bindUserService(args, connection)
            if (!connected.await(15, TimeUnit.SECONDS)) {
                throw IllegalStateException("Timed out while connecting Shizuku backup service.")
            }

            val service = serviceRef.get()
                ?: throw IllegalStateException("Shizuku backup service did not connect.")

            val backedUpFiles = mutableListOf<BackupFileInfo>()
            val missingFiles = mutableListOf<String>()
            for (relativePath in PatchDryRunPlanner.backupRelativePaths()) {
                val absolutePath = gameAbsolutePath(relativePath)
                val displayName = PatchDryRunPlanner.displayName(relativePath)
                if (!service.exists(absolutePath)) {
                    logger.add("Backup read: missing $displayName")
                    missingFiles.add(displayName)
                    continue
                }

                val length = service.length(absolutePath)
                if (length > MAX_CONFIG_BYTES) {
                    throw IllegalStateException("$displayName is too large for safe read-only backup.")
                }

                val bytes = service.readFile(absolutePath, MAX_CONFIG_BYTES)
                val info = backupManager.writeBackedUpFile(backupDirectory, displayName, relativePath, bytes)
                backedUpFiles.add(info)
                logger.add("Backup read: copied $displayName (${info.sizeBytes} bytes, sha256 ${info.sha256.take(12)}...)")
            }

            if (backedUpFiles.isEmpty()) {
                throw IllegalStateException("No allowlisted WUWA config files were backed up.")
            }

            return BackupResult(backedUpFiles, missingFiles)
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

    data class BackupResult(
        val backedUpFiles: List<BackupFileInfo>,
        val missingFiles: List<String>,
    )

    private companion object {
        const val MAX_CONFIG_BYTES = 512 * 1024
    }
}
