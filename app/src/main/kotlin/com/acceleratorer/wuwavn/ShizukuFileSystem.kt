package com.acceleratorer.wuwavn

class ShizukuFileSystem {
    fun isRestoreWriteEnabled(state: ShizukuState): Boolean = state == ShizukuState.READY

    fun isPatchWriteEnabled(): Boolean = false

    fun disabledReason(state: ShizukuState): String {
        if (state != ShizukuState.READY) {
            return "Shizuku is not ready yet."
        }
        return "Patch writing is intentionally locked until restore writing is tested on a real device."
    }
}
