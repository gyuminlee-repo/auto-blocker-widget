package com.gml.autoblocker;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.InsetDrawable;
import android.graphics.drawable.LayerDrawable;
import android.text.Editable;
import android.text.InputFilter;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;

/**
 * 위젯 사용자 지정 색 고르기. 위에서부터 프리셋 20색, 색상·채도·명도 슬라이더, #RRGGBB 입력, 미리보기.
 * 세 입력 중 하나가 바뀌면 나머지 둘을 맞춘다. updating 플래그로 서로 다시 부르지 않게 막는다.
 * 문구는 받은 Context(앱 언어로 감싼 Activity)로 푼다.
 */
final class ColorPickerDialog {
    interface Listener { void onPicked(int color); }

    /** 5열 4행. 3행에 컬러 스타일 기본값(#2E7D32 #8A4B00 #37474F), 4행에 모노톤 세 색이 있다. */
    static final int[] PRESETS = {
            0xFFC62828, 0xFFAD1457, 0xFF6A1B9A, 0xFF283593, 0xFF1565C0,
            0xFF00838F, 0xFF00695C, 0xFF2E7D32, 0xFF558B2F, 0xFFF9A825,
            0xFFEF6C00, 0xFF8A4B00, 0xFF4E342E, 0xFF37474F, 0xFF0277BD,
            0xFFFFFFFF, 0xFFE8EAED, 0xFF5F6368, 0xFF202124, 0xFF000000,
    };
    private static final int COLUMNS = 5;
    private static final int OUTLINE = 0x33000000;

    private final Context ctx;
    private final float[] hsv = new float[3];
    private int color;
    private boolean updating;

    private final View[] cells = new View[PRESETS.length];
    private SeekBar hue, sat, val;
    private GradientDrawable satGrad, valGrad;
    private EditText hex;
    private TextView preview;

    static void show(Context ctx, int initial, String stateWord, Listener listener) {
        new ColorPickerDialog(ctx).open(initial, stateWord, listener);
    }

    private ColorPickerDialog(Context ctx) {
        this.ctx = ctx;
    }

