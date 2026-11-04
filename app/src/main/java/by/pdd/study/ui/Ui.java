package by.pdd.study.ui;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

public final class Ui {
    public static final int BG = Color.rgb(246, 247, 243);
    public static final int SURFACE = Color.WHITE;
    public static final int INK = Color.rgb(23, 35, 31);
    public static final int MUTED = Color.rgb(102, 115, 109);
    public static final int LINE = Color.rgb(226, 232, 227);
    public static final int GREEN = Color.rgb(23, 77, 64);
    public static final int GREEN_2 = Color.rgb(35, 130, 103);
    public static final int MINT = Color.rgb(221, 242, 232);
    public static final int RED = Color.rgb(174, 67, 61);
    public static final int RED_BG = Color.rgb(252, 235, 232);
    public static final int AMBER = Color.rgb(154, 103, 28);
    public static final int AMBER_BG = Color.rgb(251, 242, 219);

    private Ui() { }

    public static int dp(Context context, float value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    public static LinearLayout column(Context context) {
        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        return layout;
    }

    public static LinearLayout row(Context context) {
        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.HORIZONTAL);
        layout.setGravity(Gravity.CENTER_VERTICAL);
        return layout;
    }

    public static FrameLayout frame(Context context) {
        return new FrameLayout(context);
    }

    public static TextView text(Context context, String value, float size, int color, boolean bold) {
        TextView view = new TextView(context);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setTypeface(Typeface.create("sans-serif", bold ? Typeface.BOLD : Typeface.NORMAL));
        view.setLineSpacing(dp(context, 2), 1.06f);
        return view;
    }

    public static TextView label(Context context, String value) {
        TextView view = text(context, value.toUpperCase(), 11, GREEN_2, true);
        view.setLetterSpacing(0.12f);
        return view;
    }

    public static TextView pill(Context context, String value, int foreground, int background) {
        TextView view = text(context, value, 11, foreground, true);
        view.setGravity(Gravity.CENTER);
        view.setPadding(dp(context, 10), dp(context, 6), dp(context, 10), dp(context, 6));
        view.setBackground(round(background, 50));
        return view;
    }

    public static LinearLayout card(Context context) {
        LinearLayout card = column(context);
        card.setPadding(dp(context, 18), dp(context, 18), dp(context, 18), dp(context, 18));
        card.setBackground(round(SURFACE, 20));
        return card;
    }

    public static GradientDrawable round(int color, float radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(radius);
        return drawable;
    }

    public static GradientDrawable roundDp(Context context, int color, float radius) {
        return round(color, dp(context, radius));
    }

    public static RippleDrawable ripple(Context context, int color, float radiusDp) {
        GradientDrawable content = roundDp(context, color, radiusDp);
        GradientDrawable mask = roundDp(context, Color.WHITE, radiusDp);
        return new RippleDrawable(ColorStateList.valueOf(0x22174D40), content, mask);
    }

    public static Button button(Context context, String value, boolean primary) {
        Button button = new Button(context);
        button.setText(value);
        button.setTextSize(15);
        button.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        button.setTextColor(primary ? Color.WHITE : GREEN);
        button.setAllCaps(false);
        button.setGravity(Gravity.CENTER);
        button.setMinHeight(0);
        button.setMinimumHeight(0);
        button.setPadding(dp(context, 18), dp(context, 14), dp(context, 18), dp(context, 14));
        button.setBackground(ripple(context, primary ? GREEN : MINT, 15));
        button.setStateListAnimator(null);
        return button;
    }

    public static IconView icon(Context context, String name, int color, float sizeDp) {
        IconView icon = new IconView(context);
        icon.setIcon(name);
        icon.setIconColor(color);
        icon.setLayoutParams(new ViewGroup.LayoutParams(dp(context, sizeDp), dp(context, sizeDp)));
        return icon;
    }

    public static View space(Context context, int heightDp) {
        View view = new View(context);
        view.setLayoutParams(new ViewGroup.LayoutParams(1, dp(context, heightDp)));
        return view;
    }

    public static View divider(Context context) {
        View view = new View(context);
        view.setBackgroundColor(LINE);
        view.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(context, 1)));
        return view;
    }

    public static ProgressBar progress(Context context, int color) {
        ProgressBar bar = new ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal);
        bar.setMax(100);
        bar.setProgressTintList(ColorStateList.valueOf(color));
        bar.setProgressBackgroundTintList(ColorStateList.valueOf(LINE));
        bar.setIndeterminate(false);
        return bar;
    }

    public static void add(LinearLayout parent, View child, int width, int height,
                           int left, int top, int right, int bottom) {
        Context context = parent.getContext();
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(width, height);
        params.setMargins(dp(context, left), dp(context, top), dp(context, right), dp(context, bottom));
        parent.addView(child, params);
    }

    public static void addWeight(LinearLayout parent, View child, float weight,
                                 int left, int top, int right, int bottom) {
        Context context = parent.getContext();
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, weight);
        params.setMargins(dp(context, left), dp(context, top), dp(context, right), dp(context, bottom));
        parent.addView(child, params);
    }

    public static void clickable(View view, OnClickListener listener) {
        view.setClickable(true);
        view.setFocusable(true);
        view.setOnClickListener(listener);
    }

    public static void singleLine(TextView view) {
        view.setSingleLine(true);
        view.setEllipsize(TextUtils.TruncateAt.END);
    }
}
