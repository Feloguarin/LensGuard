package com.feloguarin.lensguard;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.core.view.ViewCompat;

import java.util.Locale;

/** Builds LensGuard's views in code, with the brand palette from docs/BRAND.md. */
final class Ui {
    static final int BG = Color.rgb(13, 20, 23);
    static final int CARD = Color.rgb(24, 35, 39);
    static final int RAISED = Color.rgb(34, 49, 54);
    static final int MINT = Color.rgb(183, 247, 121);
    static final int TEXT = Color.rgb(239, 245, 239);
    static final int MUTED = Color.rgb(158, 177, 181);
    static final int AMBER = Color.rgb(255, 198, 116);
    static final int AMBER_SURFACE = Color.rgb(43, 36, 27);

    private final Context context;

    Ui(Context context) {
        this.context = context;
    }

    int dp(float value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    GradientDrawable shape(int color, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radius));
        return drawable;
    }

    /** A touchable surface: the colour plus a ripple so every press is visible. */
    Drawable surface(int color, int radius) {
        int ripple = color == MINT ? Color.argb(70, 13, 20, 23) : Color.argb(50, 255, 255, 255);
        return new RippleDrawable(ColorStateList.valueOf(ripple), shape(color, radius), null);
    }

    /** A touchable outlined surface; the mask keeps the ripple inside the rounded outline. */
    Drawable outlineSurface(int color, int radius) {
        return new RippleDrawable(ColorStateList.valueOf(Color.argb(50, 255, 255, 255)), outline(color, radius),
                shape(Color.WHITE, radius));
    }

    GradientDrawable outline(int color, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(Color.TRANSPARENT);
        drawable.setStroke(dp(1), color);
        drawable.setCornerRadius(dp(radius));
        return drawable;
    }

    TextView text(CharSequence value, int size, int color) {
        TextView view = new TextView(context);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setLineSpacing(dp(3), 1);
        if (size >= 22) view.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        return view;
    }

    /** A screen title, announced as a heading by screen readers. */
    TextView title(CharSequence value) {
        TextView view = text(value, value.length() > 24 ? 25 : 28, TEXT);
        ViewCompat.setAccessibilityHeading(view, true);
        return view;
    }

    Button button(CharSequence title, Runnable action) {
        Button view = new Button(context);
        view.setText(title);
        view.setAllCaps(false);
        view.setTextSize(13);
        view.setTextColor(TEXT);
        view.setMinWidth(0);
        view.setMinimumWidth(0);
        view.setMinHeight(dp(48));
        view.setPadding(dp(8), dp(4), dp(8), dp(4));
        // Raised against both the page and cards; no elevation shadow, which looked smudged on dark cards.
        view.setBackground(surface(RAISED, 12));
        view.setStateListAnimator(null);
        view.setOnClickListener(v -> action.run());
        return view;
    }

    Button primary(CharSequence title, Runnable action) {
        Button button = button(title, action);
        button.setTextColor(BG);
        button.setBackground(surface(MINT, 12));
        return button;
    }

    void label(LinearLayout body, CharSequence title) {
        Locale locale = context.getResources().getConfiguration().getLocales().get(0);
        TextView view = text(title.toString().toUpperCase(locale), 10, MUTED);
        view.setLetterSpacing(.12f);
        view.setPadding(0, dp(12), 0, dp(6));
        ViewCompat.setAccessibilityHeading(view, true);
        body.addView(view);
    }

    LinearLayout row(LinearLayout body, View left, View right) {
        LinearLayout row = new LinearLayout(context);
        LinearLayout.LayoutParams first = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        first.setMargins(0, dp(6), dp(4), dp(6));
        row.addView(left, first);
        LinearLayout.LayoutParams second = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        second.setMargins(dp(4), dp(6), 0, dp(6));
        row.addView(right, second);
        body.addView(row);
        return row;
    }

    /** A full-width secondary action. */
    Button action(LinearLayout body, CharSequence title, Runnable action) {
        return full(body, button(title, action));
    }

    /** Adds a button across the full width of {@code body}. */
    Button full(LinearLayout body, Button button) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, dp(6), 0, dp(6));
        body.addView(button, params);
        return button;
    }

    LinearLayout card(LinearLayout body, CharSequence title, CharSequence copy) {
        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(16), dp(16), dp(16));
        card.setBackground(shape(CARD, 16));
        if (title != null) {
            TextView heading = text(title, 13, TEXT);
            heading.setTypeface(Typeface.DEFAULT_BOLD);
            heading.setPadding(0, 0, 0, dp(8));
            ViewCompat.setAccessibilityHeading(heading, true);
            card.addView(heading);
        }
        if (copy != null) card.addView(text(copy, 13, MUTED));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.setMargins(0, dp(10), 0, dp(4));
        body.addView(card, params);
        return card;
    }

    /** A small outlined tag such as "Camera-like name". */
    TextView chip(CharSequence value, int color) {
        TextView chip = text(value, 11, color);
        chip.setPadding(dp(8), dp(2), dp(8), dp(3));
        chip.setBackground(outline(color, 10));
        chip.setGravity(Gravity.CENTER_VERTICAL);
        return chip;
    }

    /** A horizontal row of chips; observations carry at most a few. */
    LinearLayout chips(TextView... chips) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        for (TextView chip : chips) {
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-2, -2);
            params.setMargins(0, dp(4), dp(6), 0);
            row.addView(chip, params);
        }
        return row;
    }

    static void enabled(View view, boolean value) {
        if (view == null) return;
        view.setEnabled(value);
        view.setAlpha(value ? 1f : .45f);
    }

    static void set(TextView view, CharSequence message) {
        if (view != null && !message.toString().contentEquals(view.getText())) view.setText(message);
    }
}
