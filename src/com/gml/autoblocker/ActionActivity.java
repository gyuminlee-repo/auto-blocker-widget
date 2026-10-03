package com.gml.autoblocker;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

/**
 * 자동화 앱(MacroDroid, Tasker)과 앱 바로가기가 켜기 또는 끄기를 요청하는 공개 입구.
 * 액션을 목표값으로만 바꿔 TrampolineActivity 에 넘기고 즉시 끝난다. 전환 요청과 다른 extra 는 받지 않는다.
 */
public class ActionActivity extends Activity {
    static final String ACTION_TURN_ON = "com.gml.autoblocker.action.TURN_ON";
    static final String ACTION_TURN_OFF = "com.gml.autoblocker.action.TURN_OFF";

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        String action = getIntent().getAction();
        int target = ACTION_TURN_ON.equals(action) ? 1 : ACTION_TURN_OFF.equals(action) ? 0 : -1;
        // 명시적 컴포넌트 호출은 intent-filter 를 건너뛰므로 여기서 다시 거른다. -1(전환)은 넘기지 않는다.
        if (target >= 0) {
            startActivity(new Intent(this, TrampolineActivity.class)
                    .putExtra(TrampolineActivity.EXTRA_TARGET, target));
        }
        finish();
    }
}
