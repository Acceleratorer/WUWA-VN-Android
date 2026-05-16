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
        val trustedBackup = findTrustedBackup(context)

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

    private fun findTrustedBackup(context: Context): TrustedBackupInfo? {
        for (session in restoreDryRunPlanner.listBackupSessions(context)) {
            val dryRun = try {
                restoreDryRunPlanner.plan(session)
            } catch (exception: Exception) {
                null
            } ?: continue

            if (isTrustedBackup(dryRun)) {
                return TrustedBackupInfo(
                    sessionDirectory = dryRun.sessionDirectory,
                    createdAt = dryRun.createdAt,
                    verifiedFiles = dryRun.verifiedCount(),
                )
            }
        }
        return null
    }

    private fun isTrustedBackup(dryRun: RestoreDryRun): Boolean =
        dryRun.backupType == BackupManager.READ_ONLY_CONFIG_BACKUP_TYPE &&
            dryRun.gamePackage == AppConstants.GLOBAL_GAME_PACKAGE &&
            dryRun.restoreWriteEnabled == false &&
            dryRun.allFilesVerified() &&
            dryRun.hasOnlyVerifiedRequiredConfigFiles()

    private companion object {
        const val MAX_PATCH_BYTES = 1024L * 1024L * 1024L
    }
}
