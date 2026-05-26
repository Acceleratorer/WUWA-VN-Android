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
        val serviceRef = AtomicReference<IWuwaBackupService?>()
        val connected = CountDownLatch(1)
        val componentName = ComponentName(context, WuwaBackupUserService::class.java)
        val args = Shizuku.UserServiceArgs(componentName)
            .daemon(false)
            .debuggable(false)
            .processNameSuffix("path_diag_backup")
            .tag("path_diag_backup")
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
                throw IllegalStateException("Timed out while connecting Shizuku backup diagnostic service.")
            }

            val service = serviceRef.get()
                ?: throw IllegalStateException("Shizuku backup diagnostic service did not connect.")

            GamePathDiagnosticReport(
                files = GamePathDiagnosticPaths.fileCandidates.map { readFileCandidate(service, it) },
                directories = GamePathDiagnosticPaths.directoryCandidates.map { readDirectoryCandidate(service, it) },
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
        const val CONNECT_TIMEOUT_SECONDS = 10L
        const val MAX_CHILD_NAMES = 40
    }
}
