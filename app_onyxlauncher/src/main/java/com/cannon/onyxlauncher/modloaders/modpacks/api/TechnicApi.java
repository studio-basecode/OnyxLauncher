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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.ZipFile;

public class TechnicApi implements ModpackApi {
    private static final String TAG = "TechnicApi";
    private static final String BASE_URL = "https://api.technicpack.net";
    private final ApiHandler mApiHandler;
    private final Map<String, String> mHeaders;

    public TechnicApi() {
        mApiHandler = new ApiHandler(BASE_URL);
        mHeaders = new HashMap<>();
        mHeaders.put("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) OnyxLauncher/1.0");
    }

    private static String extractIconUrl(JsonObject packObj) {
        if (packObj == null) return "";
        if (packObj.has("icon") && packObj.get("icon").isJsonObject()) {
            JsonObject iconObj = packObj.getAsJsonObject("icon");
            if (iconObj.has("url") && !iconObj.get("url").isJsonNull()) {
                String url = iconObj.get("url").getAsString();
                if (url.startsWith("//")) url = "https:" + url;
                return url;
            }
        }
        return "";
    }

    private static String extractBackgroundUrl(JsonObject packObj) {
        if (packObj == null) return "";
        if (packObj.has("background") && packObj.get("background").isJsonObject()) {
            JsonObject bgObj = packObj.getAsJsonObject("background");
            if (bgObj.has("url") && !bgObj.get("url").isJsonNull()) {
                String url = bgObj.get("url").getAsString();
                if (url.startsWith("//")) url = "https:" + url;
                return url;
            }
        }
        return "";
    }

    private JsonObject fetchPackJson(String slug) {
        try {
            return mApiHandler.get("modpack/" + slug + "?build=recommended", JsonObject.class);
        } catch (Exception e) {
            Log.e(TAG, "Failed to fetch pack: " + slug, e);
            return null;
        }
    }

    private void parseAndAddModpacks(JsonObject response, ArrayList<ModItem> list) {
        if (response != null && response.has("modpacks") && response.get("modpacks").isJsonArray()) {
            JsonArray modpacks = response.getAsJsonArray("modpacks");
            for (JsonElement el : modpacks) {
                if (!el.isJsonObject()) continue;
                JsonObject p = el.getAsJsonObject();
                String slug = p.has("slug") && !p.get("slug").isJsonNull() ? p.get("slug").getAsString() : "";
                String name = p.has("name") && !p.get("name").isJsonNull() ? p.get("name").getAsString() : slug;
                if (slug.isEmpty()) continue;

                // Avoid duplicates
                boolean exists = false;
                for (ModItem item : list) {
                    if (slug.equalsIgnoreCase(item.id)) {
                        exists = true;
                        break;
                    }
                }
                if (exists) continue;

                String iconUrl = "";
                if (p.has("iconUrl") && !p.get("iconUrl").isJsonNull()) {
                    iconUrl = p.get("iconUrl").getAsString();
                } else if (p.has("icon") && p.get("icon").isJsonObject()) {
                    iconUrl = extractIconUrl(p);
                }
                if (iconUrl.startsWith("//")) iconUrl = "https:" + iconUrl;

                String description = p.has("description") && !p.get("description").isJsonNull()
                        ? p.get("description").getAsString() : "";

                list.add(new ModItem(Constants.SOURCE_TECHNIC, true, slug, name, description, iconUrl));
            }
        }
    }

