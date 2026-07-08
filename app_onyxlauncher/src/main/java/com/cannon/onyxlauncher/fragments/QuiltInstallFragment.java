package com.cannon.onyxlauncher.fragments;

import com.cannon.onyxlauncher.modloaders.FabriclikeUtils;
import com.cannon.onyxlauncher.modloaders.ModloaderListenerProxy;

public class QuiltInstallFragment extends FabriclikeInstallFragment {

    public static final String TAG = "QuiltInstallFragment";
    private static ModloaderListenerProxy sTaskProxy;

    public QuiltInstallFragment() {
        super(FabriclikeUtils.QUILT_UTILS, TAG);
    }
}
