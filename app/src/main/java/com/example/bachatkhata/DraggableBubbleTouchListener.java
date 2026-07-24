package com.example.bachatkhata;

import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;

/**
 * Makes a small floating view draggable inside its parent, while still reporting
 * taps. Used by the floating calculator bubble.
 *
 * <p>A drag and a tap start identically, so the two are told apart by distance:
 * anything under the platform touch slop is a tap, everything else is a drag.
 * Without that check the bubble would either never open or jitter open whenever
 * a finger moved a pixel.
 *
 * <p>The bubble is clamped to the parent's bounds so it can never be flung
 * off-screen where the user can't get it back.
 */
public class DraggableBubbleTouchListener implements View.OnTouchListener {

    public interface OnBubbleTap {
        void onTap();
    }

    private final OnBubbleTap tapCallback;
    private final int touchSlop;

    private float downRawX, downRawY;
    private float downX, downY;
    private boolean dragging;

    public DraggableBubbleTouchListener(View view, OnBubbleTap tapCallback) {
        this.tapCallback = tapCallback;
        this.touchSlop = ViewConfiguration.get(view.getContext()).getScaledTouchSlop();
    }

    @Override
    public boolean onTouch(View view, MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downRawX = event.getRawX();
                downRawY = event.getRawY();
                downX = view.getX();
                downY = view.getY();
                dragging = false;
                return true;

            case MotionEvent.ACTION_MOVE: {
                float dx = event.getRawX() - downRawX;
                float dy = event.getRawY() - downRawY;

                if (!dragging && Math.hypot(dx, dy) > touchSlop) {
                    dragging = true;
                }
                if (dragging) {
                    View parent = (View) view.getParent();
                    if (parent == null) return true;

                    float maxX = parent.getWidth() - view.getWidth();
                    float maxY = parent.getHeight() - view.getHeight();
                    view.setX(clamp(downX + dx, 0f, Math.max(0f, maxX)));
                    view.setY(clamp(downY + dy, 0f, Math.max(0f, maxY)));
                }
                return true;
            }

            case MotionEvent.ACTION_UP:
                if (!dragging && tapCallback != null) {
                    view.performClick();
                    tapCallback.onTap();
                }
                return true;

            case MotionEvent.ACTION_CANCEL:
                dragging = false;
                return true;

            default:
                return false;
        }
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}
