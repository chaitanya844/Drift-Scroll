package com.gladbox.driftscroll;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.InputType;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.WindowInsets;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

public class MainActivity extends Activity {
    private static final int INK = Color.rgb(30, 49, 42), GREEN = Color.rgb(37, 107, 87), MUTED = Color.rgb(91, 105, 97);
    private final Handler handler = new Handler(Looper.getMainLooper());
    private EditText minimum, maximum, duration;
    private TextView status, permissionStatus;
    private Button start, enable;
    private SharedPreferences preferences;
    private final Runnable refresh = new Runnable() {
        @Override public void run() { updateStatus(); handler.postDelayed(this, 500); }
    };

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        preferences = getSharedPreferences("reading", MODE_PRIVATE);
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        LinearLayout root = column();
        root.setPadding(dp(24), dp(20), dp(24), dp(28));
        scroll.addView(root);
        scroll.setOnApplyWindowInsetsListener((view, insets) -> {
            view.setPadding(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(),
                    insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom());
            return insets;
        });
        setContentView(scroll);
        label(root, "DRIFT  /  READING COMPANION", 12, GREEN, true);
        TextView title = label(root, "A little scroll.\nRoom to read.", 34, INK, true);
        title.setPadding(0, dp(20), 0, dp(12));
        label(root, "Gentle, short swipes for PDFs and long pages.", 16, MUTED, false);

