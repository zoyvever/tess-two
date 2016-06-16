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

    public static class Symbol {
        public String symbol;
        public List<Pair<String, Double>> choicesAndConf;
        public Rect rect;
    }

    public static class MicrInfo {
        public int top = 0;
        public int bottom = 0;
        public int minimumCharRect = 0;

        public MicrInfo(int top, int bottom, int minimumCharWidth) {
            this.top = top;
            this.bottom = bottom;
            this.minimumCharRect = minimumCharWidth;
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

    private static String mcrFilePath = null;
    public static File init(Context context) {
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
                for (Pair<String, Double> item : resultIterator.getChoicesAndConfidence(TessBaseAPI.PageIteratorLevel.RIL_SYMBOL)) {

                    Symbol symbol = new Symbol();
                    symbol.symbol = item.first;
                    symbol.choicesAndConf = choicesAndConf;
                    symbol.rect = rect;
                    symbols.add(symbol);
                }
            } while (resultIterator.next(TessBaseAPI.PageIteratorLevel.RIL_SYMBOL));
        }
        return symbols;
    }

    public static Bitmap prepareImage(Bitmap bm) {
        float threshold = 0;
        TessBaseAPI baseApi = createTessBaseApi();
        String recognizedText;
        Bitmap res;
        do {
            Pix imag = Binarize.otsuAdaptiveThreshold(ReadFile.readBitmap(bm),
                    3000, 3000,
                    3 * Binarize.OTSU_SMOOTH_X, 3 * Binarize.OTSU_SMOOTH_Y,
                    Binarize.OTSU_SCORE_FRACTION + threshold);

            Float s = Skew.findSkew(imag);
            res = WriteFile.writeBitmap(Rotate.rotate(imag, s));
            baseApi.setImage(res);
            recognizedText = baseApi.getUTF8Text();
            Log.d("rhere", "" + recognizedText);
            threshold = threshold + (float) 0.1;
        } while (threshold < (float) 0.6 && !recognizedText.matches("(.*\\n)*.{15,30}(.*\\n*)*"));

        return res;
    }

    public static MicrInfo findBorders(List<Symbol> rawSymbols) {
        int min = 1000;
        HashMap<Integer, Integer> bottom = new HashMap<>();
        HashMap<Integer, Integer> top = new HashMap<>();

        for (Symbol rawSymbol: rawSymbols) {
            Rect rect = rawSymbol.rect;

            for (Pair<String, Double> item : rawSymbol.choicesAndConf) {
                if (item.second > 73) {
                    if ((rect.right - rect.left) < min) {
                        min = rect.right - rect.left;
                    }
                    bottom = fillTheMap(bottom, rect.bottom);
                    top = fillTheMap(top, rect.top);
                }
            }
        }
        int pogr = (int) (0.6 * (findMostFrequentItem(top) - (findMostFrequentItem(bottom))));
        return new MicrInfo(findMostFrequentItem(top) + pogr, findMostFrequentItem(bottom) - pogr, min);
    }

    public static CheckData improve(MicrInfo micrInfo, Bitmap bm, List<Symbol> rawSymbols) {
        List<Symbol> symbols = new ArrayList<>();

        TessBaseAPI singleCharRecognitiion = createTessBaseApi();
        singleCharRecognitiion.setPageSegMode(TessBaseAPI.PageSegMode.PSM_SINGLE_CHAR);
        singleCharRecognitiion.setImage(bm);

        String recognizedText = "";
        int right = 0;
        Rect oneCharRect = new Rect(0, 0, 0, 0);
        for (Symbol rawSymbol: rawSymbols) {
            Rect rect = rawSymbol.rect;
            List<Pair<String, Double>> choicesAndConf = rawSymbol.choicesAndConf;
            Symbol symbol = new Symbol();
            for (Pair<String, Double> item : rawSymbol.choicesAndConf) {
                if (rect.bottom < micrInfo.bottom && rect.top > micrInfo.top) {
                    if (rect.left <= right) {
                        continue;
                    } else {
                        right = rect.right;
                    }

                    if (rect.right - rect.left < micrInfo.minimumCharRect && oneCharRect.left == 0) {
                        oneCharRect = rect;
                        continue;
                    } else if (rect.right - rect.left >= micrInfo.minimumCharRect) {
                        symbol.symbol = item.first;
                        symbol.choicesAndConf = choicesAndConf;
                        symbol.rect = rect;
                        symbols.add(symbol);
                    } else {
                        if (rect.top < oneCharRect.top) {
                            oneCharRect.top = rect.top;
                        }
                        if (rect.bottom > oneCharRect.bottom) {
                            oneCharRect.bottom = rect.bottom;
                        }
                        oneCharRect.right = rect.right;
                        singleCharRecognitiion.setRectangle(oneCharRect);
                        String s = singleCharRecognitiion.getUTF8Text();
                        Log.d("s", s);
                        if (s.trim().length() > 0) {
                            if (s.matches(".*c.*")) {
                                symbol.symbol = "c";
                            } else if (s.matches(".*d.*")) {
                                symbol.symbol = "d";
                            } else if (s.matches(".*b.*")) {
                                symbol.symbol = "b";
                            } else {
                                symbol.symbol = "a";
                            }
                            symbol.choicesAndConf = singleCharRecognitiion.getResultIterator().getChoicesAndConfidence(TessBaseAPI.PageIteratorLevel.RIL_SYMBOL);
                        } else {
                            Log.d(LOGTAG, "Cannot recognize single char at position of " + rawSymbol.symbol);
                            symbol.symbol = "-";
                            symbol.choicesAndConf = new ArrayList<>();
                            symbol.choicesAndConf.add(new Pair<>("-", 0.0));
                        }
                        symbol.rect = oneCharRect;
                        symbols.add(symbol);
                        oneCharRect.left = 0;
                    }
                    recognizedText = recognizedText + symbol.symbol;
                    Log.d("conflog ", "" + symbol.choicesAndConf.get(0).second + "; symbol1 " + symbol.choicesAndConf.get(0).first +
                            "; left " + symbol.rect.left + "; right" + symbol.rect.right + " widh " +
                            (symbol.rect.right - symbol.rect.left) + " top,bottom: " + symbol.rect.top + " , " + symbol.rect.bottom);

                }
            }
        }

        return new CheckData(bm, recognizedText, symbols);
    }

    public static Bitmap drawRecText(Bitmap bm, Float scale, List<Symbol> symbols) {
        Canvas canvas = new Canvas(bm);
        float prevRight = 0;
        float prevBottom = 0;
        String text = "";
        Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        textPaint.setColor(Color.rgb(0xff, 0, 0));
        Log.d("xxx", "xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx");
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
