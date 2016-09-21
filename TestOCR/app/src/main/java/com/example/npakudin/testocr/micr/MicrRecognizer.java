package com.example.npakudin.testocr.micr;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.util.Log;
import android.util.Pair;

import com.example.npakudin.testocr.DrawUtils;
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
import java.util.Objects;


public class MicrRecognizer {

    private static final String LOGTAG = "TextRecognizer";
    public static Float scale;

    public static CheckData recognize(Bitmap bm, String filename) {

        Pix pix = ReadFile.readBitmap(bm);
        CheckData checkData = innerRecognize(pix, filename);

        return checkData;

        //return recognizeCycle(bm);
    }

//    private static CheckData recognizeCycle(Bitmap bm) {
//
//        Pix pix = ReadFile.readBitmap(bm);
//        CheckData checkData = innerRecognize(pix);
//
//        return checkData;
////        if (checkData.confidence > 70) {
////            return checkData;
////        }
////        Log.w(LOGTAG, "checkData.confidence <= 70 : " + checkData.confidence);
////        if (checkData.confidence > 40) {
////            Bitmap cropped = cropBitmap(bm, checkData);
////            return innerRecognize(ReadFile.readBitmap(cropped));
////        }
////        Log.w(LOGTAG, "checkData.confidence <= 40 : " + checkData.confidence);
////        Bitmap scaled = WriteFile.writeBitmap(Scale.scale(pix, (float) 0.8));
////        CheckData res = innerRecognize(ReadFile.readBitmap(scaled));
////        return res;
//    }

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

