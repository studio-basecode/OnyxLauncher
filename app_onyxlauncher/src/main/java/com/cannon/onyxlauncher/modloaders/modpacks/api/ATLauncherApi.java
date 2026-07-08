package com.cannon.onyxlauncher.modloaders.modpacks.api;

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
import com.cannon.onyxlauncher.utils.DownloadUtils;
import com.cannon.onyxlauncher.utils.FileUtils;
import com.cannon.onyxlauncher.utils.ZipUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.zip.ZipFile;

public class ATLauncherApi implements ModpackApi {
    private static final String ATL_CDN = "https://download.nodecdn.net/containers/atl";
    private static final String DESCRIPTOR_PREFIX = "atlauncher|";
    private static JsonArray sCachedPacks = null;
    private final Map<String, String> mHeaders;
    private final ApiHandler mCurseforgeApiHandler;

    public ATLauncherApi() {
        this(null);
    }

    public ATLauncherApi(String curseforgeApiKey) {
        mHeaders = new HashMap<>();
        mHeaders.put("User-Agent", "OnyxLauncher/1.0.0 (Android)");
        if (curseforgeApiKey != null && !curseforgeApiKey.trim().isEmpty()) {
            mCurseforgeApiHandler = CurseforgeApi.createApiHandler(curseforgeApiKey);
        } else {
            mCurseforgeApiHandler = null;
        }
    }

