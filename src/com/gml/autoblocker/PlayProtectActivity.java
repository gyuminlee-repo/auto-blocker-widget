package com.gml.autoblocker;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.os.Bundle;
import android.util.TypedValue;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

/** 넓은 위젯의 Play 프로텍트 버튼을 받아 설정 화면을 연다. 처음 한 번은 안내 창을 먼저 띄운다. 설정값은 바꾸지 않는다. */
public class PlayProtectActivity extends Activity {
    private static final String PROTECT_PKG = "com.google.android.gms";
    private static final String PROTECT_CLS = "com.google.android.gms.security.settings.VerifyAppsSettingsActivity";
    private static final String STORE_PKG = "com.android.vending";
    private static final String HINT_PREFS = "play_protect_hint";
    /** 안내 창에서 열기를 누른 적이 있는지. 0.08.11.03 의 Toast 기록(shown)과 따로 둬서 Toast 를 본 사용자도 창은 한 번 본다. */
    private static final String HINT_DIALOG_DONE = "dialog_done";

    private AlertDialog dialog;

    /**
     * 스위치 화면은 Play 스토어 내부 화면이라 외부에서 바로 열 수 없다(Play 스토어 53.3 분석).
     * 처음에는 ⚙ 경로를 큰 글씨로 알리는 창을 띄우고 열기를 눌러야 연다. 열기를 누르지 않고 닫으면 다음에 다시 보인다.
     * 그 뒤에는 창 없이 바로 연다. 창을 띄우지 않았거나 창이 닫히면 onDone 을 부른다. 띄운 창을 돌려준다(없으면 null).
     */
    static AlertDialog openWithHint(Activity a, Runnable onDone) {
        SharedPreferences p = a.getSharedPreferences(HINT_PREFS, Context.MODE_PRIVATE);
        if (p.getBoolean(HINT_DIALOG_DONE, false)) {
            open(a);
            if (onDone != null) onDone.run();
            return null;
        }
        AlertDialog.Builder b = new AlertDialog.Builder(a);
        // 글자색이 대화상자 테마(다크 모드 포함)를 따르도록 builder 의 Context 로 만든다.
        Context dc = b.getContext();
        float density = dc.getResources().getDisplayMetrics().density;
        LinearLayout col = new LinearLayout(dc);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(Math.round(24 * density), Math.round(20 * density), Math.round(24 * density), Math.round(8 * density));
        TextView path = new TextView(dc);
        path.setText(R.string.play_protect_dialog_path);
        path.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
        path.setTypeface(Typeface.DEFAULT_BOLD);
        col.addView(path);
        TextView reason = new TextView(dc);
        reason.setText(R.string.play_protect_dialog_reason);
        reason.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        reason.setPadding(0, Math.round(12 * density), 0, 0);
        col.addView(reason);
        AlertDialog d = b.setView(col)
                .setNegativeButton(R.string.btn_cancel, null)
                .setPositiveButton(R.string.btn_open_play_protect, (di, w) -> {
                    p.edit().putBoolean(HINT_DIALOG_DONE, true).apply();
                    open(a);
                })
                .create();
        if (onDone != null) d.setOnDismissListener(di -> onDone.run());
        d.show();
        return d;
    }

    /** Play 프로텍트 설정 화면을 직접 열고, 막히면 Play 스토어를 연다. 안내 창은 openWithHint 가 맡는다. */
    static void open(Context ctx) {
        try {
            ctx.startActivity(new Intent().setComponent(new ComponentName(PROTECT_PKG, PROTECT_CLS))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            return;
        } catch (ActivityNotFoundException | SecurityException e) {
            // 아래 Play 스토어로 넘어간다.
        }
        Intent store = ctx.getPackageManager().getLaunchIntentForPackage(STORE_PKG);
        try {
            if (store != null) {
                ctx.startActivity(store);
                return;
            }
        } catch (ActivityNotFoundException | SecurityException e) {
            // 아래 toast 로 넘어간다.
        }
        Toast.makeText(ctx, R.string.toast_play_protect_fail, Toast.LENGTH_LONG).show();
    }

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(LocaleHelper.wrap(base));
    }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        // 반투명 테마라 안내 창이 필요 없으면 여기서 열고 바로 끝난다. 창을 띄웠으면 창이 닫힐 때 끝난다.
        dialog = openWithHint(this, this::finish);
    }

    @Override
    protected void onDestroy() {
        // noHistory 나 화면 회전으로 창이 열린 채 끝나면 창을 같이 닫아 WindowLeaked 를 막는다.
        // 회전 때는 새 인스턴스가 같은 토큰을 쓰므로 finish 를 부르지 않게 리스너를 먼저 뗀다.
        if (dialog != null && dialog.isShowing()) {
            dialog.setOnDismissListener(null);
            dialog.dismiss();
        }
        super.onDestroy();
    }
}
