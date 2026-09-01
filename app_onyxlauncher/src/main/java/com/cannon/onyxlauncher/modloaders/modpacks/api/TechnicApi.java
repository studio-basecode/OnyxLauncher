package com.cannon.onyxlauncher.modloaders.modpacks.api;

import android.content.Context;
import android.util.Log;
import com.google.gson.JsonObject;
import com.google.gson.JsonElement;
import com.kdt.mcgui.ProgressLayout;

import com.cannon.onyxlauncher.R;
import com.cannon.onyxlauncher.Tools;
import com.cannon.onyxlauncher.modloaders.modpacks.models.Constants;
import com.cannon.onyxlauncher.modloaders.modpacks.models.ModDetail;
import com.cannon.onyxlauncher.modloaders.modpacks.models.ModItem;
import com.cannon.onyxlauncher.modloaders.modpacks.models.SearchFilters;
import com.cannon.onyxlauncher.modloaders.modpacks.models.SearchResult;
import com.cannon.onyxlauncher.utils.ZipUtils;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Map;
import java.util.zip.ZipFile;

public class TechnicApi implements ModpackApi {
    private final ApiHandler mApiHandler;

    public TechnicApi() {
        mApiHandler = new ApiHandler("https://api.technicpack.net");
    }

    @Override
    public SearchResult searchMod(SearchFilters searchFilters, SearchResult previousPageResult) {
        if (searchFilters.name == null || searchFilters.name.trim().isEmpty()) {
            ArrayList<ModItem> popular = new ArrayList<>();
            String technicDefaultIcon = "https://www.technicpack.net/assets/shared/pack-icon.png";
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
                        iconUrl = iconObj.get("url").getAsString();
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
                String downloadUrl = response.has("url") && !response.get("url").isJsonNull() ? response.get("url").getAsString() : "";

                // Technic usually lists one or two recommended builds
                ArrayList<String> versionNames = new ArrayList<>();
                ArrayList<String> mcVersionNames = new ArrayList<>();
                ArrayList<String> versionLoaders = new ArrayList<>();
                ArrayList<String> versionUrls = new ArrayList<>();
                ArrayList<String> hashes = new ArrayList<>();
                ArrayList<String> dependencies = new ArrayList<>();

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

                String bannerUrl = "";
                if (response.has("background") && response.get("background").isJsonObject()) {
                    JsonObject bgObj = response.getAsJsonObject("background");
                    if (bgObj.has("url") && !bgObj.get("url").isJsonNull()) {
                        bannerUrl = bgObj.get("url").getAsString();
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
}
