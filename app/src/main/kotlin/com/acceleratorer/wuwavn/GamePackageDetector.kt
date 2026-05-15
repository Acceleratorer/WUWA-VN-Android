package com.acceleratorer.wuwavn

import android.content.Context
import android.content.pm.PackageManager

class GamePackageDetector {
    enum class State(val label: String) {
        GLOBAL_INSTALLED("Global game installed"),
        NON_GLOBAL_INSTALLED("Non-global game package detected"),
        NOT_INSTALLED("Game package not detected"),
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

    fun isPackageInstalled(context: Context, packageName: String): Boolean = try {
        context.packageManager.getPackageInfo(packageName, 0)
        true
    } catch (ignored: PackageManager.NameNotFoundException) {
        false
    }
}
