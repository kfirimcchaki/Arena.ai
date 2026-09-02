package dev.arena.mobile;

import android.app.DownloadManager;
import android.content.Context;
import android.net.Uri;
import android.os.Environment;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLDecoder;

/**
 * Saves images the user long-presses:
 *  - Android 10+ : MediaStore (shows up in Gallery under Pictures/Arena AI)
 *  - Android 8/9 : DownloadManager (public Downloads folder, no permission needed)
 */
public final class MediaSaver {

    public interface Callback {
        void onSaved(boolean ok, String message);
    }

    private MediaSaver() {
    }

    public static void saveImage(final Context context, final String imageUrl,
                                 final String userAgent, final Callback cb) {
        Thread t = new Thread(() -> {
            try {
                boolean ok;
                String msg;
                if (android.os.Build.VERSION.SDK_INT >= 29) {
                    ok = saveViaMediaStore(context, imageUrl, userAgent);
                    msg = ok ? "Image saved to Pictures/Arena AI" : "Could not save image";
                } else {
                    ok = saveViaDownloadManager(context, imageUrl, userAgent);
                    msg = ok ? "Image saved to Downloads" : "Could not save image";
                }
                final boolean fOk = ok;
                final String fMsg = msg;
                if (cb != null) {
                    new android.os.Handler(android.os.Looper.getMainLooper()).post(
                            () -> cb.onSaved(fOk, fMsg));
                }
            } catch (Throwable e) {
                if (cb != null) {
                    new android.os.Handler(android.os.Looper.getMainLooper()).post(
                            () -> cb.onSaved(false, "Could not save image: " + e.getMessage()));
                }
            }
        });
        t.setName("media-saver");
        t.start();
    }

    private static String fileNameFor(String url) {
        try {
            String path = Uri.parse(url).getPath();
            if (path != null && !path.isEmpty()) {
                String last = path.substring(path.lastIndexOf('/') + 1);
                if (!last.isEmpty()) {
                    last = URLDecoder.decode(last, "UTF-8");
                    last = last.replaceAll("[^a-zA-Z0-9._-]", "_");
                    if (last.length() > 80) last = last.substring(last.length() - 80);
                    return last;
                }
            }
        } catch (Exception ignored) {
        }
        return "image-" + System.currentTimeMillis() + ".jpg";
    }

    private static boolean saveViaMediaStore(Context context, String url, String ua) {
        String name = fileNameFor(url);
        android.content.ContentValues v = new android.content.ContentValues();
        v.put(android.provider.MediaStore.Images.Media.DISPLAY_NAME, name);
        v.put(android.provider.MediaStore.Images.Media.MIME_TYPE, guessMime(url));
        v.put(android.provider.MediaStore.Images.Media.RELATIVE_PATH,
                Environment.DIRECTORY_PICTURES + "/Arena AI");
        v.put(android.provider.MediaStore.Images.Media.IS_PENDING, 1);
        Uri item = context.getContentResolver().insert(
                android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, v);
        if (item == null) return false;
        try (InputStream in = openStream(url, ua);
             OutputStream out = context.getContentResolver().openOutputStream(item)) {
            if (in == null || out == null) return false;
            byte[] buf = new byte[16384];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            out.flush();
        } catch (IOException e) {
            context.getContentResolver().delete(item, null, null);
            return false;
        }
        v.clear();
        v.put(android.provider.MediaStore.Images.Media.IS_PENDING, 0);
        context.getContentResolver().update(item, v, null, null);
        return true;
    }

    private static boolean saveViaDownloadManager(Context context, String url, String ua) {
        DownloadManager dm = (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);
        if (dm == null) return false;
        DownloadManager.Request req = new DownloadManager.Request(Uri.parse(url));
        req.setTitle(fileNameFor(url));
        req.setDescription("Saved from Arena AI browser");
        req.setMimeType(guessMime(url));
        req.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileNameFor(url));
        req.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
        req.addRequestHeader("User-Agent", ua);
        dm.enqueue(req);
        return true;
    }

    private static String guessMime(String url) {
        String p = url.toLowerCase();
        if (p.contains(".png")) return "image/png";
        if (p.contains(".gif")) return "image/gif";
        if (p.contains(".webp")) return "image/webp";
        if (p.contains(".svg")) return "image/svg+xml";
        if (p.contains(".avif")) return "image/avif";
        return "image/jpeg";
    }

    private static InputStream openStream(String url, String ua) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(15000);
        c.setReadTimeout(30000);
        c.setInstanceFollowRedirects(true);
        c.setRequestProperty("User-Agent", ua);
        c.setRequestProperty("Accept", "image/avif,image/webp,image/png,image/svg+xml,image/*;q=0.8,*/*;q=0.5");
        c.connect();
        int code = c.getResponseCode();
        if (code / 100 != 2) {
            c.disconnect();
            throw new IOException("HTTP " + code);
        }
        return c.getInputStream();
    }
}
