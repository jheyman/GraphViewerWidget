package com.gbbtbb.graphviewerwidget;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class GraphViewerDataProvider extends ContentProvider {

    public static final Uri CONTENT_URI_DATAPOINTS = Uri.parse("content://com.gbbtbb.graphviewerwidget.provider.datapoints");

    public static final String DATAREFRESH_DATETIME = "current_datetime";
    public static final String DATAITEMS = "items";

    public static class Columns {
        public static final String DATAID = "dataId";
        public static final String DATETIME = "timestamp";
        public static final String VALUE = "value";
    }

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public synchronized Cursor query(Uri uri, String[] projection, String selection,
                                     String[] selectionArgs, String sortOrder) {
        assert (uri.getPathSegments().isEmpty());

        final MatrixCursor c_data = new MatrixCursor(new String[]{Columns.DATAID, Columns.DATETIME, Columns.VALUE});

        Log.i(GraphViewerWidgetProvider.TAG, "Performing HTTP request with delay="+ selectionArgs[0]+ " hours");
        String result = httpRequest("http://192.168.0.13:8081/graphlist.php?delay=-"+selectionArgs[0]+" hour");

        // Parse the received JSON data
        try {
            JSONObject jdata = new JSONObject(result);

            // First get general params
            String dataRefreshDateTime = jdata.getString(DATAREFRESH_DATETIME);
            c_data.addRow(new Object[]{DATAREFRESH_DATETIME, "<none>", dataRefreshDateTime});

            // Then get actual data points
            JSONArray jArray = jdata.getJSONArray(DATAITEMS);
            for (int i = 0; i < jArray.length(); i++) {
                JSONObject jobj = jArray.getJSONObject(i);
                String dataId = jobj.getString(Columns.DATAID);
                String timestamp = jobj.getString(Columns.DATETIME);
                double value = jobj.getDouble(Columns.VALUE);
                c_data.addRow(new Object[]{dataId, timestamp, value});
            }

        } catch (JSONException e) {
            Log.e(GraphViewerWidgetProvider.TAG, "Error parsing data " + e.toString());
        }

        return c_data;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        // not implemented / nothing to insert.
        return null;
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        // not implemented / nothing to insert.
        return 0;
    }

    @Override
    public String getType(Uri uri) {
        return "vnd.android.cursor.dir/com.gbbtbb.graphlistitems";
    }

    private String httpRequest(String url/*, ArrayList<NameValuePair> nameValuePairs*/) {
        String result = "";

        try {

            URL targetUrl = new URL(url);
            HttpURLConnection urlConnection = (HttpURLConnection) targetUrl.openConnection();
            try {
                InputStream in = new BufferedInputStream(urlConnection.getInputStream());
                result = readStream(in);
            }
            finally {
                urlConnection.disconnect();
            }
        } catch(Exception e) {
            Log.e(GraphViewerWidgetProvider.TAG, "httpRequest: Error in http connection "+e.toString());
        }

        Log.i(GraphViewerWidgetProvider.TAG, "httpRequest completed, received "+ result.length() + " bytes");

        return result;
    }

    private String readStream(InputStream is) {
        try {
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            int i = is.read();
            while(i != -1) {
                bo.write(i);
                i = is.read();
            }
            return bo.toString();
        } catch (IOException e) {
            return "";
        }
    }

    @Override
    public synchronized int update(Uri uri, ContentValues values, String selection,
                                   String[] selectionArgs) {
        assert(uri.getPathSegments().size() == 1);
        assert(uri.getPathSegments().size() == 1);

        // not implemented right now.

        getContext().getContentResolver().notifyChange(uri, null);
        return 1;
    }

}