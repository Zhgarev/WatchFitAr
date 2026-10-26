package ru.watchfitar;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.RectF;

/** Locate a filled black 50 x 10 mm reference bar. It must be in the same plane as the wrist. */
public final class MarkerDetector {
    private MarkerDetector() { }
    public static final class Result {
        public final float originalPixelsPerMm,confidence;
        public final RectF rect;
        Result(float scale, float score, RectF r) { originalPixelsPerMm=scale;confidence=score;rect=r; }
    }
    public static Result detect(Bitmap input) {
        int originalW=input.getWidth(),originalH=input.getHeight();
        float reduction=Math.min(1f, 960f/Math.max(originalW,originalH));
        Bitmap bmp = reduction<1f ? Bitmap.createScaledBitmap(input,Math.max(1,Math.round(originalW*reduction)),Math.max(1,Math.round(originalH*reduction)),true) : input;
        int w=bmp.getWidth(),h=bmp.getHeight(),n=w*h;
        int[] pixels=new int[n];bmp.getPixels(pixels,0,w,0,0,w,h);
        boolean[] dark=new boolean[n],seen=new boolean[n];
        for(int i=0;i<n;i++) {
            int r=Color.red(pixels[i]),g=Color.green(pixels[i]),b=Color.blue(pixels[i]);
            dark[i]=(r*30+g*59+b*11)<(92*100) && Math.abs(r-g)<45 && Math.abs(g-b)<45;
        }
        int[] queue=new int[n];
        double best=0;Result candidate=null;
        for(int start=0;start<n;start++) {
            if(!dark[start] || seen[start]) continue;
            seen[start]=true;int head=0,tail=1;queue[0]=start;
            int minX=start%w,maxX=minX,minY=start/w,maxY=minY,count=0;
            while(head<tail) {
                int p=queue[head++],x=p%w,y=p/w;count++;
                if(x<minX)minX=x;if(x>maxX)maxX=x;if(y<minY)minY=y;if(y>maxY)maxY=y;
                if(x>0 && dark[p-1] && !seen[p-1]) {seen[p-1]=true;queue[tail++]=p-1;}
                if(x<w-1 && dark[p+1] && !seen[p+1]) {seen[p+1]=true;queue[tail++]=p+1;}
                if(y>0 && dark[p-w] && !seen[p-w]) {seen[p-w]=true;queue[tail++]=p-w;}
                if(y<h-1 && dark[p+w] && !seen[p+w]) {seen[p+w]=true;queue[tail++]=p+w;}
            }
            int cw=maxX-minX+1,ch=maxY-minY+1;
            int large=Math.max(cw,ch),small=Math.min(cw,ch);
            if(small<7 || large<55 || large>w*0.87f || large>h*0.95f) continue;
            float aspect=(float)large/small,fill=count/(float)(cw*ch);
            if(aspect<4.3f || aspect>5.7f || fill<0.83f) continue;
            double quality=count*fill*(1.0-Math.abs(5.0-aspect)/5.0);
            if(quality>best) {
                best=quality;
                float pxPerMm=large/(reduction*50f);
                candidate=new Result(pxPerMm, fill, new RectF(minX/reduction,minY/reduction,(maxX+1)/reduction,(maxY+1)/reduction));
            }
        }
        if(bmp!=input)bmp.recycle();
        return candidate;
    }
}
