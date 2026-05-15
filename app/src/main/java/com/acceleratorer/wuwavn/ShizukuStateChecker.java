package com.acceleratorer.wuwavn;

import android.content.Context;
import android.content.pm.PackageManager;

import rikka.shizuku.Shizuku;

final class ShizukuStateChecker {
    private static final int REQUEST_CODE = 2100;

    private final GamePackageDetector packageDetector;

    ShizukuStateChecker(GamePackageDetector packageDetector) {
        this.packageDetector = packageDetector;
    }

    ShizukuState check(Context context) {
        boolean installed = packageDetector.isPackageInstalled(context, AppConstants.SHIZUKU_PACKAGE);
        try {
            if (!Shizuku.pingBinder()) {
                return installed ? ShizukuState.INSTALLED_NOT_RUNNING : ShizukuState.NOT_INSTALLED;
            }
            if (Shizuku.isPreV11()) {
                return ShizukuState.RUNNING_PERMISSION_DENIED;
            }
            return Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
                    ? ShizukuState.READY
                    : ShizukuState.RUNNING_PERMISSION_DENIED;
        } catch (Throwable ignored) {
            return installed ? ShizukuState.INSTALLED_NOT_RUNNING : ShizukuState.NOT_INSTALLED;
        }
    }

    boolean requestPermissionIfPossible(Context context) {
        if (check(context) == ShizukuState.READY) {
            return true;
        }
        try {
            if (Shizuku.pingBinder() && !Shizuku.isPreV11()) {
                Shizuku.requestPermission(REQUEST_CODE);
                return true;
            }
        } catch (Throwable ignored) {
            return false;
        }
        return false;
    }
}
