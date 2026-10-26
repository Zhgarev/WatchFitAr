package ru.watchfitar;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.content.Context;
import org.json.JSONObject;
import java.io.File;
import java.io.FileOutputStream;

public class WatchModel {
    public String id, brand, name;
    public float diameterMm, lugMm, x1, y1, x2, y2;
    public Bitmap image;

    public String label() { return brand + " " + name + " · " + trim(diameterMm) + " мм"; }
    private static String trim(float v) { return (v == (int) v) ? Integer.toString((int) v) : Float.toString(v); }
    public float casePixels() { return (float) Math.hypot(x2-x1, y2-y1); }
    public float caseCx() { return (x1 + x2) / 2f; }
    public float caseCy() { return (y1 + y2) / 2f; }

    public JSONObject toJson() throws Exception {
        JSONObject j = new JSONObject();
        j.put("id", id); j.put("brand", brand); j.put("name", name);
        j.put("diameter", (double) diameterMm); j.put("lug", (double) lugMm);
        j.put("x1", (double) x1); j.put("y1", (double) y1);
        j.put("x2", (double) x2); j.put("y2", (double) y2);
        return j;
    }
    public static WatchModel fromJson(JSONObject j, Context ctx) {
        WatchModel m = new WatchModel();
        m.id = j.optString("id"); m.brand = j.optString("brand"); m.name = j.optString("name");
        m.diameterMm = (float) j.optDouble("diameter", 40);
        m.lugMm = (float) j.optDouble("lug", 0);
        m.x1 = (float) j.optDouble("x1"); m.x2 = (float) j.optDouble("x2");
        m.y1 = (float) j.optDouble("y1"); m.y2 = (float) j.optDouble("y2");
        m.image = BitmapFactory.decodeFile(new File(ctx.getFilesDir(), "watch_"+m.id+".png").getPath());
        return m;
    }
    public void saveImage(Context context) throws Exception {
        try (FileOutputStream out = new FileOutputStream(new File(context.getFilesDir(), "watch_" + id + ".png"))) {
            if (!image.compress(Bitmap.CompressFormat.PNG, 100, out)) throw new IllegalStateException("PNG encode failed");
        }
    }
}