        LinearLayout access = card(root);
        permissionStatus = label(access, "01  Enable scrolling", 17, INK, true);
        label(access, "Allow Drift Scroll in Android accessibility settings. It sends swipes without reading your screen.", 14, MUTED, false);
        enable = button(access, "Enable accessibility", false);
        enable.setOnClickListener(view -> new AlertDialog.Builder(this)
                .setTitle("Allow automatic swipes")
                .setMessage("Drift Scroll uses Android's accessibility service to send short swipes in the app you open. It also displays a floating Stop button. It does not read screen text, capture content, or send data online.\n\nYou control every session. Enable Drift Scroll on the next screen, then return here.")
                .setNegativeButton("Not now", null)
                .setPositiveButton("Open settings", (dialog, which) -> startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)))
                .show());

        LinearLayout settings = card(root);
        label(settings, "02  Set your reading rhythm", 17, INK, true);
        label(settings, "Random time between swipe starts · seconds", 14, MUTED, false);
        LinearLayout range = new LinearLayout(this);
        range.setOrientation(LinearLayout.HORIZONTAL);
        settings.addView(range);
        LinearLayout left = column(), right = column();
        range.addView(left, new LinearLayout.LayoutParams(0, -2, 1));
        range.addView(right, new LinearLayout.LayoutParams(0, -2, 1));
        minimum = field(left, "Minimum seconds", preferences.getInt("min", 8));
        maximum = field(right, "Maximum seconds", preferences.getInt("max", 18));
        duration = field(settings, "Stop after · minutes (0 = unlimited)", preferences.getInt("minutes", 0));
        remember(minimum, "min");
        remember(maximum, "max");
        remember(duration, "minutes");
        label(settings, "SHORT & SLOW", 11, GREEN, true).setPadding(0, dp(16), 0, dp(4));
        label(settings, "24–48 dp per swipe, with subtle curves and fresh touch positions. About 1–3 lines, depending on PDF zoom and your viewer.", 14, MUTED, false);

        status = label(root, "Ready when you are", 15, GREEN, true);
        status.setPadding(0, dp(20), 0, dp(8));
        status.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        start = button(root, "Start reading session", true);
        start.setOnClickListener(view -> toggleSession());
        label(root, "Start here, then switch to your PDF. Floating Pause freezes scrolling and the timer; Resume continues. Stop ends the session. Screen lock also stops it.", 13, MUTED, false).setPadding(0, dp(12), 0, 0);
    }

    private void toggleSession() {
        ScrollService service = ScrollService.instance;
        if (service == null) { updateStatus(); return; }
        if (service.isRunning()) { service.stopSession("Stopped by you"); updateStatus(); return; }
        try {
            int min = Integer.parseInt(minimum.getText().toString().trim());
            int max = Integer.parseInt(maximum.getText().toString().trim());
            int minutes = Integer.parseInt(duration.getText().toString().trim());
            ScrollPlan.validate(min, max, minutes);
            preferences.edit().putInt("min", min).putInt("max", max).putInt("minutes", minutes).apply();
            service.startSession(min, max, minutes);
            updateStatus();
        } catch (IllegalArgumentException error) {
            new AlertDialog.Builder(this).setTitle("Check your reading rhythm")
                    .setMessage("Use positive whole seconds, with minimum no greater than maximum. Enter 0 minutes for an unlimited session, or a positive number for a timer. Values must fit within 2,147,483,647.")
                    .setPositiveButton("OK", null).show();
        }
    }

    private void remember(EditText field, String key) {
        field.setText(preferences.getString(key + "_draft", field.getText().toString()));
        field.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                // Save exact drafts, including a temporarily empty field, without requiring Start.
                preferences.edit().putString(key + "_draft", s.toString()).apply();
            }
            @Override public void afterTextChanged(Editable text) { }
        });
    }

    private void updateStatus() {
        ScrollService service = ScrollService.instance;
        boolean connected = service != null, running = connected && service.isRunning();
        if (connected) service.setOwnAppVisible(true);
        permissionStatus.setText(connected ? "01  Accessibility connected" : "01  Enable scrolling");
        enable.setText(connected ? "Accessibility settings" : "Enable accessibility");
        start.setEnabled(connected);
        start.setAlpha(connected ? 1 : 0.5f);
        start.setText(running ? "Stop session" : "Start reading session");
        status.setText(connected ? ScrollService.status : "Enable accessibility to get started");
        minimum.setEnabled(!running); maximum.setEnabled(!running); duration.setEnabled(!running);
    }

    @Override protected void onResume() { super.onResume(); handler.post(refresh); }
    @Override protected void onPause() {
        handler.removeCallbacks(refresh);
        if (ScrollService.instance != null) ScrollService.instance.setOwnAppVisible(false);
        super.onPause();
    }
    private LinearLayout column() { LinearLayout view = new LinearLayout(this); view.setOrientation(LinearLayout.VERTICAL); return view; }
    private LinearLayout card(LinearLayout parent) {
        LinearLayout view = column(); view.setPadding(dp(18), dp(18), dp(18), dp(18));
        GradientDrawable bg = new GradientDrawable(); bg.setColor(Color.WHITE); bg.setCornerRadius(dp(20)); bg.setStroke(dp(1), Color.rgb(225, 231, 220)); view.setBackground(bg);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2); params.topMargin = dp(20); parent.addView(view, params); return view;
    }
    private TextView label(LinearLayout parent, String text, int size, int color, boolean bold) {
        TextView view = new TextView(this); view.setText(text); view.setTextSize(size); view.setTextColor(color);
        if (bold) view.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        view.setLineSpacing(dp(3), 1); view.setPadding(0, dp(3), 0, dp(5)); parent.addView(view); return view;
    }
    private EditText field(LinearLayout parent, String title, int value) {
        TextView caption = label(parent, title, 13, MUTED, false); caption.setPadding(0, dp(16), 0, 0);
        EditText view = new EditText(this); view.setId(View.generateViewId()); caption.setLabelFor(view.getId());
        view.setInputType(InputType.TYPE_CLASS_NUMBER); view.setSingleLine(true); view.setSelectAllOnFocus(true);
        view.setText(String.valueOf(value)); view.setTextSize(22); view.setTextColor(INK); view.setMinHeight(dp(52));
        parent.addView(view, new LinearLayout.LayoutParams(-1, -2)); return view;
    }
    private Button button(LinearLayout parent, String title, boolean primary) {
        Button view = new Button(this); view.setText(title); view.setAllCaps(false); view.setTextSize(16);
        view.setTextColor(primary ? Color.WHITE : GREEN);
        GradientDrawable bg = new GradientDrawable(); bg.setColor(primary ? GREEN : Color.rgb(233, 242, 235)); bg.setCornerRadius(dp(14)); view.setBackground(bg);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, dp(54)); params.topMargin = dp(12); parent.addView(view, params); return view;
    }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
