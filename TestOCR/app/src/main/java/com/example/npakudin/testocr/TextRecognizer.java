package com.example.npakudin.testocr;

import android.content.Context;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
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
import java.util.List;
import java.util.Locale;

public class TextRecognizer {

    private static final String LOGTAG = "TextRecognizer";


    private static TessBaseAPI baseApi = null;
    private static void initTessBaseApi(Context context){
        if (baseApi == null) {
            File baseDir = createMicrTessData(context);

            baseApi = new TessBaseAPI();
            baseApi.init(baseDir.getAbsolutePath(), "mcr");
        }
    }

    public static CheckData recognize(Context context, Bitmap bm) {
        try {
            initTessBaseApi(context);
            baseApi.setImage(bm);

            // set recognition rect
            //Rect ligature = new Rect(780, 700, 1080, 810);
            //baseApi.setRectangle(ligature);

            String recognizedText = baseApi.getUTF8Text();
            Log.w(LOGTAG, "recognizedText: " + recognizedText);


            float scale = context.getResources().getDisplayMetrics().density;


            Canvas canvas = new Canvas(bm);

            List<String> parsed = new ArrayList<String>();
            if (recognizedText.trim().length() > 0) {

                List<Symbol> symbols = new ArrayList<Symbol>();
                {
                    ResultIterator resultIterator = baseApi.getResultIterator();
                    do {
                        Rect rect = resultIterator.getBoundingRect(TessBaseAPI.PageIteratorLevel.RIL_SYMBOL);
                        List<Pair<String, Double>> choicesAndConf = resultIterator.getChoicesAndConfidence(TessBaseAPI.PageIteratorLevel.RIL_SYMBOL);

                        Symbol symbol = new Symbol();
                        for (Pair<String, Double> item : resultIterator.getChoicesAndConfidence(TessBaseAPI.PageIteratorLevel.RIL_SYMBOL)) {
                            symbol.symbol = item.first;
                            symbol.confidence = item.second;
                            symbol.choicesAndConf = choicesAndConf;
                            symbol.rect = rect;
                        }
                        symbols.add(symbol);
                    } while (resultIterator.next(TessBaseAPI.PageIteratorLevel.RIL_SYMBOL));
                }

                float prevRight = 0;
                float prevBottom = 0;
                for (Symbol symbol : symbols) {

                    for (int i=0; i<symbol.choicesAndConf.size(); i++) {

                        Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
                        textPaint.setColor(Color.rgb(0xff, 0, 0));
                        textPaint.setTextSize((int) (symbol.rect.height() * scale / 2));
                        textPaint.setShadowLayer(1f, 0f, 1f, Color.WHITE);

                        canvas.drawText(symbol.choicesAndConf.get(i).first, symbol.rect.left, symbol.rect.top - i * 200, textPaint);


                        String conf = String.format(Locale.ENGLISH, "%02.0f", symbol.choicesAndConf.get(i).second);
                        double confColor = 255 - (symbol.confidence.doubleValue() * 155);

                        Paint confPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
                        confPaint.setColor(Color.rgb(0, 0, (int) confColor));
                        confPaint.setTextSize((int) (symbol.rect.height() * scale / 4));
                        confPaint.setShadowLayer(1f, 0f, 1f, Color.WHITE);

                        canvas.drawText(conf, symbol.rect.left, symbol.rect.top - 120 - 200 * i, confPaint);
                    }

                    Paint borderRectPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
                    borderRectPaint.setColor(Color.rgb(0, 0, 0xff));

                    if (Math.abs(symbol.rect.bottom - prevBottom) > 20) {
                        // new line
                        prevRight = 0;
                    }
                    canvas.drawRect(prevRight, symbol.rect.bottom, symbol.rect.right, symbol.rect.bottom + 3, borderRectPaint);
                    prevRight = symbol.rect.right;
                    prevBottom = symbol.rect.bottom;
                }

            }
            Paint borderRectPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            borderRectPaint.setColor(Color.rgb(0, 0xff, 0xff));
            //canvas.drawRect(ligature, borderRectPaint);

            CheckData checkData = new CheckData();
            checkData.accountNumber = parsed.size() > 0 ? parsed.get(0) : "UNRECOGNIZED";
            checkData.routingNumber = parsed.size() > 1 ? parsed.get(1) : "UNRECOGNIZED";
            checkData.checkNumber   = parsed.size() > 2 ? parsed.get(2) : "UNRECOGNIZED";

            return checkData;

        } catch (Exception e) {
            Log.w(LOGTAG, "onCreate", e);
            return null;
        }
    }

    public static class Symbol {
        public String symbol;
        public Double confidence;

        public List<Pair<String, Double>> choicesAndConf;
        public Rect rect;
    }


    @NonNull
    public static Bitmap prepareImageForOcr(Bitmap bm, int threshold) {
        //binarize and find skew
        Pix imag = Binarize.otsuAdaptiveThreshold(ReadFile.readBitmap(bm),
                3000, 3000,
                3 * Binarize.OTSU_SMOOTH_X, 3 * Binarize.OTSU_SMOOTH_Y,
                Binarize.OTSU_SCORE_FRACTION);
        Float s = Skew.findSkew(imag);
        Log.d(LOGTAG, s.toString());
        return WriteFile.writeBitmap(Rotate.rotate(imag,s));

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

    public static File getCacheDir(Context context)
    {
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
        }
        else {
            return null;
        }
    }

    public static class CheckData {
        public String raw;
        public String rawConfLevels;


        public String routingNumber;
        public String accountNumber;
        public String checkNumber;
    }
}
