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
        double qualityOfAll=0;
        AssetManager assetManager = getApplicationContext().getAssets();
        InputStream istr;
        try {
            String[] list=getAssets().list("img");
            for (String file : list) {
                Log.d(TAG, file);
                istr = assetManager.open("img/"+file);
                Bitmap src = BitmapFactory.decodeStream(istr);
                Bitmap res = TextRecognizer.prepareImageForOcr(src);
                Rect micrRect= new Rect(10,0,res.getWidth(), res.getHeight());
                micrRect=TextRecognizer.findRect(context, res, micrRect);
                TextRecognizer.CheckData checkData = TextRecognizer.recognize(context, res, micrRect);
                res=TextRecognizer.drawRecText(checkData.res,scale, checkData.symbols);

                Log.d(TAG,"file: "+file+"; recognized: "+ checkData.wholeText);
                char[] recognizedTextChars= checkData.wholeText.toCharArray();
                char[] fileNameChars = file.toCharArray();
                double recognitionOfOne=100.0*fileNameChars.length/(recognizedTextChars.length+
                                levenshteinDistance(recognizedTextChars,fileNameChars));
                Log.d("recOne", "Recognized "+recognitionOfOne+"% of "+file+"; text: "+checkData.wholeText);
                if (recognitionOfOne==100.0){
                    qualityOfAll++;
                }

                showResults(src, res, checkData);
            }
            Log.d("total: ", ""+qualityOfAll/list.length);
        } catch (IOException e) {
            Log.d(TAG, "exc");
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

    public int levenshteinDistance (char[] lhs, char[] rhs) {
        int len0 = lhs.length + 1;
        int len1 = rhs.length + 1;

        // the array of distances
        int[] cost = new int[len0];
        int[] newcost = new int[len0];

        // initial cost of skipping prefix in String s0
        for (int i = 0; i < len0; i++) cost[i] = i;

        // dynamically computing the array of distances

        // transformation cost for each letter in s1
        for (int j = 1; j < len1; j++) {
            // initial cost of skipping prefix in String s1
            newcost[0] = j;

            // transformation cost for each letter in s0
            for(int i = 1; i < len0; i++) {
                // matching current letters in both strings
                int match = (lhs[i - 1] == rhs[j - 1]) ? 0 : 1;

                // computing cost for each transformation
                int cost_replace = cost[i - 1] + match;
                int cost_insert  = cost[i] + 1;
                int cost_delete  = newcost[i - 1] + 1;

                // keep minimum cost
                newcost[i] = Math.min(Math.min(cost_insert, cost_delete), cost_replace);
            }

            // swap cost/newcost arrays
            int[] swap = cost; cost = newcost; newcost = swap;
        }

        // the distance is the cost for transforming all letters in both strings
        return cost[len0 - 1];
    }
}
