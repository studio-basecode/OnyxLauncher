package com.cannon.onyxlauncher.prefs.screens;

import android.os.Bundle;

import com.cannon.onyxlauncher.R;

public class LauncherPreferenceExperimentalFragment extends LauncherPreferenceFragment {

    @Override
    public void onCreatePreferences(Bundle b, String str) {
        addPreferencesFromResource(R.xml.pref_experimental);
    }
}
