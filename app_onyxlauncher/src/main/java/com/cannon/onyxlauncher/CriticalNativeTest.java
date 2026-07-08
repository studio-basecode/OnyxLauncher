package com.cannon.onyxlauncher;

import androidx.annotation.Keep;

@Keep
public class CriticalNativeTest {
    public static native void testCriticalNative(int arg0, int arg1);
    public static void invokeTest() {
        testCriticalNative(0, 0);
    }
}
