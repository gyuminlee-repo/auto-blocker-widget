package com.gml.autoblocker;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.widget.RemoteViews;

/**
 * 상태는 rampart 설정 화면에서 접근성 서비스가 마지막으로 본 값의 캐시다.
 * 일반 앱은 rampart_* 설정 키를 읽을 수 없으므로 Settings 를 읽지 않는다.
 */
public class AutoBlockerWidget extends AppWidgetProvider {
    static final String TAG = "AutoBlocker";
    static final String STATE_PREFS = "state";
    static final String LAST_STATE = "lastKnownState";
    static final String LAST_AT = "lastKnownAt";

    static void saveState(Context ctx, boolean on) {
        ctx.getSharedPreferences(STATE_PREFS, Context.MODE_PRIVATE).edit()
                .putInt(LAST_STATE, on ? 1 : 0)
                .putLong(LAST_AT, System.currentTimeMillis()).commit();
    }

    /** 1 켜짐, 0 꺼짐, -1 캐시 없음. */
    static int cached(Context ctx) {
        SharedPreferences p = ctx.getSharedPreferences(STATE_PREFS, Context.MODE_PRIVATE);
        return p.getInt(LAST_STATE, -1);
    }

    @Override
    public void onUpdate(Context ctx, AppWidgetManager mgr, int[] ids) {
        render(ctx, mgr, ids);
    }

    static void refresh(Context ctx) {
        AppWidgetManager mgr = AppWidgetManager.getInstance(ctx);
        int[] ids = mgr.getAppWidgetIds(new ComponentName(ctx, AutoBlockerWidget.class));
        if (ids.length > 0) render(ctx, mgr, ids);
    }

    private static void render(Context ctx, AppWidgetManager mgr, int[] ids) {
        int text;
        int bg;
        switch (cached(ctx)) {
            case 1: text = R.string.state_on; bg = R.drawable.bg_on; break;
            case 0: text = R.string.state_off; bg = R.drawable.bg_off; break;
            default: text = R.string.state_unknown; bg = R.drawable.bg_unknown; break;
        }
        Intent i = new Intent(ctx, TrampolineActivity.class);
        PendingIntent pi = PendingIntent.getActivity(ctx, 0, i, PendingIntent.FLAG_IMMUTABLE);
        RemoteViews rv = new RemoteViews(ctx.getPackageName(), R.layout.widget);
        rv.setTextViewText(R.id.label, ctx.getString(text));
        rv.setInt(R.id.root, "setBackgroundResource", bg);
        rv.setOnClickPendingIntent(R.id.root, pi);
        mgr.updateAppWidget(ids, rv);
    }
}
