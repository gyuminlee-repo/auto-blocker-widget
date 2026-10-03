package com.gml.autoblocker;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;

/** 넓은 위젯의 Play 프로텍트 버튼을 받아 설정 화면을 연 뒤 즉시 끝난다. 설정값은 바꾸지 않는다. */
public class PlayProtectActivity extends Activity {
    private static final String PROTECT_PKG = "com.google.android.gms";
    private static final String PROTECT_CLS = "com.google.android.gms.security.settings.VerifyAppsSettingsActivity";
    private static final String STORE_PKG = "com.android.vending";

    /** Play 프로텍트 설정 화면을 직접 열고, 막히면 Play 스토어를 연다. 설정 안내(MainActivity)와 함께 쓴다. */
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
        open(this);
        // Theme.NoDisplay 는 onResume 전에 끝내야 한다.
        finish();
    }
}
