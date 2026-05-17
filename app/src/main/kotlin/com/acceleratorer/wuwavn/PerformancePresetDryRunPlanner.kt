package com.acceleratorer.wuwavn

import android.content.Context

class PerformancePresetDryRunPlanner(
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
        if (installedState?.patchState != PatchInstallState.PATCHED) {
            failures.add(stateBlockReason(installedState))
        }

        val dryRun = diffPlanner.planPerformancePreview()
        val reasonParts = mutableListOf(dryRun.blockedReason)
        if (failures.isNotEmpty()) {
            reasonParts.add("Preview preconditions:\n" + failures.joinToString("\n") { "- $it" })
        } else {
            reasonParts.add("Write preconditions passed. Performance can be applied after final confirmation.")
        }

        return dryRun.copy(
            writeEnabled = failures.isEmpty(),
            blockedReason = reasonParts.joinToString("\n\n"),
        )
    }

    private fun stateBlockReason(installedState: InstalledState?): String = when (installedState?.patchState) {
        PatchInstallState.ORIGINAL ->
            "Current patch state is ORIGINAL. Install Vietnamese Patch before applying Performance."
        PatchInstallState.PARTIAL ->
            "Current patch state is PARTIAL. Use Remove Vietnamese Patch or Restore Original Files first."
        PatchInstallState.UNKNOWN, null ->
            "Current patch state is UNKNOWN. Refresh state and complete game/Shizuku setup first."
        PatchInstallState.PATCHED ->
            "Performance can be applied for this state."
    }
}
