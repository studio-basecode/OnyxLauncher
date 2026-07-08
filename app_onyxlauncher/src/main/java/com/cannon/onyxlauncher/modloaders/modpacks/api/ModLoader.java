package com.cannon.onyxlauncher.modloaders.modpacks.api;

import android.content.Context;
import android.content.Intent;

import com.cannon.onyxlauncher.JavaGUILauncherActivity;
import com.cannon.onyxlauncher.modloaders.FabriclikeDownloadTask;
import com.cannon.onyxlauncher.modloaders.FabriclikeUtils;
import com.cannon.onyxlauncher.modloaders.ForgeDownloadTask;
import com.cannon.onyxlauncher.modloaders.ForgeUtils;
import com.cannon.onyxlauncher.modloaders.ModloaderDownloadListener;
import com.cannon.onyxlauncher.modloaders.NeoForgeDownloadTask;
import com.cannon.onyxlauncher.Tools;

import java.io.File;

public class ModLoader {
    public static final int MOD_LOADER_FORGE = 0;
    public static final int MOD_LOADER_FABRIC = 1;
    public static final int MOD_LOADER_QUILT = 2;
    public static final int MOD_LOADER_NEOFORGE = 3;
    public final int modLoaderType;
    public final String modLoaderVersion;
    public final String minecraftVersion;
    public String profileId;
    public String displayName;

    public ModLoader(int modLoaderType, String modLoaderVersion, String minecraftVersion) {
        this.modLoaderType = modLoaderType;
        this.modLoaderVersion = modLoaderVersion;
        this.minecraftVersion = minecraftVersion;
    }

    /**
     * Get the Version ID (the name of the mod loader in the versions/ folder)
     * @return the Version ID as a string
     */
    public String getVersionId() {
        switch (modLoaderType) {
            case MOD_LOADER_FORGE:
                if (usesLegacyForgeVersionId()) {
                    return minecraftVersion + "-Forge" + modLoaderVersion;
                }
                return minecraftVersion+"-forge-"+modLoaderVersion;
            case MOD_LOADER_FABRIC:
                return "fabric-loader-"+modLoaderVersion+"-"+minecraftVersion;
            case MOD_LOADER_QUILT:
                return "quilt-loader-"+modLoaderVersion+"-"+minecraftVersion;
            case MOD_LOADER_NEOFORGE:
                return "neoforge-" + modLoaderVersion;
            default:
                return null;
        }
    }

    /**
     * Get the Runnable that needs to run in order to download the mod loader.
     * The task will also install the mod loader if it does not require GUI installation
     * @param listener the listener that gets notified of the installation status
     * @return the task Runnable that needs to be ran
     */
    public Runnable getDownloadTask(ModloaderDownloadListener listener) {
        switch (modLoaderType) {
            case MOD_LOADER_FORGE:
                return new ForgeDownloadTask(listener, minecraftVersion, modLoaderVersion);
            case MOD_LOADER_FABRIC:
                return createFabriclikeTask(listener, FabriclikeUtils.FABRIC_UTILS);
            case MOD_LOADER_QUILT:
                return createFabriclikeTask(listener, FabriclikeUtils.QUILT_UTILS);
            case MOD_LOADER_NEOFORGE:
                return new NeoForgeDownloadTask(listener, modLoaderVersion);
            default:
                return null;
        }
    }

    /**
     * Get the Intent to start the graphical installation of the mod loader.
     * This method should only be ran after the download task of the specified mod loader finishes.
     * This method returns null if the mod loader does not require GUI installation
     * @param context the package resolving Context (can be the base context)
     * @param modInstallerJar the JAR file of the mod installer, provided by ModloaderDownloadListener after the installation
     *                        finishes.
     * @return the Intent which the launcher needs to start in order to install the mod loader
     */
    public Intent getInstallationIntent(Context context, File modInstallerJar) {
        Intent baseIntent = new Intent(context, JavaGUILauncherActivity.class);
        switch (modLoaderType) {
            case MOD_LOADER_FORGE:
                ForgeUtils.addAutoInstallArgs(baseIntent, modInstallerJar, getVersionId());
                return baseIntent;
            case MOD_LOADER_NEOFORGE: {
                int targetMcJava = 17;
                try {
                    targetMcJava = Tools.getVersionInfo(minecraftVersion).javaVersion.majorVersion;
                } catch (Exception e) {
                    // Ignore, fallback to 17
                }
                baseIntent.putExtra("targetJavaVersion", targetMcJava);
                ForgeUtils.addCliInstallArgs(baseIntent, modInstallerJar, Tools.DIR_GAME_NEW);
                return baseIntent;
            }
            case MOD_LOADER_QUILT:
            case MOD_LOADER_FABRIC:
            default:
                return null;
        }
    }

    /**
     * Check whether the mod loader this object denotes requires GUI installation
     * @return true if mod loader requires GUI installation, false otherwise
     */
    public boolean requiresGuiInstallation() {
        switch (modLoaderType) {
            case MOD_LOADER_FORGE:
            case MOD_LOADER_NEOFORGE:
                if (isVersionJsonInstalled()) return false;
                return true;
            case MOD_LOADER_FABRIC:
            case MOD_LOADER_QUILT:
            default:
                return false;
        }
    }

    private FabriclikeDownloadTask createFabriclikeTask(ModloaderDownloadListener modloaderDownloadListener, FabriclikeUtils utils) {
        return new FabriclikeDownloadTask(modloaderDownloadListener, utils, minecraftVersion, modLoaderVersion, false);
    }

    private boolean isVersionJsonInstalled() {
        String versionId = getVersionId();
        if (versionId == null || versionId.isEmpty()) return false;
        File versionJson = new File(Tools.DIR_HOME_VERSION, versionId + "/" + versionId + ".json");
        return versionJson.isFile() && versionJson.length() > 0;
    }

    private boolean usesLegacyForgeVersionId() {
        return minecraftVersion != null &&
                (minecraftVersion.startsWith("1.6.") || minecraftVersion.startsWith("1.7."));
    }
}
