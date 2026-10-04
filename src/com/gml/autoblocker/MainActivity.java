package com.gml.autoblocker;

import android.app.Activity;
import android.app.AlertDialog;
import android.appwidget.AppWidgetManager;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** 앱 서랍 진입점. 설정 단계를 체크리스트로 보여 주고 미완료 첫 단계만 펼친다. */
public class MainActivity extends Activity {
    static final String SETUP_PREFS = "setup";
    static final String FINISH_DONE = "finishDone";
    static final String RESTRICTED_DONE = "restrictedDone";

    private TextView[] marks;
    private View[] steps, bodies;
    private ColorStateList defaultMarkColor;

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(LocaleHelper.wrap(base));
    }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_main);
        marks = new TextView[] {
                (TextView) findViewById(R.id.step1_mark),
                (TextView) findViewById(R.id.step2_mark),
                (TextView) findViewById(R.id.step3_mark),
                (TextView) findViewById(R.id.step4_mark),
                (TextView) findViewById(R.id.step5_mark)};
        steps = new View[] {findViewById(R.id.step1), findViewById(R.id.step2), findViewById(R.id.step3),
                findViewById(R.id.step4), findViewById(R.id.step5)};
        bodies = new View[] {findViewById(R.id.step1_body), findViewById(R.id.step2_body),
                findViewById(R.id.step3_body), findViewById(R.id.step4_body), findViewById(R.id.step5_body)};
        defaultMarkColor = marks[0].getTextColors();

        findViewById(R.id.btn_accessibility).setOnClickListener(v ->
                open(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));
        findViewById(R.id.btn_restricted_info).setOnClickListener(v -> openAppInfo());
        findViewById(R.id.btn_restricted_done).setOnClickListener(v -> {
            getSharedPreferences(SETUP_PREFS, Context.MODE_PRIVATE).edit()
                    .putBoolean(RESTRICTED_DONE, true).commit();
            render();
        });
        findViewById(R.id.btn_pin_widget).setOnClickListener(v -> pinWidget());
        findViewById(R.id.btn_read_state).setOnClickListener(v -> openRampartWithoutTap());
        findViewById(R.id.btn_auto_enable).setOnClickListener(v -> openRampartWithoutTap());
        findViewById(R.id.btn_play_protect).setOnClickListener(v -> PlayProtectActivity.openWithHint(this, null));
        // 위젯 탭과 같은 흐름에 켜기 전용 target 을 준다. 이미 켜져 있으면 누르지 않는다.
        // 설정 도중이므로 홈 대신 뒤로 가기로 이 화면에 돌아와 ⑤ 단계를 이어 가게 한다.
        findViewById(R.id.btn_enable_blocker).setOnClickListener(v ->
                open(new Intent(this, TrampolineActivity.class).putExtra(TrampolineActivity.EXTRA_TARGET, 1)
                        .putExtra(TrampolineActivity.EXTRA_RETURN, TrampolineActivity.RETURN_BACK)));
        findViewById(R.id.btn_finish_setup).setOnClickListener(v -> {
            getSharedPreferences(SETUP_PREFS, Context.MODE_PRIVATE).edit()
                    .putBoolean(FINISH_DONE, true).commit();
            render();
        });
        bindToggle(R.id.step1_toggle, R.id.step1_more);
        bindToggle(R.id.step2_toggle, R.id.step2_more);
        bindToggle(R.id.step3_toggle, R.id.step3_more);
        bindToggle(R.id.step4_toggle, R.id.step4_more);
        bindToggle(R.id.step5_toggle, R.id.step5_more);
        bindToggle(R.id.update_toggle, R.id.update_more);
        findViewById(R.id.btn_update).setOnClickListener(v ->
                open(new Intent(Intent.ACTION_VIEW, Uri.parse(getString(R.string.releases_url)))));
        findViewById(R.id.btn_app_info).setOnClickListener(v -> openAppInfo());
        TextView footer = (TextView) findViewById(R.id.footer);
        footer.setText(getString(R.string.main_footer, versionName()));
        // 베타 시험용. 버전 줄을 길게 누르면 상태 감지 기록을 연다.
        footer.setOnLongClickListener(v -> { showStateLog(); return true; });
        bindLanguage();
    }

    /** 언어 선택. 현재 값을 먼저 표시한 뒤 리스너를 단다. */
    private void bindLanguage() {
        RadioGroup group = (RadioGroup) findViewById(R.id.lang_group);
        String cur = LocaleHelper.get(this);
        int checked = cur.isEmpty() ? R.id.lang_system
                : "en".equals(cur) ? R.id.lang_en : "ko".equals(cur) ? R.id.lang_ko : View.NO_ID;
        if (checked != View.NO_ID) group.check(checked);
        group.setOnCheckedChangeListener((g, id) -> {
            String tag = id == R.id.lang_en ? "en" : id == R.id.lang_ko ? "ko" : "";
            if (tag.equals(LocaleHelper.get(this))) return;
            LocaleHelper.set(this, tag);
            AutoBlockerWidget.refresh(this);
            // API 33+ 는 시스템이 화면을 다시 만든다.
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) recreate();
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        // 접근성 서비스가 콜백 없이 꺼졌으면 위젯이 아직 이전 상태를 보이므로 앱을 열 때 다시 그린다(「설정 필요」 반영).
        AutoBlockerWidget.refresh(this);
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
        SharedPreferences prefs = getSharedPreferences(SETUP_PREFS, Context.MODE_PRIVATE);
        boolean service = isServiceEnabled();
        boolean[] done = {
                // 일반 앱이 「제한된 설정 허용」 여부를 읽는 공개 API 는 없다고 가정하고 사용자 기록과 접근성 켜짐으로 판정한다.
                service || prefs.getBoolean(RESTRICTED_DONE, false),
                service,
                AutoBlockerWidget.widgetCount(this) > 0,
                // 캐시는 접근성 재연결 때 미확인으로 돌아가므로 한 번이라도 읽었는지로 판정한다.
                AutoBlockerWidget.everObserved(this),
                prefs.getBoolean(FINISH_DONE, false)};

        int firstTodo = -1;
        for (int i = 0; i < done.length; i++) {
            if (!done[i] && firstTodo < 0) firstTodo = i;
            marks[i].setText(done[i] ? R.string.mark_done : R.string.mark_todo);
            if (done[i]) marks[i].setTextColor(getColor(R.color.step_done));
            else marks[i].setTextColor(defaultMarkColor);
            bodies[i].setVisibility(i == firstTodo ? View.VISIBLE : View.GONE);
            // 권한 허용 전이면 접근성 단계를, 접근성이 꺼져 있으면 그 뒤 단계를 흐리게 둔다.
            steps[i].setAlpha((i == 1 && !done[0]) || (i > 1 && !done[1]) ? 0.5f : 1f);
        }

        if (firstTodo == 2) {
            findViewById(R.id.btn_pin_widget).setVisibility(
                    mgr.isRequestPinAppWidgetSupported() ? View.VISIBLE : View.GONE);
        }
        if (firstTodo == 4) {
            // 미확인(-1)도 켜기 버튼을 보인다. 이미 켜져 있으면 서비스가 누르지 않는다(AutoTapService target 검사).
            findViewById(R.id.step5_enable).setVisibility(state != 1 ? View.VISIBLE : View.GONE);
            findViewById(R.id.step5_blocker_on).setVisibility(state == 1 ? View.VISIBLE : View.GONE);
        }

        View card = findViewById(R.id.ready_card);
        if (firstTodo < 0) {
            card.setVisibility(View.VISIBLE);
            ((TextView) findViewById(R.id.ready_state)).setText(getString(R.string.ready_state,
                    getString(state == 1 ? R.string.state_on : state == 0 ? R.string.state_off : R.string.state_unknown)));
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

    /** 상태 감지 기록(최신이 위). 외부로 보내지 않는다. */
    private void showStateLog() {
        List<String> lines = StateLog.lines(this);
        TextView body = new TextView(this);
        int pad = (int) (16 * getResources().getDisplayMetrics().density);
        body.setPadding(pad, pad, pad, pad);
        body.setTextIsSelectable(true);
        body.setTypeface(android.graphics.Typeface.MONOSPACE);
        body.setTextSize(11);
        body.setText(lines.isEmpty() ? getString(R.string.state_log_empty) : String.join("\n", lines));
        android.widget.ScrollView scroll = new android.widget.ScrollView(this);
        scroll.addView(body);
        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.state_log_title, StateLog.MAX))
                .setView(scroll)
                .setPositiveButton(R.string.state_log_close, null)
                .setNeutralButton(R.string.state_log_clear, (d, w) -> {
                    StateLog.clear(this);
                    Toast.makeText(this, R.string.state_log_cleared, Toast.LENGTH_SHORT).show();
                })
                .show();
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

    /** 위젯 추가 버튼이 고정 요청하는 provider. 처음 놓는 위젯은 2×1 이고 3×1 은 위젯 목록에서 고른다. */
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
        return AutoBlockerWidget.serviceEnabled(this);
    }

    private String versionName() {
        try {
            return getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (PackageManager.NameNotFoundException e) {
            return "?";
        }
    }
}
