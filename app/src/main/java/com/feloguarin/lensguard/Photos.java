package com.feloguarin.lensguard;

import android.annotation.SuppressLint;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.ExifInterface;

import java.io.File;
import java.io.IOException;

/** Decodes private evidence photos at reduced size, upright according to their EXIF orientation. */
@SuppressLint("ExifInterface") // The AndroidX copy backports fixes this app's minSdk 28 already has.
final class Photos {
    private Photos() {}

    static Bitmap decode(File file, int maxSide) {
        if (file == null || !file.exists()) return null;
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(file.getPath(), bounds);
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null;
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, maxSide);
        Bitmap bitmap;
        try {
            bitmap = BitmapFactory.decodeFile(file.getPath(), options);
        } catch (OutOfMemoryError tooLarge) {
            return null;
        }
        if (bitmap == null) return null;
        int degrees = rotation(file);
        if (degrees == 0) return bitmap;
        Matrix matrix = new Matrix();
        matrix.postRotate(degrees);
        Bitmap rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
        if (rotated != bitmap) bitmap.recycle();
        return rotated;
    }

    /** The largest power-of-two reduction that keeps the longer side at least {@code maxSide}. */
    static int sampleSize(int width, int height, int maxSide) {
        int sample = 1;
        while (Math.max(width, height) / (sample * 2) >= maxSide) sample *= 2;
        return sample;
    }

    private static int rotation(File file) {
        try {
            switch (new ExifInterface(file.getPath()).getAttributeInt(ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL)) {
                case ExifInterface.ORIENTATION_ROTATE_90: return 90;
                case ExifInterface.ORIENTATION_ROTATE_180: return 180;
                case ExifInterface.ORIENTATION_ROTATE_270: return 270;
                default: return 0;
            }
        } catch (IOException | RuntimeException unreadable) {
            return 0;
        }
    }
}
