package com.gml.autoblocker;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.util.ArrayMap;
import android.util.SizeF;
import android.view.View;
import android.widget.RemoteViews;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Map;

/**
 * 상태는 rampart 설정 화면에서 접근성 서비스가 마지막으로 본 값의 캐시다.
 * 일반 앱은 rampart_* 설정 키를 읽을 수 없으므로 Settings 를 읽지 않는다.
 */
public class AutoBlockerWidget extends AppWidgetProvider {
    static final String TAG = "AutoBlocker";
    static final String STATE_PREFS = "state";
    static final String LAST_STATE = "lastKnownState";
    static final String LAST_AT = "lastKnownAt";
    static final String STYLE_PREFS = "widget_style";
    static final int STYLE_COLOR = 0;
    static final int STYLE_MONO = 1;
    /** 이 폭(dp)보다 좁으면 아이콘만 보인다. 2칸 기본 minWidth 와 같다. */
    private static final float SMALL_MAX_DP = 110f;

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

    /** 폰 언어나 앱별 언어가 바뀌면 저장된 위젯 문구를 새 언어로 다시 넣는다. */
    @Override
    public void onReceive(Context ctx, Intent intent) {
        if (Intent.ACTION_LOCALE_CHANGED.equals(intent.getAction())) refresh(ctx);
        else super.onReceive(ctx, intent);
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

    @Override
    public void onAppWidgetOptionsChanged(Context ctx, AppWidgetManager mgr, int id, Bundle options) {
        render(ctx, mgr, new int[] { id });
    }

    /** 위젯을 지우면 그 id 의 스타일 저장값도 지운다. */
    @Override
    public void onDeleted(Context ctx, int[] ids) {
        SharedPreferences.Editor e = ctx.getSharedPreferences(STYLE_PREFS, Context.MODE_PRIVATE).edit();
        for (int id : ids) e.remove(styleKey(id));
        e.commit();
    }

    static String styleKey(int id) {
        return "style_" + id;
    }

    /** 저장값이 없으면 STYLE_COLOR. */
    static int style(Context ctx, int id) {
        return ctx.getSharedPreferences(STYLE_PREFS, Context.MODE_PRIVATE).getInt(styleKey(id), STYLE_COLOR);
    }

    static void saveStyle(Context ctx, int id, int style) {
        ctx.getSharedPreferences(STYLE_PREFS, Context.MODE_PRIVATE).edit().putInt(styleKey(id), style).commit();
    }

    static void render(Context ctx, AppWidgetManager mgr, int[] ids) {
        for (int id : ids) {
            RemoteViews full = build(ctx, R.layout.widget, id);
            RemoteViews rv;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                // 런처가 위젯 크기에 맞는 쪽을 고른다(dp). 좁으면 아이콘만.
                Map<SizeF, RemoteViews> m = new ArrayMap<>();
                m.put(new SizeF(40f, 40f), build(ctx, R.layout.widget_small, id));
                m.put(new SizeF(SMALL_MAX_DP, 40f), full);
                rv = new RemoteViews(m);
            } else {
                int w = mgr.getAppWidgetOptions(id).getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH);
                rv = (w > 0 && w < SMALL_MAX_DP) ? build(ctx, R.layout.widget_small, id) : full;
            }
            mgr.updateAppWidget(id, rv);
        }
    }

    private static RemoteViews build(Context ctx, int layout, int id) {
        // 런처가 레이아웃 문자열을 시스템 로캘로 풀지 않도록 문구는 모두 앱 언어 Context 로 넣는다.
        Context loc = LocaleHelper.wrap(ctx.getApplicationContext());
        boolean mono = style(ctx, id) == STYLE_MONO;
        int state = cached(ctx);
        int text, bg, icon;
        switch (state) {
            case 1: text = R.string.state_on; bg = R.drawable.bg_on; icon = R.drawable.ic_shield_on; break;
            case 0: text = R.string.state_off; bg = R.drawable.bg_off; icon = R.drawable.ic_shield_off; break;
            default: text = R.string.state_unknown; bg = R.drawable.bg_unknown; icon = R.drawable.ic_shield_unknown; break;
        }
        if (mono) bg = R.drawable.bg_mono;
        Intent i = new Intent(ctx, TrampolineActivity.class);
        PendingIntent pi = PendingIntent.getActivity(ctx, 0, i, PendingIntent.FLAG_IMMUTABLE);
        RemoteViews rv = new RemoteViews(ctx.getPackageName(), layout);
        String stateText = loc.getString(text);
        rv.setImageViewResource(R.id.icon, icon);
        rv.setInt(R.id.root, "setBackgroundResource", bg);
        if (layout == R.layout.widget) {
            rv.setTextViewText(R.id.label, stateText);
            long at = ctx.getSharedPreferences(STATE_PREFS, Context.MODE_PRIVATE).getLong(LAST_AT, 0);
            if (state >= 0 && at > 0) {
                String hhmm = new SimpleDateFormat("HH:mm", Locale.KOREA).format(new Date(at));
                rv.setTextViewText(R.id.checked_at, loc.getString(R.string.checked_at, hhmm));
                rv.setViewVisibility(R.id.checked_at, View.VISIBLE);
            } else {
                rv.setViewVisibility(R.id.checked_at, View.GONE);
            }
        }
        rv.setContentDescription(R.id.root, loc.getString(R.string.desc_fmt, stateText));
        rv.setOnClickPendingIntent(R.id.root, pi);
        return rv;
    }
}
