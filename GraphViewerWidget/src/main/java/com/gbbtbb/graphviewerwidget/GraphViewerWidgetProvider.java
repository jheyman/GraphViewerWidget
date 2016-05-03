package com.gbbtbb.graphviewerwidget;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Paint.Align;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.support.annotation.Nullable;
import android.text.TextPaint;
import android.util.Log;
import android.view.View;
import android.widget.RemoteViews;

/**
 * The widget's AppWidgetProvider.
 */
public class GraphViewerWidgetProvider extends AppWidgetProvider {
    public static final String TAG = "GraphViewerViewer";

    public static final String RELOAD_ACTION = "com.gbbtbb.graphviewerviewerwidget.RELOAD_LIST";
    public static final String CUSTOM_REFRESH_ACTION = "com.gbbtbb.graphviewerwidget.UpdateAction";
    public static final String GRAPHMEASURE_ACTION = "com.gbbtbb.graphviewerviewerwidget.GRAPHMEASURE_ACTION";
    public static final String HEADERMEASURE_ACTION = "com.gbbtbb.graphviewerviewerwidget.HEADERMEASURE_ACTION";
    public static final String FOOTERMEASURE_ACTION = "com.gbbtbb.graphviewerviewerwidget.FOOTERMEASURE_ACTION";

    public static final String SETTING_BASE = "com.gbbtbb.graphviewerviewerwidget";
    public static final String SETTING_GRAPHWIDTH = "com.gbbtbb.graphviewerviewerwidget.graphwidth";
    public static final String SETTING_GRAPHHEIGHT = "com.gbbtbb.graphviewerviewerwidget.graphheight";
    public static final String SETTING_HEADERWIDTH = "com.gbbtbb.graphviewerviewerwidget.headerwidth";
    public static final String SETTING_HEADERHEIGHT = "com.gbbtbb.graphviewerviewerwidget.headerheight";
    public static final String SETTING_FOOTERHEIGHT = "com.gbbtbb.graphviewerviewerwidget.footerheight";

    public static final int DEFAULT_WIDTH = 1000;
    public static final int DEFAULT_HEIGHT = 1000;
    public static final int DEFAULT_HEADERHEIGHT = 50;
    public static final int DEFAULT_HEADERWIDTH = 200;
    public static final int DEFAULT_FOOTERHEIGHT = 50;
    public static final int NB_VERTICAL_MARKERS = 15;

    private static boolean progressBarEnabled = false;

    // Width of graph in pixels
    public static int mGraphWidth;
    public static int mGraphHeight;
    public static int mHeaderHeight;
    public static int mHeaderWidth;
    public static int mFooterHeight;

    public static int mHistoryLengthInHours;

    // beginning and end timestamps specifying the actual time range to be visualized
    public static long timestamp_start;
    public static long timestamp_end;

    private String timeLastUpdated;

    private Settings mSettings;

