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
import com.googlecode.leptonica.android.Scale;
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


public class MicrRecognizer {

    private static final String LOGTAG = "TextRecognizer";

    public static CheckData recognize(Bitmap bm) {

        return recognizeCycle0(bm);
    }

    private static CheckData recognizeCycle0(Bitmap bm) {

        Pix pix = ReadFile.readBitmap(bm);
        CheckData checkData = recognize(pix);

        if (checkData.confidence > 70) {
            return checkData;
        }
        Log.d(LOGTAG, "checkData.confidence <= 70 : " + checkData.confidence);
        if (checkData.confidence > 40) {
            Bitmap cropped = cropBitmap(bm, checkData);
            return recognize(ReadFile.readBitmap(cropped));
        }
        Log.d(LOGTAG, "checkData.confidence <= 40 : " + checkData.confidence);
        Bitmap scaled = WriteFile.writeBitmap(Scale.scale(pix, (float) 0.8));
        CheckData res = recognize(scaled);
        return res;
    }

    private static Bitmap cropBitmap(Bitmap bm, CheckData checkData) {
        // crop image
        int top = 1000;
        int bottom = 0;
        for (Symbol symbol : checkData.symbols) {
            if (top > symbol.rect.top) {
                top = symbol.rect.top;
            }
            if (bottom < symbol.rect.bottom) {
                bottom = symbol.rect.bottom;
            }
        }
        //in case of skew take a 2 sizes of the line which allows 6 degrees skew
        int width=(bottom-top);
        return Bitmap.createBitmap(bm, 0, top-width, bm.getWidth(), 2*width);
    }

    private static CheckData recognizeCycle(Bitmap bm, int iter) {

        Pix pix = ReadFile.readBitmap(bm);
        Bitmap res;
        CheckData checkData = recognize(pix);
        Log.d("con", "conf: " + checkData.confidence+"; size: "+checkData.symbols.size());

        //if confidence is obviously lower than needed- try different ways to resize the image

        if ((checkData.confidence < 40 | checkData.symbols.size() < 6) && iter < 1) {

            //if there is not enough recognized symbols to find their borders to cut the image,
            //we try to scale it first,so binarization will have another chance to work fine

            res = WriteFile.writeBitmap(Scale.scale(pix, (float) 0.8));
            iter = 1;
            return recognizeCycle(res, iter);

        } else if (checkData.confidence < 70 && iter < 2) {

            //to crop the image we need to retrieve top and bottom

            int top = 1000;
            int bottom = 0;
            for (Symbol symbol : checkData.symbols) {
                if (top > symbol.rect.top) {
                    top = symbol.rect.top;
                }
                if (bottom < symbol.rect.bottom) {
                    bottom = symbol.rect.bottom;
                }
            }
            //in case of skew take a 2 sizes of the line which allows 6 degrees skew
            int width=(bottom-top);
            res = Bitmap.createBitmap(bm, 0, top-width, bm.getWidth(), 2*width);
            iter = 2;
            return recognizeCycle(res, iter);
        } else {
            return checkData;
        }
    }

