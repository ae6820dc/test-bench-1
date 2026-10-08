package com.ae6820dc.androidsmoketest;

import android.app.Activity;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.WindowInsets;
import android.widget.LinearLayout;
import android.widget.TextView;

/** A platform-only launch test: no services, permissions, or display-mode changes. */
public final class MainActivity extends Activity {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private long startedAt;
    private TextView heartbeat;
    private final Runnable tick = new Runnable() {
        @Override public void run() {
            heartbeat.setText(getString(R.string.heartbeat,
                    (SystemClock.elapsedRealtime() - startedAt) / 1000));
            handler.postDelayed(this, 1000);
        }
    };

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        startedAt = state == null ? SystemClock.elapsedRealtime()
                : state.getLong("startedAt", SystemClock.elapsedRealtime());

        LinearLayout screen = new LinearLayout(this);
        screen.setOrientation(LinearLayout.VERTICAL);
        screen.setGravity(Gravity.CENTER);
        screen.setBackgroundColor(Color.rgb(244, 247, 250));
        int padding = Math.round(24 * getResources().getDisplayMetrics().density);
        screen.setPadding(padding, padding, padding, padding);
        screen.setOnApplyWindowInsetsListener((view, insets) -> {
            // Android 16 enforces edge-to-edge. Keep the screen inside system bars.
            if (Build.VERSION.SDK_INT >= 30) {
                android.graphics.Insets bars = insets.getInsets(
                        WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                view.setPadding(padding + bars.left, padding + bars.top,
                        padding + bars.right, padding + bars.bottom);
            } else {
                view.setPadding(padding + insets.getSystemWindowInsetLeft(),
                        padding + insets.getSystemWindowInsetTop(),
                        padding + insets.getSystemWindowInsetRight(),
                        padding + insets.getSystemWindowInsetBottom());
            }
            return insets;
        });
        screen.addView(label(getString(R.string.test_title), 26));
        screen.addView(label(getString(R.string.test_description), 18));
        screen.addView(label(getString(R.string.device_version,
                Build.VERSION.RELEASE, Build.VERSION.SDK_INT), 16));
        heartbeat = label(getString(R.string.heartbeat, 0), 18);
        screen.addView(heartbeat);
        setContentView(screen);
        screen.requestApplyInsets();
    }

    private TextView label(String text, int size) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(size);
        view.setTextColor(Color.rgb(24, 43, 62));
        view.setGravity(Gravity.CENTER);
        view.setPadding(0, 12, 0, 12);
        return view;
    }

    @Override protected void onResume() {
        super.onResume();
        handler.post(tick);
    }

    @Override protected void onPause() {
        handler.removeCallbacks(tick);
        super.onPause();
    }

    @Override protected void onSaveInstanceState(Bundle state) {
        state.putLong("startedAt", startedAt);
        super.onSaveInstanceState(state);
    }
}
