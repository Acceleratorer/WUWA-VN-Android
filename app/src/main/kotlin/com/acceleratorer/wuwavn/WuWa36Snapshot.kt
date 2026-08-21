package com.acceleratorer.wuwavn

import org.json.JSONObject

data class WuWa36Snapshot(
    val resourceVersions: List<String>,
    val versionsWithManifest: List<String>,
    val resolvedResourceVersion: String,
    val resolvedLanguageVersion: String,
    val mountLangRelativePath: String,
    val mountLangContent: String?,
    val pakRelativePath: String,
    val sigRelativePath: String,
    val pakExists: Boolean,
    val sigExists: Boolean,
    val patchRegistered: Boolean,
    val sigSourceRelativePaths: List<String>,
    val sigSourceRelativePath: String?,
    val sigSourceSha1: String?,
) {
    val isReadyForPatch: Boolean
        get() = mountLangContent != null &&
            WuWa36Layout.isValidMountLang(mountLangContent) &&
            sigSourceRelativePaths.isNotEmpty()

    companion object {
        fun fromJson(json: String): WuWa36Snapshot {
            val root = JSONObject(json)
            fun strings(name: String): List<String> {
                val array = root.optJSONArray(name) ?: return emptyList()
                return (0 until array.length()).mapNotNull { array.optString(it).takeIf(String::isNotBlank) }
            }
            val resourceVersion = root.optString("resolvedResourceVersion")
            val langVersion = root.optString("resolvedLanguageVersion")
            require(resourceVersion.isNotBlank() && langVersion.isNotBlank()) {
                "WUWA 3.6 resource versions are not resolved."
            }
            return WuWa36Snapshot(
                resourceVersions = strings("resourceVersions"),
                versionsWithManifest = strings("versionsWithManifest"),
                resolvedResourceVersion = resourceVersion,
                resolvedLanguageVersion = langVersion,
                mountLangRelativePath = root.optString("mountLangRelativePath"),
                mountLangContent = root.optString("mountLangContent").takeIf {
                    root.has("mountLangContent") && !root.isNull("mountLangContent") && it.isNotEmpty()
                },
                pakRelativePath = root.optString("pakRelativePath"),
                sigRelativePath = root.optString("sigRelativePath"),
                pakExists = root.optBoolean("pakExists"),
                sigExists = root.optBoolean("sigExists"),
                patchRegistered = root.optBoolean("patchRegistered"),
                sigSourceRelativePaths = strings("sigSourceRelativePaths"),
                sigSourceRelativePath = root.optString("sigSourceRelativePath").takeIf(String::isNotBlank),
                sigSourceSha1 = root.optString("sigSourceSha1").takeIf(String::isNotBlank),
            )
        }
    }
}
