package com.example.npakudin.testocr.recognition;

/**
 * Created by npakudin on 16/09/16
 */
public class Pair<T1, T2> {

    public final T1 first;
    public final T2 second;

    public Pair(T1 first, T2 second) {
        this.first = first;
        this.second = second;
    }

    @Override
    public String toString() {
        return "Pair{" +
                "first=" + first +
                ", second=" + second +
                '}';
    }
}
