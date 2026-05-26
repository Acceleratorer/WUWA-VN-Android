package com.acceleratorer.wuwavn

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.os.Environment
import android.os.IBinder
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import rikka.shizuku.Shizuku

class ShizukuGamePathDiagnosticReader {
    fun read(context: Context): GamePathDiagnosticReport {
        var lastError: String? = null
        repeat(BIND_ATTEMPTS) { attempt ->
            val report = readOnce(context, attempt + 1)
            if (report.error == null) {
                return report
            }
            lastError = report.error
            if (attempt < BIND_ATTEMPTS - 1) {
                Thread.sleep(RETRY_DELAY_MS)
            }
        }

        return readWithShellFallback(lastError)
    }

    private fun readOnce(
        context: Context,
        attempt: Int,
    ): GamePathDiagnosticReport {
        val serviceRef = AtomicReference<IWuwaBackupService?>()
        val connected = CountDownLatch(1)
        val componentName = ComponentName(context, WuwaBackupUserService::class.java)
        val args = Shizuku.UserServiceArgs(componentName)
            .daemon(false)
            .debuggable(false)
            .processNameSuffix("backup")
            .tag("backup")
            .version(AppConstants.VERSION_CODE)

        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, service: IBinder) {
                serviceRef.set(IWuwaBackupService.Stub.asInterface(service))
                connected.countDown()
            }

