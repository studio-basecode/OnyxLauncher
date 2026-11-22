package com.cannon.onyxlauncher.modloaders.modpacks.api;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.kdt.mcgui.ProgressLayout;

import com.cannon.onyxlauncher.R;
import com.cannon.onyxlauncher.Tools;
import com.cannon.onyxlauncher.modloaders.modpacks.models.Constants;
import com.cannon.onyxlauncher.modloaders.modpacks.models.CurseManifest;
import com.cannon.onyxlauncher.modloaders.modpacks.models.ModDetail;
import com.cannon.onyxlauncher.modloaders.modpacks.models.ModItem;
import com.cannon.onyxlauncher.modloaders.modpacks.models.SearchFilters;
import com.cannon.onyxlauncher.modloaders.modpacks.models.SearchResult;
import com.cannon.onyxlauncher.progresskeeper.ProgressKeeper;
import com.cannon.onyxlauncher.utils.FileUtils;
import com.cannon.onyxlauncher.utils.GsonJsonUtils;
import com.cannon.onyxlauncher.utils.ZipUtils;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.zip.ZipFile;

public class CurseforgeApi implements ModpackApi{
    private static final Pattern sMcVersionPattern = Pattern.compile("([0-9]+)\\.([0-9]+)\\.?([0-9]+)?");
    private static final int ALGO_SHA_1 = 1;
    // Stolen from
    // https://github.com/AnzhiZhang/CurseForgeModpackDownloader/blob/6cb3f428459f0cc8f444d16e54aea4cd1186fd7b/utils/requester.py#L93
    private static final int CURSEFORGE_MINECRAFT_GAME_ID = 432;
    private static final int CURSEFORGE_MODPACK_CLASS_ID = 4471;
    // https://api.curseforge.com/v1/categories?gameId=432 and search for "Mods" (case-sensitive)
    private static final int CURSEFORGE_MOD_CLASS_ID = 6;
    private static final int CURSEFORGE_SORT_RELEVANCY = 1;
    private static final int CURSEFORGE_PAGINATION_SIZE = 50;
    private static final int CURSEFORGE_PAGINATION_END_REACHED = -1;
    private static final int CURSEFORGE_PAGINATION_ERROR = -2;

    private final ApiHandler mApiHandler;
    public CurseforgeApi(String apiKey) {
        mApiHandler = new ApiHandler("https://api.curseforge.com/v1", apiKey);
    }

