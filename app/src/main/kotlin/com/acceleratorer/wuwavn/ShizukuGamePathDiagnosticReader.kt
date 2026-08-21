package com.acceleratorer.wuwavn

import android.content.Context

class ShizukuGamePathDiagnosticReader(
    private val manifestRepository: PatchManifestRepository = PatchManifestRepository(),
    private val downloadClient: DownloadClient = DownloadClient(),
    private val preflightReader: WuWa36PreflightReader = WuWa36PreflightReader(),
) {
    fun read(context: Context): GamePathDiagnosticReport {
        val manifest = manifestRepository.current()
        val verifiedPak = downloadClient.verifiedPatchFile(context, manifest)
        return try {
            val snapshot = preflightReader.read(context)
            GamePathDiagnosticReport(
                snapshot = snapshot,
                verifiedLocalPak = verifiedPak != null,
                localPakSizeBytes = verifiedPak?.length(),
                localPakSha256 = verifiedPak?.let { manifest.pakSha256 },
                error = if (snapshot == null) {
                    "No WUWA 3.6 Resources layout with ResManifest was resolved."
                } else {
                    null
                },
            )
        } catch (exception: Exception) {
            GamePathDiagnosticReport(
                snapshot = null,
                verifiedLocalPak = verifiedPak != null,
                localPakSizeBytes = verifiedPak?.length(),
                localPakSha256 = verifiedPak?.let { manifest.pakSha256 },
                error = exception.message ?: exception.javaClass.simpleName,
                source = "Shizuku WUWA 3.6 snapshot unavailable",
            )
        }
    }
}
