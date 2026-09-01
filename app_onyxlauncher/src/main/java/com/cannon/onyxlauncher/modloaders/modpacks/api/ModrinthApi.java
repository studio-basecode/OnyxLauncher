package com.cannon.onyxlauncher.modloaders.modpacks.api;

import android.util.Log;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.kdt.mcgui.ProgressLayout;

import com.cannon.onyxlauncher.R;
import com.cannon.onyxlauncher.Tools;
import com.cannon.onyxlauncher.modloaders.modpacks.models.Constants;
import com.cannon.onyxlauncher.modloaders.modpacks.models.ModDetail;
import com.cannon.onyxlauncher.modloaders.modpacks.models.ModItem;
import com.cannon.onyxlauncher.modloaders.modpacks.models.ModrinthIndex;
import com.cannon.onyxlauncher.modloaders.modpacks.models.SearchFilters;
import com.cannon.onyxlauncher.modloaders.modpacks.models.SearchResult;
import com.cannon.onyxlauncher.progresskeeper.DownloaderProgressWrapper;
import com.cannon.onyxlauncher.utils.ZipUtils;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.ZipFile;

public class ModrinthApi implements ModpackApi{
    private final ApiHandler mApiHandler;
    public ModrinthApi(){
        mApiHandler = new ApiHandler("https://api.modrinth.com/v2");
    }

    @Override
    public SearchResult searchMod(SearchFilters searchFilters, SearchResult previousPageResult) {
        ModrinthSearchResult modrinthSearchResult = (ModrinthSearchResult) previousPageResult;

        // Fixes an issue where the offset being equal or greater than total_hits is ignored
        if (modrinthSearchResult != null && modrinthSearchResult.previousOffset >= modrinthSearchResult.totalResultCount) {
            ModrinthSearchResult emptyResult = new ModrinthSearchResult();
            emptyResult.results = new ModItem[0];
            emptyResult.totalResultCount = modrinthSearchResult.totalResultCount;
            emptyResult.previousOffset = modrinthSearchResult.previousOffset;
            return emptyResult;
        }


        // Build the facets filters
        HashMap<String, Object> params = new HashMap<>();
        StringBuilder facetString = new StringBuilder();
        facetString.append("[");
        String type = searchFilters.projectType != null ? searchFilters.projectType : (searchFilters.isModpack ? "modpack" : "mod");
        facetString.append(String.format("[\"project_type:%s\"]", type));
        if(searchFilters.mcVersion != null && !searchFilters.mcVersion.isEmpty())
            facetString.append(String.format(",[\"versions:%s\"]", searchFilters.mcVersion));
        if (searchFilters.modLoader != null && !searchFilters.modLoader.isEmpty()) {
            facetString.append(String.format(",[\"categories:%s\"]", searchFilters.modLoader.toLowerCase()));
        }
        facetString.append("]");
        params.put("facets", facetString.toString());
        params.put("query", searchFilters.name);
        params.put("limit", 50);
        params.put("index", "relevance");
        if(modrinthSearchResult != null)
            params.put("offset", modrinthSearchResult.previousOffset);

        JsonObject response = mApiHandler.get("search", params, JsonObject.class);
        if(response == null) return null;
        JsonArray responseHits = response.getAsJsonArray("hits");
        if(responseHits == null) return null;

        ModItem[] items = new ModItem[responseHits.size()];
        for(int i=0; i<responseHits.size(); ++i){
            JsonObject hit = responseHits.get(i).getAsJsonObject();
            items[i] = new ModItem(
                    Constants.SOURCE_MODRINTH,
                    hit.get("project_type").getAsString().equals("modpack"),
                    hit.get("project_id").getAsString(),
                    hit.get("title").getAsString(),
                    hit.get("description").getAsString(),
                    hit.get("icon_url").getAsString()
            );
        }
        if(modrinthSearchResult == null) modrinthSearchResult = new ModrinthSearchResult();
        modrinthSearchResult.previousOffset += responseHits.size();
        modrinthSearchResult.results = items;
        modrinthSearchResult.totalResultCount = response.get("total_hits").getAsInt();
        return modrinthSearchResult;
    }

    @Override
    public ModDetail getModDetails(ModItem item) {
        String[] screenshotUrls = null;
        ModItem detailItem = item;
        try {
            JsonObject project = mApiHandler.get(String.format("project/%s", item.id), JsonObject.class);
            if (project != null) {
                String title = project.has("title") && !project.get("title").isJsonNull()
                        ? project.get("title").getAsString() : item.title;
                String description = project.has("description") && !project.get("description").isJsonNull()
                        ? project.get("description").getAsString() : item.description;
                String iconUrl = project.has("icon_url") && !project.get("icon_url").isJsonNull()
                        ? project.get("icon_url").getAsString() : item.imageUrl;
                detailItem = new ModItem(item.apiSource, item.isModpack, item.id, title, description, iconUrl);
            }
            if (project != null && project.has("gallery")) {
                JsonArray gallery = project.getAsJsonArray("gallery");
                screenshotUrls = new String[gallery.size()];
                for (int i = 0; i < gallery.size(); i++) {
                    JsonObject galleryItem = gallery.get(i).getAsJsonObject();
                    screenshotUrls[i] = galleryItem.get("url").getAsString();
                }
            }
        } catch (Exception e) {
            Log.e("ModrinthApi", "Error fetching project gallery", e);
        }

        JsonArray response = mApiHandler.get(String.format("project/%s/version", item.id), JsonArray.class);
        if(response == null) return null;
        System.out.println(response);
        String[] names = new String[response.size()];
        String[] mcNames = new String[response.size()];
        String[] loaders = new String[response.size()];
        String[] urls = new String[response.size()];
        String[] hashes = new String[response.size()];
        String[] dependencies = new String[response.size()];

        for (int i=0; i<response.size(); ++i) {
            JsonObject version = response.get(i).getAsJsonObject();
            names[i] = version.get("name").getAsString();
            mcNames[i] = joinStringArray(version.get("game_versions").getAsJsonArray());
            loaders[i] = joinStringArray(version.get("loaders").getAsJsonArray());
            urls[i] = version.get("files").getAsJsonArray().get(0).getAsJsonObject().get("url").getAsString();
            dependencies[i] = joinRequiredModrinthDependencies(version.getAsJsonArray("dependencies"));
            // Assume there may not be hashes, in case the API changes
            JsonObject hashesMap = version.getAsJsonArray("files").get(0).getAsJsonObject()
                    .get("hashes").getAsJsonObject();
            if(hashesMap == null || hashesMap.get("sha1") == null){
                hashes[i] = null;
                continue;
            }

            hashes[i] = hashesMap.get("sha1").getAsString();
        }

        ModDetail detail = new ModDetail(detailItem, names, mcNames, loaders, urls, hashes, dependencies);
        detail.screenshotUrls = screenshotUrls;
        return detail;
    }

