package com.cannon.onyxlauncher.utils;

import static com.cannon.onyxlauncher.Architecture.ARCH_X86;
import static com.cannon.onyxlauncher.Architecture.is64BitsDevice;
import static com.cannon.onyxlauncher.Tools.LOCAL_RENDERER;
import static com.cannon.onyxlauncher.Tools.NATIVE_LIB_DIR;
import static com.cannon.onyxlauncher.Tools.currentDisplayMetrics;
import static com.cannon.onyxlauncher.Tools.shareLog;
import static com.cannon.onyxlauncher.prefs.LauncherPreferences.PREF_DUMP_SHADERS;
import static com.cannon.onyxlauncher.prefs.LauncherPreferences.PREF_VSYNC_IN_ZINK;
import static com.cannon.onyxlauncher.prefs.LauncherPreferences.PREF_ZINK_PREFER_SYSTEM_DRIVER;

import android.app.*;
import android.content.*;
import android.os.Build;
import android.system.*;
import android.util.*;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.oracle.dalvik.*;
import java.io.*;
import java.util.*;
import com.cannon.onyxlauncher.*;
import com.cannon.onyxlauncher.extra.ExtraConstants;
import com.cannon.onyxlauncher.extra.ExtraCore;
import com.cannon.onyxlauncher.lifecycle.LifecycleAwareAlertDialog;
import com.cannon.onyxlauncher.multirt.MultiRTUtils;
import com.cannon.onyxlauncher.multirt.Runtime;
import com.cannon.onyxlauncher.plugins.FFmpegPlugin;
import com.cannon.onyxlauncher.prefs.*;
import org.lwjgl.glfw.*;

public class JREUtils {
    private JREUtils() {}

    public static String LD_LIBRARY_PATH;
    public static String jvmLibraryPath;

    public static String findInLdLibPath(String libName) {
        if(Os.getenv("LD_LIBRARY_PATH")==null) {
            try {
                if (LD_LIBRARY_PATH != null) {
                    Os.setenv("LD_LIBRARY_PATH", LD_LIBRARY_PATH, true);
                }
            }catch (ErrnoException e) {
                e.printStackTrace();
            }
            return libName;
        }
        for (String libPath : Os.getenv("LD_LIBRARY_PATH").split(":")) {
            File f = new File(libPath, libName);
            if (f.exists() && f.isFile()) {
                return f.getAbsolutePath();
            }
        }
        return libName;
    }

    public static ArrayList<File> locateLibs(File path) {
        ArrayList<File> returnValue = new ArrayList<>();
        File[] list = path.listFiles();
        if(list != null) {
            for(File f : list) {
                if(f.isFile() && f.getName().endsWith(".so")) {
                    returnValue.add(f);
                }else if(f.isDirectory()) {
                    returnValue.addAll(locateLibs(f));
                }
            }
        }
        return returnValue;
    }

    public static void initJavaRuntime(String jreHome) {
        dlopen(findInLdLibPath("libc++_shared.so"));
        dlopen(findInLdLibPath("libjli.so"));
        if(!dlopen("libjvm.so")){
            Log.w("DynamicLoader","Failed to load with no path, trying with full path");
            dlopen(jvmLibraryPath+"/libjvm.so");
        }
        dlopen(findInLdLibPath("libverify.so"));
        dlopen(findInLdLibPath("libjava.so"));
        // dlopen(findInLdLibPath("libjsig.so"));
        dlopen(findInLdLibPath("libnet.so"));
        dlopen(findInLdLibPath("libnio.so"));
        dlopen(findInLdLibPath("libawt.so"));
        dlopen(findInLdLibPath("libawt_headless.so"));
        dlopen(findInLdLibPath("libfreetype.so"));
        dlopen(findInLdLibPath("libfontmanager.so"));
        for(File f : locateLibs(new File(jreHome, Tools.DIRNAME_HOME_JRE))) {
            dlopen(f.getAbsolutePath());
        }
        dlopen(NATIVE_LIB_DIR + "/libopenal.so");
    }

    public static void redirectAndPrintJRELog() {

        Log.v("jrelog","Log starts here");
        new Thread(new Runnable(){
            int failTime = 0;
            ProcessBuilder logcatPb;
            @Override
            public void run() {
                try {
                    if (logcatPb == null) {
                        // No filtering by tag anymore as that relied on incorrect log levels set in log.h
                        logcatPb = new ProcessBuilder().command("logcat", /* "-G", "1mb", */ "-v", "brief", "-s", "jrelog", "LIBGL", "NativeInput").redirectErrorStream(true);
                    }

                    Log.i("jrelog-logcat","Clearing logcat");
                    new ProcessBuilder().command("logcat", "-c").redirectErrorStream(true).start();
                    Log.i("jrelog-logcat","Starting logcat");
                    java.lang.Process p = logcatPb.start();

                    byte[] buf = new byte[1024];
                    int len;
                    while ((len = p.getInputStream().read(buf)) != -1) {
                        String currStr = new String(buf, 0, len);
                        Logger.appendToLog(currStr);
                    }

                    if (p.waitFor() != 0) {
                        Log.e("jrelog-logcat", "Logcat exited with code " + p.exitValue());
                        failTime++;
                        Log.i("jrelog-logcat", (failTime <= 10 ? "Restarting logcat" : "Too many restart fails") + " (attempt " + failTime + "/10");
                        if (failTime <= 10) {
                            run();
                        } else {
                            Logger.appendToLog("ERROR: Unable to get more log.");
                        }
                    }
                } catch (Throwable e) {
                    Log.e("jrelog-logcat", "Exception on logging thread", e);
                    Logger.appendToLog("Exception on logging thread:\n" + Log.getStackTraceString(e));
                }
            }
        }).start();
        Log.i("jrelog-logcat","Logcat thread started");

    }

