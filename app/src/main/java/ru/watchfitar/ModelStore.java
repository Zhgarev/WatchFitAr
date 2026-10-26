package ru.watchfitar;

import android.content.Context;
import android.graphics.*;
import org.json.JSONArray;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class ModelStore {
    private final Context context;
    public final List<WatchModel> watches = new ArrayList<>();
    public ModelStore(Context context) {
        this.context = context.getApplicationContext();
        load();
        if (watches.isEmpty()) {
            watches.add(demoWatch());
            persist();
        }
    }
    public void add(WatchModel w) throws Exception {
        w.id = UUID.randomUUID().toString();
        w.saveImage(context);
        watches.add(w);
        persist();
    }
    public void delete(WatchModel w) {
        watches.remove(w);
        new File(context.getFilesDir(), "watch_"+w.id+".png").delete();
        if (watches.isEmpty()) watches.add(demoWatch());
        persist();
    }
    private void load() {
        File f = new File(context.getFilesDir(), "watches.json");
        if (!f.isFile()) return;
        try {
            String s = new String(java.nio.file.Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
            JSONArray a = new JSONArray(s);
            for (int i=0; i<a.length(); ++i) {
                WatchModel w = WatchModel.fromJson(a.getJSONObject(i), context);
                if (w.image != null && w.casePixels() > 1 && w.diameterMm > 0) watches.add(w);
            }
        } catch (Exception ignored) { }
    }
    private void persist() {
        try {
            JSONArray a = new JSONArray();
            for (WatchModel w : watches) a.put(w.toJson());
            try (FileOutputStream out = context.openFileOutput("watches.json", Context.MODE_PRIVATE)) {
                out.write(a.toString(2).getBytes(StandardCharsets.UTF_8));
            }
        } catch (Exception e) { android.util.Log.e("WatchFit", "Save catalog", e); }
    }
    private WatchModel demoWatch() {
        WatchModel w = new WatchModel();
        w.id = "demo"; w.brand = "Демо"; w.name = "40 мм (не бренд)"; w.diameterMm = 40;
        w.lugMm = 48; w.x1 = 100; w.y1 = 440; w.x2 = 420; w.y2 = 440;
        Bitmap b = Bitmap.createBitmap(520, 900, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b); Paint p = new Paint(3);
        p.setColor(Color.rgb(44,52,56)); c.drawRoundRect(188,25,332,875,24,24,p);
        p.setColor(Color.rgb(150,154,153)); c.drawRoundRect(184,95,336,810,20,20,p);
        p.setColor(Color.rgb(30,37,40)); c.drawRoundRect(197,120,323,780,10,10,p);
        p.setColor(Color.rgb(218,222,222)); c.drawCircle(260,440,160,p);
        p.setColor(Color.rgb(22,75,69)); c.drawCircle(260,440,145,p);
        p.setColor(Color.WHITE); for (int i=0; i<12; i++) { double a=i*Math.PI/6; float xx=260+(float)Math.sin(a)*120, yy=440-(float)Math.cos(a)*120; c.drawCircle(xx,yy,4,p); }
        p.setStrokeWidth(7); p.setStrokeCap(Paint.Cap.ROUND);
        c.drawLine(260,440,260,350,p); c.drawLine(260,440,322,465,p);
        w.image=b;
        try { w.saveImage(context); } catch (Exception ignored) { }
        return w;
    }
}
