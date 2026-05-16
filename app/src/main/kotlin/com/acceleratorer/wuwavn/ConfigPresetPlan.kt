package com.acceleratorer.wuwavn

data class ConfigPresetPrecondition(
    val plan: ConfigPresetPlan?,
    val failures: List<String>,
) {
    fun isReady(): Boolean = plan != null && failures.isEmpty()
}

data class ConfigPresetPlan(
    val presetId: String,
    val presetName: String,
    val templateFiles: List<ConfigTemplateFile>,
    val trustedBackup: TrustedBackupInfo,
)

data class ConfigTemplateFile(
    val displayName: String,
    val relativePath: String,
    val content: ByteArray,
    val sizeBytes: Long,
    val sha256: String,
)
