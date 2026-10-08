package com.ae6820dc.hzlab;

import android.app.Activity;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.hardware.display.DisplayManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.Display;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Locale;

/** Platform-only window preferences. Never writes a phone-wide setting. */
public final class MainActivity extends Activity implements DisplayManager.DisplayListener {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ArrayList<Display.Mode> safeModes = new ArrayList<>();
    private DisplayManager displays;
    private SharedPreferences saved;
    private TextView displayInfo, requestedInfo, modeList, customValue, customRange;
    private TextView motionStatus, appStatus;
    private Spinner supported;
    private RadioGroup choices;
    private RadioButton supportedChoice, customChoice;
    private SeekBar customSeek;
    private Button minus, plus;
    private MotionView motion;
    private LinearLayout content;
    private float maximum = 30;
    private String modesKey = "";
    private boolean restoring = true, resumed, motionWanted;
    private long startedAt;
    private int resumes;

    private final Runnable update = new Runnable() {
        @Override public void run() {
            refreshDisplay();
            appStatus.setText(getString(R.string.status,
                    (SystemClock.elapsedRealtime() - startedAt) / 1000, resumes));
            updateMotionStatus();
            handler.postDelayed(this, 1000);
        }
    };

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        displays = getSystemService(DisplayManager.class);
        saved = getSharedPreferences("selection", MODE_PRIVATE);
        startedAt = state == null ? SystemClock.elapsedRealtime()
                : state.getLong("startedAt", SystemClock.elapsedRealtime());
        motionWanted = state != null && state.getBoolean("motionWanted");
        resumes = state == null ? 0 : state.getInt("resumes");


        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(Color.rgb(246, 248, 251));
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(20);
        content.setPadding(pad, pad, pad, pad);
        scroll.addView(content);
        scroll.setOnApplyWindowInsetsListener((view, insets) -> {
            android.view.WindowInsetsController controller = view.getWindowInsetsController();
            if (controller != null) controller.setSystemBarsAppearance(
                    android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                            | android.view.WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS,
                    android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                            | android.view.WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS);
            android.graphics.Insets bars = insets.getInsets(WindowInsets.Type.systemBars()
                    | WindowInsets.Type.displayCutout() | WindowInsets.Type.ime());
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });
        heading(R.string.title, 30);
        text(R.string.scope, 15, 0);
        heading(R.string.display_heading, 21);
        displayInfo = text(R.string.no_display, 18, R.id.display_info);
        text(R.string.display_note, 14, 0);
        heading(R.string.request_heading, 21);
        requestedInfo = text(R.string.automatic_state, 17, R.id.requested_info);
        choices = new RadioGroup(this);
        supportedChoice = new RadioButton(this);
        supportedChoice.setId(R.id.supported_radio);
        supportedChoice.setText(R.string.supported_choice);
        customChoice = new RadioButton(this);
        customChoice.setId(R.id.custom_radio);
        customChoice.setText(R.string.custom_choice);
        choices.addView(supportedChoice);
        choices.addView(customChoice);
        content.addView(choices);
        choices.check(R.id.supported_radio);
        supported = new Spinner(this);
        supported.setId(R.id.supported_spinner);
        content.addView(supported);
        customValue = text(R.string.custom_value, 18, R.id.custom_value);
        customSeek = new SeekBar(this);
        customSeek.setId(R.id.custom_seek);
        customSeek.setContentDescription(getString(R.string.custom_choice));
        content.addView(customSeek);
        customSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar bar, int progress, boolean user) {
                customValue.setText(getString(R.string.custom_value, hz(customHz())));
                if (user) choices.check(R.id.custom_radio);
            }
            @Override public void onStartTrackingTouch(SeekBar bar) { }
            @Override public void onStopTrackingTouch(SeekBar bar) { }
        });
        LinearLayout steps = row();
        minus = button(steps, R.string.minus, R.id.minus, v -> step(-1));
        plus = button(steps, R.string.plus, R.id.plus, v -> step(1));
        minus.setContentDescription(getString(R.string.decrease));
        plus.setContentDescription(getString(R.string.increase));
        customRange = text(R.string.custom_range, 14, 0);
        button(content, R.string.apply, R.id.apply, v -> applySelection());
        button(content, R.string.automatic, R.id.automatic, v -> automatic());
        text(R.string.saved, 14, 0);
        heading(R.string.motion_heading, 21);
        text(R.string.motion_description, 14, 0);
        motion = new MotionView(this);
        motion.setId(R.id.motion_view);
        motion.setContentDescription(getString(R.string.motion_accessibility));
        content.addView(motion, new LinearLayout.LayoutParams(-1, dp(100)));
        button(content, R.string.motion_start, R.id.motion_start, v -> {
            motionWanted = true;
            motion.setRunning(resumed);
            updateMotionStatus();
        });
        button(content, R.string.motion_stop, R.id.motion_stop, v -> {
            motionWanted = false;
            motion.setRunning(false);
            updateMotionStatus();
        });
        motionStatus = text(R.string.motion_idle, 15, R.id.motion_status);
        appStatus = text(R.string.title, 14, R.id.app_status);
        heading(R.string.all_modes_heading, 21);
        modeList = text(R.string.no_display, 15, R.id.mode_list);
        setContentView(scroll);
        scroll.requestApplyInsets();
    }

    private int dp(int n) { return Math.round(n * getResources().getDisplayMetrics().density); }
    private String hz(float rate) { return String.format(Locale.forLanguageTag("hu-HU"), "%.2f", rate); }
    private TextView text(int resource, int size, int id) {
        TextView view = new TextView(this);
        if (id != 0) view.setId(id);
        view.setText(resource);
        view.setTextSize(size);
        view.setTextColor(Color.rgb(25, 43, 64));
        view.setPadding(0, dp(6), 0, dp(8));
        content.addView(view);
        return view;
    }
    private void heading(int resource, int size) {
        TextView view = text(resource, size, 0);
        view.setTypeface(null, android.graphics.Typeface.BOLD);
    }
    private LinearLayout row() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        content.addView(row);
        return row;
    }
    private Button button(LinearLayout parent, int label, int id, View.OnClickListener click) {
        Button button = new Button(this);
        button.setId(id);
        button.setText(label);
        button.setAllCaps(false);
        button.setMinHeight(dp(48));
        button.setOnClickListener(click);
        parent.addView(button, parent == content ? new LinearLayout.LayoutParams(-1, -2)
                : new LinearLayout.LayoutParams(0, -2, 1));
        return button;
    }
    private float customHz() { return Math.min(30 + customSeek.getProgress(), maximum); }
    private void step(int delta) {
        choices.check(R.id.custom_radio);
        customSeek.setProgress(customSeek.getProgress() + delta);
    }

    private void refreshDisplay() {
        Display display = getDisplay();
        if (display == null) {
            displayInfo.setText(R.string.no_display);
            return;
        }
        Display.Mode active = display.getMode();
        Display.Mode[] modes = display.getSupportedModes();
        Arrays.sort(modes, Comparator.comparingDouble(Display.Mode::getRefreshRate)
                .thenComparingInt(Display.Mode::getModeId));
        displayInfo.setText(getString(R.string.display_info, hz(display.getRefreshRate()),
                active.getModeId(), hz(active.getRefreshRate()), active.getPhysicalWidth(),
                active.getPhysicalHeight()));
        StringBuilder key = new StringBuilder().append(active.getPhysicalWidth()).append('x')
                .append(active.getPhysicalHeight());
        for (Display.Mode mode : modes) key.append('/').append(mode.getModeId()).append(':')
                .append(mode.getPhysicalWidth()).append('x').append(mode.getPhysicalHeight())
                .append('@').append(mode.getRefreshRate());
        if (!modesKey.contentEquals(key)) {
            float previous = customHz();
            int previousId = safeModes.isEmpty() || supported.getSelectedItemPosition() < 0
                    ? 0 : safeModes.get(supported.getSelectedItemPosition()).getModeId();
            modesKey = key.toString();
            safeModes.clear();
            maximum = 0;
            StringBuilder all = new StringBuilder();
            ArrayList<String> options = new ArrayList<>();
            int selected = 0;
            for (Display.Mode mode : modes) {
                maximum = Math.max(maximum, mode.getRefreshRate());
                boolean same = mode.getPhysicalWidth() == active.getPhysicalWidth()
                        && mode.getPhysicalHeight() == active.getPhysicalHeight();
                if (same) {
                    if (mode.getModeId() == previousId) selected = safeModes.size();
                    safeModes.add(mode);
                    options.add(getString(R.string.supported_label, hz(mode.getRefreshRate()),
                            mode.getModeId(), mode.getPhysicalWidth(), mode.getPhysicalHeight()));
                }
                all.append(getString(R.string.mode_row, mode.getModeId(), mode.getPhysicalWidth(),
                        mode.getPhysicalHeight(), hz(mode.getRefreshRate()),
                        getString(same ? R.string.mode_safe : R.string.mode_other))).append('\n');
            }
            boolean hasCustom = maximum >= 30;
            if (options.isEmpty()) options.add(getString(R.string.no_same_resolution));
            supported.setAdapter(new ArrayAdapter<>(this,
                    android.R.layout.simple_spinner_dropdown_item, options));
            supported.setSelection(selected);
            supported.setEnabled(!safeModes.isEmpty());
            supportedChoice.setEnabled(!safeModes.isEmpty());
            customChoice.setEnabled(hasCustom);
            customSeek.setEnabled(hasCustom);
            minus.setEnabled(hasCustom);
            plus.setEnabled(hasCustom);
            customSeek.setMax(hasCustom ? (int) Math.ceil(maximum - 30) : 0);
            customSeek.setProgress(Math.max(0, Math.min(customSeek.getMax(),
                    (int) Math.ceil(previous - 30))));
            customValue.setText(getString(R.string.custom_value, hz(customHz())));
            customRange.setText(hasCustom ? getString(R.string.custom_range, hz(maximum))
                    : getString(R.string.custom_unavailable));
            modeList.setText(all.toString());
        }
        int requestedMode = getWindow().getAttributes().preferredDisplayModeId;
        if (!restoring && requestedMode != 0 && safeModes.stream()
                .noneMatch(mode -> mode.getModeId() == requestedMode)) {
            automatic();
            requestedInfo.append("\n" + getString(R.string.mode_missing));
        }
    }

    private void clearAttributes(WindowManager.LayoutParams attributes) {
        attributes.preferredDisplayModeId = 0;
        attributes.preferredRefreshRate = 0;
        attributes.preferredMinDisplayRefreshRate = 0;
        attributes.preferredMaxDisplayRefreshRate = 0;
    }
    private void automatic() {
        WindowManager.LayoutParams attributes = getWindow().getAttributes();
        clearAttributes(attributes);
        getWindow().setAttributes(attributes);
        saved.edit().clear().apply();
        choices.check(R.id.supported_radio);
        customSeek.setProgress(0);
        requestedInfo.setText(R.string.automatic_state);
    }
    private void applySelection() {
        refreshDisplay();
        WindowManager.LayoutParams attributes = getWindow().getAttributes();
        clearAttributes(attributes);
        boolean custom = choices.getCheckedRadioButtonId() == R.id.custom_radio;
        Display.Mode mode = null;
        if (custom) {
            if (maximum < 30) { requestedInfo.setText(R.string.custom_unavailable); return; }
            attributes.preferredRefreshRate = customHz();
        } else {
            int index = supported.getSelectedItemPosition();
            if (index < 0 || index >= safeModes.size()) {
                automatic();
                requestedInfo.append("\n" + getString(R.string.mode_missing));
                return;
            }
            mode = safeModes.get(index);
            attributes.preferredDisplayModeId = mode.getModeId();
        }
        try {
            getWindow().setAttributes(attributes);
            SharedPreferences.Editor edit = saved.edit().clear();
            edit.putBoolean("custom", custom);
            edit.putFloat("hz", custom ? customHz() : mode.getRefreshRate());
            if (mode != null) {
                edit.putInt("width", mode.getPhysicalWidth());
                edit.putInt("height", mode.getPhysicalHeight());
            }
            edit.apply();
            requestedInfo.setText(custom ? getString(R.string.requested_custom, hz(customHz()))
                    : getString(R.string.requested_mode, hz(mode.getRefreshRate()), mode.getModeId(),
                    mode.getPhysicalWidth(), mode.getPhysicalHeight()));
        } catch (IllegalArgumentException | IllegalStateException error) {
            automatic();
            requestedInfo.append("\n" + getString(R.string.request_error));
        }
        // Acceptance records a request only. Actual reported display data is read independently.
        refreshDisplay();
    }
    private void restoreSelection() {
        if (!saved.contains("hz")) { automatic(); return; }
        float rate = saved.getFloat("hz", 30);
        if (saved.getBoolean("custom", false)) {
            if (maximum < 30) { automatic(); return; }
            choices.check(R.id.custom_radio);
            customSeek.setProgress((int) Math.ceil(Math.max(0, Math.min(rate, maximum) - 30)));
            applySelection();
            return;
        }
        for (int i = 0; i < safeModes.size(); i++) {
            Display.Mode mode = safeModes.get(i);
            if (Math.abs(mode.getRefreshRate() - rate) < 0.01f
                    && mode.getPhysicalWidth() == saved.getInt("width", 0)
                    && mode.getPhysicalHeight() == saved.getInt("height", 0)) {
                choices.check(R.id.supported_radio);
                supported.setSelection(i);
                applySelection();
                return;
            }
        }
        automatic();
        requestedInfo.append("\n" + getString(R.string.mode_missing));
    }
    private void updateMotionStatus() {
        motionStatus.setText(!motionWanted ? getString(R.string.motion_idle)
                : !resumed ? getString(R.string.motion_paused)
                : getString(R.string.motion_running, motion.getFrameCount()));
    }
    @Override protected void onResume() {
        super.onResume();
        resumed = true;
        resumes++;
        displays.registerDisplayListener(this, handler);
        refreshDisplay();
        if (restoring) { restoring = false; restoreSelection(); }
        motion.setRunning(motionWanted);
        handler.post(update);
    }
    @Override protected void onPause() {
        resumed = false;
        handler.removeCallbacks(update);
        displays.unregisterDisplayListener(this);
        motion.setRunning(false);
        updateMotionStatus();
        super.onPause();
    }
    @Override protected void onSaveInstanceState(Bundle state) {
        state.putLong("startedAt", startedAt);
        state.putBoolean("motionWanted", motionWanted);
        state.putInt("resumes", resumes);
        super.onSaveInstanceState(state);
    }
    @Override public void onDisplayAdded(int id) { refreshDisplay(); }
    @Override public void onDisplayRemoved(int id) { refreshDisplay(); }
    @Override public void onDisplayChanged(int id) { refreshDisplay(); }
}
