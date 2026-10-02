package com.gml.autoblocker;

import android.app.LocaleManager;
import android.content.Context;
import android.content.res.Configuration;
import android.os.Build;
import android.os.LocaleList;

/**
 * 앱 언어. 값은 "" (시스템 기본), "en", "ko".
 * API 33+ 는 시스템 앱별 언어(LocaleManager)를 그대로 쓰고, 그 아래는 SharedPreferences 에 둔다.
 */
final class LocaleHelper {
    private static final String PREFS = "locale";
    private static final String TAG_KEY = "tag";

    private LocaleHelper() {}

    static String get(Context ctx) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            LocaleList l = ctx.getSystemService(LocaleManager.class).getApplicationLocales();
            return l.isEmpty() ? "" : l.get(0).getLanguage();
        }
        return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(TAG_KEY, "");
    }

    static void set(Context ctx, String tag) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ctx.getSystemService(LocaleManager.class).setApplicationLocales(tag.isEmpty()
                    ? LocaleList.getEmptyLocaleList() : LocaleList.forLanguageTags(tag));
        } else {
            ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(TAG_KEY, tag).commit();
        }
    }

    /**
     * 앱 언어를 적용한 Context. 현재 프로세스 설정에 기대지 않고 저장된 값으로 직접 만든다.
     * API 33+ 에서 언어를 바꾼 직후에는 설정 변경이 아직 도착하지 않았을 수 있기 때문이다.
     */
    static Context wrap(Context base) {
        String tag = get(base);
        LocaleList locales;
        if (!tag.isEmpty()) {
            locales = LocaleList.forLanguageTags(tag);
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            locales = base.getSystemService(LocaleManager.class).getSystemLocales();
        } else {
            return base;
        }
        Configuration c = new Configuration(base.getResources().getConfiguration());
        c.setLocales(locales);
        return base.createConfigurationContext(c);
    }
}
