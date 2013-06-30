package com.anthonykeane.speedsignfinder;

import android.os.Bundle;
import android.preference.PreferenceFragment;

/**
 * Created by Keanea on 25/06/13.
 */
public class PreferenceFragmentFeatures extends PreferenceFragment {

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.pref_features);
    }
}
