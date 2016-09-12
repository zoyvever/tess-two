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
        CheckData checkData = innerRecognize(pix);

        return checkData;
//        if (checkData.confidence > 70) {
//            return checkData;
//        }
//        Log.w(LOGTAG, "checkData.confidence <= 70 : " + checkData.confidence);
//        if (checkData.confidence > 40) {
//            Bitmap cropped = cropBitmap(bm, checkData);
//            return innerRecognize(ReadFile.readBitmap(cropped));
//        }
//        Log.w(LOGTAG, "checkData.confidence <= 40 : " + checkData.confidence);
//        Bitmap scaled = WriteFile.writeBitmap(Scale.scale(pix, (float) 0.8));
//        CheckData res = innerRecognize(ReadFile.readBitmap(scaled));
//        return res;
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
        int height=(bottom-top);
        int y = Math.max(0, top-height);
        return Bitmap.createBitmap(bm, 1, y, bm.getWidth() - 1, Math.min(bm.getHeight() - y, 2*height));
    }

    private static CheckData innerRecognize(Pix pix) {

        int[] windowsSizes = new int[] { 32, 16, };
        float[] thresholds = new float[] { 0.35f, 0.20f, 0.80f, 0.50f };

        CheckData bestCheckData = null;
        for (int windowsSize : windowsSizes) {
            for (float threshold : thresholds) {

                Pix savuolaBinarized = Binarize.sauvolaBinarizeTiled(pix,
                        windowsSize, threshold,
                        Binarize.SAUVOLA_DEFAULT_NUM_TILES_X, Binarize.SAUVOLA_DEFAULT_NUM_TILES_Y);
                Bitmap bitmap = unskew(savuolaBinarized);
                List<Symbol> symbols = rawRecognize(bitmap);
                MicrInfo micrInfo = findBorders(symbols);

                List<Symbol> filteredSymbols = MicrRecognizer.filterTheline(symbols, micrInfo);
                CheckData checkData = MicrRecognizer.joinThinSymbols(bitmap, filteredSymbols, micrInfo);

                checkData.descr = "" + windowsSize + " - " + threshold;

                if (checkData.isOk) {
                    return checkData;
                }
                if (bestCheckData == null || bestCheckData.confidence < checkData.confidence) {
                    bestCheckData = checkData;
                }
            }
        }
        return bestCheckData;
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
                symbol.confidence = choicesAndConf.get(0).second;
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

        int minWidth = 1000;

        HashMap<Integer, Integer> bottomFrequencies = new HashMap<>();
        HashMap<Integer, Integer> topFrequencies = new HashMap<>();
        HashMap<Integer, Integer> heightFrequencies = new HashMap<>();
        HashMap<Integer, Integer> widthFrequencies = new HashMap<>();

        for (Symbol rawSymbol : symbols) {
            Rect rect = rawSymbol.rect;

            if (rawSymbol.confidence > 70) {
                if ((rect.width()) < minWidth) {
                    minWidth = rect.width();
                }


                CalcUtils.putFrequency(bottomFrequencies, rect.bottom);
                CalcUtils.putFrequency(topFrequencies, rect.top);
                CalcUtils.putFrequency(heightFrequencies, rect.height());
                CalcUtils.putFrequency(widthFrequencies, rect.width());
            }
        }
        int topBorder = CalcUtils.findMostFrequentItem(topFrequencies);
        int bottomBorder = CalcUtils.findMostFrequentItem(bottomFrequencies);
        int typicalWidth = CalcUtils.findMostFrequentItem(widthFrequencies);
        int typicalHeight = CalcUtils.findMostFrequentItem(heightFrequencies);
        int inLineRecognized = CalcUtils.findTheLine(topFrequencies, topBorder, 5); // border = 5
        int tolerance = (int) (0.6 * (topBorder - bottomBorder));
        return new MicrInfo(topBorder + tolerance, bottomBorder - tolerance, minWidth, typicalWidth, typicalHeight, inLineRecognized);
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
            if (micrInfo.contains(rect) && symbol.confidence > 20) {
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
     * Then tries to innerRecognize again
     *
     */
    public static CheckData joinThinSymbols(Bitmap bm, List<Symbol> rawSymbols, MicrInfo micrInfo) {

        Rect borderRect = new Rect(0, 0, bm.getWidth(), bm.getHeight());

        StringBuilder builder = new StringBuilder();
        TessBaseAPI singleCharRecognition = createTessBaseApi();
        singleCharRecognition.setPageSegMode(TessBaseAPI.PageSegMode.PSM_SINGLE_CHAR);
        singleCharRecognition.setImage(bm);

        double conf = 0;
        double minconf = 100;
        List<Symbol> symbols = new ArrayList<>();
        Rect oneCharRect = null;

        for (int i = 0; i < rawSymbols.size(); i++) {
            Symbol rawSymbol = rawSymbols.get(i);
            Symbol nextSymbol = i < rawSymbols.size() - 1 ? rawSymbols.get(i + 1) : new Symbol("", 0, new Rect(bm.getWidth(), 0, bm.getWidth(), bm.getHeight()));

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

                Rect oneCharRectWithBorders = CalcUtils.rectWithMargins(oneCharRect, 3, borderRect);

                singleCharRecognition.setRectangle(oneCharRectWithBorders);
                String s = singleCharRecognition.getUTF8Text().trim();
                Log.w("recognized string", s);
                if (s.length() > 0) {
                    if (s.matches("\\d|a")) {
                        symbol.symbol = "#";
                    } else {
                        symbol.symbol = s;
                    }
                    symbol.confidence = singleCharRecognition.getResultIterator().getChoicesAndConfidence(TessBaseAPI.PageIteratorLevel.RIL_SYMBOL).get(0).second;
                } else {
                    symbol.symbol = "%";
                    symbol.confidence = 35.0;
                    Log.w(LOGTAG, "bad recognition");
                }
                symbol.rect = oneCharRect;
                oneCharRect = null;
            } else {
                if ((rawSymbol.rect.width() < micrInfo.minimumCharWidth &&
                        !Objects.equals(rawSymbol.symbol, "1") &&
                        nextSymbol.rect.left - rawSymbol.rect.right < micrInfo.minimumCharWidth * 2 &&
                        nextSymbol.rect.right - rawSymbol.rect.left < micrInfo.typicalHeight * 1.5)
                        ||
                        (rawSymbol.rect.height() < micrInfo.typicalHeight * 0.8 &&
                        nextSymbol.rect.left - rawSymbol.rect.right < micrInfo.minimumCharWidth * 2 &&
                        nextSymbol.rect.right - rawSymbol.rect.left < micrInfo.typicalHeight * 1.5)) {
                    //if we dont have first part of letter in oneCharRect and the width of the symbol says that that it is here
                    oneCharRect = rawSymbol.rect;
                    continue;
                } else {
                    //if everything is normal
                    symbol = rawSymbol;

                    if (symbol.confidence < 50 && symbol.confidence > 0) {
                        // try to re-innerRecognize

                        Rect oneCharRectWithBorders = CalcUtils.rectWithMargins(symbol.rect, 3, borderRect);
                        singleCharRecognition.setRectangle(oneCharRectWithBorders);
                        String s = singleCharRecognition.getUTF8Text().trim();

                        if (s.length() > 0) {
                            symbol.symbol = s;
                            symbol.confidence = singleCharRecognition.getResultIterator().getChoicesAndConfidence(TessBaseAPI.PageIteratorLevel.RIL_SYMBOL).get(0).second;
                        } else {
                            symbol.symbol = "";
                            symbol.confidence = 50.0;
                            Log.w(LOGTAG, "bad recognition");
                        }
                    }
                }
            }


            // at least 35%
            if (symbol.confidence >= 35) {
                builder.append(symbol.symbol);
                symbols.add(symbol);
                conf = conf + symbol.confidence;
                if (symbol.confidence < minconf) {
                    minconf = symbol.confidence;
                }
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
