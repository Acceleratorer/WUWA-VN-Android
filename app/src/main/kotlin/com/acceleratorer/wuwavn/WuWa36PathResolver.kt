package com.acceleratorer.wuwavn

import android.os.Environment
import java.io.File
import java.security.MessageDigest
import org.json.JSONArray
import org.json.JSONObject

class WuWa36PathResolver {
    fun snapshot(preferredSeries: String?): WuWa36Snapshot? {
        val resourcesRoot = File(
            Environment.getExternalStorageDirectory(),
            "Android/data/${AppConstants.GLOBAL_GAME_PACKAGE}/files/UE4Game/Client/Client/Saved/Resources",
        )
        val resourceVersions = resourcesRoot.listFiles()
            ?.asSequence()
            ?.filter { it.isDirectory && it.name.matches(VERSION_REGEX) }
            ?.map { it.name }
            ?.sortedWith { left, right -> compareVersion(left, right) }
            ?.toList()
            .orEmpty()
        val versionsWithManifest = resourceVersions.filter { File(resourcesRoot, "$it/ResManifest").isDirectory }
        val resolvedResourceVersion = WuWa36Layout.resolveResourceVersion(
            gameVersion = preferredSeries,
            resourceVersions = resourceVersions,
            versionsWithManifest = versionsWithManifest,
        ) ?: return null
        val resourceRoot = File(resourcesRoot, resolvedResourceVersion)
        val langVersion = WuWa36Layout.resolveLanguageVersion(
            File(resourceRoot, "ResManifest").list()?.toList().orEmpty(),
        )
        val target = WuWa36Layout.targets(resolvedResourceVersion, langVersion)
        val gameFilesRoot = File(resourcesRoot, "../../../../..").canonicalFile
        val mountLang = File(gameFilesRoot, target.mountLangRelativePath)
        val pak = File(gameFilesRoot, target.pakRelativePath)
        val sig = File(gameFilesRoot, target.sigRelativePath)
        val sourcePaths = sigSources(resourceRoot, gameFilesRoot, langVersion)
        val sourcePath = sourcePaths.firstOrNull()
        val sourceSha1 = sourcePath?.let { sha1Hex(File(gameFilesRoot, it)) }
        val mountContent = mountLang.takeIf { it.isFile && it.length() <= MAX_MOUNT_BYTES }?.readText(Charsets.UTF_8)
        val patchRegistered = mountContent?.let {
            WuWa36Layout.isValidMountLang(it) &&
                WuWa36Layout.containsPatchRegistryLine(it, langVersion)
        } == true
        return WuWa36Snapshot(
            resourceVersions = resourceVersions,
            versionsWithManifest = versionsWithManifest,
            resolvedResourceVersion = resolvedResourceVersion,
            resolvedLanguageVersion = langVersion,
            mountLangRelativePath = target.mountLangRelativePath,
            mountLangContent = mountContent,
            pakRelativePath = target.pakRelativePath,
            sigRelativePath = target.sigRelativePath,
            pakExists = pak.isFile,
            sigExists = sig.isFile,
            patchRegistered = patchRegistered,
            sigSourceRelativePaths = sourcePaths,
            sigSourceRelativePath = sourcePath,
            sigSourceSha1 = sourceSha1,
        )
    }

    fun snapshotJson(preferredSeries: String?): String {
        val snapshot = snapshot(preferredSeries)
            ?: return JSONObject().put("ready", false).toString()
        return JSONObject()
            .put("ready", true)
            .put("resourceVersions", JSONArray(snapshot.resourceVersions))
            .put("versionsWithManifest", JSONArray(snapshot.versionsWithManifest))
            .put("resolvedResourceVersion", snapshot.resolvedResourceVersion)
            .put("resolvedLanguageVersion", snapshot.resolvedLanguageVersion)
            .put("mountLangRelativePath", snapshot.mountLangRelativePath)
            .put("mountLangContent", snapshot.mountLangContent ?: JSONObject.NULL)
            .put("pakRelativePath", snapshot.pakRelativePath)
            .put("sigRelativePath", snapshot.sigRelativePath)
            .put("pakExists", snapshot.pakExists)
            .put("sigExists", snapshot.sigExists)
            .put("patchRegistered", snapshot.patchRegistered)
            .put("sigSourceRelativePaths", JSONArray(snapshot.sigSourceRelativePaths))
            .put("sigSourceRelativePath", snapshot.sigSourceRelativePath ?: JSONObject.NULL)
            .put("sigSourceSha1", snapshot.sigSourceSha1 ?: JSONObject.NULL)
            .toString()
    }

    private fun sigSources(resourceRoot: File, externalRoot: File, preferredLangVersion: String): List<String> {
        val candidates = mutableListOf<File>()
        resourceRoot.resolve("Lang_en").listFiles()?.forEach { langDir ->
            if (langDir.isDirectory) {
                candidates += langDir.listFiles().orEmpty().filter { it.isFile && it.extension.equals("sig", true) }
            }
        }
        resourceRoot.resolve("Resource/Base").listFiles()?.forEach { file ->
            if (file.isFile && file.extension.equals("sig", true)) candidates += file
        }
        return candidates
            .filterNot { it.nameWithoutExtension.endsWith("_99_P", true) }
            .filter { candidate ->
                File(candidate.parentFile, "${candidate.nameWithoutExtension}.pak").isFile
            }
            .mapNotNull { relativePath(externalRoot, it) }
            .distinct()
            .sortedWith(compareBy<String> {
                if (it.contains("/Lang_en/$preferredLangVersion/")) 0 else 1
            }.thenBy { it })
    }

    private fun relativePath(root: File, file: File): String? {
        val rootPath = root.canonicalFile.toPath()
        val filePath = file.canonicalFile.toPath()
        if (!filePath.startsWith(rootPath)) return null
        return rootPath.relativize(filePath).toString().replace(File.separatorChar, '/')
    }

    private companion object {
        const val MAX_MOUNT_BYTES = 512 * 1024L
        val VERSION_REGEX = Regex("^\\d+\\.\\d+\\.\\d+$")
        fun compareVersion(left: String, right: String): Int =
            left.split('.').map(String::toInt).zip(right.split('.').map(String::toInt))
                .firstOrNull { it.first != it.second }
                ?.let { it.first.compareTo(it.second) }
                ?: 0
    }
}

fun sha1Hex(file: File): String {
    val digest = MessageDigest.getInstance("SHA-1")
    file.inputStream().use { input ->
        val buffer = ByteArray(1024 * 1024)
        while (true) {
            val read = input.read(buffer)
            if (read == -1) break
            digest.update(buffer, 0, read)
        }
    }
    return digest.digest().joinToString("") { "%02x".format(it.toInt() and 0xff) }
}
