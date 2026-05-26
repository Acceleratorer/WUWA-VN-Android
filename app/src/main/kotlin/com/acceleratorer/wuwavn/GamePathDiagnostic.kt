package com.acceleratorer.wuwavn

data class GamePathCandidate(
    val label: String,
    val relativePath: String,
)

data class GamePathFileResult(
    val candidate: GamePathCandidate,
    val exists: Boolean,
    val isFile: Boolean,
    val sizeBytes: Long?,
    val error: String? = null,
)

data class GamePathDirectoryResult(
    val candidate: GamePathCandidate,
    val exists: Boolean,
    val isDirectory: Boolean,
    val childNames: List<String>,
    val error: String? = null,
)

data class GamePathDiagnosticReport(
    val files: List<GamePathFileResult>,
    val directories: List<GamePathDirectoryResult>,
    val error: String? = null,
)

object GamePathDiagnosticPaths {
    val fileCandidates = listOf(
        GamePathCandidate(
            "Engine.ini current",
            "UE4Game/Client/Client/Saved/Config/Android/Engine.ini",
        ),
        GamePathCandidate(
            "DeviceProfiles.ini current",
            "UE4Game/Client/Client/Saved/Config/Android/DeviceProfiles.ini",
        ),
        GamePathCandidate(
            "MountLang legacy config",
            "UE4Game/Client/Client/Saved/Config/Android/MountLang_en.txt",
        ),
        GamePathCandidate(
            "MountLang resources 3.3.0",
            "UE4Game/Client/Client/Saved/Resources/3.3.0/MountLang_en.txt",
        ),
        GamePathCandidate(
            "MountLang resources 3.3.0 alternate",
            "UE4Game/Client/Saved/Resources/3.3.0/MountLang_en.txt",
        ),
        GamePathCandidate(
            "MountLang resources Mount folder",
            "UE4Game/Client/Client/Saved/Resources/3.3.0/Mount/MountLang_en.txt",
        ),
        GamePathCandidate(
            "MountLang resources Mount folder alternate",
            "UE4Game/Client/Saved/Resources/3.3.0/Mount/MountLang_en.txt",
        ),
        GamePathCandidate(
            "Lang_en Base PAK",
            "UE4Game/Client/Client/Saved/Resources/3.3.0/Lang_en/Base/pakchunk10-Android_ASTC.pak",
        ),
        GamePathCandidate(
            "Lang_en Base SIG",
            "UE4Game/Client/Client/Saved/Resources/3.3.0/Lang_en/Base/pakchunk10-Android_ASTC.sig",
        ),
        GamePathCandidate(
            "Lang_en 3.3.9 PAK",
            "UE4Game/Client/Client/Saved/Resources/3.3.0/Lang_en/3.3.9/pakchunk10-Android_ASTC_P.pak",
        ),
        GamePathCandidate(
            "Lang_en 3.3.9 SIG",
            "UE4Game/Client/Client/Saved/Resources/3.3.0/Lang_en/3.3.9/pakchunk10-Android_ASTC_P.sig",
        ),
        GamePathCandidate(
            "Lang_en Base PAK alternate",
            "UE4Game/Client/Saved/Resources/3.3.0/Lang_en/Base/pakchunk10-Android_ASTC.pak",
        ),
        GamePathCandidate(
            "Lang_en Base SIG alternate",
            "UE4Game/Client/Saved/Resources/3.3.0/Lang_en/Base/pakchunk10-Android_ASTC.sig",
        ),
        GamePathCandidate(
            "Lang_en 3.3.9 PAK alternate",
            "UE4Game/Client/Saved/Resources/3.3.0/Lang_en/3.3.9/pakchunk10-Android_ASTC_P.pak",
        ),
        GamePathCandidate(
            "Lang_en 3.3.9 SIG alternate",
            "UE4Game/Client/Saved/Resources/3.3.0/Lang_en/3.3.9/pakchunk10-Android_ASTC_P.sig",
        ),
    )

    val directoryCandidates = listOf(
        GamePathCandidate(
            "Config Android current",
            "UE4Game/Client/Client/Saved/Config/Android",
        ),
        GamePathCandidate(
            "Resources 3.3.0 current",
            "UE4Game/Client/Client/Saved/Resources/3.3.0",
        ),
        GamePathCandidate(
            "Resources 3.3.0 alternate",
            "UE4Game/Client/Saved/Resources/3.3.0",
        ),
        GamePathCandidate(
            "Lang_en current",
            "UE4Game/Client/Client/Saved/Resources/3.3.0/Lang_en",
        ),
        GamePathCandidate(
            "Lang_en alternate",
            "UE4Game/Client/Saved/Resources/3.3.0/Lang_en",
        ),
        GamePathCandidate(
            "Lang_en Base current",
            "UE4Game/Client/Client/Saved/Resources/3.3.0/Lang_en/Base",
        ),
        GamePathCandidate(
            "Lang_en Base alternate",
            "UE4Game/Client/Saved/Resources/3.3.0/Lang_en/Base",
        ),
        GamePathCandidate(
            "Lang_en 3.3.9 current",
            "UE4Game/Client/Client/Saved/Resources/3.3.0/Lang_en/3.3.9",
        ),
        GamePathCandidate(
            "Lang_en 3.3.9 alternate",
            "UE4Game/Client/Saved/Resources/3.3.0/Lang_en/3.3.9",
        ),
        GamePathCandidate(
            "Mount folder current",
            "UE4Game/Client/Client/Saved/Resources/3.3.0/Mount",
        ),
        GamePathCandidate(
            "Mount folder alternate",
            "UE4Game/Client/Saved/Resources/3.3.0/Mount",
        ),
        GamePathCandidate(
            "Video Paks current",
            "UE4Game/Client/Client/Saved/Resources/Video/Paks",
        ),
        GamePathCandidate(
            "Video Paks alternate",
            "UE4Game/Client/Saved/Resources/Video/Paks",
        ),
        GamePathCandidate(
            "Legacy Content/Paks current",
            "UE4Game/Client/Client/Content/Paks",
        ),
        GamePathCandidate(
            "Legacy Content/Paks alternate",
            "UE4Game/Client/Content/Paks",
        ),
    )

