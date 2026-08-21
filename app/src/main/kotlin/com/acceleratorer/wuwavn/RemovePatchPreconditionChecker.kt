package com.acceleratorer.wuwavn

import android.content.Context
import java.io.File

class RemovePatchPreconditionChecker(
    private val trustedBackupFinder: TrustedBackupFinder,
    private val preflightReader: WuWa36PreflightReader = WuWa36PreflightReader(),
) {
    fun check(
        context: Context,
        gameState: GamePackageDetector.State,
        shizukuState: ShizukuState,
    ): RemovePatchPrecondition {
        val failures = mutableListOf<String>()
        if (gameState != GamePackageDetector.State.GLOBAL_INSTALLED) {
            failures.add("Wuthering Waves Global is not detected.")
        }
        if (shizukuState != ShizukuState.READY) {
            failures.add("Shizuku is not ready.")
        }
        val snapshot = if (shizukuState == ShizukuState.READY) {
            runCatching { preflightReader.read(context) }
                .onFailure { failures.add("WUWA 3.6 preflight failed: ${it.message}") }
                .getOrNull()
        } else null
        val resolvedTarget = snapshot?.pakRelativePath
        if (resolvedTarget == null || !WuWa36Layout.isPatchPakPath(resolvedTarget)) {
            failures.add("WUWA 3.6 patch target is not resolved or allowlisted.")
        }

        val trustedBackupDryRun = snapshot?.let { resolvedSnapshot ->
            trustedBackupFinder.find(context) { backup ->
                backup.files.any {
                    it.status == RestoreFileStatus.VERIFIED &&
                        it.relativePath == resolvedSnapshot.mountLangRelativePath
                }
            }
        }
        val mountLangFile = trustedBackupDryRun?.files?.firstOrNull {
            it.relativePath == snapshot?.mountLangRelativePath
        }
        if (trustedBackupDryRun == null) {
            failures.add("No trusted VERIFIED backup found. Run Backup Game Configs first.")
        } else if (mountLangFile == null) {
            failures.add("Trusted backup does not contain the resolved WUWA 3.6 Resources MountLang.")
        } else if (mountLangFile.status != RestoreFileStatus.VERIFIED) {
            failures.add("MountLang_en.txt backup is not VERIFIED.")
        }

        val plan = if (failures.isEmpty() && trustedBackupDryRun != null && mountLangFile != null) {
            val resolvedSnapshot = snapshot
                ?: return RemovePatchPrecondition(null, failures + "WUWA 3.6 snapshot is missing.")
            val target = resolvedSnapshot.pakRelativePath
            val mountSha256 = mountLangFile.expectedSha256
                ?: return RemovePatchPrecondition(null, failures + "MountLang backup SHA-256 is missing.")
            val backupFile = File(trustedBackupDryRun.sessionDirectory, mountLangFile.displayName)
            runCatching {
                RemovePatchPlan(
                    targetRelativePath = target,
                    targetDisplayName = PatchDryRunPlanner.displayName(target),
                    resourceVersion = resolvedSnapshot.resolvedResourceVersion,
                    langVersion = resolvedSnapshot.resolvedLanguageVersion,
                    mountLangContent = backupFile.readBytes(),
                    mountLangSha256 = mountSha256,
                    trustedBackupDryRun = trustedBackupDryRun,
                    mountLangFile = mountLangFile,
                )
            }.onFailure { failures.add("Trusted WUWA 3.6 MountLang backup could not be read: ${it.message}") }
                .getOrNull()
        } else {
            null
        }

        return RemovePatchPrecondition(plan, failures)
    }

}
