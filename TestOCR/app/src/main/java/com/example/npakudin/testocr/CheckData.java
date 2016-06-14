package com.example.npakudin.testocr;

import android.graphics.Bitmap;

import java.util.List;

public class CheckData {

    public int distance = -1;
    public String realText = "";
    public Bitmap res;
    public String wholeText = "";
    public List<TextRec.Symbol> symbols = null;

    public CheckData(Bitmap res, String wholeText, List<TextRec.Symbol> symbols) {
        this.res = res;
        this.wholeText = wholeText.replaceAll("\\s", "");
        this.symbols = symbols;
    }
}