package com.citybase.pos.modules.checkscanner.recognition;

import android.graphics.Rect;

/**
 * Created by npakudin on 09/09/16
 */
public class Symbol {

    public String symbol;
    public double confidence;
    public Rect rect;

    public Symbol(String symbol, double confidence, Rect rect) {
        this.symbol = symbol;
        this.confidence = confidence;
        this.rect = rect;
    }

    public Symbol() {
    }

    @Override
    public String toString() {
        return "Symbol{" +
                "symbol='" + symbol + '\'' +
                ", confidence=" + confidence +
                ", rect=" + rect +
                '}';
    }
}