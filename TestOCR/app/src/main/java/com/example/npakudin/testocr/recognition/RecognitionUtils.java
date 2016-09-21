package com.example.npakudin.testocr.recognition;

import android.graphics.Bitmap;
import android.graphics.Rect;

import java.util.Map;

/**
 * Created by npakudin on 10/09/16
 */
class RecognitionUtils {

    public static int findMostFrequentItem(Map<Integer, Integer> map) {
        int trueBorder = 0;
        int freq = 0;

        for (Map.Entry<Integer, Integer> entry : map.entrySet()) {
            if (entry.getValue() >= freq) {
                trueBorder = entry.getKey();
                freq = entry.getValue();
            }
        }
        return trueBorder;
    }

    public static int findTheLine(Map<Integer, Integer> map, int mostFrequentItem, int border) {
        int quantityOfRecognizedItems = 0;
        for (Map.Entry<Integer, Integer> entry : map.entrySet()) {
            if (entry.getKey() > mostFrequentItem - border && entry.getKey() < mostFrequentItem + border) {
                quantityOfRecognizedItems = quantityOfRecognizedItems + entry.getValue();
            }
        }
        return quantityOfRecognizedItems;
    }

    public static <T> void putFrequency(Map<T, Integer> frequencies, T item) {

        Integer newFrequency = frequencies.get(item);
        frequencies.put(item, 1 + (newFrequency == null ? 0 : newFrequency));
    }


    public static Bitmap cropBitmap(Bitmap bm, Rect rect) {

        return Bitmap.createBitmap(bm, rect.left, rect.top, rect.width(), rect.height());
    }

    public static Bitmap cropBitmap(Bitmap bm, int top, int bottom) {

        return Bitmap.createBitmap(bm, 0, top, bm.getWidth(), bottom - top);
    }
}
