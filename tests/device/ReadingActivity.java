package com.gladbox.scrolltest;

import android.app.Activity;
import android.os.Bundle;
import android.graphics.Typeface;
import android.util.Log;
import android.widget.ScrollView;
import android.widget.TextView;

/** Temporary on-device reading surface, uninstalled after validation. */
public class ReadingActivity extends Activity {
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        ScrollView scroll = new ScrollView(this);
        TextView code = new TextView(this);
        code.setTextSize(18);
        code.setTypeface(Typeface.MONOSPACE);
        code.setPadding(40, 60, 40, 60);
        StringBuilder text = new StringBuilder("DRIFT SCROLL — READING TEST\n\n");
        for (int i = 1; i <= 300; i++) text.append(String.format(java.util.Locale.ROOT, "%03d  result = readNextLine(document);\n", i));
        code.setText(text);
        scroll.addView(code);
        scroll.setOnScrollChangeListener((view, x, y, oldX, oldY) -> Log.i("DriftScrollTest", "scrollY=" + y + " delta=" + (y-oldY)));
        setContentView(scroll);
        Log.i("DriftScrollTest", "READY scrollY=0");
    }
}
