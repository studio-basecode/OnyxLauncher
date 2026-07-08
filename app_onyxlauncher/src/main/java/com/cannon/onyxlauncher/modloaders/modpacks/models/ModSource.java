package com.cannon.onyxlauncher.modloaders.modpacks.models;

import java.io.Serializable;

public abstract class ModSource implements Serializable {
    private static final long serialVersionUID = 1L;

    public int apiSource;
    public boolean isModpack;
}
