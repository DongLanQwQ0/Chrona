package com.donglan.chrona;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Matrix;
import android.graphics.BitmapFactory;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.donglan.chrona.image.ImageStore;
import com.donglan.chrona.classroom.ClassroomLesson;
import com.donglan.chrona.classroom.ClassroomStore;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;

/** Full-screen attachment viewer with pinch zoom and Android's document save picker. */
public final class AttachmentViewerActivity extends Activity {
    static final String EXTRA_IMAGE_NAME = "image_name";
    static final int CLASSROOM_GALLERY_REQUEST=3214;
    static final String EXTRA_SELECTED_RAW="selected_raw";
    private static final String EXTRA_CLASSROOM_ACCOUNT="classroom_image_account",EXTRA_CLASSROOM_LESSON="classroom_image_lesson",EXTRA_CLASSROOM_RAW="classroom_image_raw",EXTRA_CLASSROOM_IMAGES="classroom_image_list";
    private static final int CREATE_IMAGE_DOCUMENT = 1;
    private final Matrix imageMatrix = new Matrix();
    private ImageView image;
    private ImageSlidePager pager;
    private final ArrayList<String> gallery=new ArrayList<>();
    private String imageName;
    private String classroomAccount,rawName,imageMime="image/jpeg",exportName,savingRaw;
    private ClassroomLesson classroomLesson;
    private float scale = 1f;
    private boolean matrixReady;
    private boolean immersiveLandscape;
    private ScaleGestureDetector scaleDetector;
    private GestureDetector gestureDetector;

    static void openClassroom(Activity activity,String account,ClassroomLesson lesson,String raw){
        openClassroom(activity,account,lesson,new ArrayList<>(java.util.Collections.singletonList(raw)),raw);
    }

    static void openClassroom(Activity activity,String account,ClassroomLesson lesson,ArrayList<String> images,String raw){
        if(!ClassroomMonitor.allowed(activity,account)||raw==null||!raw.matches("raw-[0-9]+\\.img"))return;
        try{activity.startActivityForResult(new Intent(activity,AttachmentViewerActivity.class).putExtra(EXTRA_CLASSROOM_ACCOUNT,account)
                .putExtra(EXTRA_CLASSROOM_LESSON,lesson.json().toString()).putExtra(EXTRA_CLASSROOM_RAW,raw).putStringArrayListExtra(EXTRA_CLASSROOM_IMAGES,images),CLASSROOM_GALLERY_REQUEST);}
        catch(Exception failure){Feedback.show(activity,"无法打开课堂图片");}
    }

