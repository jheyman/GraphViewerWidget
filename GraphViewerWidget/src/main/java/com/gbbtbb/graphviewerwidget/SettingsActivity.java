package com.gbbtbb.graphviewerwidget;

// Code borrowed from this guy:  https://github.com/codeka/advbatterygraph

import android.appwidget.AppWidgetManager;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceFragment;
import android.preference.PreferenceGroup;
import android.preference.PreferenceManager;

import java.lang.reflect.Method;
import java.util.List;

public class SettingsActivity extends PreferenceActivity
        implements SharedPreferences.OnSharedPreferenceChangeListener {
    private static final String TAG = "SettingsActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        PreferenceManager.getDefaultSharedPreferences(this)
                .registerOnSharedPreferenceChangeListener(this);
    }

    @Override
    public void onBuildHeaders(List<PreferenceActivity.Header> target) {
        loadHeadersFromResource(R.xml.settings_headers, target);
        for (int i = 0; i < target.size(); i++) {
            PreferenceActivity.Header header = target.get(i);
            if (header.fragmentArguments == null) {
                header.fragmentArguments = new Bundle();
            }
            header.fragmentArguments.putInt(AppWidgetManager.EXTRA_APPWIDGET_ID,
                    getIntent().getExtras().getInt(AppWidgetManager.EXTRA_APPWIDGET_ID));
        }
    }

    @Override
    protected boolean isValidFragment(String fragmentName) {
        // Android requires to override this method, the base class returns "false" by default
        // to force developers to validate fragment, for security reasons. I don't care about
        // security in this context, let's just always accept the fragment
        return true;
    }

     //When preferences change, notify the graph to update itself.
    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        GraphViewerWidgetProvider.notifyRefresh(this, new int[]{
                getIntent().getExtras().getInt(AppWidgetManager.EXTRA_APPWIDGET_ID)
        });
    }

     // Base class for our various preference fragments.
    public static abstract class BasePreferenceFragment extends PreferenceFragment
            implements SharedPreferences.OnSharedPreferenceChangeListener {
        protected int mAppWidgetId;

        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            mAppWidgetId = getArguments().getInt(AppWidgetManager.EXTRA_APPWIDGET_ID);
        }

        protected String getPrefix() {
            if (mAppWidgetId == 0) {
                return Settings.PREF_PREFIX;
            } else {
                return Settings.PREF_PREFIX + "(" + mAppWidgetId + ").";
            }
        }

        @Override
        public void addPreferencesFromResource(int resourceId) {
            super.addPreferencesFromResource(resourceId);
            updateKeys(getPreferenceScreen());
        }

        private void updateKeys(PreferenceGroup parent) {
            for (int i = 0; i < parent.getPreferenceCount(); i++) {
                Preference pref = parent.getPreference(i);
                boolean changed = false;
                if (pref.getKey() != null && pref.getKey().contains("%d")) {
                    pref.setKey(String.format(pref.getKey(), mAppWidgetId));
                    changed = true;
                }
                if (pref.getDependency() != null && pref.getDependency().contains("%d")) {
                    pref.setDependency(String.format(pref.getDependency(), mAppWidgetId));
                    changed = true;
                }
                if (changed) {
                    reloadPreference(pref);
                }
                if (pref instanceof PreferenceGroup) {
                    updateKeys((PreferenceGroup) pref);
                }
            }
        }

        /*
         * This is kind of a dumb way to cause a preference to reload itself after changing one of
         * its properties (such as 'Key').
         *
         * The "correct" solution to this problem would be to dynamically generate all my preference
         * instances with the correct key to begin with, but that's ridiculously tedious.
        */

        private void reloadPreference(Preference pref) {
            Class cls = pref.getClass();
            while(cls != Preference.class) {
                try {
                    Method m = cls.getDeclaredMethod("onSetInitialValue",
                            boolean.class, Object.class);
                    m.setAccessible(true);
                    m.invoke(pref, true, null);
                    break;
                } catch (Exception e) { }
                cls = cls.getSuperclass();
            }
        }

        @Override
        public void onStart() {
            super.onStart();
            refreshSummaries();
            getPreferenceManager().getSharedPreferences().registerOnSharedPreferenceChangeListener(this);
        }

        @Override
        public void onStop() {
            super.onStop();
            getPreferenceManager().getSharedPreferences().unregisterOnSharedPreferenceChangeListener(this);
        }

        @Override
        public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
            refreshSummaries();
        }

        protected abstract void refreshSummaries();
    }

    public static class GraphSettingsFragment extends BasePreferenceFragment {
        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            PreferenceManager.setDefaultValues(getActivity(), R.xml.graph_settings, false);
            addPreferencesFromResource(R.xml.graph_settings);
        }

        @Override
        protected void refreshSummaries() {
            ListPreference listpref = (ListPreference) findPreference(getPrefix()+"NumHours");
            listpref.setSummary(listpref.getEntry());
        }
    }
}
