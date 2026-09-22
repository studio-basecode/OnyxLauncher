package com.cannon.onyxlauncher.modloaders.modpacks.api;

import android.content.Context;
import android.util.Log;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.kdt.mcgui.ProgressLayout;

import com.cannon.onyxlauncher.R;
import com.cannon.onyxlauncher.Tools;
import com.cannon.onyxlauncher.modloaders.modpacks.models.Constants;
import com.cannon.onyxlauncher.modloaders.modpacks.models.ModDetail;
import com.cannon.onyxlauncher.modloaders.modpacks.models.ModItem;
import com.cannon.onyxlauncher.modloaders.modpacks.models.SearchFilters;
import com.cannon.onyxlauncher.modloaders.modpacks.models.SearchResult;
import com.cannon.onyxlauncher.progresskeeper.DownloaderProgressWrapper;
import com.cannon.onyxlauncher.progresskeeper.ProgressKeeper;
import com.cannon.onyxlauncher.utils.ZipUtils;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class FtbLegacyApi implements ModpackApi {
    private final ApiHandler mApiHandler;
    private final Map<String, String> mHeaders;

    public FtbLegacyApi() {
        mApiHandler = new ApiHandler("https://api.modpacks.ch");
        mHeaders = new HashMap<>();
        mHeaders.put("User-Agent", "OnyxLauncher/1.0.0 (Android)");
    }

    private ModItem fetchPackItem(int packId) {
        try {
            String rawJson = ApiHandler.getRaw(mHeaders, "https://api.modpacks.ch/public/modpack/" + packId);
            if (rawJson != null) {
                JsonObject pack = new Gson().fromJson(rawJson, JsonObject.class);
                if (pack != null && pack.has("name")) {
                    String name = pack.get("name").getAsString();
                    String description = pack.has("synopsis") && !pack.get("synopsis").isJsonNull() 
                            ? pack.get("synopsis").getAsString() : "";
                    
                    String iconUrl = "";
                    if (pack.has("art") && pack.get("art").isJsonArray() && pack.getAsJsonArray("art").size() > 0) {
                        JsonArray art = pack.getAsJsonArray("art");
                        for (JsonElement artElement : art) {
                            if (artElement.isJsonObject()) {
                                JsonObject artObj = artElement.getAsJsonObject();
                                if (artObj.has("url") && !artObj.get("url").isJsonNull()) {
                                    String u = artObj.get("url").getAsString();
                                    String t = artObj.has("type") && !artObj.get("type").isJsonNull() ? artObj.get("type").getAsString() : "";
                                    if ("square".equalsIgnoreCase(t) || "icon".equalsIgnoreCase(t) || "logo".equalsIgnoreCase(t)) {
                                        iconUrl = u;
                                        break;
                                    }
                                    if (iconUrl.isEmpty()) iconUrl = u;
                                }
                            }
                        }
                    }

                    return new ModItem(
                            Constants.SOURCE_FTB_LEGACY,
                            true,
                            String.valueOf(packId),
                            name,
                            description,
                            iconUrl
                    );
                }
            }
        } catch (Exception e) {
            Log.e("FtbLegacyApi", "Failed to fetch details for popular pack: " + packId, e);
        }
        return null;
    }

    @Override
    public SearchResult searchMod(SearchFilters searchFilters, SearchResult previousPageResult) {
        ArrayList<ModItem> items = new ArrayList<>();
        try {
            String url;
            if (searchFilters.name == null || searchFilters.name.trim().isEmpty()) {
                url = "https://api.modpacks.ch/public/modpack/popular/installs/40";
            } else {
                url = "https://api.modpacks.ch/public/modpack/search/40?term=" + java.net.URLEncoder.encode(searchFilters.name.trim(), "UTF-8");
            }

            String rawJson = ApiHandler.getRaw(mHeaders, url);
            if (rawJson != null) {
                JsonObject response = new Gson().fromJson(rawJson, JsonObject.class);
                if (response != null && response.has("packs")) {
                    JsonArray packIds = response.getAsJsonArray("packs");
                    int maxPacks = Math.min(packIds.size(), 40);
                    
                    // Fetch pack details concurrently for fast loading
                    java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(8);
                    java.util.List<java.util.concurrent.Future<ModItem>> futures = new ArrayList<>();
                    
                    for (int i = 0; i < maxPacks; i++) {
                        int packId = packIds.get(i).getAsInt();
                        futures.add(pool.submit(() -> fetchPackItem(packId)));
                    }
                    
                    for (java.util.concurrent.Future<ModItem> future : futures) {
                        try {
                            ModItem item = future.get(5, java.util.concurrent.TimeUnit.SECONDS);
                            if (item != null) {
                                items.add(item);
                            }
                        } catch (Exception ignored) {}
                    }
                    pool.shutdown();
                }
            }
        } catch (Exception e) {
            Log.e("FtbLegacyApi", "Search failed", e);
        }

        SearchResult result = new SearchResult();
        result.results = items.toArray(new ModItem[0]);
        result.totalResultCount = items.size();
        return result;
    }

    @Override
    public ModDetail getModDetails(ModItem item) {
        try {
            String rawJson = ApiHandler.getRaw(mHeaders, "https://api.modpacks.ch/public/modpack/" + item.id);
            if (rawJson != null) {
                JsonObject pack = new Gson().fromJson(rawJson, JsonObject.class);
                if (pack != null && pack.has("versions")) {
                    JsonArray versions = pack.getAsJsonArray("versions");
                    int len = versions.size();

                    String[] versionNames = new String[len];
                    String[] mcVersionNames = new String[len];
                    String[] versionLoaders = new String[len];
                    String[] versionUrls = new String[len]; // stores version ID (used by installMod)
                    String[] hashes = new String[len];
                    String[] dependencies = new String[len];

                    for (int i = 0; i < len; i++) {
                        int srcIdx = len - 1 - i; // reverse order: newest first
                        JsonObject version = versions.get(srcIdx).getAsJsonObject();
                        String verId = version.has("id") ? version.get("id").getAsString() : "";
                        String verName = version.has("name") ? version.get("name").getAsString() : verId;

                        // Try to extract MC version from the version name or targets
                        String mcVer = "1.20.1";
                        if (version.has("targets") && version.get("targets").isJsonArray()) {
                            for (JsonElement te : version.getAsJsonArray("targets")) {
                                JsonObject t = te.getAsJsonObject();
                                if (t.has("name") && "minecraft".equalsIgnoreCase(t.get("name").getAsString())
                                        && t.has("version")) {
                                    mcVer = t.get("version").getAsString();
                                    break;
                                }
                            }
                        }

                        // Loader type from targets
                        String loaderType = "forge";
                        if (version.has("targets") && version.get("targets").isJsonArray()) {
                            for (JsonElement te : version.getAsJsonArray("targets")) {
                                JsonObject t = te.getAsJsonObject();
                                if (t.has("name")) {
                                    String targetName = t.get("name").getAsString().toLowerCase();
                                    if (targetName.equals("fabric")) { loaderType = "fabric"; break; }
                                    if (targetName.equals("neoforge")) { loaderType = "neoforge"; break; }
                                    if (targetName.equals("quilt")) { loaderType = "quilt"; break; }
                                }
                            }
                        }

                        versionNames[i] = verName;
                        mcVersionNames[i] = mcVer;
                        versionLoaders[i] = loaderType;
                        // Store the version ID — installMod will use this to fetch /public/modpack/{packId}/{verId}
                        versionUrls[i] = verId;
                        hashes[i] = "";
                        dependencies[i] = "";
                    }

                    String bannerUrl = "";
                    if (pack.has("art") && pack.get("art").isJsonArray() && pack.getAsJsonArray("art").size() > 0) {
                        JsonArray art = pack.getAsJsonArray("art");
                        for (JsonElement artElement : art) {
                            if (artElement.isJsonObject()) {
                                JsonObject artObj = artElement.getAsJsonObject();
                                if (artObj.has("url") && !artObj.get("url").isJsonNull()) {
                                    String u = artObj.get("url").getAsString();
                                    String t = artObj.has("type") && !artObj.get("type").isJsonNull() ? artObj.get("type").getAsString() : "";
                                    if ("splash".equalsIgnoreCase(t) || "background".equalsIgnoreCase(t)) {
                                        bannerUrl = u;
                                        break;
                                    }
                                    if (bannerUrl.isEmpty()) bannerUrl = u;
                                }
                            }
                        }
                    }

                    ModDetail detail = new ModDetail(
                            item,
                            versionNames,
                            mcVersionNames,
                            versionLoaders,
                            versionUrls,
                            hashes,
                            dependencies
                    );

                    if (!bannerUrl.isEmpty()) {
                        detail.screenshotUrls = new String[]{bannerUrl};
                    }
                    return detail;
                }
            }
        } catch (Exception e) {
            Log.e("FtbLegacyApi", "Failed to load details for pack " + item.id, e);
        }
        return null;
    }

    @Override
    public ModLoader installMod(ModDetail modDetail, int selectedVersion) throws IOException {
        // versionUrls contains the version ID (not a URL!) — it's used to build the API URL
        String verId = modDetail.versionUrls[selectedVersion];
        String packId = modDetail.id;

        if (verId == null || verId.isEmpty()) {
            throw new IOException("Invalid FTB version ID for pack: " + packId);
        }

        // Fetch per-version file list from FTB API
        String rawJson = ApiHandler.getRaw(mHeaders,
                String.format("https://api.modpacks.ch/public/modpack/%s/%s", packId, verId));
        if (rawJson == null) {
            throw new IOException("Failed to fetch FTB version data for pack " + packId + "/" + verId);
        }

        JsonObject response = new Gson().fromJson(rawJson, JsonObject.class);
        if (response == null || !response.has("files")) {
            throw new IOException("Invalid FTB API response for pack " + packId + "/" + verId);
        }

        // Resolve loader info from targets
        String mcVersion = modDetail.mcVersionNames[selectedVersion];
        if (mcVersion == null || mcVersion.isEmpty()) mcVersion = "1.20.1";
        int loaderType = ModLoader.MOD_LOADER_FORGE;
        String loaderVersion = "recommended";

        if (response.has("targets") && response.get("targets").isJsonArray()) {
            for (JsonElement element : response.getAsJsonArray("targets")) {
                JsonObject target = element.getAsJsonObject();
                String targetName = target.has("name") ? target.get("name").getAsString().toLowerCase() : "";
                String targetVersion = target.has("version") ? target.get("version").getAsString() : "";
                if (targetName.equals("minecraft") && !targetVersion.isEmpty()) {
                    mcVersion = targetVersion;
                }
                if (targetName.equals("forge") && !targetVersion.isEmpty()) {
                    loaderType = ModLoader.MOD_LOADER_FORGE;
                    loaderVersion = targetVersion;
                }
                if (targetName.equals("fabric") && !targetVersion.isEmpty()) {
                    loaderType = ModLoader.MOD_LOADER_FABRIC;
                    loaderVersion = targetVersion;
                }
                if (targetName.equals("neoforge") && !targetVersion.isEmpty()) {
                    loaderType = ModLoader.MOD_LOADER_NEOFORGE;
                    loaderVersion = targetVersion;
                }
                if (targetName.equals("quilt") && !targetVersion.isEmpty()) {
                    loaderType = ModLoader.MOD_LOADER_QUILT;
                    loaderVersion = targetVersion;
                }
            }
        }

        // Determine instance folder name
        String modpackName = ModpackInstaller.safeModpackFileName(
                modDetail.title, modDetail.versionNames[selectedVersion], verId);
        // Ensure unique folder
        int count = 1;
        String uniqueName = modpackName;
        com.cannon.onyxlauncher.value.launcherprofiles.LauncherProfiles.load();
        while (new File(com.cannon.onyxlauncher.Tools.DIR_GAME_HOME, "custom_instances/" + uniqueName).exists()
                || (com.cannon.onyxlauncher.value.launcherprofiles.LauncherProfiles.mainProfileJson.profiles != null
                    && com.cannon.onyxlauncher.value.launcherprofiles.LauncherProfiles.mainProfileJson.profiles.containsKey(uniqueName))) {
            uniqueName = modpackName + "_" + count;
            count++;
        }
        modpackName = uniqueName;

        File instanceDir = new File(com.cannon.onyxlauncher.Tools.DIR_GAME_HOME, "custom_instances/" + modpackName);

        // Download all files into their correct relative paths within the instance folder
        JsonArray files = response.getAsJsonArray("files");
        ModDownloader downloader = new ModDownloader(instanceDir, true);
        int totalFiles = files.size();
        for (int i = 0; i < totalFiles; i++) {
            JsonObject fileObj = files.get(i).getAsJsonObject();
            boolean serverOnly = fileObj.has("serveronly") && !fileObj.get("serveronly").isJsonNull() && fileObj.get("serveronly").getAsBoolean();
            if (serverOnly) continue;

            String fileUrl = fileObj.has("url") ? fileObj.get("url").getAsString() : "";
            if (fileUrl.isEmpty() || !fileUrl.startsWith("http")) continue;

            String dir = fileObj.has("path") && !fileObj.get("path").isJsonNull() ? fileObj.get("path").getAsString().trim() : "";
            String fileName = fileObj.has("name") && !fileObj.get("name").isJsonNull() ? fileObj.get("name").getAsString().trim() : "";
            if (dir.startsWith("./")) dir = dir.substring(2);
            if (dir.startsWith("/")) dir = dir.substring(1);
            if (dir.endsWith("/")) dir = dir.substring(0, dir.length() - 1);

            String relativeFilePath;
            if (dir.isEmpty()) {
                relativeFilePath = fileName;
            } else if (fileName.isEmpty() || dir.endsWith("/" + fileName) || dir.equals(fileName)) {
                relativeFilePath = dir;
            } else {
                relativeFilePath = dir + "/" + fileName;
            }

            String sha1 = fileObj.has("sha1") && !fileObj.get("sha1").isJsonNull() ? fileObj.get("sha1").getAsString().trim() : null;

            final String finalUrl = fileUrl;
            final String finalPath = relativeFilePath;
            final String finalSha1 = sha1;
            downloader.submitDownload(() -> new ModDownloader.FileInfo(finalUrl, finalPath, finalSha1));
        }

        downloader.awaitFinish((c, m) ->
                ProgressKeeper.submitProgress(ProgressLayout.INSTALL_MODPACK,
                        (int) Math.max((float) c / m * 100, 0),
                        R.string.modpack_download_downloading_mods_fc, c, m));

        ModLoader modLoaderInfo = new ModLoader(loaderType, loaderVersion, mcVersion);

        // Ensure unique display name
        String baseDisplayName = modDetail.title;
        String displayName = baseDisplayName;
        int displayCount = 1;
        boolean nameExists = true;
        while (nameExists) {
            nameExists = false;
            if (com.cannon.onyxlauncher.value.launcherprofiles.LauncherProfiles.mainProfileJson.profiles != null) {
                for (com.cannon.onyxlauncher.value.launcherprofiles.MinecraftProfile ep :
                        com.cannon.onyxlauncher.value.launcherprofiles.LauncherProfiles.mainProfileJson.profiles.values()) {
                    if (ep.name != null && ep.name.equalsIgnoreCase(displayName)) {
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

        // Create profile
        com.cannon.onyxlauncher.value.launcherprofiles.MinecraftProfile profile =
                new com.cannon.onyxlauncher.value.launcherprofiles.MinecraftProfile();
        profile.gameDir = "./custom_instances/" + modpackName;
        profile.name = displayName;
        profile.lastVersionId = modLoaderInfo.getVersionId();
        profile.pojavRendererName = "vulkan_zink";
        profile.javaDir = com.cannon.onyxlauncher.Tools.LAUNCHERPROFILES_RTPREFIX + "Internal-21";
        profile.ramAllocation = Math.max(3072, Math.min(4096,
                com.cannon.onyxlauncher.prefs.LauncherPreferences.PREF_RAM_ALLOCATION + 1024));

        com.cannon.onyxlauncher.modloaders.modpacks.imagecache.ModIconCache.ensureIconCached(modDetail);
        profile.icon = com.cannon.onyxlauncher.modloaders.modpacks.imagecache.ModIconCache.getBase64Image(modDetail.getIconCacheTag());

        com.cannon.onyxlauncher.value.launcherprofiles.LauncherProfiles.mainProfileJson.profiles.put(modpackName, profile);
        com.cannon.onyxlauncher.value.launcherprofiles.LauncherProfiles.write();

        modLoaderInfo.profileId = modpackName;
        modLoaderInfo.displayName = displayName;

        return modLoaderInfo;
    }
}