    @Override protected void onCreate(Bundle state) {
        ThemeStore.apply(this);
        super.onCreate(state);
        File source;
        try{
            if(getIntent().hasExtra(EXTRA_CLASSROOM_ACCOUNT)||getIntent().hasExtra(EXTRA_CLASSROOM_LESSON)||getIntent().hasExtra(EXTRA_CLASSROOM_RAW)||getIntent().hasExtra(EXTRA_CLASSROOM_IMAGES)){
                classroomAccount=getIntent().getStringExtra(EXTRA_CLASSROOM_ACCOUNT);rawName=getIntent().getStringExtra(EXTRA_CLASSROOM_RAW);
                classroomLesson=ClassroomLesson.fromJson(new JSONObject(getIntent().getStringExtra(EXTRA_CLASSROOM_LESSON)));
                ArrayList<String> supplied=getIntent().getStringArrayListExtra(EXTRA_CLASSROOM_IMAGES);
                if(supplied==null)throw new java.io.IOException("课堂图片清单无效");
                for(String name:supplied)if(name!=null&&name.matches("raw-[0-9]+\\.img")&&!gallery.contains(name))gallery.add(name);
                if(!gallery.contains(rawName))throw new java.io.IOException("课堂图片无效");
                if(state!=null&&gallery.contains(state.getString("selected_raw")))rawName=state.getString("selected_raw");
                source=sourceFile();updateExportFormat();
            }else{
                imageName=getIntent().getStringExtra(EXTRA_IMAGE_NAME);if(!ImageStore.isStoredName(imageName))throw new java.io.IOException("图片附件无效");
                source=sourceFile();exportName="Chrona-"+imageName;
            }
        }catch(Exception failure){finish();return;}
        if(state!=null)savingRaw=state.getString("saving_raw");
        FrameLayout stage = new FrameLayout(this);
        stage.addView(new GlassBackdropView(this),new FrameLayout.LayoutParams(-1,-1));
        FrameLayout safeArea=new FrameLayout(this);stage.addView(safeArea,new FrameLayout.LayoutParams(-1,-1));
        immersiveLandscape=getResources().getConfiguration().orientation==android.content.res.Configuration.ORIENTATION_LANDSCAPE;
        if(immersiveLandscape&&android.os.Build.VERSION.SDK_INT>=28){
            var attributes=getWindow().getAttributes();
            attributes.layoutInDisplayCutoutMode=android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
            getWindow().setAttributes(attributes);
        }
        pager=new ImageSlidePager(this,false);
        image = pager.image();
        image.setScaleType(ImageView.ScaleType.MATRIX);
        image.setContentDescription(classroomLesson==null?"图片附件，双指缩放或双击放大，长按拖放":"课堂PPT，双指缩放或双击放大，长按拖放");
        if(immersiveLandscape)stage.addView(pager,1,new FrameLayout.LayoutParams(-1,-1));
        else safeArea.addView(pager, new FrameLayout.LayoutParams(-1, -1));
        ImageButton save=UiStyle.navigationIcon(this,R.drawable.ic_week_export,"保存图片",this::saveImage);
        View shell;
        if(immersiveLandscape){
            FrameLayout overlay=new FrameLayout(this);overlay.setClipChildren(false);overlay.setClipToPadding(false);
            ImageButton back=UiStyle.navigationIcon(this,R.drawable.ic_arrow_left,"返回",this::finish);
            for(ImageButton control:new ImageButton[]{back,save}){
                FrameLayout.LayoutParams slot=new FrameLayout.LayoutParams(dp(48),dp(48),Gravity.TOP|(control==back?Gravity.START:Gravity.END));
                slot.setMargins(dp(20),dp(16),dp(20),0);overlay.addView(control,slot);InteractionLayers.pin(control);
            }
            shell=overlay;
        }else{
            LinearLayout content=SettingsPageLayout.content(this);
            SettingsPageLayout.header(this,content,classroomLesson==null?"图片附件":"课堂PPT",save);shell=content;
        }
        safeArea.addView(shell,new FrameLayout.LayoutParams(-1,-1));
        UiStyle.applyInsets(stage, safeArea);
        setContentView(stage);
        updateSystemBars();

        scaleDetector = new ScaleGestureDetector(this,
                new ScaleGestureDetector.SimpleOnScaleGestureListener() {
                    @Override public boolean onScale(ScaleGestureDetector detector) {
                        if (!matrixReady) return false;
                        float next = Math.max(1f, Math.min(6f, scale * detector.getScaleFactor()));
                        applyScale(next, detector.getFocusX(), detector.getFocusY());
                        return true;
                    }
                });
        gestureDetector = new GestureDetector(this,
                new GestureDetector.SimpleOnGestureListener() {
                    @Override public boolean onDown(MotionEvent event) { return true; }
                    @Override public boolean onSingleTapConfirmed(MotionEvent event) {
                        if (!insideImage(event.getX(),event.getY())) finish();
                        return true;
                    }
                    @Override public boolean onDoubleTap(MotionEvent event) {
                        if (!matrixReady) return false;
                        float next = scale > 1.05f ? 1f : 2.5f;
                        applyScale(next, event.getX(), event.getY());
                        return true;
                    }
                    @Override public void onLongPress(MotionEvent event){if(insideImage(event.getX(),event.getY()))image.performLongClick();}
                });
        image.setOnTouchListener((view, event) -> onImageTouch(event));
        image.getViewTreeObserver().addOnGlobalLayoutListener(this::fitImage);
        pager.setPagingAllowed(()->!scaleDetector.isInProgress());
        pager.setPanHandler(this::panImage);
        pager.setDragAction(()->{try{sourceFile();return ImageDrag.start(this,image,classroomLesson==null?ImageTransferProvider.attachment(this,imageName):ImageTransferProvider.classroom(this,classroomAccount,classroomLesson,rawName));}catch(Exception unavailable){Feedback.show(this,"图片已不可用");return false;}});
        pager.setListener((selected,current)->{
            if(classroomLesson!=null){rawName=gallery.get(selected);setResult(RESULT_OK,new Intent().putExtra(EXTRA_SELECTED_RAW,rawName));}
            scale=1f;matrixReady=false;fitImage();
            image.setContentDescription(classroomLesson==null?"图片附件，双指缩放或双击放大，长按拖放":"课堂PPT "+(selected+1)+" / "+gallery.size()+"，双指缩放或双击放大，长按拖放");
        });
        ArrayList<String> items=new ArrayList<>();if(classroomLesson==null)items.add(imageName);else items.addAll(gallery);
        pager.setImages(items,classroomLesson==null?0:gallery.indexOf(rawName),new ImageSlidePager.Source(){
            public File file(String name)throws Exception{return classroomLesson==null?sourceFile():classroomSource(name);}
            public boolean allowed(){return classroomLesson==null||ClassroomMonitor.allowed(AttachmentViewerActivity.this,classroomAccount);}
        });
        if(state==null){shell.post(()->UiMotion.enterPage(shell));pager.post(()->UiMotion.enterPage(pager));}
    }

