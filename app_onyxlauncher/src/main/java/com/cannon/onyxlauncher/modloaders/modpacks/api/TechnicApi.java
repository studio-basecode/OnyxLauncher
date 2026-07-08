package com.cannon.onyxlauncher.modloaders.modpacks.api;

import android.content.Context;
import android.util.Log;
import com.google.gson.JsonObject;
import com.google.gson.JsonElement;
import com.google.gson.JsonArray;
import com.kdt.mcgui.ProgressLayout;

import com.cannon.onyxlauncher.R;
import com.cannon.onyxlauncher.Tools;
import com.cannon.onyxlauncher.modloaders.modpacks.models.Constants;
import com.cannon.onyxlauncher.modloaders.modpacks.models.ModDetail;
import com.cannon.onyxlauncher.modloaders.modpacks.models.ModItem;
import com.cannon.onyxlauncher.modloaders.modpacks.models.SearchFilters;
import com.cannon.onyxlauncher.modloaders.modpacks.models.SearchResult;
import com.cannon.onyxlauncher.progresskeeper.DownloaderProgressWrapper;
import com.cannon.onyxlauncher.utils.ZipUtils;
import com.cannon.onyxlauncher.utils.FileUtils;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.zip.ZipFile;

public class TechnicApi implements ModpackApi {
    private static final String SOLDER_URL_PREFIX = "solder|";
    private final ApiHandler mApiHandler;

    public TechnicApi() {
        mApiHandler = new ApiHandler("https://api.technicpack.net");
    }

    @Override
    public SearchResult searchMod(SearchFilters searchFilters, SearchResult previousPageResult) {
        if (searchFilters.name == null || searchFilters.name.trim().isEmpty()) {
            ArrayList<ModItem> popular = new ArrayList<>();
            String technicDefaultIcon = "";
            popular.add(new ModItem(Constants.SOURCE_TECHNIC, true, "attack-of-the-bteam", "Attack of the B-Team", "A crazy modpack featuring mad science, technology, and dinosaurs! Lead the way in biological science or high-tech machinery.", technicDefaultIcon));
            popular.add(new ModItem(Constants.SOURCE_TECHNIC, true, "tekkit-lite", "Tekkit Lite", "Tekkit Lite is a premium classic technical modpack. Play with IC2, Redpower, Buildcraft, and more.", technicDefaultIcon));
            popular.add(new ModItem(Constants.SOURCE_TECHNIC, true, "hexxit", "Hexxit", "Hexxit is a legendary adventure-based modpack. Explore dungeons, fight bosses, and collect rare loot.", technicDefaultIcon));
            popular.add(new ModItem(Constants.SOURCE_TECHNIC, true, "classic-tekkit", "Tekkit Classic", "The absolute classic technical modpack featuring Equivalent Exchange, IndustrialCraft, and BuildCraft.", technicDefaultIcon));
            popular.add(new ModItem(Constants.SOURCE_TECHNIC, true, "hexxit-updated", "Hexxit Updated", "A modern take on the classic Hexxit experience updated for newer Minecraft versions.", technicDefaultIcon));

            SearchResult result = new SearchResult();
            result.results = popular.toArray(new ModItem[0]);
            result.totalResultCount = popular.size();
            return result;
        }

        String slug = searchFilters.name.trim().toLowerCase().replace(" ", "-");
        try {
            JsonObject response = mApiHandler.get("modpack/" + slug + "?build=recommended", JsonObject.class);
            if (response != null && response.has("name")) {
                String name = response.get("name").getAsString();
                String displayName = response.has("displayName") ? response.get("displayName").getAsString() : name;
                String description = response.has("description") ? response.get("description").getAsString() : "";
                
                String iconUrl = "";
                if (response.has("icon") && response.get("icon").isJsonObject()) {
                    JsonObject iconObj = response.getAsJsonObject("icon");
                    if (iconObj.has("url") && !iconObj.get("url").isJsonNull()) {
                        iconUrl = ModpackUrlUtils.normalizeUrl(iconObj.get("url").getAsString());
                    }
                }

                ModItem modItem = new ModItem(
                        Constants.SOURCE_TECHNIC,
                        true,
                        name,
                        displayName,
                        description,
                        iconUrl
                );

                SearchResult result = new SearchResult();
                result.results = new ModItem[]{modItem};
                result.totalResultCount = 1;
                return result;
            }
        } catch (Exception e) {
            Log.e("TechnicApi", "Failed to search Technic pack: " + slug, e);
        }
        return null;
    }

