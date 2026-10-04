package com.gml.autoblocker;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * 상태 감지 시험 기록(베타). 최근 MAX 건을 앱 내부 prefs 에만 둔다. 외부로 보내지 않는다.
 * 상태 캐시와 다른 prefs 파일이라 「기록 지우기」가 상태를 건드리지 않는다.
 */
final class StateLog {
    static final int MAX = 50;
    private static final String PREFS = "state_log";
    private static final String KEY = "entries";

    private StateLog() {}

    /** type 은 고정 영문 식별자(observer_ok, on_change 등), detail 은 자유 문구. */
    static void add(Context ctx, String type, String detail) {
        Log.i(AutoBlockerWidget.TAG, "log " + type + " " + detail);
        SharedPreferences p = prefs(ctx);
        List<String> list = new ArrayList<>(read(p));
        list.add(System.currentTimeMillis() + "\t" + type + "\t" + detail.replace('\n', ' ').replace('\t', ' '));
        while (list.size() > MAX) list.remove(0);
        p.edit().putString(KEY, String.join("\n", list)).commit();
    }

    /** 최신이 위로 오는 표시용 줄. 기록이 없으면 빈 목록. */
    static List<String> lines(Context ctx) {
        List<String> raw = read(prefs(ctx));
        SimpleDateFormat f = new SimpleDateFormat("MM-dd HH:mm:ss", Locale.KOREA);
        List<String> out = new ArrayList<>();
        for (int i = raw.size() - 1; i >= 0; i--) {
            String[] c = raw.get(i).split("\t", 3);
            if (c.length < 3) continue;
            long at;
            try { at = Long.parseLong(c[0]); } catch (NumberFormatException e) { continue; }
            out.add(f.format(new Date(at)) + "  " + c[1] + "  " + c[2]);
        }
        return out;
    }

    static void clear(Context ctx) {
        prefs(ctx).edit().remove(KEY).commit();
    }

    private static SharedPreferences prefs(Context ctx) {
        return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static List<String> read(SharedPreferences p) {
        String s = p.getString(KEY, "");
        return s.isEmpty() ? new ArrayList<>() : Arrays.asList(s.split("\n"));
    }
}
