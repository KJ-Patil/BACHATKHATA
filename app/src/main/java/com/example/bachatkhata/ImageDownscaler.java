package com.example.bachatkhata;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Turns a picked image into a small, square, upload-ready JPEG.
 *
 * <p>A full-resolution phone photo is several megabytes — uploaded and then
 * re-downloaded in full on every profile render. A profile avatar never needs
 * more than a couple hundred pixels, so this crops to a centred square and
 * re-encodes at 256×256, dropping the payload to roughly 15 KB.
 */
public final class ImageDownscaler {

    private static final int TARGET_SIZE = 256;
    private static final int JPEG_QUALITY = 85;

    private ImageDownscaler() {
    }

    /**
     * Decodes {@code uri}, centre-crops to a square, scales to 256×256 and encodes
     * as JPEG.
     *
     * @return the JPEG bytes, or null if the image could not be read/decoded
     */
    public static byte[] toAvatarJpeg(Context context, Uri uri) {
        Bitmap decoded = decodeSampled(context, uri);
        if (decoded == null) return null;

        Bitmap square = centerCropSquare(decoded);
        Bitmap scaled = Bitmap.createScaledBitmap(square, TARGET_SIZE, TARGET_SIZE, true);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        scaled.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out);

        // Free the intermediates promptly; avatars are decoded on the UI path.
        if (square != decoded) square.recycle();
        if (scaled != square) scaled.recycle();
        decoded.recycle();

        return out.toByteArray();
    }

    /**
     * Decodes the image already downsampled to near the target size, so a 12 MP
     * photo never has to be held in memory at full resolution just to be shrunk.
     */
    private static Bitmap decodeSampled(Context context, Uri uri) {
        try {
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            try (InputStream in = context.getContentResolver().openInputStream(uri)) {
                BitmapFactory.decodeStream(in, null, bounds);
            }

            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inSampleSize = sampleSizeFor(bounds.outWidth, bounds.outHeight);
            try (InputStream in = context.getContentResolver().openInputStream(uri)) {
                return BitmapFactory.decodeStream(in, null, opts);
            }
        } catch (IOException | SecurityException e) {
            return null;
        }
    }

    /** Largest power-of-two sample size that keeps both sides >= the target. */
    private static int sampleSizeFor(int width, int height) {
        int smaller = Math.min(width, height);
        int sample = 1;
        while (smaller / (sample * 2) >= TARGET_SIZE) {
            sample *= 2;
        }
        return sample;
    }

    private static Bitmap centerCropSquare(Bitmap source) {
        int side = Math.min(source.getWidth(), source.getHeight());
        int x = (source.getWidth() - side) / 2;
        int y = (source.getHeight() - side) / 2;
        return Bitmap.createBitmap(source, x, y, side, side);
    }
}
