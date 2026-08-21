package com.acceleratorer.wuwavn

import java.util.Locale

data class WuWa36Target(
    val resourceVersion: String,
    val langVersion: String,
) {
    val pakRelativePath: String
        get() = WuWa36Layout.resourceLangPath(resourceVersion, langVersion, "WuWaVH_99_P.pak")

    val sigRelativePath: String
        get() = WuWa36Layout.resourceLangPath(resourceVersion, langVersion, "WuWaVH_99_P.sig")

    val mountLangRelativePath: String
        get() = WuWa36Layout.mountLangPath(resourceVersion)
}

object WuWa36Layout {
    private const val RESOURCES_PREFIX = "UE4Game/Client/Client/Saved/Resources/"
    private const val PAK_NAME = "WuWaVH_99_P.pak"
    private const val SIG_NAME = "WuWaVH_99_P.sig"
    private val VERSION_REGEX = Regex("^(\\d+)\\.(\\d+)\\.(\\d+)$")
    private val LANG_MANIFEST_REGEX = Regex("^ManifestLang_en_(\\d+\\.\\d+\\.\\d+)\\.txt$")
    private val SHA1_REGEX = Regex("^[0-9a-fA-F]{40}$")
    private val PATCH_REGISTRY_PATH_REGEX = Regex("^Lang_en/[^/]+/$PAK_NAME$")

    fun resolveResourceVersion(
        gameVersion: String?,
        resourceVersions: Collection<String>,
        versionsWithManifest: Collection<String>,
    ): String? {
        val gameSeries = gameVersion
            ?.trim()
            ?.trimStart('v', 'V')
            ?.split('.')
            ?.takeIf { it.size >= 2 && it[0].toIntOrNull() != null && it[1].toIntOrNull() != null }
            ?.let { "${it[0].toInt()}.${it[1].toInt()}" }
        return resourceVersions
            .asSequence()
            .filter { VERSION_REGEX.matches(it) }
            .filter { versionsWithManifest.contains(it) }
            .filter { gameSeries == null || it.startsWith("$gameSeries.") }
            .maxWithOrNull(compareBy(::parseVersion))
    }

    fun resolveLanguageVersion(manifestNames: Collection<String>): String {
        return manifestNames
            .mapNotNull { LANG_MANIFEST_REGEX.matchEntire(it)?.groupValues?.get(1) }
            .maxWithOrNull(compareBy(::parseVersion))
            ?: "Base"
    }

    fun targets(resourceVersion: String, langVersion: String): WuWa36Target {
        require(VERSION_REGEX.matches(resourceVersion)) { "Invalid WUWA resource version." }
        require(langVersion == "Base" || VERSION_REGEX.matches(langVersion)) {
            "Invalid WUWA language version."
        }
        return WuWa36Target(resourceVersion, langVersion)
    }

    fun resourceLangPath(resourceVersion: String, langVersion: String, fileName: String): String =
        "${RESOURCES_PREFIX}$resourceVersion/Lang_en/$langVersion/$fileName"

    fun mountLangPath(resourceVersion: String): String =
        "${RESOURCES_PREFIX}$resourceVersion/Mount/MountLang_en.txt"

    fun isAllowedDynamicPath(relativePath: String): Boolean {
        val normalized = relativePath.replace('\\', '/')
        if (normalized.contains("..")) return false
        val suffix = normalized.removePrefix(RESOURCES_PREFIX)
        if (suffix == normalized) return false
        val parts = suffix.split('/')
        if (parts.size < 2 || !parts[0].startsWith("3.6.") || !VERSION_REGEX.matches(parts[0])) return false
        if (parts[1] == "ResManifest") return parts.size == 2
        if (parts[1] == "Mount") {
            return parts.size == 3 && parts[2] in setOf("MountLang_en.txt", "MountVH.txt")
        }
        if (parts[1] == "Resource") {
            return parts.size == 4 && parts[2] == "Base" &&
                parts[3].matches(Regex("^[A-Za-z0-9._-]+\\.sig$"))
        }
        if (parts[1] != "Lang_en" || parts.size != 4) return false
        if (parts[2] != "Base" && !VERSION_REGEX.matches(parts[2])) return false
        return parts[3].matches(Regex("^[A-Za-z0-9._-]+\\.(?:pak|sig)$"))
    }

