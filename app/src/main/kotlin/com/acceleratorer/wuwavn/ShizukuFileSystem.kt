package com.acceleratorer.wuwavn

class ShizukuFileSystem {
    fun isWriteEnabled(state: ShizukuState): Boolean = false

    fun disabledReason(state: ShizukuState): String {
        if (state != ShizukuState.READY) {
            return "Shizuku is not ready yet."
        }
        return "Shizuku file writing is intentionally locked until backup and restore are tested on a real device."
    }
}