    private static String joinStringArray(JsonArray array) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < array.size(); i++) {
            if (i > 0) builder.append(",");
            builder.append(array.get(i).getAsString());
        }
        return builder.toString();
    }

    private static String joinRequiredModrinthDependencies(JsonArray array) {
        if (array == null) return "";
        StringBuilder builder = new StringBuilder();
        for (JsonElement element : array) {
            JsonObject dependency = element.getAsJsonObject();
            JsonElement dependencyType = dependency.get("dependency_type");
            if (dependencyType == null || dependencyType.isJsonNull() ||
                    !"required".equalsIgnoreCase(dependencyType.getAsString())) {
                continue;
            }
            JsonElement projectId = dependency.get("project_id");
            String dependencyReference = null;
            if (projectId != null && !projectId.isJsonNull()) {
                dependencyReference = projectId.getAsString();
            } else {
                JsonElement fileName = dependency.get("file_name");
                if (fileName != null && !fileName.isJsonNull()) {
                    dependencyReference = "external:" + fileName.getAsString();
                }
            }
            if (dependencyReference == null || dependencyReference.isEmpty()) continue;
            if (builder.length() > 0) builder.append(",");
            builder.append(dependencyReference);
        }
        return builder.toString();
    }

    @Override
    public ModLoader installMod(ModDetail modDetail, int selectedVersion) throws IOException{
        //TODO considering only modpacks for now
        return ModpackInstaller.installModpack(modDetail, selectedVersion, this::installMrpack);
    }

    private static ModLoader createInfo(ModrinthIndex modrinthIndex) {
        if(modrinthIndex == null) return null;
        Map<String, String> dependencies = modrinthIndex.dependencies;
        String mcVersion = dependencies.get("minecraft");
        if(mcVersion == null) return null;
        String modLoaderVersion;
        if((modLoaderVersion = dependencies.get("forge")) != null) {
            return new ModLoader(ModLoader.MOD_LOADER_FORGE, modLoaderVersion, mcVersion);
        }
        if((modLoaderVersion = dependencies.get("neoforge")) != null) {
            return new ModLoader(ModLoader.MOD_LOADER_NEOFORGE, modLoaderVersion, mcVersion);
        }
        if((modLoaderVersion = dependencies.get("fabric-loader")) != null) {
            return new ModLoader(ModLoader.MOD_LOADER_FABRIC, modLoaderVersion, mcVersion);
        }
        if((modLoaderVersion = dependencies.get("quilt-loader")) != null) {
            return new ModLoader(ModLoader.MOD_LOADER_QUILT, modLoaderVersion, mcVersion);
        }
        return null;
    }

    private ModLoader installMrpack(File mrpackFile, File instanceDestination) throws IOException {
        try (ZipFile modpackZipFile = new ZipFile(mrpackFile)){
            ModrinthIndex modrinthIndex = Tools.GLOBAL_GSON.fromJson(
                    Tools.read(ZipUtils.getEntryStream(modpackZipFile, "modrinth.index.json")),
                    ModrinthIndex.class);
            
            ModDownloader modDownloader = new ModDownloader(instanceDestination);
            for(ModrinthIndex.ModrinthIndexFile indexFile : modrinthIndex.files) {
                modDownloader.submitDownload(indexFile.fileSize, indexFile.path, indexFile.hashes.sha1, indexFile.downloads);
            }
            modDownloader.awaitFinish(new DownloaderProgressWrapper(R.string.modpack_download_downloading_mods, ProgressLayout.INSTALL_MODPACK));
            ProgressLayout.setProgress(ProgressLayout.INSTALL_MODPACK, 0, R.string.modpack_download_applying_overrides, 1, 2);
            ZipUtils.zipExtract(modpackZipFile, "overrides/", instanceDestination);
            ProgressLayout.setProgress(ProgressLayout.INSTALL_MODPACK, 50, R.string.modpack_download_applying_overrides, 2, 2);
            ZipUtils.zipExtract(modpackZipFile, "client-overrides/", instanceDestination);
            return createInfo(modrinthIndex);
        }
    }

    class ModrinthSearchResult extends SearchResult {
        int previousOffset;
    }
}