    public static void relocateLibPath(Runtime runtime, String jreHome) {
        String JRE_ARCHITECTURE = runtime.arch;
        if (Architecture.archAsInt(JRE_ARCHITECTURE) == ARCH_X86){
            JRE_ARCHITECTURE = "i386/i486/i586";
        }

        for (String arch : JRE_ARCHITECTURE.split("/")) {
            File f = new File(jreHome, "lib/" + arch);
            if (f.exists() && f.isDirectory()) {
                Tools.DIRNAME_HOME_JRE = "lib/" + arch;
            }
        }

        String libName = is64BitsDevice() ? "lib64" : "lib";
        StringBuilder ldLibraryPath = new StringBuilder();
        if(FFmpegPlugin.isAvailable) {
            ldLibraryPath.append(FFmpegPlugin.libraryPath).append(":");
        }
        ldLibraryPath.append(jreHome)
                .append("/").append(Tools.DIRNAME_HOME_JRE)
                .append("/jli:").append(jreHome).append("/").append(Tools.DIRNAME_HOME_JRE)
                .append(":");
        ldLibraryPath.append("/system/").append(libName).append(":")
                .append("/vendor/").append(libName).append(":")
                .append("/vendor/").append(libName).append("/hw:")
                .append(NATIVE_LIB_DIR);
        LD_LIBRARY_PATH = ldLibraryPath.toString();
    }

    public static void setJavaEnvironment(Activity activity, String jreHome) throws Throwable {
        setJavaEnvironment(activity, jreHome, true);
    }

    private static void unsetGraphicsEnvironment() {
        String[] keys = new String[] {
                "ONYX_RENDERER",
                "POJAV_RENDERER",
                "ONYX_LOAD_TURNIP",
                "POJAV_LOAD_TURNIP",
                "ONYX_VSYNC_IN_ZINK",
                "POJAV_VSYNC_IN_ZINK",
                "MESA_LOADER_DRIVER_OVERRIDE",
                "GALLIUM_DRIVER",
                "MESA_GL_VERSION_OVERRIDE",
                "MESA_GLSL_VERSION_OVERRIDE",
                "VTEST_SOCKET_NAME",
                "LIBGL_ES",
                "LIBGL_GL",
                "LIBGL_MIPMAP",
                "LIBGL_NOERROR",
                "LIBGL_CUSTOMVERSION",
                "LIBGL_NOINTOVLHACK",
                "LIBGL_NORMALIZE",
                "LIBGL_VGPU_DUMP",
                "FORCE_VSYNC",
                "POJAV_BIG_CORE_AFFINITY",
                "ONYX_BIG_CORE_AFFINITY"
        };
        for(String key : keys) {
            try {
                Os.unsetenv(key);
            } catch (ErrnoException exception) {
                Log.w("JREUtils", "Failed to unset " + key, exception);
            }
        }
    }

