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
import com.cannon.onyxlauncher.utils.FileUtils;
import com.cannon.onyxlauncher.utils.ZipUtils;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class FtbLegacyApi implements ModpackApi {
    private final ApiHandler mApiHandler;
    private final ApiHandler mCurseforgeApiHandler;
    private final Map<String, String> mHeaders;

    public FtbLegacyApi() {
        this("DUMMY");
    }

    public FtbLegacyApi(String curseforgeApiKey) {
        mApiHandler = new ApiHandler("https://api.modpacks.ch");
        mCurseforgeApiHandler = CurseforgeApi.createApiHandler(curseforgeApiKey);
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
                    
                    String iconUrl = chooseArtUrl(pack, "icon", "square", "logo", "cover", "background");

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
                url = "https://api.modpacks.ch/public/modpack/search/25?term=" + java.net.URLEncoder.encode(searchFilters.name.trim(), "UTF-8");
            }

            String rawJson = ApiHandler.getRaw(mHeaders, url);
            if (rawJson != null) {
                JsonObject response = new Gson().fromJson(rawJson, JsonObject.class);
                if (response != null && response.has("packs")) {
                    JsonArray packIds = response.getAsJsonArray("packs");
                    int count = 0;
                    for (JsonElement element : packIds) {
                        if (count >= 10) break; // limit to 10 details to avoid heavy API calling
                        int packId = extractPackId(element);
                        if (packId <= 0) continue;
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
                        mcVersionNames[i] = findTargetVersion(version, "minecraft", "1.20.1");
                        versionLoaders[i] = findVersionLoader(version);
                        versionUrls[i] = verId; // Save the version ID as URL
                        hashes[i] = "";
                        dependencies[i] = "";
                    }

                    String bannerUrl = chooseArtUrl(pack, "background", "cover", "logo", "square", "icon");

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
                            if (target.has("name") && "quilt".equalsIgnoreCase(target.get("name").getAsString())) {
                                loaderType = ModLoader.MOD_LOADER_QUILT;
                                loaderVersion = target.get("version").getAsString();
                            }
                            if (target.has("name") && "neoforge".equalsIgnoreCase(target.get("name").getAsString())) {
                                loaderType = ModLoader.MOD_LOADER_NEOFORGE;
                                loaderVersion = target.get("version").getAsString();
                            }
                        }
                    }

                    String modpackName = ModpackInstaller.uniqueModpackFileName(modDetail.title, modDetail.versionNames[selectedVersion], "");
                    File instanceDestination = new File(Tools.DIR_GAME_HOME, "custom_instances/" + modpackName);
                    FileUtils.ensureDirectory(instanceDestination);
                    ModDownloader downloader = new ModDownloader(instanceDestination, true);
                    int totalFiles = files.size();
                    for (int i = 0; i < totalFiles; i++) {
                        JsonObject fileObj = files.get(i).getAsJsonObject();
                        if (getBoolean(fileObj, "serveronly") || getBoolean(fileObj, "optional")) continue;
                        String filePath = buildRelativePath(fileObj);
                        String fileUrl = getString(fileObj, "url");
                        if (fileUrl.isEmpty()) {
                            JsonObject curseforge = fileObj.has("curseforge") && fileObj.get("curseforge").isJsonObject()
                                    ? fileObj.getAsJsonObject("curseforge") : null;
                            if (curseforge != null && curseforge.has("project") && curseforge.has("file")) {
                                fileUrl = getCurseforgeDownloadUrl(curseforge.get("project").getAsLong(), curseforge.get("file").getAsLong());
                            }
                        }
                        fileUrl = ModpackUrlUtils.normalizeUrl(fileUrl);
                        if (!ModpackUrlUtils.isHttpUrl(fileUrl)) {
                            throw new IOException("FTB Legacy file has no valid download URL: " + filePath);
                        }
                        String sha1 = getString(fileObj, "sha1");
                        final String downloadUrl = fileUrl;
                        final String relativePath = filePath;
                        final String downloadSha1 = sha1;

                        downloader.submitDownload(() -> new ModDownloader.FileInfo(
                                downloadUrl,
                                relativePath,
                                downloadSha1
                        ));
                    }

                    downloader.awaitFinish((c, m) ->
                            ProgressKeeper.submitProgress(ProgressLayout.INSTALL_MODPACK, (int) Math.max((float) c / m * 100, 0), R.string.modpack_download_downloading_mods_fc, c, m)
                    );

                    ModLoader modLoaderInfo = new ModLoader(loaderType, loaderVersion, mcVersion);

                    return ModpackInstaller.createInstalledProfile(modDetail, selectedVersion, modLoaderInfo, modpackName);
                }
            }
        } catch (Exception e) {
            Log.e("FtbLegacyApi", "Failed to download and install files", e);
            throw new IOException("Failed to download modpack files via api.modpacks.ch", e);
        }

        return null;
    }

    private int extractPackId(JsonElement element) {
        try {
            if (element.isJsonPrimitive()) return element.getAsInt();
            if (element.isJsonObject()) {
                JsonObject object = element.getAsJsonObject();
                if (object.has("id")) return object.get("id").getAsInt();
                if (object.has("modpack")) return object.get("modpack").getAsInt();
            }
        } catch (Exception ignored) {
        }
        return -1;
    }

    private String chooseArtUrl(JsonObject pack, String... preferredTypes) {
        if (pack == null || !pack.has("art") || !pack.get("art").isJsonArray()) return "";
        JsonArray art = pack.getAsJsonArray("art");
        for (String preferredType : preferredTypes) {
            for (JsonElement artElement : art) {
                if (!artElement.isJsonObject()) continue;
                JsonObject artObj = artElement.getAsJsonObject();
                if (preferredType.equalsIgnoreCase(getString(artObj, "type"))) {
                    String url = ModpackUrlUtils.normalizeUrl(getString(artObj, "url"));
                    if (ModpackUrlUtils.isHttpUrl(url)) return url;
                }
            }
        }
        for (JsonElement artElement : art) {
            if (!artElement.isJsonObject()) continue;
            String url = ModpackUrlUtils.normalizeUrl(getString(artElement.getAsJsonObject(), "url"));
            if (ModpackUrlUtils.isHttpUrl(url)) return url;
        }
        return "";
    }

    private static String findTargetVersion(JsonObject version, String targetName, String fallback) {
        if (version == null || !version.has("targets") || !version.get("targets").isJsonArray()) return fallback;
        for (JsonElement targetElement : version.getAsJsonArray("targets")) {
            if (!targetElement.isJsonObject()) continue;
            JsonObject target = targetElement.getAsJsonObject();
            if (targetName.equalsIgnoreCase(getString(target, "name"))) {
                return ModpackUrlUtils.firstNonBlank(getString(target, "version"), fallback);
            }
        }
        return fallback;
    }

    private static String findVersionLoader(JsonObject version) {
        if (version == null || !version.has("targets") || !version.get("targets").isJsonArray()) return "forge";
        for (JsonElement targetElement : version.getAsJsonArray("targets")) {
            if (!targetElement.isJsonObject()) continue;
            JsonObject target = targetElement.getAsJsonObject();
            String name = getString(target, "name");
            String loaderVersion = getString(target, "version");
            if ("neoforge".equalsIgnoreCase(name)) return "neoforge-" + loaderVersion;
            if ("forge".equalsIgnoreCase(name)) return "forge-" + loaderVersion;
            if ("fabric".equalsIgnoreCase(name)) return "fabric-" + loaderVersion;
            if ("quilt".equalsIgnoreCase(name)) return "quilt-" + loaderVersion;
        }
        return "forge";
    }

    private static String buildRelativePath(JsonObject fileObj) {
        String name = getString(fileObj, "name");
        String path = getString(fileObj, "path");
        if (path.isEmpty()) return name;
        path = path.replace('\\', '/');
        while (path.startsWith("./")) path = path.substring(2);
        while (path.startsWith("/")) path = path.substring(1);
        if (!path.endsWith("/")) path += "/";
        return path + name;
    }

    private String getCurseforgeDownloadUrl(long projectID, long fileID) {
        JsonObject response = mCurseforgeApiHandler.get("mods/" + projectID + "/files/" + fileID + "/download-url", JsonObject.class);
        if (response != null && response.has("data") && !response.get("data").isJsonNull()) {
            return response.get("data").getAsString();
        }

        JsonObject fallbackResponse = mCurseforgeApiHandler.get(String.format("mods/%s/files/%s", projectID, fileID), JsonObject.class);
        if (fallbackResponse != null && fallbackResponse.has("data") && !fallbackResponse.get("data").isJsonNull()){
            JsonObject modData = fallbackResponse.get("data").getAsJsonObject();
            int id = modData.get("id").getAsInt();
            return String.format("https://edge.forgecdn.net/files/%s/%s/%s", id / 1000, id % 1000, modData.get("fileName").getAsString());
        }

        return "";
    }

    private static String getString(JsonObject object, String memberName) {
        if (object == null || !object.has(memberName) || object.get(memberName).isJsonNull()) return "";
        try {
            return object.get(memberName).getAsString();
        } catch (Exception e) {
            return "";
        }
    }

    private static boolean getBoolean(JsonObject object, String memberName) {
        if (object == null || !object.has(memberName) || object.get(memberName).isJsonNull()) return false;
        try {
            return object.get(memberName).getAsBoolean();
        } catch (Exception e) {
            return false;
        }
    }
}
