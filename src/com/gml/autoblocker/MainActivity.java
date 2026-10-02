package com.gml.autoblocker;

import android.app.Activity;
import android.appwidget.AppWidgetManager;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** 앱 서랍 진입점. 설정 단계를 체크리스트로 보여 주고 미완료 첫 단계만 펼친다. */
public class MainActivity extends Activity {
    static final String SETUP_PREFS = "setup";
    static final String FINISH_DONE = "finishDone";
    private static final String PROTECT_PKG = "com.google.android.gms";
    private static final String PROTECT_CLS = "com.google.android.gms.security.settings.VerifyAppsSettingsActivity";
    private static final String STORE_PKG = "com.android.vending";

    private TextView[] marks;
    private View[] steps, bodies;
    private ColorStateList defaultMarkColor;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_main);
        marks = new TextView[] {
                (TextView) findViewById(R.id.step1_mark),
                (TextView) findViewById(R.id.step2_mark),
                (TextView) findViewById(R.id.step3_mark),
                (TextView) findViewById(R.id.step4_mark)};
        steps = new View[] {findViewById(R.id.step1), findViewById(R.id.step2),
                findViewById(R.id.step3), findViewById(R.id.step4)};
        bodies = new View[] {findViewById(R.id.step1_body), findViewById(R.id.step2_body),
                findViewById(R.id.step3_body), findViewById(R.id.step4_body)};
        defaultMarkColor = marks[0].getTextColors();

        findViewById(R.id.btn_accessibility).setOnClickListener(v ->
                open(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));
        findViewById(R.id.btn_restricted_info).setOnClickListener(v -> openAppInfo());
        findViewById(R.id.btn_pin_widget).setOnClickListener(v -> pinWidget());
        findViewById(R.id.btn_read_state).setOnClickListener(v -> openRampartWithoutTap());
        findViewById(R.id.btn_auto_enable).setOnClickListener(v -> openRampartWithoutTap());
        findViewById(R.id.btn_play_protect).setOnClickListener(v -> openPlayProtect());
        // 위젯 탭과 같은 흐름에 켜기 전용 target 을 준다. 이미 켜져 있으면 누르지 않는다.
        findViewById(R.id.btn_enable_blocker).setOnClickListener(v ->
                open(new Intent(this, TrampolineActivity.class).putExtra(TrampolineActivity.EXTRA_TARGET, 1)));
        findViewById(R.id.btn_finish_setup).setOnClickListener(v -> {
            getSharedPreferences(SETUP_PREFS, Context.MODE_PRIVATE).edit()
                    .putBoolean(FINISH_DONE, true).commit();
            render();
        });
        bindToggle(R.id.step1_toggle, R.id.step1_more);
        bindToggle(R.id.step2_toggle, R.id.step2_more);
        bindToggle(R.id.step3_toggle, R.id.step3_more);
        bindToggle(R.id.step4_toggle, R.id.step4_more);
        bindToggle(R.id.update_toggle, R.id.update_more);
        findViewById(R.id.btn_update).setOnClickListener(v ->
                open(new Intent(Intent.ACTION_VIEW, Uri.parse(getString(R.string.releases_url)))));
        findViewById(R.id.btn_app_info).setOnClickListener(v -> openAppInfo());
        ((TextView) findViewById(R.id.footer)).setText(getString(R.string.main_footer, versionName()));
    }

    @Override
    protected void onResume() {
        super.onResume();
        render();
    }

    /** 위젯 추가 다이얼로그가 닫힐 때 onResume 이 오지 않을 수 있어 포커스 복귀 때도 다시 읽는다. */
    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) render();
    }

    private void render() {
        AppWidgetManager mgr = AppWidgetManager.getInstance(this);
        int state = AutoBlockerWidget.cached(this);
        boolean[] done = {
                isServiceEnabled(),
                mgr.getAppWidgetIds(widgetProvider()).length > 0,
                state != -1,
                getSharedPreferences(SETUP_PREFS, Context.MODE_PRIVATE).getBoolean(FINISH_DONE, false)};

        int firstTodo = -1;
        for (int i = 0; i < done.length; i++) {
            if (!done[i] && firstTodo < 0) firstTodo = i;
            marks[i].setText(done[i] ? R.string.mark_done : R.string.mark_todo);
            if (done[i]) marks[i].setTextColor(getColor(R.color.step_done));
            else marks[i].setTextColor(defaultMarkColor);
            bodies[i].setVisibility(i == firstTodo ? View.VISIBLE : View.GONE);
            // 단계 1 이 미완료면 뒤 단계는 비활성처럼 흐리게 둔다.
            steps[i].setAlpha(i > 0 && !done[0] ? 0.5f : 1f);
        }

        if (firstTodo == 1) {
            findViewById(R.id.btn_pin_widget).setVisibility(
                    mgr.isRequestPinAppWidgetSupported() ? View.VISIBLE : View.GONE);
        }
        if (firstTodo == 3) {
            findViewById(R.id.step4_enable).setVisibility(state == 0 ? View.VISIBLE : View.GONE);
            findViewById(R.id.step4_blocker_on).setVisibility(state == 1 ? View.VISIBLE : View.GONE);
        }

        View card = findViewById(R.id.ready_card);
        if (firstTodo < 0) {
            card.setVisibility(View.VISIBLE);
            ((TextView) findViewById(R.id.ready_state)).setText(getString(R.string.ready_state,
                    getString(state == 1 ? R.string.state_on : R.string.state_off)));
            long at = getSharedPreferences(AutoBlockerWidget.STATE_PREFS, Context.MODE_PRIVATE)
                    .getLong(AutoBlockerWidget.LAST_AT, 0);
            TextView checked = (TextView) findViewById(R.id.ready_checked);
            if (at > 0) {
                String when = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.KOREA).format(new Date(at));
                checked.setText(getString(R.string.ready_checked, when));
                checked.setVisibility(View.VISIBLE);
            } else {
                checked.setVisibility(View.GONE);
            }
        } else {
            card.setVisibility(View.GONE);
        }
    }

    /** 자세히 토글. 기본은 접힘(layout 에서 gone). */
    private void bindToggle(int toggleId, int moreId) {
        TextView toggle = (TextView) findViewById(toggleId);
        View more = findViewById(moreId);
        toggle.setOnClickListener(v -> {
            boolean show = more.getVisibility() != View.VISIBLE;
            more.setVisibility(show ? View.VISIBLE : View.GONE);
            toggle.setText(show ? R.string.details_hide : R.string.details_show);
        });
    }

    /** Play 프로텍트 설정 화면을 직접 열고, 막히면 Play 스토어를 연다. */
    private void openPlayProtect() {
        try {
            startActivity(new Intent().setComponent(new ComponentName(PROTECT_PKG, PROTECT_CLS))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            return;
        } catch (ActivityNotFoundException | SecurityException e) {
            // 아래 Play 스토어로 넘어간다.
        }
        Intent store = getPackageManager().getLaunchIntentForPackage(STORE_PKG);
        try {
            if (store != null) {
                startActivity(store);
                return;
            }
        } catch (ActivityNotFoundException | SecurityException e) {
            // 아래 toast 로 넘어간다.
        }
        Toast.makeText(this, R.string.toast_play_protect_fail, Toast.LENGTH_LONG).show();
    }

    private ComponentName widgetProvider() {
        return new ComponentName(this, AutoBlockerWidget.class);
    }

    private void pinWidget() {
        AppWidgetManager mgr = AppWidgetManager.getInstance(this);
        if (!mgr.isRequestPinAppWidgetSupported() || !mgr.requestPinAppWidget(widgetProvider(), null, null)) {
            Toast.makeText(this, R.string.toast_pin_unsupported, Toast.LENGTH_LONG).show();
        }
    }

    /**
     * arm 없이 rampart 화면만 연다. AutoTapService 가 창 이벤트에서 상태를 저장한다.
     * 상태 읽어 오기와 자동으로 켜기 설정 열기가 함께 쓴다.
     */
    private void openRampartWithoutTap() {
        // 위젯 탭 직후 남은 arm 표식이 있으면 스위치가 눌리므로 읽기 전에 지운다.
        AutoTapService.disarm(this);
        try {
            startActivity(TrampolineActivity.rampartIntent());
        } catch (ActivityNotFoundException | SecurityException e) {
            Toast.makeText(this, R.string.toast_open_fail, Toast.LENGTH_LONG).show();
        }
    }

    private void openAppInfo() {
        open(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", getPackageName(), null)));
    }

    private void open(Intent i) {
        try {
            startActivity(i);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, R.string.toast_no_app, Toast.LENGTH_LONG).show();
        }
    }

    private boolean isServiceEnabled() {
        String list = Settings.Secure.getString(getContentResolver(),
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        if (list == null) return false;
        ComponentName cn = new ComponentName(this, AutoTapService.class);
        String full = cn.flattenToString();
        String shrt = cn.flattenToShortString();
        for (String s : list.split(":")) {
            if (s.equalsIgnoreCase(full) || s.equalsIgnoreCase(shrt)) return true;
        }
        return false;
    }

    private String versionName() {
        try {
            return getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (PackageManager.NameNotFoundException e) {
            return "?";
        }
    }
}
