package com.example.npakudin.testocr.micr;

import android.graphics.Rect;
import android.util.Log;

import java.util.HashMap;

/**
 * Created by npakudin on 10/09/16
 */
public class CalcUtils {

    public static int findMostFrequentItem(HashMap<Integer, Integer> map) {
        int trueBorder = 0;
        int freq = 0;

        for (HashMap.Entry<Integer, Integer> entry : map.entrySet()) {
            if (entry.getValue() >= freq) {
                trueBorder = entry.getKey();
                freq = entry.getValue();
            }
        }
        return trueBorder;
    }

    public static int findTheLine(HashMap<Integer, Integer> map, int mostFrequentItem, int border) {
        int quantityOfRecognizedItems = 0;
        for (HashMap.Entry<Integer, Integer> entry : map.entrySet()) {
            if (entry.getKey() > mostFrequentItem - border && entry.getKey() < mostFrequentItem + border) {
                quantityOfRecognizedItems = quantityOfRecognizedItems + entry.getValue();
            }
        }
        return quantityOfRecognizedItems;
    }

    public static void putFrequency(HashMap<Integer, Integer> bottomFrequencies, int bottom) {

        Integer bottomNewFrequency = bottomFrequencies.get(bottom);
        bottomFrequencies.put(bottom, 1 + (bottomNewFrequency == null ? 0 : bottomNewFrequency));
    }

    public static Rect rectWithMargins(Rect srcRect, int margin, Rect borderRect) {

        return new Rect(
                Math.max(borderRect.left, srcRect.left - margin),
                Math.max(borderRect.top, srcRect.top - margin),
                Math.min(borderRect.right, srcRect.right + margin),
                Math.min(borderRect.bottom, srcRect.bottom + margin));
    }
}
