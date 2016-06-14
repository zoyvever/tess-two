package com.example.npakudin.testocr;

import android.content.Context;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.support.annotation.BoolRes;
import android.support.annotation.NonNull;
import android.util.Log;
import android.util.Pair;

import com.googlecode.leptonica.android.Binarize;
import com.googlecode.leptonica.android.Pix;
import com.googlecode.leptonica.android.ReadFile;
import com.googlecode.leptonica.android.Rotate;
import com.googlecode.leptonica.android.Skew;
import com.googlecode.leptonica.android.WriteFile;
import com.googlecode.tesseract.android.ResultIterator;
import com.googlecode.tesseract.android.TessBaseAPI;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;

public class TextRec {
    private static final String LOGTAG = "TextRecognizer";
    private static TessBaseAPI baseApi = null;

    public static class Symbol {
        public String symbol;
        public Double confidence;

        public List<Pair<String, Double>> choicesAndConf;
        public Rect rect;
    }

    public static File getCacheDir(Context context) {
        File maxDir = null;
        long maxSpace = -1;

        Log.d(LOGTAG, "getCacheDir()");

        for (File dir : context.getExternalCacheDirs()) {
            if (dir != null) {
                long space = dir.getFreeSpace();

                if (space > maxSpace) {
                    maxSpace = space;
                    maxDir = dir;
                }
            } else {
                Log.w(LOGTAG, "cache dir is null");
            }
        }

        if (maxDir != null) {
            return maxDir;
        } else {
            return null;
        }
    }

    public static HashMap<Integer, Integer> fillTheMap(HashMap<Integer, Integer> map, Integer rect) {
        if (map.get(rect) != null) {
            map.put(rect, map.get(rect) + 1);
        } else {
            map.put(rect, 1);
        }
        return map;
    }

    public static int findMostFrequentItem(HashMap<Integer, Integer> map) {
        int trueBorder = 0;
        int freq = 0;
        for (HashMap.Entry<Integer, Integer> entry : map.entrySet()) {
            if (entry.getValue() > freq) {
                trueBorder = entry.getKey();
                freq = entry.getValue();
            }
        }
        return trueBorder;
    }

    public static File createMicrTessData(Context context) {
        File baseDir = getCacheDir(context);
        File tessdata = new File(baseDir, "tessdata");
        File file = new File(tessdata, "mcr.traineddata");

        if (!file.delete()) {
            Log.w(LOGTAG, "Cannot delete file");
        }
        if (!tessdata.delete()) {
            Log.w(LOGTAG, "Cannot delete tessdata");
        }
        Log.w(LOGTAG, "tessdata.exists() :" + tessdata.exists());
        if (!tessdata.mkdirs()) {
            Log.w(LOGTAG, "Cannot mkdirs");
        }

        Log.w(LOGTAG, "filename: " + file.getAbsolutePath());
        try {
            Log.w(LOGTAG, "file.exists: " + file.exists() + " file.canWrite: " + file.canWrite());


            FileOutputStream outputStream = new FileOutputStream(file);
            AssetManager assetManager = context.getAssets();
            InputStream inputStream = assetManager.open("tessdata/mcr.traineddata");

            try {
                byte[] buffer = new byte[0x80000]; // Adjust if you want
                int bytesRead;
                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, bytesRead);
                    Log.w("MainActivity.onCreate", "bytesRead: " + bytesRead);
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
            Log.e(LOGTAG, "copy", e);
        }
        Log.wtf(LOGTAG, "file: " + file.exists() + " len : " + file.length());
        return baseDir;
    }

    private static String initTessBaseApi(Context context, Bitmap bm) {
        if (baseApi == null) {
            File baseDir = createMicrTessData(context);

            baseApi = new TessBaseAPI();
            baseApi.init(baseDir.getAbsolutePath(), "mcr");
        }
        baseApi.setPageSegMode(TessBaseAPI.PageSegMode.PSM_AUTO);
        baseApi.setImage(bm);
        return baseApi.getUTF8Text();
    }

