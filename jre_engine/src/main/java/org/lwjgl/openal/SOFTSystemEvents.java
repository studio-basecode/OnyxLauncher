package org.lwjgl.openal;

public final class SOFTSystemEvents {
    public static final int ALC_EVENT_TYPE_DEFAULT_DEVICE_CHANGED_SOFT = 0x19D6;
    public static final int ALC_EVENT_TYPE_DEVICE_ADDED_SOFT = 0x19D7;
    public static final int ALC_EVENT_TYPE_DEVICE_REMOVED_SOFT = 0x19D8;
    public static final int ALC_PLAYBACK_DEVICE_SOFT = 0x19D4;
    public static final int ALC_CAPTURE_DEVICE_SOFT = 0x19D5;
    public static final int ALC_SUPPORTED_SOFT = 0x19D9;

    private SOFTSystemEvents() {
    }

    public static boolean alcEventControlSOFT(int[] eventTypes, boolean enable) {
        return false;
    }

    public static void alcEventCallbackSOFT(SOFTSystemEventProcI callback, long userParam) {
    }

    public static int alcEventIsSupportedSOFT(int eventType, int deviceType) {
        return 0;
    }
}
