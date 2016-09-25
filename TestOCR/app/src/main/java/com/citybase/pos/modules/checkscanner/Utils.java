package com.citybase.pos.modules.checkscanner;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.Environment;
import android.util.Log;

import com.citybase.pos.modules.checkscanner.recognition.Symbol;

import java.io.File;
import java.io.FileOutputStream;
import java.util.List;
import java.util.Locale;

/**
 * Created by npakudin on 10/09/16
 */
public class Utils {


    private static final String TAG = "Utils";

    public static Bitmap drawRecText(Bitmap bm, Float scale, List<Symbol> symbols, String realText, int distance) {
        Canvas canvas = new Canvas(bm);
        float prevBottom = 0;
        String text = "";

        //scale *= 3;

        Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        Paint okTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        Paint confPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        Paint borderRectPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

        textPaint.setColor(Color.rgb(0xff, 0, 0));
        okTextPaint.setColor(Color.rgb(0xff, 0, 0));
        confPaint.setColor(Color.rgb(0, 0, 255));
        borderRectPaint.setColor(Color.rgb(0, 0, 0xff));

        borderRectPaint.setStyle(Paint.Style.STROKE);

        textPaint.setTextSize(bm.getHeight() / 15);
        canvas.drawText("Distance: " + distance, 30, 70, textPaint);

        for (int i = 0; i < symbols.size(); i++) {

            Symbol symbol = symbols.get(i);
            String realSymbol =  i < realText.length() ? realText.charAt(i) + "" : "";
            boolean isEq = false;
            if (symbol.symbol.equals(realSymbol)) {
                isEq = true;
            }

            textPaint.setTextSize   ((int) (symbol.rect.height() * scale / 3));
            okTextPaint.setTextSize ((int) (symbol.rect.height() * scale / 3));
            confPaint.setTextSize   ((int) (symbol.rect.height() * scale / 3));

            text = text + symbol.symbol;
            canvas.drawText(symbol.symbol, symbol.rect.left, symbol.rect.top, isEq ? okTextPaint : textPaint);

            String conf = String.format(Locale.ENGLISH, "%02.0f", symbol.confidence);
            canvas.drawText(conf, symbol.rect.left,  symbol.rect.bottom + (int)(symbol.rect.height() / 2), confPaint);

            if (Math.abs(symbol.rect.bottom - prevBottom) > 20) {
            }
            canvas.drawRect(symbol.rect, borderRectPaint);
            prevBottom = symbol.rect.bottom;

        }
        return bm;
    }

    public static boolean saveBitmap(Bitmap bm, String path, String name) {
        try {
            File pictureFile = getOutputMediaFile(path, name + ".jpg");

            if (pictureFile == null) {
                return true;
            }
            FileOutputStream fos = new FileOutputStream(pictureFile);
            bm.compress(Bitmap.CompressFormat.JPEG, 90, fos);
            fos.close();

            Log.w(TAG, "Pic saved to " + pictureFile.getAbsolutePath());
        } catch (Exception e) {
            Log.e(TAG, "Cannot save pic", e);
        }
        return false;
    }

    public static File getOutputMediaFile(String path, String name) {



        File mediaStorageDir = new File(Environment.getExternalStorageDirectory().getPath(), "Pictures/checks/" + path);

        if (!mediaStorageDir.exists()) {
            if (!mediaStorageDir.mkdirs()) {
                return null;
            }
        }

//        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
//        File mediaFile;
        File mediaFile = new File(mediaStorageDir.getPath() + File.separator + name);

        return mediaFile;
    }


    public static int levenshteinDistance(String recognizedText, String fileName) {

        recognizedText = recognizedText.replaceAll("_", " ");
        fileName = fileName.replaceAll("_", " ");

        char[] lhs = recognizedText.toCharArray();
        char[] rhs = fileName.toCharArray();
        int len0 = lhs.length + 1;
        int len1 = rhs.length + 1;
        int[] cost = new int[len0];
        int[] newcost = new int[len0];
        for (int i = 0; i < len0; i++) cost[i] = i;
        for (int j = 1; j < len1; j++) {
            newcost[0] = j;

            for (int i = 1; i < len0; i++) {
                int match = (lhs[i - 1] == rhs[j - 1]) ? 0 : 1;
                int cost_replace = cost[i - 1] + match;
                int cost_insert = cost[i] + 1;
                int cost_delete = newcost[i - 1] + 1;
                newcost[i] = Math.min(Math.min(cost_insert, cost_delete), cost_replace);
            }

            int[] swap = cost;
            cost = newcost;
            newcost = swap;
        }
        return cost[len0 - 1];
    }

}
