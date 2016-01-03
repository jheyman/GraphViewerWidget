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
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;

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

        String charset = "UTF-8";
        String param_db = "homelog";
        int hours = Integer.parseInt(selectionArgs[0]);
        String param_period = String.format("SELECT * FROM homelogdata WHERE time > now() - %dh", hours);
        String query = "";

        try {
            query = String.format("http://192.168.0.13:8086/query?db=%s&q=%s",
                    URLEncoder.encode(param_db, charset),
                    URLEncoder.encode(param_period, charset));
        }
        catch (UnsupportedEncodingException e) {
            Log.e(GraphViewerWidgetProvider.TAG, "Error encoding URL params: " + e.toString());
        }

        String result = httpRequest(query);

        // Parse the received JSON data
        if (!result.equals("")) {
            Log.i(GraphViewerWidgetProvider.TAG, "Parsing received JSON data");
            try {
                JSONObject jdata = new JSONObject(result);

                String dataRefreshDateTime = Utilities.getCurrentDateTime();
                c_data.addRow(new Object[]{DATAREFRESH_DATETIME, "<none>", dataRefreshDateTime});

                // Then get actual data points
                JSONObject result_field = (JSONObject)jdata.getJSONArray("results").get(0);
                JSONArray series_array = (JSONArray)result_field.getJSONArray("series");
                JSONObject serie = (JSONObject) series_array.get(0);
                JSONArray series_values = (JSONArray) serie.getJSONArray("values");

                for (int i = 0; i < series_values.length(); i++) {

                    JSONArray myArray = (JSONArray) series_values.get(i);

                    // Datetime of the points is stored in first field
                    // InfluxDB datetime are returned in format YYYY-MM-DDTHH:mm:ss.SSSSSSSSSZ, as per RFC3339
                    // Transform it into the "yyyy-MM-dd HH:mm:ss" format, for two reasons:
                    // - the rest of the code/utilities expects it like this and I'm too lazy to modify everything
                    // - I had weird issues on Android 4.0.3 and earlier in timezone conversion with the RFC3339 format
                    String datetime = myArray.getString(0).substring(0,19).replace("T", " ");

                    // Also, influxDB datatime is stored/returned in UTC: convert it to local timezone here and let the rest of the code deal only with this local time.
                    SimpleDateFormat sdf  = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

                    try {
                        sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
                        Date date = sdf.parse(datetime);

                        SimpleDateFormat pstFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
                        pstFormat.setTimeZone(TimeZone.getTimeZone("Europe/Paris"));

                        datetime = pstFormat.format(date);
                    }
                    catch (ParseException p) {
                        Log.i(GraphViewerWidgetProvider.TAG, "ERROR in parsing date string: " + datetime);
                    }

                    // Graph Id is stored in second field of each point
                    String dataId = myArray.getString(1);

                    // Value is stored in third field of each point
                    double value = myArray.getDouble(2);

                    //Log.i(GraphViewerWidgetProvider.TAG, "POINT: datetime= " + datetime + ", dataId=" + dataId + ", value=" + Double.toString(value));

                    c_data.addRow(new Object[]{dataId, datetime, value});
                }

            } catch (JSONException e) {
                Log.e(GraphViewerWidgetProvider.TAG, "Error parsing data: " + e.toString());
            }
            Log.i(GraphViewerWidgetProvider.TAG, "JSON data parsing completed");
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

        Log.i(GraphViewerWidgetProvider.TAG, "Performing HTTP request " + url);

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