    @Override
    public SearchResult searchMod(SearchFilters searchFilters, SearchResult previousPageResult) {
        ArrayList<ModItem> results = new ArrayList<>();

        if (searchFilters.name == null || searchFilters.name.trim().isEmpty()) {
            // Add classic popular modpacks first
            results.add(new ModItem(Constants.SOURCE_TECHNIC, true, "attack-of-the-bteam",
                    "Attack of the B-Team",
                    "Designed with one thing in mind, crazy mad science! Play with some of the wackiest mods we could find!",
                    "https://cdn.technicpack.net/platform2/pack-icons/552556.png?1631992549"));

            results.add(new ModItem(Constants.SOURCE_TECHNIC, true, "tekkit-lite",
                    "Tekkit Lite",
                    "Tekkit Lite is a premium technical modpack featuring IC2, Redpower, Buildcraft, and more.",
                    "https://cdn.technicpack.net/platform2/pack-icons/552553.png"));

            results.add(new ModItem(Constants.SOURCE_TECHNIC, true, "hexxit",
                    "Hexxit",
                    "Hexxit is an adventure-based modpack. Explore dungeons, fight bosses, and collect rare loot.",
                    "https://cdn.technicpack.net/platform2/pack-icons/552555.png"));

            results.add(new ModItem(Constants.SOURCE_TECHNIC, true, "classic-tekkit",
                    "Tekkit Classic",
                    "The absolute classic technical modpack featuring Equivalent Exchange, IndustrialCraft, and BuildCraft.",
                    "https://cdn.technicpack.net/platform2/pack-icons/552552.png"));

            results.add(new ModItem(Constants.SOURCE_TECHNIC, true, "hexxit-updated",
                    "Hexxit Updated",
                    "A modern continuation of the classic Hexxit experience updated for newer Minecraft versions.",
                    "https://cdn.technicpack.net/platform2/pack-icons/1583099.png"));

            results.add(new ModItem(Constants.SOURCE_TECHNIC, true, "voltz",
                    "Voltz",
                    "Voltz is centered on electricity, modern machinery, and strategic military missile warfare.",
                    "https://cdn.technicpack.net/platform2/pack-icons/552554.png"));

            results.add(new ModItem(Constants.SOURCE_TECHNIC, true, "tekkitmain",
                    "Tekkit",
                    "Tekkit brings galactic exploration, dimension doors, dimensional travel, and automation.",
                    "https://cdn.technicpack.net/platform2/pack-icons/552557.png"));

            results.add(new ModItem(Constants.SOURCE_TECHNIC, true, "the-1710-pack",
                    "The 1.7.10 Pack",
                    "One of the most popular 1.7.10 modpacks featuring over 200 mods combining technology and magic.",
                    "https://cdn.technicpack.net/platform2/pack-icons/249870.png"));

            // Also fetch trending modpacks from Technic API
            try {
                JsonObject trendingObj = mApiHandler.get("trending?build=999", JsonObject.class);
                parseAndAddModpacks(trendingObj, results);
            } catch (Exception e) {
                Log.w(TAG, "Failed to fetch trending packs: " + e.getMessage());
            }

            SearchResult result = new SearchResult();
            result.results = results.toArray(new ModItem[0]);
            result.totalResultCount = results.size();
            return result;
        }

        String query = searchFilters.name.trim();
        try {
            String encodedQuery = java.net.URLEncoder.encode(query, "UTF-8");
            JsonObject searchObj = mApiHandler.get("search?q=" + encodedQuery + "&build=999", JsonObject.class);
            parseAndAddModpacks(searchObj, results);
        } catch (Exception e) {
            Log.w(TAG, "Technic search query failed: " + e.getMessage());
        }

        // Fallback: Check if query matches a direct pack slug
        if (results.isEmpty()) {
            String slug = query.toLowerCase().replace(" ", "-");
            JsonObject packObj = fetchPackJson(slug);
            if (packObj != null && packObj.has("name")) {
                String name = packObj.get("name").getAsString();
                String displayName = packObj.has("displayName") && !packObj.get("displayName").isJsonNull()
                        ? packObj.get("displayName").getAsString() : name;
                String description = packObj.has("description") && !packObj.get("description").isJsonNull()
                        ? packObj.get("description").getAsString() : "";
                String iconUrl = extractIconUrl(packObj);

                results.add(new ModItem(Constants.SOURCE_TECHNIC, true, slug, displayName, description, iconUrl));
            }
        }

        SearchResult result = new SearchResult();
        result.results = results.toArray(new ModItem[0]);
        result.totalResultCount = results.size();
        return result;
    }

