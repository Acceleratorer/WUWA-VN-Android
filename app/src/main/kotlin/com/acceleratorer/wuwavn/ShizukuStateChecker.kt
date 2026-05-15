package com.acceleratorer.wuwavn

import android.content.Context
import android.content.pm.PackageManager
import rikka.shizuku.Shizuku

class ShizukuStateChecker(
    private val packageDetector: GamePackageDetector,
) {
    fun check(context: Context): ShizukuState {
        val installed = packageDetector.isPackageInstalled(context, AppConstants.SHIZUKU_PACKAGE)
        return try {
            if (!Shizuku.pingBinder()) {
                return if (installed) ShizukuState.INSTALLED_NOT_RUNNING else ShizukuState.NOT_INSTALLED
            }
            if (Shizuku.isPreV11()) {
                return ShizukuState.RUNNING_PERMISSION_DENIED
            }
            if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
                ShizukuState.READY
            } else {
                ShizukuState.RUNNING_PERMISSION_DENIED
            }
        } catch (ignored: Throwable) {
            if (installed) ShizukuState.INSTALLED_NOT_RUNNING else ShizukuState.NOT_INSTALLED
        }
    }

    fun requestPermissionIfPossible(context: Context): Boolean {
        if (check(context) == ShizukuState.READY) {
            return true
        }
        return try {
            if (Shizuku.pingBinder() && !Shizuku.isPreV11()) {
                Shizuku.requestPermission(REQUEST_CODE)
                true
            } else {
                false
            }
        } catch (ignored: Throwable) {
            false
        }
    }

    private companion object {
        const val REQUEST_CODE = 2100
    }
}
