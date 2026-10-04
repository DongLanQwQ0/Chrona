package com.donglan.chrona;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import java.util.Map;

/** Local-only address encoding. Pairing credentials never enter this payload. */
final class LanAddressQr {
    static void update(android.widget.ImageView view, String address) throws WriterException {
        if (address.isEmpty()) {
            view.setImageDrawable(null); view.setTag(null);
            view.setVisibility(android.view.View.GONE); return;
        }
        if (!address.equals(view.getTag())) {
            BitMatrix matrix = encode(address, 480);
            android.graphics.Bitmap bitmap = android.graphics.Bitmap.createBitmap(
                    matrix.getWidth(), matrix.getHeight(), android.graphics.Bitmap.Config.ARGB_8888);
            for (int y = 0; y < matrix.getHeight(); y++) for (int x = 0; x < matrix.getWidth(); x++)
                bitmap.setPixel(x, y, matrix.get(x, y) ? 0xff000000 : 0xffffffff);
            view.setImageBitmap(bitmap); view.setTag(address);
        }
        view.setVisibility(android.view.View.VISIBLE);
    }
    static BitMatrix encode(String address, int pixels) throws WriterException {
        return new QRCodeWriter().encode(address, BarcodeFormat.QR_CODE, pixels, pixels,
                Map.of(EncodeHintType.MARGIN, 4));
    }
}
