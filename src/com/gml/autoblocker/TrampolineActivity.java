package com.gml.autoblocker;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.SystemClock;
import android.widget.Toast;

/** 위젯 탭을 받아 armed 를 기록하고 rampart 설정 화면을 연 뒤 즉시 끝난다. */
public class TrampolineActivity extends Activity {
    static final String PREFS = "arm";
    static final String ARMED_AT = "armedAt";
    /** 선택 extra. 1 이면 켜기만, 0 이면 끄기만 한다. 없으면 -1 로 지금처럼 전환한다. */
    static final String EXTRA_TARGET = "target";
    static final String ARM_TARGET = "armTarget";
    static final String RAMPART_ACTION = "com.samsung.android.rampart.action.MAIN_SETTING_ACTIVITY";

    /** rampart 설정 화면 Intent. arm 하지 않으므로 여는 것만으로는 스위치를 누르지 않는다. */
    static Intent rampartIntent() {
        return new Intent(RAMPART_ACTION).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    }

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(LocaleHelper.wrap(base));
    }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        if (!AutoTapService.isRunning()) {
            // 서비스가 없으면 설정 화면을 열어도 누를 수 없으므로 설정 안내 화면으로 보낸다.
            Toast.makeText(this, R.string.toast_service_off, Toast.LENGTH_SHORT).show();
            startActivity(new Intent(this, MainActivity.class));
            finish();
            return;
        }
        getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                .putLong(ARMED_AT, SystemClock.elapsedRealtime())
                .putInt(ARM_TARGET, getIntent().getIntExtra(EXTRA_TARGET, -1)).commit();
        try {
            startActivity(rampartIntent());
            AutoTapService.onArmed();
        } catch (ActivityNotFoundException | SecurityException e) {
            AutoTapService.disarm(this);
            Toast.makeText(this, R.string.toast_open_fail, Toast.LENGTH_LONG).show();
        }
        finish();
    }
}
