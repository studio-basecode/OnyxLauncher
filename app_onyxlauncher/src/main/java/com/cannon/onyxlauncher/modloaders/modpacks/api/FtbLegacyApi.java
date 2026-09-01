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
                                if (artObj.has("type") && "icon".equalsIgnoreCase(artObj.get("type").getAsString()) && artObj.has("url")) {
                                    iconUrl = artObj.get("url").getAsString();
                                    break;
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
                url = "https://api.modpacks.ch/public/modpack/popular/installs/10";
            } else {
                url = "https://api.modpacks.ch/public/modpack/search/5?term=" + java.net.URLEncoder.encode(searchFilters.name.trim(), "UTF-8");
            }

            String rawJson = ApiHandler.getRaw(mHeaders, url);
            if (rawJson != null) {
                JsonObject response = new Gson().fromJson(rawJson, JsonObject.class);
                if (response != null && response.has("packs")) {
                    JsonArray packIds = response.getAsJsonArray("packs");
                    int count = 0;
                    for (JsonElement element : packIds) {
                        if (count >= 10) break; // limit to 10 details to avoid heavy API calling
                        int packId = element.getAsInt();
                        ModItem item = fetchPackItem(packId);
                        if (item != null) {
                            items.add(item);
                            count++;
                        }
                    }
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
                    String[] versionUrls = new String[len]; // We store version ID here!
                    String[] hashes = new String[len];
                    String[] dependencies = new String[len];

                    for (int i = 0; i < len; i++) {
                        JsonObject version = versions.get(i).getAsJsonObject();
                        String verId = version.get("id").getAsString();
                        versionNames[i] = version.get("name").getAsString();
                        mcVersionNames[i] = "1.20.1"; // Placeholder, will load dynamically on install
                        versionLoaders[i] = "forge";
                        versionUrls[i] = verId; // Save the version ID as URL
                        hashes[i] = "";
                        dependencies[i] = "";
                    }

                    String bannerUrl = "";
                    if (pack.has("art") && pack.get("art").isJsonArray() && pack.getAsJsonArray("art").size() > 0) {
                        JsonArray art = pack.getAsJsonArray("art");
                        for (JsonElement artElement : art) {
                            if (artElement.isJsonObject()) {
                                JsonObject artObj = artElement.getAsJsonObject();
                                if (artObj.has("type") && "background".equalsIgnoreCase(artObj.get("type").getAsString()) && artObj.has("url")) {
                                    bannerUrl = artObj.get("url").getAsString();
                                    break;
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
        String verId = modDetail.versionUrls[selectedVersion];
        String packId = modDetail.id;

        // Fetch selected version files
        try {
            String rawJson = ApiHandler.getRaw(mHeaders, String.format("https://api.modpacks.ch/public/modpack/%s/%s", packId, verId));
            if (rawJson != null) {
                JsonObject response = new Gson().fromJson(rawJson, JsonObject.class);
                if (response != null && response.has("files")) {
                    JsonArray files = response.getAsJsonArray("files");
                    
                    // Resolve loader info
                    String mcVersion = "1.20.1";
                    int loaderType = ModLoader.MOD_LOADER_FORGE;
                    String loaderVersion = "recommended";

                    if (response.has("targets")) {
                        JsonArray targets = response.getAsJsonArray("targets");
                        for (JsonElement element : targets) {
                            JsonObject target = element.getAsJsonObject();
                            if (target.has("name") && "minecraft".equalsIgnoreCase(target.get("name").getAsString())) {
                                mcVersion = target.get("version").getAsString();
                            }
                            if (target.has("name") && "forge".equalsIgnoreCase(target.get("name").getAsString())) {
                                loaderType = ModLoader.MOD_LOADER_FORGE;
                                loaderVersion = target.get("version").getAsString();
                            }
                            if (target.has("name") && "fabric".equalsIgnoreCase(target.get("name").getAsString())) {
                                loaderType = ModLoader.MOD_LOADER_FABRIC;
                                loaderVersion = target.get("version").getAsString();
                            }
                        }
                    }

                    String modpackName = ModpackInstaller.safeModpackFileName(modDetail.title, modDetail.versionNames[selectedVersion], "");
                    ModDownloader downloader = new ModDownloader(new File(Tools.DIR_GAME_HOME, "custom_instances/" + modpackName + "/mods"), true);
                    int totalFiles = files.size();
                    for (int i = 0; i < totalFiles; i++) {
                        JsonObject fileObj = files.get(i).getAsJsonObject();
                        String filePath = fileObj.get("name").getAsString();
                        String fileUrl = fileObj.get("url").getAsString();
                        long fileSize = fileObj.has("size") ? fileObj.get("size").getAsLong() : 0;

                        downloader.submitDownload(() -> new ModDownloader.FileInfo(
                                fileUrl,
                                filePath,
                                ""
                        ));
                    }

                    downloader.awaitFinish((c, m) ->
                            ProgressKeeper.submitProgress(ProgressLayout.INSTALL_MODPACK, (int) Math.max((float) c / m * 100, 0), R.string.modpack_download_downloading_mods_fc, c, m)
                    );

                    ModLoader modLoaderInfo = new ModLoader(loaderType, loaderVersion, mcVersion);

                    // Create the instance
                    com.cannon.onyxlauncher.value.launcherprofiles.MinecraftProfile profile = new com.cannon.onyxlauncher.value.launcherprofiles.MinecraftProfile();
                    profile.gameDir = "./custom_instances/" + modpackName;
                    profile.name = modDetail.title;
                    profile.lastVersionId = modLoaderInfo.getVersionId();
                    com.cannon.onyxlauncher.modloaders.modpacks.imagecache.ModIconCache.ensureIconCached(modDetail);
                    profile.icon = com.cannon.onyxlauncher.modloaders.modpacks.imagecache.ModIconCache.getBase64Image(modDetail.getIconCacheTag());

                    com.cannon.onyxlauncher.value.launcherprofiles.LauncherProfiles.mainProfileJson.profiles.put(modpackName, profile);
                    com.cannon.onyxlauncher.value.launcherprofiles.LauncherProfiles.write();

                    return modLoaderInfo;
                }
            }
        } catch (Exception e) {
            Log.e("FtbLegacyApi", "Failed to download and install files", e);
            throw new IOException("Failed to download modpack files via api.modpacks.ch", e);
        }

        return null;
    }
}
