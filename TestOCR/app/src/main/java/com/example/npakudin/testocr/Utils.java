package com.example.npakudin.testocr;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;

import java.util.List;
import java.util.Locale;

/**
 * Created by npakudin on 10/09/16
 */
public class Utils {


    public static Bitmap drawRecText(Bitmap bm, Float scale, List<Symbol> symbols) {
        Canvas canvas = new Canvas(bm);
        float prevBottom = 0;
        String text = "";
        Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        textPaint.setColor(Color.rgb(0xff, 0, 0));
        for (Symbol symbol : symbols) {
            textPaint.setTextSize((int) (symbol.rect.height() * scale / 1.5));
            text = text + symbol.symbol;
            canvas.drawText(symbol.symbol, symbol.rect.left, symbol.rect.top - (symbol.rect.height() + 10), textPaint);

            String conf = String.format(Locale.ENGLISH, "%02.0f", symbol.сonfidence);
            Paint confPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            confPaint.setColor(Color.rgb(0, 0, 255));
            confPaint.setTextSize((int) (symbol.rect.height() * scale / 3));
            canvas.drawText(conf, symbol.rect.left, symbol.rect.top - (symbol.rect.height() - 10), confPaint);

            Paint borderRectPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            borderRectPaint.setColor(Color.rgb(0, 0, 0xff));
            borderRectPaint.setStyle(Paint.Style.STROKE);
            if (Math.abs(symbol.rect.bottom - prevBottom) > 20) {
            }
            canvas.drawRect(symbol.rect, borderRectPaint);
            prevBottom = symbol.rect.bottom;

        }
        return bm;
    }

}
