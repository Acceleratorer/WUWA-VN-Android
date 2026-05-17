package com.acceleratorer.wuwavn

class ConfigStateDetector {
    fun detect(
        engineIni: String?,
        deviceProfilesIni: String?,
        mountLang: String?,
    ): ConfigInstallState {
        if (engineIni == null || deviceProfilesIni == null || mountLang == null) {
            return ConfigInstallState.UNKNOWN
        }

        val safeFiles = ConfigPresets.safeDefaultFilesByName()
        val engineMatches = matchesTemplate(engineIni, safeFiles["Engine.ini"])
        val deviceMatches = matchesTemplate(deviceProfilesIni, safeFiles["DeviceProfiles.ini"])
        val mountMatches = matchesTemplate(mountLang, safeFiles["MountLang_en.txt"])

        if (engineMatches && deviceMatches && mountMatches) {
            return ConfigInstallState.SAFE_DEFAULT
        }

        val balancedFiles = BalancedConfigTemplates.files().associateBy { it.displayName }
        val balancedEngineMatches = matchesTemplate(engineIni, balancedFiles["Engine.ini"])
        val balancedDeviceMatches = matchesTemplate(deviceProfilesIni, balancedFiles["DeviceProfiles.ini"])
        val balancedMountMatches = matchesTemplate(mountLang, balancedFiles["MountLang_en.txt"])
        return if (balancedEngineMatches && balancedDeviceMatches && balancedMountMatches) {
            ConfigInstallState.BALANCED
        } else if (matchesPerformance(engineIni, deviceProfilesIni, mountLang)) {
            ConfigInstallState.PERFORMANCE
        } else {
            ConfigInstallState.CUSTOM
        }
    }

    private fun matchesPerformance(
        engineIni: String,
        deviceProfilesIni: String,
        mountLang: String,
    ): Boolean {
        val performanceFiles = PerformanceConfigTemplates.files().associateBy { it.displayName }
        return matchesTemplate(engineIni, performanceFiles["Engine.ini"]) &&
            matchesTemplate(deviceProfilesIni, performanceFiles["DeviceProfiles.ini"]) &&
            matchesTemplate(mountLang, performanceFiles["MountLang_en.txt"])
    }

    private fun matchesTemplate(content: String, template: ConfigTemplateFile?): Boolean {
        if (template == null) {
            return false
        }
        return Sha256Verifier.sha256(normalize(content).toByteArray(Charsets.UTF_8))
            .equals(template.sha256, ignoreCase = true)
    }

    private fun normalize(content: String): String {
        val normalized = content.replace("\r\n", "\n")
        return if (normalized.endsWith('\n')) normalized else "$normalized\n"
    }
}
