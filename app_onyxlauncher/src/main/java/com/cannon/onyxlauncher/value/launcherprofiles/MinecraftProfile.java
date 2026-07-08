package com.cannon.onyxlauncher.value.launcherprofiles;

import androidx.annotation.Keep;

@Keep
public class MinecraftProfile {

	public static String LATEST_RELEASE = "latest-release";
	public static String LATEST_SNAPSHOT= "latest-snapshot";

	public String name;
	public String type;
	public String created;
	public String lastUsed;
	public String icon;
	public String lastVersionId;
	public String gameDir;
	public String javaDir;
	public String javaArgs;
	public String logConfig;
	public boolean logConfigIsXML;
	public String pojavRendererName;
	public String controlFile;
	public Integer ramAllocation;
	public Integer resolutionScale;
	public Boolean alternateSurface;
	public Boolean forceVsync;
	public MinecraftResolution[] resolution;


	public static MinecraftProfile createTemplate(){
		MinecraftProfile TEMPLATE = new MinecraftProfile();
		TEMPLATE.name = "";
		TEMPLATE.lastVersionId = LATEST_RELEASE;
		return TEMPLATE;
	}

	public static MinecraftProfile getDefaultProfile(){
		MinecraftProfile defaultProfile = new MinecraftProfile();
		defaultProfile.name = "Default";
		defaultProfile.lastVersionId = "1.7.10";
		return defaultProfile;
	}

	public MinecraftProfile(){}

	public MinecraftProfile(MinecraftProfile profile){
		name = profile.name;
		type = profile.type;
		created = profile.created;
		lastUsed = profile.lastUsed;
		icon = profile.icon;
		lastVersionId = profile.lastVersionId;
		gameDir = profile.gameDir;
		javaDir = profile.javaDir;
		javaArgs = profile.javaArgs;
		logConfig = profile.logConfig;
		logConfigIsXML = profile.logConfigIsXML;
		pojavRendererName = profile.pojavRendererName;
		controlFile = profile.controlFile;
		ramAllocation = profile.ramAllocation;
		resolutionScale = profile.resolutionScale;
		alternateSurface = profile.alternateSurface;
		forceVsync = profile.forceVsync;
		resolution = profile.resolution;
	}
}
