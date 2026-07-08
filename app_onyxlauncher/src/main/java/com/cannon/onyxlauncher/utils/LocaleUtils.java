package com.cannon.onyxlauncher.utils;


import static com.cannon.onyxlauncher.prefs.LauncherPreferences.DEFAULT_PREF;
import static com.cannon.onyxlauncher.prefs.LauncherPreferences.PREF_APP_LANGUAGE;

import android.content.*;
import android.content.res.*;
import android.os.Build;
import android.os.LocaleList;

import androidx.preference.*;
import java.util.*;

public class LocaleUtils extends ContextWrapper {

    public LocaleUtils(Context base) {
        super(base);
    }

    public static ContextWrapper setLocale(Context context) {
        SharedPreferences onyxPrefs = context.getSharedPreferences("OnyxData", Context.MODE_PRIVATE);
        PREF_APP_LANGUAGE = onyxPrefs.getString("app_language", "default");

        if (DEFAULT_PREF == null) {
            DEFAULT_PREF = PreferenceManager.getDefaultSharedPreferences(context);
        }

        if (!"default".equals(PREF_APP_LANGUAGE)) {
            Locale locale;
            if (PREF_APP_LANGUAGE.contains("-")) {
                String[] parts = PREF_APP_LANGUAGE.split("-");
                if (parts.length > 1 && parts[1].startsWith("r")) {
                    locale = new Locale(parts[0], parts[1].substring(1));
                } else {
                    locale = Locale.forLanguageTag(PREF_APP_LANGUAGE);
                }
            } else {
                locale = new Locale(PREF_APP_LANGUAGE);
            }
            Locale.setDefault(locale);
            Resources resources = context.getResources();
            Configuration configuration = resources.getConfiguration();

            configuration.setLocale(locale);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                LocaleList localeList = new LocaleList(locale);
                LocaleList.setDefault(localeList);
                configuration.setLocales(localeList);
            }

            resources.updateConfiguration(configuration, resources.getDisplayMetrics());
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1) {
                context = context.createConfigurationContext(configuration);
            }
        }

        return new LocaleUtils(context);
    }
}
