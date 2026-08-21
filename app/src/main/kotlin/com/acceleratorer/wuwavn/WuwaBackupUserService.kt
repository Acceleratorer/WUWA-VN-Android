package com.acceleratorer.wuwavn

import android.os.Environment
import android.os.RemoteException
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest

class WuwaBackupUserService : IWuwaBackupService.Stub() {
    override fun exists(absolutePath: String?): Boolean = validateBackupReadPath(absolutePath).isFile

    override fun length(absolutePath: String?): Long = validateBackupReadPath(absolutePath).length()

    override fun readFile(absolutePath: String?, maxBytes: Int): ByteArray {
        val file = validateBackupReadPath(absolutePath)
        if (!file.isFile) {
            throw RemoteException("File does not exist: ${file.name}")
        }
        if (file.length() > maxBytes) {
            throw RemoteException("File is larger than maxBytes.")
        }

        try {
            FileInputStream(file).use { input ->
                ByteArrayOutputStream().use { output ->
                    val buffer = ByteArray(8192)
                    while (true) {
                        val read = input.read(buffer)
                        if (read == -1) break
                        output.write(buffer, 0, read)
                        if (output.size() > maxBytes) {
                            throw RemoteException("File exceeded maxBytes while reading.")
                        }
                    }
                    return output.toByteArray()
                }
            }
        } catch (exception: RemoteException) {
            throw exception
        } catch (exception: Exception) {
            throw RemoteException("Read failed: ${exception.message}")
        }
    }

    override fun pathExists(absolutePath: String?): Boolean = validateDiagnosticPath(absolutePath).exists()

    override fun isFile(absolutePath: String?): Boolean = validateDiagnosticPath(absolutePath).isFile

    override fun isDirectory(absolutePath: String?): Boolean = validateDiagnosticPath(absolutePath).isDirectory

    override fun listChildNames(absolutePath: String?, maxEntries: Int): Array<String> {
        if (maxEntries <= 0 || maxEntries > MAX_CHILD_NAMES) {
            throw RemoteException("maxEntries is outside the safe diagnostic limit.")
        }

        val directory = validateDiagnosticPath(absolutePath)
        if (!directory.isDirectory) {
            return emptyArray()
        }

        return directory.listFiles()
            ?.map { it.name }
            ?.sorted()
            ?.take(maxEntries)
            ?.toTypedArray()
            ?: emptyArray()
    }

    override fun sha1(absolutePath: String?): String {
        val file = validateDiagnosticPath(absolutePath)
        if (!file.isFile) {
            throw RemoteException("File does not exist.")
        }
        return try {
            val digest = MessageDigest.getInstance("SHA-1")
            FileInputStream(file).use { input ->
                val buffer = ByteArray(1024 * 1024)
                while (true) {
                    val read = input.read(buffer)
                    if (read == -1) break
                    digest.update(buffer, 0, read)
                }
            }
            digest.digest().joinToString("") { "%02x".format(it.toInt() and 0xff) }
        } catch (exception: Exception) {
            throw RemoteException("SHA-1 failed: ${exception.message}")
        }
    }

    override fun wuwa36MountLangRelativePath(preferredSeries: String?): String? =
        WuWa36PathResolver().snapshot(preferredSeries)?.mountLangRelativePath

    private fun validateBackupReadPath(absolutePath: String?): File =
        validate(absolutePath, BACKUP_READ_RELATIVE_PATHS, "Blocked non-allowlisted backup path.", allowWuWa36MountLang = true)

    private fun validateDiagnosticPath(absolutePath: String?): File =
        validate(absolutePath, DIAGNOSTIC_RELATIVE_PATHS, "Blocked non-allowlisted diagnostic path.")

    private fun validate(
        absolutePath: String?,
        allowedRelativePaths: Set<String>,
        blockedMessage: String,
        allowWuWa36MountLang: Boolean = false,
    ): File {
        if (absolutePath == null) {
            throw RemoteException("Path is null.")
        }
        val rawPath = absolutePath.replace('\\', '/')
        if (rawPath.contains("..")) {
            throw RemoteException("Blocked path traversal.")
        }

        try {
            val file = File(absolutePath).canonicalFile
            val normalized = file.path.replace('\\', '/')
            val externalRoot = Environment.getExternalStorageDirectory()
                .canonicalFile
                .path
                .replace('\\', '/')

            for (relativePath in allowedRelativePaths) {
                val expected = File(externalRoot, relativePath)
                    .canonicalFile
                    .path
                    .replace('\\', '/')
                if (normalized == expected) {
                    return file
                }
            }
            val gameRelative = normalized.substringAfter("/Android/data/", "")
                .substringAfter("/files/", "")
            if (allowWuWa36MountLang && WuWa36Layout.isMountLangPath(gameRelative)) {
                return file
            }
            throw RemoteException(blockedMessage)
        } catch (exception: RemoteException) {
            throw exception
        } catch (exception: Exception) {
            throw RemoteException("Path validation failed: ${exception.message}")
        }
    }

    private companion object {
        const val MAX_CHILD_NAMES = 40

        val BACKUP_READ_RELATIVE_PATHS = setOf(
            "Android/data/com.kurogame.wutheringwaves.global/files/UE4Game/Client/Client/Saved/Config/Android/Engine.ini",
            "Android/data/com.kurogame.wutheringwaves.global/files/UE4Game/Client/Client/Saved/Config/Android/DeviceProfiles.ini",
        )

        val DIAGNOSTIC_RELATIVE_PATHS = BACKUP_READ_RELATIVE_PATHS
    }
}
