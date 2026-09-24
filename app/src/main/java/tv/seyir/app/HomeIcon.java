package tv.seyir.app;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.drawable.Drawable;

/** Small home-screen line icons, independent of fonts and player resources. */
final class HomeIcon extends Drawable {
    private final String kind;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    HomeIcon(String kind, int color) {
        this.kind = kind; paint.setColor(color); paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(1.7f); paint.setStrokeCap(Paint.Cap.ROUND); paint.setStrokeJoin(Paint.Join.ROUND);
    }
    void color(int color) { paint.setColor(color); invalidateSelf(); }
    @Override public void draw(Canvas c) {
        c.save(); c.translate(getBounds().left, getBounds().top);
        c.scale(getBounds().width() / 24f, getBounds().height() / 24f);
        switch (kind) {
            case "search":
                c.drawCircle(10, 10, 6.5f, paint); c.drawLine(15, 15, 21, 21, paint); break;
            case "close":
                c.drawLine(6, 6, 18, 18, paint); c.drawLine(18, 6, 6, 18, paint); break;
            case "settings":
                c.drawCircle(12, 12, 7, paint); c.drawCircle(12, 12, 2.5f, paint);
                for (int i = 0; i < 8; i++) { c.save(); c.rotate(i * 45, 12, 12); c.drawLine(12, 2, 12, 5, paint); c.restore(); } break;
            case "filter":
                for (int i = 0; i < 3; i++) { float y = 6 + i * 6; c.drawLine(3, y, 21, y, paint); }
                c.drawCircle(8, 6, 2, paint); c.drawCircle(16, 12, 2, paint); c.drawCircle(10, 18, 2, paint); break;
            case "play":
                Path triangle = new Path(); triangle.moveTo(8, 5); triangle.lineTo(19, 12); triangle.lineTo(8, 19); triangle.close(); c.drawPath(triangle, paint); break;
            default:
                Path star = new Path();
                for (int i = 0; i < 10; i++) {
                    double a = Math.toRadians(-90 + i * 36); float r = i % 2 == 0 ? 9 : 4.3f;
                    float x = 12 + (float)Math.cos(a) * r, y = 12 + (float)Math.sin(a) * r;
                    if (i == 0) star.moveTo(x, y); else star.lineTo(x, y);
                }
                star.close(); c.drawPath(star, paint);
        }
        c.restore();
    }
    @Override public void setAlpha(int alpha) { paint.setAlpha(alpha); invalidateSelf(); }
    @Override public void setColorFilter(ColorFilter filter) { paint.setColorFilter(filter); invalidateSelf(); }
    @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
}
