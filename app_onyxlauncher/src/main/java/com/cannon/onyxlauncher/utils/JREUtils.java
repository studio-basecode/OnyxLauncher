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
        Map<String, String> envMap = new ArrayMap<>();
        envMap.put("ONYX_NATIVEDIR", NATIVE_LIB_DIR);
        // Alias: prebuilt MC mods/libraries (e.g. Sodium) may still check for POJAV_NATIVEDIR
        envMap.put("POJAV_NATIVEDIR", NATIVE_LIB_DIR);
        envMap.put("JAVA_HOME", jreHome);
        envMap.put("HOME", Tools.DIR_GAME_HOME);
        envMap.put("TMPDIR", Tools.DIR_CACHE.getAbsolutePath());
        envMap.put("LIBGL_MIPMAP", "0"); // Disable gl4es-side mipmapping to prevent texture pack (TXT) crashes on Adreno 750

        // Prevent OptiFine (and other error-reporting stuff in Minecraft) from balooning the log
        envMap.put("LIBGL_NOERROR", "1");
        envMap.put("LIBGL_CUSTOMVERSION", "3.3.0"); // Custom GL version for Iris shader compatibility

        // On certain GLES drivers, overloading default functions shader hack fails, so disable it
        envMap.put("LIBGL_NOINTOVLHACK", "1");

        // Fix white color on banner and sheep, since GL4ES 1.1.5
        envMap.put("LIBGL_NORMALIZE", "1");


        if(PREF_DUMP_SHADERS)
            envMap.put("LIBGL_VGPU_DUMP", "1");
        if(PREF_VSYNC_IN_ZINK) {
            envMap.put("ONYX_VSYNC_IN_ZINK", "1");
            envMap.put("POJAV_VSYNC_IN_ZINK", "1"); // alias for prebuilt libs
        }
        if(Tools.deviceHasHangingLinker()) {
            envMap.put("ONYX_EMUI_ITERATOR_MITIGATE", "1");
            envMap.put("POJAV_EMUI_ITERATOR_MITIGATE", "1"); // alias for prebuilt libs
        }


        // The OPEN GL version is changed according
        String selectedOpenGlVersion = (String) ExtraCore.getValue(ExtraConstants.OPEN_GL_VERSION);
        envMap.put("LIBGL_ES", selectedOpenGlVersion);
        if ("3".equals(selectedOpenGlVersion)) {
            envMap.put("LIBGL_GL", "33");
        }

        envMap.put("FORCE_VSYNC", String.valueOf(LauncherPreferences.PREF_FORCE_VSYNC));

        envMap.put("MESA_GLSL_CACHE_DIR", Tools.DIR_CACHE.getAbsolutePath());
        envMap.put("force_glsl_extensions_warn", "true");
        envMap.put("allow_higher_compat_version", "true");
        envMap.put("allow_glsl_extension_directive_midshader", "true");
        if ("vulkan_zink".equals(LOCAL_RENDERER)) {
            envMap.put("MESA_LOADER_DRIVER_OVERRIDE", "zink");
            envMap.put("VTEST_SOCKET_NAME", new File(Tools.DIR_CACHE, ".virgl_test").getAbsolutePath());
        }

        envMap.put("LD_LIBRARY_PATH", LD_LIBRARY_PATH);
        envMap.put("PATH", jreHome + "/bin:" + Os.getenv("PATH"));
        if(FFmpegPlugin.isAvailable) {
            envMap.put("ONYX_FFMPEG_PATH", FFmpegPlugin.executablePath);
            envMap.put("POJAV_FFMPEG_PATH", FFmpegPlugin.executablePath); // alias for prebuilt libs
        }

        if(LOCAL_RENDERER != null) {
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
            envMap.put("POJAV_RENDERER", effectiveRenderer); // alias for prebuilt MC mods (e.g. Sodium/Iris)
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
        if(LauncherPreferences.PREF_BIG_CORE_AFFINITY) {
            envMap.put("ONYX_BIG_CORE_AFFINITY", "1");
            envMap.put("POJAV_BIG_CORE_AFFINITY", "1"); // alias for prebuilt libs
        }
        envMap.put("AWTSTUB_WIDTH", Integer.toString(CallbackBridge.windowWidth > 0 ? CallbackBridge.windowWidth : CallbackBridge.physicalWidth));
        envMap.put("AWTSTUB_HEIGHT", Integer.toString(CallbackBridge.windowHeight > 0 ? CallbackBridge.windowHeight : CallbackBridge.physicalHeight));

        GLInfoUtils.GLInfo info = GLInfoUtils.getGlInfo();
        if(!envMap.containsKey("LIBGL_ES") && LOCAL_RENDERER != null) {
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

        if(info.isAdreno() && !PREF_ZINK_PREFER_SYSTEM_DRIVER) {
            envMap.put("ONYX_LOAD_TURNIP", "1");
            envMap.put("POJAV_LOAD_TURNIP", "1"); // alias for prebuilt libs
        }

        readCustomEnv(envMap); // Must be last so it overrides anything the user sets for obvious reasons.

        for (Map.Entry<String, String> env : envMap.entrySet()) {
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
    public static void launchJavaVM(final AppCompatActivity activity, final Runtime runtime, File gameDirectory, final List<String> JVMArgs, final String userArgsString) throws Throwable {
        String runtimeHome = MultiRTUtils.getRuntimeHome(runtime.name).getAbsolutePath();

        JREUtils.relocateLibPath(runtime, runtimeHome);

        setJavaEnvironment(activity, runtimeHome);

        final String graphicsLib = loadGraphicsLibrary();
        List<String> userArgs = getJavaArgs(activity, runtimeHome, userArgsString);

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
        userArgs.add("-Xms" + LauncherPreferences.PREF_RAM_ALLOCATION + "M");
        userArgs.add("-Xmx" + LauncherPreferences.PREF_RAM_ALLOCATION + "M");
        if(LOCAL_RENDERER != null) userArgs.add("-Dorg.lwjgl.opengl.libname=" + graphicsLib);

        // Force LWJGL to use the Freetype library intended for it, instead of using the one
        // that we ship with Java (since it may be older than what's needed)
        userArgs.add("-Dorg.lwjgl.freetype.libname="+ NATIVE_LIB_DIR+"/libfreetype.so");

        // Some phones are not using the right number of cores, fix that
        userArgs.add("-XX:ActiveProcessorCount=" + java.lang.Runtime.getRuntime().availableProcessors());

        userArgs.addAll(JVMArgs);
        activity.runOnUiThread(() -> Toast.makeText(activity, activity.getString(R.string.autoram_info_msg,LauncherPreferences.PREF_RAM_ALLOCATION), Toast.LENGTH_SHORT).show());
        System.out.println(JVMArgs);

        initJavaRuntime(runtimeHome);
        JREUtils.setupExitMethod(activity.getApplication());
        JREUtils.initializeHooks();
        chdir(gameDirectory == null ? Tools.DIR_GAME_NEW : gameDirectory.getAbsolutePath());
        userArgs.add(0,"java"); //argv[0] is the program name according to C standard.

        // Clean up any Pojav / Onyx environment variables that might trigger Sodium's PojavLauncher block/crash.
        try {
            android.system.Os.unsetenv("POJAV_RENDERER");
            android.system.Os.unsetenv("POJAV_NATIVEDIR");
            android.system.Os.unsetenv("POJAV_VSYNC_IN_ZINK");
            android.system.Os.unsetenv("POJAV_EMUI_ITERATOR_MITIGATE");
            android.system.Os.unsetenv("POJAV_FFMPEG_PATH");
            android.system.Os.unsetenv("POJAV_BIG_CORE_AFFINITY");
            android.system.Os.unsetenv("POJAV_LOAD_TURNIP");
            android.system.Os.unsetenv("POJAVEXEC_EGL");

            android.system.Os.unsetenv("ONYX_RENDERER");
            android.system.Os.unsetenv("ONYX_NATIVEDIR");
            android.system.Os.unsetenv("ONYX_VSYNC_IN_ZINK");
            android.system.Os.unsetenv("ONYX_EMUI_ITERATOR_MITIGATE");
            android.system.Os.unsetenv("ONYX_FFMPEG_PATH");
            android.system.Os.unsetenv("ONYX_BIG_CORE_AFFINITY");
            android.system.Os.unsetenv("ONYX_LOAD_TURNIP");
            android.system.Os.unsetenv("ONYXEXEC_EGL");
        } catch (Exception e) {
            Log.e("JREUtils", "Failed to unset environment variables", e);
        }

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

                "-Dorg.lwjgl.vulkan.libname=libvulkan.so",
                //LWJGL 3 DEBUG FLAGS
                //"-Dorg.lwjgl.util.Debug=true",
                //"-Dorg.lwjgl.util.DebugFunctions=true",
                //"-Dorg.lwjgl.util.DebugLoader=true",
                // GLFW Stub width height
                "-Dglfwstub.windowWidth=" + Tools.getDisplayFriendlyRes(currentDisplayMetrics.widthPixels, LauncherPreferences.PREF_SCALE_FACTOR),
                "-Dglfwstub.windowHeight=" + Tools.getDisplayFriendlyRes(currentDisplayMetrics.heightPixels, LauncherPreferences.PREF_SCALE_FACTOR),
                "-Dglfwstub.initEgl=false",
                "-Dext.net.resolvPath=" +resolvFile,
                "-Dlog4j2.formatMsgNoLookups=true", //Log4j RCE mitigation
                "-Dsodium.checks.issue2561=false",
                "-Dsodium.mixins.features.render.frapi=false", // Disable incompatible Sodium FRAPI item render state mixin on MC 1.21.5+

                "-Dnet.minecraft.clientmodname=" + Tools.APP_NAME,
                "-Dfml.earlyprogresswindow=false", //Forge 1.14+ workaround
                "-Dloader.disable_forked_guis=true",
                "-Djdk.lang.Process.launchMechanism=FORK" // Default is POSIX_SPAWN which requires starting jspawnhelper, which doesn't work on Android
        ));
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
