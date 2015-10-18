package com.gbbtbb.graphviewerwidget;

import android.app.PendingIntent;
import android.app.PendingIntent.CanceledException;
import android.appwidget.AppWidgetManager;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.Log;
import android.widget.RemoteViews;
import android.widget.RemoteViewsService;

import com.gbbtbb.graphviewerwidget.GraphViewerDataProvider.Columns;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;

/**
 * This is the service that provides the factory to be bound to the collection service.
 */
public class GraphViewerWidgetService extends RemoteViewsService {
    @Override
    public RemoteViewsFactory onGetViewFactory(Intent intent) {
        return new StackRemoteViewsFactory(this.getApplicationContext(), intent);
    }
}

/**
 * This is the factory that will provide data to the collection widget.
 */
class StackRemoteViewsFactory implements RemoteViewsService.RemoteViewsFactory {
    private Context mContext;
    private Cursor mDataCursor;
    private HashMap<String, ArrayList<DataPoint>> mDataPoints;
    private String mDataRefreshDateTime;

    public StackRemoteViewsFactory(Context context, Intent intent) {
        mContext = context;
        mDataPoints = new HashMap();
        intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID);
    }

    public void onCreate() {
        // Initialize the list with some empty items, to make the list look good
        final MatrixCursor c = new MatrixCursor(new String[]{ Columns.DATAID, Columns.DATETIME, Columns.VALUE });
        mDataCursor = c;
    }

    public void onDestroy() {
        if (mDataCursor != null) {
            mDataCursor.close();
        }
    }

    public int getCount() {
        // the number of different KEYS in the mDataPoints is the number of different graphs to be displayed.
        // Add a few dummy entries, such that the widget looks ok even when there not any enough graphs to be
        // displayed to cover the whole height of the widget.
        if (mDataPoints != null)
            return (mDataPoints.size()+ GraphViewerWidgetProvider.NB_DUMMY_GRAPHS);
        else
            return GraphViewerWidgetProvider.NB_DUMMY_GRAPHS;
    }

    public void parseCursor() {
        Log.i(GraphViewerWidgetProvider.TAG, "---------------PARSING DATA ---------------");

        //////////////////////////////////////////////////////////
        // Parse the latest data refreshed by the content provider
        //////////////////////////////////////////////////////////

        // Clear previous data
        mDataPoints.clear();

        String dataId ="unknown dataset";
        String datetime = "Unknown timestamp";
        float value;

        mDataCursor.moveToFirst();

        // Among all entries retrieved for the requested time period, sort them into separate lists by dataId
        while (!mDataCursor.isAfterLast()) {

            final int dataIdColIndex = mDataCursor.getColumnIndex(Columns.DATAID);
            dataId = mDataCursor.getString(dataIdColIndex);

            // if this is a special entry and not a data point, handle it specifically
            if (dataId.equals(GraphViewerDataProvider.DATAREFRESH_DATETIME)) {
                final int valueColIndex = mDataCursor.getColumnIndex(Columns.VALUE);
                mDataRefreshDateTime = mDataCursor.getString(valueColIndex);
                Log.i(GraphViewerWidgetProvider.TAG, "Retrieved data datetime is " + mDataRefreshDateTime);
            }
            // handle graph data points
            else {
                // if an entry for this type of data does not exist yet in the map, add it
                if (!mDataPoints.containsKey(dataId)) {
                    mDataPoints.put(dataId, new ArrayList<DataPoint>());
                }

                ArrayList<DataPoint> dataPoints = mDataPoints.get(dataId);

                final int dateTimeColIndex = mDataCursor.getColumnIndex(Columns.DATETIME);
                datetime = mDataCursor.getString(dateTimeColIndex);

                final int valueColIndex = mDataCursor.getColumnIndex(Columns.VALUE);
                value = mDataCursor.getFloat(valueColIndex);

                dataPoints.add(new DataPoint(datetime, value));
            }

            // move to next item
            mDataCursor.moveToNext();
        }

        // DEBUG: verification
        Iterator<String> keySetIterator = mDataPoints.keySet().iterator();
        while(keySetIterator.hasNext()){
            String key = keySetIterator.next();
            Log.i(GraphViewerWidgetProvider.TAG,"VERIF: dataId: " + key);

            ArrayList<DataPoint> dataPoints = mDataPoints.get(key);
            for (DataPoint temp : dataPoints) {
                Log.i(GraphViewerWidgetProvider.TAG,"VERIF: datetime: " + temp.datetime +", value="+ temp.value + " (timestamp=" + Long.toString(temp.timestamp)+")");
            }
        }

        Log.i(GraphViewerWidgetProvider.TAG, "---------------DONE PARSING DATA---------------");
    }

    public RemoteViews getViewAt(int position) {

        Log.i(GraphViewerWidgetProvider.TAG, "getViewAt: " + Integer.toString(position));

        final int itemId = R.layout.widget_item;
        RemoteViews rv = new RemoteViews(mContext.getPackageName(), itemId);

        // Render graph into a bitmap and set this bitmap into the target imageview.
        rv.setImageViewBitmap(R.id.widget_item, generateGraph(position, "whatever"));

        // Set the click intent so that we can handle it
        // Do not set a click handler on dummy items that are there only to make the list look good when empty
        // TODO : adapt to not put click intent on empty graph slots
/*
        if (datetime.compareTo("")!=0) {
            final Intent fillInIntent = new Intent();
            final Bundle extras = new Bundle();
            extras.putString(GraphViewerWidgetProvider.EXTRA_ITEM_ID, datetime);
            fillInIntent.putExtras(extras);
            rv.setOnClickFillInIntent(R.id.widget_item, fillInIntent);
        }
*/
        return rv;
    }

    private Bitmap generateGraph(int graphId, String text) {

        int width =  (int)(GraphViewerWidgetProvider.mGraphWidth);
        int height = (int)(GraphViewerWidgetProvider.mGraphHeight);

        Log.i(GraphViewerWidgetProvider.TAG, "generateGraph for graphId=" + Integer.toString(graphId) + ", W=" + Integer.toString(width) + ", H=" + Integer.toString(height));

        // TODO: generate the appropriate graph based on position param

        Bitmap bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bmp);

        // Fill background with color
        Paint fillpaint = new Paint();
        /*
        switch(graphId%3) {
            case 0:
                fillpaint.setColor(Color.GREEN);
                //Log.i(GraphViewerWidgetProvider.TAG, "generate GREEN graph");
                break;
            case 1:
                fillpaint.setColor(Color.RED);
                //Log.i(GraphViewerWidgetProvider.TAG, "generate RED graph");
                break;

            case 2:
                fillpaint.setColor(Color.BLUE);
                //Log.i(GraphViewerWidgetProvider.TAG, "generate BLUE graph");
                break;
        }
        */

        //fillpaint.setColor(Color.BLACK);
        //fillpaint.setAlpha(50);
        //fillpaint.setStyle(Paint.Style.FILL);
        //canvas.drawPaint(fillpaint);
        Utilities.fillCanvas(canvas, Color.BLACK);

        // Generate on graph points from dataPoints
        String dataId;

        switch(graphId) {
            case 0:
                dataId = "waterMeterLogger";
                break;
            case 1:
                dataId = "pingStatus";
                break;
            default:
                dataId = "unknown";
                break;
        }

        if (mDataPoints != null && graphId < mDataPoints.size()) {

            ArrayList<DataPoint> dataPoints = mDataPoints.get(dataId);
            ArrayList<GraphPoint> graphPoints = new ArrayList<GraphPoint>();
            DataPoint firstDP = dataPoints.get(0);
            DataPoint lastDP = dataPoints.get(dataPoints.size() - 1);

            //long timerange = lastDP.timestamp - firstDP.timestamp;
            long timerange = GraphViewerWidgetProvider.timestamp_end - GraphViewerWidgetProvider.timestamp_start;

            for (DataPoint temp :  dataPoints)  {

                // For each datapoint in the list, generate a graph point at X coordinate proportional to timestamp over selected graph view range,
                // and Y coordinate corresponding to data value scaled to graph height
               // float x = width * (temp.timestamp - firstDP.timestamp)/ timerange;
                float x = width * (temp.timestamp - GraphViewerWidgetProvider.timestamp_start)/ timerange;
                float y = height * temp.value;

                graphPoints.add(new GraphPoint(x, y, Color.BLUE));

                Log.i(GraphViewerWidgetProvider.TAG,"Added graphPoint: x=" + Float.toString(x) +", y="+ Float.toString(y));
            }

            // Now draw the axis
            drawGraphAxis(canvas, width, height);

            // Draw some text on the graph
/*
            Paint paint = new Paint();
            paint.setAntiAlias(true);
            paint.setTextSize(35);
            paint.setColor(Color.RED);
           // paint.setStyle(Paint.Style.FILL);
            //paint.setStrokeWidth(50);
            String msg= String.format(Locale.getDefault(),
                    "%s [W=%d, H=%d] GraphID=%d",
                    text,
                    GraphViewerWidgetProvider.mGraphWidth,
                    GraphViewerWidgetProvider.mGraphHeight,
                    graphId);

            float textWidth = Utilities.getTextWidth(paint ,msg);
            float textHeight = Utilities.getTextHeight(paint, "0");
            canvas.drawText(msg, width - textWidth - 4, 0.5f* GraphViewerWidgetProvider.mGraphHeight + 0.5f*textHeight, paint);
*/





            // Draw rotated text
//        canvas.save();
//        canvas.rotate(-angle, centerPoint.x, centerPoint.y);
//        canvas.drawText(text, centerPoint.x-Math.abs(rect.exactCenterX()),
//        Math.abs(centerPoint.y-rect.exactCenterY()), paint);
//        canvas.restore();

            // draw the graph points themselves
            // except for dummy graph entries in the list

            drawGraphLine(graphPoints, canvas);
        }

        drawTimestampMarkers(canvas);
        return bmp;
    }

    private void drawGraphAxis(Canvas canvas, int width, int height) {
        Path path = new Path();
        path.moveTo(0, height/2);
        path.lineTo(width, height/2);
        Paint paint = new Paint();
        paint.setAntiAlias(true);
        paint.setARGB(128, 255, 0, 0);
        paint.setStyle(Paint.Style.STROKE);
        canvas.drawPath(path, paint);
    }

    private void drawGraphLine(List<GraphPoint> points, Canvas canvas) {
        Paint paint = new Paint();
        paint.setAntiAlias(true);
        paint.setStyle(Paint.Style.STROKE);
        paint.setAlpha(255);
        paint.setStrokeWidth(4.0f);
        Path path = null;
        int colour = Color.BLUE;
        for (GraphPoint pt : points) {
            if (pt.colour != colour || path == null) {
                if (path != null) {
                    path.lineTo(pt.x, pt.y);
                    paint.setColor(colour);
                    canvas.drawPath(path, paint);
                    colour = pt.colour;
                }
                path = new Path();
                path.moveTo(pt.x, pt.y);
            } else {
                path.lineTo(pt.x, pt.y);
            }
        }
        paint.setColor(colour);
        canvas.drawPath(path, paint);
    }

    private void drawTimestampMarkers(Canvas canvas) {

        Path path = new Path();

        Paint paint = new Paint();
        paint.setAntiAlias(true);
        paint.setARGB(255, 100, 100, 100);
        paint.setStrokeWidth(1.0f);
        paint.setStyle(Paint.Style.STROKE);

        paint.setARGB(255, 100, 100, 100);
        for (int i=0; i< GraphViewerWidgetProvider.NB_VERTICAL_MARKERS; i++) {
            float x = (0.5f+i)* GraphViewerWidgetProvider.mGraphWidth/ GraphViewerWidgetProvider.NB_VERTICAL_MARKERS;

            // Draw vertical marker at regular intervals
            path.moveTo(x, 0);
            path.lineTo(x, GraphViewerWidgetProvider.mGraphHeight);
            canvas.drawPath(path, paint);
       }
    }

    private static class DataPoint {
        public String datetime;
        public long timestamp;
        public float value;

        public DataPoint(String datetime, float value) {
            this.datetime = datetime;
            this.timestamp = Utilities.getTimeStampFromDateTime(datetime);
            this.value = value;
        }
    }

    private static class GraphPoint {
        public float x;
        public float y;
        public int colour;

        public GraphPoint(float x, float y, int colour) {
            this.x = x;
            this.y = y;
            this.colour = colour;
        }
    }

    public RemoteViews getLoadingView() {
        return null;
    }

    public int getViewTypeCount() {
        return 1;
    }

    public long getItemId(int position) {
        return position;
    }

    public boolean hasStableIds() {
        return true;
    }

    public void onDataSetChanged() {

        String dataId ="unknown dataset";
        String datetime = "Unknown timestamp";
        float value;

        Log.i(GraphViewerWidgetProvider.TAG, "onDataSetChanged called");

        // Refresh the cursors
        if (mDataCursor != null) {
            mDataCursor.close();
        }

        // TODO: specify time range in query

        mDataCursor = mContext.getContentResolver().query(GraphViewerDataProvider.CONTENT_URI_DATAPOINTS, null, null,null, null);

        parseCursor();

        // Notify RELOAD_ACTION_DONE event to WidgetProvider
        final Intent doneIntent = new Intent(mContext, GraphViewerWidgetProvider.class);
        doneIntent.setAction(GraphViewerWidgetProvider.RELOAD_ACTION_DONE);
        final PendingIntent donePendingIntent = PendingIntent.getBroadcast(mContext, 0, doneIntent, PendingIntent.FLAG_UPDATE_CURRENT);

        try {
            Log.i(GraphViewerWidgetProvider.TAG, "onDataSetChanged: launching pending Intent for loading done");
            donePendingIntent.send();
        }
        catch (CanceledException ce) {
            Log.i(GraphViewerWidgetProvider.TAG, "onDataSetChanged: Exception: "+ce.toString());
        }
    }
}