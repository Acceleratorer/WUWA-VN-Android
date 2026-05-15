package com.acceleratorer.wuwavn;

import android.content.Context;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

final class DownloadClient {
    interface ProgressListener {
        void onProgress(String message);
    }

    File downloadAndVerify(Context context, PatchManifest manifest, ProgressListener progressListener) throws Exception {
        File patchDir = context.getExternalFilesDir("patches");
        if (patchDir == null) {
            patchDir = new File(context.getFilesDir(), "patches");
        }
        if (!patchDir.exists() && !patchDir.mkdirs()) {
            throw new IllegalStateException("Could not create patch directory: " + patchDir.getAbsolutePath());
        }

        File destination = new File(patchDir, manifest.pakFileName);
        if (destination.exists() && Sha256Verifier.verify(destination, manifest.pakSha256)) {
            progressListener.onProgress("Patch download: already verified");
            return destination;
        }

        File temporary = new File(patchDir, manifest.pakFileName + ".download");
        if (temporary.exists() && !temporary.delete()) {
            throw new IllegalStateException("Could not remove old partial download.");
        }

        progressListener.onProgress("Patch download: started");
        HttpURLConnection connection = (HttpURLConnection) new URL(manifest.pakUrl).openConnection();
        connection.setConnectTimeout(15000);
        connection.setReadTimeout(30000);
        connection.setInstanceFollowRedirects(true);
        connection.connect();

        int status = connection.getResponseCode();
        if (status < 200 || status >= 300) {
            throw new IllegalStateException("Patch download failed with HTTP " + status);
        }

        long totalBytes = connection.getContentLengthLong();
        long readBytes = 0L;
        long nextLogBytes = 5L * 1024L * 1024L;
        byte[] buffer = new byte[64 * 1024];

        try (InputStream input = connection.getInputStream();
             FileOutputStream output = new FileOutputStream(temporary)) {
            int read;
            while ((read = input.read(buffer)) != -1) {
                output.write(buffer, 0, read);
                readBytes += read;
                if (readBytes >= nextLogBytes) {
                    progressListener.onProgress(progress(readBytes, totalBytes));
                    nextLogBytes += 5L * 1024L * 1024L;
                }
            }
        } finally {
            connection.disconnect();
        }

        progressListener.onProgress("SHA-256: verifying");
        if (!Sha256Verifier.verify(temporary, manifest.pakSha256)) {
            temporary.delete();
            throw new SecurityException("Patch SHA-256 verification failed.");
        }

        if (destination.exists() && !destination.delete()) {
            throw new IllegalStateException("Could not replace old patch file.");
        }
        if (!temporary.renameTo(destination)) {
            throw new IllegalStateException("Could not finalize patch download.");
        }

        progressListener.onProgress("SHA-256: verified");
        return destination;
    }

    private String progress(long readBytes, long totalBytes) {
        if (totalBytes > 0L) {
            return "Patch download: " + formatMb(readBytes) + " / " + formatMb(totalBytes);
        }
        return "Patch download: " + formatMb(readBytes);
    }

    private String formatMb(long bytes) {
        return String.format(java.util.Locale.US, "%.1f MB", bytes / 1024.0 / 1024.0);
    }
}