    val allowedRelativePaths: Set<String> =
        (fileCandidates + directoryCandidates)
            .map { it.relativePath }
            .toSet()

    val allowedAbsoluteRelativePaths: Set<String> =
        allowedRelativePaths.map {
            "Android/data/${AppConstants.GLOBAL_GAME_PACKAGE}/files/$it"
        }.toSet()

    fun isAllowed(relativePath: String): Boolean {
        val normalized = relativePath.replace('\\', '/')
        return !normalized.contains("..") && allowedRelativePaths.contains(normalized)
    }
}

object GamePathDiagnosticRenderer {
    fun render(
        report: GamePathDiagnosticReport,
        gameInfo: GamePackageDetector.GameInfo?,
        shizukuState: ShizukuState,
    ): String = buildString {
        appendLine("WUWA VN Game Path Diagnostic")
        appendLine("App version: ${AppConstants.VERSION_NAME} (${AppConstants.VERSION_CODE})")
        appendLine("Game package: ${gameInfo?.packageName ?: AppConstants.GLOBAL_GAME_PACKAGE}")
        appendLine("Game version: ${gameInfo?.versionName ?: "unknown"}")
        appendLine("Shizuku: ${shizukuState.label}")
        appendLine("Mode: read-only diagnostic, no files changed")
        appendLine()

        if (report.error != null) {
            appendLine("Diagnostic error:")
            appendLine(report.error)
            appendLine()
        }

        appendLine("Files:")
        for (file in report.files) {
            appendLine("- ${file.candidate.label}: ${fileStatus(file)}")
            appendLine("  ${file.candidate.relativePath}")
            if (file.error != null) {
                appendLine("  Error: ${file.error}")
            }
        }

        appendLine()
        appendLine("Directories:")
        for (directory in report.directories) {
            appendLine("- ${directory.candidate.label}: ${directoryStatus(directory)}")
            appendLine("  ${directory.candidate.relativePath}")
            if (directory.childNames.isNotEmpty()) {
                appendLine("  Children: ${directory.childNames.joinToString(", ")}")
            }
            if (directory.error != null) {
                appendLine("  Error: ${directory.error}")
            }
        }

        appendLine()
        appendLine("Notes:")
        for (note in notes(report)) {
            appendLine("- $note")
        }
    }

    private fun fileStatus(file: GamePathFileResult): String = when {
        file.error != null -> "ERROR"
        file.exists && file.isFile -> "FOUND (${file.sizeBytes ?: 0} bytes)"
        file.exists -> "EXISTS but is not a file"
        else -> "missing"
    }

    private fun directoryStatus(directory: GamePathDirectoryResult): String = when {
        directory.error != null -> "ERROR"
        directory.exists && directory.isDirectory -> "FOUND"
        directory.exists -> "EXISTS but is not a directory"
        else -> "missing"
    }

    private fun notes(report: GamePathDiagnosticReport): List<String> {
        val notes = mutableListOf<String>()
        if (report.error != null) {
            notes.add("Diagnostic did not read game paths because the Shizuku diagnostic connection failed.")
            notes.add("Close and reopen WUWA VN, make sure Shizuku is still Ready, then retry More Tools > Game Path Diagnostic.")
            return notes
        }

        val resourceMountLangFound = report.files.any {
            it.candidate.label.startsWith("MountLang resources") && it.exists && it.isFile
        }
        val mountFolderMountLangFound = report.files.any {
            it.candidate.label.startsWith("MountLang resources Mount folder") && it.exists && it.isFile
        }
        val legacyMountLangFound = report.files.any {
            it.candidate.label == "MountLang legacy config" && it.exists && it.isFile
        }
        val langPakFound = report.files.any {
            it.candidate.label.startsWith("Lang_en") && it.candidate.label.endsWith("PAK") && it.exists && it.isFile
        }
        val legacyPakFolderFound = report.directories.any {
            it.candidate.label.startsWith("Legacy Content/Paks") && it.exists && it.isDirectory
        }

        if ((resourceMountLangFound || mountFolderMountLangFound) && !legacyMountLangFound) {
            notes.add("Detected MountLang_en.txt under Saved/Resources/3.3.0, not the legacy Config/Android path.")
        }
        if (mountFolderMountLangFound) {
            notes.add("Video-confirmed candidate: Saved/Resources/3.3.0/Mount/MountLang_en.txt.")
        }
        if (langPakFound) {
            notes.add("Detected official Lang_en PAK/SIG layout under Saved/Resources/3.3.0/Lang_en.")
        }
        if (!legacyPakFolderFound) {
            notes.add("Legacy Content/Paks was not found. Do not force patch install until the real PAK target is confirmed.")
        }
        notes.add("Send this report before changing write paths for Android 3.3.2 layouts.")
        return notes
    }
}
