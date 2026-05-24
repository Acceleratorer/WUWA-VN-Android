package com.acceleratorer.wuwavn

import android.os.Environment
import android.os.RemoteException
import java.io.File

class WuwaPathDiagnosticUserService : IWuwaPathDiagnosticService.Stub() {
    override fun exists(absolutePath: String?): Boolean = validate(absolutePath).exists()

    override fun isFile(absolutePath: String?): Boolean = validate(absolutePath).isFile

    override fun isDirectory(absolutePath: String?): Boolean = validate(absolutePath).isDirectory

    override fun length(absolutePath: String?): Long {
        val file = validate(absolutePath)
        return if (file.isFile) file.length() else 0L
    }

    override fun listChildNames(absolutePath: String?, maxEntries: Int): Array<String> {
        if (maxEntries <= 0 || maxEntries > MAX_CHILD_NAMES) {
            throw RemoteException("maxEntries is outside the safe diagnostic limit.")
        }

        val directory = validate(absolutePath)
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

            for (relativePath in GamePathDiagnosticPaths.allowedRelativePaths) {
                val expected = File(externalRoot, "Android/data/${AppConstants.GLOBAL_GAME_PACKAGE}/files/$relativePath")
                    .canonicalFile
                    .path
                    .replace('\\', '/')
                if (normalized == expected) {
                    return file
                }
            }
            throw RemoteException("Blocked non-allowlisted diagnostic path.")
        } catch (exception: RemoteException) {
            throw exception
        } catch (exception: Exception) {
            throw RemoteException("Path validation failed: ${exception.message}")
        }
    }

    private companion object {
        const val MAX_CHILD_NAMES = 40
    }
}
