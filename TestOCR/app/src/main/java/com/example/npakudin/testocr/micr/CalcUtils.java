package com.example.npakudin.testocr.micr;

import android.util.Log;

import java.util.HashMap;

/**
 * Created by npakudin on 10/09/16
 */
public class CalcUtils {

    public static HashMap<Integer, Integer> fillTheMap(HashMap<Integer, Integer> map, Integer rect) {
        if (map.get(rect) != null) {
            map.put(rect, map.get(rect) + 1);
        } else {
            map.put(rect, 1);
        }
        return map;
    }

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

    public static int findTheLine(HashMap<Integer, Integer> map, int mostFrequentItem) {
        int quantityOfRecognizedItems = 0;
        for (HashMap.Entry<Integer, Integer> entry : map.entrySet()) {
            if (entry.getKey() > mostFrequentItem - 5 && entry.getKey() < mostFrequentItem + 5) {
                quantityOfRecognizedItems = quantityOfRecognizedItems + entry.getValue();
            }
        }
        return quantityOfRecognizedItems;
    }

}
