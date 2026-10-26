package ru.watchfitar;

import android.Manifest;
import android.app.AlertDialog;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.*;
import android.net.Uri;
import android.os.Bundle;
import android.os.Build;
import android.provider.MediaStore;
import android.content.SharedPreferences;
import android.view.*;
import android.widget.*;
import androidx.activity.ComponentActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;
import com.google.common.util.concurrent.ListenableFuture;
import java.io.*;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** WatchFit AR MVP: inspect a wrist, freeze the image, calibrate and place a real-sized watch. */
public class MainActivity extends ComponentActivity {
    private static final int REQUEST_CAMERA=101,REQUEST_PHOTO=102;
    private static final int BG=0xFF0D1420, PANEL=0xFF172132, GREEN=0xFF8EE8CC;
    private FrameLayout photoArea;
    private PreviewView preview;
    private ImageView freezeImage;
    private WatchOverlayView overlay;
    private TextView status, selectedLabel;
    private Button freezeButton;
    private ImageCapture imageCapture;
    private Bitmap frozen;
    private float imagePixelsPerMm=0f;
    private boolean isFrozen=false;
    private ModelStore store;
    private WatchModel selected;
    private SharedPreferences prefs;
    private SeekBar rotation;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(BG);getWindow().setNavigationBarColor(BG);
        prefs=getSharedPreferences("watchfit",MODE_PRIVATE);
        store=new ModelStore(this);
        selected=store.watches.get(0);
        for(WatchModel w: store.watches) if(w.id.equals(prefs.getString("selected",""))) selected=w;
        createUi();
        if(ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)==PackageManager.PERMISSION_GRANTED) startCamera();
        else requestPermissions(new String[]{Manifest.permission.CAMERA},REQUEST_CAMERA);
        restoreLastFrame();
    }
    private void createUi() {
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(BG);
        setContentView(root);
        TextView title=new TextView(this);title.setText("WATCHFIT  /  AR");title.setTextColor(GREEN);title.setTextSize(19);
        title.setTypeface(android.graphics.Typeface.DEFAULT,android.graphics.Typeface.BOLD);
        title.setPadding(dp(16),dp(13),dp(16),dp(3));root.addView(title);
        selectedLabel=new TextView(this);selectedLabel.setTextColor(Color.WHITE);selectedLabel.setTextSize(13);
        selectedLabel.setPadding(dp(16),0,dp(16),dp(10));root.addView(selectedLabel);
        photoArea=new FrameLayout(this);root.addView(photoArea,new LinearLayout.LayoutParams(-1,0,1f));
        preview=new PreviewView(this);preview.setImplementationMode(PreviewView.ImplementationMode.COMPATIBLE);
        preview.setScaleType(PreviewView.ScaleType.FILL_CENTER);
        photoArea.addView(preview,new FrameLayout.LayoutParams(-1,-1));
        freezeImage=new ImageView(this);freezeImage.setBackgroundColor(0xFF060B14);
        freezeImage.setScaleType(ImageView.ScaleType.FIT_CENTER);freezeImage.setVisibility(View.GONE);
        photoArea.addView(freezeImage,new FrameLayout.LayoutParams(-1,-1));
        overlay=new WatchOverlayView(this);
        photoArea.addView(overlay,new FrameLayout.LayoutParams(-1,-1));
        status=new TextView(this);status.setTextColor(0xFFDAE7E7);status.setTextSize(12);
        status.setBackgroundColor(PANEL);status.setPadding(dp(15),dp(8),dp(15),dp(8));
        root.addView(status,new LinearLayout.LayoutParams(-1,-2));
        HorizontalScrollView row=new HorizontalScrollView(this);row.setHorizontalScrollBarEnabled(false);
        LinearLayout buttons=new LinearLayout(this);buttons.setPadding(dp(8),dp(7),dp(8),dp(4));
        row.addView(buttons);root.addView(row);
        freezeButton=button(buttons,"📷 Кадр",()->onFreezePressed());
        button(buttons,"⌚ Часы",()->chooseWatch());
        button(buttons,"＋ Фото часов",()->selectWatchPhoto());
        button(buttons,"📏 Линейка",()->manualCalibrationDialog());
        button(buttons,"▰ Авто 50 мм",()->automaticMarker());
        button(buttons,"↓ Сохранить",()->saveComposite());
        LinearLayout rotateLine=new LinearLayout(this);rotateLine.setGravity(Gravity.CENTER_VERTICAL);
        rotateLine.setPadding(dp(15),0,dp(15),dp(8));root.addView(rotateLine);
        TextView text=new TextView(this);text.setText("Поворот");text.setTextColor(Color.WHITE);text.setTextSize(13);
        rotateLine.addView(text);
        rotation=new SeekBar(this);rotation.setMax(180);rotation.setProgress(90);
        rotateLine.addView(rotation,new LinearLayout.LayoutParams(0,dp(44),1f));
        rotation.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar s,int progress,boolean fromUser) {
                overlay.setRotation(progress-90);
                if(fromUser) storeLastState();
            }
            @Override public void onStartTrackingTouch(SeekBar s) { }
            @Override public void onStopTrackingTouch(SeekBar s) { }
        });
        updateView();
    }
    private Button button(LinearLayout parent,String label,Runnable action) {
        Button b=new Button(this);b.setText(label);b.setTextSize(12);b.setAllCaps(false);
        b.setTextColor(Color.WHITE);b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(PANEL));
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-2,dp(52));lp.setMargins(dp(4),0,dp(4),0);
        parent.addView(b,lp);b.setOnClickListener(v->action.run());return b;
    }
    private int dp(float value) {return Math.round(value*getResources().getDisplayMetrics().density);}
    private void message(String text) {Toast.makeText(this,text,Toast.LENGTH_LONG).show();}
    @Override public void onRequestPermissionsResult(int requestCode,String[] permissions,int[] results) {
        super.onRequestPermissionsResult(requestCode,permissions,results);
        if(requestCode==REQUEST_CAMERA) {
            if(results.length>0 && results[0]==PackageManager.PERMISSION_GRANTED)startCamera();
            else new AlertDialog.Builder(this).setMessage("Без доступа к камере можно загрузить часы, но нельзя снять запястье.")
                .setPositiveButton("Понятно",null).show();
        }
    }
    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> future=ProcessCameraProvider.getInstance(this);
        future.addListener(()->{
            try {
                ProcessCameraProvider provider=future.get();provider.unbindAll();
                Preview live=new Preview.Builder().build();live.setSurfaceProvider(preview.getSurfaceProvider());
                imageCapture=new ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY).build();
                provider.bindToLifecycle(this,CameraSelector.DEFAULT_BACK_CAMERA,live,imageCapture);
            } catch(Exception e) {message("Камера не запустилась: "+e.getMessage());}
        },ContextCompat.getMainExecutor(this));
    }
    private void onFreezePressed() {
        if(isFrozen) {
            overlay.cancelCalibration();isFrozen=false;frozen=null;freezeImage.setImageDrawable(null);
            imagePixelsPerMm=0;rotation.setProgress(90);overlay.resetPosition();updateView();
            return;
        }
        if(imageCapture==null) {message("Камера ещё не готова");return;}
        File f=new File(getCacheDir(),"capture.jpg");
        imageCapture.takePicture(new ImageCapture.OutputFileOptions.Builder(f).build(),
          ContextCompat.getMainExecutor(this),new ImageCapture.OnImageSavedCallback() {
            @Override public void onImageSaved(ImageCapture.OutputFileResults output) {
                try {
                    Bitmap bitmap=ImageUtils.load(MainActivity.this,Uri.fromFile(f),2400);
                    openFrozenFrame(bitmap,0,false);
                    saveLastFrame();
                } catch(Exception e) {message("Не удалось открыть кадр: "+e.getMessage());}
            }
            @Override public void onError(ImageCaptureException error) {message("Ошибка снимка: "+error.getMessage());}
          });
    }
    private void openFrozenFrame(Bitmap bitmap,float calibratedPixelsPerMm,boolean restore) {
        frozen=bitmap;isFrozen=true;imagePixelsPerMm=calibratedPixelsPerMm;overlay.clearHighlight();
        freezeImage.setImageBitmap(frozen);
        if(!restore) {overlay.resetPosition();rotation.setProgress(90);}
        updateView();
        photoArea.post(()->updateOverlayScale());
    }
    private float imageToViewScale() {
        if(frozen==null || photoArea.getWidth()<=0 || photoArea.getHeight()<=0)return 1f;
        return Math.min(photoArea.getWidth()/(float)frozen.getWidth(),photoArea.getHeight()/(float)frozen.getHeight());
    }
    private void updateOverlayScale() {
        if(isFrozen && imagePixelsPerMm>0) overlay.setViewScale(imagePixelsPerMm*imageToViewScale(),true);
        else overlay.setViewScale(0,false);
    }
    private void updateView() {
        if(overlay==null)return;
        freezeImage.setVisibility(isFrozen?View.VISIBLE:View.GONE);
        freezeButton.setText(isFrozen?"↻ Новый кадр":"📷 Кадр");
        overlay.setWatch(selected);updateOverlayScale();
        selectedLabel.setText(selected==null?"Выбери часы":selected.label());
        String s=isFrozen?"СТОП-КАДР · нажми на запястье, чтобы поставить часы":"КАМЕРА · предпросмотр, масштаб приблизительный";
        if(isFrozen) s+=imagePixelsPerMm>0?" · масштаб откалиброван":" · сначала откалибруй по линейке";
        status.setText(s);
    }
    private void chooseWatch() {
        String[] names=new String[store.watches.size()];for(int i=0;i<names.length;i++)names[i]=store.watches.get(i).label();
        new AlertDialog.Builder(this).setTitle("Модели часов")
            .setSingleChoiceItems(names,store.watches.indexOf(selected),(d,which)->{
                selected=store.watches.get(which);prefs.edit().putString("selected",selected.id).apply();
                d.dismiss();updateView();
            })
            .setPositiveButton("Добавить",(d,w)->selectWatchPhoto())
            .setNeutralButton("Удалить модель",(d,w)->confirmDelete())
            .setNegativeButton("Закрыть",null).show();
    }
    private void confirmDelete() {
        if("demo".equals(selected.id)) {message("Демонстрационную модель удалить нельзя");return;}
        WatchModel delete=selected;
        new AlertDialog.Builder(this).setTitle("Удалить "+delete.label()+"?")
            .setPositiveButton("Удалить",(d,w)->{store.delete(delete);selected=store.watches.get(0);updateView();})
            .setNegativeButton("Отмена",null).show();
    }
    private void selectWatchPhoto() {
        Intent pick=new Intent(Intent.ACTION_OPEN_DOCUMENT);
        pick.setType("image/*");pick.addCategory(Intent.CATEGORY_OPENABLE);
        startActivityForResult(pick,REQUEST_PHOTO);
    }
    @Override protected void onActivityResult(int requestCode,int resultCode,Intent data) {
        super.onActivityResult(requestCode,resultCode,data);
        if(requestCode==REQUEST_PHOTO && resultCode==RESULT_OK && data!=null && data.getData()!=null) {
            try { showImportDialog(ImageUtils.load(this,data.getData(),1500)); }
            catch(Exception e) {message("Не удалось загрузить фотографию: "+e.getMessage());}
        }
    }
    private EditText field(String hint,String value,int type) {
        EditText f=new EditText(this);f.setSingleLine(true);f.setHint(hint);f.setText(value);f.setTextColor(Color.WHITE);
        f.setHintTextColor(0xFFB8C2CD);f.setTextSize(16);f.setInputType(type);return f;
    }
    private void showImportDialog(Bitmap image) {
        LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(14),dp(4),dp(14),dp(4));
        TextView instruction=new TextView(this);
        instruction.setText("Нажми два края корпуса по горизонтали (не кончики браслета!). Если промахнулся, нажми заново.");
        instruction.setTextColor(Color.WHITE);instruction.setTextSize(13);box.addView(instruction);
        PointPickerView picker=new PointPickerView(this,image);
        box.addView(picker,new LinearLayout.LayoutParams(-1,dp(275)));
        EditText brand=field("Бренд","",android.text.InputType.TYPE_CLASS_TEXT);
        EditText model=field("Модель и референс","",android.text.InputType.TYPE_CLASS_TEXT);
        EditText diameter=field("Диаметр корпуса, мм (например, 41.5)","40",android.text.InputType.TYPE_CLASS_NUMBER|android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
        EditText lug=field("Длина корпуса (lug-to-lug), мм, необязательно","",android.text.InputType.TYPE_CLASS_NUMBER|android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
        box.addView(brand);box.addView(model);box.addView(diameter);box.addView(lug);
        CheckBox remove=new CheckBox(this);remove.setText("Убрать белый фон вокруг часов");remove.setTextColor(Color.WHITE);remove.setChecked(true);
        box.addView(remove);
        ScrollView scroller=new ScrollView(this);scroller.addView(box);
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("Добавить модель часов")
            .setView(scroller).setPositiveButton("Сохранить",null).setNegativeButton("Отмена",(d,w)->image.recycle()).create();
        dialog.setOnShowListener(d->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
            try {
                if(!picker.valid())throw new IllegalArgumentException("Укажи две точки по диаметру корпуса");
                float mm=Float.parseFloat(diameter.getText().toString().trim().replace(',','.'));
                if(mm<15 || mm>65)throw new IllegalArgumentException("Диаметр должен быть от 15 до 65 мм");
                float lugMm=0;String ll=lug.getText().toString().trim().replace(',','.');if(!ll.isEmpty())lugMm=Float.parseFloat(ll);
                WatchModel w=new WatchModel();
                w.brand=brand.getText().toString().trim();w.name=model.getText().toString().trim();
                if(w.brand.isEmpty() || w.name.isEmpty())throw new IllegalArgumentException("Введи бренд и модель");
                w.diameterMm=mm;w.lugMm=lugMm;
                w.x1=picker.points[0].x;w.y1=picker.points[0].y;
                w.x2=picker.points[1].x;w.y2=picker.points[1].y;
                w.image=remove.isChecked()?ImageUtils.removeWhiteConnectedBackground(image):image;
                store.add(w);selected=w; prefs.edit().putString("selected",w.id).apply();
                dialog.dismiss();updateView();message("Модель сохранена");
            } catch(Exception ex) {message(ex.getMessage());}
        }));
        dialog.show();
    }
    private void manualCalibrationDialog() {
        if(!isFrozen || frozen==null) {message("Сначала нажми «Кадр» и сфотографируй руку с линейкой");return;}
        EditText mm=field("Расстояние между отметками, мм","50",android.text.InputType.TYPE_CLASS_NUMBER|android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
        new AlertDialog.Builder(this).setTitle("Калибровка по линейке")
            .setMessage("Линейка должна лежать рядом с запястьем в той же плоскости. Введи расстояние между двумя хорошо видимыми метками, затем нажми на обе метки на кадре.")
            .setView(mm).setPositiveButton("Выбрать две точки",(dialog,id)->{
                try {
                    float span=Float.parseFloat(mm.getText().toString().trim().replace(',','.'));
                    if(span<5 || span>250)throw new IllegalArgumentException("Укажи 5–250 мм");
                    overlay.beginCalibration(dist->{
                        imagePixelsPerMm=dist/imageToViewScale()/span;
                        updateView();storeLastState();message("Масштаб установлен по "+span+" мм");
                    });
                } catch(Exception e) {message(e.getMessage());}
            }).setNegativeButton("Отмена",null).show();
    }
    private void automaticMarker() {
        if(!isFrozen || frozen==null) {message("Сначала сделай стоп-кадр вместе с полосой 50 × 10 мм");return;}
        MarkerDetector.Result result=MarkerDetector.detect(frozen);
        if(result==null) {message("Не найдена чёрная полоса 50 × 10 мм. Используй линейку с двумя отметками.");return;}
        overlay.highlightReference(result.rect,frozen.getWidth(),frozen.getHeight());
        new AlertDialog.Builder(this).setTitle("Контрольная полоса найдена")
            .setMessage("Опознана тёмная полоса с пропорцией около 5:1. Расчёт: "+
                String.format(Locale.US,"%.2f пикс./мм",result.originalPixelsPerMm)+
                ". Проверь, что это именно напечатанная полоса 50 × 10 мм рядом с рукой. Иначе размер будет неверным.")
            .setPositiveButton("Применить",(dialog,which)->{
                imagePixelsPerMm=result.originalPixelsPerMm;overlay.clearHighlight();updateView();storeLastState();
            }).setNegativeButton("Отмена",(dialog,which)->overlay.clearHighlight())
            .setOnCancelListener(dialog->overlay.clearHighlight()).show();
    }
    private void saveComposite() {
        if(!isFrozen || frozen==null) {message("Для сохранения сначала сделай стоп-кадр");return;}
        if(imagePixelsPerMm<=0) {message("Сначала откалибруй масштаб");return;}
        try {
            Bitmap image=Bitmap.createBitmap(photoArea.getWidth(),photoArea.getHeight(),Bitmap.Config.ARGB_8888);
            Canvas canvas=new Canvas(image);photoArea.draw(canvas);
            String filename="WatchFit_"+new SimpleDateFormat("yyyyMMdd_HHmmss",Locale.US).format(new Date())+".png";
            ContentValues values=new ContentValues();values.put(MediaStore.Images.Media.DISPLAY_NAME,filename);
            values.put(MediaStore.Images.Media.MIME_TYPE,"image/png");
            values.put(MediaStore.Images.Media.RELATIVE_PATH,"Pictures/WatchFitAR");
            Uri uri=getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,values);
            if(uri==null)throw new IOException("MediaStore returned null");
            try (OutputStream output=getContentResolver().openOutputStream(uri)) {
                if(output==null || !image.compress(Bitmap.CompressFormat.PNG,100,output))throw new IOException("PNG write failed");
            }
            image.recycle();storeLastState();message("Сохранено: Галерея / Pictures / WatchFitAR");
        } catch(Exception e) {message("Ошибка сохранения: "+e.getMessage());}
    }
    private void saveLastFrame() {
        if(frozen==null)return;
        try (FileOutputStream out=new FileOutputStream(new File(getFilesDir(),"last_frame.jpg"))) {
            frozen.compress(Bitmap.CompressFormat.JPEG,94,out);
        } catch(Exception e) {android.util.Log.e("WatchFit","Frame save failed",e);}
        storeLastState();
    }
    private void storeLastState() {
        if(!isFrozen)return;
        prefs.edit().putFloat("imagePpm",imagePixelsPerMm).putString("selected",selected.id)
            .putFloat("posX",overlay.getPositionXRatio()).putFloat("posY",overlay.getPositionYRatio())
            .putInt("rotation",rotation.getProgress()).apply();
    }
    private void restoreLastFrame() {
        File last=new File(getFilesDir(),"last_frame.jpg");if(!last.exists())return;
        try {
            Bitmap bitmap=ImageUtils.load(this,Uri.fromFile(last),2400);
            openFrozenFrame(bitmap,prefs.getFloat("imagePpm",0),true);
            rotation.setProgress(prefs.getInt("rotation",90));
            photoArea.post(()->{overlay.placeAtRatios(prefs.getFloat("posX",0.5f),prefs.getFloat("posY",0.5f));updateOverlayScale();});
        } catch(Exception ignored) { }
    }
    @Override protected void onPause() {storeLastState();super.onPause();}
}
