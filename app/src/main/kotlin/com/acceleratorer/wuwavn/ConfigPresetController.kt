package com.acceleratorer.wuwavn

import android.app.Activity
import android.widget.Toast

class ConfigPresetController(
    private val activity: Activity,
    private val logger: DebugLogger,
    private val preconditionChecker: ConfigPresetPreconditionChecker,
    private val configPresetWriter: ShizukuConfigPresetWriter,
    private val gamePackageDetector: GamePackageDetector,
    private val shizukuStateChecker: ShizukuStateChecker,
    private val dialogs: DialogFactory,
    private val onPresetFinished: () -> Unit,
) {
    @Volatile private var configWriteRunning = false

    fun showSafeDefaultDryRun(
        gameState: GamePackageDetector.State,
        shizukuState: ShizukuState,
    ) {
        if (configWriteRunning) {
            Toast.makeText(activity, "Safe config preset is already running.", Toast.LENGTH_SHORT).show()
            return
        }

        val precondition = preconditionChecker.checkSafeDefault(activity, gameState, shizukuState)
        val summary = safePresetDryRunSummary(precondition)
        if (!precondition.isReady()) {
            dialogs.showMessage("Safe config dry run", summary)
            logger.add("Safe config preset: blocked - ${precondition.failures.joinToString("; ")}")
            return
        }

        dialogs.showConfirmation(
            title = "Safe config dry run",
            message = summary + "\n\nContinue to apply the Safe / Default config preset?",
            positiveLabel = "Continue Safe Preset",
        ) {
            showFinalSafePresetConfirmation(precondition.plan!!)
        }
    }

    private fun showFinalSafePresetConfirmation(plan: ConfigPresetPlan) {
        dialogs.showConfirmation(
            title = "Final Safe preset confirmation",
            message = "This will write only bundled Safe / Default templates to these allowlisted files:\n\n" +
                "- Engine.ini\n" +
                "- DeviceProfiles.ini\n" +
                "- MountLang_en.txt\n\n" +
                "Balanced, Performance, and Max Graphics remain locked. Continue only if the trusted backup details look correct.",
            positiveLabel = "Apply Safe Preset Now",
        ) {
            applySafeDefaultPreset(plan)
        }
    }

    private fun applySafeDefaultPreset(previousPlan: ConfigPresetPlan) {
        if (configWriteRunning) {
            Toast.makeText(activity, "Safe config preset is already running.", Toast.LENGTH_SHORT).show()
            return
        }

        val gameState = gamePackageDetector.detect(activity)
        val shizukuState = shizukuStateChecker.check(activity)
        val precondition = preconditionChecker.checkSafeDefault(activity, gameState, shizukuState)
        val plan = precondition.plan
        if (plan == null) {
            dialogs.showMessage("Safe config blocked", safePresetDryRunSummary(precondition))
            logger.add("Safe config preset: blocked before write - ${precondition.failures.joinToString("; ")}")
            return
        }
        if (plan.trustedBackup.sessionDirectory != previousPlan.trustedBackup.sessionDirectory) {
            dialogs.showMessage("Safe config blocked", "Trusted backup changed after dry-run. Reopen the Safe config dry-run and confirm again.")
            logger.add("Safe config preset: blocked - trusted backup changed after dry-run")
            return
        }

        configWriteRunning = true
        logger.add("Safe config preset: started")

        Thread {
            try {
                val result = configPresetWriter.writeSafeDefaultPreset(activity, plan, logger)
                logger.add("Safe config preset: success")
                activity.runOnUiThread {
                    onPresetFinished()
                    dialogs.showMessage("Safe config applied", safePresetWriteSummary(result))
                }
            } catch (exception: Exception) {
                logger.add("Safe config preset failed: ${exception.message}")
                activity.runOnUiThread {
                    dialogs.showMessage("Safe config failed", exception.message.orEmpty())
                }
            } finally {
                configWriteRunning = false
            }
        }.start()
    }

    private fun safePresetDryRunSummary(precondition: ConfigPresetPrecondition): String = buildString {
        val plan = precondition.plan
        append("Config preset:\n")
            .append(SafeConfigTemplates.SAFE_DEFAULT_NAME)
            .append("\n\nFiles to write:\n")

        val files = plan?.templateFiles ?: SafeConfigTemplates.safeDefaultFiles()
        for (file in files) {
            append("- ")
                .append(file.displayName)
                .append(" (")
                .append(file.sizeBytes)
                .append(" bytes, SHA-256 ")
                .append(file.sha256.take(12))
                .append("...)\n  target: ")
                .append(file.relativePath)
                .append('\n')
        }

        append("\nPreset rules:\n")
            .append("- Translation-safe config only\n")
            .append("- No Balanced, Performance, or Max Graphics CVars\n")
            .append("- Target files are re-read and SHA-256 verified after write\n")

        if (plan != null) {
            append("\nTrusted backup:\n")
                .append(plan.trustedBackup.sessionDirectory.absolutePath)
                .append("\nCreated at: ")
                .append(plan.trustedBackup.createdAt)
                .append("\nVerified config files: ")
                .append(plan.trustedBackup.verifiedFiles)
                .append("/")
                .append(PatchDryRunPlanner.backupRelativePaths().size)
        } else {
            append("\nBlocked:\n")
            for (failure in precondition.failures) {
                append("- ").append(failure).append('\n')
            }
        }
    }

    private fun safePresetWriteSummary(result: ShizukuConfigPresetWriter.ConfigPresetWriteResult): String = buildString {
        append("Applied config preset:\n")
            .append(result.presetName)
            .append("\n\nWritten files:\n")
        for (file in result.writtenFiles) {
            append("- ")
                .append(file.displayName)
                .append(" (")
                .append(file.sizeBytes)
                .append(" bytes, SHA-256 ")
                .append(file.sha256.take(12))
                .append("...)\n")
        }
        append("\nAll target files were re-read from the game folder and verified.")
        append("\n\nBalanced, Performance, and Max Graphics remain locked.")
    }
}
