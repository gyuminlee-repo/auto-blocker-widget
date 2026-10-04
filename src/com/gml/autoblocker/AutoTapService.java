package com.gml.autoblocker;

import android.accessibilityservice.AccessibilityService;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.ContentObserver;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** 위젯이나 빠른 설정 타일이 armed 한 5초 안에만 rampart 설정 화면의 스위치 행을 한 번 탭한다. */
public class AutoTapService extends AccessibilityService {
    static final String ROW_ID = "com.samsung.android.rampart:id/sesl_switchbar_container";
    static final String RAMPART_PKG = "com.samsung.android.rampart";
    static final String SWITCH_ID = "com.samsung.android.rampart:id/sesl_switchbar_switch";
    static final long ARM_WINDOW_MS = 5000;
    static final long OBSERVE_MS = 10000;
    private static final String TAG = AutoBlockerWidget.TAG;

    private static AutoTapService instance;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private Runnable armTimeout;
    private Runnable observeTimeout;

    static boolean isRunning() { return instance != null; }

    /** 트램펄린이 armed 를 기록한 직후 부른다. 5초 안에 못 누르면 알린다. */
    static void onArmed() {
        Log.i(TAG, "armed");
        if (instance != null) instance.startArmTimeout();
    }

    static void disarm(Context ctx) {
        ctx.getSharedPreferences(TrampolineActivity.PREFS, MODE_PRIVATE).edit()
                .putLong(TrampolineActivity.ARMED_AT, 0)
                .putInt(TrampolineActivity.ARM_TARGET, -1)
                .putInt(TrampolineActivity.ARM_RETURN, TrampolineActivity.RETURN_HOME).commit();
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
                    Toast.makeText(LocaleHelper.wrap(AutoTapService.this), R.string.toast_node_missing, Toast.LENGTH_LONG).show();
                }
            }
        };
        handler.postDelayed(armTimeout, ARM_WINDOW_MS);
    }

    @Override
    protected void onServiceConnected() {
        boolean wasRunning = isRunning();
        instance = this;
        // 같은 인스턴스에 두 번 불려도 observer 는 하나만 둔다.
        if (observer == null) startObserver(wasRunning);
        AutoBlockerWidget.refresh(this); // 「설정 필요」를 지우고 현재 캐시로 다시 그린다.
    }

    // 해제 때 위젯을 다시 그린다. 사용자가 설정에서 끄면 ENABLED_ACCESSIBILITY_SERVICES 가 먼저 바뀐 뒤 해제된다고 보고
    // 「설정 필요」가 바로 보이길 기대한다(추정, 실기기 미확인). 콜백 없이 프로세스가 죽으면 다음 갱신 때 반영된다.
    @Override
    public boolean onUnbind(android.content.Intent i) { stopObserver("unbind"); AutoBlockerWidget.refresh(this); return super.onUnbind(i); }

    /** 클릭 전 값. 클릭을 보냈고 값 변화를 기다리는 동안만 0 또는 1, 아니면 -1. */
    private int pendingBefore = -1;
    /** 값이 바뀐 뒤 돌아가는 방식. 클릭 전에 disarm 으로 기록이 지워지므로 watch 할 때 옮겨 둔다. */
    private int pendingReturn = TrampolineActivity.RETURN_HOME;

    @Override
    public void onAccessibilityEvent(AccessibilityEvent e) {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return;
        // packageNames 로 rampart 이벤트만 받는다. 활성 창이 다른 패키지(인증 창 등)면 건드리지 않는다.
        if (!RAMPART_PKG.equals(String.valueOf(root.getPackageName()))) return;
        List<AccessibilityNodeInfo> sw = root.findAccessibilityNodeInfosByViewId(SWITCH_ID);
        if (sw == null || sw.isEmpty()) return;
        int checked = sw.get(0).isChecked() ? 1 : 0;
        lastScreenReadAt = SystemClock.elapsedRealtime();
        // 화면 스위치 값이 항상 이긴다. 알림으로 뒤집은 값과 다르면 덮고 기록한다.
        int cached = AutoBlockerWidget.cached(this);
        boolean fromFlip = AutoBlockerWidget.SRC_FLIP.equals(AutoBlockerWidget.source(this));
        if (cached != checked) {
            Log.i(TAG, "state observed=" + checked);
            StateLog.add(this, cached == -1 ? "screen_read" : fromFlip ? "mismatch_override" : "screen_change",
                    cached + " -> " + checked);
            AutoBlockerWidget.saveState(this, checked == 1, AutoBlockerWidget.SRC_SCREEN);
            AutoBlockerWidget.refresh(this);
        } else if (fromFlip) {
            StateLog.add(this, "screen_confirm", "flip value " + checked + " matches screen");
            AutoBlockerWidget.saveState(this, checked == 1, AutoBlockerWidget.SRC_SCREEN);
            AutoBlockerWidget.refresh(this);
        }

        if (pendingBefore >= 0) {
            if (checked != pendingBefore) {
                int ret = pendingReturn;
                Log.i(TAG, "value changed " + pendingBefore + " -> " + checked + ", return=" + ret);
                stopWatch();
                returnFrom(ret);
            }
            return;
        }
        if (!armed()) return;
        List<AccessibilityNodeInfo> rows = root.findAccessibilityNodeInfosByViewId(ROW_ID);
        if (rows == null || rows.isEmpty()) return;
        Log.i(TAG, "node found before=" + checked);
        SharedPreferences prefs = getSharedPreferences(TrampolineActivity.PREFS, MODE_PRIVATE);
        int target = prefs.getInt(TrampolineActivity.ARM_TARGET, -1);
        int ret = prefs.getInt(TrampolineActivity.ARM_RETURN, TrampolineActivity.RETURN_HOME);
        // 클릭 전에 disarm 해서 이벤트가 겹쳐도 정확히 한 번만 누른다.
        disarm(this);
        if (armTimeout != null) handler.removeCallbacks(armTimeout);
        if (target >= 0 && checked == target) {
            // 이미 원하는 값이면 누르지 않는다. 캐시는 위에서 저장했다.
            Log.i(TAG, "already " + target + ", no click, return=" + ret);
            returnFrom(ret);
            return;
        }
        boolean ok = rows.get(0).performAction(AccessibilityNodeInfo.ACTION_CLICK);
        Log.i(TAG, "click performed=" + ok);
        if (ok) watch(checked, ret);
    }

    /**
     * 위젯은 홈으로 간다. 타일, 자동화, 설정 안내는 뒤로 가기 한 번으로 Auto Blocker 화면을 닫아 열기 전 화면으로 돌아간다.
     * 이 메서드는 활성 창이 rampart 일 때만 불리므로(위 패키지 검사) 인증 창이 아니라 Auto Blocker 화면이 닫힌다.
     */
    private void returnFrom(int ret) {
        performGlobalAction(ret == TrampolineActivity.RETURN_BACK ? GLOBAL_ACTION_BACK : GLOBAL_ACTION_HOME);
    }

    private void watch(int before, int ret) {
        stopWatch();
        pendingBefore = before;
        pendingReturn = ret;
        observeTimeout = new Runnable() {
            @Override public void run() { Log.i(TAG, "observe timeout, no action"); stopWatch(); }
        };
        handler.postDelayed(observeTimeout, OBSERVE_MS);
    }

    private void stopWatch() {
        pendingBefore = -1;
        pendingReturn = TrampolineActivity.RETURN_HOME;
        if (observeTimeout != null) { handler.removeCallbacks(observeTimeout); observeTimeout = null; }
    }

    @Override
    public void onInterrupt() { stopWatch(); }

    @Override
    public void onDestroy() { stopWatch(); stopObserver("destroy"); AutoBlockerWidget.refresh(this); super.onDestroy(); }

    // ---- 설정 변경 알림(시험 중) ----
    // 값은 읽지 않는다. 일반 앱이 이 키를 읽으면 SecurityException 이 난다(@hide, system apps only).
    // AOSP SettingsProvider 는 저장 기록이 실제로 바뀔 때만 notify 하므로(SettingsState.Setting.update 가
    // 같은 값이면 false, SettingsRegistry.insertSettingLocked 는 success 일 때만 notifyForSettingsChange)
    // 알림 한 번을 토글 한 번으로 보고 캐시를 뒤집는다. One UI 의 SettingsProvider 가 같은지는 실기기 기록으로 확인한다.

    static final String MAIN_SWITCH_KEY = "rampart_main_switch_enabled";
    /** 한 토글에 알림이 여러 번 올 수 있다고 보고 이 안의 후속 알림은 기록만 한다(가정, 실기기 미확인). */
    static final long NOTIFY_DEDUP_MS = 1000;
    /** 화면에서 스위치를 읽은 지 이 안에 온 알림은 화면 값이 이미 반영한 변경으로 보고 뒤집지 않는다(가정). */
    static final long SCREEN_AUTHORITY_MS = 2000;
    /** 마지막 서비스 해제 시각(System.currentTimeMillis). 기록용. 프로세스가 죽으면 남지 않는다. */
    static final String SVC_OFF_AT = "serviceOffAt";

    private ContentObserver observer;
    /** elapsedRealtime. 0 은 없음. */
    private long lastNotifyAt, lastScreenReadAt;

    private void startObserver(boolean wasRunning) {
        long offAt = getSharedPreferences(AutoBlockerWidget.STATE_PREFS, MODE_PRIVATE).getLong(SVC_OFF_AT, 0);
        StateLog.add(this, "service_connected", (wasRunning ? "instance was alive, " : "")
                + (offAt == 0 ? "no disconnect record" : "last disconnect " + time(offAt)));
        ContentObserver ob = new ContentObserver(handler) {
            @Override public void onChange(boolean selfChange) { onMainSwitchChanged(); }
        };
        try {
            getContentResolver().registerContentObserver(Settings.Secure.getUriFor(MAIN_SWITCH_KEY), false, ob);
        } catch (RuntimeException e) {
            // SecurityException 등. 알림 없이 기존 동작(화면에서 읽은 캐시)만 쓴다.
            StateLog.add(this, "observer_fail", e.getClass().getSimpleName() + ": " + e.getMessage());
            return;
        }
        observer = ob;
        StateLog.add(this, "observer_ok", MAIN_SWITCH_KEY);
        // 연결 전에는 observer 가 없었으므로 그동안의 변화는 알 수 없다. 해제 기록(onUnbind, onDestroy)은
        // 프로세스가 죽으면 남지 않아 공백 길이를 믿을 수 없으므로 (재)연결마다 캐시를 미확인으로 돌린다.
        // 처음 설치 직후, 재부팅, 앱 업데이트 뒤에도 같다. 다음에 설정 화면을 보면 다시 채워진다. 위젯은 onServiceConnected 가 다시 그린다.
        int cached = AutoBlockerWidget.cached(this);
        if (cached != -1) {
            AutoBlockerWidget.clearState(this);
            StateLog.add(this, "gap_unknown", "cache " + cached + " -> unknown");
        }
    }

    private void stopObserver(String why) {
        if (observer != null) {
            try {
                getContentResolver().unregisterContentObserver(observer);
            } catch (RuntimeException ignored) {
                // 해제 실패는 서비스 종료를 막지 않는다.
            }
            observer = null;
        }
        if (instance == this) {
            getSharedPreferences(AutoBlockerWidget.STATE_PREFS, MODE_PRIVATE).edit()
                    .putLong(SVC_OFF_AT, System.currentTimeMillis()).commit();
            StateLog.add(this, "service_disconnected", why);
        }
        instance = null;
    }

    /** 메인 Looper 에서 불린다. 접근성 이벤트와 같은 스레드라 순서가 섞이지 않는다. */
    private void onMainSwitchChanged() {
        long now = SystemClock.elapsedRealtime();
        if (lastNotifyAt != 0 && now - lastNotifyAt < NOTIFY_DEDUP_MS) {
            StateLog.add(this, "on_change_dup", (now - lastNotifyAt) + " ms after previous, ignored");
            return;
        }
        lastNotifyAt = now;
        int cached = AutoBlockerWidget.cached(this);
        if (lastScreenReadAt != 0 && now - lastScreenReadAt <= SCREEN_AUTHORITY_MS) {
            StateLog.add(this, "on_change_absorbed", "screen read " + (now - lastScreenReadAt) + " ms ago, keep " + cached);
            return;
        }
        if (cached == -1) {
            StateLog.add(this, "on_change", "cache unknown, stays unknown");
            return;
        }
        int flipped = 1 - cached;
        AutoBlockerWidget.saveState(this, flipped == 1, AutoBlockerWidget.SRC_FLIP);
        AutoBlockerWidget.refresh(this);
        StateLog.add(this, "flip", cached + " -> " + flipped);
    }

    private static String time(long at) {
        return new SimpleDateFormat("MM-dd HH:mm:ss", Locale.KOREA).format(new Date(at));
    }
}
