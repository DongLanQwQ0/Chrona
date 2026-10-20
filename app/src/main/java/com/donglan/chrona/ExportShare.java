package com.donglan.chrona;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.Intent;
import android.net.Uri;

/** Shares a completed public document with temporary, read-only URI access. */
final class ExportShare {
    private ExportShare() { }

    static void show(Activity activity, Uri uri, String mimeType) {
        Intent send = new Intent(Intent.ACTION_SEND).setType(mimeType)
                .putExtra(Intent.EXTRA_STREAM, uri)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        send.setClipData(ClipData.newRawUri("导出文件", uri));
        try {
            activity.startActivity(Intent.createChooser(send, null));
        } catch (ActivityNotFoundException | SecurityException error) {
            Feedback.show(activity, "文件已保存，无法打开系统分享面板");
        }
    }
}
