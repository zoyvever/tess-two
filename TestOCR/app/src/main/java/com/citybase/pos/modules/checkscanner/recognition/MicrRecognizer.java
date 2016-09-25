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
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;


public class MicrRecognizer {

    private static final String TAG = "MicrRecognizer";

    public static final int MIN_CONFIDENCE = 70;
    public static final double SYMBOL_SIZE_VARIATION = 0.2;
    public static final double FREE_SPACE_AT_LEFT =  1.5; // in symbols

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
//        windowAndThresholds.add(new Pair<>(32, 0.80f));
//        windowAndThresholds.add(new Pair<>(64, 0.80f));
//        windowAndThresholds.add(new Pair<>(16, 0.35f));

        boolean isCropped = false;


        // for debug only
        String realText = "a271071321a c9080054103c 0903";
        String imageName = Asset2File.uniqueName();
        Asset2File.saveBitmap(bitmap, imageName + "_src");


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

                List<Symbol> symbols = rawRecognize(unskewed, null);
                Rect borders = findBorders(symbols);
                List<Symbol> filteredSymbols = filterByBorders(symbols, borders);
                MicrInfo micrInfo = findStats(filteredSymbols);
                filteredSymbols = filterByWidth(filteredSymbols, micrInfo);
                //filteredSymbols = filterByConfidence(filteredSymbols);

                {
                    // find first and last with good confidence
                    Symbol firstOk = null;
                    Symbol lastOk = null;
                    for (int i = 0; i < filteredSymbols.size(); i++) {
                        Symbol symbol = filteredSymbols.get(i);
                        if (symbol.confidence > MIN_CONFIDENCE) {
                            if (firstOk == null) {
                                firstOk = symbol;
                            }
                            lastOk = symbol;
                        }
                    }
                    assert firstOk != null;
                    assert lastOk != null;


                    // check pixels whiteness before firstOK
                    Rect firstRect = new Rect(firstOk.rect.left - 2 * (micrInfo.typicalWidth + micrInfo.typicalInterval), firstOk.rect.top, firstOk.rect.left - 1, firstOk.rect.bottom);
                    if (!checkIsEmpty(unskewed, firstRect)) {

                        // error - not empty
                        Symbol symbol = new Symbol();
                        symbol.symbol = "@";
                        symbol.rect = firstRect;
                        symbol.confidence = 99.0f;

                        filteredSymbols.add(0, symbol); // insert at the beginning

                        Log.d(TAG, "is not empty block");
                    }


                    // check pixels whiteness after lastOK
                    Rect lastRect = new Rect(lastOk.rect.right + 1, lastOk.rect.top, lastOk.rect.right + 2 * (micrInfo.typicalWidth + micrInfo.typicalInterval), lastOk.rect.bottom);
                    if (!checkIsEmpty(unskewed, lastRect)) {

                        // error - not empty
                        Symbol symbol = new Symbol();
                        symbol.symbol = "@";
                        symbol.rect = lastRect;
                        symbol.confidence = 99.0f;

                        filteredSymbols.add(symbol);

                        Log.d(TAG, "is not empty block");
                    }
                }


                CheckData checkData = joinThinSymbols(filteredSymbols, micrInfo, unskewed);

                // for debug only
                {
                    int distance = RecognitionUtils.levenshteinDistance(checkData.rawText, realText);
                    Bitmap drawed = RecognitionUtils.drawRecText(unskewed, 3.0f, filteredSymbols, realText, distance);
                    checkData.image = drawed;
                    Asset2File.saveBitmap(drawed, String.format(Locale.ENGLISH, "%s_unskewed_%d_%.2f", imageName, windowSize, threshold));
                }


                if (filteredSymbols.size() > 0) {
                    int left = filteredSymbols.get(0).rect.left;
                    int right = filteredSymbols.get(filteredSymbols.size() - 1).rect.right;

                    if ((left < micrInfo.typicalWidth * FREE_SPACE_AT_LEFT) ||
                            (unskewed.getWidth() - right < micrInfo.typicalWidth * FREE_SPACE_AT_LEFT)) {
                        // MICR text may be cutted
                        checkData.errorMessage += "MICR number can be cut, leave more free space at the left and right of text";
                        checkData.isOk = false;

                        // cropping won't help => return checkData now
                        return checkData;
                    }
                }


                if (checkData.isOk){
                    //Log.d(TAG, "OK recognize image with windowSize: " + windowSize + "; threshold: " + threshold);
                    return checkData;
                }
                //Log.d(TAG, "Cannot recognize image with windowSize: " + windowSize + "; threshold: " + threshold);

                if (!isCropped) {

                    // unskew SOURCE image
                    bitmap = WriteFile.writeBitmap(Rotate.rotate(pix, skew));
                    Asset2File.saveBitmap(bitmap, String.format(Locale.ENGLISH, "%s_src_unskewed", imageName));
                    assert bitmap != null;
                    // crop
                    bitmap = RecognitionUtils.cropBitmap(bitmap, Math.max(0, borders.top - 40), Math.min(bitmap.getHeight(), borders.bottom + 40));
                    Asset2File.saveBitmap(bitmap, String.format(Locale.ENGLISH, "%s_src_unskewed_cropped", imageName));
                    isCropped = true;
                }

