package com.anthonykeane.speedsignfinder;

import android.preference.PreferenceActivity;

import java.util.List;

/**
 * Created by Keanea on 25/06/13.
 */
public class PreferencesActivity extends PreferenceActivity {

    /**
     * Populate the activity with the top-level headers.
     */
    @Override
    public void onBuildHeaders(List<Header> target) {

        loadHeadersFromResource(R.xml.preferences_headers, target);

    }
}