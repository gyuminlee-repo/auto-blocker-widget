package com.gml.autoblocker;

import android.accessibilityservice.AccessibilityService;
import android.content.Context;
import android.database.ContentObserver;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.Toast;

import java.util.List;

/** 위젯이 armed 한 5초 안에만 rampart 설정 화면의 스위치 행을 한 번 탭한다. */
public class AutoTapService extends AccessibilityService {
    static final String ROW_ID = "com.samsung.android.rampart:id/sesl_switchbar_container";
    static final long ARM_WINDOW_MS = 5000;
    static final long OBSERVE_MS = 10000;
    private static final String TAG = AutoBlockerWidget.TAG;

    private static AutoTapService instance;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private ContentObserver observer;
    private Runnable armTimeout;
    private Runnable observeTimeout;

    static boolean isRunning() { return instance != null; }

    /** 트램펄린이 armed 를 기록한 직후 부른다. 5초 안에 못 누르면 알린다. */
    static void onArmed() {
        if (instance != null) instance.startArmTimeout();
    }

    static void disarm(Context ctx) {
        ctx.getSharedPreferences(TrampolineActivity.PREFS, MODE_PRIVATE).edit()
                .putLong(TrampolineActivity.ARMED_AT, 0).commit();
    }

    private boolean armed() {
        long at = getSharedPreferences(TrampolineActivity.PREFS, MODE_PRIVATE)
                .getLong(TrampolineActivity.ARMED_AT, 0);
        return at != 0 && SystemClock.elapsedRealtime() - at <= ARM_WINDOW_MS;
    }

    private void startArmTimeout() {
        if (armTimeout != null) handler.removeCallbacks(armTimeout);
        armTimeout = new Runnable() {
            @Override public void run() {
                if (getSharedPreferences(TrampolineActivity.PREFS, MODE_PRIVATE)
                        .getLong(TrampolineActivity.ARMED_AT, 0) != 0) {
                    disarm(AutoTapService.this);
                    Log.i(TAG, "arm timeout: row not found");
                    Toast.makeText(AutoTapService.this, R.string.toast_node_missing, Toast.LENGTH_LONG).show();
                }
            }
        };
        handler.postDelayed(armTimeout, ARM_WINDOW_MS);
    }

    @Override
    protected void onServiceConnected() { instance = this; }

    @Override
    public boolean onUnbind(android.content.Intent i) { instance = null; return super.onUnbind(i); }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent e) {
        if (!armed()) return;
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return;
        List<AccessibilityNodeInfo> rows = root.findAccessibilityNodeInfosByViewId(ROW_ID);
        if (rows == null || rows.isEmpty()) return;
        // 클릭 전에 disarm 해서 이벤트가 겹쳐도 정확히 한 번만 누른다.
        disarm(this);
        if (armTimeout != null) handler.removeCallbacks(armTimeout);
        int before = AutoBlockerWidget.read(this);
        boolean ok = rows.get(0).performAction(AccessibilityNodeInfo.ACTION_CLICK);
        Log.i(TAG, "click performed=" + ok + " before=" + before);
        if (ok) watch(before);
    }

    private void watch(final int before) {
        stopWatch();
        observer = new ContentObserver(handler) {
            @Override public void onChange(boolean selfChange) {
                int now = AutoBlockerWidget.read(AutoTapService.this);
                if (now == before || now < 0) return;
                Log.i(TAG, "value changed " + before + " -> " + now);
                stopWatch();
                performGlobalAction(GLOBAL_ACTION_HOME);
                AutoBlockerWidget.refresh(AutoTapService.this);
            }
        };
        getContentResolver().registerContentObserver(
                Settings.Secure.getUriFor(AutoBlockerWidget.KEY), false, observer);
        observeTimeout = new Runnable() {
            @Override public void run() { Log.i(TAG, "observe timeout"); stopWatch(); }
        };
        handler.postDelayed(observeTimeout, OBSERVE_MS);
    }

    private void stopWatch() {
        if (observer != null) { getContentResolver().unregisterContentObserver(observer); observer = null; }
        if (observeTimeout != null) { handler.removeCallbacks(observeTimeout); observeTimeout = null; }
    }

    @Override
    public void onInterrupt() { stopWatch(); }

    @Override
    public void onDestroy() { stopWatch(); instance = null; super.onDestroy(); }
}