    @Override public void onWindowFocusChanged(boolean focused){super.onWindowFocusChanged(focused);if(focused)updateSystemBars();}
    private void updateSystemBars(){
        if(android.os.Build.VERSION.SDK_INT>=30){
            var controller=getWindow().getInsetsController();if(controller==null)return;
            if(immersiveLandscape){controller.setSystemBarsBehavior(android.view.WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);controller.hide(android.view.WindowInsets.Type.statusBars());}
            else controller.show(android.view.WindowInsets.Type.statusBars());
        }else{
            int flags=getWindow().getDecorView().getSystemUiVisibility();
            getWindow().getDecorView().setSystemUiVisibility(immersiveLandscape?flags|View.SYSTEM_UI_FLAG_FULLSCREEN:flags&~View.SYSTEM_UI_FLAG_FULLSCREEN);
        }
    }

    private void fitImage(){
        if(image.getWidth()>0&&image.getHeight()>0)image.setClipBounds(new android.graphics.Rect(0,0,image.getWidth(),image.getHeight()));
        if(matrixReady||image.getWidth()<=0||image.getHeight()<=0)return;
        Drawable drawable=image.getDrawable();if(drawable==null||drawable.getIntrinsicWidth()<=0||drawable.getIntrinsicHeight()<=0)return;
        float fit=Math.min(image.getWidth()/(float)drawable.getIntrinsicWidth(),image.getHeight()/(float)drawable.getIntrinsicHeight());
        imageMatrix.setScale(fit,fit);imageMatrix.postTranslate((image.getWidth()-drawable.getIntrinsicWidth()*fit)/2f,(image.getHeight()-drawable.getIntrinsicHeight()*fit)/2f);
        image.setImageMatrix(imageMatrix);matrixReady=true;
    }

    private boolean insideImage(float x,float y){
        Drawable drawable=image.getDrawable();if(!matrixReady||drawable==null)return false;
        RectF bounds=new RectF(0,0,drawable.getIntrinsicWidth(),drawable.getIntrinsicHeight());imageMatrix.mapRect(bounds);
        return bounds.contains(x,y);
    }

    private void updateExportFormat()throws Exception{
        BitmapFactory.Options bounds=new BitmapFactory.Options();bounds.inJustDecodeBounds=true;BitmapFactory.decodeFile(sourceFile().getPath(),bounds);
        if(bounds.outWidth<=0||bounds.outHeight<=0||bounds.outMimeType==null)throw new java.io.IOException("课堂图片无法读取");
        imageMime=bounds.outMimeType;String extension=android.webkit.MimeTypeMap.getSingleton().getExtensionFromMimeType(imageMime);
        if(extension==null||!imageMime.startsWith("image/"))throw new java.io.IOException("课堂图片格式无效");
        exportName="课堂PPT-"+classroomLesson.date()+"-"+rawName.substring(0,rawName.length()-4)+"."+extension;
    }

