package com.cannon.onyxlauncher.modloaders.modpacks.api;

import com.kdt.mcgui.ProgressLayout;

import com.cannon.onyxlauncher.R;
import com.cannon.onyxlauncher.MobileProfileOptimizer;
import com.cannon.onyxlauncher.Tools;
import com.cannon.onyxlauncher.modloaders.modpacks.imagecache.ModIconCache;
import com.cannon.onyxlauncher.modloaders.modpacks.models.ModDetail;
import com.cannon.onyxlauncher.progresskeeper.DownloaderProgressWrapper;
import com.cannon.onyxlauncher.utils.DownloadUtils;
import com.cannon.onyxlauncher.value.launcherprofiles.LauncherProfiles;
import com.cannon.onyxlauncher.value.launcherprofiles.MinecraftProfile;
import com.cannon.onyxlauncher.prefs.LauncherPreferences;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.concurrent.Callable;

public class ModpackInstaller {

    public static ModLoader installModpack(ModDetail modDetail, int selectedVersion, InstallFunction installFunction) throws IOException {
        String versionUrl = ModpackUrlUtils.normalizeUrl(modDetail.versionUrls[selectedVersion]);
        String versionHash = modDetail.versionHashes[selectedVersion];
        if(!ModpackUrlUtils.isHttpUrl(versionUrl)) {
            throw new IOException("This modpack source did not provide a client download URL for the selected version");
        }
        
        String modpackName = uniqueModpackFileName(modDetail.title, modDetail.versionNames[selectedVersion], versionHash);

        // Get the modpack file
        File modpackFile = new File(Tools.DIR_CACHE, modpackName + ".cf"); // Cache File
        ModLoader modLoaderInfo;
        try {
            byte[] downloadBuffer = new byte[8192];
            DownloadUtils.ensureSha1(modpackFile, versionHash, (Callable<Void>) () -> {
                DownloadUtils.downloadFileMonitored(versionUrl, modpackFile, downloadBuffer,
                        new DownloaderProgressWrapper(R.string.modpack_download_downloading_metadata,
                                ProgressLayout.INSTALL_MODPACK));
                return null;
            });

            // Install the modpack
            modLoaderInfo = installFunction.installModpack(modpackFile, new File(Tools.DIR_GAME_HOME, "custom_instances/" + modpackName));

        } finally {
            modpackFile.delete();
            ProgressLayout.clearProgress(ProgressLayout.INSTALL_MODPACK);
        }
        if(modLoaderInfo == null) {
            return null;
        }

        return createInstalledProfile(modDetail, selectedVersion, modLoaderInfo, modpackName);
    }

    public static ModLoader createInstalledProfile(ModDetail modDetail, int selectedVersion, ModLoader modLoaderInfo, String modpackName) throws IOException {
        // Ensure unique display name for the profile
        String baseDisplayName = modDetail.title;
        String displayName = baseDisplayName;
        int displayCount = 1;
        boolean nameExists = true;
        while (nameExists) {
            nameExists = false;
            if (LauncherProfiles.mainProfileJson.profiles != null) {
                for (MinecraftProfile existingProfile : LauncherProfiles.mainProfileJson.profiles.values()) {
                    if (existingProfile.name != null && existingProfile.name.equalsIgnoreCase(displayName)) {
                        nameExists = true;
                        break;
                    }
                }
            }
            if (nameExists) {
                displayName = baseDisplayName + " (" + displayCount + ")";
                displayCount++;
            }
        }

        // Create the instance
        MinecraftProfile profile = new MinecraftProfile();
        profile.gameDir = "./custom_instances/" + modpackName;
        profile.name = displayName;
        profile.lastVersionId = modLoaderInfo.getVersionId();
        ModIconCache.ensureIconCached(modDetail);
        profile.icon = ModIconCache.getBase64Image(modDetail.getIconCacheTag());

        // Pre-configure settings for modpacks (Holy-Zink renderer, Java 21, Auto RAM)
        profile.pojavRendererName = "vulkan_zink";
        profile.javaDir = Tools.LAUNCHERPROFILES_RTPREFIX + "Internal-21";
        profile.ramAllocation = Math.max(3072, Math.min(4096, LauncherPreferences.PREF_RAM_ALLOCATION + 1024));
        MobileProfileOptimizer.apply(profile);

        LauncherProfiles.mainProfileJson.profiles.put(modpackName, profile);
        LauncherProfiles.write();

        modLoaderInfo.profileId = modpackName;
        modLoaderInfo.displayName = displayName;

        return modLoaderInfo;
    }

    interface InstallFunction {
        ModLoader installModpack(File modpackFile, File instanceDestination) throws IOException;
    }

    public static String safeModpackFileName(String title, String versionName, String versionHash) {
        String source = (title.toLowerCase(Locale.ROOT) + " " + versionName).trim();
        String cleanName = source.replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}\\s]+", "_")
                .replaceAll("_+", "_")
                .replaceAll("^_+|_+$", "");
        if (cleanName.isEmpty()) cleanName = "modpack";
        String hash = versionHash != null && !versionHash.isEmpty()
                ? versionHash
                : Integer.toHexString(source.hashCode());
        if (hash.length() > 12) hash = hash.substring(0, 12);
        String suffix = "_" + hash;
        int maxBaseBytes = Math.max(16, 120 - suffix.getBytes(StandardCharsets.UTF_8).length);
        cleanName = shortenUtf8(cleanName, maxBaseBytes).replaceAll("_+$", "");
        if (cleanName.isEmpty()) cleanName = "modpack";
        return cleanName + suffix;
    }

    public static String uniqueModpackFileName(String title, String versionName, String versionHash) {
        String baseModpackName = safeModpackFileName(title, versionName, versionHash);
        String modpackName = baseModpackName;
        int count = 1;
        while (new File(Tools.DIR_GAME_HOME, "custom_instances/" + modpackName).exists() ||
                (LauncherProfiles.mainProfileJson.profiles != null && LauncherProfiles.mainProfileJson.profiles.containsKey(modpackName))) {
            modpackName = baseModpackName + "_" + count;
            count++;
        }
        return modpackName;
    }

    private static String shortenUtf8(String value, int maxBytes) {
        if (value.getBytes(StandardCharsets.UTF_8).length <= maxBytes) return value;
        int end = value.length();
        while (end > 0 && value.substring(0, end).getBytes(StandardCharsets.UTF_8).length > maxBytes) {
            end--;
        }
        return value.substring(0, end);
    }
}
