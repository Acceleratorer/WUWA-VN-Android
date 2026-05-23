package com.acceleratorer.wuwavn

import java.io.IOException
import java.util.concurrent.TimeUnit

class RootAccessChecker {
    fun check(): RootAccessState {
        val process = try {
            ProcessBuilder("su", "-c", "id")
                .redirectErrorStream(true)
                .start()
        } catch (exception: IOException) {
            return RootAccessState.NOT_AVAILABLE
        } catch (exception: SecurityException) {
            return RootAccessState.CHECK_FAILED
        }

        return try {
            if (!process.waitFor(ROOT_CHECK_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                process.destroy()
                RootAccessState.DENIED
            } else {
                val output = process.inputStream.bufferedReader().use { it.readText() }
                if (process.exitValue() == 0 && output.contains("uid=0")) {
                    RootAccessState.AVAILABLE
                } else {
                    RootAccessState.DENIED
                }
            }
        } catch (exception: InterruptedException) {
            Thread.currentThread().interrupt()
            RootAccessState.CHECK_FAILED
        } catch (exception: IOException) {
            RootAccessState.CHECK_FAILED
        } finally {
            runCatching { process.inputStream.close() }
            runCatching { process.errorStream.close() }
            runCatching { process.outputStream.close() }
            process.destroy()
        }
    }

    private companion object {
        const val ROOT_CHECK_TIMEOUT_SECONDS = 8L
    }
}
