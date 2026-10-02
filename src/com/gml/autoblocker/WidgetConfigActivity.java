package com.gml.autoblocker;

import android.app.Activity;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProviderInfo;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.widget.RadioGroup;

/**
 * 위젯별 스타일(컬러 또는 모노톤) 설정. 런처가 위젯을 놓을 때나 길게 눌러 다시 설정할 때 연다.
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
        group.check(AutoBlockerWidget.style(this, id) == AutoBlockerWidget.STYLE_MONO
                ? R.id.style_mono : R.id.style_color);
        findViewById(R.id.btn_save).setOnClickListener(v -> {
            int style = group.getCheckedRadioButtonId() == R.id.style_mono
                    ? AutoBlockerWidget.STYLE_MONO : AutoBlockerWidget.STYLE_COLOR;
            AutoBlockerWidget.saveStyle(this, id, style);
            AutoBlockerWidget.render(this, mgr, new int[] { id });
            setResult(RESULT_OK, new Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id));
            finish();
        });
    }
}
