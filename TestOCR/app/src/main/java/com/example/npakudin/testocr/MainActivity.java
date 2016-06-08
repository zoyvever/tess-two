package com.example.npakudin.testocr;


import android.content.Context;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Rect;
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

    private static final String TAG = "MainActivity";
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



        button.setOnClickListener(new View.OnClickListener() {
//            Integer i=0;
            @Override
            public void onClick(View v) {
                recognize();
//                i++;

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
        Context context = getApplicationContext();
        float scale = context.getResources().getDisplayMetrics().density;
        double successfullyRecognized = 0;
        AssetManager assetManager = getApplicationContext().getAssets();
        InputStream istr;
        try {
            String[] list = getAssets().list("img");
            for (String file : list) {
//            String file =list[i];
                Log.d(TAG, file);
                istr = assetManager.open("img/" + file);
                Bitmap src = BitmapFactory.decodeStream(istr);
                Bitmap res = TextRecognizer.prepareImageForOcr(src);
                Rect micrRect = new Rect(0, 0, res.getWidth(), res.getHeight());
                micrRect = TextRecognizer.findRect(context, res, micrRect);
                TextRecognizer.CheckData checkData = TextRecognizer.recognize(context, res, micrRect);
                res = TextRecognizer.drawRecText(checkData.res, scale, checkData.symbols);

                Log.d(TAG, "file: " + file + "; recognized: " + checkData.wholeText);

                double itemRecognition = 100.0 * file.length() / (checkData.wholeText.length() +
                        levenshteinDistance(checkData.wholeText, file));
                Log.d("itemRecognition", "Recognized " + itemRecognition + "% of " + file + "; text: " + checkData.wholeText);

                if (itemRecognition == 100.0) {
                    successfullyRecognized++;
                }

                showResults(src, res, checkData, itemRecognition);
            }
            Log.d("total: ", "" + successfullyRecognized / list.length);
        } catch (IOException e) {
            Log.e(TAG, e.toString());
        }
    }

    private void showResults(Bitmap src, Bitmap res, final TextRecognizer.CheckData checkData, Double itemRecognition) {

        String message = String.format("Number: %s, %n prc: %s" , checkData.wholeText, itemRecognition );
        Log.d("rectext", checkData.wholeText);

        imageViewSrc.setImageBitmap(src);
        imageViewRes.setImageBitmap(res);
        textViewRes.setText(message);
    }

    private boolean saveBitmap(Bitmap bm, String nameSuffix) {
        try {
            File pictureFile = getOutputMediaFile(nameSuffix);

            if (pictureFile == null) {
                return true;
            }
            FileOutputStream fos = new FileOutputStream(pictureFile);
            bm.compress(Bitmap.CompressFormat.JPEG, 100, fos);
            fos.close();
        } catch (Exception e) {
            Log.e(TAG, "Cannot save pic", e);
        }
        return false;
    }

    private static File getOutputMediaFile(String nameSuffix) {
        File mediaStorageDir = new File("/sdcard/", "POS checks");

        if (!mediaStorageDir.exists()) {
            if (!mediaStorageDir.mkdirs()) {
                return null;
            }
        }

        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        File mediaFile;
        mediaFile = new File(mediaStorageDir.getPath() + File.separator + "IMG_" + timeStamp + "_" + nameSuffix + ".jpg");

        return mediaFile;
    }

    public int levenshteinDistance(String recognizedText, String fileName) {
        char[] lhs = recognizedText.toCharArray();
        char[] rhs = fileName.toCharArray();
        int len0 = lhs.length + 1;
        int len1 = rhs.length + 1;
        int[] cost = new int[len0];
        int[] newcost = new int[len0];
        for (int i = 0; i < len0; i++) cost[i] = i;
        for (int j = 1; j < len1; j++) {
            newcost[0] = j;

            for (int i = 1; i < len0; i++) {
                int match = (lhs[i - 1] == rhs[j - 1]) ? 0 : 1;
                int cost_replace = cost[i - 1] + match;
                int cost_insert = cost[i] + 1;
                int cost_delete = newcost[i - 1] + 1;
                newcost[i] = Math.min(Math.min(cost_insert, cost_delete), cost_replace);
            }

            int[] swap = cost;
            cost = newcost;
            newcost = swap;
        }
        return cost[len0 - 1];
    }
}
