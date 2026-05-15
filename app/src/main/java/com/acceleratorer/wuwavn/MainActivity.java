package com.acceleratorer.wuwavn;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MainActivity extends Activity {
    private static final String GLOBAL_PACKAGE = "com.kurogame.wutheringwaves.global";
    private static final String CN_PACKAGE = "com.kurogame.wutheringwaves";
    private static final String SHIZUKU_PACKAGE = "moe.shizuku.privileged.api";
    private static final String RELEASES_URL = "https://github.com/Acceleratorer/WUWA-VN-Android/releases";

    private final List<String> logLines = new ArrayList<>();
    private TextView statusView;
    private TextView logView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(Color.rgb(11, 17, 29));
        getWindow().setNavigationBarColor(Color.rgb(11, 17, 29));

        setContentView(createContentView());
        addLog("App version: 2.0.0");
        addLog("Android version: " + Build.VERSION.RELEASE);
        refreshStatus();
    }

    private View createContentView() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(Color.rgb(11, 17, 29));

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(24), dp(20), dp(24));
        scroll.addView(root, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT,
                ScrollView.LayoutParams.WRAP_CONTENT
        ));

        TextView title = text("WUWA Việt Hoá Android", 26, Color.WHITE, true);
        root.addView(title);

        TextView subtitle = text("Safe patch manager for Vietnamese Wuthering Waves players.", 14, Color.rgb(182, 193, 211), false);
        subtitle.setPadding(0, dp(8), 0, dp(16));
        root.addView(subtitle);

        statusView = text("", 15, Color.WHITE, false);
        statusView.setPadding(dp(14), dp(14), dp(14), dp(14));
        statusView.setBackgroundColor(Color.rgb(22, 32, 49));
        root.addView(statusView, matchWrap());

        root.addView(space(14));
        root.addView(primaryButton("Install Vietnamese Patch", new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                showPatchDryRun();
            }
        }));
        root.addView(button("Update Vietnamese Patch", new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                openUrl(RELEASES_URL);
                addLog("Update check: opened GitHub Releases");
            }
        }));
        root.addView(button("Restore Original Files", new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                showMessage("Restore Original Files", "No local backup is bundled in this clean build yet. Future builds should list backups from Download/WUWA-VH-Backup and restore only allowlisted WUWA files.");
                addLog("Restore: backup browser not available in this build");
            }
        }));
        root.addView(button("Check Game Folder", new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                refreshStatus();
                addLog("Game folder: checked package state");
            }
        }));
        root.addView(button("Open Shizuku", new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                openShizuku();
            }
        }));

        root.addView(space(18));
        TextView safetyTitle = text("Safety rules", 18, Color.WHITE, true);
        root.addView(safetyTitle);
        TextView safety = text(
                "Only known WUWA targets should be changed:\n" +
                        "- Engine.ini\n" +
                        "- DeviceProfiles.ini\n" +
                        "- MountLang_en.txt\n" +
                        "- WuWaVH_99_P.pak\n\n" +
                        "Always backup first. Never use this app for cheating, anti-cheat bypass, or gameplay manipulation.",
                14,
                Color.rgb(198, 207, 220),
                false
        );
        safety.setPadding(0, dp(8), 0, dp(12));
        root.addView(safety);

        TextView logTitle = text("Debug log", 18, Color.WHITE, true);
        root.addView(logTitle);
        logView = text("", 12, Color.rgb(209, 218, 230), false);
        logView.setTypeface(Typeface.MONOSPACE);
        logView.setPadding(dp(12), dp(12), dp(12), dp(12));
        logView.setBackgroundColor(Color.rgb(7, 12, 21));
        root.addView(logView, matchWrap());

        root.addView(button("Copy Debug Log", new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                copyLog();
            }
        }));
        root.addView(button("Send Issue Report", new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                shareLog();
            }
        }));

        return scroll;
    }

    private void refreshStatus() {
        boolean globalInstalled = isPackageInstalled(GLOBAL_PACKAGE);
        boolean cnInstalled = isPackageInstalled(CN_PACKAGE);
        boolean shizukuInstalled = isPackageInstalled(SHIZUKU_PACKAGE);

        String gameState = globalInstalled ? "Global game installed" : (cnInstalled ? "Non-global game package detected" : "Game package not detected");
        String shizukuState = shizukuInstalled ? "Shizuku installed" : "Shizuku not installed";

        statusView.setText(
                "Status\n" +
                        "Game: " + gameState + "\n" +
                        "Shizuku: " + shizukuState + "\n" +
                        "Mode: Safe / Default\n" +
                        "Patch flow: dry-run first, backup required"
        );
    }

    private void showPatchDryRun() {
        addLog("Dry run: started");
        String message =
                "Files to add:\n" +
                        "- WuWaVH_99_P.pak\n\n" +
                        "Files to modify:\n" +
                        "- Engine.ini\n" +
                        "- DeviceProfiles.ini\n" +
                        "- MountLang_en.txt\n\n" +
                        "Backup target:\n" +
                        "Download/WUWA-VH-Backup/<timestamp>\n\n" +
                        "This clean build does not write game files yet. Add the Shizuku file-system layer before enabling Apply Patch.";
        new AlertDialog.Builder(this)
                .setTitle("Dry run")
                .setMessage(message)
                .setPositiveButton("OK", null)
                .show();
        addLog("Dry run: shown");
    }

    private boolean isPackageInstalled(String packageName) {
        try {
            getPackageManager().getPackageInfo(packageName, 0);
            return true;
        } catch (PackageManager.NameNotFoundException ignored) {
            return false;
        }
    }

    private void openShizuku() {
        Intent launchIntent = getPackageManager().getLaunchIntentForPackage(SHIZUKU_PACKAGE);
        if (launchIntent != null) {
            startActivity(launchIntent);
            addLog("Shizuku: opened app");
            return;
        }
        openUrl("https://shizuku.rikka.app/");
        addLog("Shizuku: opened install guide");
    }

    private void openUrl(String url) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (ActivityNotFoundException ignored) {
            Toast.makeText(this, "No browser app found.", Toast.LENGTH_SHORT).show();
        }
    }

    private void copyLog() {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard != null) {
            clipboard.setPrimaryClip(ClipData.newPlainText("WUWA VN debug log", getLogText()));
            Toast.makeText(this, "Debug log copied.", Toast.LENGTH_SHORT).show();
            addLog("Log: copied");
        }
    }

    private void shareLog() {
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_SUBJECT, "WUWA VN issue report");
        intent.putExtra(Intent.EXTRA_TEXT, getLogText());
        startActivity(Intent.createChooser(intent, "Send Issue Report"));
        addLog("Issue report: share sheet opened");
    }

    private void showMessage(String title, String message) {
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton("OK", null)
                .show();
    }

    private void addLog(String message) {
        String time = new SimpleDateFormat("HH:mm:ss", Locale.US).format(new Date());
        logLines.add("[" + time + "] " + message);
        if (logView != null) {
            logView.setText(getLogText());
        }
    }

    private String getLogText() {
        return TextUtils.join("\n", logLines);
    }

    private TextView text(String value, int sp, int color, boolean bold) {
        TextView textView = new TextView(this);
        textView.setText(value);
        textView.setTextSize(sp);
        textView.setTextColor(color);
        textView.setLineSpacing(dp(2), 1.0f);
        if (bold) {
            textView.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        }
        return textView;
    }

    private Button primaryButton(String label, View.OnClickListener listener) {
        Button button = button(label, listener);
        button.setTextColor(Color.WHITE);
        button.setBackgroundColor(Color.rgb(25, 118, 210));
        return button;
    }

    private Button button(String label, View.OnClickListener listener) {
        Button button = new Button(this);
        button.setText(label);
        button.setTextColor(Color.WHITE);
        button.setAllCaps(false);
        button.setGravity(Gravity.CENTER);
        button.setBackgroundColor(Color.rgb(35, 48, 68));
        button.setOnClickListener(listener);
        LinearLayout.LayoutParams params = matchWrap();
        params.setMargins(0, dp(8), 0, 0);
        button.setLayoutParams(params);
        return button;
    }

    private View space(int heightDp) {
        View view = new View(this);
        view.setLayoutParams(new LinearLayout.LayoutParams(1, dp(heightDp)));
        return view;
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
