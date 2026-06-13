package com.example.bachatkhata;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.view.View;
import android.view.animation.CycleInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.TextView;

import java.util.Locale;

public class AnimationHelper {

    /**
     * Animates a view sliding up from the bottom while fading in.
     * Useful for cards and list item entry animations.
     */
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

    /**
     * Apply a scale spring animation to a view.
     * Useful for button click feedback.
     */
    public static void applySpringPress(View view) {
        view.setScaleX(1f);
        view.setScaleY(1f);

        view.animate()
                .scaleX(0.92f)
                .scaleY(0.92f)
                .setDuration(100)
                .setListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        view.animate()
                                .scaleX(1f)
                                .scaleY(1f)
                                .setDuration(150)
                                .setInterpolator(new OvershootInterpolator(1.8f))
                                .setListener(null)
                                .start();
                    }
                })
                .start();
    }

    /**
     * Shakes a view horizontally (useful for incorrect PIN entries or input errors).
     */
    public static void animateShake(View view) {
        view.animate()
                .translationX(15f)
                .setDuration(350)
                .setInterpolator(new CycleInterpolator(5))
                .setListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        view.setTranslationX(0f);
                    }
                })
                .start();
    }

    /**
     * Starts a continuous pulse animation (scaling up/down slightly).
     */
    public static void startPulseAnimation(View view) {
        view.animate()
                .scaleX(1.05f)
                .scaleY(1.05f)
                .setDuration(800)
                .setListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        view.animate()
                                .scaleX(1f)
                                .scaleY(1f)
                                .setDuration(800)
                                .setListener(new AnimatorListenerAdapter() {
                                    @Override
                                    public void onAnimationEnd(Animator animation) {
                                        startPulseAnimation(view); // Infinite loop
                                    }
                                })
                                .start();
                    }
                })
                .start();
    }

    /**
     * Stops any running animation on the view and resets scales.
     */
    public static void stopPulseAnimation(View view) {
        view.animate().cancel();
        view.setScaleX(1f);
        view.setScaleY(1f);
    }

    /**
     * Animates numeric text in a TextView from a starting value to an ending value.
     */
    public static void animateTextCount(TextView textView, double startValue, double endValue, String currencySymbol) {
        ValueAnimator animator = ValueAnimator.ofFloat((float) startValue, (float) endValue);
        animator.setDuration(1200);
        animator.addUpdateListener(animation -> {
            float animatedValue = (float) animation.getAnimatedValue();
            if (currencySymbol != null) {
                textView.setText(String.format(Locale.US, "%s%,.2f", currencySymbol, animatedValue));
            } else {
                textView.setText(String.format(Locale.US, "%,.2f", animatedValue));
            }
        });
        animator.start();
    }
}
