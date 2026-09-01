package com.cannon.onyxlauncher.modloaders.modpacks.api;

import android.content.Context;
import android.util.ArrayMap;
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
import com.cannon.onyxlauncher.utils.ZipUtils;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.ZipFile;

public class ATLauncherApi implements ModpackApi {
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
            String rawJson = ApiHandler.getRaw(mHeaders, "https://api.atlauncher.com/v1/packs/full/public");
            if (rawJson != null) {
                JsonObject response = new Gson().fromJson(rawJson, JsonObject.class);
                if (response != null && !response.get("error").getAsBoolean()) {
                    sCachedPacks = response.getAsJsonArray("data");
                }
            }
        } catch (Exception e) {
            Log.e("ATLauncherApi", "Failed to load public packs", e);
        }
        return sCachedPacks;
    }

    @Override
    public SearchResult searchMod(SearchFilters searchFilters, SearchResult previousPageResult) {
        JsonArray packs = getPublicPacks();
        if (packs == null) return null;

        String query = searchFilters.name != null ? searchFilters.name.trim().toLowerCase() : "";
        ArrayList<ModItem> filtered = new ArrayList<>();

        for (JsonElement element : packs) {
            JsonObject pack = element.getAsJsonObject();
            String displayName = pack.has("displayName") ? pack.get("displayName").getAsString() : "";
            String name = pack.has("name") ? pack.get("name").getAsString() : "";
            String description = pack.has("description") ? pack.get("description").getAsString() : "";

            if (query.isEmpty() || 
                displayName.toLowerCase().contains(query) || 
                name.toLowerCase().contains(query) || 
                description.toLowerCase().contains(query)) {
                
                String iconUrl = pack.has("icon") && !pack.get("icon").isJsonNull() ? pack.get("icon").getAsString() : "";
                
                ModItem modItem = new ModItem(
                        Constants.SOURCE_ATLAUNCHER,
                        true,
                        name,
                        displayName,
                        description,
                        iconUrl
                );
                filtered.add(modItem);
            }
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
            String name = pack.has("name") ? pack.get("name").getAsString() : "";
            if (name.equalsIgnoreCase(item.id)) {
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
                String versionVal = v.get("version").getAsString();
                String mcVal = v.get("minecraft").getAsString();
                
                versionNames[i] = "Version " + versionVal;
                mcVersionNames[i] = mcVal;
                versionLoaders[i] = "forge";
                
                String downloadUrl = String.format("https://download.atlauncher.com/packs/%s/%s/%s-%s.zip", 
                        item.id, versionVal, item.id, versionVal);
                versionUrls[i] = downloadUrl;
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
            Log.e("ATLauncherApi", "Failed to parse pack details from cache for " + item.id, e);
        }
        return null;
    }


    @Override
    public ModLoader installMod(ModDetail modDetail, int selectedVersion) throws IOException {
        return ModpackInstaller.installModpack(modDetail, selectedVersion, (zipFile, instanceDestination) -> {
            try (ZipFile modpackZipFile = new ZipFile(zipFile)) {
                ProgressLayout.setProgress(ProgressLayout.INSTALL_MODPACK, 10, R.string.modpack_download_applying_overrides, 1, 2);
                ZipUtils.zipExtract(modpackZipFile, "", instanceDestination);
                ProgressLayout.setProgress(ProgressLayout.INSTALL_MODPACK, 90, R.string.modpack_download_applying_overrides, 2, 2);
                
                String mcVersion = modDetail.mcVersionNames[selectedVersion];
                return new ModLoader(ModLoader.MOD_LOADER_FORGE, "recommended", mcVersion);
            }
        });
    }
}
