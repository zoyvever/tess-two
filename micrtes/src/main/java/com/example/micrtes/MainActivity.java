package com.example.micrtes;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Environment;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.TextView;


import com.googlecode.tesseract.android.TessBaseAPI;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        TextView textView = (TextView) findViewById(R.id.textView);
        assert textView != null;

        String filename = "mcr.traineddata";

        //String base = Environment.getExternalStorageDirectory() + File.separator + "micrtes" + File.separator;
        String base = "/data/local/tmp/micr/";

        File file0 = new File(base);
        if (!file0.mkdir()) {
            Log.wtf("MainActivity.onCreate", "Cannot mkdir 0");
        }
        Log.wtf("MainActivity.onCreate", "file0: " + file0.exists());


        File file1 = new File(base + "tessdata");
        if (!file1.mkdir()) {
            Log.wtf("MainActivity.onCreate", "Cannot mkdir 1");
        }

        File file = new File(base + "/tessdata/" + filename);
        Log.w("MainActivity.onCreate", "filename: " + file.getAbsolutePath());
        try {
            Log.wtf("MainActivity.onCreate", "file.exists: " + file.exists() + " file.canWrite: " + file.canWrite());

            FileOutputStream outputStream = openFileOutput(filename, Context.MODE_PRIVATE);
            InputStream inputStream = getResources().openRawResource(R.raw.mcr);

            try {
                byte[] buffer = new byte[0x80000]; // Adjust if you want
                int bytesRead;
                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, bytesRead);
                    Log.wtf("MainActivity.onCreate", "bytesRead: " + bytesRead);
                }
            } finally {
                try {
                    if (outputStream != null) outputStream.close();
                } catch (IOException ignored) {
                }
                try {
                    if (inputStream != null) inputStream.close();
                } catch (IOException ignored) {
                }
            }


        } catch (Exception e) {
            Log.e("MainActivity", "copy", e);
        }
        Log.wtf("MainActivity.onCreate", "file: " + file.exists() + " len : " + file.length());


        Bitmap bm= BitmapFactory.decodeResource(getResources(),R.drawable.nn3);
        TessBaseAPI baseApi = new TessBaseAPI();
        baseApi.init(base, "mcr"); // myDir + "/tessdata/eng.traineddata" must be present
        baseApi.setImage(bm);
        String recognizedText = baseApi.getUTF8Text(); // Log or otherwise display this string...
        textView.setText(recognizedText);
        baseApi.end();
    }

    private void copy(InputStream inputStream, OutputStream outputStream) {
        try {
            byte[] buffer = new byte[0x10000]; // Adjust if you want
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1)
            {
                outputStream.write(buffer, 0, bytesRead);
                Log.wtf("MainActivity.onCreate", "bytesRead: " + bytesRead);
            }
        } catch (IOException e) {
            Log.e("MainActivity", "copy", e);
        } finally {
            try {
                inputStream.close();
            } catch (IOException e) {
                Log.e("MainActivity", "copy", e);
            }
            try {
                outputStream.close();
            } catch (IOException e) {
                Log.e("MainActivity", "copy", e);
            }
        }
    }
}
