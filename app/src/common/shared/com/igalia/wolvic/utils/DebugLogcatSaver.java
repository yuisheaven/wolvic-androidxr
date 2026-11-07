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

import java.io.InputStream;
import java.io.OutputStream;
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

    private DebugLogcatSaver() {}

    public static void start(@NonNull Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q)
            return;
        final Context appContext = context.getApplicationContext();
        Thread thread = new Thread(() -> saveLogcat(appContext), "DebugLogcatSaver");
        thread.setDaemon(true);
        thread.start();
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
            try (InputStream in = process.getInputStream(); OutputStream out = resolver.openOutputStream(uri)) {
                if (out == null)
                    return;
                byte[] buffer = new byte[8192];
                long written = 0;
                int count;
                while ((count = in.read(buffer)) > 0 && written < MAX_LOG_BYTES) {
                    out.write(buffer, 0, count);
                    // Write through right away: the process may die at any moment.
                    out.flush();
                    written += count;
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
