package com.example.npakudin.testocr;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.Date;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActiity";
    Button button;
    ImageView imageViewSrc;
    ImageView imageViewRes;
    TextView textViewRes;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        button = (Button) findViewById(R.id.button);

        imageViewSrc = (ImageView) findViewById(R.id.imageViewSrc);
        imageViewRes = (ImageView) findViewById(R.id.imageViewRes);
        textViewRes = (TextView) findViewById(R.id.textViewRes);
        int i =0;

        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                recognize();
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        recognize();
    }

    private void recognize() {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inMutable = true;

        File myFolder = new File("assets/img");
        File[] files = myFolder.listFiles();
//        for (int i=0; i <files.length; i++) {
//            try {
//                InputStream ims = getAssets().open(files[i].getName());
//                Bitmap src = BitmapFactory.decodeStream(ims);
        Bitmap src = BitmapFactory.decodeResource(getResources(), R.drawable.c, options);
//        Log.d("AssetsLog", files[0].getName().toString());

                int threshold = (int) (255 * 0.30);
                Bitmap res = TextRecognizer.prepareImageForOcr(src, threshold);
                TextRecognizer.CheckData checkData = TextRecognizer.recognize(getApplicationContext(), res,true);

                showResults(src, checkData);
//            } catch (IOException ex) {
//                return;
//            }
//        }
    }

    private void showResults(Bitmap src, final TextRecognizer.CheckData checkData) {

        String message = String.format("Routing Number: %s\nAccount Number: %s\nCheck Number: %s",
                checkData.routingNumber, checkData.accountNumber, checkData.checkNumber);

        imageViewSrc.setImageBitmap(src);
        imageViewRes.setImageBitmap(checkData.getBitmap());
        textViewRes.setText(message);
    }

    private boolean saveBitmap(Bitmap bm, String nameSuffix) {
        try {
            //make a new picture file
            File pictureFile = getOutputMediaFile(nameSuffix);

            if (pictureFile == null) {
                return true;
            }

            //write the file
            FileOutputStream fos = new FileOutputStream(pictureFile);
            bm.compress(Bitmap.CompressFormat.JPEG, 100, fos);
            //fos.write(data);
            fos.close();
        } catch (Exception e) {
            Log.e(TAG, "Cannot save pic", e);
        }
        return false;
    }

    //make picture and save to a folder
    private static File getOutputMediaFile(String nameSuffix) {
        //make a new file directory inside the "sdcard" folder
        File mediaStorageDir = new File("/sdcard/", "POS checks");

        //if this "JCGCamera folder does not exist
        if (!mediaStorageDir.exists()) {
            //if you cannot make this folder return
            if (!mediaStorageDir.mkdirs()) {
                return null;
            }
        }

        //take the current timeStamp
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        File mediaFile;
        //and make a media file:
        mediaFile = new File(mediaStorageDir.getPath() + File.separator + "IMG_" + timeStamp + "_" + nameSuffix + ".jpg");

        return mediaFile;
    }
}
