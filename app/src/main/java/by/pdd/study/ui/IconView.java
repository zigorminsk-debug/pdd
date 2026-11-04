package by.pdd.study.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

/** Small dependency-free line icon set drawn on a 24×24 grid. */
public final class IconView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private String icon = "road";
    private boolean filled;

    public IconView(Context context) {
        super(context);
        init();
    }

    public IconView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        paint.setColor(Color.WHITE);
        paint.setStrokeWidth(1.8f);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeJoin(Paint.Join.ROUND);
        setContentDescription(null);
    }

    public void setIcon(String value) {
        icon = value;
        invalidate();
    }

    public void setIconColor(int color) {
        paint.setColor(color);
        invalidate();
    }

    public void setFilled(boolean value) {
        filled = value;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float scale = Math.min(getWidth(), getHeight()) / 24f;
        canvas.save();
        canvas.translate((getWidth() - 24f * scale) / 2f, (getHeight() - 24f * scale) / 2f);
        canvas.scale(scale, scale);
        paint.setStyle(filled ? Paint.Style.FILL : Paint.Style.STROKE);
        path.reset();

        switch (icon) {
            case "home":
                path.moveTo(3, 11); path.lineTo(12, 3); path.lineTo(21, 11);
                path.lineTo(21, 21); path.lineTo(15, 21); path.lineTo(15, 15);
                path.lineTo(9, 15); path.lineTo(9, 21); path.lineTo(3, 21); path.close();
                break;
            case "book":
                path.moveTo(4, 4); path.lineTo(10, 4); path.quadTo(12, 4, 12, 6);
                path.lineTo(12, 21); path.quadTo(10, 19, 7, 19); path.lineTo(4, 19); path.close();
                path.moveTo(20, 4); path.lineTo(14, 4); path.quadTo(12, 4, 12, 6);
                path.moveTo(12, 21); path.quadTo(14, 19, 17, 19); path.lineTo(20, 19); path.close();
                break;
            case "test":
                path.addRoundRect(new RectF(5, 4, 19, 21), 2, 2, Path.Direction.CW);
                path.moveTo(9, 4); path.lineTo(9, 2); path.lineTo(15, 2); path.lineTo(15, 4);
                path.moveTo(8, 10); path.lineTo(9.5f, 11.5f); path.lineTo(12, 8.5f);
                path.moveTo(14, 10); path.lineTo(17, 10);
                path.moveTo(8, 16); path.lineTo(9.5f, 17.5f); path.lineTo(12, 14.5f);
                path.moveTo(14, 16); path.lineTo(17, 16);
                break;
            case "user":
                path.addCircle(12, 7, 4, Path.Direction.CW);
                path.moveTo(4, 22); path.quadTo(4, 14, 12, 14); path.quadTo(20, 14, 20, 22);
                break;
            case "back":
                path.moveTo(20, 12); path.lineTo(4, 12);
                path.moveTo(10, 6); path.lineTo(4, 12); path.lineTo(10, 18);
                break;
            case "next":
                path.moveTo(4, 12); path.lineTo(20, 12);
                path.moveTo(14, 6); path.lineTo(20, 12); path.lineTo(14, 18);
                break;
            case "search":
                path.addCircle(10.5f, 10.5f, 6.5f, Path.Direction.CW);
                path.moveTo(15.5f, 15.5f); path.lineTo(21, 21);
                break;
            case "bookmark":
                path.moveTo(6, 3); path.lineTo(18, 3); path.lineTo(18, 22);
                path.lineTo(12, 18); path.lineTo(6, 22); path.close();
                break;
            case "clock":
                path.addCircle(12, 12, 9, Path.Direction.CW);
                path.moveTo(12, 7); path.lineTo(12, 12); path.lineTo(16, 14);
                break;
            case "play":
                paint.setStyle(Paint.Style.FILL);
                path.moveTo(7, 4); path.lineTo(20, 12); path.lineTo(7, 20); path.close();
                break;
            case "check":
                path.moveTo(4, 12); path.lineTo(9, 17); path.lineTo(20, 6);
                break;
            case "close":
                path.moveTo(5, 5); path.lineTo(19, 19); path.moveTo(19, 5); path.lineTo(5, 19);
                break;
            case "alert":
                path.moveTo(12, 3); path.lineTo(22, 21); path.lineTo(2, 21); path.close();
                path.moveTo(12, 9); path.lineTo(12, 14); path.moveTo(12, 17.5f); path.lineTo(12, 17.6f);
                break;
            case "external":
                path.moveTo(14, 3); path.lineTo(21, 3); path.lineTo(21, 10);
                path.moveTo(21, 3); path.lineTo(11, 13);
                path.moveTo(18, 14); path.lineTo(18, 21); path.lineTo(3, 21); path.lineTo(3, 6); path.lineTo(10, 6);
                break;
            case "refresh":
                path.moveTo(20, 8); path.lineTo(20, 3); path.lineTo(15, 3);
                path.moveTo(20, 3); path.quadTo(12, -2, 5, 4); path.quadTo(-2, 10, 3, 17);
                path.moveTo(4, 16); path.lineTo(4, 21); path.lineTo(9, 21);
                path.moveTo(4, 21); path.quadTo(12, 26, 19, 20); path.quadTo(26, 14, 21, 7);
                break;
            case "stats":
                path.moveTo(4, 21); path.lineTo(4, 13); path.lineTo(8, 13); path.lineTo(8, 21);
                path.moveTo(10, 21); path.lineTo(10, 3); path.lineTo(14, 3); path.lineTo(14, 21);
                path.moveTo(16, 21); path.lineTo(16, 8); path.lineTo(20, 8); path.lineTo(20, 21);
                break;
            case "info":
                path.addCircle(12, 12, 9, Path.Direction.CW);
                path.moveTo(12, 11); path.lineTo(12, 17); path.moveTo(12, 7); path.lineTo(12, 7.1f);
                break;
            case "road":
            default:
                path.moveTo(8, 2); path.lineTo(3, 22); path.moveTo(16, 2); path.lineTo(21, 22);
                path.moveTo(12, 3); path.lineTo(12, 7); path.moveTo(12, 10); path.lineTo(12, 14); path.moveTo(12, 17); path.lineTo(12, 21);
                break;
        }
        canvas.drawPath(path, paint);
        canvas.restore();
    }
}