    public static void setJavaEnvironment(Activity activity, String jreHome, boolean enableGraphicsEnvironment) throws Throwable {
        Map<String, String> envMap = new ArrayMap<>();
        envMap.put("ONYX_NATIVEDIR", NATIVE_LIB_DIR);
        // Alias: prebuilt MC mods/libraries (e.g. Sodium) may still check for POJAV_NATIVEDIR
        envMap.put("POJAV_NATIVEDIR", NATIVE_LIB_DIR);
        envMap.put("JAVA_HOME", jreHome);
        envMap.put("HOME", Tools.DIR_GAME_HOME);
        envMap.put("TMPDIR", Tools.DIR_CACHE.getAbsolutePath());
        if(enableGraphicsEnvironment) {
            // gl4es value 3 disables translator-side mipmap creation/use. This keeps
            // resource packs from exercising fragile GLES mipmap paths on mobile drivers.
            envMap.put("LIBGL_MIPMAP", "3");

            // Prevent OptiFine (and other error-reporting stuff in Minecraft) from balooning the log
            envMap.put("LIBGL_NOERROR", "1");
            envMap.put("LIBGL_CUSTOMVERSION", "3.3.0"); // Custom GL version for Iris shader compatibility

            // On certain GLES drivers, overloading default functions shader hack fails, so disable it
            envMap.put("LIBGL_NOINTOVLHACK", "1");

            // Fix white color on banner and sheep, since GL4ES 1.1.5
            envMap.put("LIBGL_NORMALIZE", "1");

            if(PREF_DUMP_SHADERS)
                envMap.put("LIBGL_VGPU_DUMP", "1");
            if(PREF_VSYNC_IN_ZINK && !shouldSkipZinkVsync()) {
                envMap.put("ONYX_VSYNC_IN_ZINK", "1");
                envMap.put("POJAV_VSYNC_IN_ZINK", "1"); // alias for prebuilt libs
            } else if(PREF_VSYNC_IN_ZINK && "vulkan_zink".equals(LOCAL_RENDERER)) {
                Log.i("JREUtils", "Skipping Zink V-Sync hook on this Android version for stability");
            }
            if(Tools.deviceHasHangingLinker()) {
                envMap.put("ONYX_EMUI_ITERATOR_MITIGATE", "1");
                envMap.put("POJAV_EMUI_ITERATOR_MITIGATE", "1"); // alias for prebuilt libs
            }

            // The OPEN GL version is changed according
            String selectedOpenGlVersion = (String) ExtraCore.getValue(ExtraConstants.OPEN_GL_VERSION);
            if(selectedOpenGlVersion != null && !selectedOpenGlVersion.isEmpty()) {
                envMap.put("LIBGL_ES", selectedOpenGlVersion);
                if ("3".equals(selectedOpenGlVersion)) {
                    envMap.put("LIBGL_GL", "33");
                }
            }

            envMap.put("FORCE_VSYNC", String.valueOf(LauncherPreferences.PREF_FORCE_VSYNC));

            envMap.put("MESA_GLSL_CACHE_DIR", Tools.DIR_CACHE.getAbsolutePath());
            envMap.put("force_glsl_extensions_warn", "true");
            envMap.put("allow_higher_compat_version", "true");
            envMap.put("allow_glsl_extension_directive_midshader", "true");
            if ("vulkan_zink".equals(LOCAL_RENDERER)) {
                envMap.put("MESA_LOADER_DRIVER_OVERRIDE", "zink");
                envMap.put("GALLIUM_DRIVER", "zink");
                envMap.put("MESA_GL_VERSION_OVERRIDE", "4.6");
                envMap.put("MESA_GLSL_VERSION_OVERRIDE", "460");
                envMap.put("VTEST_SOCKET_NAME", new File(Tools.DIR_CACHE, ".virgl_test").getAbsolutePath());
            }
        } else {
            unsetGraphicsEnvironment();
        }

        envMap.put("LD_LIBRARY_PATH", LD_LIBRARY_PATH);
        envMap.put("PATH", jreHome + "/bin:" + Os.getenv("PATH"));
        if(FFmpegPlugin.isAvailable) {
            envMap.put("ONYX_FFMPEG_PATH", FFmpegPlugin.executablePath);
            envMap.put("POJAV_FFMPEG_PATH", FFmpegPlugin.executablePath); // alias for prebuilt libs
        }

        if(enableGraphicsEnvironment && LOCAL_RENDERER != null) {
            // Android 12 and below (API <= 31): gl4es GLES2 backend does not implement
            // glMapBufferRange correctly - it returns NULL without setting a GL error.
            // LWJGL 3.3.3+ throws "Can't map buffer, opengl error 0" as a result.
            // Fix: force opengles3 renderer which uses GLES3 EGL context and backend,
            // where glMapBufferRange is natively supported. All Android 5.0+ (API 21+)
            // devices are required to support GLES 3.0, so API 31 always has GLES3.
            String effectiveRenderer = LOCAL_RENDERER;
            if ("opengles2".equals(LOCAL_RENDERER)) {
                effectiveRenderer = "opengles3";
                Log.i("JREUtils", "Forcing opengles3 renderer to fix glMapBufferRange crash (GLES3 backend)");
            }
            envMap.put("ONYX_RENDERER", effectiveRenderer);
            if (effectiveRenderer.equals("opengles3") || effectiveRenderer.equals("opengles3_ltw")) {
                envMap.put("LIBGL_ES", "3");
                // Set custom GL version string so mods like Iris can parse the GL version reliably without throwing "Could not parse GL version from ''"
                envMap.put("LIBGL_CUSTOMVERSION", "3.3.0");
                if (effectiveRenderer.equals("opengles3_ltw")) {
                    envMap.put("ONYXEXEC_EGL","libltw.so"); // Use ANGLE EGL
                    envMap.put("POJAVEXEC_EGL","libltw.so"); // alias for prebuilt libs
                }
            }
        }
        if(enableGraphicsEnvironment && LauncherPreferences.PREF_BIG_CORE_AFFINITY) {
            envMap.put("ONYX_BIG_CORE_AFFINITY", "1");
            envMap.put("POJAV_BIG_CORE_AFFINITY", "1"); // alias for prebuilt libs
        }
        envMap.put("AWTSTUB_WIDTH", Integer.toString(CallbackBridge.windowWidth > 0 ? CallbackBridge.windowWidth : CallbackBridge.physicalWidth));
        envMap.put("AWTSTUB_HEIGHT", Integer.toString(CallbackBridge.windowHeight > 0 ? CallbackBridge.windowHeight : CallbackBridge.physicalHeight));

        GLInfoUtils.GLInfo info = GLInfoUtils.getGlInfo();
        if(enableGraphicsEnvironment && !envMap.containsKey("LIBGL_ES") && LOCAL_RENDERER != null) {
            int glesMajor = info.glesMajorVersion;
            Log.i("glesDetect","GLES version detected: "+glesMajor);

            if (glesMajor < 3) {
                //fallback to 2 since it's the minimum for the entire app
                envMap.put("LIBGL_ES","2");
            } else if (LOCAL_RENDERER.startsWith("opengles")) {
                envMap.put("LIBGL_ES", LOCAL_RENDERER.replace("opengles", "").replace("_5", ""));
            } else {
                // TODO if can: other backends such as Vulkan.
                // Sure, they should provide GLES 3 support.
                envMap.put("LIBGL_ES", "3");
            }
        }

        if(enableGraphicsEnvironment && info.isAdreno() && !PREF_ZINK_PREFER_SYSTEM_DRIVER) {
            envMap.put("ONYX_LOAD_TURNIP", "1");
            envMap.put("POJAV_LOAD_TURNIP", "1"); // alias for prebuilt libs
            if(info.renderer != null && info.renderer.contains("750")) {
                Log.i("JREUtils", "Using Turnip Vulkan overlay on Adreno 750 for Zink shader stability");
            }
        }

        if(enableGraphicsEnvironment) {
            readCustomEnv(envMap); // Must be last so it overrides anything the user sets for obvious reasons.
        }
        // Sodium 0.6+ aborts on Android when POJAV_RENDERER is present. The native bridge uses
        // ONYX_RENDERER, so keep the Onyx variable and make sure the legacy alias is absent.
        envMap.remove("POJAV_RENDERER");
        try {
            Os.unsetenv("POJAV_RENDERER");
        } catch (ErrnoException exception) {
            Log.w("JREUtils", "Failed to unset POJAV_RENDERER", exception);
        }

        for (Map.Entry<String, String> env : envMap.entrySet()) {
            if(env.getValue() == null) continue;
            Logger.appendToLog("Added custom env: " + env.getKey() + "=" + env.getValue());
            try {
                Os.setenv(env.getKey(), env.getValue(), true);
            }catch (NullPointerException exception){
                Log.e("JREUtils", exception.toString());
            }
        }

        File serverFile = new File(jreHome + "/" + Tools.DIRNAME_HOME_JRE + "/server/libjvm.so");
        jvmLibraryPath = jreHome + "/" + Tools.DIRNAME_HOME_JRE + "/" + (serverFile.exists() ? "server" : "client");
        Log.d("DynamicLoader","Base LD_LIBRARY_PATH: "+LD_LIBRARY_PATH);
        Log.d("DynamicLoader","Internal LD_LIBRARY_PATH: "+jvmLibraryPath+":"+LD_LIBRARY_PATH);
        setLdLibraryPath(jvmLibraryPath+":"+LD_LIBRARY_PATH);

        // return ldLibraryPath;
    }

