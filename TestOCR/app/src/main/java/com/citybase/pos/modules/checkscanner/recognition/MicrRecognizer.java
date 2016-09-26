package com.citybase.pos.modules.checkscanner.recognition;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
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
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;


public class MicrRecognizer {

    private static final String TAG = "MicrRecognizer";

    public static final int MIN_CONFIDENCE = 70;
    public static final double SYMBOL_SIZE_VARIATION = 0.1;
    public static final double FREE_SPACE_AT_LEFT =  1.3; // in symbols

    public static final String NEED_MORE_SPACE_AT_LEFT = "!";
    public static final String NOT_RECOGNIZED = "$";
    public static final String WRONG_SYMBOL_SIZE = "#";

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


        // for debug only
        String realText = "a271071321a c9080054103c 0903";
        String imageName = Asset2File.uniqueName();
        Asset2File.saveBitmap(bitmap, "raw_" + imageName);


        CheckData bestCheckData = new CheckData("", 0);

        for (Pair<Integer, Float> item : windowAndThresholds) {
            int windowSize = item.first;
            float threshold = item.second;

            try {
                Pix pix = ReadFile.readBitmap(bitmap);
                Pix binarized = Binarize.sauvolaBinarizeTiled(pix, windowSize, threshold, Binarize.SAUVOLA_DEFAULT_NUM_TILES_X, Binarize.SAUVOLA_DEFAULT_NUM_TILES_Y);

                Bitmap unskewed;
                float skew = 0;
                if (!isCropped) {
                    skew = Skew.findSkew(binarized);
                    unskewed = WriteFile.writeBitmap(Rotate.rotate(binarized, skew));
                } else {
                    unskewed = WriteFile.writeBitmap(binarized);
                }
                assert unskewed != null; // if null - send a message

                List<Symbol> rawSymbols = rawRecognize(unskewed, null);
                Rect borders = findBorders(rawSymbols);
                List<Symbol> symbols = filterByBorders(rawSymbols, borders);
                MicrInfo micrInfo = findStats(symbols, borders);


                //List<Symbol> confidentSymbols = replaceByWidth(symbols, micrInfo);
                List<Symbol> confidentSymbols = filterByConfidence(symbols);
                if (confidentSymbols.size() == 0) {
                    // error
                    continue;
                }
                Symbol firstConfident = confidentSymbols.get(0);
                Symbol lastConfident = confidentSymbols.get(confidentSymbols.size() - 1);
                Rect confidentBorders = new Rect(firstConfident.rect.left, borders.top, lastConfident.rect.right, borders.bottom);
                symbols = filterByBorders(symbols, confidentBorders);


                symbols = replaceByWidth(symbols, micrInfo);
                symbols = replaceByConfidence(symbols);


                List<Interval> intervals = findIntervals(symbols, micrInfo);
                List<Symbol> intervalSymbols = intervalsToSymbols(intervals, unskewed, micrInfo);
                symbols = merge(
                        replaceByWidth(symbols, micrInfo),
                        intervalSymbols);

                CheckData checkData = joinThinSymbols(symbols, micrInfo, unskewed);

                // for debug only
                {
                    int distance = RecognitionUtils.levenshteinDistance(checkData.rawText, realText);
                    Bitmap drawed = RecognitionUtils.drawRecText(unskewed, 3.0f, symbols, realText, distance);

                    Bitmap tmp = RecognitionUtils.cropBitmap(drawed, Math.max(0, borders.top - 80), Math.min(bitmap.getHeight(), borders.bottom + 80));
                    checkData.image =  tmp;
                    Asset2File.saveBitmap(tmp, String.format(Locale.ENGLISH, "binarized_%s_%d_%.2f", imageName, windowSize, threshold));
                }

                if (intervalSymbols.get(0).symbol.equals(NEED_MORE_SPACE_AT_LEFT) ||
                        intervalSymbols.get(intervalSymbols.size() - 1).symbol.equals(NEED_MORE_SPACE_AT_LEFT)) {

                    checkData.errorMessage += "MICR number can be cut, leave more free space at the left and right of text";
                    checkData.isOk = false;

                    return checkData;
                }

                if (checkData.isOk){
                    Log.d(TAG, "OK recognize image with windowSize: " + windowSize + "; threshold: " + threshold);
                    //checkData.image = null;
                    return checkData;
                }
                Log.d(TAG, "Cannot recognize image with windowSize: " + windowSize + "; threshold: " + threshold);

                if (!isCropped) {

                    // unskew SOURCE image
                    bitmap = WriteFile.writeBitmap(Rotate.rotate(pix, skew));
                    //Asset2File.saveBitmap(bitmap, String.format(Locale.ENGLISH, "unskewed_%s", imageName));
                    assert bitmap != null;
                    // crop
                    bitmap = RecognitionUtils.cropBitmap(bitmap, Math.max(0, borders.top - 40), Math.min(bitmap.getHeight(), borders.bottom + 40));
                    //Asset2File.saveBitmap(bitmap, String.format(Locale.ENGLISH, "cropped_%s", imageName));
                    isCropped = true;
                }

                if (checkData.confidence > bestCheckData.confidence &&
                        (checkData.isOk || !bestCheckData.isOk)) {
                    bestCheckData = checkData;
                }
            } catch (Exception e) {
                Log.e(TAG, "Exception on recognize image with windowSize: " + windowSize + "; threshold: " + threshold, e);
            }
        }
        Log.d(TAG, "Cannot recognize image with any params");
        return bestCheckData;
    }

    private static List<Symbol> merge(List<Symbol> symbols1, List<Symbol> symbols2) {

        List<Symbol> symbols = new ArrayList<>();
        symbols.addAll(symbols1);
        symbols.addAll(symbols2);

        Collections.sort(symbols, new Comparator<Symbol>() {
            @Override
            public int compare(Symbol s1, Symbol s2) {

                return s1.rect.left < s2.rect.left ? -1 :
                        s1.rect.left == s2.rect.left ? 0 : 1;
            }
        });

        return symbols;
    }

    private static List<Symbol> intervalsToSymbols(List<Interval> intervals, Bitmap bitmap, MicrInfo micrInfo) {

        List<Symbol> symbols = new ArrayList<>();

        for (Interval interval : intervals) {
            if (interval.rect.left < 0 || interval.rect.right >= bitmap.getWidth()) {
                symbols.add(new Symbol(NEED_MORE_SPACE_AT_LEFT, 99, interval.rect));
                continue;
            }

            // 1% of typical width
            if (!isWhiteArea(bitmap, interval.rect, 0.01 * interval.rect.width() / micrInfo.typicalWidth)) {
                symbols.add(new Symbol(NOT_RECOGNIZED, 99, interval.rect));
                continue;
            }

            if (interval.rect.width() > (micrInfo.typicalWidth + micrInfo.typicalInterval) * 1.0) {
                symbols.add(new Symbol(" ", 99, interval.rect));
            }
        }

        return symbols;
    }

    @NonNull
    private static String createString(int count, char c) {
        return new String(new char[count]).replace('\0', c);
    }

    private static List<Interval> findIntervals(List<Symbol> symbols, MicrInfo micrInfo) {

        List<Interval> intervals = new ArrayList<>();

        int prevRight = symbols.get(0).rect.left - (int)(FREE_SPACE_AT_LEFT * (micrInfo.typicalWidth + micrInfo.typicalInterval));
        for (Symbol symbol : symbols) {
            Rect rect = new Rect(prevRight + 1, micrInfo.borders.top, symbol.rect.left - 1, micrInfo.borders.bottom);
            intervals.add(new Interval(rect)); // here adds 1st interval on 1st iter
            prevRight = symbol.rect.right;
        }
        // add last interval
        Rect rect = new Rect(prevRight + 1, micrInfo.borders.top, prevRight + (int)(FREE_SPACE_AT_LEFT * (micrInfo.typicalWidth + micrInfo.typicalInterval)), micrInfo.borders.bottom);
        intervals.add(new Interval(rect));

        return intervals;
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
        //int tolerance = (int) (0.6 * (bottomBorder - topBorder));
        int tolerance = 0;
        return new Rect(left, topBorder - tolerance, right, bottomBorder + tolerance);
    }

    @NonNull
    private static MicrInfo findStats(@NonNull List<Symbol> symbols, Rect borders) {

        if (symbols.size() == 0) {
            return new MicrInfo(0, 0, 0, borders);
        }

        List<Integer> widths = new ArrayList<>();
        List<Integer> heights = new ArrayList<>();
        List<Integer> intervals = new ArrayList<>();

        int prevRight = 0;
        for (Symbol rawSymbol : symbols) {
            if (rawSymbol.confidence < MIN_CONFIDENCE) {
                continue;
            }
            widths.add(rawSymbol.rect.width());
            heights.add(rawSymbol.rect.height());
            intervals.add(rawSymbol.rect.left - prevRight);
            prevRight = rawSymbol.rect.right;
        }

        Collections.sort(widths);
        Collections.sort(heights);
        Collections.sort(intervals);

        MicrInfo micrInfo = new MicrInfo();
        micrInfo.typicalWidth = widths.get(widths.size() / 2);
        micrInfo.typicalHeight = heights.get(heights.size() / 2);
        micrInfo.typicalInterval = intervals.get(intervals.size() / 2);
        micrInfo.borders = borders;

        return micrInfo;
    }

    @NonNull
    private static List<Symbol> filterByBorders(@NonNull List<Symbol> symbols, @NonNull Rect borders) {

        // increase filtering area
        int tolerance = (int) (0.6 * (borders.bottom - borders.top));
        borders = new Rect(borders.left, borders.top - tolerance, borders.right, borders.bottom + tolerance);

        int right = 0;
        List<Symbol> filteredSymbols = new ArrayList<>();
        for (Symbol symbol : symbols) {

            Rect rect = symbol.rect;
            if (borders.contains(rect)) {
                //if the rectangle starts before the last one ends- throw it away
                if (rect.left < right) {
                    Log.d(TAG, "rect.left < right: " + rect);
                    continue;
                }
                right = rect.right;
                filteredSymbols.add(symbol);
            }
        }
        return filteredSymbols;
    }

    @NonNull
    private static List<Symbol> replaceByWidth(@NonNull List<Symbol> symbols, @NonNull MicrInfo micrInfo) {

        List<Symbol> filteredSymbols = new ArrayList<>();
        for (Symbol symbol : symbols) {

            Rect rect = symbol.rect;
            if (symbol.symbol.matches("[ab02-9]]")) {
                if (!isWidthOk(rect, micrInfo) || !isHeightOk(rect, micrInfo)) {
                    symbol.symbol = WRONG_SYMBOL_SIZE;
                    //continue;
                }
            } else if (symbol.symbol.matches("1")) {
                if (!isHeightOk(rect, micrInfo)) {
                    symbol.symbol = WRONG_SYMBOL_SIZE;
                    //continue;
                }
            } else if (symbol.symbol.matches("cd")) {
                if (!isWidthOk(rect, micrInfo)) {
                    symbol.symbol = WRONG_SYMBOL_SIZE;
                    //continue;
                }
            }
            filteredSymbols.add(symbol);
        }
        return filteredSymbols;
    }

    private static boolean isWidthOk(Rect rect, MicrInfo micrInfo) {
        return rect.width() <= micrInfo.typicalWidth * (1 + SYMBOL_SIZE_VARIATION) &&
                rect.width() >= micrInfo.typicalWidth * (1 - SYMBOL_SIZE_VARIATION);
    }

    private static boolean isHeightOk(Rect rect, MicrInfo micrInfo) {
        return rect.height() <= micrInfo.typicalHeight * (1 + SYMBOL_SIZE_VARIATION) &&
                rect.height() >= micrInfo.typicalHeight * (1 - SYMBOL_SIZE_VARIATION);
    }

    @NonNull
    private static List<Symbol> filterByConfidence(@NonNull List<Symbol> symbols) {

        List<Symbol> filteredSymbols = new ArrayList<>();
        for (Symbol symbol : symbols) {

            if (symbol.confidence < MIN_CONFIDENCE) {
                //symbol.symbol = "#";
                continue;
            }
            filteredSymbols.add(symbol);
        }
        return filteredSymbols;
    }

    @NonNull
    private static List<Symbol> replaceByConfidence(@NonNull List<Symbol> symbols) {

        List<Symbol> filteredSymbols = new ArrayList<>();
        for (Symbol symbol : symbols) {

            if (symbol.confidence < MIN_CONFIDENCE) {
                symbol.symbol = "&";
            }
            filteredSymbols.add(symbol);
        }
        return filteredSymbols;
    }

    /**
     * Joins symbols if there are 2 or 3 symbols in a row are too thin
     * Then tries to innerRecognize again
     *
     */
    @NonNull
    private static CheckData joinThinSymbols(@NonNull List<Symbol> rawSymbols, @NonNull MicrInfo micrInfo, Bitmap bitmap) {

        StringBuilder builder = new StringBuilder();

        double conf = 0;
        double minconf = 100;
        List<Symbol> symbols = new ArrayList<>();

        for (int i = 0; i < rawSymbols.size(); i++) {
            Symbol symbol = rawSymbols.get(i);

            symbols.add(symbol);
            builder.append(symbol.symbol);
            conf += symbol.confidence;

            if (symbol.confidence < minconf) {
                minconf = symbol.confidence;
            }
        }

        CheckData checkData = new CheckData(builder.toString().trim(), conf / symbols.size());
        checkData.minConfidence = minconf;
        return checkData;
    }

    private static boolean isWhiteArea(Bitmap bitmap, Rect rect, double allowedPercent) {

        return isWhiteArea(bitmap, rect.left, rect.top, rect.right, rect.bottom, allowedPercent);
    }

    private static boolean isWhiteArea(Bitmap bitmap, int left, int top, int right, int bottom, double allowedPercent) {

//        left = Math.max(0, left);
//        top = Math.max(0, top);
//        right = Math.min(bitmap.getWidth(), right);
//        bottom = Math.min(bitmap.getHeight(), bottom);

        int width = right - left;
        int height = bottom - top;

        int blackPixelsCount = 0;
        for (int y=top; y<=bottom; y++) {
            for (int x=left; x<=right; x++) {
                if (bitmap.getPixel(x, y) != Color.WHITE) {
                    if (bitmap.getPixel(x, y) != Color.BLACK) {
                        bitmap.setPixel(x, y, Color.RED);
                    }
                    blackPixelsCount++;
                }
            }
        }

        return blackPixelsCount < width * height * allowedPercent;
    }
}
