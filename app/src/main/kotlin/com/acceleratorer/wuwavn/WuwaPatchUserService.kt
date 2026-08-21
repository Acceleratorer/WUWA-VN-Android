package com.acceleratorer.wuwavn

import android.os.Environment
import android.os.RemoteException
import java.io.File
import java.io.FileOutputStream
import java.nio.charset.StandardCharsets

class WuwaPatchUserService : IWuwaPatchService.Stub() {
    private var wuwa36Session: WuWa36Session? = null

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

    override fun wuwa36Snapshot(preferredSeries: String?): String =
        WuWa36PathResolver().snapshotJson(preferredSeries)

    override fun beginWuWa36Install(
        resourceVersion: String?,
        langVersion: String?,
        sigSourceRelativePath: String?,
        expectedPakSize: Long,
        expectedPakSha256: String?,
        expectedSigSha1: String?,
        mountLangContent: ByteArray?,
        expectedMountLangSha256: String?,
    ) {
        clearExistingWuWa36Session()
        val target = WuWa36Layout.targets(
            resourceVersion ?: throw RemoteException("Resource version is missing."),
            langVersion ?: throw RemoteException("Language version is missing."),
        )
        val expectedPakHash = validateSha256(expectedPakSha256, "PAK")
        val expectedSigHash = validateSha1(expectedSigSha1, "SIG")
        val expectedMountHash = validateSha256(expectedMountLangSha256, "MountLang")
        val mountBytes = mountLangContent ?: throw RemoteException("MountLang content is missing.")
        if (mountBytes.isEmpty() || mountBytes.size > MAX_MOUNT_BYTES) {
            throw RemoteException("MountLang content is outside the safe size limit.")
        }
        val mountText = mountBytes.toString(StandardCharsets.UTF_8)
        if (!Sha256Verifier.sha256(mountBytes).equals(expectedMountHash, true) ||
            !WuWa36Layout.isValidMountLang(mountText) ||
            !WuWa36Layout.containsPatchRegistryLine(mountText, target.langVersion)
        ) {
            throw RemoteException("MountLang content is invalid or does not register the 3.6 patch.")
        }

        val externalRoot = externalRoot()
        val pak = exactDynamicFile(externalRoot, target.pakRelativePath)
        val sig = exactDynamicFile(externalRoot, target.sigRelativePath)
        val mount = exactDynamicFile(externalRoot, target.mountLangRelativePath)
        val sourceRelativePath = sigSourceRelativePath?.replace('\\', '/')
            ?: throw RemoteException("SIG source is missing.")
        if (!WuWa36Layout.isOfficialSigSourcePath(sourceRelativePath)) {
            throw RemoteException("SIG source is not allowlisted.")
        }
        val source = exactDynamicFile(externalRoot, sourceRelativePath)
        val pairedPak = File(source.parentFile, "${source.nameWithoutExtension}.pak")
        if (!source.isFile || !pairedPak.isFile || sha1Hex(source) != expectedSigHash) {
            throw RemoteException("SIG source is missing or hash mismatched.")
        }
        val session = WuWa36Session(target, pak, sig, mount, source, expectedPakSize, expectedPakHash, expectedSigHash, mountBytes, expectedMountHash)
        session.prepare()
        wuwa36Session = session
    }

    override fun writeWuWa36InstallChunk(chunk: ByteArray?, length: Int, expectedSize: Long) {
        val session = wuwa36Session ?: throw RemoteException("WUWA 3.6 install session is missing.")
        if (chunk == null || length <= 0 || length > chunk.size || length > MAX_CHUNK_BYTES) {
            throw RemoteException("WUWA 3.6 chunk is invalid.")
        }
        if (expectedSize != session.expectedPakSize || session.pakTemp.length() + length > expectedSize) {
            throw RemoteException("WUWA 3.6 chunk exceeds expected PAK size.")
        }
        FileOutputStream(session.pakTemp, true).use { it.write(chunk, 0, length) }
    }

    override fun finishWuWa36Install() {
        val session = wuwa36Session ?: throw RemoteException("WUWA 3.6 install session is missing.")
        try {
            session.finish()
            wuwa36Session = null
        } catch (exception: Exception) {
            wuwa36Session = null
            throw withRollbackFailure(
                operation = "WUWA 3.6 install",
                original = exception,
                rollback = { session.rollback() },
            )
        }
    }

