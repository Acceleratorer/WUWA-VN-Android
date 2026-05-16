package com.acceleratorer.wuwavn

data class ConfigPresetPrecondition(
    val preset: ConfigPreset?,
    val plan: ConfigPresetPlan?,
    val failures: List<String>,
) {
    fun isReady(): Boolean = plan != null && failures.isEmpty()
}

data class ConfigPresetPlan(
    val preset: ConfigPreset,
    val templateFiles: List<ConfigTemplateFile>,
    val trustedBackup: TrustedBackupInfo,
)
