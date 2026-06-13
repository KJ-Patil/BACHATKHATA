package com.example.bachatkhata;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;

public class CircularProgressView extends View {

    private final Paint trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint progressPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    
    private final RectF oval = new RectF();
    private float progress = 0f; // 0 to 100
    
    private int trackColor = Color.parseColor("#E0DEFF"); // default light border/surface color
    private int progressColor = Color.parseColor("#5DCAA5"); // colorSecondary mint green
    private float strokeWidth = 16f; // pixels

    public CircularProgressView(Context context) {
        super(context);
        init();
    }

    public CircularProgressView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public CircularProgressView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        trackPaint.setStyle(Paint.Style.STROKE);
        trackPaint.setStrokeWidth(strokeWidth);
        trackPaint.setColor(trackColor);
        trackPaint.setStrokeCap(Paint.Cap.ROUND);

        progressPaint.setStyle(Paint.Style.STROKE);
        progressPaint.setStrokeWidth(strokeWidth);
        progressPaint.setColor(progressColor);
        progressPaint.setStrokeCap(Paint.Cap.ROUND);

        textPaint.setColor(Color.parseColor("#1A1A2E")); // colorTextPrimary
        textPaint.setTextSize(48f);
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setFakeBoldText(true);
    }

    public void setProgress(float progress) {
        this.progress = Math.max(0, Math.min(progress, 100));
        invalidate();
    }

    public void setColors(int trackColor, int progressColor) {
        this.trackColor = trackColor;
        this.progressColor = progressColor;
        trackPaint.setColor(trackColor);
        progressPaint.setColor(progressColor);
        invalidate();
    }

    public void setTextColor(int color) {
        textPaint.setColor(color);
        invalidate();
    }

    public void setStrokeWidth(float widthDp) {
        this.strokeWidth = widthDp * getResources().getDisplayMetrics().density;
        trackPaint.setStrokeWidth(strokeWidth);
        progressPaint.setStrokeWidth(strokeWidth);
        invalidate();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        float padding = strokeWidth / 2f + 4f;
        oval.set(padding, padding, w - padding, h - padding);
        textPaint.setTextSize(w * 0.22f); // dynamically scale text size based on view width
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        
        // 1. Draw Track Circle
        canvas.drawOval(oval, trackPaint);
        
        // 2. Draw Progress Arc
        float sweepAngle = (progress / 100f) * 360f;
        canvas.drawArc(oval, -90f, sweepAngle, false, progressPaint);
        
        // 3. Draw Percentage Text
        String text = String.format(java.util.Locale.US, "%.0f%%", progress);
        float textHeight = textPaint.descent() - textPaint.ascent();
        float textOffset = (textHeight / 2) - textPaint.descent();
        canvas.drawText(text, oval.centerX(), oval.centerY() + textOffset, textPaint);
    }
}