    @Override
    public ModDetail getModDetails(ModItem item) {
        try {
            JsonObject response = fetchPackJson(item.id);
            if (response == null) return null;

            String mcVersion = response.has("minecraft") && !response.get("minecraft").isJsonNull()
                    ? response.get("minecraft").getAsString() : "1.7.10";
            String forgeVersion = response.has("forge") && !response.get("forge").isJsonNull()
                    ? response.get("forge").getAsString() : "";

            ArrayList<String> versionNames = new ArrayList<>();
            ArrayList<String> mcVersionNames = new ArrayList<>();
            ArrayList<String> versionLoaders = new ArrayList<>();
            ArrayList<String> versionUrls = new ArrayList<>();
            ArrayList<String> hashes = new ArrayList<>();
            ArrayList<String> dependencies = new ArrayList<>();

            // If pack has a direct zip url
            String directZipUrl = response.has("url") && !response.get("url").isJsonNull()
                    ? response.get("url").getAsString() : "";
            if (directZipUrl.startsWith("//")) directZipUrl = "https:" + directZipUrl;

            // If pack uses Solder
            String solderUrl = response.has("solder") && !response.get("solder").isJsonNull()
                    ? response.get("solder").getAsString() : "";
            if (!solderUrl.isEmpty() && !solderUrl.endsWith("/")) solderUrl += "/";

            String recVersion = response.has("version") && !response.get("version").isJsonNull()
                    ? response.get("version").getAsString() : "recommended";

            if (!solderUrl.isEmpty()) {
                // Solder-based pack
                versionNames.add("Build " + recVersion + " (Recommended)");
                mcVersionNames.add(mcVersion);
                versionLoaders.add(forgeVersion.isEmpty() ? "vanilla" : "forge-" + forgeVersion);
                // Store solder url + build for installation
                versionUrls.add("solder:" + solderUrl + "modpack/" + item.id + "/" + recVersion);
                hashes.add("");
                dependencies.add("");
            } else if (!directZipUrl.isEmpty() && directZipUrl.startsWith("http")) {
                // Direct zip pack
                versionNames.add("Version " + recVersion);
                mcVersionNames.add(mcVersion);
                versionLoaders.add(forgeVersion.isEmpty() ? "vanilla" : "forge-" + forgeVersion);
                versionUrls.add(directZipUrl);
                hashes.add("");
                dependencies.add("");
            } else {
                versionNames.add("Recommended");
                mcVersionNames.add(mcVersion);
                versionLoaders.add(forgeVersion.isEmpty() ? "vanilla" : "forge-" + forgeVersion);
                versionUrls.add("https://api.technicpack.net/modpack/" + item.id);
                hashes.add("");
                dependencies.add("");
            }

            String bannerUrl = extractBackgroundUrl(response);

            ModDetail detail = new ModDetail(
                    item,
                    versionNames.toArray(new String[0]),
                    mcVersionNames.toArray(new String[0]),
                    versionLoaders.toArray(new String[0]),
                    versionUrls.toArray(new String[0]),
                    hashes.toArray(new String[0]),
                    dependencies.toArray(new String[0])
            );

            if (!bannerUrl.isEmpty()) {
                detail.screenshotUrls = new String[]{bannerUrl};
            }
            return detail;

        } catch (Exception e) {
            Log.e(TAG, "Failed to fetch details for Technic pack: " + item.id, e);
        }
        return null;
    }

