package com.acceleratorer.wuwavn

import android.content.Context

class PatchWritePreconditionChecker(
    private val manifestRepository: PatchManifestRepository,
    private val downloadClient: DownloadClient,
    private val restoreDryRunPlanner: RestoreDryRunPlanner,
) {
    fun check(
        context: Context,
        gameState: GamePackageDetector.State,
        shizukuState: ShizukuState,
    ): PatchWritePrecondition {
        val failures = mutableListOf<String>()
        val manifest = manifestRepository.current()
        val targetRelativePath = PatchDryRunPlanner.patchPakRelativePath()
        val patchFile = downloadClient.verifiedPatchFile(context, manifest)
        val trustedBackup = TrustedBackupPolicy.findTrustedBackup(context, restoreDryRunPlanner)

        if (gameState != GamePackageDetector.State.GLOBAL_INSTALLED) {
            failures.add("Wuthering Waves Global is not detected.")
        }
        if (shizukuState != ShizukuState.READY) {
            failures.add("Shizuku is not ready.")
        }
        if (!PatchDryRunPlanner.isAllowedTarget(targetRelativePath)) {
            failures.add("Patch target is not allowlisted.")
        }
        if (patchFile == null) {
            failures.add("Verified PAK is missing. Run Download & Verify Patch first.")
        } else if (patchFile.length() <= 0L || patchFile.length() > MAX_PATCH_BYTES) {
            failures.add("Verified PAK size is outside the safe write limit.")
        }
        if (trustedBackup == null) {
            failures.add("No trusted VERIFIED backup found. Run Backup Game Configs first.")
        }

        val plan = if (failures.isEmpty() && patchFile != null && trustedBackup != null) {
            PatchWritePlan(
                manifest = manifest,
                patchFile = patchFile,
                patchSizeBytes = patchFile.length(),
                patchSha256 = manifest.pakSha256,
                targetRelativePath = targetRelativePath,
                targetDisplayName = PatchDryRunPlanner.displayName(targetRelativePath),
                trustedBackup = trustedBackup,
            )
        } else {
            null
        }

        return PatchWritePrecondition(plan, failures)
    }

    private companion object {
        const val MAX_PATCH_BYTES = 1024L * 1024L * 1024L
    }
}
