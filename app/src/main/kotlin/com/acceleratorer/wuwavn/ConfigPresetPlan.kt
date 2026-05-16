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

data class ConfigPreset(
    val id: String,
    val name: String,
    val riskLevel: RiskLevel,
    val files: List<ConfigTemplateFile>,
    val warning: String,
    val cvarChanges: List<String>,
)

data class ConfigTemplateFile(
    val displayName: String,
    val relativePath: String,
    val content: ByteArray,
    val sizeBytes: Long,
    val sha256: String,
)

enum class RiskLevel(val label: String) {
    LOW("low"),
    MEDIUM("medium"),
    HIGH("high"),
}
