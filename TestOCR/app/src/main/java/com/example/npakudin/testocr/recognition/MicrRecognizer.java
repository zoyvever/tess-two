package com.example.npakudin.testocr.recognition;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
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

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


public class MicrRecognizer {

    private static final String TAG = "MicrRecognizer";
    public static final int MIN_CONFIDENCE = 70;
    public static final double TOLERANCE = 0.2;
    private static String mcrFilePath = null;

    // singleton, because creates file at exact path
    public static synchronized void init(@NonNull Context context) {
        if (mcrFilePath == null) {
            mcrFilePath = Asset2File.init(context);
        }
    }

    @NonNull
    private static TessBaseAPI createTessBaseApi() {
        TessBaseAPI baseApi = new TessBaseAPI();
        baseApi.init(mcrFilePath, "mcr");
        return baseApi;
    }


    @Nullable
    public static CheckData recognize(@NonNull Bitmap bitmap) {

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
                Rect borders = findBorders(symbols);
                List<Symbol> filteredSymbols = MicrRecognizer.filterTheline(symbols, borders);
                MicrInfo micrInfo = findStats(filteredSymbols);
                filteredSymbols = MicrRecognizer.filterTheline(filteredSymbols, micrInfo);

                CheckData checkData = MicrRecognizer.joinThinSymbols(filteredSymbols, micrInfo);

                if (filteredSymbols.size() > 0) {
                    int left = filteredSymbols.get(0).rect.left;
                    int right = filteredSymbols.get(filteredSymbols.size() - 1).rect.right;

                    if ((left < micrInfo.typicalWidth * 2) ||
                            (unskewed.getWidth() - right < micrInfo.typicalWidth * 2)) {
                        // MICR text may be cutted
                        checkData.errorMessage += " MICR text can be cutted, leave more free space at the left and right of text";
                        checkData.isOk = false;

                        // cropping won't help => return checkData now
                        return checkData;
                    }
                }


                if (checkData.isOk) {
                    //Log.d(TAG, "OK recognize image with windowSize: " + windowSize + "; threshold: " + threshold);
                    return checkData;
                }
                //Log.d(TAG, "Cannot recognize image with windowSize: " + windowSize + "; threshold: " + threshold);

                if (!isCropped) {
                    // crop image
                    bitmap = RecognitionUtils.cropBitmap(unskewed, Math.max(0, borders.top - 10), Math.min(unskewed.getHeight(), borders.bottom + 10));
                    isCropped = true;
                }
            } catch (Exception e) {
                Log.e(TAG, "Exception on recognize image with windowSize: " + windowSize + "; threshold: " + threshold, e);
            }
        }
        Log.d(TAG, "Cannot recognize image with any params");
        return null;
    }


    @NonNull
    private static List<Symbol> rawRecognize(@NonNull Bitmap bm, @Nullable Rect poiRect) {

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

    @NonNull
    private static Rect findBorders(@NonNull List<Symbol> symbols) {

        int left = Integer.MAX_VALUE;
        int right = Integer.MIN_VALUE;

        Map<Integer, Integer> bottomFrequencies = new HashMap<>();
        Map<Integer, Integer> topFrequencies = new HashMap<>();

        for (Symbol rawSymbol : symbols) {
            Rect rect = rawSymbol.rect;

            if (rawSymbol.confidence > MIN_CONFIDENCE) {

                RecognitionUtils.putFrequency(bottomFrequencies, rect.bottom);
                RecognitionUtils.putFrequency(topFrequencies, rect.top);

                if (rect.left < left) {
                    left = rect.left;
                }
                if (rect.right > right) {
                    right = rect.right;
                }
            }
        }
        int topBorder = RecognitionUtils.findMostFrequentItem(topFrequencies);
        int bottomBorder = RecognitionUtils.findMostFrequentItem(bottomFrequencies);
        int tolerance = (int) (0.6 * (bottomBorder - topBorder));
        return new Rect(left, topBorder - tolerance, right, bottomBorder + tolerance);
    }

    @NonNull
    private static MicrInfo findStats(@NonNull List<Symbol> symbols) {

        List<Integer> widths = new ArrayList<>();
        List<Integer> heights = new ArrayList<>();

        for (Symbol rawSymbol : symbols) {
            widths.add(rawSymbol.rect.width());
            heights.add(rawSymbol.rect.height());
        }

        Collections.sort(widths);
        Collections.sort(heights);



        return new MicrInfo(widths.get(widths.size() / 2), heights.get(heights.size() / 2));
    }

    /**
     * Remove overlapping symbols
     *
     */
    @NonNull
    private static List<Symbol> filterTheline(@NonNull List<Symbol> symbols, @NonNull Rect borders) {

        int right = 0;
        List<Symbol> filteredSymbols = new ArrayList<>();
        for (Symbol symbol : symbols) {

            Rect rect = symbol.rect;
            if (symbol.confidence >= MIN_CONFIDENCE && borders.contains(rect)) {
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
     * Remove overlapping symbols
     *
     */
    @NonNull
    private static List<Symbol> filterTheline(@NonNull List<Symbol> symbols, @NonNull MicrInfo micrInfo) {

        List<Symbol> filteredSymbols = new ArrayList<>();
        for (Symbol symbol : symbols) {

            Rect rect = symbol.rect;
            if (symbol.symbol.matches("[ab02-9]]")) {
                if (!isWidthOk(rect, micrInfo) || !isHeightOk(rect, micrInfo)) {
                    continue;
                }
            } else if (symbol.symbol.matches("1")) {
                if (!isHeightOk(rect, micrInfo)) {
                    continue;
                }
            } else if (symbol.symbol.matches("cd")) {
                if (!isWidthOk(rect, micrInfo)) {
                    continue;
                }
            }
            filteredSymbols.add(symbol);
        }
        return filteredSymbols;
    }

    private static boolean isWidthOk(Rect rect, MicrInfo micrInfo) {
        return rect.width() <= micrInfo.typicalWidth * (1 + TOLERANCE) &&
                rect.width() >= micrInfo.typicalWidth * (1 - TOLERANCE);
    }

    private static boolean isHeightOk(Rect rect, MicrInfo micrInfo) {
        return rect.height() <= micrInfo.typicalHeight * (1 + TOLERANCE) &&
                rect.height() >= micrInfo.typicalHeight * (1 - TOLERANCE);
    }

    /**
     * Joins symbols if there are 2 or 3 symbols in a row are too thin
     * Then tries to innerRecognize again
     *
     */
    @NonNull
    private static CheckData joinThinSymbols(@NonNull List<Symbol> rawSymbols, @NonNull MicrInfo micrInfo) {

        StringBuilder builder = new StringBuilder();

        double conf = 0;
        double minconf = 100;
        List<Symbol> symbols = new ArrayList<>();

        for (int i = 0; i < rawSymbols.size(); i++) {
            Symbol symbol = rawSymbols.get(i);

            if (symbol != null && symbol.confidence >= MIN_CONFIDENCE) {

                boolean isAddSpace = false;
                if (symbols.size() > 0) {
                    double prevRight = symbols.get(symbols.size() - 1).rect.right;
                    if (symbol.rect.left - prevRight > micrInfo.typicalWidth * (1 + TOLERANCE)) {
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
}
