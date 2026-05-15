package com.acceleratorer.wuwavn;

import android.text.TextUtils;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

final class DebugLogger {
    interface Listener {
        void onLogChanged(String text);
    }

    private final List<String> lines = new ArrayList<>();
    private Listener listener;

    void setListener(Listener listener) {
        this.listener = listener;
        notifyChanged();
    }

    void add(String message) {
        String time = new SimpleDateFormat("HH:mm:ss", Locale.US).format(new Date());
        lines.add("[" + time + "] " + message);
        notifyChanged();
    }

    String text() {
        return TextUtils.join("\n", lines);
    }

    private void notifyChanged() {
        if (listener != null) {
            listener.onLogChanged(text());
        }
    }
}
