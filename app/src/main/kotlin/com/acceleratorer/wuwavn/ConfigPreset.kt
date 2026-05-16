package com.acceleratorer.wuwavn

enum class ConfigPresetId {
    SAFE_DEFAULT,
    BALANCED,
    PERFORMANCE,
    MAX_GRAPHICS,
}

enum class PresetAvailability {
    WRITE_ENABLED,
    DRY_RUN_ONLY,
    LOCKED,
}

enum class PresetRiskLevel(val label: String) {
    SAFE("safe"),
    MEDIUM("medium"),
    HIGH("high"),
    EXTREME("extreme"),
}

data class ConfigPreset(
    val id: ConfigPresetId,
    val displayName: String,
    val riskLevel: PresetRiskLevel,
    val availability: PresetAvailability,
    val description: String,
    val files: List<ConfigTemplateFile>,
    val warnings: List<String>,
) {
    val name: String
        get() = displayName
}

data class ConfigTemplateFile(
    val displayName: String,
    val relativePath: String,
    val content: ByteArray,
    val sizeBytes: Long,
    val sha256: String,
)

object ConfigPresetAvailabilityPolicy {
    fun availability(id: ConfigPresetId): PresetAvailability = when (id) {
        ConfigPresetId.SAFE_DEFAULT -> PresetAvailability.WRITE_ENABLED
        ConfigPresetId.BALANCED -> PresetAvailability.WRITE_ENABLED
        ConfigPresetId.PERFORMANCE -> PresetAvailability.LOCKED
        ConfigPresetId.MAX_GRAPHICS -> PresetAvailability.LOCKED
    }
}
