package com.cannon.onyxlauncher.modloaders.modpacks.api;

import android.util.Log;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.kdt.mcgui.ProgressLayout;

import com.cannon.onyxlauncher.R;
import com.cannon.onyxlauncher.Tools;
import com.cannon.onyxlauncher.modloaders.modpacks.imagecache.ModIconCache;
import com.cannon.onyxlauncher.modloaders.modpacks.models.Constants;
import com.cannon.onyxlauncher.modloaders.modpacks.models.ModDetail;
import com.cannon.onyxlauncher.modloaders.modpacks.models.ModItem;
import com.cannon.onyxlauncher.modloaders.modpacks.models.SearchFilters;
import com.cannon.onyxlauncher.modloaders.modpacks.models.SearchResult;
import com.cannon.onyxlauncher.prefs.LauncherPreferences;
import com.cannon.onyxlauncher.progresskeeper.ProgressKeeper;
import com.cannon.onyxlauncher.utils.DownloadUtils;
import com.cannon.onyxlauncher.utils.ZipUtils;
import com.cannon.onyxlauncher.value.launcherprofiles.LauncherProfiles;
import com.cannon.onyxlauncher.value.launcherprofiles.MinecraftProfile;

import java.io.File;
import java.io.IOException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.zip.ZipFile;

public class ATLauncherApi implements ModpackApi {
    private static final String TAG = "ATLauncherApi";
    private static final String PACKS_URL = "https://api.atlauncher.com/v1/packs/full/public";
    private static final String CDN_BASE = "https://download.nodecdn.net/containers/atl";
    private static final String CDN_IMAGE_URL = CDN_BASE + "/launcher/images/%s.png";

    private static JsonArray sCachedPacks = null;
    private final Map<String, String> mHeaders;

    public ATLauncherApi() {
        mHeaders = new HashMap<>();
        mHeaders.put("User-Agent", "OnyxLauncher/1.0.0 (Android)");
    }

