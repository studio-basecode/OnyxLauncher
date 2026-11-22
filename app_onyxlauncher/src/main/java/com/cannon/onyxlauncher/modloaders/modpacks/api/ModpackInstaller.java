package com.cannon.onyxlauncher.modloaders.modpacks.api;

import com.kdt.mcgui.ProgressLayout;

import com.cannon.onyxlauncher.R;
import com.cannon.onyxlauncher.Tools;
import com.cannon.onyxlauncher.modloaders.modpacks.imagecache.ModIconCache;
import com.cannon.onyxlauncher.modloaders.modpacks.models.ModDetail;
import com.cannon.onyxlauncher.progresskeeper.DownloaderProgressWrapper;
import com.cannon.onyxlauncher.utils.DownloadUtils;
import com.cannon.onyxlauncher.value.launcherprofiles.LauncherProfiles;
import com.cannon.onyxlauncher.value.launcherprofiles.MinecraftProfile;
import com.cannon.onyxlauncher.prefs.LauncherPreferences;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.concurrent.Callable;

public class ModpackInstaller {

    public static final String MODPACK_META_FILENAME = ".onyx_modpack_meta.json";

    public static class ModpackMeta {
        public int apiSource;
        public String projectId;
        public String title;
        public String imageUrl;
        public String installedVersionName;
        public String installedVersionUrl;
        public String installedVersionHash;
        public String installedMcVersion;
    }

    public static void writeModpackMeta(File instanceDir, ModDetail modDetail, int selectedVersion) {
        if (instanceDir == null || modDetail == null) return;
        if (selectedVersion < 0 || modDetail.versionNames == null || selectedVersion >= modDetail.versionNames.length) return;
        try {
            if (!instanceDir.exists()) instanceDir.mkdirs();
            ModpackMeta meta = new ModpackMeta();
            meta.apiSource = modDetail.apiSource;
            meta.projectId = modDetail.id;
            meta.title = modDetail.title;
            meta.imageUrl = modDetail.imageUrl;
            meta.installedVersionName = modDetail.versionNames[selectedVersion];
            meta.installedVersionUrl = (modDetail.versionUrls != null && selectedVersion < modDetail.versionUrls.length)
                    ? modDetail.versionUrls[selectedVersion] : null;
            meta.installedVersionHash = (modDetail.versionHashes != null && selectedVersion < modDetail.versionHashes.length)
                    ? modDetail.versionHashes[selectedVersion] : null;
            meta.installedMcVersion = (modDetail.mcVersionNames != null && selectedVersion < modDetail.mcVersionNames.length)
                    ? modDetail.mcVersionNames[selectedVersion] : null;
            Tools.write(new File(instanceDir, MODPACK_META_FILENAME).getAbsolutePath(), Tools.GLOBAL_GSON.toJson(meta));
        } catch (Exception ignored) {
        }
    }

    public static ModpackMeta readModpackMeta(File instanceDir) {
        if (instanceDir == null) return null;
        File metaFile = new File(instanceDir, MODPACK_META_FILENAME);
        if (!metaFile.isFile()) return null;
        try {
            return Tools.GLOBAL_GSON.fromJson(Tools.read(metaFile.getAbsolutePath()), ModpackMeta.class);
        } catch (Exception ignored) {
            return null;
        }
    }

    public static ModLoader updateModpack(ModDetail modDetail, int selectedVersion, File instanceDestination, InstallFunction installFunction) throws IOException {
        if (instanceDestination == null) {
            throw new IOException("Invalid target instance directory");
        }
        String versionUrl = modDetail.versionUrls[selectedVersion];
        String versionHash = modDetail.versionHashes[selectedVersion];
        String tempName = safeModpackFileName(modDetail.title, modDetail.versionNames[selectedVersion], versionHash);

        File modpackFile = new File(Tools.DIR_CACHE, tempName + "_update.cf");
        ModLoader modLoaderInfo;
        try {
            byte[] downloadBuffer = new byte[8192];
            DownloadUtils.ensureSha1(modpackFile, versionHash, (Callable<Void>) () -> {
                DownloadUtils.downloadFileMonitored(versionUrl, modpackFile, downloadBuffer,
                        new DownloaderProgressWrapper(R.string.modpack_download_downloading_metadata,
                                ProgressLayout.INSTALL_MODPACK));
                return null;
            });

            // Clean old mods and mixin caches while preserving worlds, servers, options, etc.
            File modsDir = new File(instanceDestination, "mods");
            if (modsDir.exists()) {
                org.apache.commons.io.FileUtils.deleteDirectory(modsDir);
            }
            File modsStorageDir = new File(instanceDestination, "mods_storage");
            if (modsStorageDir.exists()) {
                org.apache.commons.io.FileUtils.deleteDirectory(modsStorageDir);
            }

            modLoaderInfo = installFunction.installModpack(modpackFile, instanceDestination);
        } finally {
            modpackFile.delete();
            ProgressLayout.clearProgress(ProgressLayout.INSTALL_MODPACK);
        }
        if (modLoaderInfo == null) {
            return null;
        }
        writeModpackMeta(instanceDestination, modDetail, selectedVersion);
        return modLoaderInfo;
    }

