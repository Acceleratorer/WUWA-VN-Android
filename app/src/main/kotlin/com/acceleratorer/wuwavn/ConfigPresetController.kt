package com.acceleratorer.wuwavn

import android.app.Activity
import android.widget.Toast

class ConfigPresetController(
    private val activity: Activity,
    private val logger: DebugLogger,
    private val preconditionChecker: ConfigPresetPreconditionChecker,
    private val balancedPresetDryRunPlanner: BalancedPresetDryRunPlanner,
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
        showPresetDryRun(ConfigPresetId.SAFE_DEFAULT, gameState, shizukuState)
    }

    fun showBalancedDryRun(
        gameState: GamePackageDetector.State,
        shizukuState: ShizukuState,
        installedState: InstalledState?,
    ) {
        if (configWriteRunning) {
            Toast.makeText(activity, "Config preset is already running.", Toast.LENGTH_SHORT).show()
            return
        }

        val dryRun = balancedPresetDryRunPlanner.plan(
            context = activity,
            gameState = gameState,
            shizukuState = shizukuState,
            installedState = installedState,
        )
        dialogs.showMessage("Balanced Preset Preview", dryRun.describe())
        logger.add("Balanced preview: shown")
    }

    private fun showPresetDryRun(
        presetId: ConfigPresetId,
        gameState: GamePackageDetector.State,
        shizukuState: ShizukuState,
    ) {
        if (configWriteRunning) {
            Toast.makeText(activity, "Config preset is already running.", Toast.LENGTH_SHORT).show()
            return
        }

        val precondition = preconditionChecker.check(presetId, activity, gameState, shizukuState)
        val presetName = precondition.preset?.name ?: "Config preset"
        val summary = presetDryRunSummary(precondition)
        if (!precondition.isReady()) {
            dialogs.showMessage("$presetName dry run", summary)
            logger.add("$presetName preset: blocked - ${precondition.failures.joinToString("; ")}")
            return
        }

        dialogs.showConfirmation(
            title = "$presetName dry run",
            message = summary + "\n\nContinue to apply the $presetName config preset?",
            positiveLabel = "Continue Preset",
        ) {
            showFinalPresetConfirmation(precondition.plan!!)
        }
    }

    private fun showFinalPresetConfirmation(plan: ConfigPresetPlan) {
        dialogs.showConfirmation(
            title = "Final ${plan.preset.name} confirmation",
            message = "This will write only bundled ${plan.preset.name} templates to these allowlisted files:\n\n" +
                "- Engine.ini\n" +
                "- DeviceProfiles.ini\n" +
                "- MountLang_en.txt\n\n" +
                warningBlock(plan.preset) +
                "Performance and Max Graphics remain locked. Continue only if the trusted backup details look correct.",
            positiveLabel = "Apply ${plan.preset.name} Now",
        ) {
            applyPreset(plan)
        }
    }

    private fun applyPreset(previousPlan: ConfigPresetPlan) {
        if (configWriteRunning) {
            Toast.makeText(activity, "Config preset is already running.", Toast.LENGTH_SHORT).show()
            return
        }

        val gameState = gamePackageDetector.detect(activity)
        val shizukuState = shizukuStateChecker.check(activity)
        val precondition = preconditionChecker.check(previousPlan.preset.id, activity, gameState, shizukuState)
        val plan = precondition.plan
        if (plan == null) {
            dialogs.showMessage("${previousPlan.preset.name} blocked", presetDryRunSummary(precondition))
            logger.add("${previousPlan.preset.name} preset: blocked before write - ${precondition.failures.joinToString("; ")}")
            return
        }
        if (plan.trustedBackup.sessionDirectory != previousPlan.trustedBackup.sessionDirectory) {
            dialogs.showMessage("${plan.preset.name} blocked", "Trusted backup changed after dry-run. Reopen the config dry-run and confirm again.")
            logger.add("${plan.preset.name} preset: blocked - trusted backup changed after dry-run")
            return
        }

        configWriteRunning = true
        logger.add("${plan.preset.name} preset: started")

        Thread {
            try {
                val result = configPresetWriter.writeConfigPreset(activity, plan, logger)
                logger.add("${plan.preset.name} preset: success")
                activity.runOnUiThread {
                    onPresetFinished()
                    dialogs.showMessage("${plan.preset.name} applied", presetWriteSummary(result, plan.preset))
                }
            } catch (exception: Exception) {
                logger.add("${plan.preset.name} preset failed: ${exception.message}")
                activity.runOnUiThread {
                    dialogs.showMessage("${plan.preset.name} failed", exception.message.orEmpty())
                }
            } finally {
                configWriteRunning = false
            }
        }.start()
    }

    private fun presetDryRunSummary(precondition: ConfigPresetPrecondition): String = buildString {
        val preset = precondition.preset
        val plan = precondition.plan
        append("Config preset:\n")
            .append(preset?.name ?: "unknown")
            .append("\nRisk level: ")
            .append(preset?.riskLevel?.label ?: "unknown")
            .append("\n\nFiles to write:\n")

        val files = plan?.templateFiles ?: preset?.files.orEmpty()
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

        if (preset != null && preset.warnings.isNotEmpty()) {
            append("\nWarnings:\n")
            for (warning in preset.warnings) {
                append("- ").append(warning).append('\n')
            }
        }

        append("\nConfig/CVar changes:\n")
            .append("- No graphics CVars changed by this write path.\n")

        append("\nPreset rules:\n")
            .append("- No arbitrary config paths\n")
            .append("- No Performance or Max Graphics CVars\n")
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

    private fun presetWriteSummary(
        result: ShizukuConfigPresetWriter.ConfigPresetWriteResult,
        preset: ConfigPreset,
    ): String = buildString {
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
        append("\n\nRisk level: ").append(preset.riskLevel.label)
        append("\nPerformance and Max Graphics remain locked.")
    }

    private fun warningBlock(preset: ConfigPreset): String =
        if (preset.warnings.isEmpty()) {
            ""
        } else {
            "Warnings:\n${preset.warnings.joinToString("\n") { "- $it" }}\n\n"
        }
}
