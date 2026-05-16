package com.acceleratorer.wuwavn

import android.content.Context

class RemovePatchPreconditionChecker(
    private val restoreDryRunPlanner: RestoreDryRunPlanner,
) {
    fun check(
        context: Context,
        gameState: GamePackageDetector.State,
        shizukuState: ShizukuState,
    ): RemovePatchPrecondition {
        val failures = mutableListOf<String>()
        val targetRelativePath = PatchDryRunPlanner.patchPakRelativePath()

        if (gameState != GamePackageDetector.State.GLOBAL_INSTALLED) {
            failures.add("Wuthering Waves Global is not detected.")
        }
        if (shizukuState != ShizukuState.READY) {
            failures.add("Shizuku is not ready.")
        }
        if (!PatchDryRunPlanner.isAllowedTarget(targetRelativePath)) {
            failures.add("Patch target is not allowlisted.")
        }

        val trustedBackupDryRun = findTrustedBackupDryRun(context)
        val mountLangFile = trustedBackupDryRun?.files
            ?.firstOrNull { it.displayName == MOUNT_LANG_DISPLAY_NAME }
        if (trustedBackupDryRun == null) {
            failures.add("No trusted VERIFIED backup found. Run Backup Game Configs first.")
        } else if (mountLangFile == null) {
            failures.add("Trusted backup does not contain MountLang_en.txt.")
        } else if (mountLangFile.status != RestoreFileStatus.VERIFIED) {
            failures.add("MountLang_en.txt backup is not VERIFIED.")
        }

        val plan = if (failures.isEmpty() && trustedBackupDryRun != null && mountLangFile != null) {
            RemovePatchPlan(
                targetRelativePath = targetRelativePath,
                targetDisplayName = PatchDryRunPlanner.displayName(targetRelativePath),
                trustedBackupDryRun = trustedBackupDryRun,
                mountLangFile = mountLangFile,
            )
        } else {
            null
        }

        return RemovePatchPrecondition(plan, failures)
    }

    private fun findTrustedBackupDryRun(context: Context): RestoreDryRun? {
        for (session in restoreDryRunPlanner.listBackupSessions(context)) {
            val dryRun = try {
                restoreDryRunPlanner.plan(session)
            } catch (exception: Exception) {
                null
            } ?: continue

            if (TrustedBackupPolicy.isTrustedBackup(dryRun)) {
                return dryRun
            }
        }
        return null
    }

    private companion object {
        const val MOUNT_LANG_DISPLAY_NAME = "MountLang_en.txt"
    }
}
