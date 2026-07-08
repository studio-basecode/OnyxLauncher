package com.cannon.onyxlauncher.prefs.screens;


import android.app.Activity;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;

import com.cannon.onyxlauncher.LauncherActivity;
import com.cannon.onyxlauncher.R;
import com.cannon.onyxlauncher.prefs.LauncherPreferences;

import java.util.*;

/**
 * Preference for the main screen, any sub-screen should inherit this class for consistent behavior,
 * overriding only onCreatePreferences
 */
public class LauncherPreferenceFragment extends PreferenceFragmentCompat implements SharedPreferences.OnSharedPreferenceChangeListener {

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        view.setBackgroundColor(getResources().getColor(R.color.background_app));
        super.onViewCreated(view, savedInstanceState);
    }

    @Override
    public void onCreatePreferences(Bundle b, String str) {
        addPreferencesFromResource(R.xml.pref_main);
        setupNotificationRequestPreference();
        setupLanguagePreference();
    }

    private void setupNotificationRequestPreference() {
        Preference mRequestNotificationPermissionPreference = requirePreference("notification_permission_request");
        Activity activity = getActivity();
        if(activity instanceof LauncherActivity) {
            LauncherActivity launcherActivity = (LauncherActivity)activity;
            mRequestNotificationPermissionPreference.setVisible(!launcherActivity.checkForNotificationPermission());
            mRequestNotificationPermissionPreference.setOnPreferenceClickListener(preference -> {
                launcherActivity.askForNotificationPermission(()->mRequestNotificationPermissionPreference.setVisible(false));
                return true;
            });
        }else{
            mRequestNotificationPermissionPreference.setVisible(false);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        SharedPreferences sharedPreferences = getPreferenceManager().getSharedPreferences();
        if(sharedPreferences != null) sharedPreferences.registerOnSharedPreferenceChangeListener(this);
    }

    @Override
    public void onPause() {
        SharedPreferences sharedPreferences = getPreferenceManager().getSharedPreferences();
        if(sharedPreferences != null) sharedPreferences.unregisterOnSharedPreferenceChangeListener(this);
        super.onPause();
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences p, String s) {
        LauncherPreferences.loadPreferences(getContext());
        if ("app_language".equals(s)) {
            Activity activity = getActivity();
            if (activity != null) {
                activity.recreate();
            }
        }
    }

    protected Preference requirePreference(CharSequence key) {
        Preference preference = findPreference(key);
        if(preference != null) return preference;
        throw new IllegalStateException("Preference "+key+" is null");
    }
    @SuppressWarnings("unchecked")
    protected <T extends Preference> T requirePreference(CharSequence key, Class<T> preferenceClass) {
        Preference preference = requirePreference(key);
        if(preferenceClass.isInstance(preference)) return (T)preference;
        throw new IllegalStateException("Preference "+key+" is not an instance of "+preferenceClass.getSimpleName());
    }

    private void setupLanguagePreference() {
        ListPreference languagePref = requirePreference("app_language", ListPreference.class);

        // Retrieve available locales from assets
        String[] locales = getContext().getAssets().getLocales();

        List<String> entryValuesList = new ArrayList<>();
        List<String> entriesList = new ArrayList<>();

        // Add "default" system language
        entryValuesList.add("default");
        entriesList.add(getString(R.string.language_default));

        Set<String> addedLanguages = new HashSet<>();
        List<Locale> availableLocales = new ArrayList<>();

        for (String localeStr : locales) {
            if (localeStr == null || localeStr.isEmpty() || localeStr.equalsIgnoreCase("default")) {
                continue;
            }
            Locale locale;
            if (localeStr.contains("-")) {
                String[] parts = localeStr.split("-");
                if (parts.length > 1 && parts[1].startsWith("r")) {
                    locale = new Locale(parts[0], parts[1].substring(1));
                } else {
                    locale = Locale.forLanguageTag(localeStr);
                }
            } else {
                locale = new Locale(localeStr);
            }
            String langCode = locale.getLanguage();
            if (langCode.isEmpty() || addedLanguages.contains(langCode)) {
                continue;
            }
            addedLanguages.add(langCode);
            availableLocales.add(locale);
        }

        // Sort available locales by display name in their native language
        Collections.sort(availableLocales, (o1, o2) -> {
            String name1 = o1.getDisplayName(o1);
            String name2 = o2.getDisplayName(o2);
            return name1.compareToIgnoreCase(name2);
        });

        for (Locale locale : availableLocales) {
            String valueCode = locale.getLanguage();
            if (locale.getCountry() != null && !locale.getCountry().isEmpty()) {
                valueCode = locale.getLanguage() + "-r" + locale.getCountry();
            }
            entryValuesList.add(valueCode);

            String displayName = locale.getDisplayName(locale);
            if (displayName != null && !displayName.isEmpty()) {
                displayName = displayName.substring(0, 1).toUpperCase(locale) + displayName.substring(1);
            } else {
                displayName = locale.getLanguage();
            }
            entriesList.add(displayName);
        }

        languagePref.setEntryValues(entryValuesList.toArray(new CharSequence[0]));
        languagePref.setEntries(entriesList.toArray(new CharSequence[0]));

        if (languagePref.getValue() == null) {
            languagePref.setValue("default");
        }
    }
}
