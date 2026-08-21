package com.acceleratorer.wuwavn

import android.content.Context

class TrustedBackupFinder(
    private val restoreDryRunPlanner: RestoreDryRunPlanner,
) {
    fun find(
        context: Context,
        predicate: (RestoreDryRun) -> Boolean = { true },
    ): RestoreDryRun? {
        for (session in restoreDryRunPlanner.listBackupSessions(context)) {
            val dryRun = try {
                restoreDryRunPlanner.plan(session)
            } catch (exception: Exception) {
                null
            } ?: continue

            if (TrustedBackupPolicy.isTrustedBackup(dryRun) && predicate(dryRun)) {
                return dryRun
            }
        }
        return null
    }
}
