package com.gladbox.driftscroll;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.app.KeyguardManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.widget.Button;
import android.widget.LinearLayout;
import java.util.Locale;
import java.util.Random;

public class ScrollService extends AccessibilityService {
    public static ScrollService instance;
    public static String status = "Ready when you are";
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Random random = new Random();
    private ScrollPlan plan;
    private WindowManager windows;
    private Button floatingStop;
    private Button floatingPause;
    private LinearLayout floatingControls;
    private boolean paused;
    private long pausedAt;
    private boolean running;
    private boolean ownAppVisible = true;
    private boolean receiverRegistered;
    private long deadline, nextSwipeAt;
    private int minSeconds, maxSeconds, generation;
    private final BroadcastReceiver screenOff = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) { stopSession("Stopped: screen locked"); }
    };

    @Override protected void onServiceConnected() {
        instance = this;
        status = "Ready when you are";
        windows = (WindowManager) getSystemService(WINDOW_SERVICE);
        IntentFilter filter = new IntentFilter(Intent.ACTION_SCREEN_OFF);
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(screenOff, filter, Context.RECEIVER_NOT_EXPORTED);
        else registerReceiver(screenOff, filter);
        receiverRegistered = true;
    }

    public boolean isRunning() { return running; }
    public void setOwnAppVisible(boolean visible) { ownAppVisible = visible; }
    public long remainingSeconds() { return deadline == 0 ? -1 : Math.max(0, (deadline - (paused ? pausedAt : SystemClock.elapsedRealtime()) + 999) / 1000); }

    private void togglePause() {
        if (!running) return;
        long now = SystemClock.elapsedRealtime();
        if (!screenUsable() || remainingSeconds() == 0) { stopSession("Session ended"); return; }
        generation++;
        handler.removeCallbacks(performSwipe);
        if (paused) {
            long pauseLength = now - pausedAt;
            if (deadline != 0) deadline += pauseLength;
            nextSwipeAt += pauseLength;
            paused = false;
            handler.postDelayed(performSwipe, Math.max(1, nextSwipeAt - now));
        } else {
            pausedAt = now;
            paused = true;
        }
        floatingPause.setText(paused ? "▶ Resume" : "Ⅱ Pause");
        floatingPause.setContentDescription(paused ? "Resume auto scrolling" : "Pause auto scrolling");
        handler.removeCallbacks(tick);
        handler.post(tick);
    }

    public boolean startSession(int min, int max, int minutes) {
        ScrollPlan.validate(min, max, minutes);
        stopSession("Ready when you are");
        minSeconds = min; maxSeconds = max;
        plan = new ScrollPlan(random);
        deadline = minutes == 0 ? 0 : SystemClock.elapsedRealtime() + minutes * 60_000L;
        nextSwipeAt = SystemClock.elapsedRealtime() + plan.delay(min, max);
        try { addStopButton(); }
        catch (RuntimeException error) {
            stopSession("Could not show Stop button. Re-enable the accessibility service.");
            return false;
        }
        running = true;
        status = "Open your PDF or reading app";
        handler.post(tick);
        handler.postDelayed(performSwipe, Math.max(1, nextSwipeAt - SystemClock.elapsedRealtime()));
        return true;
    }

    private final Runnable tick = new Runnable() {
        @Override public void run() {
            if (!running) return;
            if (!screenUsable()) { stopSession("Stopped: screen locked"); return; }
            long left = remainingSeconds();
            if (left == 0) { stopSession("Session complete"); return; }
            floatingStop.setText(left < 0 ? "■ Stop  ∞" : String.format(Locale.getDefault(), "■ Stop  %d:%02d", left / 60, left % 60));
            status = paused ? "Paused • tap Resume to continue" : ownAppVisible ? (left < 0 ? "Open your PDF • no time limit" : "Open your PDF • timer is running")
                    : (left < 0 ? "Reading • no time limit" : "Reading • " + left + " seconds remaining");
            handler.postDelayed(this, 250);
        }
    };

    private final Runnable performSwipe = new Runnable() {
        @Override public void run() {
            if (!running || paused) return;
            if (!screenUsable() || remainingSeconds() == 0) { stopSession("Session ended"); return; }
            if (ownAppVisible) { handler.postDelayed(this, 500); return; }
            DisplayMetrics metrics = new DisplayMetrics();
            // The service's default display reflects the current orientation.
            windows.getDefaultDisplay().getRealMetrics(metrics);
            final ScrollPlan.Swipe swipe;
            try { swipe = plan.swipe(metrics.widthPixels, metrics.heightPixels, metrics.density); }
            catch (RuntimeException error) { stopSession("Stopped: could not plan a swipe"); return; }
            Path path = new Path();
            path.moveTo(swipe.x(), swipe.y());
            path.cubicTo(swipe.cx1(), swipe.cy1(), swipe.cx2(), swipe.cy2(), swipe.endX(), swipe.endY());
            GestureDescription gesture = new GestureDescription.Builder()
                    .addStroke(new GestureDescription.StrokeDescription(path, 0, swipe.duration())).build();
            int token = generation;
            // Interval is measured start-to-start, independently of gesture duration.
            nextSwipeAt = SystemClock.elapsedRealtime() + plan.delay(minSeconds, maxSeconds);
            try {
                boolean accepted = dispatchGesture(gesture, new GestureResultCallback() {
                    @Override public void onCompleted(GestureDescription description) {
                        if (running && token == generation)
                            handler.postDelayed(performSwipe, Math.max(1, nextSwipeAt - SystemClock.elapsedRealtime()));
                    }
                    @Override public void onCancelled(GestureDescription description) {
                        if (running && token == generation) stopSession("Stopped: swipe interrupted");
                    }
                }, handler);
                if (!accepted) stopSession("Stopped: Android could not perform the swipe");
            } catch (RuntimeException error) { stopSession("Stopped: gesture unavailable"); }
        }
    };

    private boolean screenUsable() {
        return ((PowerManager) getSystemService(POWER_SERVICE)).isInteractive()
                && !((KeyguardManager) getSystemService(KEYGUARD_SERVICE)).isKeyguardLocked();
    }

    private void addStopButton() {
        floatingControls = new LinearLayout(this);
        floatingControls.setOrientation(LinearLayout.HORIZONTAL);
        floatingPause = controlButton("Ⅱ Pause");
        floatingPause.setContentDescription("Pause auto scrolling");
        floatingPause.setOnClickListener(view -> togglePause());
        floatingControls.addView(floatingPause, new LinearLayout.LayoutParams(dp(116), dp(52)));
        floatingStop = controlButton("■ Stop");
        floatingStop.setContentDescription("Stop auto scrolling session");
        LinearLayout.LayoutParams stopLayout = new LinearLayout.LayoutParams(WindowManager.LayoutParams.WRAP_CONTENT, dp(52));
        stopLayout.leftMargin = dp(6);
        floatingControls.addView(floatingStop, stopLayout);
        floatingStop.setOnClickListener(view -> stopSession("Stopped by you"));
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT, dp(52),
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP | Gravity.END;
        params.x = dp(12); params.y = dp(64);
        windows.addView(floatingControls, params);
    }

    private Button controlButton(String title) {
        Button button = new Button(this);
        button.setText(title);
        button.setTextColor(Color.WHITE);
        button.setTextSize(14);
        button.setAllCaps(false);
        GradientDrawable background = new GradientDrawable();
        background.setColor(Color.rgb(31, 65, 55));
        background.setCornerRadius(dp(24));
        button.setBackground(background);
        button.setPadding(dp(14), dp(8), dp(14), dp(8));
        return button;
    }

    public void stopSession(String reason) {
        running = false;
        paused = false;
        generation++;
        handler.removeCallbacksAndMessages(null);
        if (floatingControls != null) {
            try { windows.removeView(floatingControls); } catch (IllegalArgumentException ignored) { }
            floatingControls = null;
            floatingPause = null;
            floatingStop = null;
        }
        status = reason;
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    @Override public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED && event.getPackageName() != null) {
            String pkg = event.getPackageName().toString();
            // Ignore events from our own accessibility overlay (class android.widget.Button).
            if (getPackageName().equals(pkg) && !MainActivity.class.getName().contentEquals(event.getClassName() == null ? "" : event.getClassName())) return;
            ownAppVisible = getPackageName().equals(pkg);
            if (running && ("com.android.systemui".equals(pkg) || "com.android.settings".equals(pkg)))
                stopSession("Stopped: system controls opened");
        }
    }
    @Override public void onInterrupt() { stopSession("Service interrupted"); }
    @Override public void onDestroy() {
        stopSession("Accessibility service disconnected");
        if (receiverRegistered) unregisterReceiver(screenOff);
        if (instance == this) instance = null;
        super.onDestroy();
    }
}
