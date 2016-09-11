package com.example.npakudin.testocr.micr;

import android.content.Context;
import android.graphics.Bitmap;
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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;


public class MicrRecognizer {

    private static final String LOGTAG = "TextRecognizer";

    public static CheckData recognize(Bitmap bm) {

        return recognizeCycle(bm);
    }

    private static CheckData recognizeCycle(Bitmap bm) {

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

    private static CheckData recognize(Pix pix) {

        Pix savuolaBinarized = Binarize.sauvolaBinarizeTiled(pix);
        Bitmap bitmap = unskew(savuolaBinarized);
        List<Symbol> symbols = recognizeSymbols(bitmap);
        MicrInfo micrInfo = findBorders(symbols);

        if (micrInfo.inLineRecognized < 20) {

            //if there is possibility gradient background prevent some symbols to be clear-
            //try another type of binarization

            Pix otsuBinarized = Binarize.otsuAdaptiveThreshold(pix, 3000, 3000, 3 * Binarize.OTSU_SMOOTH_X,
                    3 * Binarize.OTSU_SMOOTH_Y, Binarize.OTSU_SCORE_FRACTION);
            Bitmap otsuBitmap = unskew(otsuBinarized);
            List<Symbol> otsuSymbols = recognizeSymbols(otsuBitmap);
            MicrInfo otsuMicrInfo = findBorders(otsuSymbols);

            if (micrInfo.inLineRecognized < otsuMicrInfo.inLineRecognized) {

                //compare the results from two binarizations and choose the best
                bitmap = otsuBitmap;
                symbols = otsuSymbols;
                micrInfo = otsuMicrInfo;
            }
        }
        List<Symbol> filteredSymbols = MicrRecognizer.filterTheline(symbols, micrInfo);
        CheckData checkData = MicrRecognizer.joinThinSymbols(bitmap, filteredSymbols, micrInfo);
        return checkData;
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
        HashMap<Integer, Integer> bottomFrequencies = new HashMap<>();
        HashMap<Integer, Integer> topFrequencies = new HashMap<>();

        for (Symbol rawSymbol : symbols) {
            Rect rect = rawSymbol.rect;

            if (rawSymbol.сonfidence > 70) {
                if ((rect.width()) < min) {
                    min = rect.width();
                }

                Integer bottomNewFrequency = bottomFrequencies.get(rect.bottom);
                bottomFrequencies.put(rect.bottom, 1 + (bottomNewFrequency == null ? 0 : bottomNewFrequency));

                Integer topNewFrequency = bottomFrequencies.get(rect.bottom);
                bottomFrequencies.put(rect.bottom, 1 + (topNewFrequency == null ? 0 : topNewFrequency));
            }
        }
        int topBorder = CalcUtils.findMostFrequentItem(topFrequencies);
        int bottomBorder = CalcUtils.findMostFrequentItem(bottomFrequencies);
        int inLineRecognized = CalcUtils.findTheLine(topFrequencies, topBorder, 5); // border = 5
        int tolerance = (int) (0.6 * (topBorder - bottomBorder));
        return new MicrInfo(topBorder + tolerance, bottomBorder - tolerance, min, inLineRecognized);
    }

    /**
     * Remove overlapping symbols
     *
     */
    public static List<Symbol> filterTheline(List<Symbol> symbols, MicrInfo micrInfo) {

        int right = 0;
        List<Symbol> filteredSymbols = new ArrayList<>();
        for (Symbol symbol : symbols) {

            Rect rect = symbol.rect;
            if (micrInfo.isInside(rect) && symbol.сonfidence > 20) {
                //if the rectangle starts before the last one ends- throw it away
                if (rect.left > right) {
                    //otherwise remember when it ends for future comparing
                    right = rect.right;
                    filteredSymbols.add(symbol);
                }
            }
        }
        return filteredSymbols;
    }

    /**
     * Joins symbols if there are 2 or 3 symbols in a row are too thin
     * Then tries to recognize again
     *
     */
    public static CheckData joinThinSymbols(Bitmap bm, List<Symbol> rawSymbols, MicrInfo micrInfo) {

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
                    (!Objects.equals(rawSymbol.symbol, "1") && rawSymbol.сonfidence < 60)) {
                //if we dont have first part of letter in oneCharRect and the width of the symbol says that that it is here
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



    private static String mcrFilePath = null;

    public static void init(Context context) {
        mcrFilePath = Asset2File.init(context);
    }

    public static TessBaseAPI createTessBaseApi() {
        TessBaseAPI baseApi = new TessBaseAPI();
        baseApi.init(mcrFilePath, "mcr");
        return baseApi;
    }
}
