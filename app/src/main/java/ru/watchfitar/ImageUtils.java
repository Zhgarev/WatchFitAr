package ru.watchfitar;

import android.content.Context;
import android.graphics.*;
import android.net.Uri;
import androidx.exifinterface.media.ExifInterface;
import java.io.InputStream;
import java.util.ArrayDeque;

public final class ImageUtils {
    private ImageUtils() { }
    public static Bitmap load(Context context, Uri uri, int maxDimension) throws Exception {
        BitmapFactory.Options opts = new BitmapFactory.Options(); opts.inJustDecodeBounds = true;
        try (InputStream in = context.getContentResolver().openInputStream(uri)) { BitmapFactory.decodeStream(in, null, opts); }
        int sample = 1;
        while (Math.max(opts.outWidth, opts.outHeight) / sample > maxDimension) sample *= 2;
        opts.inJustDecodeBounds=false; opts.inSampleSize=sample; opts.inPreferredConfig=Bitmap.Config.ARGB_8888;
        Bitmap b;
        try (InputStream in = context.getContentResolver().openInputStream(uri)) { b = BitmapFactory.decodeStream(in, null, opts); }
        if (b == null) throw new IllegalArgumentException("Невозможно прочитать изображение");
        int rotation=0;
        try (InputStream in = context.getContentResolver().openInputStream(uri)) {
            ExifInterface exif = new ExifInterface(in);
            rotation = exif.getRotationDegrees();
            // Photos from typical galleries use EXIF rotation; mirrored files are outside MVP scope.
        } catch (Exception ignored) { }
        if (rotation != 0) {
            Matrix m = new Matrix(); m.postRotate(rotation);
            Bitmap rotated = Bitmap.createBitmap(b,0,0,b.getWidth(),b.getHeight(),m,true);
            if (rotated != b) b.recycle();
            b=rotated;
        }
        int max = Math.max(b.getWidth(), b.getHeight());
        if (max > maxDimension) {
            float s = (float) maxDimension/max;
            Bitmap resized=Bitmap.createScaledBitmap(b,Math.round(b.getWidth()*s),Math.round(b.getHeight()*s),true);
            if (resized!=b) b.recycle(); b=resized;
        }
        return b;
    }
    public static Bitmap removeWhiteConnectedBackground(Bitmap src) {
        int w = src.getWidth(), h=src.getHeight(), n=w*h;
        int[] px=new int[n]; src.getPixels(px,0,w,0,0,w,h);
        byte[] seen = new byte[n]; int[] queue = new int[n]; int head=0,tail=0;
        // Flood only nearly white pixels reachable from outside. The watch face stays intact.
        for(int x=0; x<w; x++) { tail=push(px,seen,queue,tail,x);tail=push(px,seen,queue,tail,(h-1)*w+x); }
        for(int y=0; y<h; y++) { tail=push(px,seen,queue,tail,y*w);tail=push(px,seen,queue,tail,y*w+w-1); }
        while(head<tail) {
            int pos=queue[head++],x=pos%w;
            if(x>0) tail=push(px,seen,queue,tail,pos-1);
            if(x<w-1) tail=push(px,seen,queue,tail,pos+1);
            if(pos>=w) tail=push(px,seen,queue,tail,pos-w);
            if(pos<n-w) tail=push(px,seen,queue,tail,pos+w);
        }
        for(int i=0;i<n;i++) if(seen[i] == 1) px[i]=Color.TRANSPARENT;
        Bitmap b=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);
        b.setPixels(px,0,w,0,0,w,h); return b;
    }
    private static int push(int[] px,byte[] seen,int[] queue,int tail,int i) {
        if(seen[i]!=0) return tail;
        int c=px[i]; int a=Color.alpha(c),r=Color.red(c),g=Color.green(c),b=Color.blue(c);
        if(a<10 || (r>230 && g>230 && b>230 && Math.max(r,Math.max(g,b))-Math.min(r,Math.min(g,b))<25)) {
            seen[i]=1; queue[tail++]=i;
        } else seen[i]=2;
        return tail;
    }
}
