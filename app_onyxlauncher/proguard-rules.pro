# R8 rules for release, gplay, and obfuscated APK builds.

-keepattributes Signature,InnerClasses,EnclosingMethod,*Annotation*
-renamesourcefileattribute SourceFile
-keepattributes SourceFile,LineNumberTable

# Keep Android preference screens loaded from XML and class names referenced by native code.
-keep class com.cannon.onyxlauncher.prefs.screens.** { *; }
-keep class com.cannon.onyxlauncher.MainActivity {
    public static void openLink(java.lang.String);
    public static void querySystemClipboard();
    public static void putClipboardData(java.lang.String, java.lang.String);
}
-keep class com.cannon.onyxlauncher.ExitActivity {
    public static void showExitMessage(android.content.Context, int, boolean);
}
-keep class com.cannon.onyxlauncher.AWTInputBridge { *; }
-keep class com.cannon.onyxlauncher.Logger { *; }
-keep class com.cannon.onyxlauncher.Logger$eventLogListener { *; }
-keep class com.cannon.onyxlauncher.CriticalNativeTest { *; }
-keep class com.cannon.onyxlauncher.utils.JREUtils { *; }
-keep class com.oracle.dalvik.VMLauncher { *; }

# JVM bridge classes are resolved by native code and by the launched Java runtime.
-keep class org.lwjgl.** { *; }
-keep class net.java.openjdk.cacio.** { *; }
-keep class com.github.caciocavallosilano.cacio.** { *; }
-keep class git.artdeell.** { *; }

# Keep native method names for JNI entry points.
-keepclasseswithmembernames class * {
    native <methods>;
}

# Gson/json model fields must keep their serialized names.
-keepclassmembers class com.cannon.onyxlauncher.value.** { <fields>; }
-keepclassmembers class com.cannon.onyxlauncher.tasks.** { <fields>; }
-keepclassmembers class com.cannon.onyxlauncher.modloaders.** { <fields>; }
-keepclassmembers class com.cannon.onyxlauncher.customcontrols.** { <fields>; }
-keepclassmembers class com.cannon.onyxlauncher.multirt.** { <fields>; }

# Third-party expression parser uses reflective builder access.
-keep class net.objecthunter.exp4j.ExpressionBuilder** { *; }

# Keep enum helpers used by Kotlin/Java reflection and serialization paths.
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}


