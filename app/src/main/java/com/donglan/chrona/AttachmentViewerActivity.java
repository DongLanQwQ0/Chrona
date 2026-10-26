package com.donglan.chrona;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Matrix;
import android.graphics.BitmapFactory;
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

/** Full-screen attachment viewer with pinch zoom and Android's document save picker. */
public final class AttachmentViewerActivity extends Activity {
    static final String EXTRA_IMAGE_NAME = "image_name";
    private static final String EXTRA_CLASSROOM_ACCOUNT="classroom_image_account",EXTRA_CLASSROOM_LESSON="classroom_image_lesson",EXTRA_CLASSROOM_RAW="classroom_image_raw";
    private static final int CREATE_IMAGE_DOCUMENT = 1;
    private final Matrix imageMatrix = new Matrix();
    private final Matrix fitMatrix = new Matrix();
    private ImageView image;
    private String imageName;
    private String classroomAccount,rawName,imageMime="image/jpeg",exportName;
    private ClassroomLesson classroomLesson;
    private float scale = 1f;
    private float lastX;
    private float lastY;
    private boolean matrixReady;
    private boolean dragReady;
    private ScaleGestureDetector scaleDetector;
    private GestureDetector gestureDetector;

    static void openClassroom(Activity activity,String account,ClassroomLesson lesson,String raw){
        if(!ClassroomMonitor.allowed(activity,account)||raw==null||!raw.matches("raw-[0-9]+\\.img"))return;
        try{activity.startActivity(new Intent(activity,AttachmentViewerActivity.class).putExtra(EXTRA_CLASSROOM_ACCOUNT,account)
                .putExtra(EXTRA_CLASSROOM_LESSON,lesson.json().toString()).putExtra(EXTRA_CLASSROOM_RAW,raw));}
        catch(Exception failure){Feedback.show(activity,"无法打开课堂图片");}
    }

