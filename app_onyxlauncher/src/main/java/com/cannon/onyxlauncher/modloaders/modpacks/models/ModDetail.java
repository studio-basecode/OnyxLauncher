package com.cannon.onyxlauncher.modloaders.modpacks.models;


import androidx.annotation.NonNull;

import java.util.Arrays;

public class ModDetail extends ModItem {
    /* A cheap way to map from the front facing name to the underlying id */
    public String[] versionNames;
    public String [] mcVersionNames;
    public String[] versionLoaders;
    public String[] versionUrls;
    /* SHA 1 hashes, null if a hash is unavailable */
    public String[] versionHashes;
    public String[] versionDependencyProjectIds;
    public String[] screenshotUrls;
    public ModDetail(ModItem item, String[] versionNames, String[] mcVersionNames, String[] versionUrls, String[] hashes) {
        this(item, versionNames, mcVersionNames, null, versionUrls, hashes);
    }

    public ModDetail(ModItem item, String[] versionNames, String[] mcVersionNames, String[] versionLoaders, String[] versionUrls, String[] hashes) {
        this(item, versionNames, mcVersionNames, versionLoaders, versionUrls, hashes, null);
    }

    public ModDetail(ModItem item, String[] versionNames, String[] mcVersionNames, String[] versionLoaders,
                     String[] versionUrls, String[] hashes, String[] dependencyProjectIds) {
        super(item.apiSource, item.isModpack, item.id, item.title, item.description, item.imageUrl);
        this.versionNames = versionNames;
        this.mcVersionNames = mcVersionNames;
        this.versionLoaders = versionLoaders == null ? new String[versionNames.length] : versionLoaders;
        this.versionUrls = versionUrls;
        this.versionHashes = hashes;
        this.versionDependencyProjectIds = dependencyProjectIds == null ? new String[versionNames.length] : dependencyProjectIds;

        // Add the mc version to the version model
        for (int i=0; i<versionNames.length; i++){
            if (mcVersionNames[i] != null && !mcVersionNames[i].isEmpty() && !versionNames[i].contains(mcVersionNames[i]))
                versionNames[i] += " - " + mcVersionNames[i];
        }
    }

    @NonNull
    @Override
    public String toString() {
        return "ModDetail{" +
                "versionNames=" + Arrays.toString(versionNames) +
                ", mcVersionNames=" + Arrays.toString(mcVersionNames) +
                ", versionLoaders=" + Arrays.toString(versionLoaders) +
                ", versionIds=" + Arrays.toString(versionUrls) +
                ", versionDependencyProjectIds=" + Arrays.toString(versionDependencyProjectIds) +
                ", id='" + id + '\'' +
                ", title='" + title + '\'' +
                ", description='" + description + '\'' +
                ", imageUrl='" + imageUrl + '\'' +
                ", apiSource=" + apiSource +
                ", isModpack=" + isModpack +
                '}';
    }
}
