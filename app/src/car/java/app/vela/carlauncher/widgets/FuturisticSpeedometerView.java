package app.vela.carlauncher.widgets;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.SweepGradient;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.LinearInterpolator;

public class FuturisticSpeedometerView extends View {

    private Paint bgArcPaint;
    private Paint progressArcPaint;
    private Paint glowPaint;
    private Paint tickPaint;
    private Paint textSpeedPaint;
    private Paint textUnitPaint;
    private Paint pulsePaint;
    private Paint innerHudPaint;
    private Paint dotGlowPaint;

    private RectF arcBounds;
    private RectF outerBounds;

    private float currentSpeed = 0f;
    private float targetSpeed = 0f;
    private float maxSpeed = 240f;

    private ValueAnimator speedAnimator;
    private ValueAnimator pulseAnimator;
    private float pulsePhase = 0f;

    public FuturisticSpeedometerView(Context context) {
        super(context);
        init();
    }

    public FuturisticSpeedometerView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        setLayerType(LAYER_TYPE_SOFTWARE, null);

        bgArcPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        bgArcPaint.setStyle(Paint.Style.STROKE);
        bgArcPaint.setStrokeWidth(dpToPx(6));
        bgArcPaint.setColor(Color.parseColor("#1A2542"));
        bgArcPaint.setStrokeCap(Paint.Cap.ROUND);

        progressArcPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        progressArcPaint.setStyle(Paint.Style.STROKE);
        progressArcPaint.setStrokeWidth(dpToPx(12));
        progressArcPaint.setStrokeCap(Paint.Cap.ROUND);

        glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        glowPaint.setStyle(Paint.Style.STROKE);
        glowPaint.setStrokeWidth(dpToPx(16));
        glowPaint.setStrokeCap(Paint.Cap.ROUND);
        glowPaint.setMaskFilter(new BlurMaskFilter(dpToPx(28), BlurMaskFilter.Blur.NORMAL));

        tickPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        tickPaint.setStyle(Paint.Style.STROKE);
        tickPaint.setStrokeCap(Paint.Cap.ROUND);

        pulsePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        pulsePaint.setStyle(Paint.Style.STROKE);
        pulsePaint.setStrokeWidth(dpToPx(1.5f));

        innerHudPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        innerHudPaint.setStyle(Paint.Style.STROKE);
        innerHudPaint.setStrokeWidth(dpToPx(1.5f));
        innerHudPaint.setColor(Color.parseColor("#3300E5FF"));
        // Kesik cizgili HUD cemberi
        innerHudPaint.setPathEffect(new DashPathEffect(new float[]{dpToPx(15), dpToPx(8)}, 0));

        dotGlowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        dotGlowPaint.setStyle(Paint.Style.FILL);
        dotGlowPaint.setMaskFilter(new BlurMaskFilter(dpToPx(12), BlurMaskFilter.Blur.NORMAL));

        textSpeedPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        textSpeedPaint.setColor(Color.WHITE);
        textSpeedPaint.setTextAlign(Paint.Align.CENTER);
        textSpeedPaint.setTextSize(dpToPx(96));
        textSpeedPaint.setTypeface(Typeface.create("sans-serif-thin", Typeface.NORMAL));
        textSpeedPaint.setShadowLayer(dpToPx(15), 0, 0, Color.parseColor("#00E5FF"));

        textUnitPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        textUnitPaint.setColor(Color.parseColor("#00E5FF"));
        textUnitPaint.setTextAlign(Paint.Align.CENTER);
        textUnitPaint.setTextSize(dpToPx(18));
        textUnitPaint.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        textUnitPaint.setLetterSpacing(0.3f);

        arcBounds = new RectF();
        outerBounds = new RectF();

        pulseAnimator = ValueAnimator.ofFloat(0f, 1f);
        pulseAnimator.setDuration(3000);
        pulseAnimator.setRepeatCount(ValueAnimator.INFINITE);
        pulseAnimator.setInterpolator(new LinearInterpolator());
        pulseAnimator.addUpdateListener(animation -> {
            pulsePhase = (float) animation.getAnimatedValue();
            invalidate();
        });
        pulseAnimator.start();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        float padding = dpToPx(60);
        arcBounds.set(padding, padding, w - padding, h - padding);
        outerBounds.set(padding - dpToPx(35), padding - dpToPx(35), w - padding + dpToPx(35), h - padding + dpToPx(35));

        // Çok daha canli ve parlak gradient
        int[] colors = {
            Color.parseColor("#00FFFF"), // Çok parlak Cyan
            Color.parseColor("#00B4DB"), // Mavi
            Color.parseColor("#8E2DE2"), // Mor
            Color.parseColor("#FF007F")  // Neon Pembe
        };
        float[] positions = {0f, 0.3f, 0.6f, 1f};

        SweepGradient gradient = new SweepGradient(w / 2f, h / 2f, colors, positions);
        android.graphics.Matrix matrix = new android.graphics.Matrix();
        matrix.setRotate(135f, w / 2f, h / 2f);
        gradient.setLocalMatrix(matrix);

        progressArcPaint.setShader(gradient);
        glowPaint.setShader(gradient);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        float cx = getWidth() / 2f;
        float cy = getHeight() / 2f;
        float startAngle = 135f;
        float sweepAngle = 270f;

        // 1. Iç İçe Geçen HUD Çemberleri (Sci-Fi Hissi)
        float hudRadius = arcBounds.width() / 2f - dpToPx(35);
        canvas.drawCircle(cx, cy, hudRadius, innerHudPaint);