    @Override
    public ModLoader installMod(ModDetail modDetail, int selectedVersion) throws IOException {
        String targetUrl = modDetail.versionUrls[selectedVersion];
        String slug = modDetail.id;

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
        if (!instanceDir.exists()) instanceDir.mkdirs();

        String mcVersion = modDetail.mcVersionNames[selectedVersion];
        String loaderText = modDetail.versionLoaders[selectedVersion];
        int loaderType = ModLoader.MOD_LOADER_FORGE;
        String loaderVersion = "recommended";
        if (loaderText != null && loaderText.startsWith("forge-")) {
            loaderVersion = loaderText.substring(6);
        }

        if (targetUrl.startsWith("solder:")) {
            // Solder installation: fetch build JSON and download each mod
            String solderApiUrl = targetUrl.substring(7);
            String rawJson = ApiHandler.getRaw(mHeaders, solderApiUrl);
            if (rawJson == null) {
                throw new IOException("Failed to fetch Solder build from " + solderApiUrl);
            }

            JsonObject buildObj = new Gson().fromJson(rawJson, JsonObject.class);
            if (buildObj == null || !buildObj.has("mods")) {
                throw new IOException("Invalid Solder build data from " + solderApiUrl);
            }

            if (buildObj.has("minecraft") && !buildObj.get("minecraft").isJsonNull()) {
                mcVersion = buildObj.get("minecraft").getAsString();
            }
            if (buildObj.has("forge") && !buildObj.get("forge").isJsonNull()) {
                loaderVersion = buildObj.get("forge").getAsString();
            }

            JsonArray mods = buildObj.getAsJsonArray("mods");
            int totalMods = mods.size();

            // Solder mods are zip archives containing mods/, config/, etc.
            // Download and extract each one
            File tempCacheDir = new File(Tools.DIR_CACHE, "solder_temp_" + modpackName);
            if (!tempCacheDir.exists()) tempCacheDir.mkdirs();

            try {
                for (int i = 0; i < totalMods; i++) {
                    JsonObject modObj = mods.get(i).getAsJsonObject();
                    String modUrl = modObj.has("url") ? modObj.get("url").getAsString() : "";
                    String modName = modObj.has("name") ? modObj.get("name").getAsString() : ("mod_" + i);
                    if (modUrl.isEmpty() || !modUrl.startsWith("http")) continue;

                    int progress = (int) (((float) (i + 1) / totalMods) * 100);
                    ProgressLayout.setProgress(ProgressLayout.INSTALL_MODPACK, progress,
                            R.string.modpack_download_downloading_mods_fc, i + 1, totalMods);

                    File modZip = new File(tempCacheDir, modName + ".zip");
                    try {
                        DownloadUtils.downloadFileMonitored(modUrl, modZip, new byte[8192], null);
                        if (modZip.exists() && modZip.length() > 0) {
                            try (ZipFile zf = new ZipFile(modZip)) {
                                ZipUtils.zipExtract(zf, "", instanceDir);
                            }
                        }
                    } catch (Exception ex) {
                        Log.w(TAG, "Failed to download/extract Solder mod " + modName + ": " + ex.getMessage());
                    } finally {
                        if (modZip.exists()) modZip.delete();
                    }
                }
            } finally {
                if (tempCacheDir.exists()) tempCacheDir.delete();
            }

        } else if (targetUrl.startsWith("http")) {
            // Direct zip file installation
            File modpackZip = new File(Tools.DIR_CACHE, modpackName + ".zip");
            try {
                ProgressLayout.setProgress(ProgressLayout.INSTALL_MODPACK, 10,
                        R.string.modpack_download_downloading_metadata, 1, 2);
                DownloadUtils.downloadFileMonitored(targetUrl, modpackZip, new byte[8192], null);

                ProgressLayout.setProgress(ProgressLayout.INSTALL_MODPACK, 80,
                        R.string.modpack_download_applying_overrides, 2, 2);
                if (modpackZip.exists() && modpackZip.length() > 0) {
                    try (ZipFile zf = new ZipFile(modpackZip)) {
                        ZipUtils.zipExtract(zf, "", instanceDir);
                    }
                }
            } finally {
                if (modpackZip.exists()) modpackZip.delete();
            }
        } else {
            throw new IOException("Unsupported Technic download URL: " + targetUrl);
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
