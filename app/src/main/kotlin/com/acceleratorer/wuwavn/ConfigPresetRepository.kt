package com.acceleratorer.wuwavn

class ConfigPresetRepository {
    fun get(id: ConfigPresetId): ConfigPreset = when (id) {
        ConfigPresetId.SAFE_DEFAULT -> safeDefault()
        ConfigPresetId.BALANCED -> balanced()
        ConfigPresetId.PERFORMANCE -> performance()
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
            "Max Graphics remains locked.",
        ),
    )

    fun performance(): ConfigPreset = ConfigPreset(
        id = ConfigPresetId.PERFORMANCE,
        displayName = "Performance",
        riskLevel = PresetRiskLevel.HIGH,
        availability = ConfigPresetAvailabilityPolicy.availability(ConfigPresetId.PERFORMANCE),
        description = "Performance preset with conservative lower graphics load. Write enabled after trusted backup and final confirmation.",
        files = PerformanceConfigTemplates.files(),
        warnings = listOf(
            "Performance preset may reduce visual quality.",
            "Performance preset may change graphics load and device behavior.",
            "Use Safe / Default if you see crash, black screen, stutter, heat, or battery drain.",
            "FPS unlock, Vulkan override, resolution override, and high-risk graphics tokens remain blocked.",
            "Max Graphics remains locked.",
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
