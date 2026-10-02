package com.gml.autoblocker;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.drawable.GradientDrawable;
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
    static final int STYLE_CUSTOM = 2;
    /** 배경화면 색(Material You). API 31 이상만. 그 아래에서는 STYLE_COLOR 로 그린다. */
    static final int STYLE_SYSTEM = 3;
    /** 사용자 지정 색을 저장하는 상태 순서. cached() 값 1, 0, -1 과 짝이다. */
    static final int[] CUSTOM_STATES = { 1, 0, -1 };
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

    /** 위젯과 빠른 설정 타일을 함께 다시 그린다. 캐시나 언어가 바뀔 때 부른다. */
    static void refresh(Context ctx) {
        ShieldTile.requestUpdate(ctx);
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
        for (int id : ids) {
            e.remove(styleKey(id));
            for (int st : CUSTOM_STATES) e.remove(customKey(id, st));
        }
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

    static String customKey(int id, int state) {
        return "custom_" + (state == 1 ? "on" : state == 0 ? "off" : "unknown") + "_" + id;
    }

    /** 컬러 스타일의 상태별 바탕 drawable. */
    private static int colorBg(int state) {
        return state == 1 ? R.drawable.bg_on : state == 0 ? R.drawable.bg_off : R.drawable.bg_unknown;
    }

    /** 컬러 스타일 바탕색. 사용자 지정 색의 처음 값으로 쓴다. */
    static int defaultColor(Context ctx, int state) {
        return ((GradientDrawable) ctx.getDrawable(colorBg(state))).getColor().getDefaultColor();
    }

    /** 저장값이 없으면 컬러 스타일 바탕색. */
    static int customColor(Context ctx, int id, int state) {
        return ctx.getSharedPreferences(STYLE_PREFS, Context.MODE_PRIVATE)
                .getInt(customKey(id, state), defaultColor(ctx, state));
    }

    /** colors 는 CUSTOM_STATES 순서. */
    static void saveCustom(Context ctx, int id, int[] colors) {
        SharedPreferences.Editor e = ctx.getSharedPreferences(STYLE_PREFS, Context.MODE_PRIVATE).edit();
        for (int k = 0; k < CUSTOM_STATES.length; k++) e.putInt(customKey(id, CUSTOM_STATES[k]), colors[k]);
        e.commit();
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
        int style = style(ctx, id);
        if (style == STYLE_SYSTEM && Build.VERSION.SDK_INT < Build.VERSION_CODES.S) style = STYLE_COLOR;
        boolean system = style == STYLE_SYSTEM;
        boolean mono = style == STYLE_MONO;
        boolean custom = style == STYLE_CUSTOM;
        int state = cached(ctx);
        int text, bg, icon;
        switch (state) {
            case 1: text = R.string.state_on; icon = R.drawable.ic_shield_on;
                bg = mono ? R.drawable.bg_mono_on : R.drawable.bg_on; break;
            case 0: text = R.string.state_off; icon = R.drawable.ic_shield_off;
                bg = mono ? R.drawable.bg_mono_off : R.drawable.bg_off; break;
            default: text = R.string.state_unknown; icon = R.drawable.ic_shield_unknown;
                bg = mono ? R.drawable.bg_mono_unknown : R.drawable.bg_unknown; break;
        }
        // 사용자 지정은 바탕 대비가 높은 쪽, 모노톤은 밝은 켜짐만 진한 글자와 아이콘을 쓴다.
        // 나머지도 흰색을 명시해 RemoteViews 를 다시 쓸 때 이전 색이 남지 않게 한다.
        int customBg = custom ? customColor(ctx, id, state) : 0;
        boolean dark = custom ? HexColor.darkText(customBg) : mono && state == 1;
        int fg = ctx.getColor(dark ? R.color.widget_fg_dark : R.color.widget_fg_light);
        int fgSub = ctx.getColor(dark ? R.color.widget_fg_dark_sub : R.color.widget_fg_light_sub);
        Intent i = new Intent(ctx, TrampolineActivity.class);
        PendingIntent pi = PendingIntent.getActivity(ctx, 0, i, PendingIntent.FLAG_IMMUTABLE);
        RemoteViews rv = new RemoteViews(ctx.getPackageName(), layout);
        String stateText = loc.getString(text);
        // 시스템 색은 색 리소스 ID 를 넘겨 런처가 배경화면 팔레트로 다시 푼다(API 31).
        int sysBg = 0, sysFg = 0;
        if (system) {
            sysBg = state == 1 ? android.R.color.system_accent1_200
                    : state == 0 ? android.R.color.system_neutral1_800 : android.R.color.system_neutral2_600;
            sysFg = state == 1 ? android.R.color.system_accent1_900
                    : state == 0 ? android.R.color.system_neutral1_50 : android.R.color.system_neutral1_10;
        }
        rv.setImageViewResource(R.id.icon, icon);
        if (system && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            rv.setColor(R.id.icon, "setColorFilter", sysFg);
            rv.setInt(R.id.root, "setBackgroundResource", 0);
            rv.setColor(R.id.bg, "setColorFilter", sysBg);
            rv.setViewVisibility(R.id.bg, View.VISIBLE);
        } else if (custom) {
            // 둥근 흰 바탕 층(bg_custom)에 색을 입힌다. View.setBackgroundTintList 는 API 31 부터만 원격 호출된다.
            rv.setInt(R.id.root, "setBackgroundResource", 0);
            rv.setInt(R.id.icon, "setColorFilter", fg);
            rv.setInt(R.id.bg, "setColorFilter", customBg);
            rv.setViewVisibility(R.id.bg, View.VISIBLE);
        } else {
            rv.setInt(R.id.icon, "setColorFilter", fg);
            rv.setInt(R.id.root, "setBackgroundResource", bg);
            rv.setViewVisibility(R.id.bg, View.GONE);
        }
        if (layout == R.layout.widget) {
            rv.setTextViewText(R.id.label, stateText);
            if (system && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                // 시스템 팔레트에는 반투명 단계가 없어 확인 시각도 글자와 같은 색을 쓴다.
                rv.setColor(R.id.label, "setTextColor", sysFg);
                rv.setColor(R.id.checked_at, "setTextColor", sysFg);
            } else {
                rv.setTextColor(R.id.label, fg);
                rv.setTextColor(R.id.checked_at, fgSub);
            }
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