    @Override
    public ModDetail getModDetails(ModItem item) {
        try {
            JsonObject response = mApiHandler.get("modpack/" + item.id + "?build=recommended", JsonObject.class);
            if (response != null) {
                String mcVersion = response.has("minecraft") && !response.get("minecraft").isJsonNull() ? response.get("minecraft").getAsString() : "1.7.10";
                String forgeVersion = response.has("forge") && !response.get("forge").isJsonNull() ? response.get("forge").getAsString() : "";
                String downloadUrl = response.has("url") && !response.get("url").isJsonNull() ? ModpackUrlUtils.normalizeUrl(response.get("url").getAsString()) : "";
                String solderUrl = response.has("solder") && !response.get("solder").isJsonNull()
                        ? ModpackUrlUtils.normalizeUrl(response.get("solder").getAsString()) : "";

                // Technic usually lists one or two recommended builds
                ArrayList<String> versionNames = new ArrayList<>();
                ArrayList<String> mcVersionNames = new ArrayList<>();
                ArrayList<String> versionLoaders = new ArrayList<>();
                ArrayList<String> versionUrls = new ArrayList<>();
                ArrayList<String> hashes = new ArrayList<>();
                ArrayList<String> dependencies = new ArrayList<>();

                if (ModpackUrlUtils.isHttpUrl(solderUrl)) {
                    JsonObject solderPack = getSolderPackInfo(solderUrl, item.id);
                    if (solderPack != null) {
                        Set<String> buildNames = new LinkedHashSet<>();
                        String recommended = getString(solderPack, "recommended");
                        String latest = getString(solderPack, "latest");
                        if (!recommended.isEmpty()) buildNames.add(recommended);
                        if (!latest.isEmpty()) buildNames.add(latest);
                        JsonArray builds = solderPack.has("builds") && solderPack.get("builds").isJsonArray()
                                ? solderPack.getAsJsonArray("builds") : null;
                        if (builds != null) {
                            for (JsonElement buildElement : builds) {
                                if (!buildElement.isJsonNull()) buildNames.add(buildElement.getAsString());
                            }
                        }
                        for (String buildName : buildNames) {
                            String label = "Build " + buildName;
                            if (buildName.equals(recommended)) label += " (Recommended)";
                            else if (buildName.equals(latest)) label += " (Latest)";
                            versionNames.add(label);
                            mcVersionNames.add(mcVersion);
                            versionLoaders.add(forgeVersion.isEmpty() ? "forge" : "forge-" + forgeVersion);
                            versionUrls.add(buildSolderDescriptor(solderUrl, item.id, buildName));
                            hashes.add("");
                            dependencies.add("");
                        }
                    }
                }

                if (versionNames.isEmpty() && ModpackUrlUtils.isHttpUrl(downloadUrl)) {
                    if (response.has("builds") && response.get("builds").isJsonObject() && response.getAsJsonObject("builds").has("recommended")) {
                        String recommended = response.getAsJsonObject("builds").get("recommended").getAsString();
                        versionNames.add("Build " + recommended + " (Recommended)");
                        mcVersionNames.add(mcVersion);
                        versionLoaders.add(forgeVersion.isEmpty() ? "vanilla" : "forge-" + forgeVersion);
                        versionUrls.add(downloadUrl);
                        hashes.add("");
                        dependencies.add("");
                    }

                    versionNames.add("Latest Build");
                    mcVersionNames.add(mcVersion);
                    versionLoaders.add(forgeVersion.isEmpty() ? "vanilla" : "forge-" + forgeVersion);
                    versionUrls.add(downloadUrl);
                    hashes.add("");
                    dependencies.add("");
                }

                if (versionNames.isEmpty()) {
                    return null;
                }

                String bannerUrl = "";
                if (response.has("background") && response.get("background").isJsonObject()) {
                    JsonObject bgObj = response.getAsJsonObject("background");
                    if (bgObj.has("url") && !bgObj.get("url").isJsonNull()) {
                        bannerUrl = ModpackUrlUtils.normalizeUrl(bgObj.get("url").getAsString());
                    }
                }

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
            }
        } catch (Exception e) {
            Log.e("TechnicApi", "Failed to fetch details for Technic pack: " + item.id, e);
        }
        return null;
    }

    @Override
    public ModLoader installMod(ModDetail modDetail, int selectedVersion) throws IOException {
        String selectedDescriptor = modDetail.versionUrls[selectedVersion];
        if (selectedDescriptor != null && selectedDescriptor.startsWith(SOLDER_URL_PREFIX)) {
            return installSolderPack(modDetail, selectedVersion, selectedDescriptor);
        }
        return ModpackInstaller.installModpack(modDetail, selectedVersion, (zipFile, instanceDestination) -> {
            // Technic zip contains bin, mods, config folders. We extract them directly.
            try (ZipFile modpackZipFile = new ZipFile(zipFile)) {
                ProgressLayout.setProgress(ProgressLayout.INSTALL_MODPACK, 10, R.string.modpack_download_applying_overrides, 1, 2);
                ZipUtils.zipExtract(modpackZipFile, "", instanceDestination);
                ProgressLayout.setProgress(ProgressLayout.INSTALL_MODPACK, 90, R.string.modpack_download_applying_overrides, 2, 2);
                
                // Resolve minecraft and forge versions from modDetail loaders
                // e.g. forge-10.13.4.1614
                String loaderText = modDetail.versionLoaders[selectedVersion];
                String mcVersion = modDetail.mcVersionNames[selectedVersion];
                if (loaderText.startsWith("forge-")) {
                    String forgeVersion = loaderText.substring(6);
                    return new ModLoader(ModLoader.MOD_LOADER_FORGE, forgeVersion, mcVersion);
                }
                return new ModLoader(ModLoader.MOD_LOADER_FORGE, "recommended", mcVersion);
            }
        });
    }