    private int dp(float v) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, ctx.getResources().getDisplayMetrics()));
    }

    private void open(int initial, String stateWord, Listener listener) {
        LinearLayout col = new LinearLayout(ctx);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(20), dp(8), dp(20), dp(8));

        GridLayout grid = new GridLayout(ctx);
        grid.setColumnCount(COLUMNS);
        for (int k = 0; k < PRESETS.length; k++) {
            final int c = PRESETS[k];
            View cell = new View(ctx);
            GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
            lp.width = dp(44);
            lp.height = dp(44);
            lp.setMargins(dp(2), dp(2), dp(2), dp(2));
            cell.setLayoutParams(lp);
            cell.setContentDescription(HexColor.format(c));
            cell.setOnClickListener(v -> setColor(c, null, true));
            cells[k] = cell;
            grid.addView(cell);
        }
        LinearLayout.LayoutParams glp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        glp.gravity = Gravity.CENTER_HORIZONTAL;
        col.addView(grid, glp);

        int[] rainbow = new int[7];
        for (int k = 0; k < rainbow.length; k++) rainbow[k] = Color.HSVToColor(new float[] { k * 60f, 1f, 1f });
        hue = slider(col, R.string.picker_hue, 360, new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, rainbow));
        satGrad = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, new int[] { 0, 0 });
        sat = slider(col, R.string.picker_saturation, 100, satGrad);
        valGrad = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, new int[] { 0, 0 });
        val = slider(col, R.string.picker_value, 100, valGrad);

        hex = new EditText(ctx);
        hex.setSingleLine(true);
        hex.setHint(R.string.hex_hint);
        hex.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        hex.setFilters(new InputFilter[] { new InputFilter.LengthFilter(7) });
        hex.setMinHeight(dp(48));
        LinearLayout.LayoutParams hlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        hlp.topMargin = dp(12);
        col.addView(hex, hlp);
        hex.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void afterTextChanged(Editable s) {
                if (updating) return;
                Integer c = HexColor.parse(s.toString());
                // 형식이 틀리면 아무것도 움직이지 않는다.
                if (c != null) setColor(c, null, false);
            }
        });

        preview = new TextView(ctx);
        preview.setGravity(Gravity.CENTER);
        preview.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        preview.setTypeface(Typeface.DEFAULT_BOLD);
        preview.setText(stateWord);
        LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(64));
        plp.topMargin = dp(12);
        col.addView(preview, plp);

        // 폴드 커버 화면처럼 좁고 낮은 화면에서도 모두 닿도록 스크롤로 감싼다.
        ScrollView scroll = new ScrollView(ctx);
        scroll.addView(col);

        setColor(initial, null, true);
        new AlertDialog.Builder(ctx)
                .setTitle(R.string.picker_title)
                .setView(scroll)
                .setNegativeButton(R.string.btn_cancel, null)
                .setPositiveButton(R.string.btn_ok, (d, w) -> listener.onPicked(color))
                .show();
    }

    private SeekBar slider(LinearLayout col, int label, int max, GradientDrawable grad) {
        TextView t = new TextView(ctx);
        t.setText(label);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tlp.topMargin = dp(12);
        col.addView(t, tlp);

        SeekBar bar = new SeekBar(ctx);
        bar.setMax(max);
        bar.setContentDescription(ctx.getString(label));
        bar.setSplitTrack(false);
        // 기본 진행 막대 대신 그라데이션을 깐다. 좌우는 엄지가 움직이는 구간(패딩 안쪽)에 맞춘다.
        bar.setProgressDrawable(new GradientDrawable());
        grad.setCornerRadius(dp(6));
        bar.setBackground(new InsetDrawable(grad, bar.getPaddingLeft(), dp(8), bar.getPaddingRight(), dp(8)));
        bar.setMinimumHeight(dp(48));
        col.addView(bar, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48)));
        bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar s, int p, boolean fromUser) {
                if (updating || !fromUser) return;
                // 슬라이더 값을 그대로 쓴다. 색에서 HSV 를 되돌려 받으면 채도 0 일 때 색상이 0 으로 튄다.
                float[] next = { hue.getProgress(), sat.getProgress() / 100f, val.getProgress() / 100f };
                setColor(Color.HSVToColor(next), next, true);
            }
            @Override public void onStartTrackingTouch(SeekBar s) {}
            @Override public void onStopTrackingTouch(SeekBar s) {}
        });
        return bar;
    }

    /**
     * 현재 색을 바꾸고 화면을 맞춘다. fromSliders 가 null 이 아니면 슬라이더가 원인이라 슬라이더는 그대로 둔다.
     * writeHex 가 false 면 입력 중인 hex 칸을 덮어쓰지 않는다(커서가 튀지 않게).
     */
    private void setColor(int c, float[] fromSliders, boolean writeHex) {
        updating = true;
        color = 0xFF000000 | c;
        if (fromSliders != null) {
            System.arraycopy(fromSliders, 0, hsv, 0, 3);
        } else {
            Color.colorToHSV(color, hsv);
            hue.setProgress(Math.round(hsv[0]));
            sat.setProgress(Math.round(hsv[1] * 100));
            val.setProgress(Math.round(hsv[2] * 100));
        }
        satGrad.setColors(new int[] {
                Color.HSVToColor(new float[] { hsv[0], 0f, hsv[2] }),
                Color.HSVToColor(new float[] { hsv[0], 1f, hsv[2] }) });
        valGrad.setColors(new int[] {
                Color.HSVToColor(new float[] { hsv[0], hsv[1], 0f }),
                Color.HSVToColor(new float[] { hsv[0], hsv[1], 1f }) });
        if (writeHex) {
            hex.setText(HexColor.format(color));
            hex.setSelection(hex.length());
        }
        hex.setError(null);
        preview.setBackgroundColor(color);
        preview.setTextColor(HexColor.darkText(color) ? HexColor.DARK : HexColor.WHITE);
        for (int k = 0; k < cells.length; k++) cells[k].setBackground(cell(PRESETS[k], PRESETS[k] == color));
        updating = false;
    }

    /** 원형 칸. 고른 칸은 바깥 고리와 사이 틈이 생겨 어떤 색이든 보인다. */
    private Drawable cell(int c, boolean selected) {
        GradientDrawable dot = new GradientDrawable();
        dot.setShape(GradientDrawable.OVAL);
        dot.setColor(c);
        dot.setStroke(dp(1), OUTLINE);
        GradientDrawable ring = new GradientDrawable();
        ring.setShape(GradientDrawable.OVAL);
        ring.setColor(Color.TRANSPARENT);
        ring.setStroke(dp(2), selected ? HexColor.DARK : Color.TRANSPARENT);
        LayerDrawable ld = new LayerDrawable(new Drawable[] { ring, dot });
        int in = dp(5);
        ld.setLayerInset(1, in, in, in, in);
        return ld;
    }

    /** 설정 화면의 작은 원형 견본. */
    static Drawable swatch(Context ctx, int c) {
        float d = ctx.getResources().getDisplayMetrics().density;
        GradientDrawable dot = new GradientDrawable();
        dot.setShape(GradientDrawable.OVAL);
        dot.setColor(c);
        dot.setStroke(Math.round(d), OUTLINE);
        int in = Math.round(6 * d);
        return new InsetDrawable(dot, in, in, in, in);
    }
}