    private static CheckData innerRecognize(Pix pix, String filename) {

        String realText = filename.substring(0, filename.length() - 5);

        int[] windowsSizes = new int[] { 64, 32, 16, 8, };
        float[] thresholds = new float[] { 0.35f, 0.20f, 0.80f, 0.50f };
//        int[] windowsSizes = new int[] { 32, };
//        float[] thresholds = new float[] { 0.35f, };

        CheckData bestCheckData = null;
        for (int windowSize : windowsSizes) {
            for (float threshold : thresholds) {

                try {

                    Pix savuolaBinarized = Binarize.sauvolaBinarizeTiled(pix,
                            windowSize, threshold,
                            Binarize.SAUVOLA_DEFAULT_NUM_TILES_X, Binarize.SAUVOLA_DEFAULT_NUM_TILES_Y);
                    Bitmap bitmap = unskew(savuolaBinarized);
                    Log.d(LOGTAG, "before rawRecognize");
                    List<Symbol> symbols = rawRecognize(bitmap, null);
                    Log.d(LOGTAG, "after rawRecognize");
                    MicrInfo micrInfo = findBorders(symbols);

                    List<Symbol> filteredSymbols = MicrRecognizer.filterTheline(symbols, micrInfo);


                    Rect lineRect = new Rect(pix.getWidth(), pix.getHeight(), 0, 0);

                    Map<Integer, Integer> widthFrequencies1 = new HashMap<>();
                    Map<Integer, Integer> widthFrequenciesDigit = new HashMap<>();
                    Map<Integer, Integer> heightFrequencies = new HashMap<>();
                    for (Symbol symbol : filteredSymbols) {
                        if (symbol.confidence > 70) {
                            CalcUtils.putStringFrequency(globalWidth, symbol.symbol, symbol.rect.width());
                            CalcUtils.putStringFrequency(globalHeight, symbol.symbol, symbol.rect.height());

                            if (symbol.symbol.matches("[0-9]")) {
                                CalcUtils.putFrequency(heightFrequencies, symbol.rect.width());
                                if (symbol.symbol.equals("1")) {
                                    CalcUtils.putFrequency(widthFrequencies1, symbol.rect.width());
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
                    CalcUtils.handleAvgDisp(heightFrequencies, "QQQ height  ");
                    CalcUtils.handleAvgDisp(widthFrequencies1, "QQQ width 1 ");
                    CalcUtils.handleAvgDisp(widthFrequenciesDigit, "QQQ width D ");

                    if (heightFrequencies.size() > 0) {
                        Rect borderRect = new Rect(0, 0, pix.getWidth(), pix.getHeight());
                        CalcUtils.rectWithMargins(lineRect, 10, borderRect);

                        symbols = rawRecognize(bitmap, lineRect);
                        micrInfo = findBorders(symbols);
                        filteredSymbols = MicrRecognizer.filterTheline(symbols, micrInfo);
                    }

                    CheckData checkData = MicrRecognizer.joinThinSymbols(bitmap, filteredSymbols, micrInfo);
                    checkData.realText = realText;

                    checkData.descr = "" + windowSize + " - " + threshold;
                    checkData.descr += " / " + checkData.minConfidence + " - " + checkData.confidence;

                    Log.w(LOGTAG, "Saving pic for " + filename);

                    int distance = DrawUtils.levenshteinDistance(checkData.wholeText, realText);
                    Bitmap bmRes = DrawUtils.drawRecText(checkData.res, scale, checkData.symbols, realText, distance);
                    DrawUtils.saveBitmap(bmRes, String.format("%s/%s", windowSize, threshold), filename + ".jpg");
                    //DrawUtils.saveBitmap(bmRes, String.format("%s_%s_%s.jpg", filename, windowSize, threshold));

                    if (checkData.isOk) {
                        //return checkData;
                    }
                    if (bestCheckData == null || bestCheckData.confidence < checkData.confidence) {
                        bestCheckData = checkData;
                    }
                } catch (Exception e) {
                    Log.e(LOGTAG, "Cannot recognize pic " + String.format("%s_%s_%s.jpg", filename, windowSize, threshold), e);
                }
            }
        }
        return bestCheckData;
    }



    public static List<Symbol> rawRecognize(Bitmap bm, Rect poiRect) {

        TessBaseAPI baseApi = createTessBaseApi();
        baseApi.setImage(bm);
        Log.d(LOGTAG, "rawRecognize, poi: " + poiRect + " - " + bm.getWidth() + " x " + bm.getHeight());
        if (poiRect != null) {
            baseApi.setRectangle(poiRect);
        }
        List<Symbol> symbols = new ArrayList<>();

        Log.d(LOGTAG, "rawRecognize, before recognize ");

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
        Log.d(LOGTAG, "rawRecognize, after recognize ");
        return symbols;
    }

    public static Bitmap unskew(Pix imag) {
        Float s = Skew.findSkew(imag);
        return WriteFile.writeBitmap(Rotate.rotate(imag, s));
    }

    public static Map<String, Map<Integer, Integer>> globalWidth = new HashMap<>();
    public static Map<String, Map<Integer, Integer>> globalHeight = new HashMap<>();

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
                    Log.d(LOGTAG, "NEXT SYMBOL STARTS BEFORE PREV ENDS");
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

        //Rect borderRect = new Rect(0, 0, bm.getWidth(), bm.getHeight());

        StringBuilder builder = new StringBuilder();
//        TessBaseAPI singleCharRecognition = createTessBaseApi();
//        singleCharRecognition.setPageSegMode(TessBaseAPI.PageSegMode.PSM_SINGLE_CHAR);
//        singleCharRecognition.setImage(bm);

        Symbol prevSymbol = null;
        double conf = 0;
        double minconf = 100;
        List<Symbol> symbols = new ArrayList<>();

        for (int i = 0; i < rawSymbols.size(); i++) {
            Symbol rawSymbol = rawSymbols.get(i);
            Symbol symbol = null;

            symbol = rawSymbol;

//            if (prevSymbol != null) {
//
//                Rect oneCharRect = new Rect(prevSymbol.rect);
//
//                // add current symbol and re-recognize
//
//                //if we already have first part of unrecognized letter then
//                if (rawSymbol.rect.top < oneCharRect.top) {
//                    oneCharRect.top = rawSymbol.rect.top;
//                    //use lowest top
//                }
//                if (rawSymbol.rect.bottom > oneCharRect.bottom) {
//                    oneCharRect.bottom = rawSymbol.rect.bottom;
//                    //use biggest bottom
//                }
//                oneCharRect.right = rawSymbol.rect.right;
//                //use value for 'right' from the last rect
//
//
//                // anyway single char recognition doesn't work
//                symbol = new Symbol();
//                symbol.rect = oneCharRect;
//                symbol.symbol = "%";
//                symbol.confidence = 99.0;
//
//
//                //Rect oneCharRectWithBorders = CalcUtils.rectWithMargins(oneCharRect, 3, borderRect);
//
////                singleCharRecognition.clear();
////                singleCharRecognition.setPageSegMode(TessBaseAPI.PageSegMode.PSM_SINGLE_CHAR);
////                singleCharRecognition.setImage(bm);
////                singleCharRecognition.setRectangle(oneCharRect);
////                String s = singleCharRecognition.getUTF8Text().trim();
////
////                if (s.length() > 0) {
////                    symbol = new Symbol();
////                    symbol.rect = oneCharRect;
////                    symbol.symbol = s;
////                    symbol.confidence = singleCharRecognition.getResultIterator().getChoicesAndConfidence(TessBaseAPI.PageIteratorLevel.RIL_SYMBOL).get(0).second;
////
////                    prevSymbol = null;
////
////                    Log.d(LOGTAG, "JOINED SYMBOL: " + symbol);
////                } else {
////
////                    // quite stupid, by TessAPI cannot recognize single char
////
////                    Log.d(LOGTAG, "CANNON JOIN SYMBOLS: " + prevSymbol + " - " + rawSymbol);
////
//////                    // add prev symbol as is
//////                    symbol = prevSymbol;
//////                    symbols.add(symbol);
//////                    builder.append(symbol.symbol);
//////                    conf += symbol.confidence;
//////                    if (symbol.confidence < minconf) {
//////                        minconf = symbol.confidence;
//////                    }
//////
//////                    // add cur symbol as is
//////                    symbol = rawSymbol;
////                }
//
//
//
//
//            } else {
//                // check, that symbol is suspicious
//                boolean isSuspicious = false;
//                if (rawSymbol.confidence < 60) {
//                    isSuspicious = true;
//                }
//                if (rawSymbol.confidence < 80) {
////                    if (rawSymbol.rect.height() < micrInfo.typicalHeight * 0.8) {
////                        isSuspicious = true;
////                    }
//                    if (rawSymbol.rect.width() < micrInfo.typicalWidth * 0.8) {
//                        if (rawSymbol.symbol.equals("1")) {
//                            if (rawSymbol.rect.height() < micrInfo.typicalHeight * 0.8) {
//                                isSuspicious = true;
//                            }
//                        } else {
//                            isSuspicious = true;
//                        }
//                    }
//                }
//                if (isSuspicious) {
//                    prevSymbol = rawSymbol;
//                } else {
//
//                    // add to res
//                    symbol = rawSymbol;
//
//                }
//            }


            if (symbol != null && symbol.confidence >= 70) {

                boolean isAddSpace = false;
                if (symbols.size() > 0) {
                    double prevRight = symbols.get(symbols.size() - 1).rect.right;
                    if (symbol.rect.left - prevRight > micrInfo.typicalWidth * 1.2) {

                        Log.d(LOGTAG, "space: " + (symbol.rect.left - prevRight) + " - " + (micrInfo.typicalWidth * 1.2));

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

//        Rect oneCharRect = null;
//        for (int i = 0; i < rawSymbols.size(); i++) {
//            Symbol rawSymbol = rawSymbols.get(i);
//            Symbol nextSymbol = i < rawSymbols.size() - 1 ? rawSymbols.get(i + 1) : new Symbol("", 0, new Rect(bm.getWidth(), 0, bm.getWidth(), bm.getHeight()));
//
//            Symbol symbol = new Symbol();
//
//            if (oneCharRect != null) {
////                if (oneCharRect.width() + rawSymbol.rect.width() <= micrInfo.minimumCharWidth) {
////                    //in case if tle letter divided in three parts
////                    oneCharRect.right = rawSymbol.rect.right;
////                    continue;
////                }
//                //if we already have first part of unrecognized letter then
//                if (rawSymbol.rect.top < oneCharRect.top) {
//                    oneCharRect.top = rawSymbol.rect.top;
//                    //use lowest top
//                }
//                if (rawSymbol.rect.bottom > oneCharRect.bottom) {
//                    oneCharRect.bottom = rawSymbol.rect.bottom;
//                    //use biggest bottom
//                }
//                oneCharRect.right = rawSymbol.rect.right;
//                //use value for 'right' from the last rect
//
//                Rect oneCharRectWithBorders = CalcUtils.rectWithMargins(oneCharRect, 3, borderRect);
//
//                singleCharRecognition.setRectangle(oneCharRectWithBorders);
//                String s = singleCharRecognition.getUTF8Text().trim();
//                Log.w(LOGTAG, "recognized string: " + s);
//                if (s.length() > 0) {
//                    if (s.matches("\\d|a")) {
//                        symbol.symbol = "#";
//                    } else {
//                        symbol.symbol = s;
//                    }
//                    symbol.confidence = singleCharRecognition.getResultIterator().getChoicesAndConfidence(TessBaseAPI.PageIteratorLevel.RIL_SYMBOL).get(0).second;
//                } else {
//                    symbol.symbol = "%";
//                    symbol.confidence = 99.0;
//                    Log.w(LOGTAG, "bad recognition");
//                }
//                symbol.rect = oneCharRect;
//                oneCharRect = null;
//            } else {
//                if ((rawSymbol.rect.width() < micrInfo.minimumCharWidth &&
//                        !Objects.equals(rawSymbol.symbol, "1") &&
//                        nextSymbol.rect.left - rawSymbol.rect.right < micrInfo.minimumCharWidth * 2 &&
//                        nextSymbol.rect.right - rawSymbol.rect.left < micrInfo.typicalHeight * 1.5
//                )
//                        ||
//                        (rawSymbol.rect.height() < micrInfo.typicalHeight * 0.8 &&
//                        nextSymbol.rect.left - rawSymbol.rect.right < micrInfo.minimumCharWidth * 2 &&
//                        nextSymbol.rect.right - rawSymbol.rect.left < micrInfo.typicalHeight * 1.5)) {
//                    //if we dont have first part of letter in oneCharRect and the width of the symbol says that that it is here
//                    oneCharRect = rawSymbol.rect;
//                    continue;
//                } else {
//                    //if everything is normal
//                    symbol = rawSymbol;
//
//                    if (symbol.confidence < 50 && symbol.confidence > 0) {
//                        // try to re-innerRecognize
//
//                        Rect oneCharRectWithBorders = CalcUtils.rectWithMargins(symbol.rect, 3, borderRect);
//                        singleCharRecognition.setRectangle(oneCharRectWithBorders);
//                        String s = singleCharRecognition.getUTF8Text().trim();
//
//                        if (s.length() > 0) {
//                            symbol.symbol = s;
//                            symbol.confidence = singleCharRecognition.getResultIterator().getChoicesAndConfidence(TessBaseAPI.PageIteratorLevel.RIL_SYMBOL).get(0).second;
//                        } else {
//                            symbol.symbol = "";
//                            symbol.confidence = 50.0;
//                            Log.w(LOGTAG, "bad recognition");
//                        }
//                    }
//                }
//            }
//
//
//            // at least 60%
//            if (symbol.confidence >= 60) {
//
//                boolean isAddSpace = false;
//                if (symbols.size() > 0) {
//                    double prevRight = symbols.get(symbols.size() - 1).rect.right;
//                    if (symbol.rect.left - prevRight > micrInfo.typicalWidth * 1.5) {
//
//                        Log.d(LOGTAG, "space: " + (symbol.rect.left - prevRight) + " - " + (micrInfo.typicalWidth * 1.5));
//
//                        isAddSpace = true;
//                    }
//                }
//
//                if (isAddSpace) {
//                    builder.append(" ");
//                }
//
//                builder.append(symbol.symbol);
//                symbols.add(symbol);
//                conf = conf + symbol.confidence;
//                if (symbol.confidence < minconf) {
//                    minconf = symbol.confidence;
//                }
//            }
//
//        }

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