    private JsonObject getSolderPackInfo(String solderUrl, String slug) {
        String base = ensureTrailingSlash(solderUrl);
        return ApiHandler.getFullUrl(base + "modpack/" + slug, JsonObject.class);
    }

    private JsonObject getSolderBuildInfo(String solderUrl, String slug, String buildName) {
        String base = ensureTrailingSlash(solderUrl);
        return ApiHandler.getFullUrl(base + "modpack/" + slug + "/" + buildName, JsonObject.class);
    }

    private String buildSolderDescriptor(String solderUrl, String slug, String buildName) {
        return SOLDER_URL_PREFIX + solderUrl + "|" + slug + "|" + buildName;
    }

    private ModLoader installSolderPack(ModDetail modDetail, int selectedVersion, String descriptor) throws IOException {
        String[] parts = descriptor.split("\\|", 4);
        if (parts.length != 4) {
            throw new IOException("Invalid Technic Solder descriptor");
        }
        String solderUrl = parts[1];
        String slug = parts[2];
        String buildName = parts[3];
        JsonObject build = getSolderBuildInfo(solderUrl, slug, buildName);
        if (build == null || !build.has("mods") || !build.get("mods").isJsonArray()) {
            throw new IOException("Technic Solder did not return mod files for " + slug + " " + buildName);
        }

        String modpackName = ModpackInstaller.uniqueModpackFileName(modDetail.title, modDetail.versionNames[selectedVersion], "");
        File instanceDestination = new File(Tools.DIR_GAME_HOME, "custom_instances/" + modpackName);
        File cacheDir = new File(Tools.DIR_CACHE, "technic_solder/" + modpackName);
        FileUtils.ensureDirectory(instanceDestination);
        FileUtils.ensureDirectory(cacheDir);

        JsonArray mods = build.getAsJsonArray("mods");
        ModDownloader downloader = new ModDownloader(cacheDir);
        for (JsonElement element : mods) {
            if (!element.isJsonObject()) continue;
            JsonObject mod = element.getAsJsonObject();
            String url = ModpackUrlUtils.normalizeUrl(getString(mod, "url"));
            if (!ModpackUrlUtils.isHttpUrl(url)) {
                throw new IOException("Technic Solder file has no valid URL in " + slug + " " + buildName);
            }
            String fileName = FileUtils.getFileName(url);
            if (fileName == null || fileName.isEmpty()) {
                fileName = getString(mod, "name") + "-" + getString(mod, "version") + ".zip";
            }
            int fileSize = mod.has("filesize") && !mod.get("filesize").isJsonNull() ? mod.get("filesize").getAsInt() : 0;
            downloader.submitDownload(fileSize, fileName, null, url);
        }
        downloader.awaitFinish(new DownloaderProgressWrapper(R.string.modpack_download_downloading_mods, ProgressLayout.INSTALL_MODPACK));

        File[] archives = cacheDir.listFiles(file -> file.isFile() && file.getName().toLowerCase().endsWith(".zip"));
        int total = archives == null ? 0 : archives.length;
        if (total == 0) {
            throw new IOException("Technic Solder downloaded no installable archives");
        }
        for (int i = 0; i < total; i++) {
            ProgressLayout.setProgress(ProgressLayout.INSTALL_MODPACK, (int) ((i / (float) total) * 100f),
                    R.string.modpack_download_applying_overrides, i + 1, total);
            try (ZipFile moduleZip = new ZipFile(archives[i])) {
                ZipUtils.zipExtract(moduleZip, "", instanceDestination);
            }
        }
        deleteRecursively(cacheDir);

        String mcVersion = ModpackUrlUtils.firstNonBlank(getString(build, "minecraft"), modDetail.mcVersionNames[selectedVersion]);
        String forgeVersion = getString(build, "forge");
        if (forgeVersion.isEmpty()) {
            throw new IOException("Technic Solder pack installed files, but did not declare a Forge version for Android auto-install");
        }
        ModLoader modLoader = new ModLoader(ModLoader.MOD_LOADER_FORGE, forgeVersion, mcVersion);
        return ModpackInstaller.createInstalledProfile(modDetail, selectedVersion, modLoader, modpackName);
    }

    private static String ensureTrailingSlash(String value) {
        return value.endsWith("/") ? value : value + "/";
    }

    private static String getString(JsonObject object, String memberName) {
        if (object == null || !object.has(memberName) || object.get(memberName).isJsonNull()) return "";
        try {
            return object.get(memberName).getAsString();
        } catch (Exception e) {
            return "";
        }
    }

    private static void deleteRecursively(File file) {
        if (file == null || !file.exists()) return;
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) deleteRecursively(child);
            }
        }
        file.delete();
    }
}