    public static Bitmap recognize(Bitmap bm, Context context) {
//        binarize and find skew
        float i = 0;
        String recognizedText = "";
        Bitmap res;
        do {
            Pix imag = Binarize.otsuAdaptiveThreshold(ReadFile.readBitmap(bm),
                    3000, 3000,
                    3 * Binarize.OTSU_SMOOTH_X, 3 * Binarize.OTSU_SMOOTH_Y,
                    Binarize.OTSU_SCORE_FRACTION + i);
            //        Pix imag = Binarize.sauvolaBinarizeTiled(ReadFile.readBitmap(bm));
            Float s = Skew.findSkew(imag);
            res = WriteFile.writeBitmap(Rotate.rotate(imag, s));
            recognizedText = initTessBaseApi(context, res);
            Log.d("rhere", recognizedText);
            i = i + (float) 0.1;
        } while (i < (float) 0.6 && !recognizedText.matches("(.*\\n)*.{15,30}(.*\\n*)*"));

        return res;
    }

    public static int[] findBorders(){
        int min=1000;
        HashMap<Integer, Integer> bottom = new HashMap<>();
        HashMap<Integer, Integer> top = new HashMap<>();
        ResultIterator resultIterator = baseApi.getResultIterator();
        do {
          Rect rect = resultIterator.getBoundingRect(TessBaseAPI.PageIteratorLevel.RIL_SYMBOL);

            for (Pair<String, Double> item : resultIterator.getChoicesAndConfidence(TessBaseAPI.PageIteratorLevel.RIL_SYMBOL)) {
                if (item.second > 73) {
                    if ((rect.right - rect.left) < min) {
                        min = rect.right - rect.left;
                    }
                    bottom = fillTheMap(bottom, rect.bottom);
                    top = fillTheMap(top, rect.top);
                }
            }
        } while (resultIterator.next(TessBaseAPI.PageIteratorLevel.RIL_SYMBOL));
        int pogr = (int) (0.6 * (findMostFrequentItem(top) - (findMostFrequentItem(bottom))));
        return new int[]  {findMostFrequentItem(top)+pogr,findMostFrequentItem(bottom)-pogr, min};

//        return new Pair<Integer, Integer>(2*findMostFrequentItem(top), 2*findMostFrequentItem(bottom));

    }