    @Override
    public SearchResult searchMod(SearchFilters searchFilters, SearchResult previousPageResult) {
        CurseforgeSearchResult curseforgeSearchResult = (CurseforgeSearchResult) previousPageResult;

        HashMap<String, Object> params = new HashMap<>();
        params.put("gameId", CURSEFORGE_MINECRAFT_GAME_ID);
        int classId = CURSEFORGE_MOD_CLASS_ID;
        if (searchFilters.projectType != null) {
            switch (searchFilters.projectType) {
                case "modpack":
                    classId = CURSEFORGE_MODPACK_CLASS_ID;
                    break;
                case "mod":
                    classId = CURSEFORGE_MOD_CLASS_ID;
                    break;
                case "resourcepack":
                    classId = 12;
                    break;
                case "shader":
                    classId = 6552;
                    break;
            }
        } else {
            classId = searchFilters.isModpack ? CURSEFORGE_MODPACK_CLASS_ID : CURSEFORGE_MOD_CLASS_ID;
        }
        params.put("classId", classId);
        if (searchFilters.name != null && !searchFilters.name.trim().isEmpty()) {
            params.put("searchFilter", searchFilters.name.trim());
        }
        params.put("sortField", CURSEFORGE_SORT_RELEVANCY);
        params.put("sortOrder", "desc");
        if(searchFilters.mcVersion != null && !searchFilters.mcVersion.isEmpty())
            params.put("gameVersion", searchFilters.mcVersion);
        if (searchFilters.modLoader != null) {
            int loaderType = 0;
            if ("forge".equalsIgnoreCase(searchFilters.modLoader)) loaderType = 1;
            else if ("fabric".equalsIgnoreCase(searchFilters.modLoader)) loaderType = 4;
            else if ("quilt".equalsIgnoreCase(searchFilters.modLoader)) loaderType = 5;
            else if ("neoforge".equalsIgnoreCase(searchFilters.modLoader)) loaderType = 6;
            
            if (loaderType > 0) {
                params.put("modLoaderType", loaderType);
            }
        }
        if(previousPageResult != null)
            params.put("index", curseforgeSearchResult.previousOffset);

        JsonObject response = mApiHandler.get("mods/search", params, JsonObject.class);
        if(response == null) return null;
        JsonArray dataArray = response.getAsJsonArray("data");
        if(dataArray == null) return null;
        JsonObject paginationInfo = response.getAsJsonObject("pagination");
        ArrayList<ModItem> modItemList = new ArrayList<>(dataArray.size());
        for(int i = 0; i < dataArray.size(); i++) {
            JsonObject dataElement = dataArray.get(i).getAsJsonObject();
            JsonElement allowModDistribution = dataElement.get("allowModDistribution");
            // Gson automatically casts null to false, which leans to issues
            // So, only check the distribution flag if it is non-null
            if(allowModDistribution != null && !allowModDistribution.isJsonNull() && !allowModDistribution.getAsBoolean()) {
                Log.i("CurseforgeApi", "Skipping modpack "+dataElement.get("name").getAsString() + " because curseforge sucks");
                continue;
            }
            String id = dataElement.has("id") ? dataElement.get("id").getAsString() : "";
            String name = dataElement.has("name") ? dataElement.get("name").getAsString() : "Unknown";
            String summary = dataElement.has("summary") ? dataElement.get("summary").getAsString() : "";
            String logoUrl = "";
            if (dataElement.has("logo") && !dataElement.get("logo").isJsonNull() && dataElement.get("logo").isJsonObject()) {
                JsonObject logoObj = dataElement.getAsJsonObject("logo");
                if (logoObj.has("thumbnailUrl") && !logoObj.get("thumbnailUrl").isJsonNull()) {
                    logoUrl = logoObj.get("thumbnailUrl").getAsString();
                }
            }
            ModItem modItem = new ModItem(Constants.SOURCE_CURSEFORGE,
                    searchFilters.isModpack,
                    id,
                    name,
                    summary,
                    logoUrl);
            modItemList.add(modItem);
        }
        if(curseforgeSearchResult == null) curseforgeSearchResult = new CurseforgeSearchResult();
        curseforgeSearchResult.results = modItemList.toArray(new ModItem[0]);
        curseforgeSearchResult.totalResultCount = paginationInfo.get("totalCount").getAsInt();
        curseforgeSearchResult.previousOffset += dataArray.size();
        return curseforgeSearchResult;

    }

