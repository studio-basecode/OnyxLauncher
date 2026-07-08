package com.cannon.onyxlauncher;

import android.content.Context;
import android.util.Log;

import com.cannon.onyxlauncher.prefs.LauncherPreferences;
import com.cannon.onyxlauncher.value.launcherprofiles.MinecraftProfile;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public final class MobileProfileOptimizer {
    private static final String TAG = "OnyxProfileOptimizer";
    private static final String ONYX_MARKER = "# Onyx mobile profile";
    private static final String DISABLED_BY_ONYX = ".disabled-by-onyx-mobile";

    private MobileProfileOptimizer() {}

    public static boolean apply(MinecraftProfile profile) {
        return apply(null, profile);
    }

    public static boolean apply(Context context, MinecraftProfile profile) {
        return apply(context, profile, null);
    }

    public static boolean apply(Context context, MinecraftProfile profile, File gameDirectory) {
        if (profile == null) return false;

        String versionId = safeLower(profile.lastVersionId);
        String gameDir = safeLower(profile.gameDir);
        boolean customInstance = gameDir.contains("custom_instances");
        boolean forgeLike = versionId.contains("forge") || versionId.contains("neoforge");
        int modCount = countProfileMods(profile, gameDirectory);
        boolean modded = customInstance || forgeLike || versionId.contains("fabric") || versionId.contains("quilt") || modCount > 0;
        if (!modded) return false;

        boolean changed = false;
        int deviceRam = context != null ? Tools.getTotalDeviceMemory(context) : 0;
        int freeRam = context != null ? Tools.getFreeDeviceMemory(context) : 0;
        boolean heavy = forgeLike || customInstance || modCount >= 80;
        boolean veryHeavy = modCount >= 140;
        boolean extreme = modCount >= 200;
        int targetRam = chooseRamMb(deviceRam, freeRam, heavy, veryHeavy, extreme);
        int targetScale = chooseResolutionScale(deviceRam, freeRam, heavy, veryHeavy, extreme);
        int requiredJava = requiredJavaFor(versionId);

        String targetRenderer = extreme ? "opengles2" : "vulkan_zink";
        if (isBlank(profile.pojavRendererName) || (extreme && !targetRenderer.equals(profile.pojavRendererName))) {
            profile.pojavRendererName = targetRenderer;
            changed = true;
        }

        String targetRuntime = runtimeForJava(requiredJava);
        if (shouldUpdateRuntime(profile.javaDir, requiredJava)) {
            profile.javaDir = Tools.LAUNCHERPROFILES_RTPREFIX + targetRuntime;
            changed = true;
        }

        if (profile.ramAllocation == null ||
                profile.ramAllocation < Math.min(3072, targetRam) ||
                profile.ramAllocation > targetRam ||
                isTooHighForDevice(profile.ramAllocation, deviceRam) ||
                isTooHighForFreeMemory(profile.ramAllocation, freeRam)) {
            profile.ramAllocation = targetRam;
            changed = true;
        }

        if (profile.resolutionScale == null || profile.resolutionScale > targetScale) {
            profile.resolutionScale = targetScale;
            changed = true;
        }

        if (profile.alternateSurface == null || profile.alternateSurface) {
            profile.alternateSurface = false;
            changed = true;
        }

        if (profile.forceVsync == null || profile.forceVsync) {
            profile.forceVsync = false;
            changed = true;
        }

        String targetJavaArgs = mergeMobileJavaArgs(profile.javaArgs, heavy, extreme, targetRam);
        if (!equals(profile.javaArgs, targetJavaArgs)) {
            profile.javaArgs = targetJavaArgs;
            changed = true;
        }

        if (changed) {
            Log.i(TAG, "Applied mobile defaults to " + profile.name + ": ram=" + profile.ramAllocation
                    + " scale=" + profile.resolutionScale + " renderer=" + profile.pojavRendererName
                    + " java=" + profile.javaDir + " mods=" + modCount);
        }
        return changed;
    }

    public static void applyGameDirectoryOptimizations(File gameDirectory, MinecraftProfile profile) {
        if (gameDirectory == null || profile == null) return;

        String versionId = safeLower(profile.lastVersionId);
        String gameDir = safeLower(profile.gameDir);
        boolean customInstance = gameDir.contains("custom_instances");
        boolean forgeLike = versionId.contains("forge") || versionId.contains("neoforge");
        boolean modded = customInstance || forgeLike || versionId.contains("fabric") || versionId.contains("quilt");
        if (!modded) return;

        File modsDir = new File(gameDirectory, "mods");
        int modCount = countModJars(modsDir);
        boolean heavy = forgeLike || customInstance || modCount >= 80;
        boolean veryHeavy = modCount >= 140;
        boolean extreme = modCount >= 200;

        try {
            tuneMinecraftOptions(gameDirectory, veryHeavy, extreme);
            File configDir = new File(gameDirectory, "config");
            if (!configDir.isDirectory() && !configDir.mkdirs()) {
                Log.w(TAG, "Could not create config directory " + configDir);
                return;
            }
            tuneIris(configDir, heavy);
            tuneSodium(configDir, extreme);
            tuneFlywheel(configDir, extreme);
            tuneEveryCompat(configDir, extreme);
            tuneFml(configDir, forgeLike, extreme);
            tuneNeoForgeClient(configDir);
            tuneFancyMenu(configDir, extreme);
            tuneXaero(configDir, extreme);
            tuneModernFix(configDir);
            tuneC2ME(configDir, veryHeavy, extreme);
            tuneBadOptimizations(configDir);
            applyExtremeMobileModSet(modsDir, extreme);
            Log.i(TAG, "Applied mobile instance config to " + gameDirectory.getName()
                    + " mods=" + modCount + " heavy=" + heavy + " extreme=" + extreme);
        } catch (IOException e) {
            Log.w(TAG, "Failed to apply mobile instance config to " + gameDirectory, e);
        }
    }

    public static int recommendedInitialHeapMb(int ramAllocationMb) {
        if (ramAllocationMb <= 1536) return 384;
        if (ramAllocationMb <= 2304) return 512;
        if (ramAllocationMb <= 3072) return 768;
        return 1024;
    }

    public static int recommendedActiveProcessors(int availableProcessors, int ramAllocationMb) {
        int available = Math.max(1, availableProcessors);
        if (ramAllocationMb <= 1792) return Math.min(2, available);
        if (ramAllocationMb <= 2304) return Math.min(2, available);
        if (ramAllocationMb <= 3072) return Math.min(2, available);
        return Math.min(6, available);
    }

    public static boolean isExtremePack(File gameDirectory) {
        return countModJars(new File(gameDirectory, "mods")) >= 200;
    }

    private static int chooseRamMb(int deviceRam, int freeRam, boolean heavy, boolean veryHeavy, boolean extreme) {
        int preferred;
        if (deviceRam >= 10240) {
            preferred = heavy ? 3072 : 2816;
        } else if (deviceRam >= 7680) {
            preferred = heavy ? 2560 : 2304;
        } else if (deviceRam >= 6144) {
            preferred = heavy ? 2304 : 2048;
        } else if (deviceRam >= 4096) {
            preferred = heavy ? 1792 : 1536;
        } else if (deviceRam > 0) {
            preferred = heavy ? 1280 : 1536;
        } else {
            preferred = heavy ? 1792 : Math.max(2048, Math.min(3072, LauncherPreferences.PREF_RAM_ALLOCATION));
        }

        if (deviceRam > 0) {
            int reserve = heavy ? 3072 : 2048;
            int cap = Math.max(heavy ? 1280 : 1536, Math.min(4096, deviceRam - reserve));
            preferred = Math.min(preferred, cap);
        }
        if (freeRam > 0) {
            int reserve = heavy ? 1024 : 512;
            int freeCap = Math.max(heavy ? 1280 : 1024, roundDownToStep(freeRam - reserve, 256));
            preferred = Math.min(preferred, freeCap);
            if (heavy && !extreme && freeRam < 3072) {
                preferred = Math.min(preferred, 1536);
            }
        }
        if (extreme) {
            if (deviceRam >= 10240) {
                preferred = 2560;
            } else if (deviceRam >= 8192) {
                preferred = 1792;
            } else {
                preferred = Math.min(preferred, 1536);
            }
        } else if (veryHeavy) {
            preferred = Math.min(preferred, 1792);
        }
        return Math.max(heavy ? 1280 : 1024, preferred);
    }

    private static int chooseResolutionScale(int deviceRam, int freeRam, boolean heavy, boolean veryHeavy, boolean extreme) {
        if (!heavy) return 90;
        if (extreme) return 55;
        if (veryHeavy) return 60;
        if (freeRam > 0 && freeRam < 3072) return 60;
        if (deviceRam > 0 && deviceRam < 6144) return 70;
        if (deviceRam > 0 && deviceRam < 8192) return 75;
        return 70;
    }

    private static boolean isTooHighForDevice(int ramAllocation, int deviceRam) {
        if (deviceRam <= 0) return false;
        int cap = Math.max(1280, Math.min(4096, deviceRam - 3072));
        return ramAllocation > cap;
    }

    private static boolean isTooHighForFreeMemory(int ramAllocation, int freeRam) {
        if (freeRam <= 0) return false;
        int cap = Math.max(1280, roundDownToStep(freeRam - 1024, 256));
        return ramAllocation > cap;
    }

    private static String mergeMobileJavaArgs(String currentArgs, boolean heavy, boolean extreme, int targetRam) {
        String args = currentArgs == null ? "" : currentArgs.trim();
        LinkedHashMap<String, String> targetArgs = new LinkedHashMap<>();
        targetArgs.put("-XX:+UseStringDeduplication", "-XX:+UseStringDeduplication");
        targetArgs.put("-XX:+ParallelRefProcEnabled", "-XX:+ParallelRefProcEnabled");
        targetArgs.put("-XX:MaxGCPauseMillis=", "-XX:MaxGCPauseMillis=200");
        targetArgs.put("-XX:ConcGCThreads=", "-XX:ConcGCThreads=1");
        targetArgs.put("-XX:ParallelGCThreads=", targetRam <= 1792
                ? "-XX:ParallelGCThreads=1"
                : "-XX:ParallelGCThreads=2");
        targetArgs.put("-XX:CICompilerCount=", "-XX:CICompilerCount=2");
        targetArgs.put("-XX:ReservedCodeCacheSize=", heavy || targetRam <= 1792
                ? "-XX:ReservedCodeCacheSize=96M"
                : "-XX:ReservedCodeCacheSize=128M");
        targetArgs.put("-Djava.util.concurrent.ForkJoinPool.common.parallelism=", extreme || targetRam <= 1792
                ? "-Djava.util.concurrent.ForkJoinPool.common.parallelism=1"
                : "-Djava.util.concurrent.ForkJoinPool.common.parallelism=2");
        targetArgs.put("-Dio.netty.allocator.maxOrder=", "-Dio.netty.allocator.maxOrder=3");
        targetArgs.put("-Dforge.logging.mojang.level=", "-Dforge.logging.mojang.level=warn");
        targetArgs.put("-Dlog4j2.disableJmx=", "-Dlog4j2.disableJmx=true");

        for (String prefix : targetArgs.keySet()) {
            args = removeArg(args, prefix);
        }
        for (Map.Entry<String, String> entry : targetArgs.entrySet()) {
            args = appendArg(args, entry.getValue());
        }
        return args;
    }

    private static void tuneMinecraftOptions(File gameDirectory, boolean veryHeavy, boolean extreme) throws IOException {
        File options = new File(gameDirectory, "options.txt");
        String content = options.isFile() ? Tools.read(options) : "";
        content = setColonOption(content, "renderDistance", extreme ? "3" : veryHeavy ? "4" : "5");
        content = setColonOption(content, "simulationDistance", "5");
        content = setColonOption(content, "entityDistanceScaling", "0.5");
        content = setColonOption(content, "graphicsMode", "0");
        content = setColonOption(content, "clouds", "false");
        content = setColonOption(content, "particles", "2");
        content = setColonOption(content, "mipmapLevels", "0");
        content = setColonOption(content, "biomeBlendRadius", "0");
        content = setColonOption(content, "maxFps", extreme ? "30" : "45");
        writeIfDifferent(options, content);
    }

    private static void tuneIris(File configDir, boolean heavy) throws IOException {
        if (!heavy) return;
        File iris = new File(configDir, "iris.properties");
        String content = iris.isFile() ? Tools.read(iris) : "";
        content = setEqualsOption(content, "enableShaders", "false");
        content = setEqualsOption(content, "shaderPack", "off");
        writeIfDifferent(iris, content);
    }

    private static void tuneSodium(File configDir, boolean extreme) throws IOException {
        File sodium = new File(configDir, "sodium-options.json");
        if (!sodium.isFile()) return;
        try {
            JSONObject root = new JSONObject(Tools.read(sodium));
            JSONObject quality = getOrCreateObject(root, "quality");
            quality.put("weather_quality", "FAST");
            quality.put("leaves_quality", "FAST");
            quality.put("enable_vignette", false);

            JSONObject advanced = getOrCreateObject(root, "advanced");
            advanced.put("use_advanced_staging_buffers", false);
            advanced.put("cpu_render_ahead_limit", extreme ? 1 : 2);

            JSONObject performance = getOrCreateObject(root, "performance");
            performance.put("chunk_builder_threads", extreme ? 1 : 2);
            performance.put("always_defer_chunk_updates_v2", true);
            performance.put("animate_only_visible_textures", true);
            performance.put("use_entity_culling", true);
            performance.put("use_fog_occlusion", true);
            performance.put("use_block_face_culling", true);

            JSONObject debug = getOrCreateObject(root, "debug");
            debug.put("terrain_sorting_enabled", !extreme);
            writeIfDifferent(sodium, root.toString(2) + "\n");
        } catch (JSONException e) {
            Log.w(TAG, "Could not tune Sodium options " + sodium, e);
        }
    }

    private static void tuneFlywheel(File configDir, boolean extreme) throws IOException {
        File flywheel = new File(configDir, "flywheel-client.toml");
        if (!flywheel.isFile()) return;
        String content = Tools.read(flywheel);
        if (extreme) {
            content = setTomlStringOption(content, "backend", "flywheel:instancing");
            content = setTomlOption(content, "workerThreads", "0");
            content = setTomlStringOption(content, "lightSmoothness", "FLAT");
        } else {
            content = setTomlOption(content, "workerThreads", "1");
        }
        content = setTomlOption(content, "limitUpdates", "true");
        writeIfDifferent(flywheel, content);
    }

    private static void tuneEveryCompat(File configDir, boolean extreme) throws IOException {
        if (!extreme) return;

        File client = new File(configDir, "everycomp-client.toml");
        if (client.isFile()) {
            String content = Tools.read(client);
            content = setTomlStringOption(content, "dynamic_assets_generation_mode", "CACHED_ZIPPED");
            writeIfDifferent(client, content);
        }

        File common = new File(configDir, "everycomp-common.toml");
        if (common.isFile()) {
            String content = Tools.read(common);
            content = setTomlStringOption(content, "server_assets_generation_mode", "CACHED_ZIPPED");
            content = setTomlOption(content, "generate_blocktype_tags", "false");
            content = setTomlOption(content, "mod_version_check_packet", "false");
            writeIfDifferent(common, content);
        }

        File hazardous = new File(configDir, "everycomp-hazardous.toml");
        String content = ONYX_MARKER + "\n"
                + "[woodtype]\n"
                + "blacklist = []\n\n"
                + "[leavestype]\n"
                + "blacklist = []\n\n"
                + "[block]\n"
                + "blacklist = []\n\n"
                + "[entryset]\n"
                + "blacklist = []\n\n"
                + "[module]\n"
                + "blacklist = [\"chipped\", \"mcwbridges\", \"mcwfences\", \"mcwwindows\", \"mcwroofs\", \"mcwstairs\", \"mcwpaths\", \"mcwdoors\", \"mcwlights\", \"mcwtrpdoors\", \"create\", \"another_furniture\", \"decorative_blocks\", \"farmersdelight\", \"regions_unexplored\", \"shutter\", \"candlelight\", \"tradeworks\"]\n\n"
                + "[other]\n"
                + "include_all_wood_modules = false\n"
                + "enable_framed_blocks_blacklist = false\n";
        if (writeIfDifferent(hazardous, content)) {
            clearEveryCompatCache(configDir.getParentFile());
        }
    }

    private static void tuneFml(File configDir, boolean forgeLike, boolean extreme) throws IOException {
        File fml = new File(configDir, "fml.toml");
        if (!forgeLike && !fml.isFile()) return;
        String content = fml.isFile() ? Tools.read(fml) : ONYX_MARKER + "\n";
        content = setTomlOption(content, "versionCheck", "false");
        content = setTomlOption(content, "disableConfigWatcher", "true");
        content = setTomlOption(content, "maxThreads", extreme ? "1" : "2");
        writeIfDifferent(fml, content);
    }

    private static void tuneNeoForgeClient(File configDir) throws IOException {
        File neoForgeClient = new File(configDir, "neoforge-client.toml");
        if (!neoForgeClient.isFile()) return;
        String content = Tools.read(neoForgeClient);
        content = setTomlOption(content, "showLoadWarnings", "false");
        content = setTomlOption(content, "logUntranslatedConfigurationWarnings", "false");
        content = setTomlOption(content, "useCombinedDepthStencilAttachment", "false");
        writeIfDifferent(neoForgeClient, content);
    }

    private static void tuneFancyMenu(File configDir, boolean extreme) throws IOException {
        if (!extreme) return;
        File options = new File(new File(configDir, "fancymenu"), "options.txt");
        if (!options.isFile()) return;
        String content = Tools.read(options);
        content = setFancyMenuOption(content, "B:enable_ui_animations", "false");
        content = setFancyMenuOption(content, "B:play_ui_click_sounds", "false");
        content = setFancyMenuOption(content, "B:show_multiplayer_screen_server_icons", "false");
        content = setFancyMenuOption(content, "B:show_singleplayer_screen_world_icons", "false");
        content = setFancyMenuOption(content, "B:enable_element_rotation_controls", "false");
        content = setFancyMenuOption(content, "B:enable_element_tilting_controls", "false");
        content = setFancyMenuOption(content, "B:pip_window_docking", "false");
        writeIfDifferent(options, content);
    }

    private static void tuneXaero(File configDir, boolean extreme) throws IOException {
        File xaero = new File(configDir, "xaero");
        tuneXaeroClientConfig(new File(new File(xaero, "minimap"), "client.cfg"), false);
        tuneXaeroClientConfig(new File(new File(xaero, "world-map"), "client.cfg"), true);
        if (!extreme) return;
        File worldMapProfile = new File(new File(new File(xaero, "world-map"), "profiles"), "default.cfg");
        if (worldMapProfile.isFile()) {
            String content = Tools.read(worldMapProfile);
            content = setEqualsOption(content, "update_chunks", "false");
            content = setEqualsOption(content, "map_writing_distance", "2");
            writeIfDifferent(worldMapProfile, content);
        }
    }

    private static void tuneXaeroClientConfig(File file, boolean worldMap) throws IOException {
        if (!file.isFile()) return;
        String content = Tools.read(file);
        content = setEqualsOption(content, "update_notifications", "false");
        if (worldMap) {
            content = setEqualsOption(content, "max_loaded_regions", "64");
        }
        writeIfDifferent(file, content);
    }

    private static void applyExtremeMobileModSet(File modsDir, boolean extreme) throws IOException {
        if (!extreme || modsDir == null || !modsDir.isDirectory()) return;
        File[] mods = modsDir.listFiles(file -> file.isFile() && file.getName().toLowerCase(Locale.ROOT).endsWith(".jar"));
        if (mods == null) return;
        StringBuilder manifest = new StringBuilder(ONYX_MARKER).append('\n');
        for (File mod : mods) {
            String name = mod.getName();
            if (!shouldDisableForExtremeMobile(name)) continue;
            File disabled = new File(modsDir, name + DISABLED_BY_ONYX);
            if (disabled.exists()) {
                manifest.append(name).append('\n');
                continue;
            }
            if (mod.renameTo(disabled)) {
                manifest.append(name).append('\n');
                Log.i(TAG, "Disabled optional extreme-pack client mod for mobile: " + name);
            } else {
                Log.w(TAG, "Could not disable optional extreme-pack client mod " + name);
            }
        }
        if (manifest.length() > ONYX_MARKER.length() + 1) {
            writeIfDifferent(new File(modsDir, "onyx-mobile-disabled-mods.txt"), manifest.toString());
        }
    }

    private static boolean shouldDisableForExtremeMobile(String fileName) {
        String name = safeLower(fileName);
        return name.contains("fancymenu")
                || name.contains("xaeroplus")
                || name.contains("xaerominimap")
                || name.contains("xaeroworldmap")
                || name.contains("xaerotrainmap")
                || name.contains("iris-")
                || name.contains("sodiumextras")
                || name.contains("lambdynamiclights")
                || name.contains("emotecraft")
                || name.contains("emotetweaks")
                || name.contains("voicechat")
                || name.contains("createrailwaysnavigator")
                || name.contains("extra-mod-integrations");
    }

    private static void tuneModernFix(File configDir) throws IOException {
        File modernFix = new File(configDir, "modernfix-mixins.properties");
        String content = modernFix.isFile() ? Tools.read(modernFix) : ONYX_MARKER + "\n";
        content = setEqualsOption(content, "mixin.perf.dynamic_resources", "false");
        content = setEqualsOption(content, "mixin.perf.dynamic_languages", "false");
        content = setEqualsOption(content, "mixin.perf.deduplicate_location", "true");
        content = setEqualsOption(content, "mixin.perf.deduplicate_climate_parameters", "true");
        writeIfDifferent(modernFix, content);
    }

    private static void tuneC2ME(File configDir, boolean veryHeavy, boolean extreme) throws IOException {
        File c2me = new File(configDir, "c2me.toml");
        int parallelism = extreme ? 1 : 2;
        String content = ONYX_MARKER + "\n"
                + "[general]\n"
                + "globalExecutorParallelism = " + parallelism + "\n"
                + "\n"
                + "[threadedWorldGen]\n"
                + "enabled = " + (!veryHeavy) + "\n"
                + "parallelism = " + parallelism + "\n"
                + "\n"
                + "[asyncIO]\n"
                + "enabled = true\n"
                + "serializerParallelism = 1\n"
                + "ioWorkerParallelism = 1\n"
                + "\n"
                + "[noTickViewDistance]\n"
                + "enabled = false\n";
        writeIfDifferent(c2me, content);
    }

    private static void tuneBadOptimizations(File configDir) throws IOException {
        File badOptimizations = new File(configDir, "badoptimizations.txt");
        if (!badOptimizations.isFile()) return;
        String content = Tools.read(badOptimizations);
        content = setColonOption(content, "log_config", "false");
        writeIfDifferent(badOptimizations, content);
    }

    private static int countModJars(File modsDir) {
        File[] mods = modsDir.listFiles(file -> {
            if (!file.isFile()) return false;
            String name = file.getName().toLowerCase(Locale.ROOT);
            return name.endsWith(".jar") || name.endsWith(".jar" + DISABLED_BY_ONYX);
        });
        return mods == null ? 0 : mods.length;
    }

    private static int countProfileMods(MinecraftProfile profile, File gameDirectory) {
        if (gameDirectory != null) {
            int count = countModJars(new File(gameDirectory, "mods"));
            if (count > 0) return count;
        }
        try {
            return countModJars(new File(Tools.getGameDirPath(profile), "mods"));
        } catch (Throwable ignored) {
            return 0;
        }
    }

    private static String setColonOption(String content, String key, String value) {
        return setOption(content, key, value, ":");
    }

    private static String setEqualsOption(String content, String key, String value) {
        return setOption(content, key, value, "=");
    }

    private static String setTomlOption(String content, String key, String value) {
        return setOption(content, key, value, "=");
    }

    private static String setTomlStringOption(String content, String key, String value) {
        return setOption(content, key, "\"" + value + "\"", "=");
    }

    private static String setFancyMenuOption(String content, String key, String value) {
        return setOption(content, key, "'" + value + "';", "=");
    }

    private static String setOption(String content, String key, String value, String separator) {
        String[] lines = content == null || content.isEmpty() ? new String[0] : content.split("\\R", -1);
        String replacement = key + separator + value;
        StringBuilder builder = new StringBuilder();
        boolean found = false;
        for (String line : lines) {
            String normalizedLine = line.trim();
            if (isOptionLine(normalizedLine, key, separator)) {
                if (!found) {
                    builder.append(replacement).append('\n');
                    found = true;
                }
            } else if (!normalizedLine.isEmpty()) {
                builder.append(normalizedLine).append('\n');
            }
        }
        if (!found) {
            if (builder.length() > 0 && builder.charAt(builder.length() - 1) != '\n') {
                builder.append('\n');
            }
            builder.append(replacement).append('\n');
        }
        return builder.toString();
    }

    private static boolean isOptionLine(String normalizedLine, String key, String separator) {
        if (!normalizedLine.startsWith(key)) return false;
        String remainder = normalizedLine.substring(key.length()).trim();
        return remainder.startsWith(separator);
    }

    private static JSONObject getOrCreateObject(JSONObject root, String key) throws JSONException {
        JSONObject child = root.optJSONObject(key);
        if (child == null) {
            child = new JSONObject();
            root.put(key, child);
        }
        return child;
    }

    private static boolean writeIfDifferent(File file, String content) throws IOException {
        String existing = file.isFile() ? Tools.read(file) : null;
        if (content.equals(existing)) return false;
        File parent = file.getParentFile();
        if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
            throw new IOException("Could not create " + parent);
        }
        Tools.write(file.getAbsolutePath(), content);
        return true;
    }

    private static void clearEveryCompatCache(File gameDirectory) {
        if (gameDirectory == null) return;
        deleteGeneratedCache(new File(gameDirectory, "dynamic-resource-pack-cache"));
        deleteGeneratedCache(new File(gameDirectory, "dynamic-data-pack-cache"));
    }

    private static void deleteGeneratedCache(File file) {
        if (file == null || !file.exists()) return;
        File[] children = file.listFiles();
        if (children != null) {
            for (File child : children) {
                deleteGeneratedCache(child);
            }
        }
        if (!file.delete()) {
            Log.w(TAG, "Could not delete generated cache " + file);
        }
    }

    private static String appendArg(String args, String arg) {
        if (args == null || args.isEmpty()) return arg;
        return args + " " + arg;
    }

    private static String removeArg(String args, String prefix) {
        if (args == null || args.isEmpty()) return "";
        StringBuilder builder = new StringBuilder();
        for (String arg : args.split("\\s+")) {
            if (arg.startsWith(prefix)) continue;
            if (builder.length() > 0) builder.append(' ');
            builder.append(arg);
        }
        return builder.toString();
    }

    private static int roundDownToStep(int value, int step) {
        if (value <= 0) return value;
        return (value / step) * step;
    }

    private static int requiredJavaFor(String versionId) {
        String base = extractMinecraftVersion(versionId);
        if (isAtLeast(base, 1, 20, 5)) return 21;
        if (isAtLeast(base, 1, 18, 0)) return 17;
        return 8;
    }

    private static String runtimeForJava(int requiredJava) {
        if (requiredJava >= 21) return "Internal-21";
        if (requiredJava >= 17) return "Internal-17";
        return "Internal-8";
    }

    private static boolean shouldUpdateRuntime(String javaDir, int requiredJava) {
        if (isBlank(javaDir)) return true;
        if (!javaDir.startsWith(Tools.LAUNCHERPROFILES_RTPREFIX)) return false;
        String runtime = javaDir.substring(Tools.LAUNCHERPROFILES_RTPREFIX.length());
        int currentJava = javaVersionFromRuntime(runtime);
        return currentJava > 0 && currentJava < requiredJava;
    }

    private static int javaVersionFromRuntime(String runtime) {
        if (runtime == null) return 0;
        if (runtime.contains("25")) return 25;
        if (runtime.contains("21")) return 21;
        if (runtime.contains("17")) return 17;
        if (runtime.contains("8")) return 8;
        return 0;
    }

    private static String extractMinecraftVersion(String versionId) {
        if (isBlank(versionId)) return "";
        String value = versionId.toLowerCase(Locale.ROOT);
        String[] parts = value.split("[^0-9.]+");
        for (String part : parts) {
            if (part.matches("\\d+\\.\\d+(\\.\\d+)?")) {
                return part;
            }
        }
        return "";
    }

    private static boolean isAtLeast(String version, int major, int minor, int patch) {
        if (isBlank(version)) return false;
        String[] raw = version.split("\\.");
        int vMajor = raw.length > 0 ? parseInt(raw[0]) : 0;
        int vMinor = raw.length > 1 ? parseInt(raw[1]) : 0;
        int vPatch = raw.length > 2 ? parseInt(raw[2]) : 0;
        if (vMajor != major) return vMajor > major;
        if (vMinor != minor) return vMinor > minor;
        return vPatch >= patch;
    }

    private static int parseInt(String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private static String safeLower(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private static boolean equals(String left, String right) {
        if (left == null) return right == null || right.isEmpty();
        return left.equals(right);
    }
}
