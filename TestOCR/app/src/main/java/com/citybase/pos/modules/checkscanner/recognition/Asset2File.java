package com.citybase.pos.modules.checkscanner.recognition;

import android.content.Context;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.os.Environment;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.UUID;

/**
 * Created by npakudin on 10/09/16
 */
class Asset2File {

    private static final String TAG = "Asset2File";

    @NonNull
    public static String init(@NonNull Context context) {
        File baseDir = getCacheDir(context);
        if (baseDir == null) {
            throw new IllegalStateException("Cannot access temporary dir");
        }
        File tessdata = new File(baseDir, "tessdata");
        File file = new File(tessdata, "mcr.traineddata");

        if (!file.delete()) {
            Log.w(TAG, "Cannot delete mcr.traineddata");
        }
        if (!tessdata.delete()) {
            Log.w(TAG, "Cannot delete tessdata");
        }
        Log.w(TAG, "tessdata.exists() :" + tessdata.exists());
        if (!tessdata.mkdirs()) {
            Log.w(TAG, "Cannot mkdirs for tessdata");
        }

        Log.w(TAG, "filename: " + file.getAbsolutePath());
        try {
            Log.w(TAG, "file.exists: " + file.exists() + " file.canWrite: " + file.canWrite());


            FileOutputStream outputStream = new FileOutputStream(file);
            AssetManager assetManager = context.getAssets();
            InputStream inputStream = assetManager.open("tessdata/mcr.traineddata");

            try {
                byte[] buffer = new byte[0x80000]; // Adjust if you want
                int bytesRead;
                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, bytesRead);
                    Log.w("MainActivity.onCreate", "bytesRead: " + bytesRead);
                }
            } finally {
                try {
                    outputStream.close();
                } catch (IOException ignored) {
                }
                try {
                    if (inputStream != null) inputStream.close();
                } catch (IOException ignored) {
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "copy", e);
        }
        Log.wtf(TAG, "file: " + file.exists() + " len : " + file.length());

        return baseDir.getAbsolutePath();
    }

    @Nullable
    private static File getCacheDir(@NonNull Context context) {
        File maxDir = null;
        long maxSpace = -1;

        Log.w(TAG, "getCacheDir()");

        for (File dir : context.getExternalCacheDirs()) {
            if (dir != null) {
                long space = dir.getFreeSpace();

                if (space > maxSpace) {
                    maxSpace = space;
                    maxDir = dir;
                }
            } else {
                Log.w(TAG, "cache dir is null");
            }
        }

        if (maxDir != null) {
            return maxDir;
        } else {
            return null;
        }
    }


    public static boolean saveBitmap(Bitmap bm, String name) {
        try {
            //make a new picture file
            File pictureFile = getOutputMediaFile(name);
            if (pictureFile == null) {
                return false;
            }

            //write the file
            FileOutputStream fos = new FileOutputStream(pictureFile);
            bm.compress(Bitmap.CompressFormat.JPEG, 100, fos);
            fos.close();
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Cannot save pic", e);
            return false;
        }
    }

    public static File getOutputMediaFile(String name) {

        File mediaStorageDir = new File(Environment.getExternalStorageDirectory().getAbsolutePath(), "POS checks");

        if (!mediaStorageDir.exists()) {
            if (!mediaStorageDir.mkdirs()) {
                return null;
            }
        }

        File mediaFile = new File(mediaStorageDir.getPath() + File.separator + name + ".jpg");

        return mediaFile;
    }

    public static String uniqueName() {

        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        return timeStamp + "_" + UUID.randomUUID().toString().substring(0, 5);
    }
}