    private synchronized JsonArray getPublicPacks() {
        if (sCachedPacks != null) {
            return sCachedPacks;
        }
        try {
            String rawJson = ApiHandler.getRaw(mHeaders, "https://api.atlauncher.com/v1/packs/full/public");
            if (rawJson != null) {
                JsonElement parsed = new Gson().fromJson(rawJson, JsonElement.class);
                if (parsed != null && parsed.isJsonObject()) {
                    JsonObject response = parsed.getAsJsonObject();
                    if (response.has("data") && response.get("data").isJsonArray()) {
                        if (!response.has("error") || !response.get("error").getAsBoolean()) {
                            sCachedPacks = response.getAsJsonArray("data");
                        }
                    }
                } else if (parsed != null && parsed.isJsonArray()) {
                    sCachedPacks = parsed.getAsJsonArray();
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
            String safeName = getString(pack, "safeName");
            String name = getString(pack, "name");
            String displayName = getString(pack, "displayName");
            String title = ModpackUrlUtils.firstNonBlank(displayName, name, safeName, String.valueOf(getInt(pack, "id", 0)));
            String description = getString(pack, "description");

            if (query.isEmpty() || 
                title.toLowerCase().contains(query) ||
                safeName.toLowerCase().contains(query) ||
                name.toLowerCase().contains(query) || 
                description.toLowerCase().contains(query)) {
                
                String iconUrl = ModpackUrlUtils.normalizeUrl(ModpackUrlUtils.firstNonBlank(
                        getString(pack, "icon"),
                        getString(pack, "iconUrl"),
                        getString(pack, "iconURL")
                ));
                
                ModItem modItem = new ModItem(
                        Constants.SOURCE_ATLAUNCHER,
                        true,
                        ModpackUrlUtils.firstNonBlank(safeName, name),
                        title,
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
            String safeName = getString(pack, "safeName");
            String name = getString(pack, "name");
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
                String versionVal = getString(v, "version");
                String mcVal = ModpackUrlUtils.firstNonBlank(getString(v, "minecraft"), getString(v, "minecraftVersion"));
                String packSafeName = ModpackUrlUtils.firstNonBlank(getString(foundPack, "safeName"), item.id);
                
                versionNames[i] = "Version " + versionVal;
                mcVersionNames[i] = mcVal;
                versionLoaders[i] = "unknown";
                versionUrls[i] = buildVersionDescriptor(packSafeName, versionVal);
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
        String descriptor = modDetail.versionUrls[selectedVersion];
        String safeName;
        String version;
        if (descriptor != null && descriptor.startsWith(DESCRIPTOR_PREFIX)) {
            String[] parts = descriptor.split("\\|", 3);
            if (parts.length != 3) {
                throw new IOException("Invalid ATLauncher version descriptor");
            }
            safeName = parts[1];
            version = parts[2];
        } else {
            safeName = modDetail.id;
            version = parseVersionName(modDetail.versionNames[selectedVersion]);
        }

        if (safeName == null || safeName.trim().isEmpty() || version == null || version.trim().isEmpty()) {
            throw new IOException("ATLauncher pack version is incomplete");
        }

        String versionJsonUrl = getVersionJsonUrl(safeName, version);
        JsonObject packVersion = ApiHandler.getFullUrl(mHeaders, versionJsonUrl, JsonObject.class);
        if (packVersion == null) {
            throw new IOException("ATLauncher did not return version metadata for " + safeName + " " + version);
        }

        String mcVersion = ModpackUrlUtils.firstNonBlank(getString(packVersion, "minecraft"), modDetail.mcVersionNames[selectedVersion]);
        if (mcVersion.isEmpty()) {
            throw new IOException("ATLauncher pack does not declare a Minecraft version");
        }

        ModLoader modLoaderInfo = detectModLoader(packVersion, mcVersion);
        String modpackName = ModpackInstaller.uniqueModpackFileName(
                modDetail.title,
                modDetail.versionNames[selectedVersion],
                Integer.toHexString((safeName + "|" + version).hashCode())
        );
        File instanceDestination = new File(Tools.DIR_GAME_HOME, "custom_instances/" + modpackName);
        File cacheDir = new File(Tools.DIR_CACHE, "atlauncher/" + modpackName);
        FileUtils.ensureDirectory(instanceDestination);
        FileUtils.ensureDirectory(cacheDir);

        downloadAndExtractConfigs(packVersion, safeName, version, instanceDestination, cacheDir);
        downloadAndInstallFiles(packVersion, mcVersion, instanceDestination, cacheDir);
        installLegacyForgeVersion(packVersion, modLoaderInfo, mcVersion, instanceDestination);
        deleteRecursively(cacheDir);

        return ModpackInstaller.createInstalledProfile(modDetail, selectedVersion, modLoaderInfo, modpackName);
    }

    private void downloadAndExtractConfigs(JsonObject packVersion, String safeName, String version,
                                           File instanceDestination, File cacheDir) throws IOException {
        if (getBoolean(packVersion, "noConfigs", false)) {
            return;
        }
        JsonObject configs = getObject(packVersion, "configs");
        long configSize = configs == null ? -1 : getLong(configs, "filesize", -1);
        String configSha1 = configs == null ? "" : getString(configs, "sha1");
        boolean hasConfigMetadata = configSize > 0 || !configSha1.isEmpty();
        String configsUrl = ATL_CDN + "/packs/" + safeName + "/versions/" + version + "/Configs.zip";
        File configsZip = new File(cacheDir, "Configs.zip");

        try {
            ProgressLayout.setProgress(ProgressLayout.INSTALL_MODPACK, 0,
                    R.string.modpack_download_downloading_metadata, 1, 2);
            DownloadUtils.ensureSha1(configsZip, configSha1.isEmpty() ? null : configSha1, (Callable<Void>) () -> {
                DownloadUtils.downloadFileMonitored(configsUrl, configsZip, new byte[8192],
                        new DownloaderProgressWrapper(R.string.modpack_download_downloading_metadata,
                                ProgressLayout.INSTALL_MODPACK));
                return null;
            });
        } catch (IOException e) {
            if (hasConfigMetadata) {
                throw new IOException("Failed to download ATLauncher configs", e);
            }
            Log.w("ATLauncherApi", "No configs archive available for " + safeName + " " + version, e);
            return;
        }

        if (!configsZip.exists() || configsZip.length() == 0L) {
            return;
        }

        ProgressLayout.setProgress(ProgressLayout.INSTALL_MODPACK, 50,
                R.string.modpack_download_applying_overrides, 2, 2);
        try (ZipFile zipFile = new ZipFile(configsZip)) {
            ZipUtils.zipExtract(zipFile, "", instanceDestination);
        }
    }

    private void downloadAndInstallFiles(JsonObject packVersion, String mcVersion,
                                         File instanceDestination, File cacheDir) throws IOException {
        JsonArray mods = packVersion.has("mods") && packVersion.get("mods").isJsonArray()
                ? packVersion.getAsJsonArray("mods") : new JsonArray();
        ModDownloader regularDownloader = new ModDownloader(instanceDestination);
        ModDownloader specialDownloader = new ModDownloader(cacheDir);
        List<SpecialFile> specialFiles = new ArrayList<>();

        for (JsonElement element : mods) {
            if (!element.isJsonObject()) continue;
            JsonObject mod = element.getAsJsonObject();
            if (!shouldInstallOnClient(mod)) continue;

            String downloadUrl = resolveDownloadUrl(mod);
            boolean optional = getBoolean(mod, "optional", false);
            if (!ModpackUrlUtils.isHttpUrl(downloadUrl)) {
                if (optional) {
                    Log.w("ATLauncherApi", "Skipping optional ATLauncher mod without direct URL: " + getString(mod, "name"));
                    continue;
                }
                throw new IOException("ATLauncher mod has no direct client download URL: " + getString(mod, "name"));
            }

            String fileName = getModFileName(mod, downloadUrl);
            String sha1 = emptyToNull(getString(mod, "sha1"));
            int size = (int) Math.max(0, getLong(mod, "filesize", 0));
            String type = getString(mod, "type").toLowerCase(Locale.ROOT);

            if (isSpecialInstallType(type)) {
                specialDownloader.submitDownload(size, fileName, sha1, downloadUrl);
                specialFiles.add(new SpecialFile(mod, fileName, type));
            } else {
                String relativePath = getInstallRelativePath(mod, fileName, type, mcVersion);
                regularDownloader.submitDownload(size, relativePath, sha1, downloadUrl);
            }
        }

        regularDownloader.awaitFinish(new DownloaderProgressWrapper(
                R.string.modpack_download_downloading_mods, ProgressLayout.INSTALL_MODPACK));
        if (!specialFiles.isEmpty()) {
            specialDownloader.awaitFinish(new DownloaderProgressWrapper(
                    R.string.modpack_download_downloading_mods, ProgressLayout.INSTALL_MODPACK));
            int total = specialFiles.size();
            for (int i = 0; i < total; i++) {
                ProgressLayout.setProgress(ProgressLayout.INSTALL_MODPACK,
                        (int) ((i / (float) total) * 100f),
                        R.string.modpack_download_applying_overrides, i + 1, total);
                installSpecialFile(specialFiles.get(i), cacheDir, instanceDestination);
            }
        }
    }

    private void installLegacyForgeVersion(JsonObject packVersion, ModLoader modLoaderInfo, String mcVersion,
                                           File instanceDestination) throws IOException {
        if (!isLegacyForgeLoader(modLoaderInfo)) return;

        downloadLegacyLibraries(packVersion);
        installLegacyForgeUniversal(modLoaderInfo, instanceDestination);
        writeLegacyForgeVersionJson(packVersion, modLoaderInfo, mcVersion);
    }

    private boolean isLegacyForgeLoader(ModLoader modLoaderInfo) {
        return modLoaderInfo != null
                && modLoaderInfo.modLoaderType == ModLoader.MOD_LOADER_FORGE
                && modLoaderInfo.minecraftVersion != null
                && (modLoaderInfo.minecraftVersion.startsWith("1.6.")
                || modLoaderInfo.minecraftVersion.startsWith("1.7."));
    }

    private void downloadLegacyLibraries(JsonObject packVersion) throws IOException {
        JsonArray libraries = packVersion.has("libraries") && packVersion.get("libraries").isJsonArray()
                ? packVersion.getAsJsonArray("libraries") : new JsonArray();
        ModDownloader libraryDownloader = new ModDownloader(new File(Tools.DIR_HOME_LIBRARY));
        boolean hasLibraries = false;
        for (JsonElement element : libraries) {
            if (!element.isJsonObject()) continue;
            JsonObject library = element.getAsJsonObject();
            String serverPath = cleanRelativePath(getString(library, "server"));
            if (serverPath.isEmpty()) continue;
            String downloadUrl = resolveDownloadUrl(library);
            if (!ModpackUrlUtils.isHttpUrl(downloadUrl)) continue;
            int size = (int) Math.max(0, getLong(library, "filesize", 0));
            libraryDownloader.submitDownload(size, serverPath, null, downloadUrl);
            hasLibraries = true;
        }
        if (hasLibraries) {
            libraryDownloader.awaitFinish(new DownloaderProgressWrapper(
                    R.string.modpack_download_downloading_mods, ProgressLayout.INSTALL_MODPACK));
        }
    }

    private void installLegacyForgeUniversal(ModLoader modLoaderInfo, File instanceDestination) throws IOException {
        File jarmodsDir = new File(instanceDestination, "jarmods");
        String expected = "forge-" + modLoaderInfo.minecraftVersion + "-" + modLoaderInfo.modLoaderVersion;
        File forgeUniversal = null;
        File[] files = jarmodsDir.listFiles();
        if (files != null) {
            for (File file : files) {
                String name = file.getName().toLowerCase(Locale.ROOT);
                if (name.startsWith(expected.toLowerCase(Locale.ROOT)) && name.endsWith(".jar")) {
                    forgeUniversal = file;
                    break;
                }
            }
        }
        if (forgeUniversal == null || !forgeUniversal.isFile()) {
            throw new IOException("Legacy Forge universal jar was not installed for " + modLoaderInfo.getVersionId());
        }

        File forgeLibrary = new File(Tools.DIR_HOME_LIBRARY,
                "net/minecraftforge/minecraftforge/" + modLoaderInfo.modLoaderVersion
                        + "/minecraftforge-" + modLoaderInfo.modLoaderVersion + ".jar");
        copyFile(forgeUniversal, forgeLibrary);
    }

    private void writeLegacyForgeVersionJson(JsonObject packVersion, ModLoader modLoaderInfo, String mcVersion) throws IOException {
        String versionId = modLoaderInfo.getVersionId();
        File versionDir = new File(Tools.DIR_HOME_VERSION, versionId);
        FileUtils.ensureDirectory(versionDir);

        JsonObject versionJson = new JsonObject();
        versionJson.addProperty("id", versionId);
        versionJson.addProperty("inheritsFrom", mcVersion);
        versionJson.addProperty("type", "release");
        versionJson.addProperty("mainClass", legacyMainClass(packVersion));
        versionJson.addProperty("minecraftArguments", legacyMinecraftArguments(packVersion, versionId));

        JsonArray libraries = new JsonArray();
        JsonObject forgeLibrary = new JsonObject();
        forgeLibrary.addProperty("name", "net.minecraftforge:minecraftforge:" + modLoaderInfo.modLoaderVersion);
        libraries.add(forgeLibrary);

        JsonArray atLibraries = packVersion.has("libraries") && packVersion.get("libraries").isJsonArray()
                ? packVersion.getAsJsonArray("libraries") : new JsonArray();
        for (JsonElement element : atLibraries) {
            if (!element.isJsonObject()) continue;
            String coordinate = libraryCoordinateFromServerPath(getString(element.getAsJsonObject(), "server"));
            if (coordinate.isEmpty()) continue;
            JsonObject library = new JsonObject();
            library.addProperty("name", coordinate);
            libraries.add(library);
        }
        versionJson.add("libraries", libraries);

        File versionFile = new File(versionDir, versionId + ".json");
        try (FileOutputStream output = new FileOutputStream(versionFile)) {
            output.write(new Gson().toJson(versionJson).getBytes("UTF-8"));
        }
    }

    private String legacyMainClass(JsonObject packVersion) {
        JsonObject mainClass = getObject(packVersion, "mainClass");
        return ModpackUrlUtils.firstNonBlank(
                getString(mainClass, "mainClass"),
                getString(packVersion, "mainClass"),
                "net.minecraft.launchwrapper.Launch"
        );
    }

    private String legacyMinecraftArguments(JsonObject packVersion, String versionId) {
        String extraArgs = getString(getObject(packVersion, "extraArguments"), "arguments")
                .replace("--tweakClass=", "--tweakClass ");
        if (extraArgs.trim().isEmpty()) {
            extraArgs = "--tweakClass cpw.mods.fml.common.launcher.FMLTweaker";
        }
        return "--username ${auth_player_name} --session ${auth_session} --version " + versionId
                + " --gameDir ${game_directory} --assetsDir ${game_assets} " + extraArgs.trim();
    }

    private String libraryCoordinateFromServerPath(String serverPath) {
        String path = cleanRelativePath(serverPath);
        if (!path.endsWith(".jar")) return "";
        String[] parts = path.split("/");
        if (parts.length < 4) return "";
        String version = parts[parts.length - 2];
        String artifact = parts[parts.length - 3];
        StringBuilder group = new StringBuilder();
        for (int i = 0; i < parts.length - 3; i++) {
            if (i > 0) group.append('.');
            group.append(parts[i]);
        }
        if (group.length() == 0 || artifact.isEmpty() || version.isEmpty()) return "";
        return group + ":" + artifact + ":" + version;
    }

    private ModLoader detectModLoader(JsonObject packVersion, String mcVersion) throws IOException {
        JsonObject loader = getObject(packVersion, "loader");
        if (loader == null) {
            ModLoader legacyLoader = detectLegacyModLoader(packVersion, mcVersion);
            if (legacyLoader != null) return legacyLoader;
            throw new IOException("ATLauncher pack does not declare a supported mod loader");
        }

        String className = getString(loader, "className").toLowerCase(Locale.ROOT);
        JsonObject metadata = getObject(loader, "metadata");
        String version = metadata == null ? "" : ModpackUrlUtils.firstNonBlank(
                getString(metadata, "rawVersion"),
                getString(metadata, "version"),
                getString(metadata, "loader")
        );
        version = stripMinecraftPrefix(version, mcVersion);

        if (className.contains("neoforge")) {
            return new ModLoader(ModLoader.MOD_LOADER_NEOFORGE, version, mcVersion);
        }
        if (className.contains("forge")) {
            return new ModLoader(ModLoader.MOD_LOADER_FORGE, version, mcVersion);
        }
        if (className.contains("quilt")) {
            return new ModLoader(ModLoader.MOD_LOADER_QUILT, version, mcVersion);
        }
        if (className.contains("fabric")) {
            return new ModLoader(ModLoader.MOD_LOADER_FABRIC, version, mcVersion);
        }
        throw new IOException("Unsupported ATLauncher loader: " + getString(loader, "className"));
    }

    private ModLoader detectLegacyModLoader(JsonObject packVersion, String mcVersion) {
        String args = getString(getObject(packVersion, "extraArguments"), "arguments").toLowerCase(Locale.ROOT);
        String forgeVersion = findLegacyForgeVersion(packVersion);
        if (!forgeVersion.isEmpty() || args.contains("fml") || args.contains("forge")) {
            return new ModLoader(ModLoader.MOD_LOADER_FORGE, stripMinecraftPrefix(forgeVersion, mcVersion), mcVersion);
        }
        return null;
    }

    private String findLegacyForgeVersion(JsonObject packVersion) {
        JsonArray mods = packVersion.has("mods") && packVersion.get("mods").isJsonArray()
                ? packVersion.getAsJsonArray("mods") : new JsonArray();
        for (JsonElement element : mods) {
            if (!element.isJsonObject()) continue;
            JsonObject mod = element.getAsJsonObject();
            String name = getString(mod, "name").toLowerCase(Locale.ROOT);
            String type = getString(mod, "type").toLowerCase(Locale.ROOT);
            String file = getString(mod, "file").toLowerCase(Locale.ROOT);
            if (name.contains("minecraftforge") || "forge".equals(type) || file.startsWith("forge-")) {
                return ModpackUrlUtils.firstNonBlank(
                        getString(mod, "version"),
                        forgeVersionFromFile(getString(mod, "file"))
                );
            }
        }
        return "";
    }

    private String forgeVersionFromFile(String fileName) {
        if (fileName == null) return "";
        String name = fileName;
        int slash = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
        if (slash >= 0) name = name.substring(slash + 1);
        if (name.startsWith("forge-")) name = name.substring("forge-".length());
        if (name.endsWith("-universal.jar")) name = name.substring(0, name.length() - "-universal.jar".length());
        else if (name.endsWith(".jar")) name = name.substring(0, name.length() - ".jar".length());
        return name;
    }

    private String resolveDownloadUrl(JsonObject mod) {
        String downloadType = getString(mod, "download").toLowerCase(Locale.ROOT);
        String url = normalizeAtLauncherUrl(getString(mod, "url"));

        if ("server".equals(downloadType)) {
            return ATL_CDN + "/" + trimLeadingSlash(url);
        }

        if ("browser".equals(downloadType)) {
            String curseforgeUrl = resolveCurseForgeDownloadUrl(mod);
            if (ModpackUrlUtils.isHttpUrl(curseforgeUrl)) {
                return curseforgeUrl;
            }
            return isProbablyDirectFileUrl(url) ? url : "";
        }

        return url;
    }

    private String resolveCurseForgeDownloadUrl(JsonObject mod) {
        if (mCurseforgeApiHandler == null) return "";
        int projectId = getIntAny(mod, -1, "curseForgeProjectId", "curseforge_project_id", "curse_id");
        int fileId = getIntAny(mod, -1, "curseForgeFileId", "curseforge_file_id", "curse_file_id");
        if (projectId <= 0 || fileId <= 0) return "";

        try {
            JsonObject response = mCurseforgeApiHandler.get("mods/" + projectId + "/files/" + fileId + "/download-url", JsonObject.class);
            if (response != null && response.has("data") && !response.get("data").isJsonNull()) {
                String direct = normalizeAtLauncherUrl(response.get("data").getAsString());
                if (ModpackUrlUtils.isHttpUrl(direct)) return direct;
            }

            JsonObject fallbackResponse = mCurseforgeApiHandler.get("mods/" + projectId + "/files/" + fileId, JsonObject.class);
            if (fallbackResponse != null && fallbackResponse.has("data") && fallbackResponse.get("data").isJsonObject()) {
                JsonObject data = fallbackResponse.getAsJsonObject("data");
                String fileName = getString(data, "fileName");
                int id = getInt(data, "id", fileId);
                if (!fileName.isEmpty()) {
                    return normalizeAtLauncherUrl("https://edge.forgecdn.net/files/" + (id / 1000) + "/" + (id % 1000) + "/" + fileName);
                }
            }
        } catch (Exception e) {
            Log.w("ATLauncherApi", "Failed to resolve CurseForge browser download", e);
        }
        return "";
    }

    private void installSpecialFile(SpecialFile specialFile, File cacheDir, File instanceDestination) throws IOException {
        File downloadedFile = new File(cacheDir, specialFile.fileName);
        JsonObject mod = specialFile.mod;
        String type = specialFile.type;

        if ("texturepackextract".equals(type)) {
            extractZip(downloadedFile, "", new File(instanceDestination, "texturepacks/extracted"));
        } else if ("resourcepackextract".equals(type)) {
            extractZip(downloadedFile, "", new File(instanceDestination, "resourcepacks/extracted"));
        } else if ("extract".equals(type)) {
            String extractFolder = cleanRelativePath(getString(mod, "extractFolder"));
            File destination = extractDestination(instanceDestination, getString(mod, "extractTo"));
            extractZip(downloadedFile, asZipPrefix(extractFolder), destination);
        } else if ("decomp".equals(type)) {
            File tempDir = new File(cacheDir, sanitizeFileName(getString(mod, "name")) + "_decomp");
            extractZip(downloadedFile, "", tempDir);
            File source = new File(tempDir, cleanRelativePath(getString(mod, "decompFile")));
            File destination = extractDestination(instanceDestination, getString(mod, "decompType"));
            if (source.isDirectory()) {
                copyDirectory(source, destination);
            } else if (source.isFile()) {
                copyFile(source, new File(destination, source.getName()));
            }
            deleteRecursively(tempDir);
        }
    }

    private static void extractZip(File zip, String prefix, File destination) throws IOException {
        FileUtils.ensureDirectory(destination);
        try (ZipFile zipFile = new ZipFile(zip)) {
            ZipUtils.zipExtract(zipFile, prefix, destination);
        }
    }

    private static File extractDestination(File instanceDestination, String type) {
        String normalized = type == null ? "" : type.toLowerCase(Locale.ROOT);
        if ("coremods".equals(normalized)) return new File(instanceDestination, "coremods");
        if ("mods".equals(normalized)) return new File(instanceDestination, "mods");
        if ("jar".equals(normalized)) return new File(instanceDestination, "jarmods");
        return instanceDestination;
    }

    private static boolean shouldInstallOnClient(JsonObject mod) {
        if (!getBoolean(mod, "client", true)) return false;
        if (!getBoolean(mod, "optional", false)) return true;
        return getBoolean(mod, "selected", false) || getBoolean(mod, "recommended", true);
    }

    private static boolean isSpecialInstallType(String type) {
        return "extract".equals(type) || "decomp".equals(type)
                || "texturepackextract".equals(type) || "resourcepackextract".equals(type);
    }

    private static String getInstallRelativePath(JsonObject mod, String fileName, String type, String mcVersion) {
        String customPath = cleanRelativePath(getString(mod, "path"));
        if (!customPath.isEmpty()) {
            return customPath + "/" + fileName;
        }
        switch (type) {
            case "jar":
            case "forge":
            case "mcpc":
                return "jarmods/" + fileName;
            case "texturepack":
                return "texturepacks/" + fileName;
            case "resourcepack":
                return "resourcepacks/" + fileName;
            case "datapack":
                return "datapacks/" + fileName;
            case "plugins":
                return "plugins/" + fileName;
            case "ic2lib":
                return "mods/ic2/" + fileName;
            case "denlib":
                return "mods/denlib/" + fileName;
            case "flan":
                return "Flan/" + fileName;
            case "coremods":
                return "coremods/" + fileName;
            case "shaderpack":
                return "shaderpacks/" + fileName;
            case "dependency":
            case "depandency":
                return "mods/" + mcVersion + "/" + fileName;
            case "mods":
            default:
                return "mods/" + fileName;
        }
    }

    private static String getModFileName(JsonObject mod, String downloadUrl) {
        String prefix = getString(mod, "filePrefix");
        String file = ModpackUrlUtils.firstNonBlank(getString(mod, "file"), fileNameFromUrl(downloadUrl), sanitizeFileName(getString(mod, "name")) + ".jar");
        return sanitizeFileName(prefix + file);
    }

    private static String fileNameFromUrl(String url) {
        if (url == null) return "";
        int queryIndex = url.indexOf('?');
        if (queryIndex >= 0) url = url.substring(0, queryIndex);
        int fragmentIndex = url.indexOf('#');
        if (fragmentIndex >= 0) url = url.substring(0, fragmentIndex);
        String fileName = FileUtils.getFileName(url);
        if (fileName == null) return "";
        return fileName;
    }

    private static String sanitizeFileName(String value) {
        if (value == null) return "";
        String clean = value.replace('\\', '_')
                .replace('/', '_')
                .replaceAll("[\\p{Cntrl}:*?\"<>|]+", "_")
                .replaceAll("_+", "_")
                .trim();
        return clean.isEmpty() ? "file" : clean;
    }

    private static String normalizeAtLauncherUrl(String url) {
        if (url == null) return "";
        return ModpackUrlUtils.normalizeUrl(url.replace("&amp;", "&").replace(" ", "%20"));
    }

    private static boolean isProbablyDirectFileUrl(String url) {
        String lower = url == null ? "" : url.toLowerCase(Locale.ROOT);
        int queryIndex = lower.indexOf('?');
        if (queryIndex >= 0) lower = lower.substring(0, queryIndex);
        return lower.endsWith(".jar") || lower.endsWith(".zip") || lower.endsWith(".litemod")
                || lower.endsWith(".disabled") || lower.endsWith(".rar");
    }

    private static String cleanRelativePath(String path) {
        if (path == null) return "";
        String normalized = path.replace('\\', '/').trim();
        while (normalized.startsWith("./")) normalized = normalized.substring(2);
        while (normalized.startsWith("/")) normalized = normalized.substring(1);
        normalized = normalized.replace("../", "").replace("..", "");
        while (normalized.endsWith("/")) normalized = normalized.substring(0, normalized.length() - 1);
        return normalized;
    }

    private static String asZipPrefix(String value) {
        String clean = cleanRelativePath(value);
        if (clean.isEmpty()) return "";
        return clean.endsWith("/") ? clean : clean + "/";
    }

    private static String trimLeadingSlash(String value) {
        String trimmed = value == null ? "" : value.trim();
        while (trimmed.startsWith("/")) trimmed = trimmed.substring(1);
        return trimmed;
    }

    private static String stripMinecraftPrefix(String version, String mcVersion) {
        if (version == null) return "";
        String prefix = mcVersion + "-";
        return version.startsWith(prefix) ? version.substring(prefix.length()) : version;
    }

    private static String emptyToNull(String value) {
        return value == null || value.trim().isEmpty() ? null : value.trim();
    }

    private static String buildVersionDescriptor(String safeName, String version) {
        return DESCRIPTOR_PREFIX + safeName + "|" + version;
    }

    private static String getVersionJsonUrl(String safeName, String version) {
        return ATL_CDN + "/packs/" + safeName + "/versions/" + version + "/Configs.json";
    }

    private static String parseVersionName(String displayVersion) {
        if (displayVersion == null) return "";
        String value = displayVersion.replaceFirst("^Version\\s+", "");
        int dashIndex = value.indexOf(" - ");
        return dashIndex >= 0 ? value.substring(0, dashIndex).trim() : value.trim();
    }

    private static JsonObject getObject(JsonObject object, String memberName) {
        if (object == null || !object.has(memberName) || !object.get(memberName).isJsonObject()) return null;
        return object.getAsJsonObject(memberName);
    }

    private static String getString(JsonObject object, String memberName) {
        if (object == null || !object.has(memberName) || object.get(memberName).isJsonNull()) return "";
        try {
            return object.get(memberName).getAsString();
        } catch (Exception e) {
            return "";
        }
    }

    private static int getInt(JsonObject object, String memberName, int fallback) {
        if (object == null || !object.has(memberName) || object.get(memberName).isJsonNull()) return fallback;
        try {
            return object.get(memberName).getAsInt();
        } catch (Exception e) {
            return fallback;
        }
    }

    private static int getIntAny(JsonObject object, int fallback, String... memberNames) {
        for (String memberName : memberNames) {
            int value = getInt(object, memberName, Integer.MIN_VALUE);
            if (value != Integer.MIN_VALUE) return value;
        }
        return fallback;
    }

    private static long getLong(JsonObject object, String memberName, long fallback) {
        if (object == null || !object.has(memberName) || object.get(memberName).isJsonNull()) return fallback;
        try {
            return object.get(memberName).getAsLong();
        } catch (Exception e) {
            return fallback;
        }
    }

    private static boolean getBoolean(JsonObject object, String memberName, boolean fallback) {
        if (object == null || !object.has(memberName) || object.get(memberName).isJsonNull()) return fallback;
        try {
            return object.get(memberName).getAsBoolean();
        } catch (Exception e) {
            return fallback;
        }
    }

    private static void copyFile(File source, File destination) throws IOException {
        FileUtils.ensureParentDirectory(destination);
        try (FileInputStream input = new FileInputStream(source);
             FileOutputStream output = new FileOutputStream(destination)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) != -1) {
                output.write(buffer, 0, read);
            }
        }
    }

    private static void copyDirectory(File source, File destination) throws IOException {
        if (!source.exists()) return;
        if (source.isFile()) {
            copyFile(source, new File(destination, source.getName()));
            return;
        }
        FileUtils.ensureDirectory(destination);
        File[] children = source.listFiles();
        if (children == null) return;
        for (File child : children) {
            File childDestination = new File(destination, child.getName());
            if (child.isDirectory()) {
                copyDirectory(child, childDestination);
            } else {
                copyFile(child, childDestination);
            }
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

    private static class SpecialFile {
        final JsonObject mod;
        final String fileName;
        final String type;

        SpecialFile(JsonObject mod, String fileName, String type) {
            this.mod = mod;
            this.fileName = fileName;
            this.type = type;
        }
    }
}
