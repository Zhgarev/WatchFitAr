package ru.watchfitar;

import android.content.Context;
import android.graphics.*;
import android.view.*;

/** Two taps identify the outer edges of the actual round case in the source photo. */
public class PointPickerView extends View {
    private final Bitmap bitmap;
    public final PointF[] points = new PointF[2];
    private int count=0;
    private final Paint paint = new Paint(3);
    private final RectF imageRect=new RectF();
    private float fitScale=1;
    public PointPickerView(Context ctx, Bitmap bitmap) { super(ctx); this.bitmap=bitmap; setBackgroundColor(Color.rgb(13,20,32)); }
    private void calculate() {
        fitScale = Math.min(getWidth()/(float)bitmap.getWidth(),getHeight()/(float)bitmap.getHeight());
        float w=bitmap.getWidth()*fitScale, h=bitmap.getHeight()*fitScale;
        imageRect.set((getWidth()-w)/2,(getHeight()-h)/2,(getWidth()+w)/2,(getHeight()+h)/2);
    }
    public boolean valid() { return count==2 && Math.hypot(points[1].x-points[0].x,points[1].y-points[0].y)>15; }
    public void reset() { count=0;points[0]=null;points[1]=null;invalidate(); }
    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas); calculate();
        paint.setColor(Color.WHITE);paint.setStyle(Paint.Style.FILL);
        canvas.drawBitmap(bitmap,null,imageRect,paint);
        for(int i=0;i<count;i++) {
            PointF p=points[i]; float x=imageRect.left+p.x*fitScale,y=imageRect.top+p.y*fitScale;
            paint.setColor(Color.rgb(0,240,185));paint.setStrokeWidth(4);paint.setStyle(Paint.Style.STROKE);
            canvas.drawCircle(x,y,15,paint);canvas.drawLine(x-22,y,x+22,y,paint);canvas.drawLine(x,y-22,x,y+22,paint);
        }
        if(count==2){
            paint.setColor(Color.rgb(0,240,185));paint.setStrokeWidth(2);
            canvas.drawLine(imageRect.left+points[0].x*fitScale,imageRect.top+points[0].y*fitScale,
                imageRect.left+points[1].x*fitScale,imageRect.top+points[1].y*fitScale,paint);
        }
    }
    @Override public boolean onTouchEvent(android.view.MotionEvent e) {
        if(e.getAction()!=MotionEvent.ACTION_UP) return true;
        calculate();
        if(!imageRect.contains(e.getX(),e.getY())) return true;
        if(count==2) count=0;
        points[count++]=new PointF((e.getX()-imageRect.left)/fitScale,(e.getY()-imageRect.top)/fitScale);
        invalidate();return true;
    }
}
