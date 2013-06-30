package com.anthonykeane.speedsignfinder;

import android.os.Bundle;
import android.preference.PreferenceActivity;

/**
 * Created by Keanea on 26/06/13.
 */
public class PreferencesActivity extends PreferenceActivity {

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getFragmentManager().beginTransaction().replace(android.R.id.content, new PrefsFragment()).commit();

//
//        findPreference(sFeedbackKey).setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
//            public final boolean onPreferenceClick(Preference paramAnonymousPreference) {
//                sendFeedback();
//                finish();
//                return true;
//            }
//        });


    }


}