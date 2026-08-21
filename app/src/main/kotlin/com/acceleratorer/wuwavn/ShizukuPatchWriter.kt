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

            service.beginWuWa36Install(
                plan.resourceVersion,
                plan.langVersion,
                plan.sigSourceRelativePath,
                plan.patchSizeBytes,
                plan.patchSha256,
                plan.sigSha1,
                plan.mountLangContent,
                plan.mountLangSha256,
            )
            logger.add("Patch write: started ${formatMb(plan.patchSizeBytes)}")

            val buffer = ByteArray(CHUNK_BYTES)
            var writtenBytes = 0L
            var nextLogBytes = 10L * 1024L * 1024L
            FileInputStream(plan.patchFile).use { input ->
                while (true) {
                    val read = input.read(buffer)
                    if (read == -1) break
                    val chunk = if (read == buffer.size) buffer else buffer.copyOf(read)
                    service.writeWuWa36InstallChunk(chunk, read, plan.patchSizeBytes)
                    writtenBytes += read.toLong()
                    if (writtenBytes >= nextLogBytes) {
                        logger.add("Patch write: ${formatMb(writtenBytes)} / ${formatMb(plan.patchSizeBytes)}")
                        nextLogBytes += 10L * 1024L * 1024L
                    }
                }
            }

            service.finishWuWa36Install()
            val targetPath = gameAbsolutePath(plan.targetRelativePath)
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

    fun removePatchPak(
        context: Context,
        plan: RemovePatchPlan,
        logger: DebugLogger,
    ): PatchRemoveResult {
        requireSafeRemovePlan(plan)

        val serviceRef = AtomicReference<IWuwaPatchService?>()
        val connected = CountDownLatch(1)
        val componentName = ComponentName(context, WuwaPatchUserService::class.java)
        val args = Shizuku.UserServiceArgs(componentName)
            .daemon(false)
            .debuggable(false)
            .processNameSuffix("patch_remove")
            .tag("patch_remove")
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
                throw IllegalStateException("Timed out while connecting Shizuku patch remove service.")
            }

            val service = serviceRef.get()
                ?: throw IllegalStateException("Shizuku patch remove service did not connect.")

            val targetPath = gameAbsolutePath(plan.targetRelativePath)
            val existedBefore = service.exists(targetPath)
            logger.add("Patch remove: target existed before delete = $existedBefore")
            val deleted = service.removeWuWa36Patch(
                plan.resourceVersion,
                plan.langVersion,
                plan.mountLangContent,
                plan.mountLangSha256,
            )
            if (service.exists(targetPath)) {
                throw IllegalStateException("Patch target still exists after delete.")
            }

            logger.add("Patch remove: verified target deleted")
            return PatchRemoveResult(
                targetRelativePath = plan.targetRelativePath,
                targetDisplayName = plan.targetDisplayName,
                existedBefore = existedBefore,
                deleted = deleted,
            )
        } finally {
            try {
                Shizuku.unbindUserService(args, connection, true)
            } catch (ignored: Throwable) {
            }
        }
    }

    private fun requireSafePlan(plan: PatchWritePlan) {
        if (!WuWa36Layout.isPatchPakPath(plan.targetRelativePath)) {
            throw IllegalStateException("Patch write target is not allowlisted.")
        }
        if (!WuWa36Layout.isPatchSigPath(WuWa36Layout.targets(plan.resourceVersion, plan.langVersion).sigRelativePath) ||
            !WuWa36Layout.isMountLangPath(WuWa36Layout.targets(plan.resourceVersion, plan.langVersion).mountLangRelativePath)
        ) throw IllegalStateException("WUWA 3.6 transaction targets are not allowlisted.")
        if (!plan.patchFile.isFile || !Sha256Verifier.verify(plan.patchFile, plan.patchSha256)) {
            throw IllegalStateException("Patch file is missing or SHA-256 verification failed.")
        }
        if (plan.patchSizeBytes <= 0L || plan.patchSizeBytes > MAX_PATCH_BYTES) {
            throw IllegalStateException("Patch file size is outside the safe write limit.")
        }
        if (plan.trustedBackup.verifiedFiles != TrustedBackupPolicy.REQUIRED_FILE_COUNT) {
            throw IllegalStateException("Trusted backup is incomplete.")
        }
    }

    private fun requireSafeRemovePlan(plan: RemovePatchPlan) {
        val target = WuWa36Layout.targets(plan.resourceVersion, plan.langVersion)
        if (plan.targetRelativePath != target.pakRelativePath ||
            !WuWa36Layout.isPatchPakPath(plan.targetRelativePath) ||
            !WuWa36Layout.isPatchSigPath(target.sigRelativePath) ||
            !WuWa36Layout.isMountLangPath(target.mountLangRelativePath)
        ) {
            throw IllegalStateException("Patch remove target is not allowlisted.")
        }
        if (!TrustedBackupPolicy.isTrustedBackup(plan.trustedBackupDryRun)) {
            throw IllegalStateException("Patch remove requires a trusted VERIFIED backup.")
        }
        if (plan.mountLangFile.relativePath != target.mountLangRelativePath ||
            plan.mountLangFile.status != RestoreFileStatus.VERIFIED
        ) {
            throw IllegalStateException("Patch remove requires VERIFIED WUWA 3.6 Resources MountLang backup.")
        }
        if (!Sha256Verifier.sha256(plan.mountLangContent).equals(plan.mountLangSha256, ignoreCase = true)) {
            throw IllegalStateException("Patch remove MountLang backup changed before write.")
        }
    }

    private fun gameAbsolutePath(relativePath: String): String =
        if (WuWa36Layout.isAllowedDynamicPath(relativePath)) {
            Environment.getExternalStorageDirectory().absolutePath +
                "/Android/data/" + AppConstants.GLOBAL_GAME_PACKAGE + "/files/" + relativePath
        } else {
        Environment.getExternalStorageDirectory().absolutePath +
            "/Android/data/" +
            AppConstants.GLOBAL_GAME_PACKAGE +
            "/files/" +
            relativePath
        }

    private fun formatMb(bytes: Long): String =
        String.format(Locale.US, "%.1f MB", bytes / 1024.0 / 1024.0)

    data class PatchWriteResult(
        val targetRelativePath: String,
        val targetDisplayName: String,
        val sizeBytes: Long,
        val sha256: String,
    )

    data class PatchRemoveResult(
        val targetRelativePath: String,
        val targetDisplayName: String,
        val existedBefore: Boolean,
        val deleted: Boolean,
    )

    private companion object {
        const val CHUNK_BYTES = 256 * 1024
        const val MAX_PATCH_BYTES = 1024L * 1024L * 1024L
    }
}
