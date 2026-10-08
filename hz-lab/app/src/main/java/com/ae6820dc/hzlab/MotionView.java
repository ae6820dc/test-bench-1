package com.ae6820dc.hzlab;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.view.Choreographer;
import android.view.View;

/** Time-based motion; callback counts are deliberately not presented as display Hz. */
public final class MotionView extends View implements Choreographer.FrameCallback {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private boolean running;
    private long origin, frames;
    private float phase;

    public MotionView(Context context) {
        super(context);
        setBackgroundColor(Color.rgb(225, 235, 245));
        paint.setColor(Color.rgb(18, 93, 168));
    }
    public void setRunning(boolean enabled) {
        if (running == enabled) return;
        running = enabled;
        if (enabled) {
            origin = 0;
            Choreographer.getInstance().postFrameCallback(this);
        } else {
            Choreographer.getInstance().removeFrameCallback(this);
        }
    }
    public long getFrameCount() { return frames; }
    @Override public void doFrame(long time) {
        if (!running) return;
        if (origin == 0) origin = time;
        phase = (float) ((Math.sin((time - origin) / 1_000_000_000.0 * Math.PI) + 1) / 2);
        frames++;
        invalidate();
        Choreographer.getInstance().postFrameCallback(this);
    }
    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float radius = 12 * getResources().getDisplayMetrics().density;
        float x = radius + phase * Math.max(0, getWidth() - 2 * radius);
        canvas.drawCircle(x, getHeight() / 2f, radius, paint);
    }
    @Override protected void onDetachedFromWindow() {
        setRunning(false);
        super.onDetachedFromWindow();
    }
}
