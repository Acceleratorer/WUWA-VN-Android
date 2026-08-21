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

class ShizukuInstalledStateReader {
    fun read(context: Context): ReadResult {
        val diagnostics = mutableListOf<String>()
        val snapshot = readSnapshot(context, diagnostics)
        val configFiles = readConfigFiles(context, diagnostics)

        return ReadResult(
            snapshot = snapshot,
            engineIni = configFiles[PatchDryRunPlanner.engineIniRelativePath()],
            deviceProfilesIni = configFiles[PatchDryRunPlanner.deviceProfilesRelativePath()],
            diagnostics = diagnostics,
        )
    }

    private fun readSnapshot(
        context: Context,
        diagnostics: MutableList<String>,
    ): WuWa36Snapshot? = withPatchService(context) { service ->
        val json = service.wuwa36Snapshot(AppConstants.SUPPORTED_GAME_VERSION)
        if (!org.json.JSONObject(json).optBoolean("ready")) {
            null
        } else {
            WuWa36Snapshot.fromJson(json)
        }
    }.onFailure { diagnostics.add("WUWA 3.6 snapshot read failed: ${it.message}") }
        .getOrNull()

    private fun readConfigFiles(
        context: Context,
        diagnostics: MutableList<String>,
    ): Map<String, String?> {
        val values = mutableMapOf<String, String?>()
        val result = withBackupService(context) { service ->
            for (relativePath in listOf(
                PatchDryRunPlanner.engineIniRelativePath(),
                PatchDryRunPlanner.deviceProfilesRelativePath(),
            )) {
                values[relativePath] = readTextIfExists(service, relativePath, diagnostics)
            }
        }
        result.onFailure { exception ->
            diagnostics.add("Config read failed: ${exception.message}")
            for (relativePath in listOf(
                PatchDryRunPlanner.engineIniRelativePath(),
                PatchDryRunPlanner.deviceProfilesRelativePath(),
            )) {
                values.putIfAbsent(relativePath, null)
            }
        }
        return values
    }

    private fun readTextIfExists(
        service: IWuwaBackupService,
        relativePath: String,
        diagnostics: MutableList<String>,
    ): String? {
        val absolutePath = gameAbsolutePath(relativePath)
        val displayName = PatchDryRunPlanner.displayName(relativePath)
        return try {
            if (!service.exists(absolutePath)) {
                diagnostics.add("$displayName: missing")
                null
            } else {
                String(service.readFile(absolutePath, MAX_CONFIG_BYTES), Charsets.UTF_8)
            }
        } catch (exception: Exception) {
            diagnostics.add("$displayName: unreadable - ${exception.message}")
            null
        }
    }

    private fun <T> withBackupService(
        context: Context,
        block: (IWuwaBackupService) -> T,
    ): Result<T> = runCatching {
        val serviceRef = AtomicReference<IWuwaBackupService?>()
        val componentName = ComponentName(context, WuwaBackupUserService::class.java)
        val args = Shizuku.UserServiceArgs(componentName)
            .daemon(false)
            .debuggable(false)
            .processNameSuffix("state_backup")
            .tag("state_backup")
            .version(AppConstants.VERSION_CODE)
        val connection = serviceConnection(serviceRef) { IWuwaBackupService.Stub.asInterface(it) }
        try {
            Shizuku.bindUserService(args, connection.listener)
            if (!connection.connected.await(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                throw IllegalStateException("Timed out while connecting Shizuku state backup service.")
            }
            block(serviceRef.get() ?: throw IllegalStateException("Shizuku state backup service did not connect."))
        } finally {
            try {
                Shizuku.unbindUserService(args, connection.listener, true)
            } catch (ignored: Throwable) {
            }
        }
    }

    private fun <T> withPatchService(
        context: Context,
        block: (IWuwaPatchService) -> T,
    ): Result<T> = runCatching {
        val serviceRef = AtomicReference<IWuwaPatchService?>()
        val componentName = ComponentName(context, WuwaPatchUserService::class.java)
        val args = Shizuku.UserServiceArgs(componentName)
            .daemon(false)
            .debuggable(false)
            .processNameSuffix("state_patch")
            .tag("state_patch")
            .version(AppConstants.VERSION_CODE)
        val connection = serviceConnection(serviceRef) { IWuwaPatchService.Stub.asInterface(it) }
        try {
            Shizuku.bindUserService(args, connection.listener)
            if (!connection.connected.await(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                throw IllegalStateException("Timed out while connecting Shizuku state patch service.")
            }
            block(serviceRef.get() ?: throw IllegalStateException("Shizuku state patch service did not connect."))
        } finally {
            try {
                Shizuku.unbindUserService(args, connection.listener, true)
            } catch (ignored: Throwable) {
            }
        }
    }

    private fun <T> serviceConnection(
        serviceRef: AtomicReference<T?>,
        mapper: (IBinder) -> T,
    ): BoundConnection<T> {
        val connected = CountDownLatch(1)
        val listener = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, service: IBinder) {
                serviceRef.set(mapper(service))
                connected.countDown()
            }

            override fun onServiceDisconnected(name: ComponentName) {
                serviceRef.set(null)
            }
        }
        return BoundConnection(listener, connected)
    }

    private fun gameAbsolutePath(relativePath: String): String {
        if (!PatchDryRunPlanner.isAllowedTarget(relativePath)) {
            throw SecurityException("Blocked unsafe state read target: $relativePath")
        }
        return Environment.getExternalStorageDirectory().absolutePath +
            "/Android/data/" +
            AppConstants.GLOBAL_GAME_PACKAGE +
            "/files/" +
            relativePath
    }

    data class ReadResult(
        val snapshot: WuWa36Snapshot?,
        val engineIni: String?,
        val deviceProfilesIni: String?,
        val diagnostics: List<String>,
    )

    private data class BoundConnection<T>(
        val listener: ServiceConnection,
        val connected: CountDownLatch,
    )

    private companion object {
        const val CONNECT_TIMEOUT_SECONDS = 5L
        const val MAX_CONFIG_BYTES = 512 * 1024
    }
}
