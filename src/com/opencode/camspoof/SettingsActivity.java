package com.opencode.camspoof;

import android.app.Activity;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;

/**
 * UI untuk memilih identitas device yang di-spoof di EXIF kamera.
 * Pilihan disimpan ke /data/local/tmp/camspoof_conf (via root) yang dibaca
 * oleh modul di dalam proses tiap aplikasi kamera.
 */
public class SettingsActivity extends Activity {

    private static final String PREFS = "camspoof";
    private static final String KEY   = "device";
    private static final String CONF  = "/data/local/tmp/camspoof_conf";

    private static final String DEFAULT_LABEL = "Default (Xiaomi Mi 9)";
    private static final String[] OPTIONS = {
            DEFAULT_LABEL,
            "Xiaomi 18 Pro Max",
            "Xiaomi 17 Ultra",
            "Xiaomi 15 Ultra"
    };

    private static final String[] CAMERA_PKGS = {
            "com.android.camera", "com.android.MGC", "com.agc.gcam84",
            "com.google.android.GoogleCamera", "com.android.camera2",
            "com.miui.camera", "com.xiaomi.camera"
    };

    private RadioGroup group;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        int pad = dp(16);
        ScrollView sv = new ScrollView(this);
        sv.setBackgroundColor(Color.WHITE);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(pad, pad, pad, pad);
        sv.addView(root);

        TextView title = new TextView(this);
        title.setText("CamSpoof \u2013 Identitas EXIF");
        title.setTextSize(18);
        title.setTextColor(Color.BLACK);
        root.addView(title);

        TextView hint = new TextView(this);
        hint.setText("Pilih nama device yang muncul di EXIF foto. "
                + "Buka ulang aplikasi kamera setelah mengganti.");
        hint.setTextSize(12);
        hint.setTextColor(Color.DKGRAY);
        hint.setPadding(0, dp(4), 0, dp(16));
        root.addView(hint);

        group = new RadioGroup(this);
        group.setOrientation(RadioGroup.VERTICAL);
        final String saved = getPreferences().getString(KEY, DEFAULT_LABEL);
        for (int i = 0; i < OPTIONS.length; i++) {
            RadioButton rb = new RadioButton(this);
            rb.setId(i);
            rb.setText(OPTIONS[i]);
            rb.setTextColor(Color.BLACK);
            rb.setPadding(0, dp(8), 0, dp(8));
            group.addView(rb);
            if (OPTIONS[i].equals(saved)) group.check(i);
        }
        group.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override public void onCheckedChanged(RadioGroup g, int id) {
                apply(OPTIONS[id]);
            }
        });
        root.addView(group);

        TextView note = new TextView(this);
        note.setText("\"Default\" = tidak di-spoof (identitas asli Xiaomi Mi 9). "
                + "Butuh akses root sekali untuk menulis konfigurasi.");
        note.setTextSize(11);
        note.setTextColor(Color.GRAY);
        note.setPadding(0, dp(16), 0, 0);
        root.addView(note);

        setContentView(sv);
    }

    private void apply(String label) {
        getPreferences().edit().putString(KEY, label).apply();

        String make, model, brand;
        if (DEFAULT_LABEL.equals(label)) {
            make = ""; model = ""; brand = "";
        } else {
            make = "Xiaomi"; model = label; brand = "Xiaomi";
        }

        writeConf(make, model, brand);

        for (String p : CAMERA_PKGS) {
            runSu("am force-stop " + p);
        }

        String msg = DEFAULT_LABEL.equals(label)
                ? "Spoof dimatikan (default)"
                : "Spoof: " + label;
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }

    private void writeConf(String make, String model, String brand) {
        try {
            File f = new File(getFilesDir(), "camspoof_conf");
            FileOutputStream o = new FileOutputStream(f);
            String content = "make=" + make + "\n"
                    + "model=" + model + "\n"
                    + "brand=" + brand + "\n";
            o.write(content.getBytes("UTF-8"));
            o.close();
            runSu("cp '" + f.getAbsolutePath() + "' " + CONF
                    + " && chmod 644 " + CONF);
        } catch (Throwable t) {
            Toast.makeText(this, "Gagal tulis config: " + t, Toast.LENGTH_LONG).show();
        }
    }

    private void runSu(final String cmd) {
        new Thread(new Runnable() {
            @Override public void run() {
                try {
                    Process p = Runtime.getRuntime().exec(new String[]{"su", "-c", cmd});
                    p.waitFor();
                } catch (Throwable ignored) {
                }
            }
        }).start();
    }

    private SharedPreferences getPreferences() {
        return getSharedPreferences(PREFS, MODE_PRIVATE);
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
