package com.acceleratorer.wuwavn

object ConfigPresets {
    const val SAFE_DEFAULT_ID = "safe_default"
    const val SAFE_DEFAULT_NAME = "Safe / Default"
    const val BALANCED_ID = "balanced"
    const val BALANCED_NAME = "Balanced"

    fun get(id: String): ConfigPreset? = when (id) {
        SAFE_DEFAULT_ID -> safeDefault()
        BALANCED_ID -> balanced()
        else -> null
    }

    fun isUnlocked(id: String): Boolean =
        id == SAFE_DEFAULT_ID

    fun safeDefaultFilesByName(): Map<String, ConfigTemplateFile> =
        safeDefaultFiles().associateBy { it.displayName }

    private fun safeDefault(): ConfigPreset = ConfigPreset(
        id = SAFE_DEFAULT_ID,
        name = SAFE_DEFAULT_NAME,
        riskLevel = RiskLevel.LOW,
        files = safeDefaultFiles(),
        warning = "Safe / Default keeps graphics settings minimal and is recommended for all devices.",
        cvarChanges = emptyList(),
    )

    private fun balanced(): ConfigPreset = ConfigPreset(
        id = BALANCED_ID,
        name = BALANCED_NAME,
        riskLevel = RiskLevel.MEDIUM,
        files = balancedFiles(),
        warning = "Balanced is conservative, but it still changes graphics config. Recommended for Snapdragon 865 / 870 / 778G, Dimensity 8100+, or similar devices. It does not force Vulkan, resolution scale, or FPS unlock.",
        cvarChanges = listOf(
            "r.RayTracing.LoadConfig=0",
            "r.MaxAnisotropy=4",
            "r.SSR.MaxRoughness=0.85",
            "r.LightShaftQuality=1",
            "r.MobileLightShaft=0",
            "KuroCookOptimize_MultiSave=1",
            "KuroCookOptimize_AsyncLoadThread=1",
            "r.Mobile.DeviceEvaluation=3",
            "r.Mobile.HighQualityMaterial=0",
        ),
    )

    private fun safeDefaultFiles(): List<ConfigTemplateFile> {
        val contentByName = mapOf(
            "Engine.ini" to SAFE_ENGINE_INI,
            "DeviceProfiles.ini" to SAFE_DEVICE_PROFILES_INI,
            "MountLang_en.txt" to SAFE_MOUNT_LANG,
        )

        return PatchDryRunPlanner.backupRelativePaths().map { relativePath ->
            val displayName = PatchDryRunPlanner.displayName(relativePath)
            val content = contentByName[displayName]
                ?: throw IllegalStateException("Missing Safe template for $displayName.")
            templateFile(displayName, relativePath, content)
        }
    }

    private fun balancedFiles(): List<ConfigTemplateFile> {
        val contentByName = mapOf(
            "Engine.ini" to BALANCED_ENGINE_INI,
            "DeviceProfiles.ini" to BALANCED_DEVICE_PROFILES_INI,
            "MountLang_en.txt" to SAFE_MOUNT_LANG,
        )

        return PatchDryRunPlanner.backupRelativePaths().map { relativePath ->
            val displayName = PatchDryRunPlanner.displayName(relativePath)
            val content = contentByName[displayName]
                ?: throw IllegalStateException("Missing Balanced template for $displayName.")
            templateFile(displayName, relativePath, content)
        }
    }

    private fun templateFile(
        displayName: String,
        relativePath: String,
        content: String,
    ): ConfigTemplateFile {
        if (!PatchDryRunPlanner.isAllowedTarget(relativePath)) {
            throw SecurityException("Blocked unsafe config template path: $relativePath")
        }
        val bytes = ensureTrailingNewline(content.trimIndent()).toByteArray(Charsets.UTF_8)
        return ConfigTemplateFile(
            displayName = displayName,
            relativePath = relativePath,
            content = bytes,
            sizeBytes = bytes.size.toLong(),
            sha256 = Sha256Verifier.sha256(bytes),
        )
    }

    private fun ensureTrailingNewline(value: String): String =
        if (value.endsWith('\n')) value else "$value\n"

