package com.acceleratorer.wuwavn;

final class ShizukuFileSystem {
    boolean isWriteEnabled(ShizukuState state) {
        return false;
    }

    String disabledReason(ShizukuState state) {
        if (state != ShizukuState.READY) {
            return "Shizuku is not ready yet.";
        }
        return "Shizuku file writing is intentionally locked until backup and restore are tested on a real device.";
    }
}
