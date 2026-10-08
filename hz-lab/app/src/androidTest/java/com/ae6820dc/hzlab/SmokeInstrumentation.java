package com.ae6820dc.hzlab;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Intent;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.Display;
import android.view.WindowManager;
import android.widget.RadioGroup;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;

/** Dependency-free instrumentation of real controls on a running API 36 activity. */
public final class SmokeInstrumentation extends Instrumentation {
    private MainActivity activity;
    private final StringBuilder report = new StringBuilder();

    @Override public void onCreate(Bundle arguments) {
        super.onCreate(arguments);
        start();
    }
    private void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
    private void click(int id) {
        check(activity.findViewById(id).performClick(), "Click not handled: " + id);
    }
    private String text(int id) {
        return ((TextView) activity.findViewById(id)).getText().toString();
    }
    private void automaticCleared() {
        WindowManager.LayoutParams p = activity.getWindow().getAttributes();
        check(p.preferredDisplayModeId == 0 && p.preferredRefreshRate == 0
                && p.preferredMinDisplayRefreshRate == 0 && p.preferredMaxDisplayRefreshRate == 0,
                "Automatic did not clear window preferences");
        check(!activity.getSharedPreferences("selection", 0).contains("hz"),
                "Automatic did not clear saved request");
    }
    private void launch() {
        Intent intent = new Intent(getTargetContext(), MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        activity = (MainActivity) startActivitySync(intent);
        waitForIdleSync();
    }
    @Override public void onStart() {
        Bundle result = new Bundle();
        try {
            check(android.os.Build.VERSION.SDK_INT == 36, "Requires API 36 emulator");
            launch();
            runOnMainSync(() -> {
                check(text(R.id.display_info).contains("Jelentett frissítés"), "Display Hz missing");
                check(text(R.id.display_info).contains("Felbontás"), "Resolution missing");
                check(text(R.id.mode_list).contains("Hz"), "Supported modes missing");
                click(R.id.automatic);
                automaticCleared();
                Spinner spinner = activity.findViewById(R.id.supported_spinner);
                check(spinner.isEnabled() && spinner.getCount() > 0, "No resolution-safe modes");
                Display.Mode before = activity.getDisplay().getMode();
                spinner.setSelection(spinner.getCount() - 1);
                click(R.id.apply);
                int id = activity.getWindow().getAttributes().preferredDisplayModeId;
                Display.Mode match = null;
                for (Display.Mode mode : activity.getDisplay().getSupportedModes()) {
                    if (mode.getModeId() == id) match = mode;
                }
                check(match != null && match.getPhysicalWidth() == before.getPhysicalWidth()
                        && match.getPhysicalHeight() == before.getPhysicalHeight(),
                        "Supported request could change resolution");
                report.append("Supported control requested a same-resolution mode.\n");
                click(R.id.automatic);
                ((android.widget.RadioButton) activity.findViewById(R.id.custom_radio)).performClick();
                click(R.id.apply);
                check(activity.getWindow().getAttributes().preferredRefreshRate == 30,
                        "Custom range does not start at 30");
                click(R.id.plus);
                click(R.id.apply);
                check(activity.getWindow().getAttributes().preferredRefreshRate == 31,
                        "+1 Hz control failed");
                check(activity.getWindow().getAttributes().preferredDisplayModeId == 0,
                        "Custom request retained explicit display mode");
                check(text(R.id.requested_info).contains("31,00")
                        && text(R.id.requested_info).contains("nem igazolja"),
                        "Request is not separately/correctly labeled");
                click(R.id.minus);
                click(R.id.apply);
                check(activity.getWindow().getAttributes().preferredRefreshRate == 30,
                        "-1 Hz control failed");
                SeekBar seek = activity.findViewById(R.id.custom_seek);
                float maximum = 0;
                for (Display.Mode mode : activity.getDisplay().getSupportedModes()) {
                    maximum = Math.max(maximum, mode.getRefreshRate());
                }
                seek.setProgress(seek.getMax());
                click(R.id.apply);
                check(Math.abs(activity.getWindow().getAttributes().preferredRefreshRate
                        - maximum) < 0.01, "Custom range does not reach reported maximum");
                seek.setProgress(1);
                click(R.id.apply);
                check(activity.getSharedPreferences("selection", 0).getFloat("hz", 0) == 31,
                        "Request was not saved");
                report.append("Custom 30 Hz, +1/-1 controls, exact maximum and saved 31 Hz passed.\n");
                activity.finish();
            });
            waitForIdleSync();
            launch();
            runOnMainSync(() -> {
                check(activity.getWindow().getAttributes().preferredRefreshRate == 31,
                        "Saved selection was not restored on relaunch");
                click(R.id.automatic);
                automaticCleared();
                click(R.id.motion_start);
            });
            // Run real frame callbacks and display updates for at least a minute.
            final long[] first = new long[1];
            runOnMainSync(() -> first[0] = ((MotionView) activity.findViewById(R.id.motion_view)).getFrameCount());
            for (int i = 0; i < 61; i++) {
                SystemClock.sleep(1000);
                runOnMainSync(() -> check(!activity.isFinishing(), "Activity closed during 60s run"));
            }
            runOnMainSync(() -> {
                MotionView view = activity.findViewById(R.id.motion_view);
                check(view.getFrameCount() > first[0] + 30, "Motion callbacks did not advance");
                check(text(R.id.motion_status).contains("fut"), "Motion status did not update");
                click(R.id.motion_stop);
                first[0] = view.getFrameCount();
                check(text(R.id.motion_status).contains("leállítva"), "Stop control failed");
            });
            SystemClock.sleep(1200);
            runOnMainSync(() -> {
                check(((MotionView) activity.findViewById(R.id.motion_view)).getFrameCount()
                        == first[0], "Motion continued after Stop");
                click(R.id.automatic);
                automaticCleared();
                check(text(R.id.requested_info).contains("Automatikus"), "Reset label missing");
            });
            report.append("Saved-selection relaunch, Automatic clearing, motion Start/Stop and 61s operation passed.\n");
            result.putString("stream", "\n" + report + "HZ_LAB_CONTROLS_PASS\n");
            finish(Activity.RESULT_OK, result);
        } catch (Throwable failure) {
            result.putString("stream", "\n" + report + "HZ_LAB_FAILURE: " + failure + "\n"
                    + android.util.Log.getStackTraceString(failure));
            finish(Activity.RESULT_CANCELED, result);
        }
    }
}