    fun isMountLangPath(relativePath: String): Boolean =
        isAllowedDynamicPath(relativePath) && relativePath.replace('\\', '/').endsWith("/Mount/MountLang_en.txt")

    fun isPatchPakPath(relativePath: String): Boolean =
        isAllowedDynamicPath(relativePath) && relativePath.replace('\\', '/').endsWith("/$PAK_NAME")

    fun isPatchSigPath(relativePath: String): Boolean =
        isAllowedDynamicPath(relativePath) && relativePath.replace('\\', '/').endsWith("/$SIG_NAME")

    fun isOfficialSigSourcePath(relativePath: String): Boolean {
        val normalized = relativePath.replace('\\', '/')
        if (!isAllowedDynamicPath(normalized) || !normalized.endsWith(".sig")) return false
        return !normalized.endsWith("/$SIG_NAME")
    }

    fun isValidMountLang(content: String): Boolean {
        val lines = normalizedLines(content)
        if (lines.firstOrNull() != "::Mount::" || lines.lastOrNull() != "::Del::") return false
        return lines.drop(1).dropLast(1).all { line ->
            val fields = line.split(',')
            fields.size == 6 && fields[0].isNotBlank() && fields[1].toIntOrNull() != null
        }
    }

    fun containsPatchRegistryLine(content: String, langVersion: String): Boolean =
        normalizedLines(content).any { line ->
            val fields = line.split(',')
            fields.size == 6 &&
                fields[0] == "Lang_en/$langVersion/$PAK_NAME" &&
                fields[1] == "99" &&
                SHA1_REGEX.matches(fields[2]) &&
                SHA1_REGEX.matches(fields[3])
        }

    fun containsAnyPatchRegistryEntry(content: String): Boolean =
        normalizedLines(content).any { line ->
            val fields = line.split(',')
            fields.size == 6 && PATCH_REGISTRY_PATH_REGEX.matches(fields[0])
        }

    fun isOriginalMountLang(content: String): Boolean =
        isValidMountLang(content) && !containsAnyPatchRegistryEntry(content)

    fun patchMountLang(
        original: String,
        langVersion: String,
        pakSha1: String,
        sigSha1: String,
    ): String {
        require(langVersion == "Base" || VERSION_REGEX.matches(langVersion)) {
            "Invalid WUWA language version."
        }
        require(SHA1_REGEX.matches(pakSha1)) { "PAK SHA-1 is invalid." }
        require(SHA1_REGEX.matches(sigSha1)) { "SIG SHA-1 is invalid." }
        require(isValidMountLang(original)) { "MountLang_en.txt format is invalid." }

        val lines = normalizedLines(original).toMutableList()
        val delIndex = lines.lastIndexOf("::Del::")
        lines.removeAll { line ->
            val path = line.substringBefore(',')
            path.startsWith("Lang_en/") && path.endsWith("/$PAK_NAME")
        }
        val newLine = "Lang_en/$langVersion/$PAK_NAME,99," +
            pakSha1.uppercase(Locale.ROOT) + "," +
            sigSha1.uppercase(Locale.ROOT) + ",,"
        val newDelIndex = lines.indexOf("::Del::").takeIf { it >= 0 } ?: delIndex
        lines.add(newDelIndex.coerceAtLeast(1), newLine)
        return lines.joinToString("\n") + "\n"
    }

    private fun normalizedLines(content: String): List<String> =
        content.replace("\r\n", "\n").replace('\r', '\n')
            .lineSequence()
            .map(String::trim)
            .filter(String::isNotEmpty)
            .toList()

    private fun parseVersion(value: String): Version {
        val match = VERSION_REGEX.matchEntire(value)
            ?: throw IllegalArgumentException("Invalid semantic version: $value")
        return Version(
            major = match.groupValues[1].toInt(),
            minor = match.groupValues[2].toInt(),
            patch = match.groupValues[3].toInt(),
        )
    }

    private data class Version(val major: Int, val minor: Int, val patch: Int) : Comparable<Version> {
        override fun compareTo(other: Version): Int = compareValuesBy(this, other, Version::major, Version::minor, Version::patch)
    }
}
