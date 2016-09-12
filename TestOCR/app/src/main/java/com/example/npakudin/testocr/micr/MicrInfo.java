package com.example.npakudin.testocr.micr;

import android.graphics.Rect;

/**
 * Created by npakudin on 09/09/16
 */
public class MicrInfo {

    public int top = 0;
    public int bottom = 0;
    public int minimumCharWidth = 0;
    private int typicalWidth;
    public int typicalHeight = 0;
    public int inLineRecognized = 0;


    public MicrInfo() {

    }

    public MicrInfo(int top, int bottom, int minimumCharWidth,  int typicalWidth, int typicalHeight,int inLineRecognized) {
        this.top = top;
        this.bottom = bottom;
        this.minimumCharWidth = minimumCharWidth;
        this.typicalWidth = typicalWidth;
        this.typicalHeight = typicalHeight;
        this.inLineRecognized = inLineRecognized;
    }

    public boolean contains(Rect rect) {
        return rect.bottom < this.bottom && rect.top > this.top;
    }

    @Override
    public String toString() {
        return "MicrInfo{" +
                "top=" + top +
                ", bottom=" + bottom +
                ", minimumCharWidth=" + minimumCharWidth +
                ", inLineRecognized=" + inLineRecognized +
                '}';
    }
}
