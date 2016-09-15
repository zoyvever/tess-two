package com.example.npakudin.testocr;


import android.content.Context;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.util.Pair;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import com.example.npakudin.testocr.micr.CheckData;
import com.example.npakudin.testocr.micr.MicrRecognizer;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

public class MainActivity extends AppCompatActivity {

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

                holder.imageView().setImageBitmap(checkData.res);

                boolean isEq = checkData.isOk == (checkData.distance == 0);

                holder.textView().setText((isEq ? "" : "BAD ") + checkData.distance + " - " + checkData.filename + " - " + checkData.descr);
                holder.textView().setTextColor(isEq ? Color.rgb(0,0,0) : Color.rgb(0xff,0,0));

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

        AssetManager assetManager = getApplicationContext().getAssets();
        try {
            String[] list = getAssets().list("img");
            Arrays.sort(list);

            for (String file : list) {
                Log.d(TAG, file);
                InputStream istr = assetManager.open("img/" + file);
                Bitmap src = BitmapFactory.decodeStream(istr);

                Log.d(TAG, "file: " + file + "; recognizing...");
                CheckData checkData = MicrRecognizer.recognize(src);
                checkData.res = DrawUtils.drawRecText(checkData.res, scale, checkData.symbols, checkData.realText);

                checkData.realText = file.substring(0, file.length() - 5);
                checkData.distance = levenshteinDistance(checkData.wholeText, checkData.realText);

                totalSymbols += checkData.wholeText.length();
                symbolErrors += checkData.distance;
                if (checkData.distance == 0) {
                    recognizedChecks++;
                }
                //saveBitmap(checkData.res, file.substring(0, file.length() - 4));

                //checkData.res = null;

                if (checkData.distance == 0) {
                    checkData.res = null;
                } else {
                    Log.d(TAG, "file: " + file + "; src           : " + checkData.realText);
                    Log.d(TAG, "file: " + file + "; BAD recognized: " + checkData.wholeText);
                }

                checkData.filename = file;
                entities.add(checkData);
            }

            info = "symbolErrors: " + symbolErrors + " / " + totalSymbols + " = " + (symbolErrors/(double)totalSymbols) +
                    ", recognizedChecks: " + recognizedChecks + " / " + list.length + " = " + (recognizedChecks / (double)list.length);
            Log.d("MainActivity", info);


            Log.d(TAG, "Char width");
            handleStats(MicrRecognizer.globalWidth);
            Log.d(TAG, "Char height");
            handleStats(MicrRecognizer.globalHeight);

//            for (Map.Entry<String, Map<Integer, Integer>> letterItem : MicrRecognizer.globalHeight.entrySet()) {
//                Map<Integer, Integer> map = letterItem.getValue();
//                Log.d(TAG, letterItem.getKey());
//                for (Integer item : map.keySet()) {
//                    Log.d(TAG, item + ", " + map.get(item));
//                }
//            }




            for (CheckData item : entities) {
                if (item.distance == 0) {
                    continue;
                }

                String recognizedFields = String.format(" routing: %s %n account: %s %n check number: %s %n ",
                        item.routingNumber, item.accountNumber, item.checkNumber);
                Log.d(TAG, "realText:   " + item.realText);
                Log.d(TAG, "recognized: " + item.wholeText);
                Log.d(TAG, "levenshteinDistance: " + item.distance);
                Log.d(TAG, recognizedFields);
//                Log.d(TAG, "routing: "+item.routingNumber);
//                Log.d(TAG, "account: "+item.accountNumber);
//                Log.d(TAG, "check number: "+item.checkNumber);
            }

        } catch (IOException e) {
            Log.e(TAG, e.toString());
        }
    }

    private void handleStats(Map<String, Map<Integer, Integer>> stats) {
        List<String> widthChars = new ArrayList<>(stats.keySet());
        Collections.sort(widthChars);
        for (String letterItem : widthChars) {

            Map<Integer, Integer> map = stats.get(letterItem);
            List<Map.Entry<Integer, Integer>> freq = new ArrayList<>(map.entrySet());
            Collections.sort(freq, new Comparator<Map.Entry<Integer, Integer>>() {
                @Override
                public int compare(Map.Entry<Integer, Integer> lhs, Map.Entry<Integer, Integer> rhs) {
                    return lhs.getValue() < rhs.getValue() ? -1 :
                            lhs.getValue() > rhs.getValue() ? 1 : 0;
                }
            });
            double avg = 0;
            int count = 0;
            for (Map.Entry<Integer, Integer> item : freq) {
                avg += item.getKey() * item.getValue();
                count += item.getValue();
            }
            avg = avg / count;
            double disp = 0;
            for (Map.Entry<Integer, Integer> item : freq) {
                disp += Math.pow(item.getKey() - avg, 2) * item.getValue();
            }
            disp = Math.sqrt(disp / (count * (count - 1)));


            Log.d(TAG, "LETTER, " + letterItem + ", " + avg + ", " + disp + ", " + count);



            for (Map.Entry<Integer, Integer> item : freq) {
                Log.d(TAG, item.getKey() + ", " + item.getValue());
            }
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
