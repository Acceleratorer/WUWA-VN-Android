package com.acceleratorer.wuwavn

import android.content.Context
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import org.json.JSONObject

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
        val downloadUrl = mintDownloadUrl(manifest)
        val connection = openTrustedDownloadConnection(downloadUrl)

        try {
            val status = connection.responseCode
            if (status !in 200..299) {
                throw IllegalStateException("Patch download failed with HTTP $status")
            }
            requireTrustedUrl(connection.url)

            val totalBytes = connection.contentLengthLong
            if (totalBytes > 0L && totalBytes != manifest.pakSizeBytes) {
                throw SecurityException("Patch download size does not match the pinned manifest.")
            }
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
                        if (readBytes > manifest.pakSizeBytes) {
                            throw SecurityException("Patch download exceeded the pinned size.")
                        }
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

        if (temporary.length() != manifest.pakSizeBytes) {
            temporary.delete()
            throw SecurityException("Patch size verification failed.")
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

    private fun mintDownloadUrl(manifest: PatchManifest): URL {
        val connection = openTrustedConnection(URL(manifest.pakUrl))
        connection.requestMethod = "POST"
        connection.doOutput = true
        connection.setRequestProperty("Content-Type", "application/json")
        connection.setRequestProperty("X-Client-Platform", "android")
        val body = JSONObject()
            .put("box", "WuwaVH")
            .put("provider", "mod")
            .put("version", manifest.upstreamVersion)
            .toString()
            .toByteArray(Charsets.UTF_8)
        connection.setFixedLengthStreamingMode(body.size)
        try {
            connection.outputStream.use { it.write(body) }
            if (connection.responseCode !in 200..299) {
                throw IllegalStateException("Patch link request failed with HTTP ${connection.responseCode}")
            }
            val response = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            if (response.toByteArray(Charsets.UTF_8).size > MAX_MINT_RESPONSE_BYTES) {
                throw SecurityException("Patch link response is too large.")
            }
            val href = JSONObject(response).optString("href")
            if (href.isBlank()) throw IllegalStateException("Patch server did not return a download URL.")
            return requireTrustedUrl(URL(href))
        } finally {
            connection.disconnect()
        }
    }

    private fun openTrustedConnection(url: URL): HttpURLConnection {
        val connection = requireTrustedUrl(url).openConnection() as HttpURLConnection
        connection.connectTimeout = 15000
        connection.readTimeout = 30000
        connection.instanceFollowRedirects = false
        connection.setRequestProperty("User-Agent", "WUWA-VN-Android/${AppConstants.VERSION_NAME}")
        return connection
    }

    private fun openTrustedDownloadConnection(startUrl: URL): HttpURLConnection {
        var currentUrl = requireTrustedUrl(startUrl)
        repeat(MAX_REDIRECTS + 1) { hop ->
            val connection = openTrustedConnection(currentUrl)
            connection.requestMethod = "GET"
            connection.connect()
            val status = connection.responseCode
            if (status !in REDIRECT_STATUSES) {
                return connection
            }

            val location = connection.getHeaderField("Location")
            connection.disconnect()
            if (location.isNullOrBlank()) {
                throw SecurityException("Patch download redirect did not provide a location.")
            }
            currentUrl = requireTrustedUrl(URL(currentUrl, location))
            if (hop == MAX_REDIRECTS) {
                throw SecurityException("Patch download exceeded the redirect limit.")
            }
        }
        error("Patch download redirect handling failed.")
    }

    private fun requireTrustedUrl(url: URL): URL {
        val host = url.host.lowercase(Locale.ROOT)
        if (url.protocol != "https" ||
            (host !in TRUSTED_DOWNLOAD_HOSTS && !host.endsWith(".cdn.hf.co"))
        ) {
            throw SecurityException("Patch server returned an untrusted download URL.")
        }
        return url
    }

    private fun progress(readBytes: Long, totalBytes: Long): String {
        if (totalBytes > 0L) {
            return "Patch download: ${formatMb(readBytes)} / ${formatMb(totalBytes)}"
        }
        return "Patch download: ${formatMb(readBytes)}"
    }

    private fun formatMb(bytes: Long): String =
        String.format(Locale.US, "%.1f MB", bytes / 1024.0 / 1024.0)

    private companion object {
        const val MAX_MINT_RESPONSE_BYTES = 64 * 1024
        const val MAX_REDIRECTS = 5
        val TRUSTED_DOWNLOAD_HOSTS = setOf("dl.dangdev.io.vn", "huggingface.co")
        val REDIRECT_STATUSES = setOf(301, 302, 303, 307, 308)
    }
}