    private boolean onImageTouch(MotionEvent event) {
        gestureDetector.onTouchEvent(event);
        scaleDetector.onTouchEvent(event);
        return true;
    }

    private RectF imageBounds(){
        Drawable drawable=image.getDrawable();if(!matrixReady||drawable==null)return new RectF();
        RectF bounds=new RectF(0,0,drawable.getIntrinsicWidth(),drawable.getIntrinsicHeight());imageMatrix.mapRect(bounds);return bounds;
    }
    private void constrainImage(){
        RectF bounds=imageBounds();if(bounds.isEmpty())return;
        // Keep a smaller image where the gesture put it, only correcting uncovered edges.
        float minX=Math.min(0,image.getWidth()-bounds.width()),maxX=Math.max(0,image.getWidth()-bounds.width());
        float minY=Math.min(0,image.getHeight()-bounds.height()),maxY=Math.max(0,image.getHeight()-bounds.height());
        imageMatrix.postTranslate(Math.max(minX,Math.min(maxX,bounds.left))-bounds.left,
                Math.max(minY,Math.min(maxY,bounds.top))-bounds.top);
    }
    private float panImage(float dx,float dy){
        if(!matrixReady||scale<=1.001f||scaleDetector.isInProgress())return dx;
        RectF before=imageBounds();imageMatrix.postTranslate(dx,dy);constrainImage();image.setImageMatrix(imageMatrix);
        return dx-(imageBounds().left-before.left);
    }
    private void applyScale(float next, float focusX, float focusY) {
        imageMatrix.postScale(next / scale, next / scale, focusX, focusY);
        scale = next;
        constrainImage();
        image.setImageMatrix(imageMatrix);
    }

    private void saveImage() {
        try{sourceFile();if(classroomLesson!=null)updateExportFormat();savingRaw=rawName;}catch(Exception failure){Feedback.show(this,"图片已不可用");return;}
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType(imageMime);
        intent.putExtra(Intent.EXTRA_TITLE, exportName);
        try{startActivityForResult(intent, CREATE_IMAGE_DOCUMENT);}catch(Exception failure){Feedback.show(this,"无法打开文件保存器");}
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != CREATE_IMAGE_DOCUMENT || resultCode != RESULT_OK || data == null
                || data.getData() == null) return;
        try (InputStream input = new FileInputStream(classroomLesson==null?sourceFile():classroomSource(savingRaw));
                OutputStream output = getContentResolver().openOutputStream(data.getData())) {
            if (output == null) throw new IllegalStateException("无法打开保存位置");
            byte[] buffer = new byte[8192];
            int count;
            while ((count = input.read(buffer)) >= 0) output.write(buffer, 0, count);
            Feedback.show(this, "图片已保存");
        } catch (Exception exception) {
            Feedback.showLong(this, "保存图片失败：" + exception.getMessage());
        }
    }

    private File sourceFile()throws Exception{
        File file;
        if(classroomLesson!=null){
            file=classroomSource(rawName);
        }else{if(!ImageStore.isStoredName(imageName))throw new SecurityException("图片附件无效");file=new ImageStore(this).fileFor(imageName);}
        if(!file.isFile())throw new java.io.IOException("图片已不存在");return file;
    }
    private File classroomSource(String name)throws Exception{
        if(!ClassroomMonitor.allowed(this,classroomAccount)||name==null||!name.matches("raw-[0-9]+\\.img")||!gallery.contains(name))throw new SecurityException("课堂账号或图片已不可用");
        File file=new File(new ClassroomStore(this,classroomAccount).localLesson(classroomLesson),name);
        if(!file.isFile())throw new java.io.IOException("图片已不存在");return file;
    }
    @Override protected void onResume(){super.onResume();if(classroomLesson!=null&&!ClassroomMonitor.allowed(this,classroomAccount))finish();}
    @Override protected void onPause(){if(pager!=null)pager.stop();super.onPause();}
    @Override protected void onSaveInstanceState(Bundle state){super.onSaveInstanceState(state);state.putString("selected_raw",rawName);state.putString("saving_raw",savingRaw);}
    @Override protected void onDestroy(){if(pager!=null)pager.close();super.onDestroy();}

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
}
