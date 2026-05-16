package com.acceleratorer.wuwavn

class ShizukuFileSystem {
    fun isRestoreWriteEnabled(state: ShizukuState): Boolean = state == ShizukuState.READY

    fun patchWriteStatus(state: ShizukuState): String =
        if (state == ShizukuState.READY) {
            "PAK-only gated"
        } else {
            "locked"
        }

    fun configPresetWriteStatus(state: ShizukuState): String =
        if (state == ShizukuState.READY) {
            "Safe/Balanced gated"
        } else {
            "locked"
        }

    fun disabledReason(state: ShizukuState): String {
        if (state != ShizukuState.READY) {
            return "Shizuku is not ready yet."
        }
        return "Patch writing is available only through the PAK-only verified install flow. Config preset writing is available only through Safe / Default and Balanced verified flows."
    }
}
