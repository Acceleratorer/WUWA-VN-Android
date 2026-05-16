package com.acceleratorer.wuwavn

import android.content.Context

class BalancedPresetDryRunPlanner(
    private val trustedBackupFinder: TrustedBackupFinder,
    private val diffPlanner: ConfigPresetDiffPlanner,
) {
    fun plan(
        context: Context,
        gameState: GamePackageDetector.State,
        shizukuState: ShizukuState,
        installedState: InstalledState?,
    ): ConfigPresetDryRun {
        val failures = mutableListOf<String>()
        if (gameState != GamePackageDetector.State.GLOBAL_INSTALLED) {
            failures.add("Wuthering Waves Global is not detected.")
        }
        if (shizukuState != ShizukuState.READY) {
            failures.add("Shizuku is not READY.")
        }

        val trustedBackup = trustedBackupFinder.find(context)
        if (trustedBackup == null) {
            failures.add("No trusted VERIFIED backup found. Run Backup Game Configs first.")
        }
        if (!allowsBalancedWrite(installedState)) {
            failures.add(stateBlockReason(installedState))
        }

        val dryRun = diffPlanner.planBalancedPreview()
        val reasonParts = mutableListOf(dryRun.blockedReason)
        if (failures.isNotEmpty()) {
            reasonParts.add("Preview preconditions:\n" + failures.joinToString("\n") { "- $it" })
        } else {
            reasonParts.add("Preview preconditions passed. Trusted backup found:\n${trustedBackup?.sessionDirectory?.absolutePath}")
        }
        reasonParts.add(stateWarning(installedState))

        return dryRun.copy(
            writeEnabled = dryRun.writeEnabled && failures.isEmpty(),
            blockedReason = reasonParts.joinToString("\n\n"),
        )
    }

    private fun allowsBalancedWrite(installedState: InstalledState?): Boolean =
        installedState?.patchState == PatchInstallState.PATCHED

    private fun stateBlockReason(installedState: InstalledState?): String = when (installedState?.patchState) {
        PatchInstallState.ORIGINAL -> "Current patch state is ORIGINAL. Install Vietnamese Patch before applying Balanced."
        PatchInstallState.PARTIAL -> "Current patch state is PARTIAL. Use Remove Vietnamese Patch or Restore Original Files before applying Balanced."
        PatchInstallState.UNKNOWN, null -> "Current patch state is UNKNOWN. Refresh state and complete game/Shizuku setup before applying Balanced."
        PatchInstallState.PATCHED -> "Balanced write is available for this state."
    }

    private fun stateWarning(installedState: InstalledState?): String = when (installedState?.patchState) {
        PatchInstallState.ORIGINAL ->
            "Current patch state is ORIGINAL. Install Vietnamese Patch, refresh state, then apply Balanced."
        PatchInstallState.PATCHED ->
            "Current patch state is PATCHED. Balanced can be applied after final confirmation."
        PatchInstallState.PARTIAL ->
            "Current patch state is PARTIAL. Repair or restore before applying Balanced."
        PatchInstallState.UNKNOWN, null ->
            "Current patch state is UNKNOWN. Balanced write is blocked."
    }
}
