package com.cannon.onyxlauncher.modloaders;

import android.content.Intent;

import com.cannon.onyxlauncher.Tools;
import com.cannon.onyxlauncher.utils.DownloadUtils;

import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.util.List;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

public class ForgeUtils {
    private static final String FORGE_METADATA_URL = "https://maven.minecraftforge.net/net/minecraftforge/forge/maven-metadata.xml";
    private static final String FORGE_INSTALLER_URL = "https://maven.minecraftforge.net/net/minecraftforge/forge/%1$s/forge-%1$s-installer.jar";
    public static List<String> downloadForgeVersions() throws IOException {
        SAXParser saxParser;
        try {
            SAXParserFactory parserFactory = SAXParserFactory.newInstance();
            saxParser = parserFactory.newSAXParser();
        }catch (SAXException | ParserConfigurationException e) {
            e.printStackTrace();
            // if we cant make a parser we might as well not even try to parse anything
            return null;
        }
        try {
            //of_test();
            return DownloadUtils.downloadStringCached(FORGE_METADATA_URL, "forge_versions", input -> {
                try {
                    ForgeVersionListHandler handler = new ForgeVersionListHandler();
                    saxParser.parse(new InputSource(new StringReader(input)), handler);
                    return handler.getVersions();
                    // IOException is present here StringReader throws it only if the parser called close()
                    // sooner than needed, which is a parser issue and not an I/O one
                }catch (SAXException | IOException e) {
                    throw new DownloadUtils.ParseException(e);
                }
            });
        }catch (DownloadUtils.ParseException e) {
            e.printStackTrace();
            return null;
        }

    }
    public static String getInstallerUrl(String version) {
        return String.format(FORGE_INSTALLER_URL, version);
    }

    public static void addAutoInstallArgs(Intent intent, File modInstallerJar, boolean createProfile) {
        intent.putExtra("javaArgs", "-javaagent:"+ Tools.DIR_DATA+"/forge_installer/forge_installer.jar"
                + (createProfile ? "=NPS" : "") + // No Profile Suppression
                " -jar "+modInstallerJar.getAbsolutePath());
    }
    public static void addAutoInstallArgs(Intent intent, File modInstallerJar, String modpackFixupId) {
        intent.putExtra("javaArgs", "-javaagent:"+ Tools.DIR_DATA+"/forge_installer/forge_installer.jar"
                + "=\"" + modpackFixupId +"\"" +
                " -jar "+modInstallerJar.getAbsolutePath());
    }

    /**
     * Adds CLI (headless) install arguments for Forge/NeoForge installer.
     * Uses --installClient flag which bypasses the Swing GUI entirely.
     * Works with Java 17 and Java 21 without needing Caciocavallo.
     *
     * @param intent       The Intent for JavaGUILauncherActivity
     * @param installerJar The downloaded Forge/NeoForge installer .jar
     * @param mcHomeDir    The Minecraft home directory (where versions/ lives)
     */
    public static void addCliInstallArgs(Intent intent, File installerJar, String mcHomeDir) {
        // Forge Installer CLI: java -jar forge-installer.jar --installClient <MC_DIR>
        // The --installClient arg tells Forge installer to skip GUI and install silently.
        intent.putExtra("javaArgs",
                "-jar " + installerJar.getAbsolutePath() +
                " --installClient " + mcHomeDir);
    }
}
