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

        return if (engineMatches && deviceMatches && mountMatches) {
            ConfigInstallState.SAFE_DEFAULT
        } else {
            ConfigInstallState.CUSTOM
        }
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
