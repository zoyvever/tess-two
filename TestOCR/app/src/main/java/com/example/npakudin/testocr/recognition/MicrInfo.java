package com.example.npakudin.testocr.recognition;

/**
 * Created by npakudin on 09/09/16
 */
public class MicrInfo {

    public int typicalWidth;
    public int typicalHeight = 0;

    public MicrInfo(int typicalWidth, int typicalHeight) {
        this.typicalWidth = typicalWidth;
        this.typicalHeight = typicalHeight;
    }

    @Override
    public String toString() {
        return "MicrInfo{" +
                "typicalWidth=" + typicalWidth +
                ", typicalHeight=" + typicalHeight +
                '}';
    }
}
