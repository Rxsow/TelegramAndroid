package org.telegram.ui.Components;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.View;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.Utilities;

/**
 * Tele iOS: iOS-style vertical HUD indicator for the video player.
 * Shows a rounded dark pill with a vertical fill track and an icon
 * (brightness sun / volume speaker) while the user drags on the
 * left or right side of the screen.
 */
public class PlayerHudView extends View {

    public static final int TYPE_BRIGHTNESS = 0;
    public static final int TYPE_VOLUME = 1;

    private final Paint backgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint iconPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final RectF pillRect = new RectF();
    private final RectF trackRect = new RectF();

    private int type = TYPE_BRIGHTNESS;
    private float value;
    private boolean showing;

    public PlayerHudView(Context context) {
        super(context);
        backgroundPaint.setColor(0xB31C1C1E);
        trackPaint.setColor(0x4DFFFFFF);
        fillPaint.setColor(0xFFFFFFFF);
        iconPaint.setColor(0xFFFFFFFF);
        setAlpha(0f);
        setScaleX(0.92f);
        setScaleY(0.92f);
        setVisibility(GONE);
    }

    public void setTypeAndValue(int newType, float newValue) {
        boolean sideChanged = type != newType;
        type = newType;
        value = Utilities.clamp(newValue, 1f, 0f);
        if (sideChanged) {
            requestLayout();
        }
        invalidate();
    }

    public void setValue(float newValue) {
        value = Utilities.clamp(newValue, 1f, 0f);
        invalidate();
    }

    public void showAnimated() {
        showing = true;
        if (getVisibility() != VISIBLE) {
            setVisibility(VISIBLE);
        }
        animate().cancel();
        animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(180)
            .setInterpolator(CubicBezierInterpolator.DEFAULT).start();
    }

    public void hideAnimated() {
        showing = false;
        animate().alpha(0f).scaleX(0.92f).scaleY(0.92f).setDuration(220)
            .setInterpolator(CubicBezierInterpolator.DEFAULT)
            .withEndAction(() -> {
                if (!showing) {
                    setVisibility(GONE);
                }
            }).start();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        setMeasuredDimension(AndroidUtilities.dp(48), AndroidUtilities.dp(190));
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        float x = type == TYPE_BRIGHTNESS
            ? AndroidUtilities.dp(20)
            : getRootView().getWidth() - getMeasuredWidth() - AndroidUtilities.dp(20);
        setTranslationX(x);
        setTranslationY((getRootView().getHeight() - getMeasuredHeight()) / 2f);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float inset = AndroidUtilities.dp(8);
        pillRect.set(inset, inset, getMeasuredWidth() - inset, getMeasuredHeight() - inset);
        canvas.drawRoundRect(pillRect, AndroidUtilities.dp(24), AndroidUtilities.dp(24), backgroundPaint);

        float trackInset = AndroidUtilities.dp(17);
        trackRect.set(trackInset, trackInset + AndroidUtilities.dp(46), getMeasuredWidth() - trackInset, pillRect.bottom - trackInset - AndroidUtilities.dp(46));
        canvas.drawRoundRect(trackRect, AndroidUtilities.dp(6), AndroidUtilities.dp(6), trackPaint);

        float fillHeight = trackRect.height() * value;
        if (fillHeight < AndroidUtilities.dp(8)) {
            fillHeight = AndroidUtilities.dp(8);
        }
        RectF fillRect = new RectF(trackRect.left, trackRect.bottom - fillHeight, trackRect.right, trackRect.bottom);
        canvas.drawRoundRect(fillRect, AndroidUtilities.dp(6), AndroidUtilities.dp(6), fillPaint);

        drawIcon(canvas);
    }

    private void drawIcon(Canvas canvas) {
        float cx = getMeasuredWidth() / 2f;
        float cy = getMeasuredHeight() - AndroidUtilities.dp(30);
        iconPaint.setStyle(Paint.Style.STROKE);
        iconPaint.setStrokeWidth(AndroidUtilities.dp(2));
        iconPaint.setStrokeCap(Paint.Cap.ROUND);
        if (type == TYPE_BRIGHTNESS) {
            canvas.drawCircle(cx, cy, AndroidUtilities.dp(7), iconPaint);
            for (int a = 0; a < 8; a++) {
                double angle = Math.PI / 4 * a;
                float x1 = (float) (cx + Math.cos(angle) * AndroidUtilities.dp(11));
                float y1 = (float) (cy + Math.sin(angle) * AndroidUtilities.dp(11));
                float x2 = (float) (cx + Math.cos(angle) * AndroidUtilities.dp(14));
                float y2 = (float) (cy + Math.sin(angle) * AndroidUtilities.dp(14));
                canvas.drawLine(x1, y1, x2, y2, iconPaint);
            }
        } else {
            Path speaker = new Path();
            float bx = cx - AndroidUtilities.dp(9);
            speaker.moveTo(bx, cy - AndroidUtilities.dp(3));
            speaker.lineTo(bx + AndroidUtilities.dp(4), cy - AndroidUtilities.dp(3));
            speaker.lineTo(bx + AndroidUtilities.dp(8), cy - AndroidUtilities.dp(7));
            speaker.lineTo(bx + AndroidUtilities.dp(8), cy + AndroidUtilities.dp(7));
            speaker.lineTo(bx + AndroidUtilities.dp(4), cy + AndroidUtilities.dp(3));
            speaker.lineTo(bx, cy + AndroidUtilities.dp(3));
            speaker.close();
            iconPaint.setStyle(Paint.Style.FILL);
            canvas.drawPath(speaker, iconPaint);
            iconPaint.setStyle(Paint.Style.STROKE);
            canvas.drawArc(bx + AndroidUtilities.dp(10), cy - AndroidUtilities.dp(7), bx + AndroidUtilities.dp(20), cy + AndroidUtilities.dp(7), -55, 110, false, iconPaint);
            canvas.drawArc(bx + AndroidUtilities.dp(10), cy - AndroidUtilities.dp(11), bx + AndroidUtilities.dp(26), cy + AndroidUtilities.dp(11), -55, 110, false, iconPaint);
        }
    }

    @Override
    public boolean onTouchEvent(android.view.MotionEvent event) {
        // Tele iOS: HUD never intercepts touches – it is a pure pass-through indicator
        return false;
    }
}
