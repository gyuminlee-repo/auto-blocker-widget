package com.gml.autoblocker;

import java.util.regex.Pattern;

/** 사용자 지정 위젯 색의 입력 해석과 글자색 선택. Android API 를 쓰지 않아 JVM 에서 바로 시험할 수 있다. */
final class HexColor {
    private static final Pattern HEX = Pattern.compile("#?[0-9a-fA-F]{6}");
    /** 진한 글자색 #202124. res/values/colors.xml 의 widget_fg_dark 와 같은 값이다. */
    static final int DARK = 0xFF202124;
    static final int WHITE = 0xFFFFFFFF;

    private HexColor() {}

    /** "#RRGGBB" 또는 "RRGGBB"(대소문자 무관)면 불투명 ARGB, 아니면 null. */
    static Integer parse(String s) {
        if (s == null) return null;
        String t = s.trim();
        if (!HEX.matcher(t).matches()) return null;
        if (t.startsWith("#")) t = t.substring(1);
        return 0xFF000000 | Integer.parseInt(t, 16);
    }

    static String format(int argb) {
        return String.format("#%06X", argb & 0xFFFFFF);
    }

    /** WCAG 2.x 상대 휘도. */
    static double luminance(int argb) {
        double[] c = { (argb >> 16) & 0xFF, (argb >> 8) & 0xFF, argb & 0xFF };
        for (int i = 0; i < 3; i++) {
            double v = c[i] / 255.0;
            c[i] = v <= 0.04045 ? v / 12.92 : Math.pow((v + 0.055) / 1.055, 2.4);
        }
        return 0.2126 * c[0] + 0.7152 * c[1] + 0.0722 * c[2];
    }

    static double contrast(int a, int b) {
        double la = luminance(a), lb = luminance(b);
        return (Math.max(la, lb) + 0.05) / (Math.min(la, lb) + 0.05);
    }

    /** 바탕 위에서 흰색보다 #202124 의 WCAG 대비가 높으면 true. */
    static boolean darkText(int bg) {
        return contrast(DARK, bg) > contrast(WHITE, bg);
    }
}