    override fun removeWuWa36Patch(
        resourceVersion: String?,
        langVersion: String?,
        originalMountLangContent: ByteArray?,
        expectedMountLangSha256: String?,
    ): Boolean {
        clearExistingWuWa36Session()
        val target = WuWa36Layout.targets(
            resourceVersion ?: throw RemoteException("Resource version is missing."),
            langVersion ?: throw RemoteException("Language version is missing."),
        )
        val mountBytes = originalMountLangContent ?: throw RemoteException("Original MountLang is missing.")
        val expectedMountHash = validateSha256(expectedMountLangSha256, "MountLang")
        val mountText = mountBytes.toString(StandardCharsets.UTF_8)
        if (!Sha256Verifier.sha256(mountBytes).equals(expectedMountHash, true) ||
            !WuWa36Layout.isOriginalMountLang(mountText)
        ) {
            throw RemoteException("Original MountLang is invalid or changed.")
        }
        val root = externalRoot()
        val pak = exactDynamicFile(root, target.pakRelativePath)
        val sig = exactDynamicFile(root, target.sigRelativePath)
        val mount = exactDynamicFile(root, target.mountLangRelativePath)
        val session = WuWa36RemoveSession(pak, sig, mount, mountBytes, expectedMountHash)
        return try {
            session.remove()
            true
        } catch (exception: Exception) {
            throw withRollbackFailure(
                operation = "WUWA 3.6 removal",
                original = exception,
                rollback = { session.rollback() },
            )
        }
    }

    private fun clearExistingWuWa36Session() {
        val existing = wuwa36Session ?: return
        try {
            existing.rollback()
            wuwa36Session = null
        } catch (exception: Exception) {
            throw RemoteException("Could not clean up the previous WUWA 3.6 session: ${exception.message}")
        }
    }

