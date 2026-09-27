package com.donglan.chrona;

import android.app.Activity;
import android.net.Uri;
import android.view.Gravity;
import android.graphics.Outline;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;

/** Shared cropped image preview with a themed, accessible remove affordance. */
final class AttachmentImageTile extends FrameLayout {
    AttachmentImageTile(Activity activity, Uri imageUri, String imageName,
            Runnable onOpen, Runnable onRemove) {
        super(activity);
        setClipChildren(true);
        setClipToPadding(true);
        setClipToOutline(true);
        setOutlineProvider(new ViewOutlineProvider() {
            @Override public void getOutline(View view, Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), dp(12));
            }
        });

        ImageView preview = new ImageView(activity);
        preview.setScaleType(ImageView.ScaleType.CENTER_CROP);
        preview.setImageURI(imageUri);
        preview.setContentDescription("查看图片原图，可放大并保存：" + imageName);
        preview.setOnClickListener(view -> onOpen.run());
        addView(preview, new FrameLayout.LayoutParams(-1, -1));

        Button remove = new Button(activity);
        remove.setText("×");
        remove.setTextSize(20);
        remove.setGravity(Gravity.CENTER);
        remove.setPadding(0, 0, 0, 0);
        remove.setTextColor(UiStyle.colors(activity).primary);
        remove.setContentDescription("移除图片：" + imageName);
        remove.setMinimumWidth(dp(48));
        remove.setMinimumHeight(dp(48));
        UiStyle.glass(remove);
        UiStyle.pressable(remove);
        remove.setOnClickListener(view -> onRemove.run());
        FrameLayout.LayoutParams removeParams = new FrameLayout.LayoutParams(dp(48), dp(48),
                Gravity.TOP | Gravity.END);
        removeParams.setMargins(0, dp(6), dp(6), 0);
        addView(remove, removeParams);
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + .5f);
    }
}
