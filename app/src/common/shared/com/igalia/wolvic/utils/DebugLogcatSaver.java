/* -*- Mode: Java; c-basic-offset: 4; tab-width: 4; indent-tabs-mode: nil; -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package com.igalia.wolvic.utils;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;

import androidx.annotation.NonNull;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Debug helper for testers without adb: streams this app's own logcat into
 * Download/Wolvic/wolvic-log-*.txt, so crash logs can be collected on the device itself.
 * Apps can read their own log entries without any permission. The stream includes the log
 * entries of previous runs still in the log buffer, so a crash right after start is captured
 * either live or on the next launch.
 */
public final class DebugLogcatSaver {
    private static final String LOGTAG = SystemUtils.createLogtag(DebugLogcatSaver.class);
    private static final long MAX_LOG_BYTES = 32L * 1024 * 1024;
    // Per-frame messages from the Android XR runtime client that flood the log.
    private static final String[] SPAM = {
            "Buffer item consumer returned null buffer",
            "Unimplemented layer type",
            "Failed to latch buffer",
            "Found no buffer for compositor",
            "EglImage dataspace changed",
    };

    private DebugLogcatSaver() {}

    public static void start(@NonNull Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q)
            return;
        final Context appContext = context.getApplicationContext();
        Thread thread = new Thread(() -> saveLogcat(appContext), "DebugLogcatSaver");
        thread.setDaemon(true);
        thread.start();
    }

    private static boolean isSpam(String line) {
        for (String spam : SPAM) {
            if (line.contains(spam))
                return true;
        }
        return false;
    }

    private static void saveLogcat(@NonNull Context context) {
        String name = "wolvic-log-" + new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(new Date()) + ".txt";
        ContentValues values = new ContentValues();
        values.put(MediaStore.MediaColumns.DISPLAY_NAME, name);
        values.put(MediaStore.MediaColumns.MIME_TYPE, "text/plain");
        values.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/Wolvic");

        ContentResolver resolver = context.getContentResolver();
        Process process = null;
        try {
            Uri uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
            if (uri == null) {
                Log.e(LOGTAG, "Could not create " + name + " in Downloads");
                return;
            }
            // Skip the very chatty StrictMode and IME stack traces, they filled the log in minutes.
            process = new ProcessBuilder("logcat", "-v", "threadtime", "StrictMode:S", "InputMethodManager:S", "*:D")
                    .redirectErrorStream(true).start();
            try (BufferedReader in = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8));
                 OutputStream out = resolver.openOutputStream(uri)) {
                if (out == null)
                    return;
                long written = 0;
                long skipped = 0;
                String line;
                while ((line = in.readLine()) != null && written < MAX_LOG_BYTES) {
                    if (isSpam(line)) {
                        // Keep a trace of the spam so that its timing is still visible.
                        if (++skipped % 1000 != 1)
                            continue;
                        line = line + "   [DebugLogcatSaver: " + skipped + " spam lines so far]";
                    }
                    byte[] bytes = (line + "\n").getBytes(StandardCharsets.UTF_8);
                    // Write through right away: the process may die at any moment.
                    out.write(bytes);
                    written += bytes.length;
                }
            }
        } catch (Exception e) {
            Log.e(LOGTAG, "Saving logcat failed: " + e);
        } finally {
            if (process != null)
                process.destroy();
        }
    }
}
