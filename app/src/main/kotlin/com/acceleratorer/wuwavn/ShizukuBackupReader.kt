package com.acceleratorer.wuwavn

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.os.Environment
import android.os.IBinder
import java.io.File
import java.util.Base64
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import rikka.shizuku.Shizuku

class ShizukuBackupReader(
    private val backupManager: BackupManager,
) {
    fun backupConfigFiles(
        context: Context,
        backupDirectory: File,
        logger: DebugLogger,
    ): BackupResult = try {
        backupWithUserService(context, backupDirectory, logger)
    } catch (exception: Exception) {
        logger.add("Backup user service failed: ${exception.message}")
        logger.add("Backup shell fallback: started")
        backupWithShellFallback(context, backupDirectory, logger, exception.message)
    }

    private fun backupWithUserService(
        context: Context,
        backupDirectory: File,
        logger: DebugLogger,
    ): BackupResult {
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

        try {
            Shizuku.bindUserService(args, connection)
            if (!connected.await(15, TimeUnit.SECONDS)) {
                throw IllegalStateException("Timed out while connecting Shizuku backup service.")
            }

            val service = serviceRef.get()
                ?: throw IllegalStateException("Shizuku backup service did not connect.")

            val backedUpFiles = mutableListOf<BackupFileInfo>()
            val missingFiles = mutableListOf<String>()
            val dynamicMountLang = service.wuwa36MountLangRelativePath(AppConstants.SUPPORTED_GAME_VERSION)
                ?: throw IllegalStateException("WUWA 3.6 Resources MountLang was not resolved.")
            for (relativePath in PatchDryRunPlanner.backupReadableRelativePaths(dynamicMountLang)) {
                val absolutePath = gameAbsolutePath(relativePath)
                val displayName = PatchDryRunPlanner.backupDisplayName(relativePath)
                if (!service.exists(absolutePath)) {
                    logger.add("Backup read: missing $displayName")
                    missingFiles.add(relativePath)
                    continue
                }

                val length = service.length(absolutePath)
                if (length > MAX_CONFIG_BYTES) {
                    throw IllegalStateException("$displayName is too large for safe read-only backup.")
                }

                val bytes = service.readFile(absolutePath, MAX_CONFIG_BYTES)
                val info = backupManager.writeBackedUpFile(backupDirectory, displayName, relativePath, bytes)
                backedUpFiles.add(info)
                logger.add("Backup read: copied $displayName (${info.sizeBytes} bytes, sha256 ${info.sha256.take(12)}...)")
            }

            if (backedUpFiles.isEmpty()) {
                throw IllegalStateException("No allowlisted WUWA config files were backed up.")
            }

            return BackupResult(backedUpFiles, missingFiles, source = "Shizuku backup service")
        } finally {
            try {
                Shizuku.unbindUserService(args, connection, true)
            } catch (ignored: Throwable) {
            }
        }
    }

    private fun backupWithShellFallback(
        context: Context,
        backupDirectory: File,
        logger: DebugLogger,
        serviceError: String?,
    ): BackupResult {
        val dynamicMountLang = resolveDynamicMountLangWithShell()
        if (dynamicMountLang == null) {
            throw IllegalStateException("WUWA 3.6 Resources MountLang was not resolved.")
        }
        val readablePaths = PatchDryRunPlanner.backupReadableRelativePaths(dynamicMountLang)
        val output = runShizukuShell(buildShellBackupScript(readablePaths))
        val blocks = parseShellBlocks(output)
        val backedUpFiles = mutableListOf<BackupFileInfo>()
        val missingFiles = mutableListOf<String>()

        for ((index, relativePath) in readablePaths.withIndex()) {
            val displayName = PatchDryRunPlanner.backupDisplayName(relativePath)
            val lines = blocks["__WUWA_BACKUP_FILE__$index"].orEmpty()
            if (lines.isEmpty()) {
                logger.add("Backup shell fallback: no output for $displayName")
                missingFiles.add(relativePath)
                continue
            }

            val exists = shellValue(lines, "exists") == "1"
            if (!exists) {
                logger.add("Backup shell fallback: missing $displayName")
                missingFiles.add(relativePath)
                continue
            }

            val size = shellValue(lines, "size")?.toLongOrNull()
                ?: throw IllegalStateException("$displayName size is not available from shell fallback.")
            if (size > MAX_CONFIG_BYTES) {
                throw IllegalStateException("$displayName is too large for safe read-only backup.")
            }

            val encoded = contentBase64(lines)
                ?: throw IllegalStateException("$displayName content was not returned by shell fallback.")
            val bytes = try {
                Base64.getMimeDecoder().decode(encoded)
            } catch (exception: Exception) {
                throw IllegalStateException("$displayName shell fallback decode failed: ${exception.message}")
            }
            if (bytes.size.toLong() != size) {
                throw IllegalStateException("$displayName shell fallback size mismatch.")
            }

            val info = backupManager.writeBackedUpFile(backupDirectory, displayName, relativePath, bytes)
            backedUpFiles.add(info)
            logger.add("Backup shell fallback: copied $displayName (${info.sizeBytes} bytes, sha256 ${info.sha256.take(12)}...)")
        }

        if (backedUpFiles.isEmpty()) {
            val details = serviceError?.let { " User service error: $it" }.orEmpty()
            throw IllegalStateException("No allowlisted WUWA config files were backed up.$details")
        }

        return BackupResult(backedUpFiles, missingFiles, source = "Shizuku shell fallback")
    }

    private fun buildShellBackupScript(readablePaths: List<String>): String = buildString {
        appendLine("echo __WUWA_BACKUP_BEGIN__")
        readablePaths.forEachIndexed { index, relativePath ->
            appendLine("echo __WUWA_BACKUP_FILE__$index")
            appendLine("p=${shellQuote(gameAbsolutePath(relativePath))}")
            appendLine("if [ -f \"\$p\" ]; then")
            appendLine("  echo exists=1")
            appendLine("  size=\$(wc -c < \"\$p\" 2>/dev/null | tr -d '[:space:]')")
            appendLine("  echo size=\$size")
            appendLine("  if [ -n \"\$size\" ] && [ \"\$size\" -le $MAX_CONFIG_BYTES ]; then")
            appendLine("    echo __WUWA_CONTENT_BEGIN__")
            appendLine("    base64 \"\$p\" 2>/dev/null")
            appendLine("    echo __WUWA_CONTENT_END__")
            appendLine("  fi")
            appendLine("else")
            appendLine("  echo exists=0")
            appendLine("fi")
        }
        appendLine("echo __WUWA_BACKUP_END__")
    }

    private fun resolveDynamicMountLangWithShell(): String? {
        val root = gameAbsolutePath("UE4Game/Client/Client/Saved/Resources")
        val script = "for d in ${shellQuote(root)}/3.6.*; do [ -d \"\$d/ResManifest\" ] && basename \"\$d\"; done"
        val versions = runShizukuShellRaw(script).lineSequence().map(String::trim)
            .filter { it.matches(Regex("^3\\.6\\.\\d+$")) }.toList()
        val resourceVersion = WuWa36Layout.resolveResourceVersion(
            AppConstants.SUPPORTED_GAME_VERSION,
            versions,
            versions,
        ) ?: return null
        return WuWa36Layout.mountLangPath(resourceVersion)
    }

    private fun runShizukuShellRaw(script: String): String {
        val process = startShizukuProcess(arrayOf("/system/bin/sh", "-c", script))
        val reader = ThreadedProcessOutput(process)
        if (!waitForShizukuProcess(process)) {
            process.destroy()
            throw IllegalStateException("Timed out while resolving WUWA 3.6 Resources layout.")
        }
        return reader.stdout()
    }

    private fun runShizukuShell(script: String): String {
        val process = startShizukuProcess(arrayOf("/system/bin/sh", "-c", script))
        val output = ThreadedProcessOutput(process)
        val completed = waitForShizukuProcess(process)
        if (!completed) {
            process.destroy()
            throw IllegalStateException("Timed out while running Shizuku backup shell fallback.")
        }

        val stdout = output.stdout()
        val stderr = output.stderr().trim()
        if (stdout.contains("__WUWA_BACKUP_BEGIN__") && stdout.contains("__WUWA_BACKUP_END__")) {
            return stdout
        }

        val exitCode = runCatching { process.exitValue() }.getOrNull()
        if (exitCode != null && exitCode != 0 && stdout.isBlank()) {
            throw IllegalStateException("Shizuku backup shell fallback failed with exit code $exitCode: $stderr")
        }
        throw IllegalStateException("Shizuku backup shell fallback returned no marker output.")
    }

    private fun waitForShizukuProcess(process: Process): Boolean {
        val method = runCatching {
            process.javaClass.getMethod("waitForTimeout", java.lang.Long.TYPE, TimeUnit::class.java)
        }.getOrNull()

        if (method != null) {
            return method.invoke(process, SHELL_TIMEOUT_SECONDS, TimeUnit.SECONDS) as? Boolean ?: false
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

    private class ThreadedProcessOutput(private val process: Process) {
        private val stdoutThread: Thread
        private val stderrThread: Thread
        @Volatile private var stdoutBytes = ByteArray(0)
        @Volatile private var stderrBytes = ByteArray(0)

        init {
            stdoutThread = Thread { stdoutBytes = process.inputStream.readBytes() }.apply { start() }
            stderrThread = Thread { stderrBytes = process.errorStream.readBytes() }.apply { start() }
        }

        fun stdout(): String {
            stdoutThread.join(2_000)
            return stdoutBytes.toString(Charsets.UTF_8)
        }

        fun stderr(): String {
            stderrThread.join(2_000)
            return stderrBytes.toString(Charsets.UTF_8)
        }
    }

    private fun parseShellBlocks(output: String): Map<String, List<String>> {
        val blocks = mutableMapOf<String, MutableList<String>>()
        var currentKey: String? = null
        for (rawLine in output.lineSequence()) {
            val line = rawLine.trimEnd('\r')
            when {
                line.startsWith("__WUWA_BACKUP_FILE__") -> {
                    currentKey = line
                    blocks.getOrPut(line) { mutableListOf() }
                }
                line == "__WUWA_BACKUP_BEGIN__" || line == "__WUWA_BACKUP_END__" -> {
                    currentKey = null
                }
                currentKey != null -> {
                    blocks.getOrPut(currentKey) { mutableListOf() }.add(line)
                }
            }
        }
        return blocks
    }

    private fun shellValue(lines: List<String>, key: String): String? =
        lines.firstOrNull { it.startsWith("$key=") }?.substringAfter("=")

    private fun contentBase64(lines: List<String>): String? {
        val start = lines.indexOf("__WUWA_CONTENT_BEGIN__")
        val end = lines.indexOf("__WUWA_CONTENT_END__")
        if (start < 0 || end <= start) {
            return null
        }
        return lines.subList(start + 1, end).joinToString("")
    }

    private fun shellQuote(value: String): String = "'" + value.replace("'", "'\"'\"'") + "'"

    private fun gameAbsolutePath(relativePath: String): String =
        Environment.getExternalStorageDirectory().absolutePath +
            "/Android/data/" +
            AppConstants.GLOBAL_GAME_PACKAGE +
            "/files/" +
            relativePath

    data class BackupResult(
        val backedUpFiles: List<BackupFileInfo>,
        val missingFiles: List<String>,
        val source: String,
    ) {
        fun isTrustedForWriteActions(backupDirectory: File): Boolean {
            val backedUp = backedUpFiles.map { it.relativePath }.toSet()
            val engine = PatchDryRunPlanner.engineIniRelativePath()
            val device = PatchDryRunPlanner.deviceProfilesRelativePath()
            val mountPath = backedUp.firstOrNull(WuWa36Layout::isMountLangPath)
            if (backedUpFiles.size != 3 || engine !in backedUp || device !in backedUp || mountPath == null) {
                return false
            }
            val mountFile = File(backupDirectory, PatchDryRunPlanner.backupDisplayName(mountPath))
            return mountFile.isFile &&
                mountFile.length() <= MAX_CONFIG_BYTES &&
                runCatching {
                    WuWa36Layout.isOriginalMountLang(mountFile.readText(Charsets.UTF_8))
                }.getOrDefault(false)
        }
    }

    private companion object {
        const val MAX_CONFIG_BYTES = 512 * 1024
        const val SHELL_TIMEOUT_SECONDS = 12L
    }
}
