package com.gml.autoblocker;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.os.Bundle;
import android.os.SystemClock;
import android.widget.Toast;

/** 위젯 탭을 받아 armed 를 기록하고 rampart 설정 화면을 연 뒤 즉시 끝난다. */
public class TrampolineActivity extends Activity {
    static final String PREFS = "arm";
    static final String ARMED_AT = "armedAt";
    static final String RAMPART_ACTION = "com.samsung.android.rampart.action.MAIN_SETTING_ACTIVITY";

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        if (!AutoTapService.isRunning()) {
            Toast.makeText(this, R.string.toast_service_off, Toast.LENGTH_LONG).show();
        }
        getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                .putLong(ARMED_AT, SystemClock.elapsedRealtime()).commit();
        try {
            startActivity(new Intent(RAMPART_ACTION).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            AutoTapService.onArmed();
        } catch (ActivityNotFoundException | SecurityException e) {
            AutoTapService.disarm(this);
            Toast.makeText(this, R.string.toast_open_fail, Toast.LENGTH_LONG).show();
        }
        finish();
    }
}
