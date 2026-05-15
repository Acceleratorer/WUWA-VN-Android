package com.acceleratorer.wuwavn

import android.os.Environment
import android.os.RemoteException
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream

class WuwaBackupUserService : IWuwaBackupService.Stub() {
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

            for (relativePath in ALLOWED_RELATIVE_PATHS) {
                val expected = File(externalRoot, relativePath)
                    .canonicalFile
                    .path
                    .replace('\\', '/')
                if (normalized == expected) {
                    return file
                }
            }
            throw RemoteException("Blocked non-allowlisted path.")
        } catch (exception: RemoteException) {
            throw exception
        } catch (exception: Exception) {
            throw RemoteException("Path validation failed: ${exception.message}")
        }
    }

    private companion object {
        val ALLOWED_RELATIVE_PATHS = setOf(
            "Android/data/com.kurogame.wutheringwaves.global/files/UE4Game/Client/Client/Saved/Config/Android/Engine.ini",
            "Android/data/com.kurogame.wutheringwaves.global/files/UE4Game/Client/Client/Saved/Config/Android/DeviceProfiles.ini",
            "Android/data/com.kurogame.wutheringwaves.global/files/UE4Game/Client/Client/Saved/Config/Android/MountLang_en.txt",
        )
    }
}
