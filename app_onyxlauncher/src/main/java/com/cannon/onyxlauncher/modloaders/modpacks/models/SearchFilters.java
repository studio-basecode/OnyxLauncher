package com.cannon.onyxlauncher.modloaders.modpacks.models;

import org.jetbrains.annotations.Nullable;

/**
 * Search filters, passed to APIs
 */
public class SearchFilters {
    public boolean isModpack;
    public String name;
    public String projectType;
    @Nullable public String mcVersion;
    @Nullable public String modLoader;

}
