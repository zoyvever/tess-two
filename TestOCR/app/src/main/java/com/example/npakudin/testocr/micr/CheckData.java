package com.example.npakudin.testocr.micr;

import android.graphics.Bitmap;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CheckData {

    public boolean isOk;
    public int distance = -1;
    public String realText = "";
    public Bitmap res;
    public String wholeText = "";
    public String routingNumber = "";
    public String accountNumber = "";
    public String checkNumber = "";
    public double confidence = 0;
    String toCut = "";
    public List<Symbol> symbols = null;
    public String filename;
    public String descr;

    public CheckData(Bitmap res, String wholeText, List<Symbol> symbols, double confidence) {
        this.res = res;
        this.wholeText = wholeText;
        this.symbols = symbols;
        this.confidence=confidence;

        this.isOk = !wholeText.contains("%") && !wholeText.contains("#") && !wholeText.contains("@") &&
            confidence >= 55 && wholeText.length() >= 20;

        toCut = wholeText;
        routingNumber = findPattern("a\\d{9}(?!c|\\d)|\\d{9}a", "a");
        accountNumber = findPattern("\\d{1,15}c", "c");
        checkNumber = findPattern("\\d{1,10}", "");
    }

    public String findPattern(String pattern, String replace) {
        String s = "UNRECOGNIZED";
        Pattern pat = Pattern.compile(pattern);
        Matcher m = pat.matcher(toCut);

        while (m.find()) {
            s = m.group().replace(replace, "");
        }

        toCut = toCut.replaceAll(s, " ");
        return s;
    }
}