package com.example.npakudin.testocr;

import android.graphics.Bitmap;
import android.util.Log;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CheckData {

    public int distance = -1;
    public String realText = "";
    public Bitmap res;
    public String wholeText = "";
    public String routingNumber = "";
    //    public String accountNumber="";
    public List<TextRec.Symbol> symbols = null;

    public CheckData(Bitmap res, String wholeText, List<TextRec.Symbol> symbols) {
        this.res = res;
        this.wholeText = wholeText;
        this.symbols = symbols;
        Pattern routingPattern = Pattern.compile("c\\d{9,10}|\\d{9,10}c");
        Matcher m = routingPattern.matcher(wholeText);
        while (m.find()) {
            routingNumber = m.group(0).replace("c", "");
        }
    }
}