    private synchronized JsonArray getPublicPacks() {
        if (sCachedPacks != null) {
            return sCachedPacks;
        }
        try {
            String rawJson = ApiHandler.getRaw(mHeaders, PACKS_URL);
            if (rawJson != null) {
                JsonObject response = new Gson().fromJson(rawJson, JsonObject.class);
                if (response != null && response.has("error") && !response.get("error").getAsBoolean()
                        && response.has("data")) {
                    sCachedPacks = response.getAsJsonArray("data");
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to load public packs", e);
        }
        return sCachedPacks;
    }

    private static String buildIconUrl(JsonObject pack) {
        String safeName = pack.has("safeName") && !pack.get("safeName").isJsonNull()
                ? pack.get("safeName").getAsString().trim() : "";
        if (safeName.isEmpty()) {
            safeName = pack.has("name") && !pack.get("name").isJsonNull()
                    ? pack.get("name").getAsString().trim() : "";
        }
        if (safeName.isEmpty()) return "";
        return String.format(Locale.ROOT, CDN_IMAGE_URL, safeName.toLowerCase(Locale.ROOT));
    }

    private static String getDisplayName(JsonObject pack) {
        String name = pack.has("name") && !pack.get("name").isJsonNull()
                ? pack.get("name").getAsString().trim() : "";
        if (!name.isEmpty() && !name.contains(" ")) {
            name = name.replaceAll("([a-z])([A-Z])", "$1 $2").trim();
        }
        return name;
    }

    @Override
    public SearchResult searchMod(SearchFilters searchFilters, SearchResult previousPageResult) {
        JsonArray packs = getPublicPacks();
        if (packs == null) return null;

        String query = searchFilters.name != null ? searchFilters.name.trim().toLowerCase(Locale.ROOT) : "";
        ArrayList<ModItem> filtered = new ArrayList<>();

        for (JsonElement element : packs) {
            JsonObject pack = element.getAsJsonObject();
            String name = pack.has("name") ? pack.get("name").getAsString() : "";
            String safeName = pack.has("safeName") ? pack.get("safeName").getAsString() : name;
            String displayName = getDisplayName(pack);
            String description = pack.has("description") && !pack.get("description").isJsonNull()
                    ? pack.get("description").getAsString() : "";

            if (query.isEmpty()
                    || name.toLowerCase(Locale.ROOT).contains(query)
                    || safeName.toLowerCase(Locale.ROOT).contains(query)
                    || displayName.toLowerCase(Locale.ROOT).contains(query)
                    || description.toLowerCase(Locale.ROOT).contains(query)) {

                String iconUrl = buildIconUrl(pack);

                ModItem modItem = new ModItem(
                        Constants.SOURCE_ATLAUNCHER,
                        true,
                        safeName,     // id is safeName
                        displayName,  // title is readable
                        description,
                        iconUrl
                );
                filtered.add(modItem);
            }

            if (filtered.size() >= 200) break;
        }

        SearchResult result = new SearchResult();
        result.results = filtered.toArray(new ModItem[0]);
        result.totalResultCount = filtered.size();
        return result;
    }

    @Override
    public ModDetail getModDetails(ModItem item) {
        JsonArray packs = getPublicPacks();
        if (packs == null) return null;

        JsonObject foundPack = null;
        for (JsonElement element : packs) {
            JsonObject pack = element.getAsJsonObject();
            String safeName = pack.has("safeName") ? pack.get("safeName").getAsString() : "";
            String name = pack.has("name") ? pack.get("name").getAsString() : "";
            if (safeName.equalsIgnoreCase(item.id) || name.equalsIgnoreCase(item.id)) {
                foundPack = pack;
                break;
            }
        }

        if (foundPack == null) return null;

        try {
            JsonArray versions = foundPack.getAsJsonArray("versions");
            if (versions == null || versions.size() == 0) {
                return null;
            }

            int length = versions.size();
            String[] versionNames = new String[length];
            String[] mcVersionNames = new String[length];
            String[] versionLoaders = new String[length];
            String[] versionUrls = new String[length];
            String[] hashes = new String[length];
            String[] dependencies = new String[length];

            for (int i = 0; i < length; i++) {
                JsonObject v = versions.get(i).getAsJsonObject();
                String versionVal = v.has("version") ? v.get("version").getAsString() : "1.0.0";
                String mcVal = v.has("minecraft") ? v.get("minecraft").getAsString() : "1.12.2";

                versionNames[i] = "Version " + versionVal;
                mcVersionNames[i] = mcVal;
                versionLoaders[i] = "forge";
                versionUrls[i] = versionVal; // store version string
                hashes[i] = "";
                dependencies[i] = "";
            }

            return new ModDetail(
                    item,
                    versionNames,
                    mcVersionNames,
                    versionLoaders,
                    versionUrls,
                    hashes,
                    dependencies
            );
        } catch (Exception e) {
            Log.e(TAG, "Failed to parse pack details for " + item.id, e);
        }
        return null;
    }

    @Override
    public ModLoader installMod(ModDetail modDetail, int selectedVersion) throws IOException {
        String versionVal = modDetail.versionUrls[selectedVersion];
        String safeName = modDetail.id;

        // Fetch Configs.json for this version
        String configsJsonUrl = String.format("%s/packs/%s/versions/%s/Configs.json",
                CDN_BASE, safeName, versionVal);
        String rawConfigsJson = ApiHandler.getRaw(mHeaders, configsJsonUrl);
        if (rawConfigsJson == null) {
            throw new IOException("Failed to load pack configuration from " + configsJsonUrl);
        }

        JsonObject configsObj = new Gson().fromJson(rawConfigsJson, JsonObject.class);
        if (configsObj == null) {
            throw new IOException("Invalid Configs.json received for " + safeName);
        }

        String mcVersion = configsObj.has("minecraft") && !configsObj.get("minecraft").isJsonNull()
                ? configsObj.get("minecraft").getAsString() : modDetail.mcVersionNames[selectedVersion];
        if (mcVersion == null || mcVersion.isEmpty()) mcVersion = "1.12.2";

        int loaderType = ModLoader.MOD_LOADER_FORGE;
        String loaderVersion = "recommended";

        // Generate safe unique instance folder name
        String modpackName = ModpackInstaller.safeModpackFileName(
                modDetail.title, modDetail.versionNames[selectedVersion], "");
        int count = 1;
        String uniqueName = modpackName;
        LauncherProfiles.load();
        while (new File(Tools.DIR_GAME_HOME, "custom_instances/" + uniqueName).exists()
                || (LauncherProfiles.mainProfileJson.profiles != null
                && LauncherProfiles.mainProfileJson.profiles.containsKey(uniqueName))) {
            uniqueName = modpackName + "_" + count;
            count++;
        }
        modpackName = uniqueName;

        File instanceDir = new File(Tools.DIR_GAME_HOME, "custom_instances/" + modpackName);
        File modsDir = new File(instanceDir, "mods");
        if (!modsDir.exists()) modsDir.mkdirs();

        // 1. Download and extract Configs.zip if present
        String configsZipUrl = String.format("%s/packs/%s/versions/%s/Configs.zip",
                CDN_BASE, safeName, versionVal);
        File tempConfigsZip = new File(Tools.DIR_CACHE, modpackName + "_configs.zip");
        try {
            ProgressLayout.setProgress(ProgressLayout.INSTALL_MODPACK, 5,
                    R.string.modpack_download_downloading_metadata, 1, 2);
            DownloadUtils.downloadFileMonitored(configsZipUrl, tempConfigsZip, new byte[8192], null);
            if (tempConfigsZip.exists() && tempConfigsZip.length() > 0) {
                try (ZipFile zf = new ZipFile(tempConfigsZip)) {
                    ZipUtils.zipExtract(zf, "", instanceDir);
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "Configs.zip could not be extracted (maybe not present): " + e.getMessage());
        } finally {
            if (tempConfigsZip.exists()) tempConfigsZip.delete();
        }

        // 2. Parse mods list from Configs.json and download
        if (configsObj.has("mods") && configsObj.get("mods").isJsonArray()) {
            JsonArray modsArray = configsObj.getAsJsonArray("mods");
            ModDownloader downloader = new ModDownloader(instanceDir, true);

            for (JsonElement el : modsArray) {
                if (!el.isJsonObject()) continue;
                JsonObject modObj = el.getAsJsonObject();

                // Skip server-only mods
                if (modObj.has("server") && modObj.get("server").getAsBoolean()
                        && modObj.has("client") && !modObj.get("client").getAsBoolean()) {
                    continue;
                }

                String type = modObj.has("type") ? modObj.get("type").getAsString().toLowerCase(Locale.ROOT) : "mods";
                String rawUrl = modObj.has("url") ? modObj.get("url").getAsString() : "";
                String file = modObj.has("file") ? modObj.get("file").getAsString() : "";
                String sha1 = modObj.has("sha1") && !modObj.get("sha1").isJsonNull() ? modObj.get("sha1").getAsString().trim() : null;

                // Detect Forge version
                if ("forge".equalsIgnoreCase(type)) {
                    loaderType = ModLoader.MOD_LOADER_FORGE;
                    if (modObj.has("version")) {
                        loaderVersion = modObj.get("version").getAsString();
                    }
                    continue;
                }

                if (rawUrl.isEmpty()) continue;

                // Build download URL
                String finalUrl;
                if (rawUrl.startsWith("http://") || rawUrl.startsWith("https://")) {
                    finalUrl = rawUrl;
                } else {
                    String relative = rawUrl.startsWith("/") ? rawUrl.substring(1) : rawUrl;
                    String[] parts = relative.split("/");
                    StringBuilder encoded = new StringBuilder();
                    for (int p = 0; p < parts.length; p++) {
                        if (p > 0) encoded.append("/");
                        try {
                            encoded.append(URLEncoder.encode(parts[p], "UTF-8").replace("+", "%20"));
                        } catch (Exception ex) {
                            encoded.append(parts[p]);
                        }
                    }
                    finalUrl = CDN_BASE + "/" + encoded;
                }

                // Determine relative path in instance
                String relativePath;
                if ("extract".equalsIgnoreCase(type)) {
                    relativePath = "mods/" + file;
                } else if ("coremods".equalsIgnoreCase(type)) {
                    relativePath = "coremods/" + file;
                } else {
                    relativePath = "mods/" + file;
                }

                final String dlUrl = finalUrl;
                final String destPath = relativePath;
                final String hash = (sha1 != null && !sha1.isEmpty()) ? sha1 : null;

                downloader.submitDownload(() -> new ModDownloader.FileInfo(dlUrl, destPath, hash));
            }

            downloader.awaitFinish((c, m) ->
                    ProgressKeeper.submitProgress(ProgressLayout.INSTALL_MODPACK,
                            (int) Math.max((float) c / m * 100, 0),
                            R.string.modpack_download_downloading_mods_fc, c, m));
        }

        ModLoader modLoaderInfo = new ModLoader(loaderType, loaderVersion, mcVersion);

        // Ensure unique display name
        String baseDisplayName = modDetail.title;
        String displayName = baseDisplayName;
        int displayCount = 1;
        boolean nameExists = true;
        while (nameExists) {
            nameExists = false;
            if (LauncherProfiles.mainProfileJson.profiles != null) {
                for (MinecraftProfile ep : LauncherProfiles.mainProfileJson.profiles.values()) {
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

        // Create profile in launcher
        MinecraftProfile profile = new MinecraftProfile();
        profile.gameDir = "./custom_instances/" + modpackName;
        profile.name = displayName;
        profile.lastVersionId = modLoaderInfo.getVersionId();
        profile.pojavRendererName = "vulkan_zink";
        profile.javaDir = Tools.LAUNCHERPROFILES_RTPREFIX + "Internal-21";
        profile.ramAllocation = Math.max(3072, Math.min(4096,
                LauncherPreferences.PREF_RAM_ALLOCATION + 1024));

        ModIconCache.ensureIconCached(modDetail);
        profile.icon = ModIconCache.getBase64Image(modDetail.getIconCacheTag());

        LauncherProfiles.mainProfileJson.profiles.put(modpackName, profile);
        LauncherProfiles.write();

        modLoaderInfo.profileId = modpackName;
        modLoaderInfo.displayName = displayName;

        return modLoaderInfo;
    }
}
