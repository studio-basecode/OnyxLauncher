package com.cannon.onyxlauncher.modloaders.modpacks.api;

import com.google.gson.JsonObject;
import com.kdt.mcgui.ProgressLayout;

import com.cannon.onyxlauncher.R;
import com.cannon.onyxlauncher.Tools;
import com.cannon.onyxlauncher.modloaders.modpacks.models.Constants;
import com.cannon.onyxlauncher.modloaders.modpacks.models.CurseManifest;
import com.cannon.onyxlauncher.modloaders.modpacks.models.ModDetail;
import com.cannon.onyxlauncher.modloaders.modpacks.models.ModItem;
import com.cannon.onyxlauncher.modloaders.modpacks.models.ModrinthIndex;
import com.cannon.onyxlauncher.progresskeeper.DownloaderProgressWrapper;
import com.cannon.onyxlauncher.progresskeeper.ProgressKeeper;
import com.cannon.onyxlauncher.utils.FileUtils;
import com.cannon.onyxlauncher.utils.ZipUtils;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public final class LocalModpackInstaller {
    private LocalModpackInstaller() {}

    public static ModLoader installLocalFile(File localFile, String displayName, String curseforgeApiKey) throws IOException {
        try (ZipFile zipFile = new ZipFile(localFile)) {
            ZipEntry modrinthEntry = zipFile.getEntry("modrinth.index.json");
            if (modrinthEntry != null) {
                return installMrpack(zipFile, displayName, localFile);
            }
            ZipEntry curseEntry = zipFile.getEntry("manifest.json");
            if (curseEntry != null) {
                return installCurseZip(zipFile, displayName, localFile, curseforgeApiKey);
            }
        }
        throw new IOException("Local modpack must be a Modrinth .mrpack or CurseForge export .zip");
    }

    private static ModLoader installMrpack(ZipFile zipFile, String displayName, File sourceFile) throws IOException {
        ModrinthIndex index = Tools.GLOBAL_GSON.fromJson(
                Tools.read(ZipUtils.getEntryStream(zipFile, "modrinth.index.json")),
                ModrinthIndex.class);
        if (index == null || index.dependencies == null) {
            throw new IOException("Invalid Modrinth modpack index");
        }

        ModLoader modLoader = createInfo(index);
        if (modLoader == null) {
            throw new IOException("Local Modrinth modpack does not declare a supported loader");
        }

        String title = ModpackUrlUtils.firstNonBlank(index.name, displayName, "Local Modrinth Pack");
        String versionName = ModpackUrlUtils.firstNonBlank(index.versionId, "Local import");
        ModDetail detail = localDetail(Constants.SOURCE_MODRINTH, title, versionName, modLoader.minecraftVersion, modLoader.getVersionId(), sourceFile);
        String modpackName = ModpackInstaller.uniqueModpackFileName(detail.title, detail.versionNames[0], String.valueOf(sourceFile.lastModified()));
        File instanceDestination = new File(Tools.DIR_GAME_HOME, "custom_instances/" + modpackName);
        FileUtils.ensureDirectory(instanceDestination);

        ModDownloader downloader = new ModDownloader(instanceDestination);
        if (index.files != null) {
            for (ModrinthIndex.ModrinthIndexFile indexFile : index.files) {
                if (indexFile == null || indexFile.path == null || indexFile.downloads == null || indexFile.downloads.length == 0) continue;
                if (indexFile.env != null && Constants.MODRINTH_FILE_ENV_UNSUPPORTED.equalsIgnoreCase(indexFile.env.client)) {
                    continue;
                }
                String sha1 = indexFile.hashes == null ? null : indexFile.hashes.sha1;
                downloader.submitDownload(indexFile.fileSize, indexFile.path, sha1, indexFile.downloads);
            }
        }
        downloader.awaitFinish(new DownloaderProgressWrapper(R.string.modpack_download_downloading_mods, ProgressLayout.INSTALL_MODPACK));
        ProgressLayout.setProgress(ProgressLayout.INSTALL_MODPACK, 0, R.string.modpack_download_applying_overrides, 1, 2);
        ZipUtils.zipExtract(zipFile, "overrides/", instanceDestination);
        ProgressLayout.setProgress(ProgressLayout.INSTALL_MODPACK, 50, R.string.modpack_download_applying_overrides, 2, 2);
        ZipUtils.zipExtract(zipFile, "client-overrides/", instanceDestination);

        return ModpackInstaller.createInstalledProfile(detail, 0, modLoader, modpackName);
    }

    private static ModLoader installCurseZip(ZipFile zipFile, String displayName, File sourceFile, String curseforgeApiKey) throws IOException {
        CurseManifest manifest = Tools.GLOBAL_GSON.fromJson(
                Tools.read(ZipUtils.getEntryStream(zipFile, "manifest.json")),
                CurseManifest.class);
        if (!verifyManifest(manifest)) {
            throw new IOException("Invalid CurseForge modpack manifest");
        }

        ModLoader modLoader = createInfo(manifest.minecraft);
        if (modLoader == null) {
            throw new IOException("Local CurseForge modpack does not declare a supported loader");
        }

        String title = ModpackUrlUtils.firstNonBlank(manifest.name, displayName, "Local CurseForge Pack");
        String versionName = ModpackUrlUtils.firstNonBlank(manifest.version, "Local import");
        ModDetail detail = localDetail(Constants.SOURCE_CURSEFORGE, title, versionName, manifest.minecraft.version, modLoader.getVersionId(), sourceFile);
        String modpackName = ModpackInstaller.uniqueModpackFileName(detail.title, detail.versionNames[0], String.valueOf(sourceFile.lastModified()));
        File instanceDestination = new File(Tools.DIR_GAME_HOME, "custom_instances/" + modpackName);
        FileUtils.ensureDirectory(instanceDestination);

        ApiHandler curseforge = CurseforgeApi.createApiHandler(curseforgeApiKey);
        ModDownloader downloader = new ModDownloader(new File(instanceDestination, "mods"), true);
        for (CurseManifest.CurseFile curseFile : manifest.files) {
            if (curseFile == null) continue;
            downloader.submitDownload(() -> {
                String url = getCurseforgeDownloadUrl(curseforge, curseFile.projectID, curseFile.fileID);
                if (url.isEmpty()) {
                    if (curseFile.required) {
                        throw new IOException("Failed to obtain download URL for " + curseFile.projectID + " " + curseFile.fileID);
                    }
                    return null;
                }
                return new ModDownloader.FileInfo(url, FileUtils.getFileName(url), null);
            });
        }
        downloader.awaitFinish((c, m) ->
                ProgressKeeper.submitProgress(ProgressLayout.INSTALL_MODPACK, (int) Math.max((float) c / m * 100, 0), R.string.modpack_download_downloading_mods_fc, c, m)
        );

        String overridesDir = manifest.overrides == null ? "overrides" : manifest.overrides;
        ZipUtils.zipExtract(zipFile, overridesDir, instanceDestination);

        return ModpackInstaller.createInstalledProfile(detail, 0, modLoader, modpackName);
    }

    private static ModDetail localDetail(int source, String title, String versionName, String mcVersion, String loader, File sourceFile) {
        ModItem item = new ModItem(source, true, "local_" + sourceFile.lastModified(), title,
                "Local modpack import", "");
        return new ModDetail(item,
                new String[]{versionName},
                new String[]{mcVersion},
                new String[]{loader},
                new String[]{sourceFile.toURI().toString()},
                new String[]{String.valueOf(sourceFile.length())},
                new String[]{""});
    }

    private static ModLoader createInfo(ModrinthIndex modrinthIndex) {
        Map<String, String> dependencies = modrinthIndex.dependencies;
        String mcVersion = dependencies.get("minecraft");
        if (mcVersion == null) return null;
        String modLoaderVersion;
        if ((modLoaderVersion = dependencies.get("forge")) != null) {
            return new ModLoader(ModLoader.MOD_LOADER_FORGE, modLoaderVersion, mcVersion);
        }
        if ((modLoaderVersion = dependencies.get("neoforge")) != null) {
            return new ModLoader(ModLoader.MOD_LOADER_NEOFORGE, modLoaderVersion, mcVersion);
        }
        if ((modLoaderVersion = dependencies.get("fabric-loader")) != null) {
            return new ModLoader(ModLoader.MOD_LOADER_FABRIC, modLoaderVersion, mcVersion);
        }
        if ((modLoaderVersion = dependencies.get("quilt-loader")) != null) {
            return new ModLoader(ModLoader.MOD_LOADER_QUILT, modLoaderVersion, mcVersion);
        }
        return null;
    }

    private static ModLoader createInfo(CurseManifest.CurseMinecraft minecraft) {
        CurseManifest.CurseModLoader primaryModLoader = null;
        for (CurseManifest.CurseModLoader modLoader : minecraft.modLoaders) {
            if (modLoader.primary) {
                primaryModLoader = modLoader;
                break;
            }
        }
        if (primaryModLoader == null) primaryModLoader = minecraft.modLoaders[0];
        String modLoaderId = primaryModLoader.id;
        int dashIndex = modLoaderId.indexOf('-');
        if (dashIndex <= 0 || dashIndex >= modLoaderId.length() - 1) return null;
        String modLoaderName = modLoaderId.substring(0, dashIndex);
        String modLoaderVersion = modLoaderId.substring(dashIndex + 1);
        int modLoaderTypeInt;
        switch (modLoaderName) {
            case "forge":
                modLoaderTypeInt = ModLoader.MOD_LOADER_FORGE;
                break;
            case "fabric":
                modLoaderTypeInt = ModLoader.MOD_LOADER_FABRIC;
                break;
            case "quilt":
                modLoaderTypeInt = ModLoader.MOD_LOADER_QUILT;
                break;
            case "neoforge":
                modLoaderTypeInt = ModLoader.MOD_LOADER_NEOFORGE;
                break;
            default:
                return null;
        }
        return new ModLoader(modLoaderTypeInt, modLoaderVersion, minecraft.version);
    }

    private static boolean verifyManifest(CurseManifest manifest) {
        return manifest != null &&
                "minecraftModpack".equals(manifest.manifestType) &&
                manifest.manifestVersion == 1 &&
                manifest.minecraft != null &&
                manifest.minecraft.version != null &&
                manifest.minecraft.modLoaders != null &&
                manifest.minecraft.modLoaders.length >= 1;
    }

    private static String getCurseforgeDownloadUrl(ApiHandler curseforge, long projectID, long fileID) {
        JsonObject response = curseforge.get("mods/" + projectID + "/files/" + fileID + "/download-url", JsonObject.class);
        if (response != null && response.has("data") && !response.get("data").isJsonNull()) {
            return response.get("data").getAsString();
        }

        JsonObject fallbackResponse = curseforge.get(String.format("mods/%s/files/%s", projectID, fileID), JsonObject.class);
        if (fallbackResponse != null && fallbackResponse.has("data") && !fallbackResponse.get("data").isJsonNull()){
            JsonObject modData = fallbackResponse.get("data").getAsJsonObject();
            int id = modData.get("id").getAsInt();
            return String.format("https://edge.forgecdn.net/files/%s/%s/%s", id / 1000, id % 1000, modData.get("fileName").getAsString());
        }

        return "";
    }
}
