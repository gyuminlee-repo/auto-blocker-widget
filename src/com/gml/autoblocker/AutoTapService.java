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
        long now = SystemClock.elapsedRealtime();
        // elapsedRealtime 은 재부팅 때 0 부터 다시 센다. now < at 이면 재부팅 전 기록이다.
        if (at != 0 && now >= at && now - at <= ARM_WINDOW_MS) return true;
        if (at != 0) disarm(this);
        return false;
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
        // 남은 arm 은 여기서 지우지 않는다. 프로세스가 산 채로 서비스만 1~2초 만에 다시 붙는 일이 있어
        // (실기기 로그 2026-10-04) 여기서 지우면 방금 탭한 arm 을 잃는다.
        // 재부팅 전 기록과 오래된 기록은 armed() 의 시각 검사가 거른다.
        // 같은 인스턴스에 두 번 불려도 observer 는 하나만 둔다.
        if (observer == null) startObserver(wasRunning);
        AutoBlockerWidget.refresh(this); // 「설정 필요」를 지우고 현재 캐시로 다시 그린다.
    }

    // 해제 때 위젯을 다시 그린다. 사용자가 설정에서 끄면 ENABLED_ACCESSIBILITY_SERVICES 가 먼저 바뀐 뒤 해제된다고 보고
    // 「설정 필요」가 바로 보인다(실기기 확인, Galaxy Z Fold8 One UI 9, 2026-10-04). 콜백 없이 프로세스가 죽으면 다음 갱신 때 반영된다.
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
        // 화면 스위치 값이 항상 이긴다. 알림으로 뒤집은 값이나 디버깅 설정으로 확정한 값과 다르면 덮고 기록한다.
        int cached = AutoBlockerWidget.cached(this);
        String src = AutoBlockerWidget.source(this);
        boolean inferred = AutoBlockerWidget.SRC_FLIP.equals(src) || AutoBlockerWidget.SRC_DEBUG.equals(src);
        if (cached != checked) {
            Log.i(TAG, "state observed=" + checked);
            StateLog.add(this, cached == -1 ? "screen_read" : inferred ? "mismatch_override" : "screen_change",
                    cached + " -> " + checked);
            AutoBlockerWidget.saveState(this, checked == 1, AutoBlockerWidget.SRC_SCREEN);
            AutoBlockerWidget.refresh(this);
            probeOnScreen(checked);
        } else if (inferred) {
            StateLog.add(this, "screen_confirm", src + " value " + checked + " matches screen");
            AutoBlockerWidget.saveState(this, checked == 1, AutoBlockerWidget.SRC_SCREEN);
            AutoBlockerWidget.refresh(this);
            probeOnScreen(checked);
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
    // 알림 한 번을 토글 한 번으로 보고 캐시를 뒤집는다. One UI 9 실기기 기록에서도 토글마다 알림이 1회 왔다.
    // One UI 「30분 뒤 자동으로 켜기」가 실행될 때도 이 키의 알림이 와서 화면 없이 꺼짐에서 켜짐으로 뒤집혔다(2026-10-04 15:37:25).
    // 30분 정각에 알림이 없다가 설정 화면을 연 순간 온 기록도 있다. One UI 가 기한을 다음 계기에 처리하는 것으로 보고(추정) 키가 바뀐 순간을 따른다.

    static final String MAIN_SWITCH_KEY = "rampart_main_switch_enabled";
    /** 한 토글에 알림이 여러 번 올 수 있다고 보고 이 안의 후속 알림은 기록만 한다(대비용, One UI 9 실기기에서는 1회). */
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
        probe(-1);
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
        //
        // 예외: USB 디버깅(adb_enabled) 또는 무선 디버깅(adb_wifi_enabled)이 1 이면 꺼짐으로 확정한다.
        // Auto Blocker 가 켜져 있는 동안에는 USB·무선 디버깅이 차단된다. 실기기 기록(Galaxy Z Fold8 One UI 9, 2026-10-04
        // 14:04~14:05 probe)에서 화면 꺼짐 3회는 모두 adbwifi=1, 켜짐 3회는 모두 adbwifi=0 이었다. 재부팅 기록에서 무선(14:15:21)과
        // USB 단독(14:17:59, adb=1 adbwifi=0) 모두 꺼짐 확정이 동작했다. 한 방향만 성립한다. 0 은 켜짐의 근거가 아니다(사용자가 끈 것일 수 있다).
        // 이 값은 화면 값처럼 덮일 수 있다(onAccessibilityEvent 의 mismatch_override). 알림 뒤집기도 그대로 적용된다.
        String adb = readGlobal(Settings.Global.ADB_ENABLED);
        String adbWifi = readGlobal(ADB_WIFI_KEY);
        String debug = "adb=" + adb + " adbwifi=" + adbWifi;
        if ("1".equals(adb) || "1".equals(adbWifi)) {
            AutoBlockerWidget.saveState(this, false, AutoBlockerWidget.SRC_DEBUG);
            StateLog.add(this, "gap_debug_off", debug);
            AutoBlockerWidget.refresh(this);
            return;
        }
        int cached = AutoBlockerWidget.cached(this);
        if (cached != -1) {
            AutoBlockerWidget.clearState(this);
            StateLog.add(this, "gap_unknown", "cache " + cached + " -> unknown " + debug);
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
        probe(-1);
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

    // ---- 상관 관찰(시험 중, 판단에 쓰지 않음) ----
    // adb_wifi_enabled 는 켜짐 때 0 으로 강제된다는 관측이 있으나 특이도가 낮다(0 은 켜짐의 근거가 아니다).
    // 공개 SDK 34 에 ADB_WIFI_ENABLED 상수가 없어 키 문자열을 쓴다. adb_enabled 는 공개 상수 Settings.Global.ADB_ENABLED 다.
    // 읽기만 하고 쓰지 않는다. adb·adbwifi 는 공백 처리(startObserver)에서 꺼짐 확정에도 쓴다.
    // rampart_suw_main_on 은 실기기(One UI 9)에서 켜짐·꺼짐과 무관하게 늘 1 이어서 읽지 않는다.
    static final String ADB_WIFI_KEY = "adb_wifi_enabled";
    /** 화면 이벤트 뒤에 마지막으로 남긴 probe 본문. 같은 값이면 다시 남기지 않는다. */
    private String lastScreenProbe;

    private void probeOnScreen(int screen) {
        String body = probeBody(screen);
        if (body.equals(lastScreenProbe)) return;
        lastScreenProbe = body;
        StateLog.add(this, "probe", body);
    }

    /** screen 이 -1 이면 그 시점 화면 값이 없다는 뜻이다. */
    private void probe(int screen) {
        StateLog.add(this, "probe", probeBody(screen));
    }

    private String probeBody(int screen) {
        return "adb=" + readGlobal(Settings.Global.ADB_ENABLED) + " adbwifi=" + readGlobal(ADB_WIFI_KEY)
                + " screen=" + (screen < 0 ? "?" : String.valueOf(screen));
    }

    /** Settings.Global 정수 값. 없으면 "-1", 읽기 거부는 "denied", 그 밖의 실패는 "error". */
    private String readGlobal(String key) {
        try {
            return String.valueOf(Settings.Global.getInt(getContentResolver(), key, -1));
        } catch (SecurityException e) {
            return "denied";
        } catch (RuntimeException e) {
            return "error";
        }
    }

    private static String time(long at) {
        return new SimpleDateFormat("MM-dd HH:mm:ss", Locale.KOREA).format(new Date(at));
    }
}
