package com.example.bachatkhata;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

public class CircleScoreView extends View {

    private Paint backgroundPaint;
    private Paint progressPaint;
    private Paint textScorePaint;
    private Paint textLabelPaint;

    private float strokeWidth = 24f;
    private RectF arcBounds = new RectF();

    private int score = 0;
    private int minScore = 0;
    private int maxScore = 100;
    private float animatedSweepAngle = 0f;
    private String centerLabel = "SCORE";

    public CircleScoreView(Context context) {
        super(context);
        init();
    }

    public CircleScoreView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public CircleScoreView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        backgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        backgroundPaint.setColor(Color.parseColor("#E0DEFF")); // Soft clay placeholder
        backgroundPaint.setStyle(Paint.Style.STROKE);
        backgroundPaint.setStrokeWidth(strokeWidth);
        backgroundPaint.setStrokeCap(Paint.Cap.ROUND);

        progressPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        progressPaint.setStyle(Paint.Style.STROKE);
        progressPaint.setStrokeWidth(strokeWidth);
        progressPaint.setStrokeCap(Paint.Cap.ROUND);

        textScorePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        textScorePaint.setColor(Color.parseColor("#2D2D44")); // colorTextPrimary
        textScorePaint.setTextSize(96f);
        textScorePaint.setTextAlign(Paint.Align.CENTER);
        textScorePaint.setFakeBoldText(true);

        textLabelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        textLabelPaint.setColor(Color.parseColor("#6B6B8A")); // colorTextSecondary
        textLabelPaint.setTextSize(32f);
        textLabelPaint.setTextAlign(Paint.Align.CENTER);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        float pad = strokeWidth / 2 + 16;
        arcBounds.set(pad, pad, w - pad, h - pad);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        // Draw 270 degree arc from 135 degrees
        canvas.drawArc(arcBounds, 135f, 270f, false, backgroundPaint);

        // Draw active progress arc
        progressPaint.setColor(getGaugeColor());
        canvas.drawArc(arcBounds, 135f, animatedSweepAngle, false, progressPaint);

        // Draw centered score text
        float centerX = getWidth() / 2f;
        float centerY = getHeight() / 2f;

        // Draw score
        canvas.drawText(String.valueOf(score), centerX, centerY + 16f, textScorePaint);

        // Draw label
        canvas.drawText(centerLabel, centerX, centerY + 72f, textLabelPaint);
    }

    private int getGaugeColor() {
        float pct = getPercentage();
        if (pct >= 0.8f) {
            return Color.parseColor("#5DCAA5"); // Green (colorSecondary)
        } else if (pct >= 0.5f) {
            return Color.parseColor("#EF9F27"); // Amber (colorAccent)
        } else {
            return Color.parseColor("#E24B4A"); // Red (colorDanger)
        }
    }

    private float getPercentage() {
        if (maxScore == minScore) return 0f;
        float val = Math.min(maxScore, Math.max(minScore, score));
        return (val - minScore) / (maxScore - minScore);
    }

    public void setCenterLabel(String label) {
        this.centerLabel = label;
        invalidate();
    }

    public void setScore(int targetScore, int min, int max) {
        this.minScore = min;
        this.maxScore = max;
        this.score = targetScore;

        float targetPercentage = getPercentage();
        float targetSweepAngle = targetPercentage * 270f;

        ValueAnimator animator = ValueAnimator.ofFloat(0f, targetSweepAngle);
        animator.setDuration(1200);
        animator.setInterpolator(new DecelerateInterpolator());
        animator.addUpdateListener(animation -> {
            animatedSweepAngle = (float) animation.getAnimatedValue();
            invalidate();
        });
        animator.start();
    }

    public void setScoreInstant(int targetScore, int min, int max) {
        this.minScore = min;
        this.maxScore = max;
        this.score = targetScore;
        this.animatedSweepAngle = getPercentage() * 270f;
        invalidate();
    }
}
