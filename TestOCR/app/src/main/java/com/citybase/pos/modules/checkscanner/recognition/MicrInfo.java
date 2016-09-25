package com.citybase.pos.modules.checkscanner.recognition;
/**
 * Created by npakudin on 09/09/16
 */
public class MicrInfo {

    public int typicalWidth;
    public int typicalHeight;
    public int typicalInterval;

    public MicrInfo(int typicalWidth, int typicalHeight, int typicalInterval) {
        this.typicalWidth = typicalWidth;
        this.typicalHeight = typicalHeight;
        this.typicalInterval = typicalInterval;
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
