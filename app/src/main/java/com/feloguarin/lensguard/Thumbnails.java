package com.feloguarin.lensguard;

import android.graphics.Bitmap;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
import android.widget.ImageView;

import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Loads photo thumbnails off the main thread and keeps recent ones in memory. */
final class Thumbnails {
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private final LruCache<String, Bitmap> cache = new LruCache<String, Bitmap>(16 * 1024 * 1024) {
        @Override protected int sizeOf(String key, Bitmap bitmap) { return bitmap.getByteCount(); }
    };

    void load(File file, ImageView target, int size) {
        String key = file.getAbsolutePath() + "@" + size + "@" + file.lastModified();
        target.setTag(key);
        Bitmap cached = cache.get(key);
        if (cached != null) {
            target.setImageBitmap(cached);
            return;
        }
        executor.execute(() -> {
            Bitmap bitmap = Photos.decode(file, size);
            main.post(() -> {
                if (bitmap == null) return;
                cache.put(key, bitmap);
                if (key.equals(target.getTag())) target.setImageBitmap(bitmap);
            });
        });
    }

    void clear() {
        cache.evictAll();
    }

    void shutdown() {
        executor.shutdownNow();
        cache.evictAll();
    }
}
