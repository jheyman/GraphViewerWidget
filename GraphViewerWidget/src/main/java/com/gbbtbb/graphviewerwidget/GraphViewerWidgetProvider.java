package com.gbbtbb.graphviewerwidget;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.database.ContentObserver;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Paint.Align;
import android.graphics.Path;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.annotation.Nullable;
import android.text.TextPaint;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.View;
import android.widget.RemoteViews;

/**
 * Our data observer just notifies an update for all widgets when it detects a change.
 */
class GraphViewerDataProviderObserver extends ContentObserver {
    private AppWidgetManager mAppWidgetManager;
    private ComponentName mComponentName;

    GraphViewerDataProviderObserver(AppWidgetManager mgr, ComponentName cn, Handler h) {
        super(h);
        mAppWidgetManager = mgr;
        mComponentName = cn;
    }

    @Override
    public void onChange(boolean selfChange) {
        // The data has changed, so notify the widget that the collection view needs to be updated.
        // In response, the factory's onDataSetChanged() will be called which will requery the
        // cursor for the new data.
        Log.i(GraphViewerWidgetProvider.TAG, "GraphViewerDataProviderObserver:onChange");
        mAppWidgetManager.notifyAppWidgetViewDataChanged(
                mAppWidgetManager.getAppWidgetIds(mComponentName), R.id.graph_list);
    }
}

/**
 * The widget's AppWidgetProvider.
 */
public class GraphViewerWidgetProvider extends AppWidgetProvider {
    public static final String TAG = "GraphViewerViewer";

    public static final String CLICK_ACTION = "com.gbbtbb.graphviewerviewerwidget.CLICK";
    public static final String RELOAD_ACTION = "com.gbbtbb.graphviewerviewerwidget.RELOAD_LIST";
    public static final String RELOAD_ACTION_DONE = "com.gbbtbb.graphviewerviewerwidget.RELOAD_LIST_DONE";
    public static final String EXTRA_ITEM_ID = "com.gbbtbb.graphviewerviewerwidget.item";
    public static final String CUSTOM_REFRESH_ACTION = "com.gbbtbb.graphviewerwidget.UpdateAction";

    private static final int HeaderHeight = 50;
    private static final int FooterHeight = 45;

    public static final int NB_DUMMY_GRAPHS = 5;
    public static final int DEFAULT_WIDTH = 1000;
    public static final int DEFAULT_HEIGHT = 150;
    public static final int NB_VERTICAL_MARKERS = 15;

    private static GraphViewerDataProviderObserver sDataObserver=null;

    private static HandlerThread sWorkerThread;
    private static Handler sWorkerQueue;
    private static boolean progressBarEnabled = false;

    public DisplayMetrics mDisplayMetrics;

    // Width and height of each graph, in pixels
    public static int mGraphWidth;
    public static int mGraphHeight;

    // beginning and end timestamps specifying the actual time range to be visualized
    public static long timestamp_start;
    public static long timestamp_end;

    private Settings mSettings;

    public static void notifyRefresh(Context context, @Nullable int[] appWidgetIds) {
        Intent i = new Intent(context, GraphViewerWidgetProvider.class);
        i.setAction(GraphViewerWidgetProvider.CUSTOM_REFRESH_ACTION);
        i.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, appWidgetIds);
        context.sendBroadcast(i);
    }

    public GraphViewerWidgetProvider() {
        // Start the worker threaD
        sWorkerThread = new HandlerThread("GraphViewerWidgetProvider-worker");
        sWorkerThread.start();
        sWorkerQueue = new Handler(sWorkerThread.getLooper());
    }

    @Override
    public void onDeleted(Context context, int[] appWidgetIds) {
        super.onDeleted(context, appWidgetIds);
        Log.i(GraphViewerWidgetProvider.TAG, "onDeleted");
    }

    @Override
    public void onEnabled(Context context) {
        Log.i(GraphViewerWidgetProvider.TAG, "onEnabled");

        // Register for external updates to the data to trigger an update of the widget.  When using
        // content providers, the data is often updated via a background service, or in response to
        // user interaction in the main app.  To ensure that the widget always reflects the current
        // state of the data, we must listen for changes and update ourselves accordingly.
        final ContentResolver r = context.getContentResolver();
        if (sDataObserver == null) {
            final AppWidgetManager mgr = AppWidgetManager.getInstance(context);
            final ComponentName cn = new ComponentName(context, GraphViewerWidgetProvider.class);
            sDataObserver = new GraphViewerDataProviderObserver(mgr, cn, sWorkerQueue);
            r.registerContentObserver(GraphViewerDataProvider.CONTENT_URI_DATAPOINTS, true, sDataObserver);
            Log.i(GraphViewerWidgetProvider.TAG, "onEnabled: Registered data observer");
        }
    }

    @Override
    public void onReceive(Context ctx, Intent intent) {
        final String action = intent.getAction();

        Log.i(GraphViewerWidgetProvider.TAG, "onReceive: " + action);

        mSettings = Settings.get(ctx);

        if (action.equals(CLICK_ACTION)) {

        //TODO

        } else if (action.equals(RELOAD_ACTION) || action.equals(AppWidgetManager.ACTION_APPWIDGET_UPDATE) || action.equals(CUSTOM_REFRESH_ACTION)) {

            final Context context = ctx;
            sWorkerQueue.removeMessages(0);
            sWorkerQueue.post(new Runnable() {
                @Override
                public void run() {

                    setLoadingInProgress(context, true);

                    // Just force a reload by notifying that data has changed
                    final AppWidgetManager mgr = AppWidgetManager.getInstance(context);
                    final ComponentName cn = new ComponentName(context, GraphViewerWidgetProvider.class);
                    mgr.notifyAppWidgetViewDataChanged(mgr.getAppWidgetIds(cn), R.id.graph_list);
                    Log.i(GraphViewerWidgetProvider.TAG, "onReceive: notified appwidget to refresh");
                }
            });
        }
        else if (action.equals(RELOAD_ACTION_DONE)) {

            Log.i(GraphViewerWidgetProvider.TAG, "processing RELOAD_ACTION_DONE...");
            final Context context = ctx;
            setLoadingInProgress(context, false);
        }

        super.onReceive(ctx, intent);
    }

    private void setLoadingInProgress(Context context, boolean state) {

        AppWidgetManager widgetManager = AppWidgetManager.getInstance(context);
        ComponentName widgetComponent = new ComponentName(context, GraphViewerWidgetProvider.class);
        int[] widgetIds = widgetManager.getAppWidgetIds(widgetComponent);

        progressBarEnabled = state;
        onUpdate(context, widgetManager, widgetIds);
    }

    private Bitmap drawCommonHeader(Context ctx, int width, int height) {
        Bitmap bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bmp);

