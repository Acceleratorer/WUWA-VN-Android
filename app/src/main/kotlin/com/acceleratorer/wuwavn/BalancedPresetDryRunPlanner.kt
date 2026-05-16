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

        val dryRun = diffPlanner.planBalancedPreview()
        val reasonParts = mutableListOf(dryRun.blockedReason)
        if (failures.isNotEmpty()) {
            reasonParts.add("Preview preconditions:\n" + failures.joinToString("\n") { "- $it" })
        } else {
            reasonParts.add("Preview preconditions passed. Trusted backup found:\n${trustedBackup?.sessionDirectory?.absolutePath}")
        }
        reasonParts.add(stateWarning(installedState))

        return dryRun.copy(
            writeEnabled = false,
            blockedReason = reasonParts.joinToString("\n\n"),
        )
    }

    private fun stateWarning(installedState: InstalledState?): String = when (installedState?.patchState) {
        PatchInstallState.ORIGINAL ->
            "Current patch state is ORIGINAL. Balanced preview is safe, but install the patch before expecting the Vietnamese PAK mount to be active."
        PatchInstallState.PATCHED ->
            "Current patch state is PATCHED. Balanced preview can be reviewed safely."
        PatchInstallState.PARTIAL ->
            "Current patch state is PARTIAL. Repair or restore before applying future presets."
        PatchInstallState.UNKNOWN, null ->
            "Current patch state is UNKNOWN. This preview will not write anything."
    }
}
