package com.acceleratorer.wuwavn

data class HomeActionState(
    val installPatchEnabled: Boolean,
    val removePatchEnabled: Boolean,
    val applySafeEnabled: Boolean,
    val applyBalancedEnabled: Boolean,
    val applyPerformanceEnabled: Boolean,
    val restoreEnabled: Boolean,
    val backupEnabled: Boolean,
    val downloadPatchEnabled: Boolean,
    val primaryHint: String,
)

object HomeActionStateResolver {
    fun resolve(
        installedState: InstalledState?,
        gameState: GamePackageDetector.State,
        shizukuState: ShizukuState,
    ): HomeActionState {
        val baseReady = gameState == GamePackageDetector.State.GLOBAL_INSTALLED &&
            shizukuState == ShizukuState.READY

        if (!baseReady || installedState == null) {
            return HomeActionState(
                installPatchEnabled = false,
                removePatchEnabled = false,
                applySafeEnabled = false,
                applyBalancedEnabled = false,
                applyPerformanceEnabled = false,
                restoreEnabled = false,
                backupEnabled = baseReady,
                downloadPatchEnabled = true,
                primaryHint = "Complete game/Shizuku setup before file operations.",
            )
        }

        return when (installedState.patchState) {
            PatchInstallState.ORIGINAL -> HomeActionState(
                installPatchEnabled = installedState.hasTrustedBackup,
                removePatchEnabled = false,
                applySafeEnabled = installedState.hasTrustedBackup && presetWriteEnabled(ConfigPresetId.SAFE_DEFAULT),
                applyBalancedEnabled = false,
                applyPerformanceEnabled = false,
                restoreEnabled = installedState.hasTrustedBackup && Wuwa36SafetyPolicy.RESTORE_WRITE_ENABLED,
                backupEnabled = true,
                downloadPatchEnabled = true,
                primaryHint = if (installedState.hasTrustedBackup) {
                    "Original state detected. Install Vietnamese Patch before applying Balanced or Performance."
                } else {
                    "Original state detected. Run Backup Game Configs before patch install."
                },
            )

            PatchInstallState.PATCHED -> HomeActionState(
                installPatchEnabled = installedState.hasTrustedBackup,
                removePatchEnabled = installedState.hasTrustedBackup,
                applySafeEnabled = installedState.hasTrustedBackup && presetWriteEnabled(ConfigPresetId.SAFE_DEFAULT),
                applyBalancedEnabled = installedState.hasTrustedBackup && presetWriteEnabled(ConfigPresetId.BALANCED),
                applyPerformanceEnabled = installedState.hasTrustedBackup && presetWriteEnabled(ConfigPresetId.PERFORMANCE),
                restoreEnabled = installedState.hasTrustedBackup && Wuwa36SafetyPolicy.RESTORE_WRITE_ENABLED,
                backupEnabled = true,
                downloadPatchEnabled = true,
                primaryHint = if (installedState.hasTrustedBackup) {
                    "Vietnamese patch appears installed. Reinstall/update or Remove is available; config and general restore writes are locked for WUWA 3.6."
                } else {
                    "Vietnamese patch appears installed. Run Backup Game Configs before write actions."
                },
            )

            PatchInstallState.PARTIAL -> HomeActionState(
                installPatchEnabled = false,
                removePatchEnabled = installedState.hasTrustedBackup,
                applySafeEnabled = false,
                applyBalancedEnabled = false,
                applyPerformanceEnabled = false,
                restoreEnabled = installedState.hasTrustedBackup && Wuwa36SafetyPolicy.RESTORE_WRITE_ENABLED,
                backupEnabled = true,
                downloadPatchEnabled = true,
                primaryHint = if (installedState.hasTrustedBackup) {
                    "Partial state detected. Use Remove Patch or Restore Original Files."
                } else {
                    "Partial state detected. Create or recover a trusted backup before repair."
                },
            )

            PatchInstallState.UNKNOWN -> HomeActionState(
                installPatchEnabled = false,
                removePatchEnabled = false,
                applySafeEnabled = false,
                applyBalancedEnabled = false,
                applyPerformanceEnabled = false,
                restoreEnabled = false,
                backupEnabled = true,
                downloadPatchEnabled = true,
                primaryHint = "State unknown. Dangerous actions are disabled.",
            )
        }
    }

    private fun presetWriteEnabled(id: ConfigPresetId): Boolean =
        ConfigPresetAvailabilityPolicy.availability(id) == PresetAvailability.WRITE_ENABLED
}
