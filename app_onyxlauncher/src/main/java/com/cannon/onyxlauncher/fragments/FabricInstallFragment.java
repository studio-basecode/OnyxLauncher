package com.cannon.onyxlauncher.fragments;

import com.cannon.onyxlauncher.modloaders.FabriclikeUtils;
import com.cannon.onyxlauncher.modloaders.ModloaderListenerProxy;

public class FabricInstallFragment extends FabriclikeInstallFragment {

    public static final String TAG = "FabricInstallFragment";

    public FabricInstallFragment() {
        super(FabriclikeUtils.FABRIC_UTILS, TAG);
    }
}
