package com.gml.autoblocker;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.provider.Settings;
import android.util.Log;
import android.widget.RemoteViews;

public class AutoBlockerWidget extends AppWidgetProvider {
    /** One UI 9.0 (SM-F971N) 에서 관측한 마스터 키. 읽기만 가능하고 쓰기는 거부된다. */
    static final String KEY = "rampart_main_switch_enabled";
    static final String TAG = "AutoBlocker";

    /** 1 켜짐, 0 꺼짐, -1 읽기 불가. */
    static int read(Context ctx) {
        try {
            int v = Settings.Secure.getInt(ctx.getContentResolver(), KEY);
            Log.i(TAG, "read " + KEY + "=" + v);
            return v;
        } catch (Settings.SettingNotFoundException e) {
            Log.i(TAG, "read " + KEY + " not found");
            return -1;
        }
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
        switch (read(ctx)) {
            case 1: text = R.string.state_on; bg = R.drawable.bg_on; break;
            case 0: text = R.string.state_off; bg = R.drawable.bg_off; break;
            default: text = R.string.state_unknown; bg = R.drawable.bg_err; break;
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
