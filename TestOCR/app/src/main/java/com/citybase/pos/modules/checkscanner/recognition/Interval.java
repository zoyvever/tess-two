package com.citybase.pos.modules.checkscanner.recognition;

import android.graphics.Rect;

/**
 * Created by npakudin on 26/09/16
 */
public class Interval {

    public Rect rect;
    public int blackPixelsCount;

    public Interval(Rect rect) {
        this.rect = rect;
    }
}