/*        Paint fillpaint = new Paint();
        fillpaint.setColor(Color.WHITE);
        fillpaint.setAlpha(50);
        fillpaint.setStyle(Paint.Style.FILL);
        canvas.drawPaint(fillpaint);*/


        Typeface myfont = Typeface.createFromAsset(ctx.getAssets(), "passing_notes.ttf");

        TextPaint textPaint = new TextPaint();
        textPaint.setTypeface(myfont);
        textPaint.setStyle(Paint.Style.FILL);
        textPaint.setTextSize(40);
        textPaint.setColor(Color.WHITE);
        textPaint.setTextAlign(Align.LEFT);
        textPaint.setAntiAlias(true);
        textPaint.setSubpixelText(true);

        //StaticLayout sl = new StaticLayout("Graph header", textPaint, canvas.getWidth(), Layout.Alignment.ALIGN_NORMAL, 1, 1, false);

        float textHeight = Utilities.getTextHeight(textPaint, "r");
        // Rect bounds = new Rect();
        //textPaint.getTextBounds("G", 0, 1, bounds);
        canvas.drawText(ctx.getResources().getString(R.string.header_text), 10.0f, 0.5f*(height + textHeight), textPaint);

       // canvas.save();
        //canvas.translate(0, 15);
        // canvas.translate(0, 0.5f*(canvas.getHeight() - bounds.height()));
        //canvas.translate(0, 0.5f*(canvas.getHeight() - textHeight));
        //sl.draw(canvas);
        //canvas.restore();

        return bmp;
    }

    private Bitmap drawCommonFooter(Context ctx, int width, int height) {

        Bitmap bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bmp);

        Utilities.fillCanvas(canvas, Color.BLACK);
        /*
        Paint fillpaint = new Paint();
        fillpaint.setColor(Color.BLACK);
        fillpaint.setStyle(Paint.Style.FILL);
        canvas.drawPaint(fillpaint);*/

        //Typeface myfont = Typeface.createFromAsset(ctx.getAssets(), "passing_notes.ttf");

        TextPaint textPaint = new TextPaint();
        //textPaint.setTypeface(myfont);
        textPaint.setStyle(Paint.Style.FILL);
        textPaint.setTextSize(12);
        textPaint.setColor(Color.WHITE);
        textPaint.setTextAlign(Align.LEFT);
        textPaint.setAntiAlias(true);
        textPaint.setSubpixelText(true);


        Paint paint = new Paint();
        paint.setAntiAlias(true);
        paint.setARGB(255, 100, 100, 100);
        paint.setStrokeWidth(1.0f);
        paint.setStyle(Paint.Style.STROKE);

        Path path = new Path();

        paint.setARGB(255, 100, 100, 100);

        String latestDatePrinted = "";

        for (int i=0; i<NB_VERTICAL_MARKERS; i++) {
            float x = (0.5f+i)*width/NB_VERTICAL_MARKERS;

            // Draw vertical marker on top half of the footer
            path.moveTo(x, 0);
            path.lineTo(x, 0.33f*height);
            canvas.drawPath(path, paint);

            // On bottom half of the footer, under each marker, print corresponding date and time
            //Rect bounds = new Rect();
            float textHeight = Utilities.getTextHeight(textPaint, "0");
            float textWidth;

            long timerange = timestamp_end - timestamp_start;

            // Compute and display time for this marker
            long timestamp = timestamp_start + (long)(x * timerange / width);
            String timetext = Utilities.getTimeFromTimeStamp(timestamp);

            textWidth = Utilities.getTextWidth(textPaint, timetext);
            canvas.drawText(timetext, x - 0.5f*textWidth, (0.33f+0.5f*0.33f)*height + 0.5f*textHeight, textPaint);

            // Compute and display date for this marker
            // But do not print the date on this marker if it is the same day as one of the previous markers
            String datetext = Utilities.getDateFromTimeStamp(timestamp);
            if (!datetext.equals(latestDatePrinted)) {
                textWidth = Utilities.getTextWidth(textPaint, datetext);
                canvas.drawText(datetext, x - 0.5f * textWidth, (0.66f + 0.5f*0.33f) * height + 0.5f * textHeight, textPaint);
                latestDatePrinted = datetext;
            }
        }
        return bmp;
    }

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        Log.i(GraphViewerWidgetProvider.TAG, "onUpdate called");

        final ContentResolver r = context.getContentResolver();
        if (sDataObserver == null) {
            final AppWidgetManager mgr = AppWidgetManager.getInstance(context);
            final ComponentName cn = new ComponentName(context, GraphViewerWidgetProvider.class);
            sDataObserver = new GraphViewerDataProviderObserver(mgr, cn, sWorkerQueue);
            r.registerContentObserver(GraphViewerDataProvider.CONTENT_URI_DATAPOINTS, true, sDataObserver);
            Log.i(GraphViewerWidgetProvider.TAG, "onUpdate: Registered data observer");
        }

        // Update each of the widgets with the remote adapter
        for (int i = 0; i < appWidgetIds.length; ++i) {

            Log.i(GraphViewerWidgetProvider.TAG, "onUpdate: recreating RemoteViews for widgetId " + Integer.toString(i));

            Settings.GraphSettings gs = mSettings.getGraphSettings(appWidgetIds[i]);
            mGraphWidth = gs.getGraphWidth();
            mGraphHeight = gs.getGraphHeight();

            if (mGraphWidth <= 0) mGraphWidth = DEFAULT_WIDTH;
            if (mGraphHeight <= 0) mGraphHeight = DEFAULT_HEIGHT;

            // Format: "yyyy-MM-dd kk:mm:ss"
            timestamp_start = Utilities.getTimeStampFromDateTime("2015-08-24 00:00:00");
            timestamp_end = Utilities.getTimeStampFromDateTime("2015-08-30 23:30:00");

            // Specify the service to provide data for the collection widget.  Note that we need to
            // embed the appWidgetId via the data otherwise it will be ignored.
            final Intent intent = new Intent(context, GraphViewerWidgetService.class);
            intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetIds[i]);
            intent.setData(Uri.parse(intent.toUri(Intent.URI_INTENT_SCHEME)));
            final RemoteViews rv = new RemoteViews(context.getPackageName(), R.layout.widget_layout);
            rv.setRemoteAdapter(R.id.graph_list, intent);

            // Set the empty view to be displayed if the collection is empty.  It must be a sibling
            // view of the collection view.
            rv.setEmptyView(R.id.graph_list, R.id.empty_view);

            //rv.setImageViewBitmap(R.id.textGraphTitle, drawTextOnList(context, "GraphXXX", R.drawable.paperpad_top));
            rv.setImageViewBitmap(R.id.textGraphTitle, drawCommonHeader(context, mGraphWidth, HeaderHeight));

            //rv.setImageViewBitmap(R.id.footer, drawTextOnList(context, "", R.drawable.paperpad_bottom));
            rv.setImageViewBitmap(R.id.footer, drawCommonFooter(context, mGraphWidth, FooterHeight));

            //float pixelDensity = context.getResources().getDisplayMetrics().density;
            //Log.i(GraphViewerWidgetProvider.TAG, "pixel density=" + Float.toString(pixelDensity));


            final Intent onClickIntent = new Intent(context, GraphViewerWidgetProvider.class);
            onClickIntent.setAction(GraphViewerWidgetProvider.CLICK_ACTION);
            onClickIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetIds[i]);
            onClickIntent.setData(Uri.parse(onClickIntent.toUri(Intent.URI_INTENT_SCHEME)));
            final PendingIntent onClickPendingIntent = PendingIntent.getBroadcast(context, 0, onClickIntent, PendingIntent.FLAG_UPDATE_CURRENT);
            rv.setPendingIntentTemplate(R.id.graph_list, onClickPendingIntent);

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

            // Update the widget with this newly built RemoveViews
            appWidgetManager.updateAppWidget(appWidgetIds[i], rv);
        }
        super.onUpdate(context, appWidgetManager, appWidgetIds);
    }


}