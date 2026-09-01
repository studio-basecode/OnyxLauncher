# OnyxLauncher

[![Platform](https://img.shields.io/badge/Platform-Android-3DDC84?style=flat-square)](#)
[![Architecture](https://img.shields.io/badge/Architecture-ARM64%20%7C%20ARMv7-2196F3?style=flat-square)](#)
[![License](https://img.shields.io/badge/License-LGPL--3.0-orange?style=flat-square)](LICENSE)
[![Base](https://img.shields.io/badge/Base-PojavLauncher-64748B?style=flat-square)](https://github.com/PojavLauncherTeam/PojavLauncher)

OnyxLauncher is an Android launcher for Minecraft: Java Edition, built on top of PojavLauncher and extended with a modern instance manager, modpack browsing, custom profile icons, mobile performance defaults, renderer controls, and mod/resource/shader workflows.

<p align="center">
  <a href="https://play.google.com/store/apps/details?id=com.cannon.onyxlauncher">
    <img src="https://play.google.com/intl/en_us/badges/static/images/badges/en_badge_web_generic.png" alt="Get it on Google Play" height="80"/>
  </a>
  &nbsp;&nbsp;
  <a href="https://basecodestudio.pages.dev">
    <img src="https://raw.githubusercontent.com/studio-basecode/OnyxLauncher/main/studio_basecode_logo.png" alt="Studio BaseCode" height="80"/>
  </a>
  &nbsp;&nbsp;
  <a href="https://github.com/studio-basecode/OnyxLauncher/releases/latest">
    <img src="https://img.shields.io/github/v/release/studio-basecode/OnyxLauncher?style=for-the-badge&logo=github&logoColor=white&label=Download%20APK&color=161b22" alt="Download APK from GitHub Releases" height="55"/>
  </a>
</p>

---


## Project Overview

Running Minecraft: Java Edition on Android requires a launcher that can prepare Java runtimes, Minecraft assets, modloaders, renderer settings, game profiles, and storage paths for mobile hardware. OnyxLauncher keeps that workflow inside one Android app while preserving the PojavLauncher foundation that makes Java Edition playable on Android.

This repository contains the public source tree for the launcher. Private signing files, local build configuration, and private service credentials are intentionally not included.

## Key Features

- Launch Minecraft: Java Edition on Android.
- Microsoft account login and local offline profiles.
- Instance management with custom names and icons.
- Fabric, Quilt, Forge, and NeoForge profile support.
- Modpack search and install flows for supported sources.
- Local modpack import for Modrinth `.mrpack` and CurseForge export `.zip` files.
- Mod, resource-pack, and shader-pack management.
- Renderer and runtime settings tuned for mobile devices.
- Playtime statistics and launcher-side session tracking.

## Runtime And JRE

OnyxLauncher uses Android-ready Java runtimes tailored for Minecraft compatibility and mobile performance.

JRE release repository:

[studio-basecode/onyx-jre-releases](https://github.com/studio-basecode/onyx-jre-releases)

| Java Runtime | Typical Minecraft Range | Notes |
| --- | --- | --- |
| Java 8 | 1.7.10 - 1.16.5 | Legacy Minecraft and older Forge/Fabric packs. |
| Java 17 | 1.17 - 1.20.4 | Modern Minecraft versions before Java 21 became standard. |
| Java 21 | 1.20.5+ | Current Minecraft versions and newer modpacks. |

## Modding Ecosystem

| Loader | Status | Notes |
| --- | --- | --- |
| Fabric | Supported | Commonly used for performance and lightweight modpacks. |
| Quilt | Supported | Fabric-like loader support where compatible. |
| Forge | Supported | Compatibility depends on Minecraft and Forge installer behavior. |
| NeoForge | Supported | Used by newer Forge-family modpacks. |

Compatibility still depends on the Minecraft version, modloader version, Java runtime, renderer, GPU driver, and the mods included in a specific pack.

## Modpack Sources

OnyxLauncher includes browser and install flows for multiple modpack ecosystems.

| Source | Search | Install | Notes |
| --- | --- | --- | --- |
| CurseForge | Supported | Supported | Requires the launcher build to provide access through an allowed service configuration. |
| Modrinth | Supported | Supported | Used for mods and modpacks with public download metadata. |
| Technic | Supported | Supported | Icon and metadata availability depends on the pack listing. |
| ATLauncher | Supported | Supported | Pack availability depends on upstream metadata and mirrors. |
| FTB Legacy | Supported | Supported | Legacy feed compatibility may vary by pack. |
| Local files | File picker | Supported | Supports local `.mrpack` and CurseForge export `.zip` imports. |

## Build From Source

Requirements:

- Android Studio or Android SDK command-line tools.
- JDK 17 for Android builds.
- Android NDK version `25.2.9519653`.
- A normal Android `local.properties` file pointing to your SDK installation.

Debug build:

```bash
./gradlew :app_onyxlauncher:assembleDebug
```

The debug APK is generated under:

```text
app_onyxlauncher/build/outputs/apk/debug/
```

Release signing is not configured in this public repository. Use your own private keystore and never commit signing files.

## Offline Accounts

Offline profiles can be created from the launcher account screen by choosing the local/offline account option and entering a player name.

Offline mode is intended for local testing, development, and cases where online authentication is not required. The recommended and supported way to play Minecraft is with a legitimate Minecraft account and the official Google Play build linked above.

## Project Layout

| Path | Purpose |
| --- | --- |
| `app_onyxlauncher/` | Android application module. |
| `arc_dns_injector/` | Runtime helper module. |
| `cacio_compat/` | Desktop Java compatibility support. |
| `forge_installer/` | Forge installer integration. |
| `jre_engine/` | Java runtime helper module. |
| `minecraft_compat_src/` | Minecraft compatibility sources. |
| `cloudflare/` | Optional service-side helpers for private deployments. |
| `scripts/` | Build and maintenance helper scripts. |

## License

OnyxLauncher is derived from PojavLauncher and is licensed under the GNU Lesser General Public License v3.0.

See [LICENSE](LICENSE) for the full license text.

## Credits

OnyxLauncher is maintained by Studio BaseCode.

This project uses PojavLauncher as its launcher foundation and preserves the LGPLv3 licensing model of the upstream project. Credit and thanks go to the PojavLauncher team and contributors for the original Android Minecraft: Java Edition launcher work.

PojavLauncher upstream:

[https://github.com/PojavLauncherTeam/PojavLauncher](https://github.com/PojavLauncherTeam/PojavLauncher)
