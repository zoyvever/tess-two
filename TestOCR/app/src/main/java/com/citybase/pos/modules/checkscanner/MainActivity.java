package com.citybase.pos.modules.checkscanner;


import android.app.Activity;
import android.content.Context;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import com.citybase.pos.modules.checkscanner.recognition.CheckData;
import com.citybase.pos.modules.checkscanner.recognition.MicrRecognizer;
import com.example.npakudin.testocr.R;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class MainActivity extends Activity {

    private static final String TAG = "MainActivity";
    TextView textViewRes;
    ListView listView;
    ArrayAdapter<CheckData> adapter;

    static List<CheckData> entities = new ArrayList<>();
    static String info;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        MicrRecognizer.init(getApplicationContext());

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

                CheckData checkData = entities.get(position);

                holder.imageView().setImageBitmap(checkData.image);

                //boolean isBad = checkData.isOk && (checkData.distance != 0);

                //holder.textView().setText((isBad ? "BAD " : "") + checkData. + " - " + checkData.filename + " - " + checkData.descr);
                holder.textView2().setText(checkData.rawText);
                holder.textView().setTextColor(Color.rgb(0,0,0));

                return convertView;
            }
        };

        listView.setAdapter(adapter);
    }

    public class ViewHolder {
        private View row;
        private ImageView imageView;
        private TextView textView;
        private TextView textView2;

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

        public TextView textView2() {
            if (this.textView2 == null) {
                this.textView2 = (TextView) row.findViewById(R.id.textView2);
            }
            return this.textView2;
        }
    }

    //private boolean isRecognized = false;

    @Override
    protected void onResume() {
        super.onResume();
//
        //Log.d(TAG, "onResume, start, isRecognized = " + isRecognized);
        if (entities.size() == 0) {
            recognize();
            //Log.d(TAG, "onResume, recognized, isRecognized = " + isRecognized);
        }

        adapter.clear();
        adapter.addAll(entities);
        adapter.notifyDataSetChanged();

        textViewRes.setText(info);
    }

    private void recognize() {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inMutable = true;
        Context context = getApplicationContext();
        float scale = context.getResources().getDisplayMetrics().density;

        int symbolErrors = 0;
        int totalSymbols = 0;

        int recognizedChecks = 0;
        int incorrectlyRecognizedChecks = 0;

        AssetManager assetManager = getApplicationContext().getAssets();
        try {
            String[] list = getAssets().list("img_ok");
            Arrays.sort(list);

            for (String file : list) {
                Log.d(TAG, file);
                InputStream istr = assetManager.open("img_ok/" + file);
                Bitmap src = BitmapFactory.decodeStream(istr);

                Log.d(TAG, "file: " + file + "; recognizing...");
                CheckData checkData = MicrRecognizer.recognize(src);
                if (checkData != null && checkData.isOk) {

                    String rawText = checkData.rawText;//.replace(" ", "").replace("%", "a");

                    //String realText = file.substring(0, file.length() - 5).replace("_", " ");
                    String realText = "a271071321a c9080054103c 0903";
                    int distance = Utils.levenshteinDistance(rawText, realText);

                    totalSymbols += checkData.rawText.length();
                    symbolErrors += distance;
                    recognizedChecks++;
                    if (distance > 0) {
                        incorrectlyRecognizedChecks++;
                    }


                    //checkData.res = Utils.drawRecText(checkData.res, scale, checkData.symbols, checkData.realText, checkData.distance);
                }
                entities.add(checkData);
            }

            info = "symbolErrors: " + symbolErrors + " / " + totalSymbols + " = " + (symbolErrors/(double)totalSymbols) +
                    ", recognizedChecks: " + recognizedChecks + " / " + list.length + " = " + (recognizedChecks / (double)list.length) +
                    ", incorrectlyRecognizedChecks: " + incorrectlyRecognizedChecks + " / " + list.length + " = " + (incorrectlyRecognizedChecks / (double)list.length);

            Log.d("MainActivity", info);




        } catch (IOException e) {
            Log.e(TAG, e.toString());
        }
    }
}
