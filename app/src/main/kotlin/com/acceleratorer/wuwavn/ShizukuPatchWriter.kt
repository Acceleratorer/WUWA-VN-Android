package com.acceleratorer.wuwavn

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.os.Environment
import android.os.IBinder
import java.io.FileInputStream
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import rikka.shizuku.Shizuku

class ShizukuPatchWriter {
    fun writePatchPak(
        context: Context,
        plan: PatchWritePlan,
        logger: DebugLogger,
    ): PatchWriteResult {
        requireSafePlan(plan)

        val serviceRef = AtomicReference<IWuwaPatchService?>()
        val connected = CountDownLatch(1)
        val componentName = ComponentName(context, WuwaPatchUserService::class.java)
        val args = Shizuku.UserServiceArgs(componentName)
            .daemon(false)
            .debuggable(false)
            .processNameSuffix("patch")
            .tag("patch")
            .version(AppConstants.VERSION_CODE)

        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, service: IBinder) {
                serviceRef.set(IWuwaPatchService.Stub.asInterface(service))
                connected.countDown()
            }

            override fun onServiceDisconnected(name: ComponentName) {
                serviceRef.set(null)
            }
        }

        try {
            Shizuku.bindUserService(args, connection)
            if (!connected.await(15, TimeUnit.SECONDS)) {
                throw IllegalStateException("Timed out while connecting Shizuku patch service.")
            }

            val service = serviceRef.get()
                ?: throw IllegalStateException("Shizuku patch service did not connect.")

            val targetPath = gameAbsolutePath(plan.targetRelativePath)
            service.beginWritePatch(targetPath, plan.patchSizeBytes, plan.patchSha256)
            logger.add("Patch write: started ${formatMb(plan.patchSizeBytes)}")

            val buffer = ByteArray(CHUNK_BYTES)
            var writtenBytes = 0L
            var nextLogBytes = 10L * 1024L * 1024L
            FileInputStream(plan.patchFile).use { input ->
                while (true) {
                    val read = input.read(buffer)
                    if (read == -1) break
                    val chunk = if (read == buffer.size) buffer else buffer.copyOf(read)
                    service.writePatchChunk(targetPath, chunk, read, plan.patchSizeBytes)
                    writtenBytes += read.toLong()
                    if (writtenBytes >= nextLogBytes) {
                        logger.add("Patch write: ${formatMb(writtenBytes)} / ${formatMb(plan.patchSizeBytes)}")
                        nextLogBytes += 10L * 1024L * 1024L
                    }
                }
            }

            service.finishWritePatch(targetPath, plan.patchSizeBytes, plan.patchSha256)
            val targetSize = service.length(targetPath)
            val targetSha256 = service.sha256(targetPath)
            if (targetSize != plan.patchSizeBytes) {
                throw IllegalStateException("Patch target size mismatch after write.")
            }
            if (!targetSha256.equals(plan.patchSha256, ignoreCase = true)) {
                throw IllegalStateException("Patch target SHA-256 mismatch after write.")
            }

            logger.add("Patch write: verified ${targetSha256.take(12)}...")
            return PatchWriteResult(
                targetRelativePath = plan.targetRelativePath,
                targetDisplayName = plan.targetDisplayName,
                sizeBytes = targetSize,
                sha256 = targetSha256,
            )
        } finally {
            try {
                Shizuku.unbindUserService(args, connection, true)
            } catch (ignored: Throwable) {
            }
        }
    }

    private fun requireSafePlan(plan: PatchWritePlan) {
        if (plan.targetRelativePath != PatchDryRunPlanner.patchPakRelativePath()) {
            throw IllegalStateException("Patch write target must be WuWaVH_99_P.pak only.")
        }
        if (!PatchDryRunPlanner.isAllowedTarget(plan.targetRelativePath)) {
            throw IllegalStateException("Patch write target is not allowlisted.")
        }
        if (!plan.patchFile.isFile || !Sha256Verifier.verify(plan.patchFile, plan.patchSha256)) {
            throw IllegalStateException("Patch file is missing or SHA-256 verification failed.")
        }
        if (plan.patchSizeBytes <= 0L || plan.patchSizeBytes > MAX_PATCH_BYTES) {
            throw IllegalStateException("Patch file size is outside the safe write limit.")
        }
        if (plan.trustedBackup.verifiedFiles != PatchDryRunPlanner.backupRelativePaths().size) {
            throw IllegalStateException("Trusted backup is incomplete.")
        }
    }

    private fun gameAbsolutePath(relativePath: String): String =
        Environment.getExternalStorageDirectory().absolutePath +
            "/Android/data/" +
            AppConstants.GLOBAL_GAME_PACKAGE +
            "/files/" +
            relativePath

    private fun formatMb(bytes: Long): String =
        String.format(Locale.US, "%.1f MB", bytes / 1024.0 / 1024.0)

    data class PatchWriteResult(
        val targetRelativePath: String,
        val targetDisplayName: String,
        val sizeBytes: Long,
        val sha256: String,
    )

    private companion object {
        const val CHUNK_BYTES = 256 * 1024
        const val MAX_PATCH_BYTES = 1024L * 1024L * 1024L
    }
}
