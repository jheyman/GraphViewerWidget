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
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.text.TextPaint;
import android.util.Log;
import android.widget.RemoteViews;
import android.widget.RemoteViewsService;

import com.gbbtbb.graphviewerwidget.GraphViewerDataProvider.Columns;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

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
    private HashMap<String, float[]> mCumulatedValues;
    private HashMap<String, GraphParameters> mGraphParams;

    private String mDataRefreshDateTime;
    private final String DATA_ID_NONE="NONE";
    private final String DATA_ID_INTERGRAPH_BLANK="INTERBLANK";
    private final String DATA_ID_FINALFILLER="FINALFILLER";

    private final int INTERGRAPH_BLANK_HEIGHT = 10;
    private final int FINALFILLER_HEIGHT = 500;
    private final float LINEPLOT_DOTSIZE = 5.0f;

    public StackRemoteViewsFactory(Context context, Intent intent) {
        mContext = context;
        mDataPoints = new HashMap();
        mCumulatedValues = new HashMap();
        mGraphParams = new HashMap();
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
        // - The number of different KEYS in the mDataPoints is the number of different graphs to be displayed.
        // - Add the nb of blank graphs inserted after each actual graph
        // - Add a final filler/empty graph at the bottom in any case such that the list looks good even when partially empty
        if (mDataPoints != null)
            return (mDataPoints.size() + mDataPoints.size() + 1);
        else
            return 1;
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

        // Parse each graph points, to figure out optimal scaling for display.
        Iterator<String> keySetIterator = mDataPoints.keySet().iterator();
        while(keySetIterator.hasNext()){
            String key = keySetIterator.next();
            Log.i(GraphViewerWidgetProvider.TAG,"dataId: " + key );

            ArrayList<DataPoint> dataPoints = mDataPoints.get(key);
            Log.i(GraphViewerWidgetProvider.TAG, "dataId: " + Integer.toString(dataPoints.size()) + " elements");
            float maxValue=-1.0f;
            float[] cv = new float[GraphViewerWidgetProvider.NB_VERTICAL_MARKERS+1];

            for (int i=0; i < cv.length; i++) {
                cv[i]= 0.0f;
            }

            long delta = (GraphViewerWidgetProvider.timestamp_end - GraphViewerWidgetProvider.timestamp_start)/(GraphViewerWidgetProvider.NB_VERTICAL_MARKERS+1);

            //Log.i(GraphViewerWidgetProvider.TAG,"timestamp_start=" + Long.toString(GraphViewerWidgetProvider.timestamp_start));
            //Log.i(GraphViewerWidgetProvider.TAG,"timestamp_end=" + Long.toString(GraphViewerWidgetProvider.timestamp_end));
           // Log.i(GraphViewerWidgetProvider.TAG,"delta=" + Long.toString(delta));

            for (DataPoint temp : dataPoints) {
                //Log.i(GraphViewerWidgetProvider.TAG,"datetime: " + temp.datetime +", value="+ temp.value + " (timestamp=" + Long.toString(temp.timestamp)+")");
                if (temp.value > maxValue)
                    maxValue = temp.value;

                int index= (int)((temp.timestamp - GraphViewerWidgetProvider.timestamp_start) / delta);

                //Log.i(GraphViewerWidgetProvider.TAG,"x=" + Long.toString(temp.timestamp - GraphViewerWidgetProvider.timestamp_start));

                //Log.i(GraphViewerWidgetProvider.TAG,"timestamp=" + Long.toString(temp.timestamp)+", Histoindex="+Integer.toString(index));
                cv[(int)((temp.timestamp - GraphViewerWidgetProvider.timestamp_start) / delta)]+=temp.value;
                Log.i(GraphViewerWidgetProvider.TAG,key+ ": adding value " + Float.toString(temp.value)+" to histo index "+Integer.toString(index));
            }

            // Adjust this graph setting.
            GraphParameters gp = new GraphParameters();
            gp.scale = 1.2f * maxValue; // Scale the graph to allow 20% margin between the max value and the top of the graph.
            gp.type = getGraphTypeForDataId(key);
            gp.height = getGraphHeightForDataId(key);
            gp.unit = getGraphUnitForDataId(key);
            mGraphParams.put(key,gp);

            mCumulatedValues.put(key, cv);

            Log.i(GraphViewerWidgetProvider.TAG, "graph params: auto-scale = " + Float.toString(gp.scale) + ", type=" + gp.type);
        }

        Log.i(GraphViewerWidgetProvider.TAG, "---------------DONE PARSING DATA---------------");
    }

    public RemoteViews getViewAt(int position) {

        //Log.i(GraphViewerWidgetProvider.TAG, "getViewAt: " + Integer.toString(position));

        final int itemId = R.layout.widget_item;
        RemoteViews rv = new RemoteViews(mContext.getPackageName(), itemId);

        // Render graph into a bitmap and set this bitmap into the target imageview.
        rv.setImageViewBitmap(R.id.widget_item, generateGraph(position));

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
    private int getGraphHeightForDataId (String dataID) {

        int height=0;
        // TODO: make this configurable.
        switch(dataID) {
            case "camerapi_ping":
            case "sdbtbbpi_ping":
            case "cuisinepi_ping":
                height =50;
                break;
            case "waterMeter":
                height = 150;
                break;
            default:
                height = 100;
                break;
        }

        return height;
    }

    private String getGraphUnitForDataId (String dataID) {

        String unit;
        // TODO: make this configurable.
        switch(dataID) {
            case "camerapi_ping":
            case "sdbtbbpi_ping":
            case "cuisinepi_ping":
                unit = "";
                break;
            case "waterMeter":
                unit = "l";
                break;
            default:
                unit = "";
                break;
        }

        return unit;
    }

    private GraphType getGraphTypeForDataId (String dataID) {

        GraphType gt;
        // TODO: make this configurable.
        switch(dataID) {
            case "camerapi_ping":
            case "sdbtbbpi_ping":
            case "cuisinepi_ping":
                gt = GraphType.binarystatus;
                break;
            case "waterMeter":
                gt = GraphType.bargraph;
                break;
            default:
                gt = GraphType.lineplot;
                break;
        }

        return gt;
    }

    private String getDataIdForPosition (int position) {

        String dataId;
        // TODO: make this configurable.
        switch(position) {
            case 0:
                dataId = "waterMeter";
                break;
            case 2:
                dataId = "camerapi_ping";
                break;
            case 4:
                dataId = "sdbtbbpi_ping";
                break;
            case 6:
                dataId = "cuisinepi_ping";
                break;
            case 1:
            case 3:
            case 5:
            case 7:
                dataId = DATA_ID_INTERGRAPH_BLANK;
                break;
            default:
                dataId = DATA_ID_NONE;
                break;
        }

        return dataId;
    }

    private Bitmap generateGraph(int position) {

        int width =  (int)(GraphViewerWidgetProvider.mGraphWidth);

        // Figure out what data to plot in this graph
        String dataId = getDataIdForPosition(position);

        Log.i(GraphViewerWidgetProvider.TAG, "generateGraph for position=" + Integer.toString(position) + ", dataId retrieved is " + dataId );

        GraphParameters gp;
        float[] cumulatedValues;

        gp = mGraphParams.get(dataId);

        if (gp == null) {

            Log.e(GraphViewerWidgetProvider.TAG, "GraphParams not found for dataID=" + dataId + ", setting default values");

            gp = new GraphParameters();
        } else {
            Log.i(GraphViewerWidgetProvider.TAG, "GraphParams found for dataID=" + dataId + ", scale is " + Float.toString(gp.scale) + ", type is " + gp.type.name());
        }

        cumulatedValues = mCumulatedValues.get(dataId);

        if (cumulatedValues == null) {
            Log.e(GraphViewerWidgetProvider.TAG, "Cumulated values  not found for dataID=" + dataId);

            cumulatedValues = new float[GraphViewerWidgetProvider.NB_VERTICAL_MARKERS+1];
            for(int i=0; i < GraphViewerWidgetProvider.NB_VERTICAL_MARKERS+1; i++) {
                cumulatedValues[i] = 0.0f;
            }
        } else {
            Log.i(GraphViewerWidgetProvider.TAG, "Cumulated values found for dataID=" + dataId + ", length=" +Integer.toString(cumulatedValues.length));
        }

        // Retrieve params for this series
        if (dataId.equals(DATA_ID_INTERGRAPH_BLANK)) {
            gp = new GraphParameters();
            gp.height = INTERGRAPH_BLANK_HEIGHT;
        } else if (dataId.equals(DATA_ID_FINALFILLER)) {
            gp = new GraphParameters();
            gp.height = FINALFILLER_HEIGHT;
        }

        Log.i(GraphViewerWidgetProvider.TAG, "W=" + Integer.toString(width) + ", H=" + Integer.toString(gp.height));

        Bitmap bmp = Bitmap.createBitmap(width, gp.height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bmp);

        Utilities.fillCanvas(canvas, mContext.getResources().getColor(R.color.background_color));

        // Generate graph points for this graphID, from data points corresponding to dataId defined above.
        if (!dataId.equals(DATA_ID_NONE) & mDataPoints != null) {

            ArrayList<DataPoint> dataPoints = mDataPoints.get(dataId);
            ArrayList<GraphPoint> graphPoints = new ArrayList<GraphPoint>();

            long timerange = GraphViewerWidgetProvider.timestamp_end - GraphViewerWidgetProvider.timestamp_start;

            if (dataPoints != null) {
                for (DataPoint temp : dataPoints) {

                    // For each datapoint in the list, generate a graph point at X coordinate proportional to timestamp over selected graph view range,
                    // and Y coordinate corresponding to data value scaled to graph height
                    float x = width * (temp.timestamp - GraphViewerWidgetProvider.timestamp_start) / timerange;
                    float y = gp.height * temp.value / gp.scale;

                    graphPoints.add(new GraphPoint(x, y, mContext.getResources().getColor(R.color.lineplot_color)));
                }

                // draw the horizontal/base axis for this graph
                drawGraphAxis(canvas, width, gp.height);

                // Draw rotated text
//        canvas.save();
//        canvas.rotate(-angle, centerPoint.x, centerPoint.y);
//        canvas.drawText(text, centerPoint.x-Math.abs(rect.exactCenterX()),
//        Math.abs(centerPoint.y-rect.exactCenterY()), paint);
//        canvas.restore();

                // draw the graph points themselves
                switch (gp.type) {
                    case bargraph:
                        drawBarGraph(graphPoints, cumulatedValues, gp.unit, canvas, width, gp.height);
                        break;
                    case bargraph_and_lineplot:
                        drawBarGraph(graphPoints, cumulatedValues, gp.unit, canvas, width, gp.height);
                        drawLinePlot(graphPoints, canvas, width, gp.height);
                        break;
                    case lineplot:
                        drawLinePlot(graphPoints, canvas, width, gp.height);
                        break;
                    case binarystatus:
                        drawBinaryGraph(graphPoints, canvas, width, gp.height);
                        break;
                }

                // Draw some text on the graph
                drawGraphTitle(canvas, dataId);

            }
            else {
                Log.i(GraphViewerWidgetProvider.TAG, "ERROR: no dataPoints available for dataId " + dataId );
            }
        }
        drawTimestampMarkers(canvas, width, gp.height);
        return bmp;
    }

    private void drawGraphTitle(Canvas canvas, String dataId) {
        Paint paint = new Paint();
        paint.setAntiAlias(true);
        paint.setTextSize(10);
        paint.setColor(mContext.getResources().getColor(R.color.graph_label_text_color));
        String msg= String.format(Locale.getDefault(),"%s", dataId);

        float textWidth = Utilities.getTextWidth(paint, msg);
        float textHeight = Utilities.getTextHeight(paint, "0");

        Paint boxpaint = new Paint();
        boxpaint.setAntiAlias(true);
        boxpaint.setColor(mContext.getResources().getColor(R.color.graph_label_text_background));
        boxpaint.setStyle(Paint.Style.FILL);

        // Draw text brackground, and then text itself
        canvas.drawRect(0, 0, textWidth + 10, textHeight + 10, boxpaint);
        canvas.drawText(msg, 5, 5 + 0.5f * textHeight, paint);
    }

    private void drawGraphAxis(Canvas canvas, int width, int height) {
        Path path = new Path();
        path.moveTo(0, height);
        path.lineTo(width, height);
        Paint paint = new Paint();
        paint.setAntiAlias(true);
        paint.setColor(mContext.getResources().getColor(R.color.axis_color));
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(3.0f);
        canvas.drawPath(path, paint);
    }

    private void drawLinePlot(List<GraphPoint> points, Canvas canvas, int width, int height) {
        Paint paint = new Paint();
        paint.setAntiAlias(true);
        paint.setStyle(Paint.Style.STROKE);
        paint.setAlpha(255);
        paint.setStrokeWidth(4.0f);
        Path path = null;

        int colour = mContext.getResources().getColor(R.color.lineplot_color);
        for (GraphPoint pt : points) {
            if (pt.colour != colour || path == null) {
                if (path != null) {
                    path.lineTo(pt.x, height - pt.y);
                    paint.setColor(colour);
                    canvas.drawPath(path, paint);
                    colour = pt.colour;
                }
                path = new Path();
                path.moveTo(pt.x, height - pt.y);
            } else {
                path.lineTo(pt.x, height- pt.y);
            }

            Paint pointpaint = new Paint();
            pointpaint.setAntiAlias(true);
            pointpaint.setColor(colour);
            pointpaint.setStyle(Paint.Style.FILL);
            canvas.drawCircle(pt.x, height-pt.y, LINEPLOT_DOTSIZE, pointpaint);
        }
        paint.setColor(colour);
        canvas.drawPath(path, paint);
    }

    private void drawBarGraph(List<GraphPoint> points, float[] cumulatedValues, String unit, Canvas canvas, int width, int height) {

        int barcolour = mContext.getResources().getColor(R.color.bargraph_color);

        float bar_width;
        if (points.size()>1) {

            GraphPoint first = points.get(0);
            GraphPoint last = points.get(points.size() - 1);

            bar_width = (last.x - first.x) / (points.size() - 1);
        } else {
            // Arbitrarily set the unique bar to 10% of total width
            bar_width = 0.1f*width;
        }

        for (GraphPoint pt : points) {

            Paint barpaint = new Paint();
            barpaint.setAntiAlias(true);
            barpaint.setColor(barcolour);
            barpaint.setStyle(Paint.Style.FILL);

            // Draw text background, and then text itself
            canvas.drawRect(pt.x - 0.5f * bar_width, height, pt.x + 0.5f * bar_width, height - pt.y, barpaint);
        }

        // Then draw text statistics for each time sub-region
        TextPaint textPaint = new TextPaint();
        textPaint.setStyle(Paint.Style.FILL);
        textPaint.setTextSize(12);
        textPaint.setColor(barcolour);

        textPaint.setTextAlign(Paint.Align.LEFT);
        textPaint.setAntiAlias(true);
        textPaint.setSubpixelText(true);

        String latestDatePrinted = "";

        for (int i=0; i<GraphViewerWidgetProvider.NB_VERTICAL_MARKERS+1; i++) {
            float x = (0.5f+i)*width/(GraphViewerWidgetProvider.NB_VERTICAL_MARKERS+1);

            // On bottom half of the footer, under each marker, print corresponding date and time
            //Rect bounds = new Rect();
            float textHeight = Utilities.getTextHeight(textPaint, "0");
            float textWidth;
            String text = Float.toString(cumulatedValues[i])+unit;

            textWidth = Utilities.getTextWidth(textPaint, text);
            canvas.drawText(text, x - 0.5f*textWidth, (0.125f)*height - 0.5f*textHeight, textPaint);
        }
    }

    private void drawBinaryGraph(List<GraphPoint> points, Canvas canvas, int width, int height) {

        Paint barpaint = new Paint();
        barpaint.setAntiAlias(true);
        barpaint.setStyle(Paint.Style.FILL);

        int pointIndex = 0;

        for (GraphPoint pt : points) {
            barpaint.setColor(pt.y > 0.0f ? Color.GREEN: Color.RED);
            if (pointIndex == 0) {
                canvas.drawRect(0, height, pt.x, 0, barpaint);
            } else if (pointIndex < points.size()-1) {
                canvas.drawRect(pt.x, height, points.get(pointIndex+1).x, 0, barpaint);
            } else {
                canvas.drawRect(pt.x, height, width, 0, barpaint);
            }

            pointIndex++;
        }
    }

    private void drawTimestampMarkers(Canvas canvas, int width, int height) {

        Path path = new Path();

        Paint paint = new Paint();
        paint.setAntiAlias(true);
        paint.setARGB(127, 100, 100, 100);
        paint.setStrokeWidth(1.0f);
        paint.setPathEffect(new DashPathEffect(new float[]{2, 2}, 0));
        paint.setStyle(Paint.Style.STROKE);

        paint.setARGB(255, 100, 100, 100);
        for (int i=0; i< GraphViewerWidgetProvider.NB_VERTICAL_MARKERS; i++) {
            float x = (1.0f+i)* width/ (GraphViewerWidgetProvider.NB_VERTICAL_MARKERS+1);

            // Draw vertical marker at regular intervals
            path.moveTo(x, 0);
            path.lineTo(x, height);
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

    public enum GraphType {
        lineplot,
        bargraph,
        bargraph_and_lineplot,
        binarystatus
    }

    private static class GraphParameters {
        public float scale;
        public GraphType type;
        public int height;
        public String unit;

        public GraphParameters() {
            this.scale = 1.0f;
            this.type = GraphType.lineplot;
            this.height = 300;
            this.unit ="";
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

        String[] args = new String[1];
        args[0] = Integer.toString(GraphViewerWidgetProvider.mHistoryLengthInHours);

        mDataCursor = mContext.getContentResolver().query(GraphViewerDataProvider.CONTENT_URI_DATAPOINTS, null, null,args, null);

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