    @Override protected void onCreate(Bundle state) {
        ThemeStore.apply(this);
        super.onCreate(state);
        File source;
        try{
            if(getIntent().hasExtra(EXTRA_CLASSROOM_ACCOUNT)||getIntent().hasExtra(EXTRA_CLASSROOM_LESSON)||getIntent().hasExtra(EXTRA_CLASSROOM_RAW)){
                classroomAccount=getIntent().getStringExtra(EXTRA_CLASSROOM_ACCOUNT);rawName=getIntent().getStringExtra(EXTRA_CLASSROOM_RAW);
                classroomLesson=ClassroomLesson.fromJson(new JSONObject(getIntent().getStringExtra(EXTRA_CLASSROOM_LESSON)));
                source=sourceFile();BitmapFactory.Options bounds=new BitmapFactory.Options();bounds.inJustDecodeBounds=true;BitmapFactory.decodeFile(source.getPath(),bounds);
                if(bounds.outWidth<=0||bounds.outHeight<=0||bounds.outMimeType==null)throw new java.io.IOException("课堂图片无法读取");
                imageMime=bounds.outMimeType;String extension=android.webkit.MimeTypeMap.getSingleton().getExtensionFromMimeType(imageMime);
                if(extension==null||!imageMime.startsWith("image/"))throw new java.io.IOException("课堂图片格式无效");
                exportName="课堂PPT-"+classroomLesson.date()+"-"+rawName.substring(0,rawName.length()-4)+"."+extension;
            }else{
                imageName=getIntent().getStringExtra(EXTRA_IMAGE_NAME);if(!ImageStore.isStoredName(imageName))throw new java.io.IOException("图片附件无效");
                source=sourceFile();exportName="Chrona-"+imageName;
            }
        }catch(Exception failure){finish();return;}
        FrameLayout stage = new FrameLayout(this);
        stage.addView(new GlassBackdropView(this),new FrameLayout.LayoutParams(-1,-1));
        FrameLayout safeArea=new FrameLayout(this);stage.addView(safeArea,new FrameLayout.LayoutParams(-1,-1));
        image = new ImageView(this);
        image.setScaleType(ImageView.ScaleType.MATRIX);
        image.setContentDescription(classroomLesson==null?"图片附件，双指缩放或双击放大":"课堂PPT，双指缩放或双击放大");
        if(classroomLesson==null)image.setImageURI(Uri.fromFile(source));
        else{BitmapFactory.Options options=new BitmapFactory.Options();options.inJustDecodeBounds=true;BitmapFactory.decodeFile(source.getPath(),options);options.inSampleSize=1;while(Math.max(options.outWidth,options.outHeight)/options.inSampleSize>2048)options.inSampleSize*=2;options.inJustDecodeBounds=false;image.setImageBitmap(BitmapFactory.decodeFile(source.getPath(),options));}
        safeArea.addView(image, new FrameLayout.LayoutParams(-1, -1));
        LinearLayout shell=SettingsPageLayout.content(this);
        ImageButton save=UiStyle.navigationIcon(this,R.drawable.ic_week_export,"保存图片",this::saveImage);
        SettingsPageLayout.header(this,shell,classroomLesson==null?"图片附件":"课堂PPT",save);
        safeArea.addView(shell,new FrameLayout.LayoutParams(-1,-1));
        UiStyle.applyInsets(stage, safeArea);
        setContentView(stage);

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
                    @Override public boolean onDoubleTap(MotionEvent event) {
                        if (!matrixReady) return false;
                        float next = scale > 1.05f ? 1f : 2.5f;
                        applyScale(next, event.getX(), event.getY());
                        return true;
                    }
                });
        image.setOnTouchListener((view, event) -> onImageTouch(event));
        image.getViewTreeObserver().addOnGlobalLayoutListener(() -> {
            if (matrixReady || image.getWidth() <= 0 || image.getHeight() <= 0) return;
            Drawable drawable = image.getDrawable();
            if (drawable == null || drawable.getIntrinsicWidth() <= 0
                    || drawable.getIntrinsicHeight() <= 0) return;
            float fit = Math.min(image.getWidth() / (float) drawable.getIntrinsicWidth(),
                    image.getHeight() / (float) drawable.getIntrinsicHeight());
            float left = (image.getWidth() - drawable.getIntrinsicWidth() * fit) / 2f;
            float top = (image.getHeight() - drawable.getIntrinsicHeight() * fit) / 2f;
            imageMatrix.setScale(fit, fit);
            imageMatrix.postTranslate(left, top);
            fitMatrix.set(imageMatrix);
            image.setImageMatrix(imageMatrix);
            matrixReady = true;
        });
    }

    private boolean onImageTouch(MotionEvent event) {
        gestureDetector.onTouchEvent(event);
        scaleDetector.onTouchEvent(event);
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_POINTER_UP) {
            int remaining = event.getActionIndex() == 0 ? 1 : 0;
            lastX = event.getX(remaining);
            lastY = event.getY(remaining);
            dragReady = true;
            return true;
        }
        if (action == MotionEvent.ACTION_POINTER_DOWN || action == MotionEvent.ACTION_UP
                || action == MotionEvent.ACTION_CANCEL) dragReady = false;
        if (event.getPointerCount() == 1 && scale > 1f && !scaleDetector.isInProgress()) {
            if (action == MotionEvent.ACTION_DOWN || !dragReady) {
                lastX = event.getX();
                lastY = event.getY();
                dragReady = true;
            } else if (action == MotionEvent.ACTION_MOVE) {
                float dx = event.getX() - lastX;
                float dy = event.getY() - lastY;
                imageMatrix.postTranslate(dx, dy);
                image.setImageMatrix(imageMatrix);
                lastX = event.getX();
                lastY = event.getY();
            }
        }
        return true;
    }

    private void applyScale(float next, float focusX, float focusY) {
        if (next <= 1f) imageMatrix.set(fitMatrix);
        else imageMatrix.postScale(next / scale, next / scale, focusX, focusY);
        scale = next;
        image.setImageMatrix(imageMatrix);
    }

    private void saveImage() {
        try{sourceFile();}catch(Exception failure){Feedback.show(this,"图片已不可用");return;}
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
        try (InputStream input = new FileInputStream(sourceFile());
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
            if(!ClassroomMonitor.allowed(this,classroomAccount)||rawName==null||!rawName.matches("raw-[0-9]+\\.img"))throw new SecurityException("课堂账号已不可用");
            file=new File(new ClassroomStore(this,classroomAccount).localLesson(classroomLesson),rawName);
        }else{if(!ImageStore.isStoredName(imageName))throw new SecurityException("图片附件无效");file=new ImageStore(this).fileFor(imageName);}
        if(!file.isFile())throw new java.io.IOException("图片已不存在");return file;
    }
    @Override protected void onResume(){super.onResume();if(classroomLesson!=null&&!ClassroomMonitor.allowed(this,classroomAccount))finish();}

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
}