    public static ModLoader installModpack(ModDetail modDetail, int selectedVersion, InstallFunction installFunction) throws IOException {
        String versionUrl = modDetail.versionUrls[selectedVersion];
        String versionHash = modDetail.versionHashes[selectedVersion];
        
        // Ensure unique modpackName/folder/profileId
        String baseModpackName = safeModpackFileName(modDetail.title, modDetail.versionNames[selectedVersion], versionHash);
        String modpackName = baseModpackName;
        int count = 1;
        while (new File(Tools.DIR_GAME_HOME, "custom_instances/" + modpackName).exists() ||
               (LauncherProfiles.mainProfileJson.profiles != null && LauncherProfiles.mainProfileJson.profiles.containsKey(modpackName))) {
            modpackName = baseModpackName + "_" + count;
            count++;
        }

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
            File instanceDir = new File(Tools.DIR_GAME_HOME, "custom_instances/" + modpackName);
            modLoaderInfo = installFunction.installModpack(modpackFile, instanceDir);
            if (modLoaderInfo != null) {
                writeModpackMeta(instanceDir, modDetail, selectedVersion);
            }

        } finally {
            modpackFile.delete();
            ProgressLayout.clearProgress(ProgressLayout.INSTALL_MODPACK);
        }
        if(modLoaderInfo == null) {
            return null;
        }

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

        // Pre-configure mobile-optimized settings for modpacks (GLES + 50% resolution + auto RAM + matching JRE)
        profile.pojavRendererName = "opengles2";
        profile.javaDir = Tools.LAUNCHERPROFILES_RTPREFIX + recommendedRuntimeForMcVersion(modLoaderInfo.minecraftVersion);
        profile.resolutionScale = 50;
        profile.forceVsync = false;
        int totalRamMb = readTotalDeviceMemoryMb();
        if (totalRamMb > 0 && totalRamMb < 5500) {
            profile.ramAllocation = Math.max(2048, Math.min(totalRamMb - 1024, Math.max(LauncherPreferences.PREF_RAM_ALLOCATION, 3072)));
        } else {
            int targetPackRam = 4096;
            profile.ramAllocation = Math.min(4096, Math.max(3584, Math.max(LauncherPreferences.PREF_RAM_ALLOCATION, targetPackRam)));
        }

        LauncherProfiles.mainProfileJson.profiles.put(modpackName, profile);
        LauncherProfiles.write();

        modLoaderInfo.profileId = modpackName;
        modLoaderInfo.displayName = displayName;

        return modLoaderInfo;
    }

    private static String recommendedRuntimeForMcVersion(String mcVersion) {
        if (mcVersion != null) {
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("1\\.(\\d+)(?:\\.(\\d+))?").matcher(mcVersion);
            if (m.find()) {
                int minor = Integer.parseInt(m.group(1));
                int patch = m.group(2) != null ? Integer.parseInt(m.group(2)) : 0;
                if (minor > 20 || (minor == 20 && patch >= 5)) return "Internal-21";
                if (minor >= 17) return "Internal-17";
                return "Internal-8";
            }
        }
        return "Internal-21";
    }

    private static int readTotalDeviceMemoryMb() {
        try (BufferedReader reader = new BufferedReader(new FileReader("/proc/meminfo"))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("MemTotal:")) {
                    String digits = line.replaceAll("[^0-9]", "");
                    if (!digits.isEmpty()) {
                        return (int) (Long.parseLong(digits) / 1024L);
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return -1;
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

    private static String shortenUtf8(String value, int maxBytes) {
        if (value.getBytes(StandardCharsets.UTF_8).length <= maxBytes) return value;
        int end = value.length();
        while (end > 0 && value.substring(0, end).getBytes(StandardCharsets.UTF_8).length > maxBytes) {
            end--;
        }
        return value.substring(0, end);
    }
}
