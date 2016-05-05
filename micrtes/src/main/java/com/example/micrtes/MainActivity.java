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
        File file = new File(this.getFilesDir() + "/data/tessdata/", filename);
        if (!file.mkdirs()) {
            Log.wtf("MainActivity.onCreate", "Cannot mkdirs");
        }

        Log.w("MainActivity.onCreate", "filename: " + file.getAbsolutePath());

        try {
            FileOutputStream outputStream = openFileOutput(filename, Context.MODE_PRIVATE);
            InputStream mcrInputStream = getResources().openRawResource(R.raw.mcr);

            copy(mcrInputStream, outputStream);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }

        // TODO: compile datamodel for Android or use existing for English - I want to recognize anything for Android now
        //
        // Now if run app under debug (Shift + F9), then open Android Monitor (Alt + 6 - not F6, just 6)
        // Logs looks like below. Main problem - SIGSEGV
        //
        // 05-06 01:02:12.100 18564-18564/com.example.micrtes W/onCreate: filename: /data/data/com.example.micrtes/files/data/tessdata/mcr.traineddata
        // 05-06 01:02:12.110 18564-18564/com.example.micrtes V/BitmapFactory: DecodeImagePath(decodeResourceStream3) : res/drawable/nn3.png
        // 05-06 01:02:12.170 18564-18564/com.example.micrtes E/Tesseract(native): Could not initialize Tesseract API with language=mcr!
        // 05-06 01:02:12.410 18564-18564/com.example.micrtes A/libc: Fatal signal 11 (SIGSEGV), code 1, fault addr 0x8 in tid 18564 (example.micrtes)


        Bitmap bm= BitmapFactory.decodeResource(getResources(),R.drawable.nn3);
        TessBaseAPI baseApi = new TessBaseAPI();
        baseApi.init(this.getFilesDir() + "/data/", "mcr"); // myDir + "/tessdata/eng.traineddata" must be present
        baseApi.setImage(bm);
        String recognizedText = baseApi.getUTF8Text(); // Log or otherwise display this string...
        textView.setText(recognizedText);
        baseApi.end();
    }

    private void copy(InputStream inputStream, OutputStream outputStream) {
        try {
            byte[] buffer = new byte[1024]; // Adjust if you want
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1)
            {
                outputStream.write(buffer, 0, bytesRead);
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                inputStream.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
            try {
                outputStream.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