    @Override
    public ModDetail getModDetails(ModItem item) {
        String[] screenshotUrls = null;
        ModItem detailItem = item;
        try {
            JsonObject modResponse = mApiHandler.get("mods/" + item.id, JsonObject.class);
            if (modResponse != null && modResponse.has("data")) {
                JsonObject data = modResponse.getAsJsonObject("data");
                String title = GsonJsonUtils.getStringSafe(data, "name");
                String description = GsonJsonUtils.getStringSafe(data, "summary");
                String imageUrl = item.imageUrl;
                JsonObject logo = GsonJsonUtils.getJsonObjectSafe(data, "logo");
                if (logo != null) {
                    String thumbnailUrl = GsonJsonUtils.getStringSafe(logo, "thumbnailUrl");
                    if (thumbnailUrl != null) imageUrl = thumbnailUrl;
                }
                detailItem = new ModItem(item.apiSource, item.isModpack, item.id,
                        title == null ? item.title : title,
                        description == null ? item.description : description,
                        imageUrl);
                if (data.has("screenshots")) {
                    JsonArray screenshots = data.getAsJsonArray("screenshots");
                    screenshotUrls = new String[screenshots.size()];
                    for (int i = 0; i < screenshots.size(); i++) {
                        JsonObject screenshotItem = screenshots.get(i).getAsJsonObject();
                        screenshotUrls[i] = screenshotItem.get("url").getAsString();
                    }
                }
            }
        } catch (Exception e) {
            Log.e("CurseforgeApi", "Error fetching project screenshots", e);
        }

        ArrayList<JsonObject> allModDetails = new ArrayList<>();
        int index = 0;
        while(index != CURSEFORGE_PAGINATION_END_REACHED &&
                index != CURSEFORGE_PAGINATION_ERROR) {
            index = getPaginatedDetails(allModDetails, index, item.id);
        }
        if(index == CURSEFORGE_PAGINATION_ERROR) return null;
        int length = allModDetails.size();
        String[] versionNames = new String[length];
        String[] mcVersionNames = new String[length];
        String[] versionLoaders = new String[length];
        String[] versionUrls = new String[length];
        String[] hashes = new String[length];
        String[] dependencies = new String[length];
        String[] releaseTypes = new String[length];
        String[] dates = new String[length];
        String[] versionIds = new String[length];
        for(int i = 0; i < allModDetails.size(); i++) {
            JsonObject modDetail = allModDetails.get(i);
            versionNames[i] = modDetail.get("displayName").getAsString();
            versionIds[i] = modDetail.has("id") && !modDetail.get("id").isJsonNull() ? modDetail.get("id").getAsString() : "";
            int relType = modDetail.has("releaseType") && !modDetail.get("releaseType").isJsonNull() ? modDetail.get("releaseType").getAsInt() : 1;
            releaseTypes[i] = relType == 2 ? "beta" : (relType == 3 ? "alpha" : "release");
            String fileDate = modDetail.has("fileDate") && !modDetail.get("fileDate").isJsonNull() ? modDetail.get("fileDate").getAsString() : "";
            dates[i] = fileDate.length() >= 10 ? fileDate.substring(0, 10) : fileDate;

            JsonElement downloadUrl = modDetail.get("downloadUrl");
            String resolvedDownloadUrl = null;
            if (downloadUrl != null && !downloadUrl.isJsonNull()) {
                resolvedDownloadUrl = downloadUrl.getAsString();
            }
            if (resolvedDownloadUrl == null || resolvedDownloadUrl.isEmpty()) {
                resolvedDownloadUrl = getDownloadUrl(Long.parseLong(item.id), modDetail.get("id").getAsLong());
            }
            versionUrls[i] = resolvedDownloadUrl == null ? "" : resolvedDownloadUrl;

            JsonArray gameVersions = modDetail.getAsJsonArray("gameVersions");
            StringBuilder mcVersions = new StringBuilder();
            StringBuilder loaders = new StringBuilder();
            for(JsonElement jsonElement : gameVersions) {
                String gameVersion = jsonElement.getAsString();
                if(sMcVersionPattern.matcher(gameVersion).matches()) {
                    if(mcVersions.length() > 0) mcVersions.append(",");
                    mcVersions.append(gameVersion);
                    continue;
                }
                String lowerGameVersion = gameVersion.toLowerCase(Locale.ROOT);
                if(lowerGameVersion.contains("fabric") || lowerGameVersion.contains("forge") ||
                        lowerGameVersion.contains("neoforge") || lowerGameVersion.contains("quilt")) {
                    if(loaders.length() > 0) loaders.append(",");
                    loaders.append(lowerGameVersion);
                }
            }
            mcVersionNames[i] = mcVersions.toString();
            versionLoaders[i] = loaders.toString();

            hashes[i] = getSha1FromModData(modDetail);
            dependencies[i] = joinRequiredCurseforgeDependencies(GsonJsonUtils.getJsonArraySafe(modDetail, "dependencies"));
        }
        ModDetail detail = new ModDetail(detailItem, versionNames, mcVersionNames, versionLoaders, versionUrls, hashes, dependencies);
        detail.versionReleaseTypes = releaseTypes;
        detail.versionDates = dates;
        detail.versionIds = versionIds;
        detail.screenshotUrls = screenshotUrls;
        return detail;
    }

    @Override
    public String getVersionChangelog(ModDetail detail, int versionIndex) {
        if (detail == null || detail.versionIds == null || versionIndex < 0 || versionIndex >= detail.versionIds.length) {
            return null;
        }
        String fileId = detail.versionIds[versionIndex];
        if (fileId == null || fileId.isEmpty()) return null;
        try {
            JsonObject resp = mApiHandler.get("mods/" + detail.id + "/files/" + fileId + "/changelog", JsonObject.class);
            if (resp != null && resp.has("data") && !resp.get("data").isJsonNull()) {
                return resp.get("data").getAsString();
            }
        } catch (Exception e) {
            Log.w("CurseforgeApi", "Failed to load changelog for " + detail.id + "/" + fileId, e);
        }
        return null;
    }

    @Override
    public ModLoader installMod(ModDetail modDetail, int selectedVersion) throws IOException{
        //TODO considering only modpacks for now
        return ModpackInstaller.installModpack(modDetail, selectedVersion, this::installCurseforgeZip);
    }

