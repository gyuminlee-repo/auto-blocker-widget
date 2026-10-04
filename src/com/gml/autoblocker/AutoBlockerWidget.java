package com.gml.autoblocker;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.LocaleList;
import android.os.Looper;
import android.provider.Settings;
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
 * 접근성 서비스가 켜져 있는 동안은 변경 알림으로 캐시를 뒤집는다(AutoTapService, 시험 중).
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
    /** 위젯 토글(0), 빠른 설정 타일(1)과 겹치지 않는 PendingIntent 요청 코드. */
    private static final int REQ_PLAY_PROTECT = 2;

    /**
     * 캐시 값의 출처. SRC_SCREEN 은 설정 화면 스위치, SRC_FLIP 은 설정 변경 알림으로 뒤집은 값,
     * SRC_DEBUG 는 서비스 (재)연결 때 USB 또는 무선 디버깅이 켜져 있어 꺼짐으로 확정한 값.
     */
    static final String LAST_SRC = "lastKnownSource";
    static final String SRC_SCREEN = "screen";
    static final String SRC_FLIP = "flip";
    static final String SRC_DEBUG = "debug";
    /** 한 번이라도 상태를 읽은 적 있는지. 캐시가 미확인으로 돌아가도 설정 안내 ④ 단계는 완료로 둔다. */
    static final String EVER_OBSERVED = "everObserved";

    static void saveState(Context ctx, boolean on, String src) {
        ctx.getSharedPreferences(STATE_PREFS, Context.MODE_PRIVATE).edit()
                .putInt(LAST_STATE, on ? 1 : 0)
                .putString(LAST_SRC, src)
                .putBoolean(EVER_OBSERVED, true)
                .putLong(LAST_AT, System.currentTimeMillis()).commit();
    }

    /** 캐시를 미확인(-1)으로 되돌린다. 이전 버전에서 캐시만 있던 경우도 ④ 단계 완료가 유지되게 표식을 남긴다. */
    static void clearState(Context ctx) {
        SharedPreferences p = ctx.getSharedPreferences(STATE_PREFS, Context.MODE_PRIVATE);
        SharedPreferences.Editor e = p.edit().remove(LAST_STATE).remove(LAST_AT).remove(LAST_SRC);
        if (p.contains(LAST_STATE)) e.putBoolean(EVER_OBSERVED, true);
        e.commit();
    }

    static String source(Context ctx) {
        return ctx.getSharedPreferences(STATE_PREFS, Context.MODE_PRIVATE).getString(LAST_SRC, SRC_SCREEN);
    }

    static boolean everObserved(Context ctx) {
        SharedPreferences p = ctx.getSharedPreferences(STATE_PREFS, Context.MODE_PRIVATE);
        return p.getBoolean(EVER_OBSERVED, false) || p.contains(LAST_STATE);
    }

    /**
     * 접근성 서비스(AutoTapService)가 켜져 있는지. 공개 키 Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES 는
     * 권한 없이 읽을 수 있고 켜진 서비스 컴포넌트를 ':' 로 이어 둔 목록이라 여기에 이 앱 서비스가 있는지로 판정한다.
     * AutoTapService.isRunning() 은 그 프로세스의 정적 필드라 위젯이 그려지는 시점(프로세스 재시작 직후 등)에는 믿을 수 없어 쓰지 않는다.
     * 시스템 설정에서 서비스를 끌 때 서비스 콜백(onUnbind, onDestroy) 없이 프로세스가 죽으면
     * 위젯은 다음 갱신(앱 열기, 위젯 업데이트 주기, 언어 변경) 때 이 값을 다시 읽어 반영한다.
     */
    static boolean serviceEnabled(Context ctx) {
        String list = Settings.Secure.getString(ctx.getContentResolver(),
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        if (list == null) return false;
        ComponentName cn = new ComponentName(ctx, AutoTapService.class);
        String full = cn.flattenToString();
        String shrt = cn.flattenToShortString();
        for (String s : list.split(":")) {
            if (s.equalsIgnoreCase(full) || s.equalsIgnoreCase(shrt)) return true;
        }
        return false;
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
        else if (Intent.ACTION_MY_PACKAGE_REPLACED.equals(intent.getAction())) {
            // 덮어 설치 뒤 런처가 위젯을 initialLayout 으로 되돌린다. 즉시 한 번으로는 런처가 뒤이어 덮어써 4초 뒤 한 번 더 그린다
            // (형제 앱 실측, Galaxy Z Fold8 One UI 9). goAsync 제한(약 10초) 안이다.
            refresh(ctx);
            final Context app = ctx.getApplicationContext();
            final PendingResult pr = goAsync();
            new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
                @Override public void run() {
                    try { refresh(app); } finally { pr.finish(); }
                }
            }, 4000);
        }
        else super.onReceive(ctx, intent);
    }

    /**
     * 3×1 provider(AutoBlockerWideWidget)면 true. 넓은 레이아웃은 위젯 폭이 아니라 이것으로 고른다.
     * 폭으로 고르면 칸이 넓은 화면(Galaxy Z Fold 펼친 화면 등)에서 2칸도 넓은 쪽으로 갈 수 있다.
     */
    boolean wide() {
        return false;
    }

    /** 위젯 id 가 3×1 provider 소속인지. provider 를 모르는 호출부(WidgetConfigActivity)용. */
    static boolean isWide(ComponentName provider) {
        return provider != null && AutoBlockerWideWidget.class.getName().equals(provider.getClassName());
    }

    @Override
    public void onUpdate(Context ctx, AppWidgetManager mgr, int[] ids) {
        render(ctx, mgr, ids, wide());
    }

    /** 위젯과 빠른 설정 타일을 함께 다시 그린다. 캐시나 언어가 바뀔 때 부른다. 2×1 과 3×1 provider 를 모두 갱신한다. */
    static void refresh(Context ctx) {
        ShieldTile.requestUpdate(ctx);
        AppWidgetManager mgr = AppWidgetManager.getInstance(ctx);
        int[] ids = mgr.getAppWidgetIds(new ComponentName(ctx, AutoBlockerWidget.class));
        if (ids.length > 0) render(ctx, mgr, ids, false);
        int[] wideIds = mgr.getAppWidgetIds(new ComponentName(ctx, AutoBlockerWideWidget.class));
        if (wideIds.length > 0) render(ctx, mgr, wideIds, true);
    }

    /** 두 provider 에 놓인 위젯 수의 합. 설정 안내의 「위젯 추가」 단계 판정용. */
    static int widgetCount(Context ctx) {
        AppWidgetManager mgr = AppWidgetManager.getInstance(ctx);
        return mgr.getAppWidgetIds(new ComponentName(ctx, AutoBlockerWidget.class)).length
                + mgr.getAppWidgetIds(new ComponentName(ctx, AutoBlockerWideWidget.class)).length;
    }

    @Override
    public void onAppWidgetOptionsChanged(Context ctx, AppWidgetManager mgr, int id, Bundle options) {
        render(ctx, mgr, new int[] { id }, wide());
    }

    /** 위젯을 지우면 그 id 의 스타일과 언어 저장값도 지운다. */
    @Override
    public void onDeleted(Context ctx, int[] ids) {
        SharedPreferences.Editor e = ctx.getSharedPreferences(STYLE_PREFS, Context.MODE_PRIVATE).edit();
        for (int id : ids) {
            e.remove(styleKey(id));
            e.remove(langKey(id));
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

    static String langKey(int id) {
        return "lang_" + id;
    }

    /** 위젯 언어. "" 은 앱 언어 따라감(저장값이 없을 때도), "en", "ko". */
    static String lang(Context ctx, int id) {
        return ctx.getSharedPreferences(STYLE_PREFS, Context.MODE_PRIVATE).getString(langKey(id), "");
    }

    static void saveLang(Context ctx, int id, String tag) {
        ctx.getSharedPreferences(STYLE_PREFS, Context.MODE_PRIVATE).edit().putString(langKey(id), tag).commit();
    }

    /** 위젯 문구를 읽을 Context. 언어를 지정하지 않았으면 앱 언어(LocaleHelper.wrap) 그대로다. */
    private static Context widgetLocale(Context ctx, int id) {
        Context app = ctx.getApplicationContext();
        String tag = lang(ctx, id);
        if (tag.isEmpty()) return LocaleHelper.wrap(app);
        Configuration c = new Configuration(app.getResources().getConfiguration());
        c.setLocales(LocaleList.forLanguageTags(tag));
        return app.createConfigurationContext(c);
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

    /**
     * wide 는 ids 가 속한 provider 가 3×1 인지다. 3×1 은 크기와 API 수준에 상관없이 항상 widget_wide 를 쓴다.
     * widget_info_wide.xml 의 minResizeWidth 가 3칸 아래로 줄이지 못하게 하므로 좁은 대체 레이아웃을 두지 않는다.
     */
    static void render(Context ctx, AppWidgetManager mgr, int[] ids, boolean wide) {
        for (int id : ids) {
            RemoteViews rv;
            if (wide) {
                rv = build(ctx, R.layout.widget_wide, id);
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                // 런처가 위젯 크기에 맞는 쪽을 고른다(dp). 좁으면 아이콘만.
                Map<SizeF, RemoteViews> m = new ArrayMap<>();
                m.put(new SizeF(40f, 40f), build(ctx, R.layout.widget_small, id));
                m.put(new SizeF(SMALL_MAX_DP, 40f), build(ctx, R.layout.widget, id));
                rv = new RemoteViews(m);
            } else {
                // 크기를 바꾸면 onAppWidgetOptionsChanged 가 다시 부른다.
                int w = mgr.getAppWidgetOptions(id).getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH);
                rv = build(ctx, (w > 0 && w < SMALL_MAX_DP) ? R.layout.widget_small : R.layout.widget, id);
            }
            mgr.updateAppWidget(id, rv);
        }
    }

    private static RemoteViews build(Context ctx, int layout, int id) {
        // 런처가 레이아웃 문자열을 시스템 로캘로 풀지 않도록 문구는 모두 이 위젯 언어(기본은 앱 언어) Context 로 넣는다.
        Context loc = widgetLocale(ctx, id);
        int style = style(ctx, id);
        if (style == STYLE_SYSTEM && Build.VERSION.SDK_INT < Build.VERSION_CODES.S) style = STYLE_COLOR;
        boolean system = style == STYLE_SYSTEM;
        boolean mono = style == STYLE_MONO;
        boolean custom = style == STYLE_CUSTOM;
        // 서비스가 꺼져 있으면 캐시와 상관없이 미확인 모양(아이콘, 바탕, 색)에 「설정 필요」 글자를 쓴다. 탭은 그대로 TrampolineActivity 가 설정 안내로 보낸다.
        boolean setup = !serviceEnabled(ctx);
        int state = setup ? -1 : cached(ctx);
        int text, bg, icon;
        switch (state) {
            case 1: text = R.string.state_on; icon = R.drawable.ic_shield_on;
                bg = mono ? R.drawable.bg_mono_on : R.drawable.bg_on; break;
            case 0: text = R.string.state_off; icon = R.drawable.ic_shield_off;
                bg = mono ? R.drawable.bg_mono_off : R.drawable.bg_off; break;
            default: text = setup ? R.string.widget_state_setup : R.string.state_unknown;
                icon = setup ? R.drawable.ic_shield_setup : R.drawable.ic_shield_unknown;
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
        if (layout != R.layout.widget_small) {
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
        rv.setContentDescription(R.id.root, setup ? loc.getString(R.string.desc_setup)
                : loc.getString(R.string.desc_fmt, stateText));
        rv.setOnClickPendingIntent(R.id.root, pi);
        if (layout == R.layout.widget_wide) {
            // 상태 색은 Auto Blocker 쪽만. 바로가기는 같은 글자색 한 가지로 칠한다.
            rv.setTextViewText(R.id.pp_label, loc.getString(R.string.widget_play_protect));
            if (system && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                rv.setColor(R.id.pp_icon, "setColorFilter", sysFg);
                rv.setColor(R.id.pp_label, "setTextColor", sysFg);
                rv.setColor(R.id.pp_divider, "setBackgroundColor", sysFg);
            } else {
                rv.setInt(R.id.pp_icon, "setColorFilter", fg);
                rv.setTextColor(R.id.pp_label, fg);
                rv.setInt(R.id.pp_divider, "setBackgroundColor", fgSub);
            }
            rv.setContentDescription(R.id.play_protect, loc.getString(R.string.btn_play_protect));
            rv.setOnClickPendingIntent(R.id.play_protect, PendingIntent.getActivity(ctx, REQ_PLAY_PROTECT,
                    new Intent(ctx, PlayProtectActivity.class), PendingIntent.FLAG_IMMUTABLE));
        }
        return rv;
    }
}
