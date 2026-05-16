package com.acceleratorer.wuwavn

import android.os.Environment
import android.os.RemoteException
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

class WuwaConfigUserService : IWuwaRestoreService.Stub() {
    override fun exists(absolutePath: String?): Boolean = validate(absolutePath).isFile

    override fun length(absolutePath: String?): Long = validate(absolutePath).length()

    override fun readFile(absolutePath: String?, maxBytes: Int): ByteArray {
        val file = validate(absolutePath)
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

    override fun writeConfigFile(
        absolutePath: String?,
        content: ByteArray?,
        expectedSha256: String?,
    ) {
        val file = validate(absolutePath)
        if (content == null) {
            throw RemoteException("Restore content is null.")
        }
        if (expectedSha256.isNullOrBlank()) {
            throw RemoteException("Expected SHA-256 is missing.")
        }
        if (content.size > MAX_CONFIG_BYTES) {
            throw RemoteException("Restore content is larger than maxBytes.")
        }

        val contentSha256 = Sha256Verifier.sha256(content)
        if (!contentSha256.equals(expectedSha256, ignoreCase = true)) {
            throw RemoteException("Restore content hash mismatch before write.")
        }

        try {
            val parent = file.parentFile
            if (parent == null || !parent.isDirectory) {
                throw RemoteException("Target parent folder does not exist.")
            }

            FileOutputStream(file, false).use { output ->
                output.write(content)
            }

            val restoredSha256 = Sha256Verifier.sha256(file)
            if (!restoredSha256.equals(expectedSha256, ignoreCase = true)) {
                throw RemoteException("Target hash mismatch after write.")
            }
        } catch (exception: RemoteException) {
            throw exception
        } catch (exception: Exception) {
            throw RemoteException("Write failed: ${exception.message}")
        }
    }

    private fun validate(absolutePath: String?): File {
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

            for (relativePath in RESTORE_RELATIVE_PATHS) {
                val expected = File(externalRoot, relativePath)
                    .canonicalFile
                    .path
                    .replace('\\', '/')
                if (normalized == expected) {
                    return file
                }
            }
            throw RemoteException("Blocked non-allowlisted restore path.")
        } catch (exception: RemoteException) {
            throw exception
        } catch (exception: Exception) {
            throw RemoteException("Path validation failed: ${exception.message}")
        }
    }

    private companion object {
        const val MAX_CONFIG_BYTES = 512 * 1024

        val RESTORE_RELATIVE_PATHS = setOf(
            "Android/data/com.kurogame.wutheringwaves.global/files/UE4Game/Client/Client/Saved/Config/Android/Engine.ini",
            "Android/data/com.kurogame.wutheringwaves.global/files/UE4Game/Client/Client/Saved/Config/Android/DeviceProfiles.ini",
            "Android/data/com.kurogame.wutheringwaves.global/files/UE4Game/Client/Client/Saved/Config/Android/MountLang_en.txt",
        )
    }
}
