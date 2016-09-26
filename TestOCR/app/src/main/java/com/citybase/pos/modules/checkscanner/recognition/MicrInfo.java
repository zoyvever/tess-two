package com.citybase.pos.modules.checkscanner.recognition;

import android.graphics.Rect;

/**
 * Created by npakudin on 09/09/16
 */
public class MicrInfo {

    public int typicalWidth;
    public int typicalHeight;
    public int typicalInterval;
    public Rect borders;

    public MicrInfo(int typicalWidth, int typicalHeight, int typicalInterval, Rect borders) {
        this.typicalWidth = typicalWidth;
        this.typicalHeight = typicalHeight;
        this.typicalInterval = typicalInterval;
        this.borders = borders;
    }

    public MicrInfo() {
        
    }


    @Override
    public String toString() {
        return "MicrInfo{" +
                "typicalWidth=" + typicalWidth +
                ", typicalHeight=" + typicalHeight +
                ", typicalInterval=" + typicalInterval +
                '}';
    }
}
