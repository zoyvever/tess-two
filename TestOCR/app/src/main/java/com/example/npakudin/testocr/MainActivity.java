package com.example.npakudin.testocr;


import android.content.Context;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";
    TextView textViewRes;
    ListView listView;
    List<CheckData> entities = new ArrayList<>();
    ArrayAdapter<CheckData> adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        TextRec.init(getApplicationContext());

        textViewRes = (TextView) findViewById(R.id.textViewRes);
        listView = (ListView) findViewById(R.id.listview);


        adapter = new ArrayAdapter<CheckData>(this, R.layout.list_item) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                ViewHolder holder = null;
                LayoutInflater inflater = getLayoutInflater();
                if (convertView == null) {
                    convertView = inflater.inflate(R.layout.list_item, null, false);
                    holder = new ViewHolder(convertView);
                    convertView.setTag(holder);
                } else {
                    holder = (ViewHolder) convertView.getTag();
                }
                holder.imageView().setImageBitmap(entities.get(position).res);
                holder.textView().setText(entities.get(position).distance + "");

                return convertView;
            }
        };

        listView.setAdapter(adapter);
    }

    public class ViewHolder {
        private View row;
        private ImageView imageView;
        private TextView textView;

        public ViewHolder(View row) {
            this.row = row;
        }

        public ImageView imageView() {
            if (this.imageView == null) {
                this.imageView = (ImageView) row.findViewById(R.id.imageView);
            }
            return this.imageView;
        }

        public TextView textView() {
            if (this.textView == null) {
                this.textView = (TextView) row.findViewById(R.id.textView);
            }
            return this.textView;
        }

    }

    private boolean isRecognized = false;

    @Override
    protected void onResume() {
        super.onResume();
//
        if (!isRecognized) {
            recognize();
            isRecognized = true;
            adapter.clear();
            adapter.addAll(entities);
            adapter.notifyDataSetChanged();
        }
    }

    private void recognize() {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inMutable = true;
        Context context = getApplicationContext();
        float scale = context.getResources().getDisplayMetrics().density;
        double successfullyRecognized = 0;
        double allPrc = 0;
        AssetManager assetManager = getApplicationContext().getAssets();
        InputStream istr;
        try {
            String[] list = getAssets().list("img");
            for (String file : list) {
                Log.d(TAG, file);
                istr = assetManager.open("img/" + file);
                Bitmap src = BitmapFactory.decodeStream(istr);
                double threshold=0;

                Bitmap res=null;
                TextRec.MicrInfo micrInfo=null;
                List<TextRec.Symbol> rawRecognize=null;
                do {
                    res = TextRec.prepareImage(src, threshold);
                    rawRecognize = TextRec.rawRecognize(res);
                    micrInfo = TextRec.findBorders(rawRecognize);
                    threshold=threshold+ 0.1;
                }while(threshold<0.5 && micrInfo.inLineRecognized<8);

                rawRecognize=TextRec.filterTheline(rawRecognize,micrInfo);
                CheckData checkData = TextRec.improve(res, rawRecognize,micrInfo);
                checkData.res = TextRec.drawRecText(res, scale, checkData.symbols);

                Log.d(TAG, "file: " + file + "; recognized: " + checkData.wholeText);

                checkData.realText = file.substring(0, file.length() - 5);
                checkData.distance = levenshteinDistance(checkData.wholeText, checkData.realText);

                double itemRecognition = (checkData.wholeText.length() - checkData.distance) / (file.length() - 5.0);
                Log.d("itemRecognition", "Recognized " + itemRecognition + "% of " + file + "; text: " + checkData.wholeText);
                allPrc = allPrc + itemRecognition;
                if (itemRecognition == 1) {
                    successfullyRecognized++;
                }
                saveBitmap(checkData.res, file.substring(0, file.length() - 4));


                entities.add(checkData);
            }
            Log.d("allPrc", "" + allPrc / list.length);
            Log.d("total: ", "" + successfullyRecognized / list.length);


            for (CheckData item : entities) {
                Log.d(TAG, "realText:   " + item.realText);
                Log.d(TAG, "recognized: " + item.wholeText);
                Log.d(TAG, "levenshteinDistance: " + item.distance);
            }
        } catch (IOException e) {
            Log.e(TAG, e.toString());
        }
    }

    private boolean saveBitmap(Bitmap bm, String name) {
        try {
            File pictureFile = getOutputMediaFile(name + ".jpg");

            if (pictureFile == null) {
                return true;
            }
            FileOutputStream fos = new FileOutputStream(pictureFile);
            bm.compress(Bitmap.CompressFormat.JPEG, 90, fos);
            fos.close();
        } catch (Exception e) {
            Log.e(TAG, "Cannot save pic", e);
        }
        return false;
    }

    private static File getOutputMediaFile(String name) {
        File mediaStorageDir = new File("/sdcard/Pictures", "checks");

        if (!mediaStorageDir.exists()) {
            if (!mediaStorageDir.mkdirs()) {
                return null;
            }
        }

//        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
//        File mediaFile;
        File mediaFile = new File(mediaStorageDir.getPath() + File.separator + name);

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
