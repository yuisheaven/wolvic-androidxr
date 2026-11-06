/* -*- Mode: Java; c-basic-offset: 4; tab-width: 4; indent-tabs-mode: nil; -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package com.igalia.wolvic;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.KeyEvent;

import com.google.androidgamesdk.GameActivity;
import com.igalia.wolvic.ui.widgets.WidgetManagerDelegate;

public class PlatformActivity extends GameActivity {

    // Android XR (e.g. Samsung Galaxy XR) gates OpenXR hand tracking and eye gaze behind regular
    // Android runtime permissions.
    private static final String ANDROID_XR_OPENXR_FEATURE = "android.software.xr.api.openxr";
    private static final String ANDROID_XR_HAND_TRACKING_PERMISSION = "android.permission.HAND_TRACKING";
    private static final String ANDROID_XR_EYE_TRACKING_PERMISSION = "android.permission.EYE_TRACKING_FINE";
    private static final int ANDROID_XR_PERMISSIONS_REQUEST_CODE = 0x58520;

    private boolean isAndroidXR() {
        return getPackageManager().hasSystemFeature(ANDROID_XR_OPENXR_FEATURE);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Ask early so the hand trackers can be created right away. If the permission is granted
        // later, the native side retries creating them.
        if (isAndroidXR() && checkSelfPermission(ANDROID_XR_HAND_TRACKING_PERMISSION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[] { ANDROID_XR_HAND_TRACKING_PERMISSION }, ANDROID_XR_PERMISSIONS_REQUEST_CODE);
        }
    }

    public static boolean filterPermission(final String aPermission) {
        // Dummy implementation.
        return false;
    }

    public static boolean isPositionTrackingSupported() {
        // Dummy implementation.
        return true;
    }

    public final PlatformActivityPlugin createPlatformPlugin(WidgetManagerDelegate delegate) { return null; }

    protected Intent getStoreIntent() {
        // Dummy implementation.
        return null;
    }

    protected String getEyeTrackingPermissionString() {
        if (isAndroidXR())
            return ANDROID_XR_EYE_TRACKING_PERMISSION;
        // FIXME: currently returning MagicLeap2 string, should be generalized to other devices.
        return "com.magicleap.permission.EYE_TRACKING";
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            // GameActivity does not invoke onBackPressed() on KEYCODE_BACK
            onBackPressed();
        }
        return super.onKeyUp(keyCode, event);
    }

    protected native void queueRunnable(Runnable aRunnable);
    protected native boolean platformExit();
}
