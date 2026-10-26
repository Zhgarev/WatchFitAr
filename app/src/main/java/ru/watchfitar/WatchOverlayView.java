package ru.watchfitar;

import android.content.Context;
import android.graphics.*;
import android.view.MotionEvent;
import android.view.View;

/** Moves the watch over a camera view. Measurements are anchored in the frozen image. */
public class WatchOverlayView extends View {
    public interface OnManualCalibration { void onMeasurement(float distanceViewPx); }
    private OnManualCalibration calibrationCallback;
    private WatchModel model;
    private float centerX=-1,centerY=-1, rotationDegrees=0;
    private float viewPixelsPerMm=0;
    private boolean calibrated=false, manualMode=false;
    private int tapCount=0;
    private final PointF[] taps={new PointF(),new PointF()};
    private final Paint paint=new Paint(3);
    private RectF highlightedMarker;
    private int referenceImageWidth,referenceImageHeight;
    public WatchOverlayView(Context ctx) { super(ctx); }
    public void setWatch(WatchModel m) { model=m; invalidate(); }
    public void setViewScale(float ppm,boolean measured) { viewPixelsPerMm=Math.max(0,ppm);calibrated=measured;invalidate(); }
    public void setRotation(float value) { rotationDegrees=value;invalidate(); }
    public void highlightReference(RectF reference,int width,int height) {
        highlightedMarker=reference;referenceImageWidth=width;referenceImageHeight=height;invalidate();
    }
    public void clearHighlight() {highlightedMarker=null;invalidate();}
    public void beginCalibration(OnManualCalibration cb) { calibrationCallback=cb;manualMode=true;tapCount=0;invalidate(); }
    public void cancelCalibration() { manualMode=false;tapCount=0;invalidate(); }
    public boolean isCalibrating() { return manualMode; }
    public void resetPosition() { centerX=-1;centerY=-1;invalidate(); }
    public void placeAtRatios(float x,float y) { centerX=x*getWidth();centerY=y*getHeight();invalidate(); }
    public float getPositionXRatio() { return centerX<0?0.5f:centerX/Math.max(1,getWidth()); }
    public float getPositionYRatio() { return centerY<0?0.5f:centerY/Math.max(1,getHeight()); }
    @Override protected void onDraw(Canvas c) {
        super.onDraw(c);
        if(model!=null && model.image!=null && model.casePixels()>0 && !manualMode) {
            float x=centerX<0?getWidth()/2f:centerX,y=centerY<0?getHeight()/2f:centerY;
            float ppm = viewPixelsPerMm>0?viewPixelsPerMm:Math.min(getWidth(),getHeight())/120f;
            float scale = model.diameterMm*ppm / model.casePixels();
            c.save(); c.translate(x,y); c.rotate(rotationDegrees); c.scale(scale,scale);
            paint.setColor(Color.WHITE);paint.setAlpha(255);
            c.drawBitmap(model.image,-model.caseCx(),-model.caseCy(),paint);c.restore();
            float r=model.diameterMm*ppm/2f;
            paint.setColor(calibrated?0xFF5FFFD4:0xFFFFBE63);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(2);
            c.drawCircle(x,y,r,paint);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(0xCC101923);c.drawRoundRect(x-r,y+r+9,x+r,y+r+40,8,8,paint);
            paint.setColor(calibrated?0xFF5FFFD4:Color.WHITE);paint.setTextSize(25);paint.setTextAlign(Paint.Align.CENTER);
            c.drawText(Float.toString(model.diameterMm)+" мм",x,y+r+32,paint);
        }
        if(highlightedMarker != null && referenceImageWidth>0 && referenceImageHeight>0) {
            float scale=Math.min(getWidth()/(float)referenceImageWidth,getHeight()/(float)referenceImageHeight);
            float left=(getWidth()-scale*referenceImageWidth)/2f;
            float top=(getHeight()-scale*referenceImageHeight)/2f;
            paint.setColor(0xFF60FF93);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(5);
            c.drawRect(left+highlightedMarker.left*scale-5,top+highlightedMarker.top*scale-5,
                left+highlightedMarker.right*scale+5,top+highlightedMarker.bottom*scale+5,paint);
            paint.setStyle(Paint.Style.FILL);
        }
        if(manualMode) {
            paint.setStyle(Paint.Style.FILL);paint.setColor(0xC9000000);
            c.drawRoundRect(10,10,getWidth()-10,75,12,12,paint);
            paint.setColor(Color.WHITE);paint.setTextSize(28);paint.setTextAlign(Paint.Align.CENTER);
            c.drawText("Линейка: нажми две отметки",getWidth()/2f,53,paint);
            paint.setColor(0xFF5FFFD4);paint.setStrokeWidth(3);paint.setStyle(Paint.Style.STROKE);
            for(int i=0;i<tapCount;i++) {PointF p=taps[i];c.drawCircle(p.x,p.y,15,paint);c.drawLine(p.x-20,p.y,p.x+20,p.y,paint);c.drawLine(p.x,p.y-20,p.x,p.y+20,paint);}
            if(tapCount==2) c.drawLine(taps[0].x,taps[0].y,taps[1].x,taps[1].y,paint);
        }
    }
    @Override public boolean onTouchEvent(MotionEvent event) {
        if(event.getAction()!=MotionEvent.ACTION_UP) return true;
        if(manualMode) {
            taps[tapCount++].set(event.getX(),event.getY());invalidate();
            if(tapCount==2) {
                float d=(float)Math.hypot(taps[1].x-taps[0].x,taps[1].y-taps[0].y);
                manualMode=false;tapCount=0;
                if(calibrationCallback!=null) calibrationCallback.onMeasurement(d);
            }
        } else { centerX=event.getX();centerY=event.getY();invalidate(); }
        return true;
    }
}
