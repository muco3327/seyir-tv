package tv.seyir.app;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Home-only styling. Deliberately independent from the player's shared Ui class. */
final class HomeStyle {
    static final int BG = Color.rgb(12, 14, 19);
    static final int PANEL = Color.rgb(28, 31, 39);
    static final int WHITE = Color.rgb(245, 246, 249);
    static final int MUTED = Color.rgb(166, 174, 190);
    static final int ACCENT = Color.rgb(141, 222, 209);
    static int dp(Context c, float n) { return Math.round(n * c.getResources().getDisplayMetrics().density); }
    static GradientDrawable shape(Context c, int color, int border, int radius) {
        GradientDrawable d = new GradientDrawable(); d.setColor(color);
        d.setCornerRadius(dp(c, radius));
        if (border != 0) d.setStroke(dp(c, 2), border);
        return d;
    }
    static LinearLayout column(Context c) {
        LinearLayout v = new LinearLayout(c); v.setOrientation(LinearLayout.VERTICAL); return v;
    }
    static LinearLayout row(Context c) {
        LinearLayout v = new LinearLayout(c); v.setOrientation(LinearLayout.HORIZONTAL);
        v.setGravity(Gravity.CENTER_VERTICAL); return v;
    }
    static TextView text(Context c, String value, int size, int color, boolean bold) {
        TextView t = new TextView(c); t.setText(value); t.setTextSize(size); t.setTextColor(color);
        t.setFontFeatureSettings("kern"); t.setIncludeFontPadding(false);
        t.setTypeface(Typeface.create("sans-serif" + (bold ? "-medium" : ""), Typeface.NORMAL));
        return t;
    }
    static TextView button(Context c, String label, boolean active, Runnable action) {
        TextView b = text(c, label, 14, active ? WHITE : MUTED, true);
        b.setGravity(Gravity.CENTER); b.setPadding(dp(c, 15), dp(c, 11), dp(c, 15), dp(c, 11));
        b.setMinHeight(dp(c, 44)); b.setSingleLine(true);
        focus(b, active, 14); b.setOnClickListener(v -> action.run()); return b;
    }
    static void focus(View view, boolean active, int radius) {
        view.setFocusable(true); view.setClickable(true);
        view.setBackground(shape(view.getContext(), active ? Color.rgb(49, 57, 66) : PANEL, 0, radius));
        view.setOnFocusChangeListener((v, focused) -> {
            v.setBackground(shape(v.getContext(), focused ? Color.rgb(51, 61, 73) : active ? Color.rgb(49, 57, 66) : PANEL,
                focused ? WHITE : 0, radius));
            v.animate().scaleX(focused ? 1.025f : 1f).scaleY(focused ? 1.025f : 1f).setDuration(130).start();
            v.setElevation(dp(v.getContext(), focused ? 8 : 0));
        });
    }
}
