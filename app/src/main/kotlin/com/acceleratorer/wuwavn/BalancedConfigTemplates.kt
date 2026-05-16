package com.acceleratorer.wuwavn

object BalancedConfigTemplates {
    fun files(): List<ConfigTemplateFile> = listOf(
        engineIni(),
        deviceProfilesIni(),
        mountLang(),
    )

    private fun engineIni(): ConfigTemplateFile {
        val content = """
            [/Script/Engine.RendererSettings]
            ; Balanced preset for WUWA VN Android.
            ; No extreme graphics override in v3.3.6.
        """.trimIndent() + "\n"

        return templateFile(
            displayName = "Engine.ini",
            relativePath = PatchDryRunPlanner.engineIniRelativePath(),
            content = content,
        )
    }

    private fun deviceProfilesIni(): ConfigTemplateFile {
        val content = """
            [Android DeviceProfile]
            ; Balanced preset.
            ; Conservative visual tuning.
            +CVars=sg.ViewDistanceQuality=1
            +CVars=sg.ShadowQuality=1
            +CVars=sg.TextureQuality=2
            +CVars=sg.EffectsQuality=1
        """.trimIndent() + "\n"

        return templateFile(
            displayName = "DeviceProfiles.ini",
            relativePath = PatchDryRunPlanner.deviceProfilesRelativePath(),
            content = content,
        )
    }

    private fun mountLang(): ConfigTemplateFile {
        val content = "../../../Client/Content/Paks/WuWaVH_99_P.pak\n"
        return templateFile(
            displayName = "MountLang_en.txt",
            relativePath = PatchDryRunPlanner.mountLangRelativePath(),
            content = content,
        )
    }

    private fun templateFile(
        displayName: String,
        relativePath: String,
        content: String,
    ): ConfigTemplateFile {
        if (!PatchDryRunPlanner.isAllowedTarget(relativePath)) {
            throw SecurityException("Blocked unsafe config template path: $relativePath")
        }
        val bytes = content.toByteArray(Charsets.UTF_8)
        return ConfigTemplateFile(
            displayName = displayName,
            relativePath = relativePath,
            content = bytes,
            sizeBytes = bytes.size.toLong(),
            sha256 = Sha256Verifier.sha256(bytes),
        )
    }
}
