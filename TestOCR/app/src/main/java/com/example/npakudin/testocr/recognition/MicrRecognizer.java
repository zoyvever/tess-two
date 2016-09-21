package com.example.npakudin.testocr.recognition;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.util.Log;
import android.util.Pair;

import com.example.npakudin.testocr.Utils;
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

    public static CheckData recognize(Bitmap bitmap) {

        // 1. binarize with default params
        // 2. find skew
        // 3. unskew src image
        // 4. raw recognize
        // 5. find borders
        // 6. recognize
        // 7. if bad:
        //      crop unskewed
        //      binarize with other params
        //      4 - 6

        List<Pair<Integer, Float>> windowAndThresholds = new ArrayList<>();
        windowAndThresholds.add(new Pair<>(16, 0.80f));
        windowAndThresholds.add(new Pair<>(32, 0.80f));
        windowAndThresholds.add(new Pair<>(64, 0.80f));
        windowAndThresholds.add(new Pair<>(16, 0.35f));

        boolean isCropped = false;

        for (Pair<Integer, Float> item : windowAndThresholds) {
            int windowSize = item.first;
            float threshold = item.second;

            try {
                Pix pix = ReadFile.readBitmap(bitmap);
                Pix binarized = Binarize.sauvolaBinarizeTiled(pix, windowSize, threshold, Binarize.SAUVOLA_DEFAULT_NUM_TILES_X, Binarize.SAUVOLA_DEFAULT_NUM_TILES_Y);

                Bitmap unskewed;
                if (!isCropped) {
                    float skew = Skew.findSkew(binarized);
                    unskewed = WriteFile.writeBitmap(Rotate.rotate(binarized, skew));
                } else {
                    unskewed = WriteFile.writeBitmap(binarized);
                }
                List<Symbol> symbols = rawRecognize(unskewed, null);
                MicrInfo micrInfo = findBorders(symbols);
                List<Symbol> filteredSymbols = MicrRecognizer.filterTheline(symbols, micrInfo);

                CheckData checkData = MicrRecognizer.joinThinSymbols(filteredSymbols, micrInfo);
                if (checkData.isOk) {
                    //Log.d(TAG, "OK recognize image with windowSize: " + windowSize + "; threshold: " + threshold);
                    return checkData;
                }
                //Log.d(TAG, "Cannot recognize image with windowSize: " + windowSize + "; threshold: " + threshold);

                if (!isCropped) {
                    // crop image
                    bitmap = Utils.cropBitmap(unskewed, Math.max(0, micrInfo.top - 10), Math.min(unskewed.getHeight(), micrInfo.bottom + 10));
                    isCropped = true;
                }
            } catch (Exception e) {
                Log.e(TAG, "Exception on recognize image with windowSize: " + windowSize + "; threshold: " + threshold, e);
            }
        }
        Log.d(TAG, "Cannot recognize image with any params");
        return null;
    }



    private static List<Symbol> rawRecognize(Bitmap bm, Rect poiRect) {

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


    private static MicrInfo findBorders(List<Symbol> symbols) {

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
    private static List<Symbol> filterTheline(List<Symbol> symbols, MicrInfo micrInfo) {

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
    private static CheckData joinThinSymbols(List<Symbol> rawSymbols, MicrInfo micrInfo) {

        StringBuilder builder = new StringBuilder();

        double conf = 0;
        double minconf = 100;
        List<Symbol> symbols = new ArrayList<>();

        for (int i = 0; i < rawSymbols.size(); i++) {
            Symbol symbol = rawSymbols.get(i);

            if (symbol != null && symbol.confidence >= 70) {

                boolean isAddSpace = false;
                if (symbols.size() > 0) {
                    double prevRight = symbols.get(symbols.size() - 1).rect.right;
                    if (symbol.rect.left - prevRight > micrInfo.typicalWidth * 1.2) {
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

        CheckData checkData = new CheckData(builder.toString(), conf / symbols.size());
        checkData.minConfidence = minconf;
        return checkData;
    }



    private static String mcrFilePath = null;

    public static void init(Context context) {
        mcrFilePath = Asset2File.init(context);
    }

    private static TessBaseAPI createTessBaseApi() {
        TessBaseAPI baseApi = new TessBaseAPI();
        baseApi.init(mcrFilePath, "mcr");
        return baseApi;
    }
}
