package com.example.npakudin.testocr.micr;

import android.graphics.Rect;

/**
 * Created by npakudin on 09/09/16
 */
public class MicrInfo {

    public int top = 0;
    public int bottom = 0;
    public int minimumCharWidth = 0;
    public int inLineRecognized = 0;

    public MicrInfo(int top, int bottom, int minimumCharWidth, int inLineRecognized) {
        this.top = top;
        this.bottom = bottom;
        this.minimumCharWidth = minimumCharWidth;
        this.inLineRecognized = inLineRecognized;
    }

    public boolean isInside(Rect rect) {
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