    private static void readCustomEnv(Map<String, String> envMap) throws IOException {
        File customEnvFile = new File(Tools.DIR_GAME_HOME, "custom_env.txt");
        if (customEnvFile.exists() && customEnvFile.isFile()) {
            BufferedReader reader = new BufferedReader(new FileReader(customEnvFile));
            String line;
            while ((line = reader.readLine()) != null) {
                // Not use split() as only split first one
                int index = line.indexOf("=");
                if (index <= 0) continue;
                envMap.put(line.substring(0, index), line.substring(index + 1));
            }
            reader.close();
        }
    }

    private static boolean shouldSkipZinkVsync() {
        return "vulkan_zink".equals(LOCAL_RENDERER) && Build.VERSION.SDK_INT >= 35;
    }

    private static void applyRendererCompatibilityPolicy(Context context, File gameDirectory) {
        File effectiveGameDirectory = gameDirectory == null ? new File(Tools.DIR_GAME_NEW) : gameDirectory;
        if(MobileProfileOptimizer.isExtremePack(effectiveGameDirectory)) {
            if("vulkan_zink".equals(LOCAL_RENDERER)) {
                Log.i("JREUtils", "Keeping extreme modpack off Zink to avoid GL memory pressure");
                Logger.appendToLog("Info: Keeping extreme modpack on Holy GL4ES to avoid Zink GL memory pressure");
                Tools.LOCAL_RENDERER = "opengles2";
            }
            return;
        }
        if(LOCAL_RENDERER == null || !LOCAL_RENDERER.startsWith("opengles")) return;
        if(!Tools.checkRendererCompatible(context, "vulkan_zink")) return;

        if(!usesModernShaderPipeline(effectiveGameDirectory)) return;

        Log.i("JREUtils", "Switching renderer from " + LOCAL_RENDERER + " to vulkan_zink for Sodium/Iris shader stability");
        Logger.appendToLog("Info: Switching renderer from " + LOCAL_RENDERER + " to vulkan_zink for Sodium/Iris shader stability");
        Tools.LOCAL_RENDERER = "vulkan_zink";
    }

    private static boolean usesModernShaderPipeline(File gameDirectory) {
        return hasKnownModernRendererMod(gameDirectory) || hasEnabledShaderPack(gameDirectory);
    }

