package com.acceleratorer.wuwavn;

import android.content.Context;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

final class BackupManager {
    File planBackupSession(Context context) {
        File root = context.getExternalFilesDir("WUWA-VH-Backup");
        if (root == null) {
            root = new File(context.getFilesDir(), "WUWA-VH-Backup");
        }
        String timestamp = new SimpleDateFormat("yyyy-MM-dd_HH-mm", Locale.US).format(new Date());
        return new File(root, timestamp);
    }

    File createBackupSession(Context context) {
        File session = planBackupSession(context);
        if (!session.exists() && !session.mkdirs()) {
            throw new IllegalStateException("Could not create backup directory: " + session.getAbsolutePath());
        }
        return session;
    }
}
