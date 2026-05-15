package com.acceleratorer.wuwavn;

import android.content.Context;
import android.content.pm.PackageManager;

final class GamePackageDetector {
    enum State {
        GLOBAL_INSTALLED("Global game installed"),
        NON_GLOBAL_INSTALLED("Non-global game package detected"),
        NOT_INSTALLED("Game package not detected");

        private final String label;

        State(String label) {
            this.label = label;
        }

        String label() {
            return label;
        }
    }

    State detect(Context context) {
        if (isPackageInstalled(context, AppConstants.GLOBAL_GAME_PACKAGE)) {
            return State.GLOBAL_INSTALLED;
        }
        if (isPackageInstalled(context, AppConstants.CN_GAME_PACKAGE)) {
            return State.NON_GLOBAL_INSTALLED;
        }
        return State.NOT_INSTALLED;
    }

    boolean isPackageInstalled(Context context, String packageName) {
        try {
            context.getPackageManager().getPackageInfo(packageName, 0);
            return true;
        } catch (PackageManager.NameNotFoundException ignored) {
            return false;
        }
    }
}