    private static CheckData recognize(Pix pix) {

        Pix binarized = Binarize.sauvolaBinarizeTiled(pix);
        Bitmap res = unskew(binarized);
        List<Symbol> symbols = recognizeSymbols(res);
        MicrInfo micrInfo = findBorders(symbols);

        if (micrInfo.inLineRecognized < 20) {

            //if there is possibility gradient background prevent some symbols to be clear-
            //try another type of binarization

            binarized = Binarize.otsuAdaptiveThreshold(pix, 3000, 3000, 3 * Binarize.OTSU_SMOOTH_X,
                    3 * Binarize.OTSU_SMOOTH_Y, Binarize.OTSU_SCORE_FRACTION);
            Bitmap res1 = unskew(binarized);
            List<Symbol> rawRecognize1 = recognizeSymbols(res1);
            MicrInfo micrInfo1 = findBorders(rawRecognize1);
            Log.d("inline", micrInfo.inLineRecognized + ", " + micrInfo1.inLineRecognized);

            if (micrInfo.inLineRecognized < micrInfo1.inLineRecognized) {

                //compare the results from two binarizations and choose the best

                res = res1;
                symbols = rawRecognize1;
                micrInfo = micrInfo1;
            }
        }
        List<Symbol> filteredSymbols = MicrRecognizer.filterTheline(symbols, micrInfo);
        CheckData checkData = MicrRecognizer.improve(res, filteredSymbols, micrInfo);
        return checkData;
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
            if (entry.getValue() >= freq) {
                trueBorder = entry.getKey();
                freq = entry.getValue();
            }
        }
        return trueBorder;
    }

    public static int findTheLine(HashMap<Integer, Integer> map, int mostFrequentItem) {
        int quantityOfRecognizedItems = 0;
        for (HashMap.Entry<Integer, Integer> entry : map.entrySet()) {
            if (entry.getKey() > mostFrequentItem - 5 && entry.getKey() < mostFrequentItem + 5) {
                quantityOfRecognizedItems = quantityOfRecognizedItems + entry.getValue();
            }
        }
        Log.d(LOGTAG, "quantity of recognized items in line: " + quantityOfRecognizedItems);
        return quantityOfRecognizedItems;
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

    public static List<Symbol> recognizeSymbols(Bitmap bm) {
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

    public static Bitmap unskew(Pix imag) {
        Float s = Skew.findSkew(imag);
        return WriteFile.writeBitmap(Rotate.rotate(imag, s));
    }

    public static MicrInfo findBorders(List<Symbol> symbols) {
        int min = 1000;
        HashMap<Integer, Integer> bottom = new HashMap<>();
        HashMap<Integer, Integer> top = new HashMap<>();

        for (Symbol rawSymbol : symbols) {
            Rect rect = rawSymbol.rect;

            if (rawSymbol.сonfidence > 70) {
                if ((rect.width()) < min) {
                    min = rect.width();
                }
                bottom = fillTheMap(bottom, rect.bottom);
                top = fillTheMap(top, rect.top);
            }
        }
        int topBorder = findMostFrequentItem(top);
        int bottomBorder = findMostFrequentItem(bottom);
        int inLineRecognized = findTheLine(top, topBorder);
        int pogr = (int) (0.6 * (topBorder - bottomBorder));
        return new MicrInfo(topBorder + pogr, bottomBorder - pogr, min, inLineRecognized);
    }

    public static List<Symbol> filterTheline(List<Symbol> rawSymbols, MicrInfo micrInfo) {
        int right = 0;
        List<Symbol> symbols = new ArrayList<>();
        Log.d(LOGTAG, "top: " + micrInfo.top + "; bottom: " + micrInfo.bottom + "; min: " + micrInfo.minimumCharWidth + "; recognized in Line: " + micrInfo.inLineRecognized);
        for (Symbol rawSymbol : rawSymbols) {
//
//            Log.d("filtrConflog ", "" + rawSymbol.сonfidence + "; symbol1 " + rawSymbol.symbol +
//                    "; left " + rawSymbol.rect.left + "; right" + rawSymbol.rect.right + " widh " + rawSymbol.rect.width()
//                    + " top,bottom: " + rawSymbol.rect.top + " , " + rawSymbol.rect.bottom);

            Rect rect = rawSymbol.rect;
            Symbol symbol;
            if ((rect.bottom < micrInfo.bottom && rect.top > micrInfo.top) && rawSymbol.сonfidence > 20) {

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
        double conf = 0;
        double minconf = 100;
        List<Symbol> symbols = new ArrayList<>();
        Rect oneCharRect = null;
        for (Symbol rawSymbol : rawSymbols) {
            Symbol symbol = new Symbol();
            if (oneCharRect != null) {
                if (oneCharRect.width() + rawSymbol.rect.width() <= micrInfo.minimumCharWidth) {
                    //in case if tle letter divided in three parts
                    oneCharRect.right = rawSymbol.rect.right;
                    continue;
                }
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
                Log.d("recognized string", s);
                if (s.trim().length() > 0) {
                    if (s.matches("\\s*\\d|a\\s*")) {
                        symbol.symbol = "a";
                    } else {
                        symbol.symbol = s;
                    }
                    symbol.сonfidence = singleCharRecognition.getResultIterator().getChoicesAndConfidence(TessBaseAPI.PageIteratorLevel.RIL_SYMBOL).get(0).second;
                } else {
                    symbol.symbol = "a";
                    symbol.сonfidence = 40.0;
                    Log.d(LOGTAG, "bad recognition");
                }
                symbol.rect = oneCharRect;
                oneCharRect = null;
            } else if ((rawSymbol.rect.width() < micrInfo.minimumCharWidth) &&
                    (rawSymbol.symbol != "1" && rawSymbol.сonfidence < 60)) {
                //if we dont have first part of letter in oneCharRect and the width of the symbl says that that it is here
                oneCharRect = rawSymbol.rect;
                continue;
            } else {
                //if everything is normal
                symbol = rawSymbol;
            }
            builder.append(symbol.symbol);
            symbols.add(symbol);
            conf = conf + symbol.сonfidence;
            if (symbol.сonfidence < minconf) {
                minconf = symbol.сonfidence;
            }

        }

        return new CheckData(bm, builder.toString(), symbols, conf / symbols.size());
    }

}
