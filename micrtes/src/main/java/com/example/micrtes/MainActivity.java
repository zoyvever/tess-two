package com.example.micrtes;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.widget.ImageView;
import android.widget.TextView;


import com.googlecode.tesseract.android.TessBaseAPI;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import fakeawt.Rectangle;
import magick.MagickException;
import magick.MagickImage;
import magick.util.MagickBitmap;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        String filename = "mcr.traineddata";
        //String base = Environment.getExternalStorageDirectory() + File.separator + "micrtes" + File.separator;
        String base = "/data/local/tmp/micr/";
        File file0 = new File(base);
        File file1 = new File(base + "tessdata");
        File file = new File(base + "/tessdata/" + filename);
        try {
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

        CropAndRecognize(base,2,3,4,5);
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

    public void CropAndRecognize(String base, int a, int b, int c,int d){
        TextView textView = (TextView) findViewById(R.id.textView);
        assert textView != null;
        Bitmap bm= BitmapFactory.decodeResource(getResources(),R.drawable.nn3);

//        Rectangle bounds = new Rectangle(a,b,c,d);
        //TODO check if bounds >0

        ImageView imgview= (ImageView) findViewById(R.id.pic);
        final MagickImage img;
        try {
            img = new MagickBitmap().fromBitmap(bm);
//            img.cropImage(bounds);
            img.thresholdImage(3);
            imgview.setImageBitmap(MagickBitmap.ToBitmap(img));
        } catch (MagickException e) {
            e.printStackTrace();
        }

        TessBaseAPI baseApi = new TessBaseAPI();
        baseApi.init(base, "mcr");
        baseApi.setImage(bm);
        String recognizedText = baseApi.getUTF8Text();.
        textView.setText(recognizedText);
        baseApi.end();

    }
}
