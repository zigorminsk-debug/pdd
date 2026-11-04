package by.pdd.study.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;

public final class ProgressRingView extends View {
    private final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint progress = new Paint(Paint.ANTI_ALIAS_FLAG);
    private float fraction;

    public ProgressRingView(Context context) {
        super(context);
        track.setStyle(Paint.Style.STROKE);
        track.setColor(0xFFE2E8E3);
        track.setStrokeCap(Paint.Cap.ROUND);
        progress.setStyle(Paint.Style.STROKE);
        progress.setColor(0xFF238267);
        progress.setStrokeCap(Paint.Cap.ROUND);
    }

    public void setFraction(float value) {
        fraction = Math.max(0f, Math.min(1f, value));
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float stroke = getWidth() * 0.09f;
        track.setStrokeWidth(stroke);
        progress.setStrokeWidth(stroke);
        RectF bounds = new RectF(stroke, stroke, getWidth() - stroke, getHeight() - stroke);
        canvas.drawArc(bounds, -90, 360, false, track);
        canvas.drawArc(bounds, -90, 360 * fraction, false, progress);
    }
}