    private const val SAFE_ENGINE_INI = """
[Core.System]
Paths=../../../Engine/Content
Paths=%GAMEDIR%Content
Paths=../../../Engine/Plugins/ThirdParty/ImpostorBaker/Content
Paths=../../../Engine/Plugins/json2struct/Content
Paths=../../../Engine/Plugins/Experimental/FieldSystemPlugin/Content
Paths=../../../Client/Plugins/LGUI/LGUI/Content
Paths=../../../Engine/Plugins/PrefabSystem/Content
Paths=../../../Engine/Plugins/FX/Niagara/Content
Paths=../../../Client/Plugins/Kuro/KuroGameplay/Content
Paths=../../../Client/Plugins/Puerts/Puerts/Content
Paths=../../../Client/Plugins/Wwise/Content
Paths=../../../Engine/Plugins/Editor/GeometryMode/Content
Paths=../../../Engine/Plugins/MovieScene/SequencerScripting/Content
Paths=../../../Engine/Plugins/Experimental/PythonScriptPlugin/Content
Paths=../../../Client/Plugins/CrashSight/Content
Paths=../../../Engine/Plugins/ThirdParty/QuickEditor/Content
Paths=../../../Client/Plugins/Sharphereal/Content
Paths=../../../Engine/Plugins/Experimental/GeometryProcessing/Content
Paths=../../../Client/Plugins/Kuro/TASdkPlugin/Content
Paths=../../../Client/Plugins/Kuro/KRDataAnalyticsPlugin/Content
Paths=../../../Engine/Plugins/rdLODtools/Content
Paths=../../../Client/Plugins/AudioMaterialPlugin/Content
Paths=../../../Engine/Plugins/Runtime/Nvidia/DLSS/Content
Paths=../../../Engine/Plugins/Runtime/HoudiniEngine/Content
Paths=../../../Client/Plugins/Kuro/KuroHotPatch/Content
Paths=../../../Client/Plugins/Kuro/KuroImposter/Content
Paths=../../../Client/Plugins/Kuro/KuroAutomationTool/Content
Paths=../../../Engine/Plugins/FX/HoudiniNiagara/Content
Paths=../../../Client/Plugins/LogicDriverLite/Content
Paths=../../../Engine/Plugins/Runtime/AudioSynesthesia/Content
Paths=../../../Engine/Plugins/Experimental/ControlRig/Content
Paths=../../../Engine/Plugins/Media/MediaCompositing/Content
Paths=../../../Engine/Plugins/Runtime/Synthesis/Content
Paths=../../../Engine/Plugins/SequenceDialogue/Content
Paths=../../../Client/Plugins/Puerts/ReactUMG/Content
Paths=../../../Client/Plugins/genesis-ue-plugin/RenderExporter/Content
Paths=../../../Engine/Plugins/KuroiOSDelegate/Content
Paths=../../../Client/Plugins/Kuro/KuroGameplayUI/Content
Paths=../../../Engine/Plugins/Runtime/Nvidia/OpacityMicroMap/Content
Paths=../../../Engine/Plugins/Experimental/ColorCorrectRegions/Content
Paths=../../../Engine/Plugins/Compositing/OpenCVLensDistortion/Content
Paths=../../../Engine/Plugins/Experimental/FastGeoStreaming/Content
Paths=../../../Client/Plugins/Kuro/KuroWorldPartition/Content
Paths=../../../Client/Plugins/BlockoutToolsPlugin/Content
Paths=../../../Client/Plugins/ComfyTextures/Content
Paths=../../../Client/Plugins/KuroComputeShader/Content
Paths=../../../Client/Plugins/KuroTDM/Content
Paths=../../../Client/Plugins/Kuro/ImposterBaker/Content
Paths=../../../Client/Plugins/Kuro/KuroDynamicMeshBatch/Content
Paths=../../../Client/Plugins/Kuro/KuroGachaTools/Content
Paths=../../../Client/Plugins/Kuro/KuroPerfCat/Content
Paths=../../../Client/Plugins/Kuro/KuroPSOTools/Content
Paths=../../../Client/Plugins/Kuro/KuroPushSdk/Content
Paths=../../../Client/Plugins/MagtModule/Content
Paths=../../../Client/Plugins/SdkParamExtend/Content
Paths=../../../Client/Plugins/SpinePlugin/Content
Paths=../../../Client/Plugins/TFlow/Content
Paths=../../../Client/Plugins/TpSafe/Content
Paths=../../../Engine/Plugins/AFME/Content
Paths=../../../Engine/Plugins/Animation/ACLPlugin/Content
Paths=../../../Engine/Plugins/AssetChecker/Content
Paths=../../../Engine/Plugins/AssetMemoryAnalyzer/Content
Paths=../../../Engine/Plugins/DawnSDK/DawnSDK/Content
Paths=../../../Engine/Plugins/Editor/SpeedTreeImporter/Content
Paths=../../../Engine/Plugins/Experimental/ChaosClothEditor/Content
Paths=../../../Engine/Plugins/Experimental/ChaosNiagara/Content
Paths=../../../Engine/Plugins/Experimental/ChaosSolverPlugin/Content
Paths=../../../Engine/Plugins/GSR/Content
Paths=../../../Engine/Plugins/KuroFI/Content
Paths=../../../Engine/Plugins/MagicDawn/Content
Paths=../../../Engine/Plugins/MFRCModule/Content
Paths=../../../Engine/Plugins/Runtime/Intel/XeSS/Content
Paths=../../../Engine/Plugins/Runtime/Nvidia/NRD/Content
"""