    @Override
    public ModLoader updateMod(ModDetail modDetail, int selectedVersion, File instanceDestination) throws IOException {
        return ModpackInstaller.updateModpack(modDetail, selectedVersion, instanceDestination, this::installCurseforgeZip);
    }


    private int getPaginatedDetails(ArrayList<JsonObject> objectList, int index, String modId) {
        HashMap<String, Object> params = new HashMap<>();
        params.put("index", index);
        params.put("pageSize", CURSEFORGE_PAGINATION_SIZE);

        JsonObject response = mApiHandler.get("mods/"+modId+"/files", params, JsonObject.class);
        JsonArray data = GsonJsonUtils.getJsonArraySafe(response, "data");
        if(data == null) return CURSEFORGE_PAGINATION_ERROR;

        for(int i = 0; i < data.size(); i++) {
            JsonObject fileInfo = data.get(i).getAsJsonObject();
            if(fileInfo.get("isServerPack").getAsBoolean()) continue;
            objectList.add(fileInfo);
        }
        if(data.size() < CURSEFORGE_PAGINATION_SIZE) {
            return CURSEFORGE_PAGINATION_END_REACHED; // we read the remainder! yay!
        }
        return index + data.size();
    }

    public ModLoader installCurseforgeZip(File zipFile, File instanceDestination) throws IOException {
        try (ZipFile modpackZipFile = new ZipFile(zipFile)){
            String manifestRaw = Tools.read(ZipUtils.getEntryStream(modpackZipFile, "manifest.json"));
            CurseManifest curseManifest = Tools.GLOBAL_GSON.fromJson(manifestRaw, CurseManifest.class);
            if(!verifyManifest(curseManifest)) {
                Log.i("CurseforgeApi","manifest verification failed");
                return null;
            }
            try {
                File savedManifest = new File(instanceDestination, "manifest.json");
                FileUtils.ensureParentDirectory(savedManifest);
                Tools.write(savedManifest.getAbsolutePath(), manifestRaw);
            } catch (Exception e) {
                Log.w("CurseforgeApi", "Could not persist manifest.json to instance dir", e);
            }

            downloadManifestFiles(curseManifest, instanceDestination, (c, m) ->
                    ProgressKeeper.submitProgress(ProgressLayout.INSTALL_MODPACK, (int) Math.max((float)c/m*100,0), R.string.modpack_download_downloading_mods_fc, c, m)
            );
            String overridesDir = "overrides";
            if(curseManifest.overrides != null) overridesDir = curseManifest.overrides;
            ZipUtils.zipExtract(modpackZipFile, overridesDir, instanceDestination);
            return createInfo(curseManifest.minecraft);
        }
    }

    public void ensureManifestFilesInstalled(File instanceDestination, @Nullable Tools.DownloaderFeedback feedback) throws IOException {
        File manifestFile = new File(instanceDestination, "manifest.json");
        if (!manifestFile.isFile()) return;
        String manifestRaw = Tools.read(manifestFile.getAbsolutePath());
        CurseManifest curseManifest = Tools.GLOBAL_GSON.fromJson(manifestRaw, CurseManifest.class);
        if (!verifyManifest(curseManifest) || curseManifest.files == null || curseManifest.files.length == 0) return;

        downloadManifestFiles(curseManifest, instanceDestination, feedback);
    }

