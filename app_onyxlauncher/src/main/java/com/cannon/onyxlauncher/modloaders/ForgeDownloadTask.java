package com.cannon.onyxlauncher.modloaders;

import com.kdt.mcgui.ProgressLayout;

import com.cannon.onyxlauncher.R;
import com.cannon.onyxlauncher.Tools;
import com.cannon.onyxlauncher.progresskeeper.ProgressKeeper;
import com.cannon.onyxlauncher.utils.DownloadUtils;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.List;

public class ForgeDownloadTask implements Runnable, Tools.DownloaderFeedback {
    private String mDownloadUrl;
    private String mFullVersion;
    private String mLoaderVersion;
    private String mGameVersion;
    private final ModloaderDownloadListener mListener;
    public ForgeDownloadTask(ModloaderDownloadListener listener, String forgeVersion) {
        this.mListener = listener;
        this.mDownloadUrl = ForgeUtils.getInstallerUrl(forgeVersion);
        this.mFullVersion = forgeVersion;
    }

    public ForgeDownloadTask(ModloaderDownloadListener listener, String gameVersion, String loaderVersion) {
        this.mListener = listener;
        this.mLoaderVersion = loaderVersion;
        this.mGameVersion = gameVersion;
    }
    @Override
    public void run() {
        if(determineDownloadUrl()) {
            downloadForge();
        }
        ProgressLayout.clearProgress(ProgressLayout.INSTALL_MODPACK);
    }

    @Override
    public void updateProgress(int curr, int max) {
        int progress100 = (int)(((float)curr / (float)max)*100f);
        ProgressKeeper.submitProgress(ProgressLayout.INSTALL_MODPACK, progress100, R.string.forge_dl_progress, mFullVersion);
    }

    private void downloadForge() {
        ProgressKeeper.submitProgress(ProgressLayout.INSTALL_MODPACK, 0, R.string.forge_dl_progress, mFullVersion);
        try {
            File destinationFile = new File(Tools.DIR_CACHE, "forge-installer.jar");
            byte[] buffer = new byte[8192];
            DownloadUtils.downloadFileMonitored(mDownloadUrl, destinationFile, buffer, this);
            mListener.onDownloadFinished(destinationFile);
        }catch (FileNotFoundException e) {
            mListener.onDataNotAvailable();
        } catch (IOException e) {
            mListener.onDownloadError(e);
        }
    }

    public boolean determineDownloadUrl() {
        if(mDownloadUrl != null && mFullVersion != null) return true;
        ProgressKeeper.submitProgress(ProgressLayout.INSTALL_MODPACK, 0, R.string.forge_dl_searching);
        try {
            if(!findVersion()) {
                mListener.onDataNotAvailable();
                return false;
            }
        }catch (IOException e) {
            mListener.onDownloadError(e);
            return false;
        }
        return true;
    }

    public boolean findVersion() throws IOException {
        List<String> forgeVersions = ForgeUtils.downloadForgeVersions();
        if(forgeVersions == null || forgeVersions.isEmpty()) return false;
        
        String cleanLoaderVersion = mLoaderVersion != null ? mLoaderVersion.trim() : "";
        if (cleanLoaderVersion.startsWith("forge-") || cleanLoaderVersion.startsWith("Forge-")) {
            cleanLoaderVersion = cleanLoaderVersion.substring(6);
        }
        
        String mcPrefix = mGameVersion != null ? (mGameVersion + "-") : "";
        
        // 1. If a specific loader build is requested (and it's not "recommended" / "latest" / empty)
        if (!cleanLoaderVersion.isEmpty() && !cleanLoaderVersion.equalsIgnoreCase("recommended") && !cleanLoaderVersion.equalsIgnoreCase("latest")) {
            // Check for exact start match: e.g. "1.12.2-14.23.5.2860"
            String versionStart = mcPrefix + cleanLoaderVersion;
            for (String versionName : forgeVersions) {
                if (versionName.startsWith(versionStart) || versionName.equalsIgnoreCase(cleanLoaderVersion)) {
                    mFullVersion = versionName;
                    mDownloadUrl = ForgeUtils.getInstallerUrl(mFullVersion);
                    return true;
                }
            }
            // Check for contains match: e.g. contains "14.23.5.2860" and matches MC version
            for (String versionName : forgeVersions) {
                if (versionName.startsWith(mcPrefix) && versionName.contains(cleanLoaderVersion)) {
                    mFullVersion = versionName;
                    mDownloadUrl = ForgeUtils.getInstallerUrl(mFullVersion);
                    return true;
                }
            }
        }
        
        // 2. Recommended / Latest / Fallback: Pick the first (latest/recommended) matching Forge build for this MC version
        for (String versionName : forgeVersions) {
            if (versionName.startsWith(mcPrefix)) {
                mFullVersion = versionName;
                mDownloadUrl = ForgeUtils.getInstallerUrl(mFullVersion);
                return true;
            }
        }
        
        return false;
    }

}
