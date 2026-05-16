package com.acceleratorer.wuwavn

data class HomeActionState(
    val installPatchEnabled: Boolean,
    val removePatchEnabled: Boolean,
    val applySafeEnabled: Boolean,
    val applyBalancedEnabled: Boolean,
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
                applySafeEnabled = installedState.hasTrustedBackup,
                applyBalancedEnabled = installedState.hasTrustedBackup,
                restoreEnabled = installedState.hasTrustedBackup,
                backupEnabled = true,
                downloadPatchEnabled = true,
                primaryHint = if (installedState.hasTrustedBackup) {
                    "Original state detected. Patch install is available."
                } else {
                    "Original state detected. Run Backup Game Configs before patch install."
                },
            )

            PatchInstallState.PATCHED -> HomeActionState(
                installPatchEnabled = false,
                removePatchEnabled = installedState.hasTrustedBackup,
                applySafeEnabled = installedState.hasTrustedBackup,
                applyBalancedEnabled = installedState.hasTrustedBackup,
                restoreEnabled = installedState.hasTrustedBackup,
                backupEnabled = true,
                downloadPatchEnabled = true,
                primaryHint = if (installedState.hasTrustedBackup) {
                    "Vietnamese patch appears installed. Remove or restore is available."
                } else {
                    "Vietnamese patch appears installed. Run Backup Game Configs before write actions."
                },
            )

            PatchInstallState.PARTIAL -> HomeActionState(
                installPatchEnabled = false,
                removePatchEnabled = installedState.hasTrustedBackup,
                applySafeEnabled = false,
                applyBalancedEnabled = false,
                restoreEnabled = installedState.hasTrustedBackup,
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
                restoreEnabled = false,
                backupEnabled = true,
                downloadPatchEnabled = true,
                primaryHint = "State unknown. Dangerous actions are disabled.",
            )
        }
    }
}