    private void downloadManifestFiles(CurseManifest curseManifest, File instanceDestination, @Nullable Tools.DownloaderFeedback feedback) throws IOException {
        File modsDir = new File(instanceDestination, "mods");
        if (!modsDir.exists()) modsDir.mkdirs();

        // Batch-query all file IDs via POST /v1/mods/files in one request to avoid HTTP 429 rate-limits
        HashMap<Long, JsonObject> batchMap = new HashMap<>();
        try {
            JsonObject requestBody = new JsonObject();
            JsonArray fileIdsArray = new JsonArray();
            for (CurseManifest.CurseFile cf : curseManifest.files) {
                fileIdsArray.add(cf.fileID);
            }
            requestBody.add("fileIds", fileIdsArray);
            JsonObject batchResp = mApiHandler.post("mods/files", requestBody, JsonObject.class);
            JsonArray dataArr = GsonJsonUtils.getJsonArraySafe(batchResp, "data");
            if (dataArr != null) {
                for (JsonElement el : dataArr) {
                    JsonObject obj = GsonJsonUtils.getJsonObjectSafe(el);
                    if (obj != null && obj.has("id") && !obj.get("id").isJsonNull()) {
                        batchMap.put(obj.get("id").getAsLong(), obj);
                    }
                }
            }
            Log.i("CurseforgeApi", "Batch resolved " + batchMap.size() + "/" + curseManifest.files.length + " files via POST /v1/mods/files");
        } catch (Exception e) {
            Log.w("CurseforgeApi", "Batch POST /v1/mods/files failed, falling back to per-file queries", e);
        }

        // Check existing files in mods/ (including URL-encoded variants and .disabled_gl4es)
        File[] existingModFiles = modsDir.listFiles();
        java.util.HashSet<String> validExistingNames = new java.util.HashSet<>();
        if (existingModFiles != null) {
            for (File f : existingModFiles) {
                if (!f.isFile()) continue;
                String n = f.getName();
                if (n.endsWith(".disabled_gl4es")) {
                    String orig = n.substring(0, n.length() - ".disabled_gl4es".length());
                    validExistingNames.add(orig);
                    try {
                        validExistingNames.add(java.net.URLDecoder.decode(orig, "UTF-8"));
                    } catch (Exception ignored) {}
                } else if (com.cannon.onyxlauncher.utils.DownloadUtils.isValidZipArchive(f)) {
                    validExistingNames.add(n);
                    try {
                        validExistingNames.add(java.net.URLDecoder.decode(n, "UTF-8"));
                    } catch (Exception ignored) {}
                }
            }
        }

        ArrayList<CurseManifest.CurseFile> missingFiles = new ArrayList<>();
        for (CurseManifest.CurseFile cf : curseManifest.files) {
            JsonObject cached = batchMap.get(cf.fileID);
            if (cached != null) {
                String fName = GsonJsonUtils.getStringSafe(cached, "fileName");
                if (fName != null && validExistingNames.contains(fName)) {
                    continue;
                }
            }
            missingFiles.add(cf);
        }

        if (missingFiles.isEmpty()) {
            return;
        }
        Log.i("CurseforgeApi", "Downloading " + missingFiles.size() + " missing modpack files into " + modsDir.getAbsolutePath());

        ModDownloader modDownloader = new ModDownloader(modsDir, true);
        for (final CurseManifest.CurseFile curseFile : missingFiles) {
            final JsonObject preloaded = batchMap.get(curseFile.fileID);
            modDownloader.submitDownload(() -> {
                String url = null;
                String fileName = null;
                String sha1 = null;
                if (preloaded != null) {
                    fileName = GsonJsonUtils.getStringSafe(preloaded, "fileName");
                    url = GsonJsonUtils.getStringSafe(preloaded, "downloadUrl");
                    if ((url == null || url.isEmpty()) && fileName != null) {
                        long id = curseFile.fileID;
                        url = String.format(Locale.ROOT, "https://edge.forgecdn.net/files/%d/%d/%s", id / 1000, id % 1000, fileName);
                    }
                    sha1 = getSha1FromModData(preloaded);
                }
                if (url == null || url.isEmpty()) {
                    url = getDownloadUrl(curseFile.projectID, curseFile.fileID);
                }
                if (url == null && curseFile.required) {
                    throw new IOException("Failed to obtain download URL for " + curseFile.projectID + " " + curseFile.fileID);
                } else if (url == null) {
                    return null;
                }
                if (fileName == null || fileName.isEmpty()) {
                    fileName = FileUtils.getFileName(url);
                }
                if (sha1 == null && preloaded == null) {
                    sha1 = getDownloadSha1(curseFile.projectID, curseFile.fileID);
                }
                // Route .zip resourcepacks in manifest to ../resourcepacks/ so NeoForge doesn't treat them as mod jars
                String relativeTarget = fileName.toLowerCase(Locale.ROOT).endsWith(".zip")
                        ? "../resourcepacks/" + fileName
                        : fileName;
                return new ModDownloader.FileInfo(url, relativeTarget, sha1);
            });
        }
        modDownloader.awaitFinish((c, m) -> {
            if (feedback != null) {
                feedback.updateProgress(c, m);
            }
        });
    }

