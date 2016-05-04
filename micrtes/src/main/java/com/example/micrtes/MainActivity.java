package com.example.micrtes;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Environment;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;


import com.googlecode.tesseract.android.TessBaseAPI;

import java.io.File;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

    }

    public void recoFunc(View v){
        TextView textView = (TextView) findViewById(R.id.textView);


        File myDir = Environment.getRootDirectory(); //I dont have a sdcard
        File img = new File(myDir, "nn3.png");
        TessBaseAPI baseApi = new TessBaseAPI();
        baseApi.init(myDir+ "mcr.traineddata", "mcr"); // myDir + "/tessdata/eng.traineddata" must be present
        baseApi.setImage(img);

        String recognizedText = baseApi.getUTF8Text(); // Log or otherwise display this string...
        baseApi.end();
        textView.setText(recognizedText);
//        Bitmap bm= BitmapFactory.decodeResource(getResources(),R.drawable.nn3);

    }
}


//todo
//написать путь до модели
//написать путь до картинки