            override fun onServiceDisconnected(name: ComponentName) {
                serviceRef.set(null)
            }
        }

        return try {
            Shizuku.bindUserService(args, connection)
            if (!connected.await(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                throw IllegalStateException("Timed out while connecting Shizuku backup diagnostic service (attempt $attempt/$BIND_ATTEMPTS).")
            }

            val service = serviceRef.get()
                ?: throw IllegalStateException("Shizuku backup diagnostic service did not connect (attempt $attempt/$BIND_ATTEMPTS).")

            GamePathDiagnosticReport(
                files = GamePathDiagnosticPaths.fileCandidates.map { readFileCandidate(service, it) },
                directories = GamePathDiagnosticPaths.directoryCandidates.map { readDirectoryCandidate(service, it) },
                source = "Shizuku backup service",
            )
        } catch (exception: Exception) {
            GamePathDiagnosticReport(
                files = emptyList(),
                directories = emptyList(),
                error = exception.message ?: exception.javaClass.simpleName,
            )
        } finally {
            try {
                Shizuku.unbindUserService(args, connection, true)
            } catch (ignored: Throwable) {
            }
        }
    }

    private fun readWithShellFallback(serviceError: String?): GamePathDiagnosticReport =
        try {
            parseShellDiagnostic(runShizukuShell(buildShellDiagnosticScript()))
        } catch (exception: Exception) {
            val details = listOfNotNull(
                serviceError?.let { "Backup service: $it" },
                "Shell fallback: ${exception.message ?: exception.javaClass.simpleName}",
            ).joinToString("\n")

            GamePathDiagnosticReport(
                files = emptyList(),
                directories = emptyList(),
                error = details,
                source = "Shizuku diagnostic unavailable",
            )
        }

    private fun buildShellDiagnosticScript(): String = buildString {
        appendLine("echo __WUWA_DIAG_BEGIN__")
        GamePathDiagnosticPaths.fileCandidates.forEachIndexed { index, candidate ->
            appendLine("echo __WUWA_FILE__$index")
            appendLine("p=${shellQuote(gameAbsolutePath(candidate.relativePath))}")
            appendLine("if [ -e \"\$p\" ]; then echo exists=1; else echo exists=0; fi")
            appendLine("if [ -f \"\$p\" ]; then echo file=1; size=\$(wc -c < \"\$p\" 2>/dev/null | tr -d '[:space:]'); echo size=\$size; else echo file=0; fi")
            if (shouldHashAndPreview(candidate)) {
                appendLine("if [ -f \"\$p\" ]; then sha=\$(sha256sum \"\$p\" 2>/dev/null | awk '{print \$1}'); if [ -n \"\$sha\" ]; then echo sha256=\$sha; else echo sha256=unavailable; fi; fi")
                appendLine("if [ -f \"\$p\" ]; then head -n $MAX_PREVIEW_LINES \"\$p\" 2>/dev/null | sed 's/\\r//g' | sed 's/^/preview=/'; fi")
            }
        }
        GamePathDiagnosticPaths.directoryCandidates.forEachIndexed { index, candidate ->
            appendLine("echo __WUWA_DIR__$index")
            appendLine("p=${shellQuote(gameAbsolutePath(candidate.relativePath))}")
            appendLine("if [ -e \"\$p\" ]; then echo exists=1; else echo exists=0; fi")
            appendLine("if [ -d \"\$p\" ]; then echo dir=1; ls -1 \"\$p\" 2>/dev/null | head -n $MAX_CHILD_NAMES | sed 's/^/child=/'; else echo dir=0; fi")
        }
        appendLine("echo __WUWA_DIAG_END__")
    }

    private fun runShizukuShell(script: String): String {
        val process = startShizukuProcess(arrayOf("/system/bin/sh", "-c", script))
        val completed = waitForShizukuProcess(process)
        if (!completed) {
            process.destroy()
            throw IllegalStateException("Timed out while running Shizuku shell diagnostic.")
        }

        val stdout = process.inputStream.readBytes().toString(Charsets.UTF_8)
        val stderr = process.errorStream.readBytes().toString(Charsets.UTF_8).trim()
        if (stdout.contains("__WUWA_DIAG_BEGIN__") && stdout.contains("__WUWA_DIAG_END__")) {
            return stdout
        }

        val exitCode = runCatching { process.exitValue() }.getOrNull()
        if (exitCode != null && exitCode != 0 && stdout.isBlank()) {
            throw IllegalStateException("Shizuku shell diagnostic failed with exit code $exitCode: $stderr")
        }
        return stdout
    }

    private fun waitForShizukuProcess(process: Process): Boolean {
        val method = runCatching {
            process.javaClass.getMethod("waitForTimeout", java.lang.Long.TYPE, TimeUnit::class.java)
        }.getOrNull()

        if (method != null) {
            return method.invoke(process, SHELL_TIMEOUT_SECONDS, TimeUnit.SECONDS) as Boolean
        }

        return process.waitFor(SHELL_TIMEOUT_SECONDS, TimeUnit.SECONDS)
    }

    private fun startShizukuProcess(command: Array<String>): Process {
        val method = Shizuku::class.java.getDeclaredMethod(
            "newProcess",
            Array<String>::class.java,
            Array<String>::class.java,
            String::class.java,
        )
        method.isAccessible = true
        return method.invoke(null, *arrayOf<Any?>(command, null, null)) as Process
    }

    private fun parseShellDiagnostic(output: String): GamePathDiagnosticReport {
        if (!output.contains("__WUWA_DIAG_BEGIN__")) {
            throw IllegalStateException("Shizuku shell diagnostic returned no marker output.")
        }

        val blocks = mutableMapOf<String, MutableList<String>>()
        var currentKey: String? = null
        for (rawLine in output.lineSequence()) {
            val line = rawLine.trimEnd('\r')
            when {
                line.startsWith("__WUWA_FILE__") || line.startsWith("__WUWA_DIR__") -> {
                    currentKey = line
                    blocks.getOrPut(line) { mutableListOf() }
                }
                line == "__WUWA_DIAG_BEGIN__" || line == "__WUWA_DIAG_END__" -> {
                    currentKey = null
                }
                currentKey != null -> {
                    blocks.getOrPut(currentKey) { mutableListOf() }.add(line)
                }
            }
        }

        return GamePathDiagnosticReport(
            files = GamePathDiagnosticPaths.fileCandidates.mapIndexed { index, candidate ->
                parseShellFileCandidate(candidate, blocks["__WUWA_FILE__$index"].orEmpty())
            },
            directories = GamePathDiagnosticPaths.directoryCandidates.mapIndexed { index, candidate ->
                parseShellDirectoryCandidate(candidate, blocks["__WUWA_DIR__$index"].orEmpty())
            },
            source = "Shizuku shell fallback",
        )
    }

    private fun parseShellFileCandidate(
        candidate: GamePathCandidate,
        lines: List<String>,
    ): GamePathFileResult {
        if (lines.isEmpty()) {
            return GamePathFileResult(candidate, exists = false, isFile = false, sizeBytes = null, error = "No shell output.")
        }

        val exists = shellValue(lines, "exists") == "1"
        val isFile = shellValue(lines, "file") == "1"
        val sizeBytes = if (isFile) shellValue(lines, "size")?.toLongOrNull() else null
        val sha256 = shellValue(lines, "sha256")
            ?.takeIf { SHA_256_REGEX.matches(it) }
            ?.lowercase()
        val previewLines = lines.filter { it.startsWith("preview=") }
            .map { it.removePrefix("preview=") }
            .take(MAX_PREVIEW_LINES)
        return GamePathFileResult(candidate, exists, isFile, sizeBytes, sha256 = sha256, previewLines = previewLines)
    }

    private fun parseShellDirectoryCandidate(
        candidate: GamePathCandidate,
        lines: List<String>,
    ): GamePathDirectoryResult {
        if (lines.isEmpty()) {
            return GamePathDirectoryResult(candidate, exists = false, isDirectory = false, childNames = emptyList(), error = "No shell output.")
        }

        val exists = shellValue(lines, "exists") == "1"
        val isDirectory = shellValue(lines, "dir") == "1"
        val children = if (isDirectory) {
            lines.filter { it.startsWith("child=") }
                .map { it.removePrefix("child=") }
                .take(MAX_CHILD_NAMES)
        } else {
            emptyList()
        }
        return GamePathDirectoryResult(candidate, exists, isDirectory, children)
    }

    private fun shellValue(lines: List<String>, key: String): String? =
        lines.firstOrNull { it.startsWith("$key=") }?.substringAfter("=")

    private fun shouldHashAndPreview(candidate: GamePathCandidate): Boolean =
        candidate.label.startsWith("MountLang")

    private fun shellQuote(value: String): String = "'" + value.replace("'", "'\"'\"'") + "'"

    private fun readFileCandidate(
        service: IWuwaBackupService,
        candidate: GamePathCandidate,
    ): GamePathFileResult {
        val absolutePath = gameAbsolutePath(candidate.relativePath)
        return try {
            val exists = service.pathExists(absolutePath)
            val isFile = service.isFile(absolutePath)
            val sizeBytes = if (isFile) service.length(absolutePath) else null
            GamePathFileResult(candidate, exists, isFile, sizeBytes)
        } catch (exception: Exception) {
            GamePathFileResult(candidate, exists = false, isFile = false, sizeBytes = null, error = exception.message)
        }
    }

    private fun readDirectoryCandidate(
        service: IWuwaBackupService,
        candidate: GamePathCandidate,
    ): GamePathDirectoryResult {
        val absolutePath = gameAbsolutePath(candidate.relativePath)
        return try {
            val exists = service.pathExists(absolutePath)
            val isDirectory = service.isDirectory(absolutePath)
            val children = if (isDirectory) {
                service.listChildNames(absolutePath, MAX_CHILD_NAMES).toList()
            } else {
                emptyList()
            }
            GamePathDirectoryResult(candidate, exists, isDirectory, children)
        } catch (exception: Exception) {
            GamePathDirectoryResult(candidate, exists = false, isDirectory = false, childNames = emptyList(), error = exception.message)
        }
    }

    private fun gameAbsolutePath(relativePath: String): String {
        if (!GamePathDiagnosticPaths.isAllowed(relativePath)) {
            throw SecurityException("Blocked unsafe diagnostic target: $relativePath")
        }
        return Environment.getExternalStorageDirectory().absolutePath +
            "/Android/data/" +
            AppConstants.GLOBAL_GAME_PACKAGE +
            "/files/" +
            relativePath
    }

    private companion object {
        const val BIND_ATTEMPTS = 2
        const val CONNECT_TIMEOUT_SECONDS = 15L
        const val SHELL_TIMEOUT_SECONDS = 12L
        const val MAX_CHILD_NAMES = 40
        const val MAX_PREVIEW_LINES = 8
        const val RETRY_DELAY_MS = 750L
        val SHA_256_REGEX = Regex("^[0-9a-fA-F]{64}$")
    }
}
