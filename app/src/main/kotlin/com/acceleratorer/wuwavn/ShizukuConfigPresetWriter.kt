package com.acceleratorer.wuwavn

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.os.Environment
import android.os.IBinder
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import rikka.shizuku.Shizuku

class ShizukuConfigPresetWriter {
    fun writeSafeDefaultPreset(
        context: Context,
        plan: ConfigPresetPlan,
        logger: DebugLogger,
    ): ConfigPresetWriteResult {
        requireSafePlan(plan)

        val serviceRef = AtomicReference<IWuwaRestoreService?>()
        val connected = CountDownLatch(1)
        val componentName = ComponentName(context, WuwaConfigUserService::class.java)
        val args = Shizuku.UserServiceArgs(componentName)
            .daemon(false)
            .debuggable(false)
            .processNameSuffix("config")
            .tag("config")
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
                throw IllegalStateException("Timed out while connecting Shizuku config service.")
            }

            val service = serviceRef.get()
                ?: throw IllegalStateException("Shizuku config service did not connect.")

            val writtenFiles = mutableListOf<ConfigPresetWriteInfo>()
            for (template in plan.templateFiles) {
                val contentSha256 = Sha256Verifier.sha256(template.content)
                if (!contentSha256.equals(template.sha256, ignoreCase = true)) {
                    throw IllegalStateException("${template.displayName} template changed before write.")
                }

                val targetPath = gameAbsolutePath(template.relativePath)
                logger.add("Safe preset write: writing ${template.displayName}")
                service.writeConfigFile(targetPath, template.content, template.sha256)

                val targetBytes = service.readFile(targetPath, MAX_CONFIG_BYTES)
                val targetSha256 = Sha256Verifier.sha256(targetBytes)
                if (!targetSha256.equals(template.sha256, ignoreCase = true)) {
                    throw IllegalStateException("${template.displayName} target verification failed after write.")
                }

                writtenFiles.add(
                    ConfigPresetWriteInfo(
                        displayName = template.displayName,
                        relativePath = template.relativePath,
                        sizeBytes = targetBytes.size.toLong(),
                        sha256 = targetSha256,
                    ),
                )
                logger.add("Safe preset write: verified ${template.displayName} (${targetSha256.take(12)}...)")
            }

            return ConfigPresetWriteResult(
                presetName = plan.presetName,
                writtenFiles = writtenFiles,
            )
        } finally {
            try {
                Shizuku.unbindUserService(args, connection, true)
            } catch (ignored: Throwable) {
            }
        }
    }

    private fun requireSafePlan(plan: ConfigPresetPlan) {
        if (plan.presetId != SafeConfigTemplates.SAFE_DEFAULT_ID) {
            throw IllegalStateException("Only Safe / Default config preset is unlocked.")
        }
        val requiredPaths = PatchDryRunPlanner.backupRelativePaths().toSet()
        val templatePaths = plan.templateFiles.map { it.relativePath }.toSet()
        if (plan.templateFiles.size != requiredPaths.size || templatePaths != requiredPaths) {
            throw IllegalStateException("Safe preset must contain exactly the three required config files.")
        }
        for (template in plan.templateFiles) {
            if (!PatchDryRunPlanner.isAllowedTarget(template.relativePath)) {
                throw IllegalStateException("${template.displayName} target is not allowlisted.")
            }
            if (template.content.isEmpty() || template.content.size > MAX_CONFIG_BYTES) {
                throw IllegalStateException("${template.displayName} template size is outside the safe write limit.")
            }
            if (!Sha256Verifier.sha256(template.content).equals(template.sha256, ignoreCase = true)) {
                throw IllegalStateException("${template.displayName} template SHA-256 mismatch.")
            }
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

    data class ConfigPresetWriteResult(
        val presetName: String,
        val writtenFiles: List<ConfigPresetWriteInfo>,
    )

    data class ConfigPresetWriteInfo(
        val displayName: String,
        val relativePath: String,
        val sizeBytes: Long,
        val sha256: String,
    )

    private companion object {
        const val MAX_CONFIG_BYTES = 512 * 1024
    }
}
