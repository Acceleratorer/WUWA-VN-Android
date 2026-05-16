package com.acceleratorer.wuwavn

import android.os.Environment
import android.os.RemoteException
import java.io.File
import java.io.FileOutputStream

class WuwaPatchUserService : IWuwaPatchService.Stub() {
    override fun exists(absolutePath: String?): Boolean = validate(absolutePath).isFile

    override fun length(absolutePath: String?): Long = validate(absolutePath).length()

    override fun sha256(absolutePath: String?): String {
        val file = validate(absolutePath)
        if (!file.isFile) {
            throw RemoteException("Patch target does not exist.")
        }
        return try {
            Sha256Verifier.sha256(file)
        } catch (exception: Exception) {
            throw RemoteException("Patch target hash failed: ${exception.message}")
        }
    }

    override fun beginWritePatch(
        absolutePath: String?,
        expectedSize: Long,
        expectedSha256: String?,
    ) {
        val target = validate(absolutePath)
        validatePatchExpectations(expectedSize, expectedSha256)
        val parent = target.parentFile
        if (parent == null || !parent.isDirectory) {
            throw RemoteException("Patch target parent folder does not exist.")
        }

        val temp = tempFile(target)
        if (temp.exists() && !temp.delete()) {
            throw RemoteException("Could not delete old patch temp file.")
        }
        try {
            FileOutputStream(temp, false).use {
                // Create an empty temp file so later chunks cannot skip beginWritePatch.
            }
        } catch (exception: Exception) {
            throw RemoteException("Could not create patch temp file: ${exception.message}")
        }
    }

    override fun writePatchChunk(
        absolutePath: String?,
        chunk: ByteArray?,
        length: Int,
        expectedSize: Long,
    ) {
        val target = validate(absolutePath)
        if (chunk == null) {
            throw RemoteException("Patch chunk is null.")
        }
        if (length <= 0 || length > chunk.size || length > MAX_CHUNK_BYTES) {
            throw RemoteException("Patch chunk length is invalid.")
        }
        if (expectedSize <= 0L || expectedSize > MAX_PATCH_BYTES) {
            throw RemoteException("Expected patch size is invalid.")
        }

        val temp = tempFile(target)
        if (!temp.isFile) {
            throw RemoteException("Patch temp file is missing.")
        }
        if (temp.length() + length.toLong() > expectedSize) {
            throw RemoteException("Patch temp file would exceed expected size.")
        }

        try {
            FileOutputStream(temp, true).use { output ->
                output.write(chunk, 0, length)
            }
        } catch (exception: Exception) {
            throw RemoteException("Patch chunk write failed: ${exception.message}")
        }
    }

    override fun finishWritePatch(
        absolutePath: String?,
        expectedSize: Long,
        expectedSha256: String?,
    ) {
        val target = validate(absolutePath)
        val expected = validatePatchExpectations(expectedSize, expectedSha256)
        val temp = tempFile(target)
        if (!temp.isFile) {
            throw RemoteException("Patch temp file is missing.")
        }
        if (temp.length() != expectedSize) {
            throw RemoteException("Patch temp size mismatch.")
        }

        try {
            val tempSha256 = Sha256Verifier.sha256(temp)
            if (!tempSha256.equals(expected, ignoreCase = true)) {
                throw RemoteException("Patch temp SHA-256 mismatch.")
            }

            if (target.exists() && !target.delete()) {
                throw RemoteException("Could not replace old patch target.")
            }
            if (!temp.renameTo(target)) {
                throw RemoteException("Could not move patch temp into place.")
            }

            val targetSha256 = Sha256Verifier.sha256(target)
            if (!targetSha256.equals(expected, ignoreCase = true)) {
                throw RemoteException("Patch target SHA-256 mismatch after write.")
            }
        } catch (exception: RemoteException) {
            throw exception
        } catch (exception: Exception) {
            throw RemoteException("Patch finish failed: ${exception.message}")
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
            val expected = File(externalRoot, PATCH_TARGET_RELATIVE_PATH)
                .canonicalFile
                .path
                .replace('\\', '/')

            if (normalized == expected) {
                return file
            }
            throw RemoteException("Blocked non-allowlisted patch path.")
        } catch (exception: RemoteException) {
            throw exception
        } catch (exception: Exception) {
            throw RemoteException("Path validation failed: ${exception.message}")
        }
    }

    private fun validatePatchExpectations(
        expectedSize: Long,
        expectedSha256: String?,
    ): String {
        if (expectedSize <= 0L || expectedSize > MAX_PATCH_BYTES) {
            throw RemoteException("Expected patch size is invalid.")
        }
        val expected = expectedSha256?.trim().orEmpty()
        if (!expected.matches(SHA256_REGEX)) {
            throw RemoteException("Expected SHA-256 is invalid.")
        }
        return expected
    }

    private fun tempFile(target: File): File =
        File(target.parentFile, "${target.name}.tmp")

    private companion object {
        const val MAX_CHUNK_BYTES = 256 * 1024
        const val MAX_PATCH_BYTES = 1024L * 1024L * 1024L
        const val PATCH_TARGET_RELATIVE_PATH =
            "Android/data/com.kurogame.wutheringwaves.global/files/UE4Game/Client/Client/Content/Paks/WuWaVH_99_P.pak"

        val SHA256_REGEX = Regex("^[0-9a-fA-F]{64}$")
    }
}
