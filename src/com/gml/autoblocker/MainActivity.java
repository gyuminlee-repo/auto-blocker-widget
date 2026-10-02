package com.gml.autoblocker;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.TextView;
import android.widget.Toast;

/** 앱 서랍 진입점. 접근성 서비스 상태와 설정, 위젯 추가 안내, 앱 정보(삭제) 바로가기를 보여 준다. */
public class MainActivity extends Activity {
    private TextView status;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_main);
        status = (TextView) findViewById(R.id.service_status);
        findViewById(R.id.btn_accessibility).setOnClickListener(v ->
                open(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));
        findViewById(R.id.btn_update).setOnClickListener(v ->
                open(new Intent(Intent.ACTION_VIEW, Uri.parse(getString(R.string.releases_url)))));
        findViewById(R.id.btn_app_info).setOnClickListener(v ->
                open(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.fromParts("package", getPackageName(), null))));
        ((TextView) findViewById(R.id.footer)).setText(getString(R.string.main_footer, versionName()));
    }

    @Override
    protected void onResume() {
        super.onResume();
        status.setText(isServiceEnabled() ? R.string.main_service_on : R.string.main_service_off);
    }

    private void open(Intent i) {
        try {
            startActivity(i);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, R.string.toast_no_app, Toast.LENGTH_LONG).show();
        }
    }

    private boolean isServiceEnabled() {
        String list = Settings.Secure.getString(getContentResolver(),
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        if (list == null) return false;
        ComponentName cn = new ComponentName(this, AutoTapService.class);
        String full = cn.flattenToString();
        String shrt = cn.flattenToShortString();
        for (String s : list.split(":")) {
            if (s.equalsIgnoreCase(full) || s.equalsIgnoreCase(shrt)) return true;
        }
        return false;
    }

    private String versionName() {
        try {
            return getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (PackageManager.NameNotFoundException e) {
            return "?";
        }
    }
}