    private const val SAFE_DEVICE_PROFILES_INI = """
[Android DeviceProfile]
; Safe / Default keeps the game's Android graphics defaults.
; No FPS, resolution, Vulkan, or quality CVars are changed by this preset.
"""

    private const val SAFE_MOUNT_LANG = """
../../../Client/Content/Paks/WuWaVH_99_P.pak
"""

    private const val BALANCED_ENGINE_INI = """
[Core.System]
Paths=../../../Engine/Content
Paths=%GAMEDIR%Content
Paths=../../../Engine/Plugins/ThirdParty/ImpostorBaker/Content
Paths=../../../Engine/Plugins/json2struct/Content
Paths=../../../Engine/Plugins/Experimental/FieldSystemPlugin/Content
Paths=../../../Client/Plugins/LGUI/LGUI/Content
Paths=../../../Engine/Plugins/PrefabSystem/Content
Paths=../../../Engine/Plugins/FX/Niagara/Content
Paths=../../../Client/Plugins/Kuro/KuroGameplay/Content
Paths=../../../Client/Plugins/Puerts/Puerts/Content
Paths=../../../Client/Plugins/Wwise/Content
Paths=../../../Engine/Plugins/Editor/GeometryMode/Content
Paths=../../../Engine/Plugins/MovieScene/SequencerScripting/Content
Paths=../../../Engine/Plugins/Experimental/PythonScriptPlugin/Content
Paths=../../../Client/Plugins/CrashSight/Content
Paths=../../../Engine/Plugins/ThirdParty/QuickEditor/Content
Paths=../../../Client/Plugins/Sharphereal/Content
Paths=../../../Engine/Plugins/Experimental/GeometryProcessing/Content
Paths=../../../Client/Plugins/Kuro/TASdkPlugin/Content
Paths=../../../Client/Plugins/Kuro/KRDataAnalyticsPlugin/Content
Paths=../../../Engine/Plugins/rdLODtools/Content
Paths=../../../Client/Plugins/AudioMaterialPlugin/Content
Paths=../../../Engine/Plugins/Runtime/Nvidia/DLSS/Content
Paths=../../../Engine/Plugins/Runtime/HoudiniEngine/Content
Paths=../../../Client/Plugins/Kuro/KuroHotPatch/Content
Paths=../../../Client/Plugins/Kuro/KuroImposter/Content
Paths=../../../Client/Plugins/Kuro/KuroAutomationTool/Content
Paths=../../../Engine/Plugins/FX/HoudiniNiagara/Content
Paths=../../../Client/Plugins/LogicDriverLite/Content
Paths=../../../Engine/Plugins/Runtime/AudioSynesthesia/Content
Paths=../../../Engine/Plugins/Experimental/ControlRig/Content
Paths=../../../Engine/Plugins/Media/MediaCompositing/Content
Paths=../../../Engine/Plugins/Runtime/Synthesis/Content
Paths=../../../Engine/Plugins/SequenceDialogue/Content
Paths=../../../Client/Plugins/Puerts/ReactUMG/Content
Paths=../../../Client/Plugins/genesis-ue-plugin/RenderExporter/Content
Paths=../../../Engine/Plugins/KuroiOSDelegate/Content
Paths=../../../Client/Plugins/Kuro/KuroGameplayUI/Content
Paths=../../../Engine/Plugins/Runtime/Nvidia/OpacityMicroMap/Content
Paths=../../../Engine/Plugins/Experimental/ColorCorrectRegions/Content
Paths=../../../Engine/Plugins/Compositing/OpenCVLensDistortion/Content
Paths=../../../Engine/Plugins/Experimental/FastGeoStreaming/Content
Paths=../../../Client/Plugins/Kuro/KuroWorldPartition/Content
Paths=../../../Client/Plugins/BlockoutToolsPlugin/Content
Paths=../../../Client/Plugins/ComfyTextures/Content
Paths=../../../Client/Plugins/KuroComputeShader/Content
Paths=../../../Client/Plugins/KuroTDM/Content
Paths=../../../Client/Plugins/Kuro/ImposterBaker/Content
Paths=../../../Client/Plugins/Kuro/KuroDynamicMeshBatch/Content
Paths=../../../Client/Plugins/Kuro/KuroGachaTools/Content
Paths=../../../Client/Plugins/Kuro/KuroPerfCat/Content
Paths=../../../Client/Plugins/Kuro/KuroPSOTools/Content
Paths=../../../Client/Plugins/Kuro/KuroPushSdk/Content
Paths=../../../Client/Plugins/MagtModule/Content
Paths=../../../Client/Plugins/SdkParamExtend/Content
Paths=../../../Client/Plugins/SpinePlugin/Content
Paths=../../../Client/Plugins/TFlow/Content
Paths=../../../Client/Plugins/TpSafe/Content
Paths=../../../Engine/Plugins/AFME/Content
Paths=../../../Engine/Plugins/Animation/ACLPlugin/Content
Paths=../../../Engine/Plugins/AssetChecker/Content
Paths=../../../Engine/Plugins/AssetMemoryAnalyzer/Content
Paths=../../../Engine/Plugins/DawnSDK/DawnSDK/Content
Paths=../../../Engine/Plugins/Editor/SpeedTreeImporter/Content
Paths=../../../Engine/Plugins/Experimental/ChaosClothEditor/Content
Paths=../../../Engine/Plugins/Experimental/ChaosNiagara/Content
Paths=../../../Engine/Plugins/Experimental/ChaosSolverPlugin/Content
Paths=../../../Engine/Plugins/GSR/Content
Paths=../../../Engine/Plugins/KuroFI/Content
Paths=../../../Engine/Plugins/MagicDawn/Content
Paths=../../../Engine/Plugins/MFRCModule/Content
Paths=../../../Engine/Plugins/Runtime/Intel/XeSS/Content
Paths=../../../Engine/Plugins/Runtime/Nvidia/NRD/Content

[/Script/Engine.RendererSettings]
; Conservative Balanced preset. No forced Vulkan, resolution scale, or FPS unlock.
r.RayTracing.LoadConfig=0
r.MaxAnisotropy=4
r.SSR.MaxRoughness=0.85
r.LightShaftQuality=1
r.MobileLightShaft=0

[KuroCookOptimize]
KuroCookOptimize_MultiSave=1
KuroCookOptimize_AsyncLoadThread=1
"""

    private const val BALANCED_DEVICE_PROFILES_INI = """
[Android DeviceProfile]
; Conservative Balanced preset. No high-end profile spoofing.
CVars=r.Mobile.DeviceEvaluation=3
CVars=r.Mobile.HighQualityMaterial=0
"""
}
