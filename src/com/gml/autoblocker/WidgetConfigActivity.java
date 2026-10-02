package com.gml.autoblocker;

import android.app.Activity;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProviderInfo;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.EditText;
import android.widget.RadioGroup;

/**
 * 위젯별 스타일(컬러, 모노톤, 사용자 지정 색, API 31 이상의 시스템 색) 설정. 런처가 위젯을 놓을 때나 길게 눌러 다시 설정할 때 연다.
 * 외부에서 열리므로 받은 id 가 이 앱 위젯일 때만 다룬다.
 */
public class WidgetConfigActivity extends Activity {
    private int id = AppWidgetManager.INVALID_APPWIDGET_ID;

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(LocaleHelper.wrap(base));
    }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        // 뒤로 가기나 검증 실패는 취소로 끝난다.
        setResult(RESULT_CANCELED);
        Bundle extras = getIntent().getExtras();
        if (extras != null) id = extras.getInt(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID);
        final AppWidgetManager mgr = AppWidgetManager.getInstance(this);
        AppWidgetProviderInfo info = id == AppWidgetManager.INVALID_APPWIDGET_ID ? null : mgr.getAppWidgetInfo(id);
        if (info == null || info.provider == null || !getPackageName().equals(info.provider.getPackageName())) {
            finish();
            return;
        }
        setContentView(R.layout.activity_widget_config);
        final RadioGroup group = (RadioGroup) findViewById(R.id.style_group);
        final View fields = findViewById(R.id.custom_fields);
        boolean hasSystem = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S;
        findViewById(R.id.style_system).setVisibility(hasSystem ? View.VISIBLE : View.GONE);
        findViewById(R.id.style_system_hint).setVisibility(hasSystem ? View.VISIBLE : View.GONE);
        // CUSTOM_STATES 순서(켜짐, 꺼짐, 미확인)
        final EditText[] inputs = {
                (EditText) findViewById(R.id.color_on),
                (EditText) findViewById(R.id.color_off),
                (EditText) findViewById(R.id.color_unknown) };
        final View[] swatches = {
                findViewById(R.id.swatch_on), findViewById(R.id.swatch_off), findViewById(R.id.swatch_unknown) };
        final int[] stateWords = { R.string.state_on, R.string.state_off, R.string.state_unknown };
        for (int k = 0; k < inputs.length; k++) {
            final EditText input = inputs[k];
            final View swatch = swatches[k];
            final int word = stateWords[k];
            final int stored = AutoBlockerWidget.customColor(this, id, AutoBlockerWidget.CUSTOM_STATES[k]);
            // 맞는 hex 를 치면 견본이 따라간다. 틀린 동안은 마지막 맞는 색을 둔다.
            input.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
                @Override public void onTextChanged(CharSequence s, int a, int b, int c) {}
                @Override public void afterTextChanged(Editable s) {
                    Integer c = HexColor.parse(s.toString());
                    if (c != null) swatch.setBackground(ColorPickerDialog.swatch(WidgetConfigActivity.this, c));
                }
            });
            input.setText(HexColor.format(stored));
            swatch.setOnClickListener(v -> {
                Integer cur = HexColor.parse(input.getText().toString());
                ColorPickerDialog.show(this, cur != null ? cur : stored, getString(word), picked -> {
                    input.setText(HexColor.format(picked));
                    input.setError(null);
                });
            });
        }
        int saved = AutoBlockerWidget.style(this, id);
        group.setOnCheckedChangeListener((g, checked) ->
                fields.setVisibility(checked == R.id.style_custom ? View.VISIBLE : View.GONE));
        group.check(saved == AutoBlockerWidget.STYLE_MONO ? R.id.style_mono
                : saved == AutoBlockerWidget.STYLE_CUSTOM ? R.id.style_custom
                : saved == AutoBlockerWidget.STYLE_SYSTEM && hasSystem ? R.id.style_system : R.id.style_color);
        findViewById(R.id.btn_save).setOnClickListener(v -> {
            int checked = group.getCheckedRadioButtonId();
            int style = checked == R.id.style_mono ? AutoBlockerWidget.STYLE_MONO
                    : checked == R.id.style_custom ? AutoBlockerWidget.STYLE_CUSTOM
                    : checked == R.id.style_system ? AutoBlockerWidget.STYLE_SYSTEM : AutoBlockerWidget.STYLE_COLOR;
            if (style == AutoBlockerWidget.STYLE_CUSTOM) {
                int[] colors = new int[inputs.length];
                boolean ok = true;
                for (int k = 0; k < inputs.length; k++) {
                    Integer c = HexColor.parse(inputs[k].getText().toString());
                    if (c == null) {
                        inputs[k].setError(getString(R.string.error_hex));
                        ok = false;
                    } else {
                        inputs[k].setError(null);
                        colors[k] = c;
                    }
                }
                if (!ok) return;
                AutoBlockerWidget.saveCustom(this, id, colors);
            }
            AutoBlockerWidget.saveStyle(this, id, style);
            AutoBlockerWidget.render(this, mgr, new int[] { id });
            setResult(RESULT_OK, new Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id));
            finish();
        });
    }
}
