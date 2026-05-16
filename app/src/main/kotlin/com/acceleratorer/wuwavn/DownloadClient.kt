package com.acceleratorer.wuwavn

import android.content.Context
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

class DownloadClient {
    fun interface ProgressListener {
        fun onProgress(message: String)
    }

    fun patchFile(context: Context, manifest: PatchManifest): File {
        val patchDir = context.getExternalFilesDir("patches")
            ?: File(context.filesDir, "patches")
        return File(patchDir, manifest.pakFileName)
    }

    fun verifiedPatchFile(context: Context, manifest: PatchManifest): File? {
        val file = patchFile(context, manifest)
        return if (file.isFile && Sha256Verifier.verify(file, manifest.pakSha256)) {
            file
        } else {
            null
        }
    }

    fun downloadAndVerify(
        context: Context,
        manifest: PatchManifest,
        progressListener: ProgressListener,
    ): File {
        val patchDir = patchFile(context, manifest).parentFile
            ?: throw IllegalStateException("Could not locate patch directory.")
        if (!patchDir.exists() && !patchDir.mkdirs()) {
            throw IllegalStateException("Could not create patch directory: ${patchDir.absolutePath}")
        }

        val destination = patchFile(context, manifest)
        if (destination.exists() && Sha256Verifier.verify(destination, manifest.pakSha256)) {
            progressListener.onProgress("Patch download: already verified")
            return destination
        }

        val temporary = File(patchDir, "${manifest.pakFileName}.download")
        if (temporary.exists() && !temporary.delete()) {
            throw IllegalStateException("Could not remove old partial download.")
        }

        progressListener.onProgress("Patch download: started")
        val connection = URL(manifest.pakUrl).openConnection() as HttpURLConnection
        connection.connectTimeout = 15000
        connection.readTimeout = 30000
        connection.instanceFollowRedirects = true
        connection.connect()

        try {
            val status = connection.responseCode
            if (status !in 200..299) {
                throw IllegalStateException("Patch download failed with HTTP $status")
            }

            val totalBytes = connection.contentLengthLong
            var readBytes = 0L
            var nextLogBytes = 5L * 1024L * 1024L
            val buffer = ByteArray(64 * 1024)

            connection.inputStream.use { input ->
                FileOutputStream(temporary).use { output ->
                    while (true) {
                        val read = input.read(buffer)
                        if (read == -1) break
                        output.write(buffer, 0, read)
                        readBytes += read.toLong()
                        if (readBytes >= nextLogBytes) {
                            progressListener.onProgress(progress(readBytes, totalBytes))
                            nextLogBytes += 5L * 1024L * 1024L
                        }
                    }
                }
            }
        } finally {
            connection.disconnect()
        }

        progressListener.onProgress("SHA-256: verifying")
        if (!Sha256Verifier.verify(temporary, manifest.pakSha256)) {
            temporary.delete()
            throw SecurityException("Patch SHA-256 verification failed.")
        }

        if (destination.exists() && !destination.delete()) {
            throw IllegalStateException("Could not replace old patch file.")
        }
        if (!temporary.renameTo(destination)) {
            throw IllegalStateException("Could not finalize patch download.")
        }

        progressListener.onProgress("SHA-256: verified")
        return destination
    }

    private fun progress(readBytes: Long, totalBytes: Long): String {
        if (totalBytes > 0L) {
            return "Patch download: ${formatMb(readBytes)} / ${formatMb(totalBytes)}"
        }
        return "Patch download: ${formatMb(readBytes)}"
    }

    private fun formatMb(bytes: Long): String =
        String.format(Locale.US, "%.1f MB", bytes / 1024.0 / 1024.0)
}
