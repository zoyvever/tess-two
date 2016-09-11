package com.example.npakudin.testocr.micr;

import android.graphics.Rect;

/**
 * Created by npakudin on 09/09/16
 */
public class Symbol {

    public String symbol;
    public double сonfidence;
    public Rect rect;

    @Override
    public String toString() {
        return "Symbol{" +
                "symbol='" + symbol + '\'' +
                ", сonfidence=" + сonfidence +
                ", rect=" + rect +
                '}';
    }
}