    private fun withRollbackFailure(
        operation: String,
        original: Exception,
        rollback: () -> Unit,
    ): RemoteException {
        val rollbackFailure = runCatching { rollback() }.exceptionOrNull()
        val message = buildString {
            append(operation).append(" failed: ").append(original.message)
            if (rollbackFailure != null) {
                append("; rollback also failed: ").append(rollbackFailure.message)
            }
        }
        return RemoteException(message).also { it.initCause(original) }
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
            val expectedPrefix = File(
                Environment.getExternalStorageDirectory(),
                "Android/data/${AppConstants.GLOBAL_GAME_PACKAGE}/files",
            ).canonicalFile
            if (!file.toPath().startsWith(expectedPrefix.toPath())) {
                throw RemoteException("Blocked path outside WUWA Global files.")
            }
            val gameRelative = normalized.substringAfter("/Android/data/", "")
                .substringAfter("/files/", "")
            if (WuWa36Layout.isPatchPakPath(gameRelative) ||
                WuWa36Layout.isPatchSigPath(gameRelative) ||
                WuWa36Layout.isMountLangPath(gameRelative)
            ) {
                return file
            }
            throw RemoteException("Blocked non-allowlisted patch path.")
        } catch (exception: RemoteException) {
            throw exception
        } catch (exception: Exception) {
            throw RemoteException("Path validation failed: ${exception.message}")
        }
    }

    private fun externalRoot(): File = Environment.getExternalStorageDirectory().canonicalFile

    private fun exactDynamicFile(root: File, relativePath: String): File {
        if (!WuWa36Layout.isAllowedDynamicPath(relativePath)) {
            throw RemoteException("Blocked non-allowlisted WUWA 3.6 path.")
        }
        val file = File(root, "Android/data/${AppConstants.GLOBAL_GAME_PACKAGE}/files/$relativePath").canonicalFile
        val expectedPrefix = File(root, "Android/data/${AppConstants.GLOBAL_GAME_PACKAGE}/files").canonicalFile
        if (!file.toPath().startsWith(expectedPrefix.toPath())) {
            throw RemoteException("Blocked WUWA 3.6 path outside game files.")
        }
        return file
    }

    private fun validateSha256(value: String?, label: String): String {
        val normalized = value?.trim().orEmpty()
        if (!normalized.matches(SHA256_REGEX)) throw RemoteException("$label SHA-256 is invalid.")
        return normalized.lowercase()
    }

    private fun validateSha1(value: String?, label: String): String {
        val normalized = value?.trim().orEmpty()
        if (!normalized.matches(SHA1_REGEX)) throw RemoteException("$label SHA-1 is invalid.")
        return normalized.lowercase()
    }

    private class WuWa36Session(
        private val target: WuWa36Target,
        private val pak: File,
        private val sig: File,
        private val mount: File,
        private val sigSource: File,
        val expectedPakSize: Long,
        private val expectedPakSha256: String,
        private val expectedSigSha1: String,
        private val mountBytes: ByteArray,
        private val expectedMountSha256: String,
    ) {
        val pakTemp = File(pak.parentFile, "${pak.name}.download")
        private val sigTemp = File(sig.parentFile, "${sig.name}.download")
        private val mountTemp = File(mount.parentFile, "${mount.name}.download")
        private val pakBackup = File(pak.parentFile, "${pak.name}.backup")
        private val sigBackup = File(sig.parentFile, "${sig.name}.backup")
        private val mountBackup = File(mount.parentFile, "${mount.name}.backup")
        private var pakExistedBefore = false
        private var sigExistedBefore = false
        private var mountExistedBefore = false
        private var commitStarted = false

        fun prepare() {
            if (expectedPakSize <= 0L || expectedPakSize > MAX_PATCH_BYTES) throw RemoteException("PAK size is invalid.")
            pak.parentFile?.mkdirs()
            mount.parentFile?.mkdirs()
            listOf(pakTemp, sigTemp, mountTemp, pakBackup, sigBackup, mountBackup).forEach { it.delete() }
            FileOutputStream(pakTemp, false).use { }
            sigSource.copyTo(sigTemp, overwrite = true)
            FileOutputStream(mountTemp, false).use { it.write(mountBytes) }
            if (sha1Hex(sigTemp) != expectedSigSha1 || !Sha256Verifier.verify(mountTemp, expectedMountSha256)) {
                cleanup()
                throw RemoteException("WUWA 3.6 staged SIG or MountLang verification failed.")
            }
        }

        fun finish() {
            if (pakTemp.length() != expectedPakSize || !Sha256Verifier.verify(pakTemp, expectedPakSha256)) {
                throw RemoteException("WUWA 3.6 staged PAK verification failed.")
            }
            if (sha1Hex(sigTemp) != expectedSigSha1 || !Sha256Verifier.verify(mountTemp, expectedMountSha256)) {
                throw RemoteException("WUWA 3.6 staged file verification failed.")
            }
            val stagedMount = mountTemp.readText(Charsets.UTF_8)
            val fields = stagedMount.lineSequence().map(String::trim).firstOrNull {
                it.startsWith("Lang_en/${target.langVersion}/WuWaVH_99_P.pak,")
            }?.split(',') ?: throw RemoteException("WUWA 3.6 MountLang patch entry is missing.")
            if (fields.size != 6 || fields[2].lowercase() != sha1Hex(pakTemp) || fields[3].lowercase() != sha1Hex(sigTemp)) {
                throw RemoteException("WUWA 3.6 MountLang hashes do not match staged files.")
            }
            pakExistedBefore = pak.exists()
            sigExistedBefore = sig.exists()
            mountExistedBefore = mount.exists()
            listOf(pak, sig, mount).forEach { file ->
                if (file.exists() && !file.isFile) {
                    throw RemoteException("WUWA 3.6 target is not a regular file: ${file.name}")
                }
            }
            commitStarted = true
            backup(pak, pakBackup)
            backup(sig, sigBackup)
            backup(mount, mountBackup)
            replace(pakTemp, pak)
            replace(sigTemp, sig)
            replace(mountTemp, mount)
            if (!pak.isFile || !sig.isFile || !mount.isFile ||
                !Sha256Verifier.verify(pak, expectedPakSha256) || sha1Hex(sig) != expectedSigSha1 ||
                !Sha256Verifier.verify(mount, expectedMountSha256)
            ) throw RemoteException("WUWA 3.6 committed files failed verification.")
            cleanup()
        }

        fun rollback() {
            var rollbackFailure: Exception? = null
            if (commitStarted) {
                listOf(
                    Triple(pakBackup, pak, pakExistedBefore),
                    Triple(sigBackup, sig, sigExistedBefore),
                    Triple(mountBackup, mount, mountExistedBefore),
                ).forEach { (source, destination, existedBefore) ->
                    try {
                        restoreOrDelete(source, destination, existedBefore)
                    } catch (exception: Exception) {
                        if (rollbackFailure == null) rollbackFailure = exception
                    }
                }
            }
            try {
                cleanup()
            } catch (exception: Exception) {
                if (rollbackFailure == null) rollbackFailure = exception
            }
            rollbackFailure?.let {
                throw IllegalStateException("WUWA 3.6 install rollback failed: ${it.message}", it)
            }
        }

        private fun backup(source: File, destination: File) {
            if (source.isFile) source.copyTo(destination, overwrite = true)
        }

        private fun restore(source: File, destination: File) {
            if (!source.isFile) throw IllegalStateException("Rollback backup is missing: ${source.name}")
            source.copyTo(destination, overwrite = true)
        }

        private fun restoreOrDelete(source: File, destination: File, existedBefore: Boolean) {
            if (existedBefore) {
                restore(source, destination)
            } else if (destination.exists() && !destination.delete()) {
                throw IllegalStateException("Could not remove newly committed ${destination.name} during rollback.")
            }
        }

        private fun replace(source: File, destination: File) {
            if (destination.exists() && !destination.delete()) throw RemoteException("Could not replace ${destination.name}.")
            if (!source.renameTo(destination)) throw RemoteException("Could not commit ${destination.name}.")
        }

        private fun cleanup() {
            listOf(pakTemp, sigTemp, mountTemp, pakBackup, sigBackup, mountBackup).forEach { it.delete() }
        }
    }

    private class WuWa36RemoveSession(
        private val pak: File,
        private val sig: File,
        private val mount: File,
        private val originalMountBytes: ByteArray,
        private val expectedMountSha256: String,
    ) {
        private val mountTemp = File(mount.parentFile, "${mount.name}.remove")
        private val pakBackup = File(pak.parentFile, "${pak.name}.remove-backup")
        private val sigBackup = File(sig.parentFile, "${sig.name}.remove-backup")
        private val mountBackup = File(mount.parentFile, "${mount.name}.remove-backup")
        private var pakExistedBefore = false
        private var sigExistedBefore = false
        private var mountExistedBefore = false
        private var commitStarted = false

        fun remove() {
            listOf(mountTemp, pakBackup, sigBackup, mountBackup).forEach { it.delete() }
            FileOutputStream(mountTemp, false).use { it.write(originalMountBytes) }
            if (!Sha256Verifier.verify(mountTemp, expectedMountSha256)) throw RemoteException("Original MountLang staging failed.")
            pakExistedBefore = pak.exists()
            sigExistedBefore = sig.exists()
            mountExistedBefore = mount.exists()
            listOf(pak, sig, mount).forEach { file ->
                if (file.exists() && !file.isFile) {
                    throw RemoteException("WUWA 3.6 target is not a regular file: ${file.name}")
                }
            }
            commitStarted = true
            if (pakExistedBefore) pak.copyTo(pakBackup, overwrite = true)
            if (sigExistedBefore) sig.copyTo(sigBackup, overwrite = true)
            if (mountExistedBefore) mount.copyTo(mountBackup, overwrite = true)
            if (mount.exists() && !mount.delete()) throw RemoteException("Could not restore MountLang.")
            if (!mountTemp.renameTo(mount)) throw RemoteException("Could not commit original MountLang.")
            if (pak.exists() && !pak.delete()) throw RemoteException("Could not delete WUWA 3.6 PAK.")
            if (sig.exists() && !sig.delete()) throw RemoteException("Could not delete WUWA 3.6 SIG.")
            if (pak.exists() || sig.exists() || !Sha256Verifier.verify(mount, expectedMountSha256)) throw RemoteException("WUWA 3.6 removal verification failed.")
            cleanup()
        }

        fun rollback() {
            var rollbackFailure: Exception? = null
            if (commitStarted) {
                listOf(
                    Triple(pakBackup, pak, pakExistedBefore),
                    Triple(sigBackup, sig, sigExistedBefore),
                    Triple(mountBackup, mount, mountExistedBefore),
                ).forEach { (source, destination, existedBefore) ->
                    try {
                        restoreOrDelete(source, destination, existedBefore)
                    } catch (exception: Exception) {
                        if (rollbackFailure == null) rollbackFailure = exception
                    }
                }
            }
            try {
                mountTemp.delete()
                cleanup()
            } catch (exception: Exception) {
                if (rollbackFailure == null) rollbackFailure = exception
            }
            rollbackFailure?.let {
                throw IllegalStateException("WUWA 3.6 removal rollback failed: ${it.message}", it)
            }
        }

        private fun restore(source: File, destination: File) {
            if (!source.isFile) throw IllegalStateException("Rollback backup is missing: ${source.name}")
            source.copyTo(destination, overwrite = true)
        }

        private fun restoreOrDelete(source: File, destination: File, existedBefore: Boolean) {
            if (existedBefore) {
                restore(source, destination)
            } else if (destination.exists() && !destination.delete()) {
                throw IllegalStateException("Could not remove newly committed ${destination.name} during rollback.")
            }
        }

        private fun cleanup() = listOf(mountTemp, pakBackup, sigBackup, mountBackup).forEach { it.delete() }
    }

    private companion object {
        const val MAX_CHUNK_BYTES = 256 * 1024
        const val MAX_PATCH_BYTES = 1024L * 1024L * 1024L
        val SHA256_REGEX = Regex("^[0-9a-fA-F]{64}$")
        val SHA1_REGEX = Regex("^[0-9a-fA-F]{40}$")
        const val MAX_MOUNT_BYTES = 512 * 1024
    }
}
