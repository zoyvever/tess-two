package com.example.npakudin.testocr;

import android.content.Context;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
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

    public static CheckData recognize(Bitmap bm) {
        double threshold = 0;

        Bitmap res = null;
        TextRec.MicrInfo micrInfo = null;
        List<TextRec.Symbol> rawRecognize = null;
        do {
            res = prepareImage(bm, threshold);
            rawRecognize = rawRecognize(res);
            micrInfo = findBorders(rawRecognize);
            threshold = threshold + 0.1;
        } while (threshold < 0.5 && micrInfo.inLineRecognized < 8);

        rawRecognize = TextRec.filterTheline(rawRecognize, micrInfo);
        CheckData checkData = TextRec.improve(res, rawRecognize, micrInfo);
        return checkData;
    }

    public static class Symbol {
        public String symbol;
        public double сonfidence;
        public Rect rect;

    }

    public static class MicrInfo {
        public int top = 0;
        public int bottom = 0;
        public int minimumCharWidth = 0;
        public int inLineRecognized = 0;

        public MicrInfo(int top, int bottom, int minimumCharWidth, int inLineRecognized) {
            this.top = top;
            this.bottom = bottom;
            this.minimumCharWidth = minimumCharWidth;
            this.inLineRecognized = inLineRecognized;
        }
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

    private static HashMap<Integer, Integer> fillTheMap(HashMap<Integer, Integer> map, Integer rect) {
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

    public static int findTheLine(HashMap<Integer, Integer> map, int mostFrequentItem) {
        int cuantityOfRecognizedItems = 0;
        for (HashMap.Entry<Integer, Integer> entry : map.entrySet()) {
            if (entry.getKey() > mostFrequentItem - 5 && entry.getKey() < mostFrequentItem + 5) {
                cuantityOfRecognizedItems = cuantityOfRecognizedItems + entry.getValue();
            }
        }
        Log.d(LOGTAG, "quantity of recognized items in line: " + cuantityOfRecognizedItems);
        return cuantityOfRecognizedItems;
    }

    private static String mcrFilePath = null;

    public static File init(Context context) {
        File baseDir = getCacheDir(context);
        if (baseDir == null) {
            throw new IllegalStateException("Cannot access temporary dir");
        }
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
                    outputStream.close();
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

        mcrFilePath = baseDir.getAbsolutePath();
        return baseDir;
    }

    public static TessBaseAPI createTessBaseApi() {
        TessBaseAPI baseApi = new TessBaseAPI();
        baseApi.init(mcrFilePath, "mcr");
        return baseApi;
    }

    public static List<Symbol> rawRecognize(Bitmap bm) {
        TessBaseAPI baseApi = createTessBaseApi();
        baseApi.setImage(bm);

        List<Symbol> symbols = new ArrayList<>();

        if (baseApi.getUTF8Text().trim().length() > 0) {
            // in other case app fails on getting iterator on empty image

            ResultIterator resultIterator = baseApi.getResultIterator();

            do {
                Rect rect = resultIterator.getBoundingRect(TessBaseAPI.PageIteratorLevel.RIL_SYMBOL);
                List<Pair<String, Double>> choicesAndConf = resultIterator.getChoicesAndConfidence(TessBaseAPI.PageIteratorLevel.RIL_SYMBOL);
                Symbol symbol = new Symbol();
                symbol.symbol = choicesAndConf.get(0).first;
                symbol.сonfidence = choicesAndConf.get(0).second;
                symbol.rect = rect;
                symbols.add(symbol);
            } while (resultIterator.next(TessBaseAPI.PageIteratorLevel.RIL_SYMBOL));
        }
        return symbols;
    }

    public static Bitmap prepareImage(Bitmap bm, double threshold) {
        Bitmap res;
        Pix imag = Binarize.otsuAdaptiveThreshold(ReadFile.readBitmap(bm),
                3000, 3000,
                3 * Binarize.OTSU_SMOOTH_X, 3 * Binarize.OTSU_SMOOTH_Y,
                Binarize.OTSU_SCORE_FRACTION + (float) threshold);
        Float s = Skew.findSkew(imag);
        res = WriteFile.writeBitmap(Rotate.rotate(imag, s));

        return res;
    }

    public static MicrInfo findBorders(List<Symbol> rawSymbols) {
        int min = 1000;
        HashMap<Integer, Integer> bottom = new HashMap<>();
        HashMap<Integer, Integer> top = new HashMap<>();

        for (Symbol rawSymbol : rawSymbols) {
            Rect rect = rawSymbol.rect;

            if (rawSymbol.сonfidence > 70) {
                if ((rect.right - rect.left) < min) {
                    min = rect.right - rect.left;
                }
                bottom = fillTheMap(bottom, rect.bottom);
                top = fillTheMap(top, rect.top);
            }
        }
        int topBorder = findMostFrequentItem(top);
        int bottomBorder = findMostFrequentItem(bottom);
        int inLineRecognized = findTheLine(top, topBorder);
        int pogr = (int) (0.8 * (topBorder - bottomBorder));
        return new MicrInfo(topBorder + pogr, bottomBorder - pogr, min, inLineRecognized);
    }

    public static List<Symbol> filterTheline(List<Symbol> rawSymbols, MicrInfo micrInfo) {
        int right = 0;
        List<Symbol> symbols = new ArrayList<>();
        for (Symbol rawSymbol : rawSymbols) {
            Rect rect = rawSymbol.rect;
            Symbol symbol;
            if (rect.bottom < micrInfo.bottom && rect.top > micrInfo.top) {

                if (rect.left <= right) {
                    //if the rectangle starts before the last one ends- throw it away
                    continue;
                } else {
                    //otherwise remember when it ends for future comparing
                    right = rect.right;
                    symbol = rawSymbol;
                    symbols.add(symbol);
                }
            }
        }
        return symbols;
    }

    public static CheckData improve(Bitmap bm, List<Symbol> rawSymbols, MicrInfo micrInfo) {
        StringBuilder builder = new StringBuilder();
        TessBaseAPI singleCharRecognition = createTessBaseApi();
        singleCharRecognition.setPageSegMode(TessBaseAPI.PageSegMode.PSM_SINGLE_CHAR);
        singleCharRecognition.setImage(bm);
        List<Symbol> symbols = new ArrayList<>();
        Rect oneCharRect = null;

        for (Symbol rawSymbol : rawSymbols) {
            Symbol symbol = new Symbol();
            if (oneCharRect != null) {
                //if we already have first part of unrecognized letter then
                if (rawSymbol.rect.top < oneCharRect.top) {
                    oneCharRect.top = rawSymbol.rect.top;
                    //use lowest top
                }
                if (rawSymbol.rect.bottom > oneCharRect.bottom) {
                    oneCharRect.bottom = rawSymbol.rect.bottom;
                    //use biggest bottom
                }
                oneCharRect.right = rawSymbol.rect.right;
                //use value for 'right' from the last rect
                singleCharRecognition.setRectangle(oneCharRect);
                String s = singleCharRecognition.getUTF8Text();
                Log.d("s", s);
                if (s.trim().length() > 0) {
                    if (s.matches(".*\\d|a.*")) {
                        symbol.symbol = "a";
                    } else {
                        symbol.symbol = s;
                    }
                    symbol.сonfidence = singleCharRecognition.getResultIterator().getChoicesAndConfidence(TessBaseAPI.PageIteratorLevel.RIL_SYMBOL).get(0).second;
                } else {
                    symbol.symbol = "a";
                    symbol.сonfidence = 0.0;
                }
                symbol.rect = oneCharRect;
                oneCharRect = null;
            } else if (rawSymbol.rect.width() < micrInfo.minimumCharWidth) {
                //if we dont have first part of letter in oneCharRect and the width of the symbl says that that it is here
                oneCharRect = rawSymbol.rect;
                continue;
            } else {
                //if everything is normal
                symbol = rawSymbol;
            }
            builder.append(symbol.symbol);
            symbols.add(symbol);
        }

        return new CheckData(bm, builder.toString(), symbols);
    }

    public static Bitmap drawRecText(Bitmap bm, Float scale, List<Symbol> symbols) {
        Canvas canvas = new Canvas(bm);
        float prevBottom = 0;
        String text = "";
        Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        textPaint.setColor(Color.rgb(0xff, 0, 0));
        for (Symbol symbol : symbols) {
            textPaint.setTextSize((int) (symbol.rect.height() * scale / 1.5));
            text = text + symbol.symbol;
            canvas.drawText(symbol.symbol, symbol.rect.left, symbol.rect.top - (symbol.rect.height() + 10), textPaint);

            String conf = String.format(Locale.ENGLISH, "%02.0f", symbol.сonfidence);
            Paint confPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            confPaint.setColor(Color.rgb(0, 0, 255));
            confPaint.setTextSize((int) (symbol.rect.height() * scale / 3));
            canvas.drawText(conf, symbol.rect.left, symbol.rect.top - (symbol.rect.height() - 10), confPaint);

            Paint borderRectPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            borderRectPaint.setColor(Color.rgb(0, 0, 0xff));
            borderRectPaint.setStyle(Paint.Style.STROKE);
            if (Math.abs(symbol.rect.bottom - prevBottom) > 20) {
            }
            canvas.drawRect(symbol.rect, borderRectPaint);
            prevBottom = symbol.rect.bottom;

        }
        return bm;
    }

}
