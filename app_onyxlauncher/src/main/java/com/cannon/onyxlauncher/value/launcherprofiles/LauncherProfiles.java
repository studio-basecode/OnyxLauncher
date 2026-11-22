package com.cannon.onyxlauncher.value.launcherprofiles;

import android.util.Log;

import androidx.annotation.NonNull;

import com.cannon.onyxlauncher.Tools;
import com.cannon.onyxlauncher.prefs.LauncherPreferences;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class LauncherProfiles {
    public static volatile MinecraftLauncherProfiles mainProfileJson;
    private static final File launcherProfilesFile = new File(Tools.GAME_PROFILES_FILE);
    private static final File launcherProfilesTempFile = new File(Tools.GAME_PROFILES_FILE + ".tmp");
    private static final File launcherProfilesBackupFile = new File(Tools.GAME_PROFILES_FILE + ".bak");
    private static final Object LOCK = new Object();
    private static long sLastLoadedMtime = -1L;
    private static long sLastLoadedLength = -1L;

    /** Reload the profile from the file, creating a default one if necessary */
    public static void load() {
        synchronized (LOCK) {
            if (launcherProfilesFile.exists()) {
                long mtime = launcherProfilesFile.lastModified();
                long len = launcherProfilesFile.length();
                if (mainProfileJson != null && mainProfileJson.profiles != null
                        && mtime == sLastLoadedMtime && len == sLastLoadedLength && len > 0) {
                    return;
                }

                MinecraftLauncherProfiles parsed = null;
                Exception lastError = null;
                for (int attempt = 0; attempt < 3; attempt++) {
                    try {
                        String rawJson = Tools.read(launcherProfilesFile.getAbsolutePath());
                        if (rawJson != null && !rawJson.trim().isEmpty()) {
                            parsed = Tools.GLOBAL_GSON.fromJson(rawJson, MinecraftLauncherProfiles.class);
                            if (parsed != null) break;
                        }
                    } catch (Exception e) {
                        lastError = e;
                        Log.w("LauncherProfiles", "Attempt " + (attempt + 1) + " to parse launcher_profiles.json failed: " + e.getMessage());
                        try {
                            Thread.sleep(100);
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            break;
                        }
                    }
                }

                if (parsed == null && launcherProfilesBackupFile.exists()) {
                    try {
                        String bakJson = Tools.read(launcherProfilesBackupFile.getAbsolutePath());
                        parsed = Tools.GLOBAL_GSON.fromJson(bakJson, MinecraftLauncherProfiles.class);
                        if (parsed != null) {
                            Log.w("LauncherProfiles", "Recovered launcher_profiles.json from backup file");
                        }
                    } catch (Exception e) {
                        Log.e("LauncherProfiles", "Failed to load backup launcher_profiles.json.bak", e);
                    }
                }

                if (parsed != null) {
                    mainProfileJson = parsed;
                    sLastLoadedMtime = launcherProfilesFile.lastModified();
                    sLastLoadedLength = launcherProfilesFile.length();
                } else if (mainProfileJson == null) {
                    Log.e("LauncherProfiles", "Could not parse launcher_profiles.json, falling back to empty profile map", lastError);
                }
            }

            // Fill with default
            if (mainProfileJson == null) mainProfileJson = new MinecraftLauncherProfiles();
            if (mainProfileJson.profiles == null) mainProfileJson.profiles = new HashMap<>();
            if (mainProfileJson.profiles.size() == 0)
                mainProfileJson.profiles.put(UUID.randomUUID().toString(), MinecraftProfile.getDefaultProfile());

            // Normalize profile names from mod installers
            if (normalizeProfileIds(mainProfileJson)) {
                write();
                load();
            }
        }
    }

    /** Apply the current configuration into a file atomically */
    public static void write() {
        synchronized (LOCK) {
            if (mainProfileJson == null) return;
            try {
                String json = mainProfileJson.toJson();
                File parent = launcherProfilesFile.getParentFile();
                if (parent != null && !parent.exists()) {
                    parent.mkdirs();
                }
                Tools.write(launcherProfilesTempFile.getAbsolutePath(), json);
                if (launcherProfilesFile.exists() && launcherProfilesFile.length() > 0) {
                    try {
                        Tools.write(launcherProfilesBackupFile.getAbsolutePath(), Tools.read(launcherProfilesFile.getAbsolutePath()));
                    } catch (Exception ignored) {
                    }
                }
                if (!launcherProfilesTempFile.renameTo(launcherProfilesFile)) {
                    Tools.write(launcherProfilesFile.getAbsolutePath(), json);
                    launcherProfilesTempFile.delete();
                }
                sLastLoadedMtime = launcherProfilesFile.lastModified();
                sLastLoadedLength = launcherProfilesFile.length();
            } catch (IOException e) {
                Log.e(LauncherProfiles.class.toString(), "Failed to write profile file", e);
                throw new RuntimeException(e);
            }
        }
    }

    public static @NonNull MinecraftProfile getCurrentProfile() {
        synchronized (LOCK) {
            if (mainProfileJson == null) LauncherProfiles.load();
            String defaultProfileName = LauncherPreferences.DEFAULT_PREF.getString(LauncherPreferences.PREF_KEY_CURRENT_PROFILE, "");
            MinecraftProfile profile = mainProfileJson.profiles.get(defaultProfileName);
            if (profile == null) throw new RuntimeException("The current profile stopped existing :(");
            return profile;
        }
    }

    /**
     * Insert a new profile into the profile map
     * @param minecraftProfile the profile to insert
     */
    public static void insertMinecraftProfile(MinecraftProfile minecraftProfile) {
        synchronized (LOCK) {
            if (mainProfileJson == null) LauncherProfiles.load();
            mainProfileJson.profiles.put(getFreeProfileKey(), minecraftProfile);
        }
    }

    /**
     * Pick an unused normalized key to store a new profile with
     * @return an unused key
     */
    public static String getFreeProfileKey() {
        synchronized (LOCK) {
            if (mainProfileJson == null) LauncherProfiles.load();
            Map<String, MinecraftProfile> profileMap = mainProfileJson.profiles;
            String freeKey = UUID.randomUUID().toString();
            while (profileMap.get(freeKey) != null) freeKey = UUID.randomUUID().toString();
            return freeKey;
        }
    }

    /**
     * For all keys to be UUIDs, effectively isolating profile created by installers
     * This avoids certain profiles to be erased by the installer
     * @return Whether some profiles have been normalized
     */
    private static boolean normalizeProfileIds(MinecraftLauncherProfiles launcherProfiles){
        // Wyłączamy automatyczne kasowanie i przenoszenie profili o nazwach tekstowych do UUID
        return false;
    }

}