    private static boolean hasKnownModernRendererMod(File gameDirectory) {
        File modsDir = new File(gameDirectory, "mods");
        File[] mods = modsDir.listFiles(file -> file.isFile() && file.getName().endsWith(".jar"));
        if(mods == null) return false;

        for(File mod : mods) {
            String name = mod.getName().toLowerCase(Locale.ROOT);
            if(name.contains("sodium") ||
                    name.contains("iris") ||
                    name.contains("optifine") ||
                    name.contains("oculus") ||
                    name.contains("embeddium") ||
                    name.contains("rubidium")) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasEnabledShaderPack(File gameDirectory) {
        if(hasEnabledShaderPackProperty(new File(gameDirectory, "config/iris.properties"))) return true;
        return hasEnabledShaderPackProperty(new File(gameDirectory, "optionsshaders.txt"));
    }

    private static boolean hasEnabledShaderPackProperty(File file) {
        if(!file.isFile()) return false;
        Properties properties = new Properties();
        try(FileInputStream inputStream = new FileInputStream(file)) {
            properties.load(inputStream);
        } catch (IOException e) {
            Log.w("JREUtils", "Failed to inspect shader config " + file, e);
            return false;
        }

        String enabled = properties.getProperty("enableShaders");
        String shaderPack = properties.getProperty("shaderPack", "").trim();
        boolean shaderPackSelected = !shaderPack.isEmpty() &&
                !"off".equalsIgnoreCase(shaderPack) &&
                !"(off)".equalsIgnoreCase(shaderPack);
        return shaderPackSelected && (enabled == null || Boolean.parseBoolean(enabled));
    }

    private static void stabilizeIrisSodiumStack(File gameDirectory) {
        File effectiveGameDirectory = gameDirectory == null ? new File(Tools.DIR_GAME_NEW) : gameDirectory;
        File modsDir = new File(effectiveGameDirectory, "mods");
        File[] mods = modsDir.listFiles(file -> file.isFile() && file.getName().endsWith(".jar"));
        if(mods == null) return;

        ArrayList<File> irisJarsToDelete = new ArrayList<>();
        ArrayList<File> sodiumJarsToDelete = new ArrayList<>();
        boolean needsStableIrisStack = false;
        boolean hasCompatibleSodium = false;

        for(File mod : mods) {
            String fabricModJson = readJarEntry(mod, "fabric.mod.json");
            if(fabricModJson == null) continue;

            String modId = extractJsonString(fabricModJson, "id");
            String version = extractJsonString(fabricModJson, "version");
            if("iris".equals(modId)) {
                boolean betaIrisWithSodium08 = version.toLowerCase(Locale.ROOT).contains("beta") &&
                        fabricModJson.contains("\"sodium\"") &&
                        fabricModJson.contains("0.8.x");
                if(betaIrisWithSodium08) {
                    needsStableIrisStack = true;
                    irisJarsToDelete.add(mod);
                }
            } else if("sodium".equals(modId)) {
                if(version.startsWith("0.6.")) {
                    hasCompatibleSodium = true;
                } else {
                    sodiumJarsToDelete.add(mod);
                }
            }
        }

        if(!needsStableIrisStack) return;

        try {
            File stableIris = new File(modsDir, "Iris 1.8.8 for Fabric 1.21.1-onyx.jar");
            File stableSodium = new File(modsDir, "Sodium 0.6.13 for Fabric 1.21.1-onyx.jar");
            downloadIfMissing("https://cdn.modrinth.com/data/YL57xq9U/versions/zsoi0dso/iris-fabric-1.8.8%2Bmc1.21.1.jar", stableIris);
            if(!hasCompatibleSodium) {
                downloadIfMissing("https://cdn.modrinth.com/data/AANobbMI/versions/u1OEbNKx/sodium-fabric-0.6.13%2Bmc1.21.1.jar", stableSodium);
            }

            for(File file : irisJarsToDelete) deleteModJar(file);
            for(File file : sodiumJarsToDelete) deleteModJar(file);
            Log.i("JREUtils", "Replaced beta Iris/Sodium stack with Iris 1.8.8 and Sodium 0.6.13");
            Logger.appendToLog("Info: Replaced beta Iris/Sodium stack with stable 1.21.1-compatible mods");
        } catch (IOException e) {
            Log.e("JREUtils", "Failed to stabilize Iris/Sodium stack", e);
            Logger.appendToLog("Warning: Failed to stabilize Iris/Sodium stack: " + e.getMessage());
        }
    }

    private static void stabilizeIrisShaderOptions(File gameDirectory) {
        File effectiveGameDirectory = gameDirectory == null ? new File(Tools.DIR_GAME_NEW) : gameDirectory;
        GLInfoUtils.GLInfo info = GLInfoUtils.getGlInfo();
        if(info.renderer == null || !info.renderer.contains("750")) return;

        File irisConfigFile = new File(effectiveGameDirectory, "config/iris.properties");
        if(!irisConfigFile.isFile()) return;

        Properties irisProperties = new Properties();
        try(FileInputStream inputStream = new FileInputStream(irisConfigFile)) {
            irisProperties.load(inputStream);
        } catch (IOException e) {
            Log.w("JREUtils", "Failed to read Iris config", e);
            return;
        }

        if(!Boolean.parseBoolean(irisProperties.getProperty("enableShaders", "true"))) return;
        String shaderPack = irisProperties.getProperty("shaderPack", "").trim();
        if(shaderPack.isEmpty() || "off".equalsIgnoreCase(shaderPack) || "(off)".equalsIgnoreCase(shaderPack)) return;
        if(!shaderPack.toLowerCase(Locale.ROOT).contains("photon")) return;

        File shaderPackFile = new File(new File(effectiveGameDirectory, "shaderpacks"), shaderPack);
        if(!shaderPackFile.isFile()) return;

        File optionsFile = new File(shaderPackFile.getParentFile(), shaderPack + ".txt");
        Properties options = new Properties();
        if(optionsFile.isFile()) {
            try(FileInputStream inputStream = new FileInputStream(optionsFile)) {
                options.load(inputStream);
            } catch (IOException e) {
                Log.w("JREUtils", "Failed to read Iris shader options " + optionsFile.getName(), e);
            }
        }

        boolean changed = false;
        changed |= putIfDifferent(options, "INFO", "0");
        changed |= putIfDifferent(options, "shadowMapResolution", "1024");
        changed |= putIfDifferent(options, "ENTITY_SHADOWS", "false");
        changed |= putIfDifferent(options, "ENVIRONMENT_REFLECTIONS", "false");
        changed |= putIfDifferent(options, "SHADOW_COLOR", "false");
        changed |= putIfDifferent(options, "SHADOW_SSRT", "false");
        changed |= putIfDifferent(options, "SHADOW_VPS", "false");
        changed |= putIfDifferent(options, "SH_SKYLIGHT", "false");
        changed |= putIfDifferent(options, "VL", "false");
        changed |= putIfDifferent(options, "WATER_PARALLAX", "false");

        if(!changed) return;

        try {
            File parent = optionsFile.getParentFile();
            if(parent != null && !parent.isDirectory() && !parent.mkdirs()) {
                throw new IOException("Could not create " + parent);
            }
            try(FileOutputStream outputStream = new FileOutputStream(optionsFile)) {
                options.store(outputStream, "Onyx Adreno 750 Photon safety profile");
            }
            Log.i("JREUtils", "Applied Adreno 750 safe Iris shader options for Photon");
            Logger.appendToLog("Info: Applied Adreno 750 safe Iris shader options for Photon");
        } catch (IOException e) {
            Log.w("JREUtils", "Failed to write Iris shader options " + optionsFile.getName(), e);
        }
    }

    private static boolean putIfDifferent(Properties properties, String key, String value) {
        if(value.equals(properties.getProperty(key))) return false;
        properties.setProperty(key, value);
        return true;
    }

    private static String readJarEntry(File jarFile, String entryName) {
        try(java.util.zip.ZipFile zipFile = new java.util.zip.ZipFile(jarFile)) {
            java.util.zip.ZipEntry entry = zipFile.getEntry(entryName);
            if(entry == null) return null;
            try(InputStream inputStream = zipFile.getInputStream(entry)) {
                return new String(readAllBytes(inputStream), java.nio.charset.StandardCharsets.UTF_8);
            }
        } catch (IOException e) {
            Log.w("JREUtils", "Failed to read " + entryName + " from " + jarFile.getName(), e);
            return null;
        }
    }

    private static byte[] readAllBytes(InputStream inputStream) throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int read;
        while((read = inputStream.read(buffer)) != -1) {
            outputStream.write(buffer, 0, read);
        }
        return outputStream.toByteArray();
    }

    private static String extractJsonString(String json, String key) {
        String needle = "\"" + key + "\"";
        int keyIndex = json.indexOf(needle);
        if(keyIndex < 0) return "";
        int colonIndex = json.indexOf(':', keyIndex + needle.length());
        if(colonIndex < 0) return "";
        int startQuote = json.indexOf('"', colonIndex + 1);
        if(startQuote < 0) return "";
        int endQuote = json.indexOf('"', startQuote + 1);
        if(endQuote < 0) return "";
        return json.substring(startQuote + 1, endQuote);
    }

    private static void downloadIfMissing(String url, File destination) throws IOException {
        if(destination.isFile() && destination.length() > 0) return;
        File tempFile = new File(destination.getParentFile(), destination.getName() + ".tmp");
        java.net.HttpURLConnection connection = (java.net.HttpURLConnection) new java.net.URL(url).openConnection();
        connection.setConnectTimeout(15000);
        connection.setReadTimeout(30000);
        connection.setRequestProperty("User-Agent", "OnyxLauncher/1.0");
        try(InputStream inputStream = connection.getInputStream();
            FileOutputStream outputStream = new FileOutputStream(tempFile)) {
            byte[] buffer = new byte[8192];
            int read;
            while((read = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, read);
            }
        } finally {
            connection.disconnect();
        }
        if(destination.exists() && !destination.delete()) {
            throw new IOException("Could not replace " + destination.getName());
        }
        if(!tempFile.renameTo(destination)) {
            throw new IOException("Could not move " + tempFile.getName() + " to " + destination.getName());
        }
        Log.i("JREUtils", "Downloaded " + destination.getName());
    }

    private static void deleteModJar(File file) {
        if(file.getName().endsWith("-onyx.jar")) return;
        if(file.delete()) {
            Log.i("JREUtils", "Deleted incompatible mod jar " + file.getName());
        } else {
            Log.w("JREUtils", "Could not delete incompatible mod jar " + file.getName());
        }
    }

    public static void launchJavaVM(final AppCompatActivity activity, final Runtime runtime, File gameDirectory, final List<String> JVMArgs, final String userArgsString) throws Throwable {
        launchJavaVM(activity, runtime, gameDirectory, JVMArgs, userArgsString, false);
    }

    public static void launchJavaVM(final AppCompatActivity activity, final Runtime runtime, File gameDirectory, final List<String> JVMArgs, final String userArgsString, boolean headlessTool) throws Throwable {
        String runtimeHome = MultiRTUtils.getRuntimeHome(runtime.name).getAbsolutePath();

        if(!headlessTool) {
            stabilizeIrisSodiumStack(gameDirectory);
            stabilizeIrisShaderOptions(gameDirectory);
            applyRendererCompatibilityPolicy(activity, gameDirectory);
        }

        JREUtils.relocateLibPath(runtime, runtimeHome);

        setJavaEnvironment(activity, runtimeHome, !headlessTool);

        final String graphicsLib = headlessTool ? null : loadGraphicsLibrary();
        List<String> userArgs = getJavaArgs(activity, runtimeHome, userArgsString, !headlessTool);

        //Remove arguments that can interfere with the good working of the launcher
        purgeArg(userArgs,"-Xms");
        purgeArg(userArgs,"-Xmx");
        purgeArg(userArgs,"-d32");
        purgeArg(userArgs,"-d64");
        purgeArg(userArgs, "-Xint");
        purgeArg(userArgs, "-XX:+UseTransparentHugePages");
        purgeArg(userArgs, "-XX:+UseLargePagesInMetaspace");
        purgeArg(userArgs, "-XX:+UseLargePages");
        purgeArg(userArgs, "-Dorg.lwjgl.opengl.libname");
        // Don't let the user specify a custom Freetype library (as the user is unlikely to specify a version compiled for Android)
        purgeArg(userArgs, "-Dorg.lwjgl.freetype.libname");
        // Overridden by us to specify the exact number of cores that the android system has
        purgeArg(userArgs, "-XX:ActiveProcessorCount");

        //Add automatically generated args
        int initialHeapMb = MobileProfileOptimizer.recommendedInitialHeapMb(LauncherPreferences.PREF_RAM_ALLOCATION);
        if(!headlessTool) {
            userArgs.add("-Xms" + initialHeapMb + "M");
            userArgs.add("-Xmx" + LauncherPreferences.PREF_RAM_ALLOCATION + "M");
            if(LOCAL_RENDERER != null) userArgs.add("-Dorg.lwjgl.opengl.libname=" + graphicsLib);

            // Force LWJGL to use the Freetype library intended for it, instead of using the one
            // that we ship with Java (since it may be older than what's needed)
            userArgs.add("-Dorg.lwjgl.freetype.libname="+ NATIVE_LIB_DIR+"/libfreetype.so");
        }

        // Some phones are not using the right number of cores, fix that
        int activeProcessors = MobileProfileOptimizer.recommendedActiveProcessors(
                java.lang.Runtime.getRuntime().availableProcessors(),
                LauncherPreferences.PREF_RAM_ALLOCATION);
        userArgs.add("-XX:ActiveProcessorCount=" + activeProcessors);
        if(!headlessTool) {
            Logger.appendToLog("Info: JVM mobile limits: Xms=" + initialHeapMb
                    + "M Xmx=" + LauncherPreferences.PREF_RAM_ALLOCATION
                    + "M ActiveProcessorCount=" + activeProcessors);
        }

        userArgs.addAll(JVMArgs);
        if(!headlessTool) {
            activity.runOnUiThread(() -> Toast.makeText(activity, activity.getString(R.string.autoram_info_msg,LauncherPreferences.PREF_RAM_ALLOCATION), Toast.LENGTH_SHORT).show());
        }
        System.out.println(JVMArgs);

        initJavaRuntime(runtimeHome);
        JREUtils.setupExitMethod(activity.getApplication());
        JREUtils.initializeHooks();
        chdir(gameDirectory == null ? Tools.DIR_GAME_NEW : gameDirectory.getAbsolutePath());
        userArgs.add(0,"java"); //argv[0] is the program name according to C standard.

        final int exitCode = VMLauncher.launchJVM(userArgs.toArray(new String[0]));
        Logger.appendToLog("Java Exit code: " + exitCode);
        if (exitCode != 0) {
            LifecycleAwareAlertDialog.DialogCreator dialogCreator = (dialog, builder)->
                    builder.setMessage(activity.getString(R.string.mcn_exit_title, exitCode))
                    .setPositiveButton(R.string.main_share_logs, (dialogInterface, which)-> shareLog(activity));

            LifecycleAwareAlertDialog.haltOnDialog(activity.getLifecycle(), activity, dialogCreator);
        }
        Tools.fullyExit();
    }

    /**
     *  Gives an argument list filled with both the user args
     *  and the auto-generated ones (eg. the window resolution).
     * @param ctx The application context
     * @return A list filled with args.
     */
    public static List<String> getJavaArgs(Context ctx, String runtimeHome, String userArgumentsString) {
        return getJavaArgs(ctx, runtimeHome, userArgumentsString, true);
    }

    public static List<String> getJavaArgs(Context ctx, String runtimeHome, String userArgumentsString, boolean includeGameArguments) {
        List<String> userArguments = parseJavaArguments(userArgumentsString);
        String resolvFile;
        resolvFile = new File(Tools.DIR_DATA,"resolv.conf").getAbsolutePath();

        ArrayList<String> overridableArguments = new ArrayList<>(Arrays.asList(
                "-Djava.home=" + runtimeHome,
                "-Djava.io.tmpdir=" + Tools.DIR_CACHE.getAbsolutePath(),
                "-Djna.boot.library.path=" + NATIVE_LIB_DIR,
                "-Duser.home=" + Tools.DIR_GAME_HOME,
                "-Duser.language=" + System.getProperty("user.language"),
                "-Dos.name=Linux",
                "-Dos.version=Android-" + Build.VERSION.RELEASE,
                "-Donyx.path.minecraft=" + Tools.DIR_GAME_NEW,
                "-Donyx.path.private.account=" + Tools.DIR_ACCOUNT_NEW,
                "-Duser.timezone=" + TimeZone.getDefault().getID(),
                "-Dext.net.resolvPath=" +resolvFile,
                "-Dlog4j2.formatMsgNoLookups=true", //Log4j RCE mitigation
                "-Djdk.lang.Process.launchMechanism=FORK" // Default is POSIX_SPAWN which requires starting jspawnhelper, which doesn't work on Android
        ));
        if(includeGameArguments) {
            overridableArguments.addAll(Arrays.asList(
                    "-Dorg.lwjgl.vulkan.libname=libvulkan.so",
                    //LWJGL 3 DEBUG FLAGS
                    //"-Dorg.lwjgl.util.Debug=true",
                    //"-Dorg.lwjgl.util.DebugFunctions=true",
                    //"-Dorg.lwjgl.util.DebugLoader=true",
                    // GLFW Stub width height
                    "-Dglfwstub.windowWidth=" + Tools.getDisplayFriendlyRes(currentDisplayMetrics.widthPixels, LauncherPreferences.PREF_SCALE_FACTOR),
                    "-Dglfwstub.windowHeight=" + Tools.getDisplayFriendlyRes(currentDisplayMetrics.heightPixels, LauncherPreferences.PREF_SCALE_FACTOR),
                    "-Dglfwstub.initEgl=false",
                    "-Dsodium.checks.issue2561=false",
                    "-Dsodium.mixins.features.render.frapi=false", // Disable incompatible Sodium FRAPI item render state mixin on MC 1.21.5+

                    "-Dnet.minecraft.clientmodname=" + Tools.APP_NAME,
                    "-Dfml.earlyprogresswindow=false", //Forge 1.14+ workaround
                    "-Dloader.disable_forked_guis=true"
            ));
        }
        if(LauncherPreferences.PREF_ARC_CAPES) {
            overridableArguments.add("-javaagent:"+new File(Tools.DIR_DATA,"arc_dns_injector/arc_dns_injector.jar").getAbsolutePath()+"=23.95.137.176");
        }
        List<String> additionalArguments = new ArrayList<>();
        for(String arg : overridableArguments) {
            String strippedArg = arg.substring(0,arg.indexOf('='));
            boolean add = true;
            for(String uarg : userArguments) {
                if(uarg.startsWith(strippedArg)) {
                    add = false;
                    break;
                }
            }
            if(add)
                additionalArguments.add(arg);
            else
                Log.i("ArgProcessor","Arg skipped: "+arg);
        }

        //Add all the arguments
        userArguments.addAll(additionalArguments);
        return userArguments;
    }

    /**
     * Parse and separate java arguments in a user friendly fashion
     * It supports multi line and absence of spaces between arguments
     * The function also supports auto-removal of improper arguments, although it may miss some.
     *
     * @param args The un-parsed argument list.
     * @return Parsed args as an ArrayList
     */
    public static ArrayList<String> parseJavaArguments(String args){
        ArrayList<String> parsedArguments = new ArrayList<>(0);
        args = args.trim().replace(" ", "");
        //For each prefixes, we separate args.
        String[] separators = new String[]{"-XX:-","-XX:+", "-XX:","--", "-D", "-X", "-javaagent:", "-verbose"};
        for(String prefix : separators){
            while (true){
                int start = args.indexOf(prefix);
                if(start == -1) break;
                //Get the end of the current argument by checking the nearest separator
                int end = -1;
                for(String separator: separators){
                    int tempEnd = args.indexOf(separator, start + prefix.length());
                    if(tempEnd == -1) continue;
                    if(end == -1){
                        end = tempEnd;
                        continue;
                    }
                    end = Math.min(end, tempEnd);
                }
                //Fallback
                if(end == -1) end = args.length();

                //Extract it
                String parsedSubString = args.substring(start, end);
                args = args.replace(parsedSubString, "");

                //Check if two args aren't bundled together by mistake
                if(parsedSubString.indexOf('=') == parsedSubString.lastIndexOf('=')) {
                    int arraySize = parsedArguments.size();
                    if(arraySize > 0){
                        String lastString = parsedArguments.get(arraySize - 1);
                        // Looking for list elements
                        if(lastString.charAt(lastString.length() - 1) == ',' ||
                                parsedSubString.contains(",")){
                            parsedArguments.set(arraySize - 1, lastString + parsedSubString);
                            continue;
                        }
                    }
                    parsedArguments.add(parsedSubString);
                }
                else Log.w("JAVA ARGS PARSER", "Removed improper arguments: " + parsedSubString);
            }
        }
        return parsedArguments;
    }

    /**
     * Open the render library in accordance to the settings.
     * It will fallback if it fails to load the library.
     * @return The name of the loaded library
     */
    public static String loadGraphicsLibrary(){
        if(LOCAL_RENDERER == null) return null;
        String renderLibrary;
        switch (LOCAL_RENDERER){
            case "opengles2":
            case "opengles2_5":
            case "opengles3":
                renderLibrary = "libgl4es_114.so"; break;
            case "vulkan_zink": renderLibrary = "libOSMesa.so"; break;
            case "opengles3_ltw" : renderLibrary = "libltw.so"; break;
            default:
                Log.w("RENDER_LIBRARY", "No renderer selected, defaulting to opengles2");
                renderLibrary = "libgl4es_114.so";
                break;
        }

        if (!dlopen(renderLibrary) && !dlopen(findInLdLibPath(renderLibrary))) {
            Log.e("RENDER_LIBRARY","Failed to load renderer " + renderLibrary + ". Falling back to GL4ES 1.1.4");
            LOCAL_RENDERER = "opengles2";
            renderLibrary = "libgl4es_114.so";
            dlopen(NATIVE_LIB_DIR + "/libgl4es_114.so");
        }
        return renderLibrary;
    }

    /**
     * Remove the argument from the list, if it exists
     * If the argument exists multiple times, they will all be removed.
     * @param argList The argument list to purge
     * @param argStart The argument to purge from the list.
     */
    private static void purgeArg(List<String> argList, String argStart) {
        Iterator<String> args = argList.iterator();
        while(args.hasNext()) {
            String arg = args.next();
            if(arg.startsWith(argStart)) args.remove();
        }
    }
    private static final int EGL_OPENGL_ES_BIT = 0x0001;
    private static final int EGL_OPENGL_ES2_BIT = 0x0004;
    private static final int EGL_OPENGL_ES3_BIT_KHR = 0x0040;
    @SuppressWarnings("SameParameterValue")
    private static boolean hasExtension(String extensions, String name) {
        int start = extensions.indexOf(name);
        while (start >= 0) {
            // check that we didn't find a prefix of a longer extension name
            int end = start + name.length();
            if (end == extensions.length() || extensions.charAt(end) == ' ') {
                return true;
            }
            start = extensions.indexOf(name, end);
        }
        return false;
    }

    public static int getDetectedVersion() {
        return GLInfoUtils.getGlInfo().glesMajorVersion;
    }
    public static native int chdir(String path);
    public static native boolean dlopen(String libPath);
    public static native void setLdLibraryPath(String ldLibraryPath);
    public static native void setupBridgeWindow(Object surface);
    public static native void releaseBridgeWindow();
    public static native void initializeHooks();
    public static native void setupExitMethod(Context context);
    // Obtain AWT screen pixels to render on Android SurfaceView
    public static native int[] renderAWTScreenFrame(/* Object canvas, int width, int height */);
    static {
        System.loadLibrary("c++_shared");
        System.loadLibrary("exithook");
        System.loadLibrary("pojavexec");
        System.loadLibrary("pojavexec_awt");
    }
}
