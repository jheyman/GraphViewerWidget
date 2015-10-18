package com.gbbtbb.graphviewerwidget;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.util.Log;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Created by etabli on 08/09/15.
 */
public class Utilities {

   // public float mPixelDensity = 1.0f;

    public static long getTimeStampFromDateTime(String datetime) {

        long timestamp;
        // compute numerical timestamp value corresponding to datetime string
        //SimpleDateFormat sdf  = new SimpleDateFormat("yyyy-MM-dd kk:mm:ss.SSS");
        SimpleDateFormat sdf  = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        try {
            Date date = sdf.parse(datetime);
            timestamp = date.getTime();
        }
        catch (ParseException p) {
            Log.i(GraphViewerWidgetProvider.TAG, "ERROR in parsing date string: " + datetime);
            timestamp = 0;
        }

        return timestamp;
    }

    public static String getDateTimeFromTimeStamp(long timestamp) {
        Date date = new Date(timestamp);
        SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd' 'HH:mm:ss");
        return simpleDateFormat.format(date);
    }

    public static String getDateFromTimeStamp(long timestamp) {
        Date date = new Date(timestamp);
        SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd");
        return simpleDateFormat.format(date);
    }

    public static String getTimeFromTimeStamp(long timestamp) {
        Date date = new Date(timestamp);
        SimpleDateFormat simpleDateFormat = new SimpleDateFormat("HH:mm:ss");
        return simpleDateFormat.format(date);
    }

    public static float getTextWidth(Paint p, String text) {
        return p.measureText(text);
    }

    public static float getTextHeight(Paint p, String text) {
        Rect bounds = new Rect();
        p.getTextBounds(text, 0, 1, bounds);
        return bounds.height();
    }

    public static void fillCanvas( Canvas canvas, int color) {
        Paint fillpaint = new Paint();
        fillpaint.setColor(color);
        fillpaint.setStyle(Paint.Style.FILL);
        canvas.drawPaint(fillpaint);
    }

    /*
    public int dipToPixels(float dipValue) {
        return (int) Math.ceil(dipValue * mPixelDensity);
    }

    public float pixelsToDip(int pixelValue) {
        return pixelValue/mPixelDensity;
    }
*/

}
