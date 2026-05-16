package com.acceleratorer.wuwavn

class ConfigPresetRepository {
    fun get(id: ConfigPresetId): ConfigPreset = when (id) {
        ConfigPresetId.SAFE_DEFAULT -> safeDefault()
        ConfigPresetId.BALANCED -> balanced()
        ConfigPresetId.PERFORMANCE -> locked(
            id = ConfigPresetId.PERFORMANCE,
            displayName = "Performance",
            riskLevel = PresetRiskLevel.HIGH,
            description = "Performance preset is locked until a later dry-run release.",
        )
        ConfigPresetId.MAX_GRAPHICS -> locked(
            id = ConfigPresetId.MAX_GRAPHICS,
            displayName = "Max Graphics",
            riskLevel = PresetRiskLevel.EXTREME,
            description = "Max Graphics preset is locked until device testing is complete.",
        )
    }

    fun safeDefault(): ConfigPreset = ConfigPreset(
        id = ConfigPresetId.SAFE_DEFAULT,
        displayName = "Safe / Default",
        riskLevel = PresetRiskLevel.SAFE,
        availability = ConfigPresetAvailabilityPolicy.availability(ConfigPresetId.SAFE_DEFAULT),
        description = "Safe default config preset with minimal changes.",
        files = ConfigPresets.safeDefaultFiles(),
        warnings = emptyList(),
    )

    fun balanced(): ConfigPreset = ConfigPreset(
        id = ConfigPresetId.BALANCED,
        displayName = "Balanced",
        riskLevel = PresetRiskLevel.MEDIUM,
        availability = ConfigPresetAvailabilityPolicy.availability(ConfigPresetId.BALANCED),
        description = "Balanced visual preset with conservative config writes.",
        files = BalancedConfigTemplates.files(),
        warnings = listOf(
            "Balanced preset may change visual quality and device performance.",
            "Use Safe / Default if you experience heat, lag, stutter, battery drain, or crashes.",
            "Performance and Max Graphics remain locked.",
        ),
    )

    private fun locked(
        id: ConfigPresetId,
        displayName: String,
        riskLevel: PresetRiskLevel,
        description: String,
    ): ConfigPreset = ConfigPreset(
        id = id,
        displayName = displayName,
        riskLevel = riskLevel,
        availability = ConfigPresetAvailabilityPolicy.availability(id),
        description = description,
        files = emptyList(),
        warnings = listOf("$displayName preset is locked."),
    )
}
