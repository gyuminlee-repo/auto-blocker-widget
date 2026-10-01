package com.gml.autoblocker;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.provider.Settings;
import android.widget.RemoteViews;
import android.widget.Toast;

public class AutoBlockerWidget extends AppWidgetProvider {
    /** Settings.Secure 키. One UI 버전에 따라 다를 수 있다(기기에서 미확인). */
    static final String KEY = "auto_blocker_enabled";
    static final String ACTION_TOGGLE = "com.gml.autoblocker.TOGGLE";

    private enum View { ON, OFF, NO_PERMISSION, FAILED }

    @Override
    public void onUpdate(Context ctx, AppWidgetManager mgr, int[] ids) {
        render(ctx, mgr, ids, currentView(ctx));
    }

    @Override
    public void onReceive(Context ctx, Intent intent) {
        super.onReceive(ctx, intent);
        if (ACTION_TOGGLE.equals(intent.getAction())) {
            toggle(ctx);
        }
    }

    private static View currentView(Context ctx) {
        return read(ctx) == 1 ? View.ON : View.OFF;
    }

    private static int read(Context ctx) {
        return Settings.Secure.getInt(ctx.getContentResolver(), KEY, 0);
    }

    private void toggle(Context ctx) {
        int next = read(ctx) == 1 ? 0 : 1;
        View result;
        try {
            boolean ok = Settings.Secure.putInt(ctx.getContentResolver(), KEY, next);
            if (!ok) {
                toast(ctx, R.string.toast_write_fail);
                result = View.FAILED;
            } else if (read(ctx) != next) {
                toast(ctx, R.string.toast_mismatch);
                result = View.FAILED;
            } else {
                result = next == 1 ? View.ON : View.OFF;
            }
        } catch (SecurityException e) {
            toast(ctx, R.string.toast_grant);
            result = View.NO_PERMISSION;
        }
        AppWidgetManager mgr = AppWidgetManager.getInstance(ctx);
        int[] ids = mgr.getAppWidgetIds(new ComponentName(ctx, AutoBlockerWidget.class));
        render(ctx, mgr, ids, result);
    }

    private static void toast(Context ctx, int resId) {
        Toast.makeText(ctx, resId, Toast.LENGTH_LONG).show();
    }

    private static void render(Context ctx, AppWidgetManager mgr, int[] ids, View v) {
        int text;
        int bg;
        switch (v) {
            case ON: text = R.string.state_on; bg = R.drawable.bg_on; break;
            case OFF: text = R.string.state_off; bg = R.drawable.bg_off; break;
            case NO_PERMISSION: text = R.string.state_err; bg = R.drawable.bg_err; break;
            default: text = R.string.state_fail; bg = R.drawable.bg_err; break;
        }
        Intent i = new Intent(ctx, AutoBlockerWidget.class).setAction(ACTION_TOGGLE);
        PendingIntent pi = PendingIntent.getBroadcast(ctx, 0, i, PendingIntent.FLAG_IMMUTABLE);
        RemoteViews rv = new RemoteViews(ctx.getPackageName(), R.layout.widget);
        rv.setTextViewText(R.id.label, ctx.getString(text));
        rv.setInt(R.id.root, "setBackgroundResource", bg);
        rv.setOnClickPendingIntent(R.id.root, pi);
        mgr.updateAppWidget(ids, rv);
    }
}
