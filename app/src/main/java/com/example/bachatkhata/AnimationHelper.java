package com.example.bachatkhata;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.view.View;
import android.view.animation.CycleInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.TextView;

import java.util.List;
import java.util.Locale;

public class AnimationHelper {

    /** Pop-out entrance: scales up from small to full with an overshoot bounce + fade. */
    public static void popOutAnimation(View view, int duration, int delay) {
        view.setAlpha(0f);
        view.setScaleX(0.7f);
        view.setScaleY(0.7f);
        view.setVisibility(View.VISIBLE);

        view.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(duration)
                .setStartDelay(delay)
                .setInterpolator(new OvershootInterpolator(2.5f))
                .start();
    }

    public static void animateSlideUpIn(View view, int duration, int delay) {
        view.setAlpha(0f);
        view.setTranslationY(100f);
        view.setVisibility(View.VISIBLE);

        view.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(duration)
                .setStartDelay(delay)
                .setInterpolator(new OvershootInterpolator(1.2f))
                .start();
    }

    // Card entry: translateY(-20dp -> 0) + alpha(0 -> 1), 300ms, DecelerateInterpolator
    public static void cardEntryAnimation(View view, int delayMs) {
        view.setAlpha(0f);
        // Translate up by 20dp in pixels
        float density = view.getContext().getResources().getDisplayMetrics().density;
        view.setTranslationY(-20f * density);
        view.setVisibility(View.VISIBLE);

        view.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(300)
                .setStartDelay(delayMs)
                .setInterpolator(new DecelerateInterpolator())
                .start();
    }

    // Staggered entry animation for lists/grid items
    public static void staggeredEntry(List<View> views, int startDelay, int stepDelay) {
        for (int i = 0; i < views.size(); i++) {
            cardEntryAnimation(views.get(i), startDelay + (i * stepDelay));
        }
    }

    // Button press: scale to 0.96 with spring (stiffness=400, damping=0.7)
    // We will simulate the spring overshoot using an OvershootInterpolator
    public static void buttonPressAnimation(View view) {
        view.setScaleX(1f);
        view.setScaleY(1f);

        view.animate()
                .scaleX(0.96f)
                .scaleY(0.96f)
                .setDuration(100)
                .setListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        view.animate()
                                .scaleX(1f)
                                .scaleY(1f)
                                .setDuration(200)
                                .setInterpolator(new OvershootInterpolator(1.5f))
                                .setListener(null)
                                .start();
                    }
                })
                .start();
    }

    // Shake animation for incorrect PIN inputs
    public static void shakeAnimation(View view) {
        view.animate()
                .translationX(15f)
                .setDuration(350)
                .setInterpolator(new CycleInterpolator(4))
                .setListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        view.setTranslationX(0f);
                    }
                })
                .start();
    }

    // Continuous pulse scale animation
    public static void pulseAnimation(View view) {
        view.animate()
                .scaleX(1.04f)
                .scaleY(1.04f)
                .setDuration(600)
                .setListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        view.animate()
                                .scaleX(1f)
                                .scaleY(1f)
                                .setDuration(600)
                                .setListener(new AnimatorListenerAdapter() {
                                    @Override
                                    public void onAnimationEnd(Animator animation) {
                                        pulseAnimation(view); // repeat indefinitely
                                    }
                                })
                                .start();
                    }
                })
                .start();
    }

    public static void stopPulseAnimation(View view) {
        view.animate().cancel();
        view.setScaleX(1f);
        view.setScaleY(1f);
    }

    // Count up anim for numeric views
    public static void countUpAnimation(TextView textView, double from, double to, int durationMs, String prefix) {
        ValueAnimator animator = ValueAnimator.ofFloat((float) from, (float) to);
        animator.setDuration(durationMs);
        animator.addUpdateListener(animation -> {
            float val = (float) animation.getAnimatedValue();
            String prefixText = prefix != null ? prefix : "";
            // Use local currency symbols or format helper if available
            textView.setText(String.format(Locale.US, "%s%,.2f", prefixText, val));
        });
        animator.start();
    }

    public static void countUpAmountAnimation(TextView textView, double from, double to, int durationMs) {
        ValueAnimator animator = ValueAnimator.ofFloat((float) from, (float) to);
        animator.setDuration(durationMs);
        animator.addUpdateListener(animation -> {
            float val = (float) animation.getAnimatedValue();
            textView.setText(CurrencyManager.getInstance().formatAmount(val));
        });
        animator.start();
    }

    public static void countUpIntegerAnimation(TextView textView, int from, int to, int durationMs) {
        ValueAnimator animator = ValueAnimator.ofInt(from, to);
        animator.setDuration(durationMs);
        animator.addUpdateListener(animation -> {
            int val = (int) animation.getAnimatedValue();
            textView.setText(String.valueOf(val));
        });
        animator.start();
    }
}