    public static void notifyRefresh(Context context, @Nullable int[] appWidgetIds) {
        Intent i = new Intent(context, GraphViewerWidgetProvider.class);
        i.setAction(GraphViewerWidgetProvider.CUSTOM_REFRESH_ACTION);
        i.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, appWidgetIds);
        context.sendBroadcast(i);
    }

    public GraphViewerWidgetProvider() {
    }

    @Override
    public void onDeleted(Context context, int[] appWidgetIds) {
        super.onDeleted(context, appWidgetIds);
        Log.i(GraphViewerWidgetProvider.TAG, "onDeleted");
    }

    @Override
    public void onEnabled(Context context) {
        Log.i(GraphViewerWidgetProvider.TAG, "onEnabled");

        getPrefs(context);
    }

    private void getPrefs(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(SETTING_BASE, Context.MODE_PRIVATE);

        mGraphWidth = prefs.getInt(SETTING_GRAPHWIDTH, DEFAULT_WIDTH);
        Log.i(GraphViewerWidgetProvider.TAG, "GraphWidth retrieved from Prefs (" + Integer.toString(mGraphWidth)+ ")");

        mGraphHeight = prefs.getInt(SETTING_GRAPHHEIGHT, DEFAULT_HEIGHT);
        Log.i(GraphViewerWidgetProvider.TAG, "GraphHeight retrieved from Prefs (" + Integer.toString(mGraphHeight)+ ")");

        mHeaderWidth = prefs.getInt(SETTING_HEADERWIDTH, DEFAULT_HEADERWIDTH);
        Log.i(GraphViewerWidgetProvider.TAG, "HeaderWidth retrieved from Prefs (" + Integer.toString(mHeaderWidth)+ ")");

        mHeaderHeight = prefs.getInt(SETTING_HEADERHEIGHT, DEFAULT_HEADERHEIGHT);
        Log.i(GraphViewerWidgetProvider.TAG, "HeaderHeight retrieved from Prefs (" + Integer.toString(mHeaderHeight)+ ")");

        mFooterHeight = prefs.getInt(SETTING_FOOTERHEIGHT, DEFAULT_FOOTERHEIGHT);
        Log.i(GraphViewerWidgetProvider.TAG, "FooterHeight retrieved from Prefs (" + Integer.toString(mFooterHeight)+ ")");
    }

    @Override
    public void onReceive(Context ctx, Intent intent) {
        final String action = intent.getAction();

        Log.i(GraphViewerWidgetProvider.TAG, "onReceive: " + action);

        mSettings = Settings.get(ctx);

        if (action.equals(RELOAD_ACTION) || action.equals(AppWidgetManager.ACTION_APPWIDGET_UPDATE) || action.equals(CUSTOM_REFRESH_ACTION)) {

            progressBarEnabled = true;

            AppWidgetManager widgetManager = AppWidgetManager.getInstance(ctx);
            ComponentName widgetComponent = new ComponentName(ctx, GraphViewerWidgetProvider.class);
            int[] widgetIds = widgetManager.getAppWidgetIds(widgetComponent);
            onUpdate(ctx, widgetManager, widgetIds);

        } else if (action.equals(GRAPHMEASURE_ACTION)) {
            Log.i(GraphViewerWidgetProvider.TAG, "GRAPHMEASURE_ACTION" );

            Rect r = intent.getSourceBounds();

            if (r != null) {
                mGraphWidth = r.right - r.left;
                mGraphHeight = r.bottom - r.top;

                SharedPreferences prefs = ctx.getSharedPreferences(SETTING_BASE, Context.MODE_PRIVATE);
                prefs.edit().putInt(SETTING_GRAPHWIDTH, mGraphWidth).commit();
                prefs.edit().putInt(SETTING_GRAPHHEIGHT, mGraphHeight).commit();

                Log.i(GraphViewerWidgetProvider.TAG, "GRAPH WIDTH and HEIGHT auto-adjusted to (" + Integer.toString(mGraphWidth)+ ", " + Integer.toString(mGraphHeight)+")");
            }
        } else if (action.equals(HEADERMEASURE_ACTION)) {
            Log.i(GraphViewerWidgetProvider.TAG, "HEADERMEASURE_ACTION" );

            Rect r = intent.getSourceBounds();

            if (r != null) {
                mHeaderWidth = r.right - r.left;
                mHeaderHeight = r.bottom - r.top;

                SharedPreferences prefs = ctx.getSharedPreferences(SETTING_BASE, Context.MODE_PRIVATE);
                prefs.edit().putInt(SETTING_HEADERWIDTH, mHeaderWidth).commit();
                prefs.edit().putInt(SETTING_HEADERHEIGHT, mHeaderHeight).commit();

                Log.i(GraphViewerWidgetProvider.TAG, "HEADER HEIGHT auto-adjusted to (" + Integer.toString(mHeaderHeight)+ ")");
            }
        } else if (action.equals(FOOTERMEASURE_ACTION)) {
            Log.i(GraphViewerWidgetProvider.TAG, "FOOTERMEASURE_ACTION" );

            Rect r = intent.getSourceBounds();

            if (r != null) {
                mFooterHeight = r.bottom - r.top;

                SharedPreferences prefs = ctx.getSharedPreferences(SETTING_BASE, Context.MODE_PRIVATE);
                prefs.edit().putInt(SETTING_FOOTERHEIGHT, mFooterHeight).commit();

                Log.i(GraphViewerWidgetProvider.TAG, "FOOTER HEIGHT auto-adjusted to (" + Integer.toString(mFooterHeight)+ ")");
            }
        }

        super.onReceive(ctx, intent);
    }

    private Bitmap drawCommonHeader(Context ctx, int width, int height) {
        Bitmap bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bmp);

        Typeface myfont = Typeface.createFromAsset(ctx.getAssets(), "passing_notes.ttf");

        TextPaint textPaint = new TextPaint();
        textPaint.setTypeface(myfont);
        textPaint.setStyle(Paint.Style.FILL);
        textPaint.setTextSize(ctx.getResources().getDimension(R.dimen.header_text_size));
        textPaint.setColor(ctx.getResources().getColor(R.color.text_color));

        textPaint.setTextAlign(Align.LEFT);
        textPaint.setAntiAlias(true);
        textPaint.setSubpixelText(true);

        float textHeight = Utilities.getTextHeight(textPaint, "r");
        canvas.drawText(ctx.getResources().getString(R.string.header_text) + ": " + mHistoryLengthInHours + " heures", 10.0f, 0.5f * (height + textHeight), textPaint);

        TextPaint textPaintComments = new TextPaint();
        textPaintComments.setStyle(Paint.Style.FILL);
        textPaintComments.setTextSize(ctx.getResources().getDimension(R.dimen.header_comments_size));
        textPaintComments.setColor(ctx.getResources().getColor(R.color.text_color));

        String commentText = "(Dernière MAJ: " + timeLastUpdated + ")";
        canvas.drawText(commentText , 0.5f*width, 0.5f * (height + textHeight), textPaintComments);

        return bmp;
    }

    private Bitmap drawCommonFooter(Context ctx, int width, int height) {

        Log.i(GraphViewerWidgetProvider.TAG, "drawCommonFooter");

        Bitmap bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bmp);

        Utilities.fillCanvas(canvas, ctx.getResources().getColor(R.color.background_color));

        TextPaint textPaint = new TextPaint();
        textPaint.setStyle(Paint.Style.FILL);
        textPaint.setTextSize(12);
        textPaint.setColor(ctx.getResources().getColor(R.color.text_color));

        textPaint.setTextAlign(Align.LEFT);
        textPaint.setAntiAlias(true);
        textPaint.setSubpixelText(true);

        String latestDatePrinted = "";

        for (int i=0; i<NB_VERTICAL_MARKERS; i++) {
            float x = (1.0f+i)*width/(NB_VERTICAL_MARKERS+1);

            // On bottom half of the footer, under each marker, print corresponding date and time
            //Rect bounds = new Rect();
            float textHeight = Utilities.getTextHeight(textPaint, "0");
            float textWidth;

            long timerange = timestamp_end - timestamp_start;

            // Compute and display time for this marker
            long timestamp = timestamp_start + (long)(x * timerange / width);
            String timetext = Utilities.getTimeFromTimeStamp(timestamp);

            textWidth = Utilities.getTextWidth(textPaint, timetext);
            canvas.drawText(timetext, x - 0.5f*textWidth, (0.25f)*height + 0.5f*textHeight, textPaint);

            // Compute and display date for this marker
            // But do not print the date on this marker if it is the same day as one of the previous markers
            String datetext = Utilities.getDateFromTimeStamp(timestamp);
            if (!datetext.equals(latestDatePrinted)) {
                textWidth = Utilities.getTextWidth(textPaint, datetext);
                canvas.drawText(datetext, x - 0.5f * textWidth, (0.75f) * height + 0.5f * textHeight, textPaint);
                latestDatePrinted = datetext;
            }
        }
        return bmp;
    }

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        Log.i(GraphViewerWidgetProvider.TAG, "onUpdate called");

        timeLastUpdated = Utilities.getCurrentTime();

        getPrefs(context);

        // Update each of the widgets with the remote adapter
        for (int i = 0; i < appWidgetIds.length; ++i) {

            Log.i(GraphViewerWidgetProvider.TAG, "onUpdate: recreating RemoteViews for widgetId " + Integer.toString(i));

            Settings.GraphSettings gs = mSettings.getGraphSettings(appWidgetIds[i]);

            mHistoryLengthInHours = gs.getHistoryLength();

            if (mGraphWidth <= 0) mGraphWidth = DEFAULT_WIDTH;
            if (mGraphHeight <= 0) mGraphHeight = DEFAULT_HEIGHT;
            if (mHeaderHeight <= 0) mHeaderHeight = DEFAULT_HEADERHEIGHT;
            if (mHeaderWidth <= 0) mHeaderWidth = DEFAULT_HEADERWIDTH;
            if (mFooterHeight <= 0) mFooterHeight = DEFAULT_FOOTERHEIGHT;

            timestamp_end = Utilities.getCurrentTimeStamp();
            timestamp_start = timestamp_end - mHistoryLengthInHours*60*60*1000;

            final RemoteViews rv = new RemoteViews(context.getPackageName(), R.layout.widget_layout);
            rv.setImageViewBitmap(R.id.textGraphTitle, drawCommonHeader(context, mHeaderWidth, mHeaderHeight));
            rv.setImageViewBitmap(R.id.footer, drawCommonFooter(context, mGraphWidth, mFooterHeight));

            // Bind the click intent for the refresh button on the widget
            final Intent reloadIntent = new Intent(context, GraphViewerWidgetProvider.class);
            reloadIntent.setAction(GraphViewerWidgetProvider.RELOAD_ACTION);
            final PendingIntent refreshPendingIntent = PendingIntent.getBroadcast(context, 0, reloadIntent, PendingIntent.FLAG_UPDATE_CURRENT);
            rv.setOnClickPendingIntent(R.id.reloadList, refreshPendingIntent);

            // Bind the click intent for the Settings button on the widget
            Intent settingsIntent = new Intent(context, SettingsActivity.class);
            //Intent settingsIntent = new Intent(context, PreferenceWithHeaders.class);

            settingsIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            settingsIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetIds[i]);
            PendingIntent settingsPendingIntent = PendingIntent.getActivity(context, appWidgetIds[i], settingsIntent, PendingIntent.FLAG_CANCEL_CURRENT);
            rv.setOnClickPendingIntent(R.id.settings, settingsPendingIntent);

            // Show either the reload button of the spinning progress icon, depending of the current state 
            rv.setViewVisibility(R.id.reloadList, progressBarEnabled ? View.GONE : View.VISIBLE);
            rv.setViewVisibility(R.id.loadingProgress, progressBarEnabled ? View.VISIBLE : View.GONE);

            // Register a callback so that when clicking on the graph, a message is broadcasted back to this provider, so that its actual
            // dimensions on screen can be measured, and the rendering height and width be updated accordingly.
            final Intent graphIntent = new Intent(context, GraphViewerWidgetProvider.class);
            graphIntent.setAction(GraphViewerWidgetProvider.GRAPHMEASURE_ACTION);
            final PendingIntent graphPendingIntent = PendingIntent.getBroadcast(context, 0, graphIntent, PendingIntent.FLAG_UPDATE_CURRENT);
            rv.setOnClickPendingIntent(R.id.GraphBody, graphPendingIntent);

            // and the same for footer measurement...
            final Intent headerIntent = new Intent(context, GraphViewerWidgetProvider.class);
            headerIntent.setAction(GraphViewerWidgetProvider.HEADERMEASURE_ACTION);
            final PendingIntent headerPendingIntent = PendingIntent.getBroadcast(context, 0, headerIntent, PendingIntent.FLAG_UPDATE_CURRENT);
            rv.setOnClickPendingIntent(R.id.textGraphTitle, headerPendingIntent);

            // and the same for footer measurement...
            final Intent footerIntent = new Intent(context, GraphViewerWidgetProvider.class);
            footerIntent.setAction(GraphViewerWidgetProvider.FOOTERMEASURE_ACTION);
            final PendingIntent footerPendingIntent = PendingIntent.getBroadcast(context, 0, footerIntent, PendingIntent.FLAG_UPDATE_CURRENT);
            rv.setOnClickPendingIntent(R.id.footer, footerPendingIntent);

            // Update the widget with this newly built RemoveViews
            appWidgetManager.updateAppWidget(appWidgetIds[i], rv);
        }

        // Get all ids
        ComponentName thisWidget = new ComponentName(context, GraphViewerWidgetProvider.class);

        // Build the intent to call the service
        Intent intent = new Intent(context.getApplicationContext(), GraphViewerWidgetService.class);
        intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, appWidgetIds);

        // Update the widgets via the service
        context.startService(intent);

        Log.i(GraphViewerWidgetProvider.TAG, "onUpdate: background service started");

        super.onUpdate(context, appWidgetManager, appWidgetIds);
    }

}