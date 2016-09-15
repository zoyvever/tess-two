package com.example.npakudin.testocr.micr;

import android.graphics.Rect;
import android.util.Log;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by npakudin on 10/09/16
 */
public class CalcUtils {

    private static final String TAG = "CalcUtils";

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

    public static <T> void putStringFrequency(Map<String, Map<T, Integer>> charMap, String letter, T item) {

        Map<T, Integer> map = charMap.get(letter);
        if (map == null) {
            map = new HashMap<>();
            charMap.put(letter, map);
        }
        CalcUtils.putFrequency(map, item);
    }

    public static Rect rectWithMargins(Rect srcRect, int margin, Rect borderRect) {

        return new Rect(
                Math.max(borderRect.left, srcRect.left - margin),
                Math.max(borderRect.top, srcRect.top - margin),
                Math.min(borderRect.right, srcRect.right + margin),
                Math.min(borderRect.bottom, srcRect.bottom + margin));
    }


    public static void handleAvgDisp(Map<Integer, Integer> map, String letterItem) {

        List<Map.Entry<Integer, Integer>> freq = new ArrayList<>(map.entrySet());
        Collections.sort(freq, new Comparator<Map.Entry<Integer, Integer>>() {
            @Override
            public int compare(Map.Entry<Integer, Integer> lhs, Map.Entry<Integer, Integer> rhs) {
                return lhs.getValue() < rhs.getValue() ? -1 :
                        lhs.getValue() > rhs.getValue() ? 1 : 0;
            }
        });
        double avg = 0;
        int count = 0;
        for (Map.Entry<Integer, Integer> item : freq) {
            avg += item.getKey() * item.getValue();
            count += item.getValue();
        }
        avg = avg / count;
        double disp = 0;
        for (Map.Entry<Integer, Integer> item : freq) {
            disp += Math.pow(item.getKey() - avg, 2) * item.getValue();
        }
        disp = Math.sqrt(disp / (count * (count - 1)));


        Log.d(TAG, "LETTER, " + letterItem + ", " + avg + ", " + disp + ", " + count);

    }
}