    public static CheckData improve (int[] borders, Bitmap bm, Context context){
        List<Symbol> symbols = new ArrayList();
        ResultIterator resultIterator = baseApi.getResultIterator();
        String recognizedText="";
        int right=0;
        Log.d("borders", ""+borders[0]+" "+borders[1]);
        Rect temp =new Rect(0,0,0,0);
        do {
            Rect rect = resultIterator.getBoundingRect(TessBaseAPI.PageIteratorLevel.RIL_SYMBOL);
            List<Pair<String, Double>> choicesAndConf = resultIterator.getChoicesAndConfidence(TessBaseAPI.PageIteratorLevel.RIL_SYMBOL);
            Symbol symbol = new Symbol();
               for (Pair<String, Double> item : resultIterator.getChoicesAndConfidence(TessBaseAPI.PageIteratorLevel.RIL_SYMBOL)) {
                if (rect.bottom<borders[1] && rect.top>borders[0]) {
                    for (int i = 0; i < choicesAndConf.size(); i++) {
                        if (rect.left <= right) {
                            continue;
                        } else {
                            right = rect.right;
                        }

                        if (rect.right - rect.left < borders[2] && temp.left == 0) {
                            temp = rect;
                            continue;
                        } else if (rect.right - rect.left >= borders[2]) {
                            symbol.symbol = item.first;
                            symbol.choicesAndConf = choicesAndConf;
                            symbol.rect = rect;
                            symbols.add(symbol);
                        }
                        else {
                            if (rect.top < temp.top) {
                                temp.top = rect.top;
                            }
                            if (rect.bottom > temp.bottom) {
                                temp.bottom = rect.bottom;
                            }
                            temp.right = rect.right;
                            TessBaseAPI tempBaseApi = new TessBaseAPI();
                            tempBaseApi.init(getCacheDir(context).getAbsolutePath(), "mcr");
                            tempBaseApi.setPageSegMode(TessBaseAPI.PageSegMode.PSM_SINGLE_CHAR);
                            tempBaseApi.setImage(bm);
                            tempBaseApi.setRectangle(temp);
                            String s=tempBaseApi.getUTF8Text();
                            Log.d("s", s);
                            if (s.matches(".*?c.*?")) {
                                symbol.symbol = "c";
                            }
                            else if (s.matches(".*?d.*?")){
                                symbol.symbol="d";
                            }
                            else{
                                symbol.symbol="a";
                            }
                            symbol.choicesAndConf=resultIterator.getChoicesAndConfidence(TessBaseAPI.PageIteratorLevel.RIL_SYMBOL);
                            symbol.rect=temp;
                            symbols.add(symbol);
                            tempBaseApi.end();
                            temp.left = 0;
                        }
                        recognizedText=recognizedText+symbol.symbol;
                        Log.d("conflog ", "" + symbol.choicesAndConf.get(i).second + "; symbol1 " + symbol.choicesAndConf.get(i).first +
                        "; left " + symbol.rect.left + "; right" + symbol.rect.right + " widh " +
                            (symbol.rect.right - symbol.rect.left)+" top,bottom: "+symbol.rect.top+" , "+symbol.rect.bottom);

                    }
                }
            }
        } while (resultIterator.next(TessBaseAPI.PageIteratorLevel.RIL_SYMBOL));

        return new CheckData(bm,recognizedText,symbols);
    }

    public static Bitmap drawRecText(Bitmap bm, Float scale, List<Symbol> symbols) {
        Canvas canvas = new Canvas(bm);
        float prevRight = 0;
        float prevBottom = 0;
        String text = "";
        Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        textPaint.setColor(Color.rgb(0xff, 0, 0));
        Log.d("xxx","xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx");
        for (Symbol symbol : symbols) {
            for (int i = 0; i < symbol.choicesAndConf.size(); i++) {
                textPaint.setTextSize((int) (symbol.rect.height() * scale / 2));
                text = text + symbol.choicesAndConf.get(i).first;

                canvas.drawText(symbol.choicesAndConf.get(i).first, symbol.rect.left, symbol.rect.top - i * 200, textPaint);

                String conf = String.format(Locale.ENGLISH, "%02.0f", symbol.choicesAndConf.get(i).second);
                Paint confPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
                confPaint.setColor(Color.rgb(0, 0, 255));
                confPaint.setTextSize((int) (symbol.rect.height() * scale / 4));
                confPaint.setShadowLayer(1f, 0f, 1f, Color.WHITE);
//                Log.d("conflog ", "" + symbol.choicesAndConf.get(i).second + "; symbol1 " + symbol.choicesAndConf.get(i).first +
//                        "; left " + symbol.rect.left + "; right" + symbol.rect.right + " widh " +
//                        (symbol.rect.right - symbol.rect.left)+" top,bottom: "+symbol.rect.top+" , "+symbol.rect.bottom);

                canvas.drawText(conf, symbol.rect.left, symbol.rect.top - symbol.rect.height() * (i + 1), confPaint);

                Paint borderRectPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
                borderRectPaint.setColor(Color.rgb(0, 0, 0xff));
                if (Math.abs(symbol.rect.bottom - prevBottom) > 20) {
                    prevRight = 0;
                }
                canvas.drawRect(prevRight, symbol.rect.bottom, symbol.rect.right, symbol.rect.bottom + 3, borderRectPaint);
                prevRight = symbol.rect.right;
                prevBottom = symbol.rect.bottom;
            }
        }
        return bm;
    }

}
