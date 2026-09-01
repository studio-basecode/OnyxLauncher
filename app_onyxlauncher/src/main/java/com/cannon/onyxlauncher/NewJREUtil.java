package com.cannon.onyxlauncher;

import static com.cannon.onyxlauncher.Architecture.archAsString;

import android.app.Activity;
import android.content.res.AssetManager;
import android.util.Log;

import com.cannon.onyxlauncher.multirt.MultiRTUtils;
import com.cannon.onyxlauncher.multirt.Runtime;
import com.cannon.onyxlauncher.utils.MathUtils;
import com.cannon.onyxlauncher.value.launcherprofiles.LauncherProfiles;
import com.cannon.onyxlauncher.value.launcherprofiles.MinecraftProfile;
import com.kdt.mcgui.ProgressLayout;
import com.cannon.onyxlauncher.progresskeeper.ProgressKeeper;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class NewJREUtil {
    public static final String JRE_DOWNLOAD_URL_PREFIX = "https://github.com/studio-basecode/onyx-jre-releases/releases/download/v1.0.0/jre-";

    private static boolean checkInternalRuntime(AssetManager assetManager, InternalRuntime internalRuntime) {
        String launcher_runtime_version;
        String installed_runtime_version = MultiRTUtils.readInternalRuntimeVersion(internalRuntime.name);
        try {
            launcher_runtime_version = Tools.read(assetManager.open(internalRuntime.path+"/version"));
        }catch (IOException exc) {
            //we don't have a runtime included!
            //if we have one installed -> return true -> proceed (no updates but the current one should be functional)
            //if we don't -> return false -> Cannot find compatible Java runtime
            return installed_runtime_version != null;
        }
        // this implicitly checks for null, so it will unpack the runtime even if we don't have one installed
        if(!launcher_runtime_version.equals(installed_runtime_version))
            return unpackInternalRuntime(assetManager, internalRuntime, launcher_runtime_version);
        else return true;
    }

    private static boolean isBundledInternalRuntime(AssetManager assetManager, InternalRuntime internalRuntime) {
        try (InputStream ignored = assetManager.open(internalRuntime.path + "/version")) {
            return true;
        } catch (IOException ignored) {
            return false;
        }
    }

    private static boolean canDownloadInternalRuntime(InternalRuntime internalRuntime) {
        return internalRuntime == InternalRuntime.JRE_8 ||
                internalRuntime == InternalRuntime.JRE_17 ||
                internalRuntime == InternalRuntime.JRE_21 ||
                internalRuntime == InternalRuntime.JRE_25;
    }

    private static boolean isInstallableInternalRuntime(AssetManager assetManager, InternalRuntime internalRuntime) {
        return isBundledInternalRuntime(assetManager, internalRuntime) || canDownloadInternalRuntime(internalRuntime);
    }

    public static String getRecommendedInternalRuntimeName(int javaVersion) {
        if (javaVersion <= 8) return InternalRuntime.JRE_8.name;
        if (javaVersion <= 17) return InternalRuntime.JRE_17.name;
        if (javaVersion <= 21) return InternalRuntime.JRE_21.name;
        return InternalRuntime.JRE_25.name;
    }


    private static boolean unpackInternalRuntime(AssetManager assetManager, InternalRuntime internalRuntime, String version) {
        try {
            MultiRTUtils.installRuntimeNamedBinpack(
                    assetManager.open(internalRuntime.path+"/universal.tar.xz"),
                    assetManager.open(internalRuntime.path+"/bin-" + archAsString(Tools.DEVICE_ARCHITECTURE) + ".tar.xz"),
                    internalRuntime.name, version);
            MultiRTUtils.postPrepare(internalRuntime.name);
            return true;
        }catch (IOException e) {
            Log.e("NewJREAuto", "Internal JRE unpack failed", e);
            return false;
        }
    }

    private static InternalRuntime getInternalRuntime(Runtime runtime) {
        for(InternalRuntime internalRuntime : InternalRuntime.values()) {
            if(internalRuntime.name.equals(runtime.name)) return internalRuntime;
        }
        return null;
    }

    private static MathUtils.RankedValue<Runtime> getNearestInstalledRuntime(int targetVersion) {
        List<Runtime> runtimes = MultiRTUtils.getRuntimes();
        return MathUtils.findNearestPositive(targetVersion, runtimes, (runtime)->runtime.javaVersion);
    }

    private static MathUtils.RankedValue<InternalRuntime> getNearestInternalRuntime(AssetManager assetManager, int targetVersion) {
        List<InternalRuntime> runtimeList = new ArrayList<>();
        for (InternalRuntime runtime : InternalRuntime.values()) {
            if (isInstallableInternalRuntime(assetManager, runtime)) {
                runtimeList.add(runtime);
            }
        }
        return MathUtils.findNearestPositive(targetVersion, runtimeList, (runtime)->runtime.majorVersion);
    }


    /** @return true if everything is good, false otherwise.  */
    public static boolean installNewJreIfNeeded(Activity activity, JMinecraftVersionList.Version versionInfo) throws IOException {
        //Now we have the reliable information to check if our runtime settings are good enough
        if (versionInfo.javaVersion == null || versionInfo.javaVersion.component.equalsIgnoreCase("jre-legacy"))
            return true;

        int gameRequiredVersion = versionInfo.javaVersion.majorVersion;

        LauncherProfiles.load();
        AssetManager assetManager = activity.getAssets();
        MinecraftProfile minecraftProfile = LauncherProfiles.getCurrentProfile();
        String profileRuntime = Tools.getSelectedRuntime(minecraftProfile);
        Runtime runtime = MultiRTUtils.read(profileRuntime);

        // If the profile uses an internal runtime (e.g. Internal-25), but the recommended internal runtime for this game version is different (e.g. Internal-21 for Java 21 / MC 1.21.1),
        // switch to the recommended internal runtime to prevent mod dependency mismatches (like Fabric mod 'depends java @ [21]').
        String recommendedRuntimeName = getRecommendedInternalRuntimeName(gameRequiredVersion);
        if (profileRuntime != null && profileRuntime.startsWith("Internal-") && !profileRuntime.equals(recommendedRuntimeName)) {
            InternalRuntime recInternalRuntime = null;
            for (InternalRuntime ir : InternalRuntime.values()) {
                if (ir.name.equals(recommendedRuntimeName)) {
                    recInternalRuntime = ir;
                    break;
                }
            }
            if (recInternalRuntime != null) {
                if (!checkInternalRuntime(assetManager, recInternalRuntime)) {
                    try {
                        downloadAndInstallJre(activity, recInternalRuntime.majorVersion);
                    } catch (IOException e) {
                        Log.w("NewJREUtil", "Failed to install recommended runtime " + recommendedRuntimeName, e);
                    }
                }
                if (Tools.isRuntimeInstalled(recInternalRuntime.name) || isBundledInternalRuntime(assetManager, recInternalRuntime)) {
                    minecraftProfile.javaDir = Tools.LAUNCHERPROFILES_RTPREFIX + recInternalRuntime.name;
                    LauncherProfiles.write();
                    return true;
                }
            }
        }

        // If the runtime reports javaVersion=0 (not yet installed), try to extract the major version
        // directly from the name (e.g. "Internal-21" → 21) so we respect the user's explicit choice.
        int effectiveRuntimeVersion = runtime.javaVersion;
        if (effectiveRuntimeVersion == 0 && profileRuntime != null && profileRuntime.startsWith("Internal-")) {
            try {
                effectiveRuntimeVersion = Integer.parseInt(profileRuntime.substring("Internal-".length()));
                Log.i("NewJREUtil", "Runtime not yet installed, inferred version " + effectiveRuntimeVersion + " from name: " + profileRuntime);
            } catch (NumberFormatException ignored) {}
        }

        // Partly trust the user with his own selection, if the game can even try to run in this case
        if (effectiveRuntimeVersion >= gameRequiredVersion) {
            // Determine which InternalRuntime this maps to
            InternalRuntime internalRuntime = getInternalRuntime(runtime);
            if (internalRuntime == null && profileRuntime != null) {
                // Runtime not installed yet — match by name
                for (InternalRuntime ir : InternalRuntime.values()) {
                    if (ir.name.equals(profileRuntime)) {
                        internalRuntime = ir;
                        break;
                    }
                }
            }

            if (internalRuntime != null) {
                // Not calling showRuntimeFail on failure here because we did, technically, find the compatible runtime
                if (!checkInternalRuntime(assetManager, internalRuntime)) {
                    try {
                        downloadAndInstallJre(activity, internalRuntime.majorVersion);
                        minecraftProfile.javaDir = Tools.LAUNCHERPROFILES_RTPREFIX + internalRuntime.name;
                        LauncherProfiles.write();
                        return true;
                    } catch (IOException jre25Fail) {
                        // JRE download failed (e.g. JRE 25 not yet available) — fall back to JRE 21
                        Log.w("NewJREUtil", "Failed to install JRE " + internalRuntime.majorVersion + ", falling back to JRE 21: " + jre25Fail.getMessage());
                        InternalRuntime fallback = InternalRuntime.JRE_21;
                        if (!checkInternalRuntime(assetManager, fallback)) {
                            downloadAndInstallJre(activity, fallback.majorVersion);
                        }
                        minecraftProfile.javaDir = Tools.LAUNCHERPROFILES_RTPREFIX + fallback.name;
                        LauncherProfiles.write();
                        return true;
                    }
                }
                // Verify the installed runtime is actually usable (not just version-matching)
                if (!Tools.isRuntimeInstalled(internalRuntime.name)) {
                    Log.w("NewJREUtil", "Runtime " + internalRuntime.name + " claims installed but files missing, installing JRE 21 as fallback");
                    InternalRuntime fallback = InternalRuntime.JRE_21;
                    if (!checkInternalRuntime(assetManager, fallback)) {
                        downloadAndInstallJre(activity, fallback.majorVersion);
                    }
                    minecraftProfile.javaDir = Tools.LAUNCHERPROFILES_RTPREFIX + fallback.name;
                    LauncherProfiles.write();
                    return true;
                }
            }
            return true;
        }


        // If the runtime version selected by the user is not appropriate for this version (which means the game won't run at all)
        // automatically pick from either an already installed runtime, or a runtime packed with the launcher
        MathUtils.RankedValue<?> nearestInstalledRuntime = getNearestInstalledRuntime(gameRequiredVersion);
        MathUtils.RankedValue<?> nearestInternalRuntime = getNearestInternalRuntime(assetManager, gameRequiredVersion);

        MathUtils.RankedValue<?> selectedRankedRuntime = MathUtils.objectMin(
                nearestInternalRuntime, nearestInstalledRuntime, (value)->value.rank
        );

        // No possible selections
        if(selectedRankedRuntime == null) {
            String appropriateRuntime = getRecommendedInternalRuntimeName(gameRequiredVersion);
            installInternalRuntime(activity, appropriateRuntime);
            minecraftProfile.javaDir = Tools.LAUNCHERPROFILES_RTPREFIX + appropriateRuntime;
            LauncherProfiles.write();
            return true;
        }

        Object selected = selectedRankedRuntime.value;
        String appropriateRuntime;
        InternalRuntime internalRuntime;

        // Perform checks on the picked runtime
        if(selected instanceof Runtime) {
            // If it's an already installed runtime, save its name and check if
            // it's actually an internal one (just in case)
            Runtime selectedRuntime = (Runtime) selected;
            appropriateRuntime = selectedRuntime.name;
            internalRuntime = getInternalRuntime(selectedRuntime);
        } else if (selected instanceof InternalRuntime) {
            // If it's an internal runtime, set it's name as the appropriate one.
            internalRuntime = (InternalRuntime) selected;
            appropriateRuntime = internalRuntime.name;
        } else {
            throw new RuntimeException("Unexpected type of selected: "+selected.getClass().getName());
        }

        // If it turns out the selected runtime is actually an internal one, attempt automatic installation or update
        if(internalRuntime != null && !checkInternalRuntime(assetManager, internalRuntime)) {
            downloadAndInstallJre(activity, internalRuntime.majorVersion);
            minecraftProfile.javaDir = Tools.LAUNCHERPROFILES_RTPREFIX + internalRuntime.name;
            LauncherProfiles.write();
            return true;
        }

        minecraftProfile.javaDir = Tools.LAUNCHERPROFILES_RTPREFIX + appropriateRuntime;
        LauncherProfiles.write();
        return true;
    }

    public static boolean installInternalRuntime(Activity activity, String runtimeName) throws IOException {
        if (!Tools.isValidString(runtimeName)) {
            throw new IOException(activity.getString(R.string.jre_error_no_java_selected));
        }

        for (InternalRuntime internalRuntime : InternalRuntime.values()) {
            if (internalRuntime.name.equals(runtimeName)) {
                if (!checkInternalRuntime(activity.getAssets(), internalRuntime)) {
                    downloadAndInstallJre(activity, internalRuntime.majorVersion);
                }
                return true;
            }
        }

        if (runtimeName.startsWith("Internal-")) {
            try {
                int javaVersion = Integer.parseInt(runtimeName.substring("Internal-".length()));
                if (javaVersion != 8 && javaVersion != 17 && javaVersion != 21 && javaVersion != 25) {
                    throw new IOException(activity.getString(R.string.jre_error_java_not_supported, javaVersion));
                }
                downloadAndInstallJre(activity, javaVersion);
                return true;
            } catch (NumberFormatException e) {
                throw new IOException(activity.getString(R.string.jre_error_unknown_java_version, runtimeName), e);
            }
        }

        throw new IOException(activity.getString(R.string.jre_error_unsupported_java_environment, runtimeName));
    }

    private static void downloadAndInstallJre(Activity activity, int javaVersion) throws IOException {
        String arch = archAsString(Tools.DEVICE_ARCHITECTURE);
        // Cache-buster appended to force bypass GitHub CDN cached (stale) assets
        long cacheBuster = System.currentTimeMillis();
        String universalUrl = JRE_DOWNLOAD_URL_PREFIX + javaVersion + "-universal.tar.xz?cb=" + cacheBuster;
        String platformUrl = JRE_DOWNLOAD_URL_PREFIX + javaVersion + "-bin-" + arch + ".tar.xz?cb=" + cacheBuster;

        File tempUniversalFile = new File(Tools.DIR_CACHE, "temp_jre_" + javaVersion + "_universal.tar.xz");
        File tempPlatformFile = new File(Tools.DIR_CACHE, "temp_jre_" + javaVersion + "_bin_" + arch + ".tar.xz");

        try {
            if (tempUniversalFile.exists()) tempUniversalFile.delete();
            if (tempPlatformFile.exists()) tempPlatformFile.delete();

            // Download Universal
            Log.i("NewJREUtil", "Downloading JRE " + javaVersion + " universal from: " + universalUrl);
            downloadFileWithProgress(universalUrl, tempUniversalFile, activity.getString(R.string.mcl_launch_downloading, "Java " + javaVersion + " (1/2)"));

            // Download Platform-specific
            Log.i("NewJREUtil", "Downloading JRE " + javaVersion + " bin-" + arch + " from: " + platformUrl);
            downloadFileWithProgress(platformUrl, tempPlatformFile, activity.getString(R.string.mcl_launch_downloading, "Java " + javaVersion + " (2/2)"));

            // Unpack both files
            Log.i("NewJREUtil", "Installing downloaded JRE " + javaVersion);
            ProgressKeeper.submitProgress(ProgressLayout.DOWNLOAD_MINECRAFT, 95, R.string.global_unpacking, "Java " + javaVersion);

            try (FileInputStream universalIn = new FileInputStream(tempUniversalFile);
                 FileInputStream platformIn = new FileInputStream(tempPlatformFile)) {
                String name = "Internal-" + javaVersion;
                String versionStr = String.valueOf(javaVersion);
                if (javaVersion == 25) {
                    versionStr = "25.4";
                }

                MultiRTUtils.installRuntimeNamedBinpack(universalIn, platformIn, name, versionStr);
                MultiRTUtils.postPrepare(name);

                // Overwrite the release file so MultiRTUtils.read() detects the correct Java version.
                // The downloaded archive may have a different version in the release file.
                File releaseFile = new File(Tools.MULTIRT_HOME + "/" + name + "/release");
                String osArch = javaReleaseArch(arch);
                String releaseContent =
                        "IMPLEMENTOR=\"OnyxLauncher\"\n" +
                        "JAVA_VERSION=\"" + javaVersion + "\"\n" +
                        "OS_ARCH=\"" + osArch + "\"\n" +
                        "OS_NAME=\"Linux\"\n";
                try (FileOutputStream relFos = new FileOutputStream(releaseFile, false)) {
                    relFos.write(releaseContent.getBytes("UTF-8"));
                }
                Log.i("NewJREUtil", "Wrote release file: JAVA_VERSION=" + javaVersion);
                // Force re-read of runtime so cache is updated
                MultiRTUtils.forceReread(name);
            }

            if (tempUniversalFile.exists()) tempUniversalFile.delete();
            if (tempPlatformFile.exists()) tempPlatformFile.delete();
        } catch (Exception e) {
            Log.e("NewJREUtil", "Failed to download and install JRE " + javaVersion, e);
            if (tempUniversalFile.exists()) tempUniversalFile.delete();
            if (tempPlatformFile.exists()) tempPlatformFile.delete();
            if (e instanceof IOException) {
                throw (IOException) e;
            } else {
                throw new IOException(activity.getString(R.string.jre_error_installation_failed, javaVersion, e.getMessage()), e);
            }
        }
    }

    private static String javaReleaseArch(String arch) {
        switch (arch) {
            case "arm64":
                return "aarch64";
            case "x86":
                return "i386";
            default:
                return arch;
        }
    }

    private static void downloadFileWithProgress(String urlString, File outputFile, String taskDescription) throws IOException {
        com.cannon.onyxlauncher.utils.FileUtils.ensureParentDirectory(outputFile);

        // Follow redirects manually to handle GitHub releases CDN redirects
        String currentUrl = urlString;
        HttpURLConnection conn = null;
        int maxRedirects = 10;
        for (int i = 0; i < maxRedirects; i++) {
            conn = (HttpURLConnection) new URL(currentUrl).openConnection();
            conn.setRequestProperty("User-Agent", "OnyxLauncher");
            conn.setConnectTimeout(60000);
            conn.setReadTimeout(60000);
            conn.setInstanceFollowRedirects(false);
            conn.connect();

            int responseCode = conn.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_MOVED_TEMP
                    || responseCode == HttpURLConnection.HTTP_MOVED_PERM
                    || responseCode == 307 || responseCode == 308) {
                String location = conn.getHeaderField("Location");
                conn.disconnect();
                if (location == null) throw new IOException("Redirect without Location header from: " + currentUrl);
                Log.i("NewJREUtil", "Redirecting to: " + location);
                currentUrl = location;
                continue;
            }

            if (responseCode != HttpURLConnection.HTTP_OK) {
                conn.disconnect();
                throw new IOException("Server returned HTTP " + responseCode + " (" + conn.getResponseMessage() + ") dla URL: " + currentUrl);
            }
            break;
        }

        if (conn == null) throw new IOException("Nie udalo sie polaczyc z: " + urlString);

        long length = conn.getContentLengthLong();
        InputStream is = conn.getInputStream();
        FileOutputStream fos = new FileOutputStream(outputFile);

        byte[] buffer = new byte[65536];
        int current;
        long overall = 0;

        try {
            while ((current = is.read(buffer)) != -1) {
                overall += current;
                fos.write(buffer, 0, current);
                int progressPercent = length > 0 ? (int) ((overall * 100L) / length) : 0;
                ProgressKeeper.submitProgress(ProgressLayout.DOWNLOAD_MINECRAFT, progressPercent, R.string.mcl_launch_downloading, taskDescription + " (" + progressPercent + "%)");
            }
        } finally {
            fos.close();
            is.close();
            conn.disconnect();
        }
    }

    private static void showRuntimeFail(Activity activity, JMinecraftVersionList.Version verInfo) {
        Tools.dialogOnUiThread(activity, activity.getString(R.string.global_error),
                activity.getString(R.string.multirt_nocompatiblert, verInfo.javaVersion.majorVersion));
    }

    private enum InternalRuntime {
        JRE_8(8, "Internal-8", "components/jre-legacy"),
        JRE_17(17, "Internal-17", "components/jre-new"),
        JRE_21(21, "Internal-21", "components/jre-21"),
        JRE_25(25, "Internal-25", "components/jre-25");


        public final int majorVersion;
        public final String name;
        public final String path;
        InternalRuntime(int majorVersion, String name, String path) {
            this.majorVersion = majorVersion;
            this.name = name;
            this.path = path;
        }
    }

}