                return checkData;
            } catch (Exception e) {
                Log.e(TAG, "Exception on recognize image with windowSize: " + windowSize + "; threshold: " + threshold, e);
            }
        }
        Log.d(TAG, "Cannot recognize image with any params");
        return new CheckData("", 0);
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

        if (symbols.size() == 0) {
            return new MicrInfo(0, 0, 0);
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



        return new MicrInfo(widths.get(widths.size() / 2), heights.get(heights.size() / 2), intervals.get(intervals.size() / 2));
    }

    /**
     * Remove overlapping symbols
     *
     */
    @NonNull
    private static List<Symbol> filterByBorders(@NonNull List<Symbol> symbols, @NonNull Rect borders) {

        int right = 0;
        List<Symbol> filteredSymbols = new ArrayList<>();
        for (Symbol symbol : symbols) {

            Rect rect = symbol.rect;
            if (borders.contains(rect)) {
                //if the rectangle starts before the last one ends- throw it away
                if (rect.left > right) {
                    //otherwise remember when it ends for future comparing
                    right = rect.right;

                    filteredSymbols.add(symbol);
                } else {
                    //Log.d(TAG, "NEXT SYMBOL STARTS BEFORE PREV ENDS");
                }
            }
        }
        return filteredSymbols;
    }

    @NonNull
    private static List<Symbol> filterByWidth(@NonNull List<Symbol> symbols, @NonNull MicrInfo micrInfo) {

        List<Symbol> filteredSymbols = new ArrayList<>();
        for (Symbol symbol : symbols) {

            Rect rect = symbol.rect;
            if (symbol.symbol.matches("[ab02-9]]")) {
                if (!isWidthOk(rect, micrInfo) || !isHeightOk(rect, micrInfo)) {
                    symbol.symbol = "#";
                    //continue;
                }
            } else if (symbol.symbol.matches("1")) {
                if (!isHeightOk(rect, micrInfo)) {
                    symbol.symbol = "#";
                    //continue;
                }
            } else if (symbol.symbol.matches("cd")) {
                if (!isWidthOk(rect, micrInfo)) {
                    symbol.symbol = "#";
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
                symbol.symbol = "#";
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

        // find first and last with good confidence
        Symbol firstOk = null;
        Symbol lastOk = null;
        for (int i = 0; i < rawSymbols.size(); i++) {
            Symbol symbol = rawSymbols.get(i);
            if (symbol.confidence > MIN_CONFIDENCE) {
                if (firstOk == null) {
                    firstOk = symbol;
                }
                lastOk = symbol;
            }
        }
        assert firstOk != null;
        assert lastOk != null;

        for (int i = 0; i < rawSymbols.size(); i++) {
            Symbol symbol = rawSymbols.get(i);

            if (symbol.confidence < MIN_CONFIDENCE) {
                if ((firstOk.rect.left - symbol.rect.left > micrInfo.typicalWidth * (1 + SYMBOL_SIZE_VARIATION) ) ||
                        (symbol.rect.left - lastOk.rect.left > micrInfo.typicalWidth * (1 + SYMBOL_SIZE_VARIATION) )) {
                    // skip, if symbol is before or after the line
                    // and there are spaces between (so, it's not badly recognized 1st or last symbol)
                    continue;
                } else {
                    symbol.symbol = "#";
                }
            }

            //if (symbol != null && symbol.confidence >= MIN_CONFIDENCE) {

            boolean isAddSpace = false;
            if (symbols.size() > 0) {
                int prevRight = symbols.get(symbols.size() - 1).rect.right;
                if (symbol.rect.left - prevRight > micrInfo.typicalWidth * (1 + SYMBOL_SIZE_VARIATION)) {

                    // TODO: check, that here is empty
                    if (!checkIsEmpty(bitmap, prevRight + 1, symbol.rect.top, symbol.rect.left - 1, symbol.rect.bottom)) {
                        // error - not empty
                        builder.append("#");
                        Log.d(TAG, "is not empty block");
                    } else {
                        isAddSpace = true;
                    }
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
            //}
        }

        CheckData checkData = new CheckData(builder.toString(), conf / symbols.size());
        checkData.minConfidence = minconf;
        return checkData;
    }

    private static boolean checkIsEmpty(Bitmap bitmap, Rect rect) {

        return checkIsEmpty(bitmap, rect.left, rect.top, rect.right, rect.bottom);
    }

    private static boolean checkIsEmpty(Bitmap bitmap, int left, int top, int right, int bottom) {

        left = Math.max(0, left);
        top = Math.max(0, top);
        right = Math.min(bitmap.getWidth(), right);
        bottom = Math.min(bitmap.getHeight(), bottom);

        int width = right - left;
        int height = bottom - top;

        int blackPixelsCount = 0;
        for (int y=top; y< bottom; y++) {
            for (int x=left; x< right; x++) {
                if (bitmap.getPixel(x, y) != Color.WHITE) {
                    if (bitmap.getPixel(x, y) != Color.BLACK) {
                        bitmap.setPixel(x, y, Color.RED);
                    }
                    blackPixelsCount++;
                }
            }
        }

        return blackPixelsCount < width * height * 0.01; // usually min 4% if there is rest of symbol
    }
}
