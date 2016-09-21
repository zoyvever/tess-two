package com.example.npakudin.testocr.recognition;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.util.Log;
import android.util.Pair;

import com.example.npakudin.testocr.micr.CalcUtils;
import com.googlecode.leptonica.android.Binarize;
import com.googlecode.leptonica.android.Pix;
import com.googlecode.leptonica.android.ReadFile;
import com.googlecode.leptonica.android.Rotate;
import com.googlecode.leptonica.android.Skew;
import com.googlecode.leptonica.android.WriteFile;
import com.googlecode.tesseract.android.ResultIterator;
import com.googlecode.tesseract.android.TessBaseAPI;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


public class MicrRecognizer {

    private static final String TAG = "MicrRecognizer";

    public static CheckData recognize(Bitmap bm, String filename) {

        Pix pix = ReadFile.readBitmap(bm);
        CheckData checkData = innerRecognize(pix, filename);

        return checkData;
    }

    private static CheckData innerRecognize(Pix pix, String filename) {

        List<Pair<Integer, Float>> windowAndThresholds = new ArrayList<>();
        windowAndThresholds.add(new Pair<>(32, 0.80f));

        // may be later allow it
//        windowAndThresholds.add(new Pair<>(16, 0.80f));
//        windowAndThresholds.add(new Pair<>(64, 0.80f));
//        windowAndThresholds.add(new Pair<>(16, 0.35f));

        CheckData bestCheckData = null;
        for (Pair<Integer, Float> item : windowAndThresholds) {
            int windowSize = item.first;
            float threshold = item.second;

            try {

                // binarize
                Pix savuolaBinarized = Binarize.sauvolaBinarizeTiled(pix,
                        windowSize, threshold,
                        Binarize.SAUVOLA_DEFAULT_NUM_TILES_X, Binarize.SAUVOLA_DEFAULT_NUM_TILES_Y);
                Bitmap bitmap = unskew(savuolaBinarized);
                List<Symbol> symbols = rawRecognize(bitmap, null);
                MicrInfo micrInfo = findBorders(symbols);

                List<Symbol> filteredSymbols = MicrRecognizer.filterTheline(symbols, micrInfo);


                Rect lineRect = new Rect(pix.getWidth(), pix.getHeight(), 0, 0);

                Map<Integer, Integer> widthFrequenciesOf1 = new HashMap<>();
                Map<Integer, Integer> widthFrequenciesDigit = new HashMap<>();
                Map<Integer, Integer> heightFrequencies = new HashMap<>();
                for (Symbol symbol : filteredSymbols) {
                    if (symbol.confidence > 70) {

                        if (symbol.symbol.matches("[0-9]")) {
                            CalcUtils.putFrequency(heightFrequencies, symbol.rect.width());
                            if (symbol.symbol.equals("1")) {
                                CalcUtils.putFrequency(widthFrequenciesOf1, symbol.rect.width());
                            } else {
                                CalcUtils.putFrequency(widthFrequenciesDigit, symbol.rect.width());
                            }
                        }
                    }

                    if (symbol.rect.left < lineRect.left) {
                        lineRect.left = symbol.rect.left;
                    }
                    if (symbol.rect.top < lineRect.top) {
                        lineRect.top = symbol.rect.top;
                    }
                    if (symbol.rect.right > lineRect.right) {
                        lineRect.right = symbol.rect.right;
                    }
                    if (symbol.rect.bottom > lineRect.bottom) {
                        lineRect.bottom = symbol.rect.bottom;
                    }
                }

                if (heightFrequencies.size() > 0) {
                    Rect borderRect = new Rect(0, 0, pix.getWidth(), pix.getHeight());
                    CalcUtils.rectWithMargins(lineRect, 10, borderRect);

                    symbols = rawRecognize(bitmap, lineRect);
                    micrInfo = findBorders(symbols);
                    filteredSymbols = MicrRecognizer.filterTheline(symbols, micrInfo);
                }

                CheckData checkData = MicrRecognizer.joinThinSymbols(bitmap, filteredSymbols, micrInfo);

                if (checkData.isOk) {
                    //return checkData;
                }
                if (bestCheckData == null || bestCheckData.confidence < checkData.confidence) {
                    bestCheckData = checkData;
                }


            } catch (Exception e) {
                Log.e(TAG, "Cannot recognize pic " + String.format("%s_%s_%s.jpg", filename, windowSize, threshold), e);
            }
        }
        return bestCheckData;
    }



    public static List<Symbol> rawRecognize(Bitmap bm, Rect poiRect) {

        TessBaseAPI baseApi = createTessBaseApi();
        baseApi.setImage(bm);
        if (poiRect != null) {
            baseApi.setRectangle(poiRect);
        }
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

        Map<Integer, Integer> bottomFrequencies = new HashMap<>();
        Map<Integer, Integer> topFrequencies = new HashMap<>();
        Map<Integer, Integer> widthFrequencies = new HashMap<>();
        Map<Integer, Integer> heightFrequencies = new HashMap<>();

        for (Symbol rawSymbol : symbols) {
            Rect rect = rawSymbol.rect;

            if (rawSymbol.confidence > 70) {
                if ((rect.width()) < minWidth) {
                    minWidth = rect.width();
                }


                CalcUtils.putFrequency(bottomFrequencies, rect.bottom);
                CalcUtils.putFrequency(topFrequencies, rect.top);
                CalcUtils.putFrequency(widthFrequencies, rect.width());
                CalcUtils.putFrequency(heightFrequencies, rect.height());

//                CalcUtils.putStringFrequency(globalWidth, rawSymbol.symbol, rect.width());
//                CalcUtils.putStringFrequency(globalHeight, rawSymbol.symbol, rect.height());
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
            if (micrInfo.contains(rect) && symbol.rect.height() < micrInfo.typicalHeight * 1.2) {
                //if the rectangle starts before the last one ends- throw it away
                if (rect.left > right) {
                    //otherwise remember when it ends for future comparing
                    right = rect.right;
                    filteredSymbols.add(symbol);
                } else {
                    Log.d(TAG, "NEXT SYMBOL STARTS BEFORE PREV ENDS");
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

        StringBuilder builder = new StringBuilder();

        Symbol prevSymbol = null;
        double conf = 0;
        double minconf = 100;
        List<Symbol> symbols = new ArrayList<>();

        for (int i = 0; i < rawSymbols.size(); i++) {
            Symbol rawSymbol = rawSymbols.get(i);
            Symbol symbol = null;

            symbol = rawSymbol;

            if (symbol != null && symbol.confidence >= 70) {

                boolean isAddSpace = false;
                if (symbols.size() > 0) {
                    double prevRight = symbols.get(symbols.size() - 1).rect.right;
                    if (symbol.rect.left - prevRight > micrInfo.typicalWidth * 1.2) {

                        Log.d(TAG, "space: " + (symbol.rect.left - prevRight) + " - " + (micrInfo.typicalWidth * 1.2));

                        isAddSpace = true;
                    }
                }

                if (isAddSpace) {
                    builder.append(" ");
                }


                symbols.add(symbol);
                builder.append(symbol.symbol);
                conf += symbol.confidence;

                if (symbol.confidence < minconf) {
                    minconf = symbol.confidence;
                }
            }
        }

        CheckData checkData = new CheckData(bm, builder.toString(), symbols, conf / symbols.size());
        checkData.minConfidence = minconf;
        return checkData;
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
