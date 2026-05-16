package com.acceleratorer.wuwavn

import android.content.Context
import android.os.Build
import android.content.pm.PackageManager

class GamePackageDetector {
    enum class State(val label: String) {
        GLOBAL_INSTALLED("Global game installed"),
        NON_GLOBAL_INSTALLED("Non-global game package detected"),
        NOT_INSTALLED("Game package not detected"),
    }

    data class GameInfo(
        val packageName: String,
        val versionName: String?,
        val versionCode: Long?,
    ) {
        val compatibilityLabel: String
            get() = when {
                versionName.isNullOrBlank() -> "package detected, version unknown"
                versionName.startsWith(AppConstants.SUPPORTED_GAME_VERSION) ->
                    "compatible with WUWA ${AppConstants.SUPPORTED_GAME_VERSION}"
                else -> "not confirmed for WUWA ${AppConstants.SUPPORTED_GAME_VERSION}"
            }
    }

    fun detect(context: Context): State {
        if (isPackageInstalled(context, AppConstants.GLOBAL_GAME_PACKAGE)) {
            return State.GLOBAL_INSTALLED
        }
        if (isPackageInstalled(context, AppConstants.CN_GAME_PACKAGE)) {
            return State.NON_GLOBAL_INSTALLED
        }
        return State.NOT_INSTALLED
    }

    fun detectGlobalInfo(context: Context): GameInfo? {
        val info = try {
            context.packageManager.getPackageInfo(AppConstants.GLOBAL_GAME_PACKAGE, 0)
        } catch (ignored: PackageManager.NameNotFoundException) {
            return null
        }

        val versionCode = if (Build.VERSION.SDK_INT >= 28) {
            info.longVersionCode
        } else {
            @Suppress("DEPRECATION")
            info.versionCode.toLong()
        }

        return GameInfo(
            packageName = info.packageName,
            versionName = info.versionName,
            versionCode = versionCode,
        )
    }

    fun isPackageInstalled(context: Context, packageName: String): Boolean = try {
        context.packageManager.getPackageInfo(packageName, 0)
        true
    } catch (ignored: PackageManager.NameNotFoundException) {
        false
    }
}
