package com.donglan.chrona;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Matrix;
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

import java.io.FileInputStream;
import java.io.InputStream;
import java.io.OutputStream;

/** Full-screen attachment viewer with pinch zoom and Android's document save picker. */
public final class AttachmentViewerActivity extends Activity {
    static final String EXTRA_IMAGE_NAME = "image_name";
    private static final int CREATE_IMAGE_DOCUMENT = 1;
    private final Matrix imageMatrix = new Matrix();
    private final Matrix fitMatrix = new Matrix();
    private ImageView image;
    private String imageName;
    private float scale = 1f;
    private float lastX;
    private float lastY;
    private boolean matrixReady;
    private boolean dragReady;
    private ScaleGestureDetector scaleDetector;
    private GestureDetector gestureDetector;

    @Override protected void onCreate(Bundle state) {
        ThemeStore.apply(this);
        super.onCreate(state);
        imageName = getIntent().getStringExtra(EXTRA_IMAGE_NAME);
        if (imageName == null || !ImageStore.isStoredName(imageName)) {
            finish();
            return;
        }
        FrameLayout stage = new FrameLayout(this);
        stage.setBackgroundColor(0xFF090A0C);
        image = new ImageView(this);
        image.setScaleType(ImageView.ScaleType.MATRIX);
        image.setImageURI(Uri.fromFile(new ImageStore(this).fileFor(imageName)));
        stage.addView(image, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout shell = new LinearLayout(this);
        shell.setOrientation(LinearLayout.VERTICAL);
        shell.setPadding(dp(16), dp(6), dp(16), dp(10));
        ImageButton back = new ImageButton(this);
        back.setImageResource(R.drawable.ic_arrow_left);
        back.setImageTintList(android.content.res.ColorStateList.valueOf(
                UiStyle.colors(this).primary));
        back.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        back.setContentDescription("返回图片附件");
        UiStyle.pill(back, false);
        back.setPadding(dp(12), dp(12), dp(12), dp(12));
        back.setOnClickListener(view -> finish());
        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.addView(back, new LinearLayout.LayoutParams(dp(48), dp(48)));
        TextView title = new TextView(this);
        title.setText("图片附件");
        title.setTextSize(17);
        UiStyle.title(title);
        title.setGravity(Gravity.CENTER);
        header.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        TextView save = new TextView(this);
        save.setText("保存");
        save.setTextSize(15);
        save.setGravity(Gravity.CENTER);
        save.setTextColor(UiStyle.colors(this).primary);
        save.setMinHeight(dp(48));
        save.setMinWidth(dp(56));
        UiStyle.pill(save, false);
        save.setOnClickListener(view -> saveImage());
        header.addView(save, new LinearLayout.LayoutParams(dp(56), dp(48)));
        shell.addView(header, new LinearLayout.LayoutParams(-1, -2));
        View spacer = new View(this);
        shell.addView(spacer, new LinearLayout.LayoutParams(-1, 0, 1));
        TextView hint = new TextView(this);
        hint.setText("双指缩放 · 双击放大");
        hint.setTextSize(12);
        hint.setGravity(Gravity.CENTER);
        hint.setTextColor(0xCCFFFFFF);
        shell.addView(hint, new LinearLayout.LayoutParams(-1, dp(34)));
        FrameLayout.LayoutParams shellParams = new FrameLayout.LayoutParams(-1, -1);
        stage.addView(shell, shellParams);
        UiStyle.applyInsets(stage, shell);
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
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("image/jpeg");
        intent.putExtra(Intent.EXTRA_TITLE, "Chrona-" + imageName);
        startActivityForResult(intent, CREATE_IMAGE_DOCUMENT);
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != CREATE_IMAGE_DOCUMENT || resultCode != RESULT_OK || data == null
                || data.getData() == null) return;
        try (InputStream input = new FileInputStream(new ImageStore(this).fileFor(imageName));
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

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
}
