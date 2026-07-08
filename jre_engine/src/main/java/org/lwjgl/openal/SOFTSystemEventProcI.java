package org.lwjgl.openal;

@FunctionalInterface
public interface SOFTSystemEventProcI {
    void invoke(int eventType, int deviceType, long deviceName, int deviceNameLength, long message, long userParam);
}
