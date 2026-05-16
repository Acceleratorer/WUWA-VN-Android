package com.acceleratorer.wuwavn

import android.content.Context

class ConfigPresetPreconditionChecker(
    private val restoreDryRunPlanner: RestoreDryRunPlanner,
) {
    fun checkSafeDefault(
        context: Context,
        gameState: GamePackageDetector.State,
        shizukuState: ShizukuState,
    ): ConfigPresetPrecondition {
        val failures = mutableListOf<String>()
        val templateFiles = SafeConfigTemplates.safeDefaultFiles()
        val trustedBackup = TrustedBackupPolicy.findTrustedBackup(context, restoreDryRunPlanner)

        if (gameState != GamePackageDetector.State.GLOBAL_INSTALLED) {
            failures.add("Wuthering Waves Global is not detected.")
        }
        if (shizukuState != ShizukuState.READY) {
            failures.add("Shizuku is not ready.")
        }
        if (trustedBackup == null) {
            failures.add("No trusted VERIFIED backup found. Run Backup Game Configs first.")
        }
        if (!hasExactSafeConfigSet(templateFiles)) {
            failures.add("Safe preset templates must be exactly Engine.ini, DeviceProfiles.ini, and MountLang_en.txt.")
        }
        for (file in templateFiles) {
            if (!PatchDryRunPlanner.isAllowedTarget(file.relativePath)) {
                failures.add("${file.displayName} is not allowlisted.")
            }
            if (file.content.isEmpty() || file.sizeBytes > MAX_CONFIG_BYTES) {
                failures.add("${file.displayName} template size is outside the safe write limit.")
            }
            val actualSha256 = Sha256Verifier.sha256(file.content)
            if (!actualSha256.equals(file.sha256, ignoreCase = true)) {
                failures.add("${file.displayName} template hash mismatch.")
            }
        }

        val plan = if (failures.isEmpty() && trustedBackup != null) {
            ConfigPresetPlan(
                presetId = SafeConfigTemplates.SAFE_DEFAULT_ID,
                presetName = SafeConfigTemplates.SAFE_DEFAULT_NAME,
                templateFiles = templateFiles,
                trustedBackup = trustedBackup,
            )
        } else {
            null
        }

        return ConfigPresetPrecondition(plan, failures)
    }

    private fun hasExactSafeConfigSet(templateFiles: List<ConfigTemplateFile>): Boolean {
        val requiredPaths = PatchDryRunPlanner.backupRelativePaths().toSet()
        val templatePaths = templateFiles.map { it.relativePath }.toSet()
        return templateFiles.size == requiredPaths.size && templatePaths == requiredPaths
    }

    private companion object {
        const val MAX_CONFIG_BYTES = 512 * 1024
    }
}
