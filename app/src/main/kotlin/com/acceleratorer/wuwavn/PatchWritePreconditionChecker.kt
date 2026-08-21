package com.acceleratorer.wuwavn

import android.content.Context

class PatchWritePreconditionChecker(
    private val manifestRepository: PatchManifestRepository,
    private val downloadClient: DownloadClient,
    private val restoreDryRunPlanner: RestoreDryRunPlanner,
    private val preflightReader: WuWa36PreflightReader = WuWa36PreflightReader(),
) {
    fun check(
        context: Context,
        gameState: GamePackageDetector.State,
        shizukuState: ShizukuState,
    ): PatchWritePrecondition {
        val failures = mutableListOf<String>()
        val manifest = manifestRepository.current()
        val patchFile = downloadClient.verifiedPatchFile(context, manifest)
        val trustedBackup = TrustedBackupPolicy.findTrustedBackup(context, restoreDryRunPlanner)
        val snapshot = if (shizukuState == ShizukuState.READY) {
            try {
                preflightReader.read(context)
            } catch (exception: Exception) {
                failures.add("WUWA 3.6 preflight failed: ${exception.message}")
                null
            }
        } else {
            null
        }
        val targetRelativePath = snapshot?.pakRelativePath.orEmpty()

        if (gameState != GamePackageDetector.State.GLOBAL_INSTALLED) {
            failures.add("Wuthering Waves Global is not detected.")
        }
        if (shizukuState != ShizukuState.READY) {
            failures.add("Shizuku is not ready.")
        }
        if (snapshot != null && !WuWa36Layout.isPatchPakPath(targetRelativePath)) {
            failures.add("Patch target is not allowlisted.")
        }
        if (patchFile == null) {
            failures.add("Verified PAK is missing. Run Download & Verify Patch first.")
        } else if (patchFile.length() <= 0L || patchFile.length() > MAX_PATCH_BYTES) {
            failures.add("Verified PAK size is outside the safe write limit.")
        }
        if (trustedBackup == null) {
            failures.add("No trusted VERIFIED backup found. Run Backup Game Configs first.")
        }
        if (snapshot == null) {
            failures.add("WUWA 3.6 Resources layout is not ready or could not be read.")
        } else {
            if (!snapshot.isReadyForPatch) failures.add("WUWA 3.6 MountLang/SIG preconditions are incomplete.")
            if (snapshot.sigSourceRelativePath == null || snapshot.sigSourceSha1 == null) {
                failures.add("No verified official SIG source was found for WUWA 3.6.")
            }
            if (snapshot.mountLangContent == null || !WuWa36Layout.isValidMountLang(snapshot.mountLangContent)) {
                failures.add("MountLang_en.txt is missing or has an unsupported format.")
            }
            if (trustedBackup != null) {
                val backupMatchesResolvedMount = runCatching {
                    restoreDryRunPlanner.plan(trustedBackup.sessionDirectory)
                        .files.any {
                            it.status == RestoreFileStatus.VERIFIED &&
                                it.relativePath == snapshot.mountLangRelativePath
                        }
                }.getOrDefault(false)
                if (!backupMatchesResolvedMount) {
                    failures.add("Trusted backup does not contain the current WUWA 3.6 Resources MountLang.")
                }
            }
        }

        val plan = if (failures.isEmpty() && patchFile != null && trustedBackup != null && snapshot != null) {
            val pakSha1 = sha1Hex(patchFile)
            val mountLang = WuWa36Layout.patchMountLang(
                original = snapshot.mountLangContent!!,
                langVersion = snapshot.resolvedLanguageVersion,
                pakSha1 = pakSha1,
                sigSha1 = snapshot.sigSourceSha1!!,
            ).toByteArray(Charsets.UTF_8)
            PatchWritePlan(
                manifest = manifest,
                patchFile = patchFile,
                patchSizeBytes = patchFile.length(),
                patchSha256 = manifest.pakSha256,
                targetRelativePath = targetRelativePath,
                targetDisplayName = PatchDryRunPlanner.displayName(targetRelativePath),
                trustedBackup = trustedBackup,
                resourceVersion = snapshot.resolvedResourceVersion,
                langVersion = snapshot.resolvedLanguageVersion,
                sigSourceRelativePath = snapshot.sigSourceRelativePath!!,
                sigSha1 = snapshot.sigSourceSha1,
                mountLangContent = mountLang,
                mountLangSha256 = Sha256Verifier.sha256(mountLang),
            )
        } else {
            null
        }

        return PatchWritePrecondition(plan, failures)
    }

    private companion object {
        const val MAX_PATCH_BYTES = 1024L * 1024L * 1024L
    }
}