    private ModLoader createInfo(CurseManifest.CurseMinecraft minecraft) {
        CurseManifest.CurseModLoader primaryModLoader = null;
        for(CurseManifest.CurseModLoader modLoader : minecraft.modLoaders) {
            if(modLoader.primary) {
                primaryModLoader = modLoader;
                break;
            }
        }
        if(primaryModLoader == null) primaryModLoader = minecraft.modLoaders[0];
        String modLoaderId = primaryModLoader.id;
        int dashIndex = modLoaderId.indexOf('-');
        String modLoaderName = dashIndex != -1 ? modLoaderId.substring(0, dashIndex) : modLoaderId;
        String modLoaderVersion = dashIndex != -1 ? modLoaderId.substring(dashIndex + 1) : "recommended";
        Log.i("CurseforgeApi", modLoaderId + " " + modLoaderName + " " + modLoaderVersion);
        int modLoaderTypeInt;
        switch (modLoaderName.toLowerCase(Locale.ROOT)) {
            case "forge":
                modLoaderTypeInt = ModLoader.MOD_LOADER_FORGE;
                break;
            case "fabric":
                modLoaderTypeInt = ModLoader.MOD_LOADER_FABRIC;
                break;
            case "neoforge":
                modLoaderTypeInt = ModLoader.MOD_LOADER_NEOFORGE;
                break;
            case "quilt":
                modLoaderTypeInt = ModLoader.MOD_LOADER_QUILT;
                break;
            default:
                return null;
        }
        return new ModLoader(modLoaderTypeInt, modLoaderVersion, minecraft.version);
    }

    private String getDownloadUrl(long projectID, long fileID) {
        // First try the official api endpoint
        JsonObject response = mApiHandler.get("mods/"+projectID+"/files/"+fileID+"/download-url", JsonObject.class);
        if (response != null && !response.get("data").isJsonNull())
            return response.get("data").getAsString();

        // Otherwise, fallback to building an edge link
        JsonObject fallbackResponse = mApiHandler.get(String.format("mods/%s/files/%s", projectID, fileID), JsonObject.class);
        if (fallbackResponse != null && fallbackResponse.has("data") && !fallbackResponse.get("data").isJsonNull()){
            JsonObject modData = fallbackResponse.get("data").getAsJsonObject();
            int id = modData.get("id").getAsInt();
            return String.format(Locale.ROOT, "https://edge.forgecdn.net/files/%d/%d/%s", id / 1000, id % 1000, modData.get("fileName").getAsString());
        }

        return null;
    }

    private @Nullable String getDownloadSha1(long projectID, long fileID) {
        // Try the api endpoint, die in the other case
        JsonObject response = mApiHandler.get("mods/"+projectID+"/files/"+fileID, JsonObject.class);
        JsonObject data = GsonJsonUtils.getJsonObjectSafe(response, "data");
        if(data == null) return null;
        return getSha1FromModData(data);
    }

    private String getSha1FromModData(@NonNull JsonObject object) {
        JsonArray hashes = GsonJsonUtils.getJsonArraySafe(object, "hashes");
        if(hashes == null) return null;
        for (JsonElement jsonElement : hashes) {
            // The sha1 = 1; md5 = 2;
            JsonObject jsonObject = GsonJsonUtils.getJsonObjectSafe(jsonElement);
            if(GsonJsonUtils.getIntSafe(
                    jsonObject,
                    "algo",
                    -1) == ALGO_SHA_1) {
                return GsonJsonUtils.getStringSafe(jsonObject, "value");
            }
        }
        return null;
    }

    private String joinRequiredCurseforgeDependencies(JsonArray dependencyArray) {
        if (dependencyArray == null) return "";
        StringBuilder builder = new StringBuilder();
        for (JsonElement jsonElement : dependencyArray) {
            JsonObject dependency = GsonJsonUtils.getJsonObjectSafe(jsonElement);
            if (dependency == null) continue;
            // CurseForge relationType: 3 = RequiredDependency.
            if (GsonJsonUtils.getIntSafe(dependency, "relationType", -1) != 3) continue;
            String modId = GsonJsonUtils.getStringSafe(dependency, "modId");
            if (modId == null || modId.isEmpty()) continue;
            if (builder.length() > 0) builder.append(",");
            builder.append(modId);
        }
        return builder.toString();
    }

    private boolean verifyManifest(CurseManifest manifest) {
        if(!"minecraftModpack".equals(manifest.manifestType)) return false;
        if(manifest.manifestVersion != 1) return false;
        if(manifest.minecraft == null) return false;
        if(manifest.minecraft.version == null) return false;
        if(manifest.minecraft.modLoaders == null) return false;
        return manifest.minecraft.modLoaders.length >= 1;
    }

    static class CurseforgeSearchResult extends SearchResult {
        int previousOffset;
    }
}
