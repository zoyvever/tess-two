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
import java.io.FileOutputStream;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

    }

    public void recoFunc(View v){
        TextView textView = (TextView) findViewById(R.id.textView);

//

        Bitmap bm= BitmapFactory.decodeResource(getResources(),R.drawable.nn3);
        TessBaseAPI baseApi = new TessBaseAPI();
        baseApi.init(/+ "/mcr.traineddata", "mcr"); // myDir + "/tessdata/eng.traineddata" must be present
        baseApi.setImage(bm);
        String recognizedText = baseApi.getUTF8Text(); // Log or otherwise display this string...
        textView.setText(recognizedText);
        baseApi.end();




    }
}


//todo
//написать путь до модели
//написать путь до картинки

