package com.acceleratorer.wuwavn

data class ConfigChange(
    val fileName: String,
    val key: String,
    val oldValue: String?,
    val newValue: String,
    val note: String,
)

data class ConfigPresetDryRun(
    val preset: ConfigPreset,
    val filesToWrite: List<ConfigTemplateFile>,
    val changes: List<ConfigChange>,
    val writeEnabled: Boolean,
    val blockedReason: String,
) {
    fun describe(): String = buildString {
        append(preset.displayName)
            .append(" preset\n")
            .append("Risk level: ")
            .append(preset.riskLevel.label)
            .append("\nAvailability: ")
            .append(preset.availability)
            .append("\n\nWrite status:\n")
            .append(if (writeEnabled) "WRITE ENABLED after dry-run and final confirmation." else "LOCKED. No game files will be modified.")
            .append("\n\nFiles that would be changed:\n")

        for (file in filesToWrite) {
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

        append("\nPlanned config changes:\n")
        if (changes.isEmpty()) {
            append("- No config keys or CVars changed.\n")
        } else {
            var currentFile: String? = null
            for (change in changes) {
                if (change.fileName != currentFile) {
                    currentFile = change.fileName
                    append(change.fileName).append('\n')
                }
                append("- ")
                    .append(change.key)
                    .append(": ")
                    .append(change.oldValue ?: "default")
                    .append(" -> ")
                    .append(change.newValue)
                    .append("\n  ")
                    .append(change.note)
                    .append('\n')
            }
        }

        if (preset.warnings.isNotEmpty()) {
            append("\nWarnings:\n")
            for (warning in preset.warnings) {
                append("- ").append(warning).append('\n')
            }
        }

        append("\nReason:\n")
            .append(blockedReason)
    }
}

class ConfigPresetDiffPlanner {
    fun planBalancedPreview(): ConfigPresetDryRun {
        val preset = ConfigPresetRepository().balanced()
        val writeEnabled = preset.availability == PresetAvailability.WRITE_ENABLED
        return ConfigPresetDryRun(
            preset = preset,
            filesToWrite = preset.files,
            changes = listOf(
                ConfigChange(
                    fileName = "DeviceProfiles.ini",
                    key = "sg.ViewDistanceQuality",
                    oldValue = "default",
                    newValue = "1",
                    note = "Small view distance bump for balanced visuals.",
                ),
                ConfigChange(
                    fileName = "DeviceProfiles.ini",
                    key = "sg.ShadowQuality",
                    oldValue = "default",
                    newValue = "1",
                    note = "Conservative shadow quality.",
                ),
                ConfigChange(
                    fileName = "DeviceProfiles.ini",
                    key = "sg.TextureQuality",
                    oldValue = "default",
                    newValue = "2",
                    note = "Moderate texture quality.",
                ),
                ConfigChange(
                    fileName = "DeviceProfiles.ini",
                    key = "sg.EffectsQuality",
                    oldValue = "default",
                    newValue = "1",
                    note = "Conservative effects quality.",
                ),
                ConfigChange(
                    fileName = "MountLang_en.txt",
                    key = "Vietnamese PAK mount",
                    oldValue = "current",
                    newValue = "../../../Client/Content/Paks/WuWaVH_99_P.pak",
                    note = "Keep Vietnamese PAK mount path.",
                ),
            ),
            writeEnabled = writeEnabled,
            blockedReason = if (writeEnabled) {
                "Balanced write is enabled in v3.3.5 only after this dry-run, trusted backup check, and final confirmation."
            } else {
                "Balanced write is locked. This is preview only."
            },
        )
    }
}