        // 2. Pulse Radar Animasyonu (2 Dalga halinde)
        float maxRadius = outerBounds.width() / 2f - dpToPx(10);
        float minRadius = hudRadius;

        // Dalga 1
        float currentRadius1 = minRadius + (maxRadius - minRadius) * pulsePhase;
        int alpha1 = (int) (255 * (1f - pulsePhase) * 0.5f);
        pulsePaint.setColor(Color.parseColor("#00FFFF"));
        pulsePaint.setAlpha(alpha1);
        canvas.drawCircle(cx, cy, currentRadius1, pulsePaint);

        // Dalga 2 (Gecikmeli)
        float phase2 = pulsePhase - 0.5f;
        if (phase2 < 0) phase2 += 1f;
        float currentRadius2 = minRadius + (maxRadius - minRadius) * phase2;
        int alpha2 = (int) (255 * (1f - phase2) * 0.3f);
        pulsePaint.setAlpha(alpha2);
        canvas.drawCircle(cx, cy, currentRadius2, pulsePaint);

        // 3. Modern Dis Kadran Çizgileri (Tick marks)
        float radius = outerBounds.width() / 2f;
        int tickCount = 60;
        for (int i = 0; i <= tickCount; i++) {
            float angle = startAngle + (i * sweepAngle / tickCount);
            double rad = Math.toRadians(angle);
            float startX, startY, endX, endY;

            if (i % 5 == 0) {
                tickPaint.setStrokeWidth(dpToPx(2.5f));
                tickPaint.setColor(Color.parseColor("#CC00FFFF"));
                startX = cx + (float) Math.cos(rad) * (radius - dpToPx(14));
                startY = cy + (float) Math.sin(rad) * (radius - dpToPx(14));
            } else {
                tickPaint.setStrokeWidth(dpToPx(1f));
                tickPaint.setColor(Color.parseColor("#4400FFFF"));
                startX = cx + (float) Math.cos(rad) * (radius - dpToPx(6));
                startY = cy + (float) Math.sin(rad) * (radius - dpToPx(6));
            }
            endX = cx + (float) Math.cos(rad) * radius;
            endY = cy + (float) Math.sin(rad) * radius;
            canvas.drawLine(startX, startY, endX, endY, tickPaint);
        }

        // 4. Kalin Arka Plan Yayi
        canvas.drawArc(arcBounds, startAngle, sweepAngle, false, bgArcPaint);

        // 5. Hiz İlerleyişi (Progress)
        float progressSweep = (currentSpeed / maxSpeed) * sweepAngle;

        // Eger Hiz 0 ise sadece parlayan cok estetik bir "Nokta" (Neon Dot) goster
        float arcRadius = arcBounds.width() / 2f;
        if (progressSweep < 1f) {
            double startRad = Math.toRadians(startAngle);
            float dotX = cx + (float) Math.cos(startRad) * arcRadius;
            float dotY = cy + (float) Math.sin(startRad) * arcRadius;

            dotGlowPaint.setColor(Color.parseColor("#00FFFF"));
            canvas.drawCircle(dotX, dotY, dpToPx(8), dotGlowPaint); // Glowlu top

            Paint dotCore = new Paint(Paint.ANTI_ALIAS_FLAG);
            dotCore.setStyle(Paint.Style.FILL);
            dotCore.setColor(Color.WHITE);
            canvas.drawCircle(dotX, dotY, dpToPx(4), dotCore); // Beyaz merkez
        } else {
            if (progressSweep > sweepAngle) progressSweep = sweepAngle;
            // Parlama efekti ve Ana hat
            canvas.drawArc(arcBounds, startAngle, progressSweep, false, glowPaint);
            canvas.drawArc(arcBounds, startAngle, progressSweep, false, progressArcPaint);

            // Çizginin Ucunda Parlayan Top
            double endRad = Math.toRadians(startAngle + progressSweep);
            float endDotX = cx + (float) Math.cos(endRad) * arcRadius;
            float endDotY = cy + (float) Math.sin(endRad) * arcRadius;
            dotGlowPaint.setColor(Color.parseColor("#FF007F")); // Hiza gore pembe uclu glow
            canvas.drawCircle(endDotX, endDotY, dpToPx(10), dotGlowPaint);
            Paint dotCore = new Paint(Paint.ANTI_ALIAS_FLAG);
            dotCore.setStyle(Paint.Style.FILL);
            dotCore.setColor(Color.WHITE);
            canvas.drawCircle(endDotX, endDotY, dpToPx(5), dotCore);
        }

        // 6. Devasa İnce Tipografi Hız Metni
        canvas.drawText(String.valueOf((int) currentSpeed), cx, cy + dpToPx(18), textSpeedPaint);
        canvas.drawText("KM/H", cx, cy + dpToPx(54), textUnitPaint);
    }

    public void setSpeed(float speed) {
        this.targetSpeed = speed;
        if (speedAnimator != null && speedAnimator.isRunning()) {
            speedAnimator.cancel();
        }
        speedAnimator = ValueAnimator.ofFloat(currentSpeed, targetSpeed);
        speedAnimator.setDuration(700);
        speedAnimator.setInterpolator(new android.view.animation.OvershootInterpolator(1.2f));
        speedAnimator.addUpdateListener(animation -> {
            currentSpeed = (float) animation.getAnimatedValue();
            invalidate();
        });
        speedAnimator.start();
    }

    private float dpToPx(float dp) {
        return dp * getContext().getResources().getDisplayMetrics().density;
    }
}
