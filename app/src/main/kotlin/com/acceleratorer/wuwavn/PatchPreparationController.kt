package com.acceleratorer.wuwavn

import android.app.Activity
import android.widget.Toast

class PatchPreparationController(
    private val activity: Activity,
    private val logger: DebugLogger,
    private val dryRunPlanner: PatchDryRunPlanner,
    private val manifestRepository: PatchManifestRepository,
    private val shizukuFileSystem: ShizukuFileSystem,
    private val downloadClient: DownloadClient,
    private val patchWritePreconditionChecker: PatchWritePreconditionChecker,
    private val removePatchPreconditionChecker: RemovePatchPreconditionChecker,
    private val patchWriter: ShizukuPatchWriter,
    private val gamePackageDetector: GamePackageDetector,
    private val shizukuStateChecker: ShizukuStateChecker,
    private val dialogs: DialogFactory,
    private val onBackupPath: (String) -> Unit,
) {
    @Volatile private var patchPreparationRunning = false
    @Volatile private var patchWriteRunning = false
    @Volatile private var patchRemoveRunning = false

    fun showPatchDryRun(
        gameState: GamePackageDetector.State,
        shizukuState: ShizukuState,
    ) {
        logger.add("Dry run: started")
        try {
            val dryRun = dryRunPlanner.plan(activity)
            onBackupPath(dryRun.backupDirectory.absolutePath)
            var message = dryRun.describe() + "\n\n" + shizukuFileSystem.disabledReason(shizukuState)
            if (gameState != GamePackageDetector.State.GLOBAL_INSTALLED) {
                message = "Global Wuthering Waves package is not detected.\n\n$message"
            }
            dialogs.showMessage("Dry run", message)
            logger.add("Dry run: allowlist verified")
            logger.add("Backup target planned: ${dryRun.backupDirectory.absolutePath}")
        } catch (exception: RuntimeException) {
            dialogs.showMessage("Dry run failed", exception.message.orEmpty())
            logger.add("Dry run: failed - ${exception.message}")
        }
    }

    fun preparePatchSafely() {
        if (patchPreparationRunning) {
            Toast.makeText(activity, "Patch preparation is already running.", Toast.LENGTH_SHORT).show()
            return
        }

        patchPreparationRunning = true
        logger.add("Patch preparation: started")

        Thread {
            val manifest = manifestRepository.current()
            try {
                dryRunPlanner.plan(activity)
                logger.add("Dry run: allowlist verified before download")

                val patchFile = downloadClient.downloadAndVerify(
                    activity,
                    manifest,
                    DownloadClient.ProgressListener { message -> logger.add(message) },
                )

                logger.add("Patch file: ${patchFile.absolutePath}")
                activity.runOnUiThread {
                    dialogs.showMessage(
                        "Patch verified",
                        "Patch was downloaded and verified successfully.\n\nNext:\n1. Run Backup Game Configs.\n2. If Install Vietnamese Patch is enabled, you can install.\n3. If it stays disabled, open More Tools > Game Path Diagnostic and send the report.\n\nWUWA 3.6 install requires a resolved Resources layout and verified SIG source.",
                    )
                }
            } catch (exception: Exception) {
                logger.add("Patch preparation failed: ${exception.message}")
                activity.runOnUiThread {
                    dialogs.showMessage("Patch preparation failed", exception.message.orEmpty())
                }
            } finally {
                patchPreparationRunning = false
            }
        }.start()
    }

    fun showPatchWriteDryRun(
        gameState: GamePackageDetector.State,
        shizukuState: ShizukuState,
    ) {
        if (patchWriteRunning) {
            Toast.makeText(activity, "Patch install is already running.", Toast.LENGTH_SHORT).show()
            return
        }

        val precondition = patchWritePreconditionChecker.check(activity, gameState, shizukuState)
        val summary = patchWriteDryRunSummary(precondition)
        if (!precondition.isReady()) {
            dialogs.showMessage("Patch write dry run", summary)
            logger.add("Patch write: blocked - ${precondition.failures.joinToString("; ")}")
            return
        }

        dialogs.showConfirmation(
            title = "Patch write dry run",
            message = summary + "\n\nContinue to install the Vietnamese PAK?",
            positiveLabel = "Continue Install",
        ) {
            showFinalPatchConfirmation(precondition.plan!!)
        }
    }

    fun showRemovePatchDryRun(
        gameState: GamePackageDetector.State,
        shizukuState: ShizukuState,
    ) {
        if (patchRemoveRunning) {
            Toast.makeText(activity, "Patch removal is already running.", Toast.LENGTH_SHORT).show()
            return
        }

        val precondition = removePatchPreconditionChecker.check(activity, gameState, shizukuState)
        val summary = removePatchDryRunSummary(precondition)
        if (!precondition.isReady()) {
            dialogs.showMessage("Remove patch dry run", summary)
            logger.add("Patch remove: blocked - ${precondition.failures.joinToString("; ")}")
            return
        }

        dialogs.showConfirmation(
            title = "Remove patch dry run",
            message = summary + "\n\nContinue to the final removal confirmation?",
            positiveLabel = "Continue Remove",
        ) {
            showFinalRemoveConfirmation(precondition.plan!!)
        }
    }

    private fun showFinalPatchConfirmation(plan: PatchWritePlan) {
        dialogs.showConfirmation(
            title = "Final patch confirmation",
            message = "This will write only this allowlisted patch file into the game folder:\n\n" +
                "- ${plan.targetDisplayName}\n\n" +
                "Config files will not be modified by this PAK install. Use Apply Safe Config Preset separately. Continue only if backup and patch details look correct.",
            positiveLabel = "Install PAK Now",
        ) {
            installPatchPak()
        }
    }

    private fun installPatchPak() {
        if (patchWriteRunning) {
            Toast.makeText(activity, "Patch install is already running.", Toast.LENGTH_SHORT).show()
            return
        }

        val gameState = gamePackageDetector.detect(activity)
        val shizukuState = shizukuStateChecker.check(activity)
        val precondition = patchWritePreconditionChecker.check(activity, gameState, shizukuState)
        val plan = precondition.plan
        if (plan == null) {
            dialogs.showMessage("Patch install blocked", patchWriteDryRunSummary(precondition))
            logger.add("Patch write: blocked before write - ${precondition.failures.joinToString("; ")}")
            return
        }

        patchWriteRunning = true
        logger.add("Patch write: started")

        Thread {
            try {
                val result = patchWriter.writePatchPak(activity, plan, logger)
                logger.add("Patch write: success")
                activity.runOnUiThread {
                    dialogs.showMessage("Patch installed", patchWriteResultSummary(result))
                }
            } catch (exception: Exception) {
                logger.add("Patch write failed: ${exception.message}")
                activity.runOnUiThread {
                    dialogs.showMessage("Patch install failed", exception.message.orEmpty())
                }
            } finally {
                patchWriteRunning = false
            }
        }.start()
    }

    private fun showFinalRemoveConfirmation(plan: RemovePatchPlan) {
        dialogs.showConfirmation(
            title = "Final remove confirmation",
            message = "This will restore the WUWA 3.6 Resources MountLang from a trusted VERIFIED backup, then delete only the allowlisted PAK and SIG:\n\n" +
                "- ${plan.targetDisplayName}\n\n" +
                "Engine.ini and DeviceProfiles.ini will not be changed by this remove flow. Continue only if the backup and target look correct.",
            positiveLabel = "Remove Patch Now",
        ) {
            removePatchPak(plan)
        }
    }

    private fun removePatchPak(confirmedPlan: RemovePatchPlan) {
        if (patchRemoveRunning) {
            Toast.makeText(activity, "Patch removal is already running.", Toast.LENGTH_SHORT).show()
            return
        }

        val gameState = gamePackageDetector.detect(activity)
        val shizukuState = shizukuStateChecker.check(activity)
        val precondition = removePatchPreconditionChecker.check(activity, gameState, shizukuState)
        val plan = precondition.plan
        if (plan == null) {
            dialogs.showMessage("Patch removal blocked", removePatchDryRunSummary(precondition))
            logger.add("Patch remove: blocked before write - ${precondition.failures.joinToString("; ")}")
            return
        }
        if (plan.trustedBackupDryRun.sessionDirectory != confirmedPlan.trustedBackupDryRun.sessionDirectory ||
            plan.mountLangFile.expectedSha256 != confirmedPlan.mountLangFile.expectedSha256 ||
            plan.targetRelativePath != confirmedPlan.targetRelativePath ||
            plan.resourceVersion != confirmedPlan.resourceVersion ||
            plan.langVersion != confirmedPlan.langVersion ||
            plan.mountLangSha256 != confirmedPlan.mountLangSha256
        ) {
            dialogs.showMessage("Patch removal blocked", "Trusted backup changed after dry-run. Reopen Remove Vietnamese Patch and confirm again.")
            logger.add("Patch remove: blocked - trusted backup changed after dry-run")
            return
        }

        patchRemoveRunning = true
        logger.add("Patch remove: started")

        Thread {
            try {
                val removeResult = patchWriter.removePatchPak(activity, plan, logger)
                logger.add("Patch remove: success")
                activity.runOnUiThread {
                    dialogs.showMessage("Patch removed", removePatchResultSummary(removeResult))
                }
            } catch (exception: Exception) {
                logger.add("Patch remove failed: ${exception.message}")
                activity.runOnUiThread {
                    dialogs.showMessage("Patch removal failed", exception.message.orEmpty())
                }
            } finally {
                patchRemoveRunning = false
            }
        }.start()
    }

    private fun patchWriteDryRunSummary(precondition: PatchWritePrecondition): String = buildString {
        val plan = precondition.plan
        append("Patch write mode:\n")
            .append("PAK-only install\n\n")
            .append("Files to write:\n")
            .append("- WuWaVH_99_P.pak\n\n")
            .append("Config files:\n")
            .append("Not modified by this PAK install. Config presets are separate verified flows.\n")

        if (plan != null) {
            append("\nVerified PAK:\n")
                .append(plan.patchFile.absolutePath)
                .append("\nSize: ")
                .append(plan.patchSizeBytes)
                .append(" bytes\nSHA-256: ")
                .append(plan.patchSha256)
                .append("\n\nTrusted backup:\n")
                .append(plan.trustedBackup.sessionDirectory.absolutePath)
                .append("\nCreated at: ")
                .append(plan.trustedBackup.createdAt)
                .append("\nVerified config files: ")
                .append(plan.trustedBackup.verifiedFiles)
                .append("/")
                .append(TrustedBackupPolicy.REQUIRED_FILE_COUNT)
                .append("\n\nTarget:\n")
                .append(plan.targetRelativePath)
                .append("\n\nAfter write:\n")
                .append("The app will re-read the game PAK and verify size + SHA-256.")
        } else {
            append("\nBlocked:\n")
            for (failure in precondition.failures) {
                append("- ").append(failure).append('\n')
            }
        }
    }

    private fun patchWriteResultSummary(result: ShizukuPatchWriter.PatchWriteResult): String = buildString {
        append("Installed patch file:\n")
            .append(result.targetDisplayName)
            .append("\n\nTarget:\n")
            .append(result.targetRelativePath)
            .append("\n\nSize:\n")
            .append(result.sizeBytes)
            .append(" bytes\n\nSHA-256:\n")
            .append(result.sha256)
            .append("\n\nTarget file was re-read from the game folder and verified.")
            .append("\n\nConfig preset writing is handled separately.")
    }

    private fun removePatchDryRunSummary(precondition: RemovePatchPrecondition): String = buildString {
        val plan = precondition.plan
        append("Remove Vietnamese Patch plan:\n")
            .append("WUWA 3.6 PAK + SIG removal with Resources MountLang rollback\n\n")
            .append("File to remove:\n")
            .append("- WuWaVH_99_P.pak\n- WuWaVH_99_P.sig\n\n")
            .append("Target:\n")
            .append(plan?.targetRelativePath ?: "resolved after WUWA 3.6 preflight")
            .append("\n\nSafety rules:\n")
            .append("- Restore the resolved WUWA 3.6 Resources MountLang from a trusted VERIFIED backup\n")
            .append("- Delete only the resolved allowlisted PAK + SIG targets\n")
            .append("- Verify both patch files no longer exist after delete\n")
            .append("- Do not modify Engine.ini or DeviceProfiles.ini\n")

        if (plan != null) {
            append("\nTrusted backup:\n")
                .append(plan.trustedBackupDryRun.sessionDirectory.absolutePath)
                .append("\nCreated at: ")
                .append(plan.trustedBackupDryRun.createdAt)
                .append("\nResources MountLang: ")
                .append(plan.mountLangFile.status.label)
                .append("\nSHA-256: ")
                .append(plan.mountLangFile.expectedSha256.orEmpty())
                .append("\n\nAfter write:\n")
                .append("The app will verify Resources MountLang and verify both patch files are deleted.")
        } else {
            append("\nBlocked:\n")
            for (failure in precondition.failures) {
                append("- ").append(failure).append('\n')
            }
        }
    }

    private fun removePatchResultSummary(
        removeResult: ShizukuPatchWriter.PatchRemoveResult,
    ): String = buildString {
        append("Removed patch target:\n")
            .append(removeResult.targetDisplayName)
            .append("\n\nTarget:\n")
            .append(removeResult.targetRelativePath)
            .append("\n\nPAK existed before removal:\n")
            .append(removeResult.existedBefore)
            .append("\nDelete performed:\n")
            .append(removeResult.deleted)
            .append("\n\nWUWA 3.6 Resources MountLang restored from trusted backup in the same transaction.")
            .append("\n\nThe app verified the WUWA 3.6 PAK target no longer exists after removal.")
    }

}
