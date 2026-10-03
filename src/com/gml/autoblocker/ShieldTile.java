package com.gml.autoblocker;

import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;

/**
 * 빠른 설정 타일. 상태는 위젯과 같은 캐시(AutoBlockerWidget.cached)를 보여 주고,
 * 탭하면 위젯 탭과 같은 TrampolineActivity 경로로 전환한다.
 */
public class ShieldTile extends TileService {

    /** 위젯 캐시가 바뀌면 부른다. 시스템이 onStartListening 을 다시 불러 타일을 갱신한다. */
    static void requestUpdate(Context ctx) {
        TileService.requestListeningState(ctx, new ComponentName(ctx, ShieldTile.class));
    }

    @Override
    public void onStartListening() {
        Tile tile = getQsTile();
        if (tile == null) return;
        Context loc = LocaleHelper.wrap(getApplicationContext());
        int state = AutoBlockerWidget.cached(this);
        int text = state == 1 ? R.string.state_on : state == 0 ? R.string.state_off : R.string.state_unknown;
        tile.setState(state == 1 ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE);
        tile.setLabel(loc.getString(R.string.app_name));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) tile.setSubtitle(loc.getString(text));
        tile.setContentDescription(loc.getString(R.string.desc_fmt, loc.getString(text)));
        tile.updateTile();
    }

    @Override
    public void onClick() {
        if (isLocked()) unlockAndRun(this::launch);
        else launch();
    }

    /** 위젯 탭과 같은 경로지만 값을 바꾼 뒤 홈 대신 타일을 누르기 전 화면으로 돌아간다. 서비스가 꺼져 있으면 TrampolineActivity 가 설정 안내 화면을 연다. */
    @SuppressWarnings("deprecation") // startActivityAndCollapse(Intent) 는 API 34 미만 분기에서만 부른다.
    private void launch() {
        Intent i = new Intent(this, TrampolineActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .putExtra(TrampolineActivity.EXTRA_RETURN, TrampolineActivity.RETURN_BACK);
        if (Build.VERSION.SDK_INT >= 34) {
            // PendingIntent 는 extra 를 구별하지 않으므로 위젯(요청 코드 0)과 다른 요청 코드를 써야 BACK 이 섞이지 않는다.
            startActivityAndCollapse(PendingIntent.getActivity(this, 1, i, PendingIntent.FLAG_IMMUTABLE));
        } else {
            startActivityAndCollapse(i);
        }
    }
}
