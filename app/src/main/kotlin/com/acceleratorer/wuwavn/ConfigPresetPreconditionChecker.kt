package com.acceleratorer.wuwavn

import android.content.Context

class ConfigPresetPreconditionChecker(
    private val restoreDryRunPlanner: RestoreDryRunPlanner,
    private val presetRepository: ConfigPresetRepository = ConfigPresetRepository(),
) {
    fun check(
        presetId: ConfigPresetId,
        context: Context,
        gameState: GamePackageDetector.State,
        shizukuState: ShizukuState,
    ): ConfigPresetPrecondition {
        val failures = mutableListOf<String>()
        val preset = presetRepository.get(presetId)
        val templateFiles = preset.files
        val trustedBackup = TrustedBackupPolicy.findTrustedBackup(context, restoreDryRunPlanner)

        if (preset.availability != PresetAvailability.WRITE_ENABLED) {
            val lockReason = when (preset.availability) {
                PresetAvailability.DRY_RUN_ONLY -> "${preset.name} is preview-only in this release."
                PresetAvailability.LOCKED -> "${preset.name} is still locked."
                PresetAvailability.WRITE_ENABLED -> "${preset.name} is not available for writing."
            }
            failures.add(lockReason)
        }
        if (gameState != GamePackageDetector.State.GLOBAL_INSTALLED) {
            failures.add("Wuthering Waves Global is not detected.")
        }
        if (shizukuState != ShizukuState.READY) {
            failures.add("Shizuku is not ready.")
        }
        if (trustedBackup == null) {
            failures.add("No trusted VERIFIED backup found. Run Backup Game Configs first.")
        }
        if (!hasExactConfigSet(templateFiles)) {
            failures.add("Config preset templates must be exactly Engine.ini, DeviceProfiles.ini, and MountLang_en.txt.")
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
        // Balanced/Performance state gates live in the controller; high-risk graphics/FPS tokens stay blocked here too.
        if (preset.id == ConfigPresetId.BALANCED && hasForbiddenBalancedLine(templateFiles)) {
            failures.add("Balanced preset contains a forbidden high-risk graphics or FPS setting.")
        }
        if (preset.id == ConfigPresetId.PERFORMANCE && hasForbiddenPerformanceLine(templateFiles)) {
            failures.add("Performance preset contains a forbidden high-risk graphics, FPS, Vulkan, or resolution setting.")
        }

        val plan = if (failures.isEmpty() && trustedBackup != null) {
            ConfigPresetPlan(
                preset = preset,
                templateFiles = preset.files,
                trustedBackup = trustedBackup,
            )
        } else {
            null
        }

        return ConfigPresetPrecondition(preset, plan, failures)
    }

    private fun hasExactConfigSet(templateFiles: List<ConfigTemplateFile>): Boolean {
        val requiredPaths = PatchDryRunPlanner.backupRelativePaths().toSet()
        val templatePaths = templateFiles.map { it.relativePath }.toSet()
        return templateFiles.size == requiredPaths.size && templatePaths == requiredPaths
    }

    private fun hasForbiddenBalancedLine(templateFiles: List<ConfigTemplateFile>): Boolean {
        val content = templateFiles.joinToString("\n") { it.content.toString(Charsets.UTF_8) }
        return FORBIDDEN_BALANCED_TOKENS.any { token ->
            content.contains(token, ignoreCase = true)
        }
    }

    private fun hasForbiddenPerformanceLine(templateFiles: List<ConfigTemplateFile>): Boolean {
        val content = templateFiles.joinToString("\n") { it.content.toString(Charsets.UTF_8) }
        return FORBIDDEN_PERFORMANCE_TOKENS.any { token ->
            content.contains(token, ignoreCase = true)
        }
    }

    private companion object {
        const val MAX_CONFIG_BYTES = 512 * 1024
        val FORBIDDEN_BALANCED_TOKENS = listOf(
            "r.Android.DisableVulkanSupport",
            "bSupportsVulkan",
            "bEnableDynamicMaxFPS",
            "r.MobileContentScaleFactor",
            "r.ScreenPercentage",
            "t.MaxFPS",
            "dp.override",
            "Windows_ExtraHigh",
            "Android_VeryHigh",
            "Nvidia_RTX",
        )
        val FORBIDDEN_PERFORMANCE_TOKENS = listOf(
            "r.Android.DisableVulkanSupport",
            "bSupportsVulkan",
            "bEnableDynamicMaxFPS",
            "r.MobileContentScaleFactor",
            "r.ScreenPercentage",
            "t.MaxFPS",
            "rhi.SyncInterval",
            "r.VSync",
            "dp.override",
            "Windows_ExtraHigh",
            "Android_VeryHigh",
            "Android_High",
            "Nvidia_RTX",
        )
    }
}
