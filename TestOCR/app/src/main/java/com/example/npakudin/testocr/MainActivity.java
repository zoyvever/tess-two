package com.example.npakudin.testocr;


import android.content.Context;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
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
        Context context=getApplicationContext();
        float scale = context.getResources().getDisplayMetrics().density;

        AssetManager assetManager = getApplicationContext().getAssets();
        InputStream istr;
        try {
            String[] list=getAssets().list("img");
            for (String file : list) {
                Log.d("asset", file);
                istr = assetManager.open("img/"+file);
                Bitmap src = BitmapFactory.decodeStream(istr);
                Bitmap res = TextRecognizer.prepareImageForOcr(src);
                int[] borders=TextRecognizer.findRect(context, res,0, res.getHeight());

                TextRecognizer.CheckData checkData = TextRecognizer.recognize(context, res, borders[0], borders[1]);
                res=TextRecognizer.drowRecText(checkData.res,scale, checkData.symbols);
                showResults(src, res, checkData);
            }
        } catch (IOException e) {
            Log.d("exc", "exc");
        }
//        Bitmap src = BitmapFactory.decodeResource(getResources(), R.drawable.b, options);
//        Bitmap res = TextRecognizer.prepareImageForOcr(src);
//        TextRecognizer.CheckData checkData = TextRecognizer.recognize(getApplicationContext(), res,true, 0, 0);//
    }

    private void showResults(Bitmap src, Bitmap res, final TextRecognizer.CheckData checkData) {

//        String message = String.format("Routing Number: %s\nAccount Number: %s\nCheck Number: %s",
//                checkData.routingNumber, checkData.accountNumber, checkData.checkNumber);
        String message = String.format("Number: %s", checkData.wholeText);
        Log.d("rectext", checkData.wholeText);

        imageViewSrc.setImageBitmap(src);
        imageViewRes.setImageBitmap(res);
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
