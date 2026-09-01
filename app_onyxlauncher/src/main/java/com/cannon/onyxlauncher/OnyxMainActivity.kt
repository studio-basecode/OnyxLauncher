package com.cannon.onyxlauncher

import java.io.IOException
import com.kdt.mcgui.ProgressLayout
import com.cannon.onyxlauncher.modloaders.modpacks.models.Constants

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import java.util.UUID
import java.util.zip.ZipInputStream
import com.cannon.onyxlauncher.prefs.LauncherPreferences
import com.cannon.onyxlauncher.value.MinecraftAccount
import com.cannon.onyxlauncher.value.launcherprofiles.LauncherProfiles
import com.cannon.onyxlauncher.value.launcherprofiles.MinecraftProfile
import android.net.Uri
import android.util.Log
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.ui.viewinterop.AndroidView
import com.cannon.onyxlauncher.authenticator.microsoft.MicrosoftBackgroundLogin
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.os.Handler
import android.os.Looper
import com.cannon.onyxlauncher.authenticator.microsoft.PresentedException
import java.net.URLEncoder
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.lazy.LazyRow
import kotlinx.coroutines.delay
import com.cannon.onyxlauncher.modloaders.modpacks.api.CommonApi
import com.cannon.onyxlauncher.modloaders.modpacks.models.SearchFilters
import com.cannon.onyxlauncher.modloaders.modpacks.models.ModItem
import com.cannon.onyxlauncher.modloaders.modpacks.models.ModDetail
import com.cannon.onyxlauncher.modloaders.modpacks.api.CurseforgeApi
import com.cannon.onyxlauncher.modloaders.modpacks.api.ModrinthApi
import com.cannon.onyxlauncher.modloaders.modpacks.api.TechnicApi
import com.cannon.onyxlauncher.modloaders.modpacks.api.ATLauncherApi
import com.cannon.onyxlauncher.modloaders.modpacks.api.FtbLegacyApi

val BgDark = Color(0xFF0F172A)
val CardBg = Color(0xFF1E293B)
val StrokeColor = Color(0xFF334155)
val TextPrimary = Color(0xFFF8FAFC)
val TextSecondary = Color(0xFF94A3B8)
val AccentColor = Color(0xFF818CF8)
val MicrosoftGreen = Color(0xFF107C10)

fun getInstanceDir(instanceId: String): File {
    LauncherProfiles.load()
    val p = LauncherProfiles.mainProfileJson.profiles.get(instanceId)
    return if (p != null) Tools.getGameDirPath(p) else File(Tools.DIR_GAME_HOME, "instances/$instanceId")
}

fun getUniqueInstanceName(baseName: String, instances: List<InstanceData>): String {
    var uniqueName = baseName
    var count = 1
    while (instances.any { it.name.equals(uniqueName, ignoreCase = true) }) {
        uniqueName = "$baseName ($count)"
        count++
    }
    return uniqueName
}

class OnyxMainActivity : ComponentActivity() {
    private val REQUEST_STORAGE_REQUEST_CODE = 1

    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(com.cannon.onyxlauncher.utils.LocaleUtils.setLocale(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        com.cannon.onyxlauncher.utils.LocaleUtils.setLocale(this)
        super.onCreate(savedInstanceState)
        
        // Krytyczna inicjalizacja dla silnika gry
        Tools.getDisplayMetrics(this)
        Tools.initEarlyConstants(this)

        if (android.os.Build.VERSION.SDK_INT >= 23 && android.os.Build.VERSION.SDK_INT < 29 && !isStorageAllowed()) {
            requestStoragePermission()
        } else {
            initStorageAndUnpack()
        }
    }

    private fun isStorageAllowed(): Boolean {
        val result1 = androidx.core.content.ContextCompat.checkSelfPermission(this, android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
        val result2 = androidx.core.content.ContextCompat.checkSelfPermission(this, android.Manifest.permission.READ_EXTERNAL_STORAGE)
        return result1 == android.content.pm.PackageManager.PERMISSION_GRANTED &&
                result2 == android.content.pm.PackageManager.PERMISSION_GRANTED
    }

    private fun requestStoragePermission() {
        androidx.core.app.ActivityCompat.requestPermissions(
            this,
            arrayOf(android.Manifest.permission.WRITE_EXTERNAL_STORAGE, android.Manifest.permission.READ_EXTERNAL_STORAGE),
            REQUEST_STORAGE_REQUEST_CODE
        )
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_STORAGE_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                initStorageAndUnpack()
            } else {
                Toast.makeText(this, R.string.toast_permission_denied, Toast.LENGTH_LONG).show()
                requestStoragePermission()
            }
        }
    }

    private fun initStorageAndUnpack() {
        if (!Tools.checkStorageRoot(this)) {
            startActivity(Intent(this, MissingStorageActivity::class.java))
            finish()
            return
        }
        Tools.initStorageConstants(this)
        com.cannon.onyxlauncher.tasks.AsyncAssetManager.unpackComponents(this)
        com.cannon.onyxlauncher.tasks.AsyncAssetManager.unpackSingleFiles(this)
        
        LauncherPreferences.loadPreferences(this)
        LauncherProfiles.load()
        
        com.cannon.onyxlauncher.tasks.AsyncVersionList().getVersionList({ list ->
            com.cannon.onyxlauncher.extra.ExtraCore.setValue(com.cannon.onyxlauncher.extra.ExtraConstants.RELEASE_TABLE, list)
        }, false)
        
        val startScreen = intent.getStringExtra("open_screen") ?: "Home"

        setContent { 
            MaterialTheme { 
                MainApp(startScreen) 
            } 
        }
    }
}

// MODELE
data class MinecraftVersion(val id: String, val type: String, val url: String)
data class InstanceData(val id: String, val name: String, val mcVersion: String)
data class AccountInfo(val username: String, val isPremium: Boolean, val uuid: String = UUID.randomUUID().toString())
data class DownloadState(val isDownloading: Boolean = false, val fileName: String = "", val progress: Float = 0f, val downloadedMb: String = "")

// LOGI - Czytanie z folderu gry Pojav
fun readLogFromFile(context: Context): String = try {
    val logFile = File(Tools.DIR_GAME_HOME, "latestlog.txt")
    if (logFile.exists()) logFile.readText() else context.getString(R.string.log_no_logs)
} catch (e: Exception) { context.getString(R.string.log_read_error, e.message) }

@Composable
fun FittedSingleLineText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = TextPrimary,
    maxFontSp: Float = 14f,
    minFontSp: Float = 9f,
    fontWeight: FontWeight? = null,
    textAlign: TextAlign = TextAlign.Start
) {
    BoxWithConstraints(modifier = modifier) {
        val textLength = text.length.coerceAtLeast(1)
        val availableWidth = maxWidth.value.coerceAtLeast(1f)
        val estimatedWidth = textLength * maxFontSp * 0.58f
        val scale = (availableWidth / estimatedWidth).coerceIn(minFontSp / maxFontSp, 1f)
        Text(
            text = text,
            color = color,
            fontSize = (maxFontSp * scale).sp,
            fontWeight = fontWeight,
            textAlign = textAlign,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Clip,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

fun logLineColor(line: String): Color {
    val normalized = line.lowercase(Locale.ROOT)
    return when {
        normalized.contains("crash") ||
            normalized.contains("fatal") ||
            normalized.contains("exception") ||
            normalized.contains("error") ||
            normalized.contains("failed") ||
            normalized.contains("caused by") -> Color(0xFFFF6B6B)
        normalized.startsWith("\tat ") ||
            normalized.startsWith("at ") -> Color(0xFFFFA76B)
        normalized.contains("warn") -> Color(0xFFFBBF24)
        normalized.contains("info") -> Color(0xFF93C5FD)
        normalized.contains("debug") -> Color(0xFF94A3B8)
        else -> Color(0xFFE5E7EB)
    }
}

fun getNeoForgeBaseVersion(neoforgeVersion: String): String {
    val clean = neoforgeVersion.removePrefix("neoforge-")
    val parts = clean.split('.')
    if (parts.size >= 2) {
        val major = parts[0]
        val minor = parts[1]
        return if (minor == "0") "1.$major" else "1.$major.$minor"
    }
    return ""
}

fun isLaunchVersionForBase(launchVersion: String?, baseVersion: String): Boolean {
    if (launchVersion.isNullOrBlank()) return false
    if (launchVersion == baseVersion || launchVersion.endsWith("-$baseVersion")) return true
    if (launchVersion.startsWith("$baseVersion-forge-")) return true
    if (launchVersion.startsWith("$baseVersion-neoforge-")) return true
    if (launchVersion.startsWith("neoforge-")) {
        val mappedBase = getNeoForgeBaseVersion(launchVersion)
        return mappedBase == baseVersion
    }
    return false
}

fun resolveLaunchVersion(profile: MinecraftProfile?, baseVersion: String): String {
    var savedVersion = profile?.lastVersionId
    if (baseVersion == "1.21.1" && savedVersion != null && savedVersion.startsWith("fabric-loader-")) {
        savedVersion = "fabric-loader-0.16.10-1.21.1"
    }
    return if (isLaunchVersionForBase(savedVersion, baseVersion)) savedVersion!! else baseVersion
}

fun modLoaderLabel(launchVersion: String, baseVersion: String): String = when {
    launchVersion.startsWith("fabric-loader-") -> "Fabric"
    launchVersion.startsWith("quilt-loader-") -> "Quilt"
    launchVersion.startsWith("$baseVersion-forge-") -> "Forge"
    launchVersion.startsWith("$baseVersion-neoforge-") || launchVersion.startsWith("neoforge-") -> "NeoForge"
    else -> "Vanilla"
}

fun isLoaderInstalled(launchVersion: String, baseVersion: String): Boolean {
    val loader = modLoaderLabel(launchVersion, baseVersion)
    if (loader == "Vanilla") return true

    val jsonFile = File(Tools.DIR_HOME_VERSION, "$launchVersion/$launchVersion.json")
    if (!jsonFile.exists() || jsonFile.length() == 0L) return false

    if (loader == "Forge") {
        val loaderVersion = launchVersion.removePrefix("$baseVersion-forge-")
        val forgeDir = File(Tools.DIR_HOME_LIBRARY, "net/minecraftforge/forge/$baseVersion-$loaderVersion")
        val hasJar = forgeDir.exists() && (forgeDir.listFiles { _, name -> name.endsWith(".jar") }?.isNotEmpty() ?: false)
        if (!hasJar) return false
    }

    if (loader == "NeoForge") {
        val loaderVersion = launchVersion.removePrefix("neoforge-").removePrefix("$baseVersion-neoforge-")
        val neoforgeDir = File(Tools.DIR_HOME_LIBRARY, "net/neoforged/neoforge/$loaderVersion")
        val hasJar = neoforgeDir.exists() && (neoforgeDir.listFiles { _, name -> name.endsWith(".jar") }?.isNotEmpty() ?: false)
        if (!hasJar) return false
    }

    return true
}

suspend fun ensureFabricLanguageKotlin(context: Context, instanceId: String, baseVersion: String) {
    val modsDir = File(getInstanceDir(instanceId), "mods")
    if (!modsDir.exists()) modsDir.mkdirs()

    val kotlinModExists = modsDir.listFiles()?.any {
        val name = it.name.lowercase(Locale.ROOT)
        name.contains("fabric") && name.contains("language") && name.contains("kotlin")
    } ?: false

    if (kotlinModExists) return

    withContext(Dispatchers.IO) {
        try {
            val api = ModrinthApi()
            val modItem = ModItem(
                com.cannon.onyxlauncher.modloaders.modpacks.models.Constants.SOURCE_MODRINTH,
                false,
                "fabric-language-kotlin",
                "fabric-language-kotlin",
                "Fabric Language Kotlin",
                ""
            )
            val details = api.getModDetails(modItem)
            if (details != null) {
                val versionIndex = chooseBestVersionIndex(
                    details,
                    baseVersion,
                    preferFabric = true,
                    preferForge = false,
                    preferNeoForge = false,
                    preferQuilt = false
                )
                if (versionIndex >= 0 && details.versionUrls[versionIndex].isNotBlank()) {
                    val downloadUrl = details.versionUrls[versionIndex]
                    val fileName = safeDownloadFileName(details.versionNames[versionIndex], baseVersion, ".jar")
                    val destFile = File(modsDir, fileName)
                    downloadFileBlocking(downloadUrl, destFile)
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, context.getString(R.string.fabric_kotlin_downloaded), Toast.LENGTH_SHORT).show()
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("OnyxLauncher", "Failed to auto-download Fabric Language Kotlin", e)
        }
    }
}

suspend fun ensureSodiumForIris(context: Context, instanceId: String, baseVersion: String) {
    val modsDir = File(getInstanceDir(instanceId), "mods")
    if (!modsDir.exists()) return

    val jarFiles = modsDir.listFiles { _, name -> name.endsWith(".jar", ignoreCase = true) } ?: return
    var hasIris = false
    var hasSodium = false
    val filesToDelete = mutableListOf<File>()

    for (jarFile in jarFiles) {
        try {
            java.util.zip.ZipFile(jarFile).use { zip ->
                val entry = zip.getEntry("fabric.mod.json")
                if (entry != null) {
                    zip.getInputStream(entry).use { inputStream ->
                        val content = inputStream.bufferedReader().readText()
                        val match = Regex("""\"id\"\s*:\s*\"([^\"]+)\"""").find(content)
                        if (match != null) {
                            val modId = match.groupValues[1]
                            if (modId == "iris") {
                                hasIris = true
                            } else if (modId == "sodium") {
                                val versionMatch = Regex("""\"version\"\s*:\s*\"([^\"]+)\"""").find(content)
                                val versionStr = versionMatch?.groupValues?.get(1).orEmpty()
                                if (baseVersion == "1.21.1" && (versionStr.startsWith("0.8.") || versionStr.startsWith("0.7.") || versionStr.startsWith("0.6."))) {
                                    filesToDelete.add(jarFile)
                                } else {
                                    hasSodium = true
                                }
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            // Ignore parse errors
        }
    }

    for (file in filesToDelete) {
        try {
            file.delete()
        } catch (e: Exception) {
            Log.e("OnyxLauncher", "Failed to delete incompatible Sodium jar: ${file.name}", e)
        }
    }

    if (hasIris && !hasSodium) {
        withContext(Dispatchers.IO) {
            try {
                val api = ModrinthApi()
                val modItem = ModItem(
                    com.cannon.onyxlauncher.modloaders.modpacks.models.Constants.SOURCE_MODRINTH,
                    false,
                    "sodium",
                    "sodium",
                    "Sodium",
                    ""
                )
                val details = api.getModDetails(modItem)
                if (details != null) {
                    var versionIndex = chooseBestVersionIndex(
                        details,
                        baseVersion,
                        preferFabric = true,
                        preferForge = false,
                        preferNeoForge = false,
                        preferQuilt = false
                    )
                    if (modItem.id == "sodium" && baseVersion == "1.21.1") {
                        val compatibleIndices = details.versionUrls.indices.filter { idx ->
                            versionSupportsMinecraft(details, idx, baseVersion) &&
                            versionLoaderText(details, idx).contains("fabric") &&
                            details.versionNames[idx].contains("0.5.11")
                        }
                        if (compatibleIndices.isNotEmpty()) {
                            versionIndex = compatibleIndices.first()
                        }
                    }
                    if (versionIndex >= 0 && details.versionUrls[versionIndex].isNotBlank()) {
                        val downloadUrl = details.versionUrls[versionIndex]
                        val fileName = safeDownloadFileName(details.versionNames[versionIndex], baseVersion, ".jar")
                        val destFile = File(modsDir, fileName)
                        downloadFileBlocking(downloadUrl, destFile)
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, context.getString(R.string.sodium_downloaded), Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("OnyxLauncher", "Failed to auto-download Sodium for Iris", e)
            }
        }
    }
}

fun generateFabricLoaderOverrides(instanceId: String) {
    val modsDir = File(getInstanceDir(instanceId), "mods")
    val configDir = File(getInstanceDir(instanceId), "config")
    if (!modsDir.exists()) return

    val jarFiles = modsDir.listFiles { _, name -> name.endsWith(".jar", ignoreCase = true) } ?: return
    val fabricEntries = mutableListOf<String>()
    val quiltEntries = mutableListOf<String>()

    for (jarFile in jarFiles) {
        try {
            java.util.zip.ZipFile(jarFile).use { zip ->
                val entry = zip.getEntry("fabric.mod.json")
                if (entry != null) {
                    zip.getInputStream(entry).use { inputStream ->
                        val content = inputStream.bufferedReader().readText()
                        val match = Regex("""\"id\"\s*:\s*\"([^\"]+)\"""").find(content)
                        if (match != null) {
                            val modId = match.groupValues[1]

                            // Fabric entry
                            val fabricEntry = "    \"$modId\": {\n" +
                                              "      \"-depends\": {\n" +
                                              "        \"fabricloader\": \"\"\n" +
                                              "      }\n" +
                                              "    }"
                            fabricEntries.add(fabricEntry)

                            // Quilt entry by Path
                            val quiltEntryPath = "    {\n" +
                                                 "      \"path\": \"<mods>/${jarFile.name}\",\n" +
                                                 "      \"depends\": [\n" +
                                                 "        {\n" +
                                                 "          \"remove\": {\n" +
                                                 "            \"id\": \"fabricloader\"\n" +
                                                 "          }\n" +
                                                 "        }\n" +
                                                 "      ]\n" +
                                                 "    }"
                            quiltEntries.add(quiltEntryPath)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("OnyxLauncher", "Failed to parse jar ${jarFile.name} for overrides", e)
        }
    }

    val fabricOverrideFile = File(configDir, "fabric_loader_dependencies.json")
    val quiltOverrideFile = File(configDir, "quilt-loader-overrides.json")

    if (fabricEntries.isNotEmpty() || quiltEntries.isNotEmpty()) {
        if (!configDir.exists()) configDir.mkdirs()
        try {
            val fabricContent = "{\n  \"version\": 1,\n  \"overrides\": {\n" + fabricEntries.joinToString(",\n") + "\n  }\n}\n"
            val quiltContent = "{\n  \"schema_version\": 1,\n  \"overrides\": [\n" + quiltEntries.joinToString(",\n") + "\n  ]\n}\n"

            fabricOverrideFile.writeText(fabricContent)
            quiltOverrideFile.writeText(quiltContent)
            Log.i("OnyxLauncher", "Generated overrides for ${fabricEntries.size} mods in fabric_loader_dependencies.json and ${quiltEntries.size} entries in quilt-loader-overrides.json")
        } catch (e: Exception) {
            Log.e("OnyxLauncher", "Failed to write override files", e)
        }
    } else {
        if (fabricOverrideFile.exists()) {
            fabricOverrideFile.delete()
        }
        if (quiltOverrideFile.exists()) {
            quiltOverrideFile.delete()
        }
    }
}

fun isValidZip(file: File): Boolean {

    if (!file.exists() || file.length() == 0L) return false
    return try {
        java.util.zip.ZipFile(file).use { }
        true
    } catch (e: Exception) {
        false
    }
}

fun saveInstanceLaunchVersion(instanceId: String, baseVersion: String, launchVersion: String) {
    LauncherProfiles.load()
    val profile = LauncherProfiles.mainProfileJson.profiles[instanceId] ?: MinecraftProfile.getDefaultProfile().also {
        LauncherProfiles.mainProfileJson.profiles[instanceId] = it
    }
    if (isLaunchVersionForBase(launchVersion, baseVersion)) {
        profile.lastVersionId = launchVersion
        LauncherProfiles.write()
    }
}

fun installFabricLoaderForInstance(instanceId: String, baseVersion: String): String {
    val versions = com.cannon.onyxlauncher.modloaders.FabriclikeUtils.FABRIC_UTILS.downloadLoaderVersions(baseVersion)
        ?: throw java.io.IOException("Failed to download Fabric list for $baseVersion")
    val versionComparator = Comparator<com.cannon.onyxlauncher.modloaders.FabricVersion> { a, b ->
        val partsA = a.version.split('-')[0].split('.').map { it.toIntOrNull() ?: 0 }
        val partsB = b.version.split('-')[0].split('.').map { it.toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(partsA.size, partsB.size)) {
            val numA = partsA.getOrNull(i) ?: 0
            val numB = partsB.getOrNull(i) ?: 0
            if (numA != numB) return@Comparator numA.compareTo(numB)
        }
        val hasPreA = a.version.contains("beta") || a.version.contains("alpha")
        val hasPreB = b.version.contains("beta") || b.version.contains("alpha")
        if (hasPreA && !hasPreB) return@Comparator -1
        if (!hasPreA && hasPreB) return@Comparator 1
        a.version.compareTo(b.version)
    }
    val loaderVersion = versions.toList().filter { it.stable }.maxWithOrNull(versionComparator)
        ?: versions.toList().maxWithOrNull(versionComparator)
        ?: throw java.io.IOException("No Fabric loader for $baseVersion")
    val launchVersion = "fabric-loader-${loaderVersion.version}-$baseVersion"
    val jsonFile = File(Tools.DIR_HOME_VERSION, "$launchVersion/$launchVersion.json")
    if (!jsonFile.canRead()) {
        var installError: Exception? = null
        var installed = false
        val listener = object : com.cannon.onyxlauncher.modloaders.ModloaderDownloadListener {
            override fun onDownloadFinished(downloadedFile: File?) {
                installed = true
            }

            override fun onDataNotAvailable() {
                installError = java.io.IOException("Brak danych Fabric dla $baseVersion")
            }

            override fun onDownloadError(e: Exception) {
                installError = e
            }
        }
        com.cannon.onyxlauncher.modloaders.FabriclikeDownloadTask(
            listener,
            com.cannon.onyxlauncher.modloaders.FabriclikeUtils.FABRIC_UTILS,
            baseVersion,
            loaderVersion.version,
            false
        ).run()
        installError?.let { throw it }
        if (!installed && !jsonFile.canRead()) {
            throw java.io.IOException("Fabric installation did not complete successfully")
        }
    }
    saveInstanceLaunchVersion(instanceId, baseVersion, launchVersion)
    return launchVersion
}

fun installQuiltLoaderForInstance(instanceId: String, baseVersion: String): String {
    val versions = com.cannon.onyxlauncher.modloaders.FabriclikeUtils.QUILT_UTILS.downloadLoaderVersions(baseVersion)
        ?: throw java.io.IOException("Failed to download Quilt list for $baseVersion")
    val versionComparator = Comparator<com.cannon.onyxlauncher.modloaders.FabricVersion> { a, b ->
        val partsA = a.version.split('-')[0].split('.').map { it.toIntOrNull() ?: 0 }
        val partsB = b.version.split('-')[0].split('.').map { it.toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(partsA.size, partsB.size)) {
            val numA = partsA.getOrNull(i) ?: 0
            val numB = partsB.getOrNull(i) ?: 0
            if (numA != numB) return@Comparator numA.compareTo(numB)
        }
        val hasPreA = a.version.contains("beta") || a.version.contains("alpha")
        val hasPreB = b.version.contains("beta") || b.version.contains("alpha")
        if (hasPreA && !hasPreB) return@Comparator -1
        if (!hasPreA && hasPreB) return@Comparator 1
        a.version.compareTo(b.version)
    }
    val loaderVersion = versions.toList().filter { it.stable }.maxWithOrNull(versionComparator)
        ?: versions.toList().maxWithOrNull(versionComparator)
        ?: throw java.io.IOException("No Quilt loader for $baseVersion")
    val launchVersion = "quilt-loader-${loaderVersion.version}-$baseVersion"
    val jsonFile = File(Tools.DIR_HOME_VERSION, "$launchVersion/$launchVersion.json")
    if (!jsonFile.canRead()) {
        var installError: Exception? = null
        var installed = false
        val listener = object : com.cannon.onyxlauncher.modloaders.ModloaderDownloadListener {
            override fun onDownloadFinished(downloadedFile: File?) {
                installed = true
            }

            override fun onDataNotAvailable() {
                installError = java.io.IOException("Brak danych Quilt dla $baseVersion")
            }

            override fun onDownloadError(e: Exception) {
                installError = e
            }
        }
        com.cannon.onyxlauncher.modloaders.FabriclikeDownloadTask(
            listener,
            com.cannon.onyxlauncher.modloaders.FabriclikeUtils.QUILT_UTILS,
            baseVersion,
            loaderVersion.version,
            false
        ).run()
        installError?.let { throw it }
        if (!installed && !jsonFile.canRead()) {
            throw java.io.IOException("Quilt installation did not complete successfully")
        }
    }
    saveInstanceLaunchVersion(instanceId, baseVersion, launchVersion)
    return launchVersion
}

fun downloadNeoForgeVersions(): List<String>? {
    return try {
        val url = java.net.URL("https://maven.neoforged.net/releases/net/neoforged/neoforge/maven-metadata.xml")
        val xml = url.readText()
        val regex = "<version>([^<]+)</version>".toRegex()
        regex.findAll(xml).map { it.groupValues[1] }.toList()
    } catch (e: Exception) {
        android.util.Log.e("OnyxLauncher", "Failed to download NeoForge versions", e)
        null
    }
}

fun prepareNeoForgeLoaderForInstance(instanceId: String, baseVersion: String): ForgeLoaderPreparation {
    val neoforgeVersions = downloadNeoForgeVersions()
        ?: throw java.io.IOException("Failed to download NeoForge version")
    val parts = baseVersion.split('.')
    val major = parts.getOrNull(1) ?: "20"
    val minor = parts.getOrNull(2) ?: "0"
    val prefix = if (baseVersion == "1.21") "21.0." else "$major.$minor."

    val fullNeoForgeVersion = neoforgeVersions
        .filter { it.startsWith(prefix) }
        .lastOrNull()
        ?: throw java.io.IOException("No NeoForge for Minecraft $baseVersion")

    val launchVersion = "neoforge-$fullNeoForgeVersion"
    val jsonFile = File(Tools.DIR_HOME_VERSION, "$launchVersion/$launchVersion.json")
    if (jsonFile.canRead()) {
        saveInstanceLaunchVersion(instanceId, baseVersion, launchVersion)
        return ForgeLoaderPreparation(launchVersion, null)
    }

    val installerFile = File(Tools.DIR_CACHE, safeDownloadFileName("neoforge-$fullNeoForgeVersion-installer", baseVersion, ".jar"))
    if (!installerFile.canRead()) {
        downloadFileBlocking("https://maven.neoforged.net/releases/net/neoforged/neoforge/$fullNeoForgeVersion/neoforge-$fullNeoForgeVersion-installer.jar", installerFile)
    }
    saveInstanceLaunchVersion(instanceId, baseVersion, launchVersion)
    return ForgeLoaderPreparation(launchVersion, installerFile)
}

data class ForgeLoaderPreparation(val launchVersion: String, val installerFile: File?)

fun prepareForgeLoaderForInstance(instanceId: String, baseVersion: String): ForgeLoaderPreparation {
    val forgeVersions = com.cannon.onyxlauncher.modloaders.ForgeUtils.downloadForgeVersions()
        ?: throw java.io.IOException("Failed to download Forge list for $baseVersion")
    val fullForgeVersion = forgeVersions
        .filter { it.startsWith("$baseVersion-") }
        .lastOrNull()
        ?: throw java.io.IOException("No Forge for Minecraft $baseVersion")
    val loaderVersion = fullForgeVersion.removePrefix("$baseVersion-")
    val launchVersion = "$baseVersion-forge-$loaderVersion"
    val jsonFile = File(Tools.DIR_HOME_VERSION, "$launchVersion/$launchVersion.json")
    if (jsonFile.canRead()) {
        saveInstanceLaunchVersion(instanceId, baseVersion, launchVersion)
        return ForgeLoaderPreparation(launchVersion, null)
    }

    val installerFile = File(Tools.DIR_CACHE, safeDownloadFileName("forge-$fullForgeVersion-installer", baseVersion, ".jar"))
    if (!installerFile.canRead()) {
        downloadFileBlocking(com.cannon.onyxlauncher.modloaders.ForgeUtils.getInstallerUrl(fullForgeVersion), installerFile)
    }
    saveInstanceLaunchVersion(instanceId, baseVersion, launchVersion)
    return ForgeLoaderPreparation(launchVersion, installerFile)
}

fun chooseBestVersionIndex(
    detail: ModDetail,
    baseVersion: String,
    preferFabric: Boolean,
    preferForge: Boolean = false,
    preferNeoForge: Boolean = false,
    preferQuilt: Boolean = false
): Int {
    val compatible = detail.versionUrls.indices.filter { versionSupportsMinecraft(detail, it, baseVersion) }
    val candidates = if (compatible.isNotEmpty()) compatible else detail.versionUrls.indices.toList()
    if (candidates.isEmpty()) return -1
    if (preferNeoForge) {
        candidates.firstOrNull {
            val loader = versionLoaderText(detail, it)
            loader.contains("neoforge")
        }?.let { return it }
    }
    if (preferForge) {
        candidates.firstOrNull {
            val loader = versionLoaderText(detail, it)
            loader.contains("forge") && !loader.contains("neoforge")
        }?.let { return it }
    }
    if (preferQuilt) {
        candidates.firstOrNull {
            val loader = versionLoaderText(detail, it)
            loader.contains("quilt")
        }?.let { return it }
        // Quilt is compatible with Fabric mods - fall back to Fabric if no Quilt version
        candidates.firstOrNull {
            val loader = versionLoaderText(detail, it)
            loader.contains("fabric") && !loader.contains("forge") && !loader.contains("neoforge")
        }?.let { return it }
        candidates.firstOrNull {
            val loader = versionLoaderText(detail, it)
            !loader.contains("forge") && !loader.contains("neoforge")
        }?.let { return it }
    }
    if (preferFabric) {
        candidates.firstOrNull {
            val loader = versionLoaderText(detail, it)
            loader.contains("fabric") && !loader.contains("forge") && !loader.contains("neoforge")
        }?.let { return it }
        candidates.firstOrNull {
            val loader = versionLoaderText(detail, it)
            !loader.contains("forge") && !loader.contains("neoforge")
        }?.let { return it }
    }
    return candidates.first()
}

fun versionSupportsMinecraft(detail: ModDetail, index: Int, baseVersion: String): Boolean {
    val versions = detail.mcVersionNames.getOrNull(index).orEmpty()
    return versions.split(",")
        .map { it.trim() }
        .any { it == baseVersion }
}

fun versionLoaderText(detail: ModDetail, index: Int): String {
    val loader = detail.versionLoaders?.getOrNull(index).orEmpty()
    val name = detail.versionNames.getOrNull(index).orEmpty()
    return "$loader $name".lowercase(Locale.ROOT)
}

fun runtimeMajorVersion(runtimeName: String): Int? {
    return runtimeName.removePrefix("Internal-").substringBefore('-').toIntOrNull()
}

fun getRequiredJavaVersion(versionId: String): Int {
    try {
        val vInfo = Tools.getVersionInfo(versionId)
        if (vInfo?.javaVersion != null && vInfo.javaVersion.majorVersion > 0) {
            return vInfo.javaVersion.majorVersion
        }
    } catch (e: Exception) {
        // ignore
    }
    try {
        val normalized = com.cannon.onyxlauncher.tasks.AsyncMinecraftDownloader.normalizeVersionId(versionId)
        val listed = com.cannon.onyxlauncher.tasks.AsyncMinecraftDownloader.getListedVersion(normalized)
        if (listed?.javaVersion != null && listed.javaVersion.majorVersion > 0) {
            return listed.javaVersion.majorVersion
        }
    } catch (e: Exception) {
        // ignore
    }
    val match = Regex("""1\.(\d+)(?:\.(\d+))?""").find(versionId)
    if (match != null) {
        val minor = match.groupValues[1].toIntOrNull() ?: 0
        val patch = match.groupValues[2].toIntOrNull() ?: 0
        if (minor > 20 || (minor == 20 && patch >= 5)) return 21
        if (minor >= 18) return 17
        if (minor == 17) return 16
    }
    return 8
}

fun runtimeForRequirement(runtimeName: String, requiredJavaVersion: Int?): String {
    if (requiredJavaVersion == null) return runtimeName
    val major = runtimeMajorVersion(runtimeName)
    if (major != null && major >= requiredJavaVersion) return runtimeName
    return NewJREUtil.getRecommendedInternalRuntimeName(requiredJavaVersion)
}

suspend fun ensureJreForInstaller(context: android.content.Context, file: java.io.File, targetJavaVersion: Int = 8) {
    val activity = context as? android.app.Activity ?: return
    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        var requiredJarJava = com.cannon.onyxlauncher.JavaGUILauncherActivity.getJavaVersion(file)
        if (targetJavaVersion > requiredJarJava) {
            requiredJarJava = targetJavaVersion
        }
        if (requiredJarJava > 0) {
            val jreName = com.cannon.onyxlauncher.NewJREUtil.getRecommendedInternalRuntimeName(requiredJarJava)
            if (!Tools.isRuntimeInstalled(jreName)) {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    android.widget.Toast.makeText(context, context.getString(R.string.toast_downloading_java_for_installer, requiredJarJava.toString()), android.widget.Toast.LENGTH_SHORT).show()
                }
                com.cannon.onyxlauncher.NewJREUtil.installInternalRuntime(activity, jreName)
            }
        }
    }
}

private const val MAX_DOWNLOAD_FILE_NAME_BYTES = 120
private val unsafeFileNameChars = Regex("[/\\\\:*?\"<>|\\p{Cntrl}]")

fun safeDownloadFileName(versionName: String, baseVersion: String, extension: String): String {
    val fallbackExtension = if (extension.startsWith(".")) extension else ".$extension"
    val trimmedName = versionName.replace(" - $baseVersion", "").trim()
    val extensionFromName = Regex("\\.(jar|zip)$", RegexOption.IGNORE_CASE).find(trimmedName)?.value
    val finalExtension = (extensionFromName ?: fallbackExtension).lowercase(Locale.ROOT)
    val rawBaseName = if (extensionFromName != null) trimmedName.dropLast(extensionFromName.length) else trimmedName
    val cleanBaseName = rawBaseName
        .replace(unsafeFileNameChars, "-")
        .replace(Regex("\\s+"), " ")
        .replace(Regex("-+"), "-")
        .trim(' ', '.', '-')
        .ifBlank { "download" }
    val hash = java.lang.Long.toHexString(
        ("$versionName|$baseVersion|$finalExtension").hashCode().toLong() and 0xffffffffL
    ).padStart(8, '0')
    val suffix = "-$hash$finalExtension"
    val maxBaseBytes = (MAX_DOWNLOAD_FILE_NAME_BYTES - suffix.toByteArray(Charsets.UTF_8).size).coerceAtLeast(16)
    val shortenedBaseName = shortenUtf8(cleanBaseName, maxBaseBytes).trim(' ', '.', '-').ifBlank { "download" }
    return "$shortenedBaseName$suffix"
}

private fun shortenUtf8(value: String, maxBytes: Int): String {
    if (value.toByteArray(Charsets.UTF_8).size <= maxBytes) return value
    var end = value.length
    while (end > 0 && value.substring(0, end).toByteArray(Charsets.UTF_8).size > maxBytes) {
        end--
    }
    return value.substring(0, end)
}

fun downloadFileBlocking(downloadUrl: String, destinationFile: File) {
    destinationFile.parentFile?.mkdirs()
    val connection = URL(downloadUrl).openConnection() as HttpURLConnection
    connection.connectTimeout = 15000
    connection.readTimeout = 30000
    connection.inputStream.use { input ->
        FileOutputStream(destinationFile).use { output ->
            input.copyTo(output)
        }
    }
}

fun downloadDependencyMod(
    context: Context,
    baseVersion: String,
    instanceDir: File,
    query: String,
    preferFabric: Boolean,
    preferForge: Boolean,
    preferNeoForge: Boolean = false,
    preferQuilt: Boolean = false
) {
    val modsDir = File(instanceDir, "mods")
    modsDir.mkdirs()
    if (modsDir.listFiles()?.any { cleanModName(it.name).contains(cleanModName(query), ignoreCase = true) } == true) return
    val api = CommonApi(context.getString(R.string.curseforge_api_key))
    val filters = SearchFilters().apply {
        name = query
        mcVersion = baseVersion
        isModpack = false
        projectType = "mod"
    }
    val result = api.searchMod(filters) ?: throw java.io.IOException(context.getString(R.string.mod_not_found, query, baseVersion))
    val item = result.results.firstOrNull { it.title.contains(query, ignoreCase = true) } ?: result.results.firstOrNull()
        ?: throw java.io.IOException(context.getString(R.string.mod_not_found, query, baseVersion))
    val detail = api.getModDetails(item) ?: throw java.io.IOException(context.getString(R.string.mod_data_download_failed, query))
    val index = chooseBestVersionIndex(
        detail,
        baseVersion,
        preferFabric = preferFabric,
        preferForge = preferForge,
        preferNeoForge = preferNeoForge,
        preferQuilt = preferQuilt
    )
    if (index < 0 || detail.versionUrls[index].isBlank()) {
        throw java.io.IOException(context.getString(R.string.no_compatible_mod_version, query, baseVersion))
    }
    val unresolvedDependencies = installRequiredDependencyMods(
        context = context,
        api = api,
        baseVersion = baseVersion,
        modsDir = modsDir,
        parentDetail = detail,
        parentVersionIndex = index,
        preferFabric = preferFabric,
        preferForge = preferForge,
        preferNeoForge = preferNeoForge,
        preferQuilt = preferQuilt
    )
    if (unresolvedDependencies.isNotEmpty()) {
        throw java.io.IOException(context.getString(R.string.missing_mod_dependencies, query, unresolvedDependencies.joinToString(", ")))
    }
    val fileName = safeDownloadFileName(detail.versionNames[index], baseVersion, ".jar")
    val destination = File(modsDir, fileName)
    if (!destination.exists()) {
        downloadFileBlocking(detail.versionUrls[index], destination)
    }
}

fun installRequiredDependencyMods(
    context: Context,
    api: CommonApi,
    baseVersion: String,
    modsDir: File,
    parentDetail: ModDetail,
    parentVersionIndex: Int,
    preferFabric: Boolean,
    preferForge: Boolean,
    preferNeoForge: Boolean = false,
    preferQuilt: Boolean = false,
    visited: MutableSet<String> = mutableSetOf(),
    depth: Int = 0
): List<String> {
    if (depth > 4) return emptyList()
    val dependencyText = parentDetail.versionDependencyProjectIds?.getOrNull(parentVersionIndex).orEmpty()
    if (dependencyText.isBlank()) return emptyList()

    val unresolved = mutableListOf<String>()
    dependencyText.split(",")
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .forEach { dependencyId ->
            if (dependencyId.startsWith("external:", ignoreCase = true)) {
                unresolved += dependencyId.removePrefix("external:")
                return@forEach
            }
            val key = "${parentDetail.apiSource}:$dependencyId"
            if (!visited.add(key)) return@forEach

            val dependencyItem = ModItem(
                parentDetail.apiSource,
                false,
                dependencyId,
                dependencyId,
                context.getString(R.string.required_dependency),
                ""
            )
            val dependencyDetail = api.getModDetails(dependencyItem)
            if (dependencyDetail == null) {
                unresolved += dependencyId
                return@forEach
            }

            if (modsDir.listFiles()?.any {
                    cleanModName(it.name).contains(cleanModName(dependencyDetail.title), ignoreCase = true)
                } == true) {
                return@forEach
            }

            val dependencyVersionIndex = chooseBestVersionIndex(
                dependencyDetail,
                baseVersion,
                preferFabric = preferFabric,
                preferForge = preferForge,
                preferNeoForge = preferNeoForge,
                preferQuilt = preferQuilt
            )
            if (dependencyVersionIndex < 0 || dependencyDetail.versionUrls[dependencyVersionIndex].isBlank()) {
                unresolved += dependencyDetail.title
                return@forEach
            }

            unresolved += installRequiredDependencyMods(
                context = context,
                api = api,
                baseVersion = baseVersion,
                modsDir = modsDir,
                parentDetail = dependencyDetail,
                parentVersionIndex = dependencyVersionIndex,
                preferFabric = preferFabric,
                preferForge = preferForge,
                preferNeoForge = preferNeoForge,
                preferQuilt = preferQuilt,
                visited = visited,
                depth = depth + 1
            )

            val fileDepName = safeDownloadFileName(dependencyDetail.versionNames[dependencyVersionIndex], baseVersion, ".jar")
            val destDepFile = File(modsDir, fileDepName)
            if (!destDepFile.exists()) {
                downloadFileBlocking(dependencyDetail.versionUrls[dependencyVersionIndex], destDepFile)
            }
        }
    return unresolved.distinct()
}

fun enableResourcePack(instanceDir: File, fileName: String) {
    val optionsFile = File(instanceDir, "options.txt")
    val escapedName = fileName.replace("\\", "\\\\").replace("\"", "\\\"")
    val selectedValue = "[\"file/$escapedName\"]"
    val lines = if (optionsFile.exists()) optionsFile.readLines().toMutableList() else mutableListOf()
    val index = lines.indexOfFirst { it.startsWith("resourcePacks:") }
    if (index >= 0) lines[index] = "resourcePacks:$selectedValue" else lines.add("resourcePacks:$selectedValue")
    optionsFile.parentFile?.mkdirs()
    optionsFile.writeText(lines.joinToString("\n") + "\n")
}

fun enableShaderPack(instanceDir: File, fileName: String) {
    val configDir = File(instanceDir, "config")
    configDir.mkdirs()
    val irisFile = File(configDir, "iris.properties")
    val properties = java.util.Properties()
    if (irisFile.exists()) {
        irisFile.inputStream().use { properties.load(it) }
    }
    properties.setProperty("enableShaders", "true")
    properties.setProperty("shaderPack", fileName)
    irisFile.outputStream().use { properties.store(it, "Onyx Launcher shader selection") }

    val optifineFile = File(instanceDir, "optionsshaders.txt")
    val lines = if (optifineFile.exists()) optifineFile.readLines().toMutableList() else mutableListOf()
    val index = lines.indexOfFirst { it.startsWith("shaderPack=") }
    if (index >= 0) lines[index] = "shaderPack=$fileName" else lines.add("shaderPack=$fileName")
    optifineFile.writeText(lines.joinToString("\n") + "\n")
}

@Composable
fun MainApp(startScreen: String) {
    val context = LocalContext.current
    val sharedPrefs = context.getSharedPreferences("OnyxData", Context.MODE_PRIVATE)

    var accounts by remember { mutableStateOf(loadAccounts(sharedPrefs)) }
    var selectedAccount by remember { mutableStateOf(accounts.find { it.uuid == sharedPrefs.getString("selected_account_uuid", "") } ?: accounts.firstOrNull()) }
    var showAccountDialog by remember { mutableStateOf(false) }

    var myInstances by remember { mutableStateOf(loadInstances(sharedPrefs)) }
    var currentScreen by remember { mutableStateOf(startScreen) } 
    var activeInstance by remember { mutableStateOf<InstanceData?>(null) }
    var mojangVersions by remember { mutableStateOf(listOf<MinecraftVersion>()) }

    LaunchedEffect(Unit) { 
        withContext(Dispatchers.IO) {
            mojangVersions = fetchMinecraftVersions() 
        }
    }
    
    BackHandler(enabled = currentScreen != "Home") { 
        currentScreen = if (currentScreen == "InstanceSettings") "InstanceDetails" else "Home" 
    }

    Surface(modifier = Modifier.fillMaxSize(), color = BgDark) {
        Column(modifier = Modifier.fillMaxSize().systemBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp)) {
            
            TopBar(
                currentAccount = selectedAccount, 
                onAccountClick = { showAccountDialog = true }, 
                onSettingsClick = { currentScreen = "Settings" }, 
                onBackClick = if (currentScreen != "Home") { { 
                    currentScreen = if (currentScreen == "InstanceSettings") "InstanceDetails" else "Home" 
                } } else null
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                when (currentScreen) {
                    "Home" -> HomeScreen(
                        instances = myInstances, 
                        onAddClick = { currentScreen = "AddInstance" }, 
                        onInstanceClick = { activeInstance = it; currentScreen = "InstanceDetails" },
                        currentScreen = currentScreen
                    )
                    "AddInstance" -> AddInstanceScreen(
                        versions = mojangVersions, 
                        onCreate = { name, versionId ->
                            val uniqueName = getUniqueInstanceName(name, myInstances)
                            val newInstance = InstanceData(UUID.randomUUID().toString(), uniqueName, versionId)
                            myInstances = myInstances + newInstance
                            saveInstances(sharedPrefs, myInstances) 
                            currentScreen = "Home"
                        },
                        onModpackInstalled = { title, versionId, profileId ->
                            val uniqueName = getUniqueInstanceName(title, myInstances)
                            val newInstance = InstanceData(profileId, uniqueName, versionId)
                            myInstances = myInstances + newInstance
                            saveInstances(sharedPrefs, myInstances) 
                            currentScreen = "Home"
                        }
                    )
                    "InstanceDetails" -> activeInstance?.let { instance ->
                        InstanceDetailsScreen(
                            instance = instance, 
                            selectedAccount = selectedAccount, 
                            onLaunch = { versionId ->
                                val intent = Intent(context, MainActivity::class.java)
                                intent.putExtra(MainActivity.INTENT_MINECRAFT_VERSION, versionId)
                                context.startActivity(intent)
                            }, 
                            onShowLogs = { currentScreen = "CrashLogs" },
                            onShowSettings = { currentScreen = "InstanceSettings" },
                            onDelete = { 
                                myInstances = myInstances.filter { it.id != instance.id }
                                saveInstances(sharedPrefs, myInstances)
                                currentScreen = "Home" 
                            }
                        )
                    }
                    "InstanceSettings" -> activeInstance?.let { instance ->
                        InstanceSettingsScreen(
                            instance = instance,
                            onBack = { currentScreen = "InstanceDetails" }
                        )
                    }
                    "Settings" -> SettingsScreen(sharedPrefs)
                    "CrashLogs" -> CrashLogScreen { currentScreen = "InstanceDetails" }
                    "MicrosoftLogin" -> MicrosoftLoginScreen(
                        onSuccess = { info ->
                            accounts = accounts.filter { it.username != info.username } + info
                            selectedAccount = info
                            saveAccounts(sharedPrefs, accounts)
                            sharedPrefs.edit().putString("selected_account_uuid", info.uuid).apply()
                            currentScreen = "Home"
                        },
                        onCancel = {
                            currentScreen = "Home"
                        }
                    )
                }
            }
        }
        if (showAccountDialog) {
            AccountManagerDialog(
                accs = accounts, 
                sel = selectedAccount, 
                onDismiss = { showAccountDialog = false }, 
                onMicrosoftLoginClick = {
                    showAccountDialog = false
                    currentScreen = "MicrosoftLogin"
                },
                onChange = { newAccs, sel -> 
                    accounts = newAccs
                    selectedAccount = sel
                    saveAccounts(sharedPrefs, newAccs)
                    sharedPrefs.edit().putString("selected_account_uuid", sel?.uuid ?: "").apply() 
                }
            )
        }
    }
}

@Composable
fun TopBar(currentAccount: AccountInfo?, onAccountClick: () -> Unit, onSettingsClick: () -> Unit, onBackClick: (() -> Unit)?) {
    Row(
        modifier = Modifier.fillMaxWidth(), 
        horizontalArrangement = Arrangement.SpaceBetween, 
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (onBackClick != null) {
                IconButton(onClick = onBackClick) { 
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.global_back), tint = Color.White) 
                }
            }
            Text(text = "Onyx Launcher", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onSettingsClick) { 
                Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.content_desc_settings), tint = TextSecondary) 
            }
            Surface(
                color = CardBg, 
                shape = RoundedCornerShape(24.dp), 
                border = BorderStroke(1.dp, StrokeColor),
                modifier = Modifier.clickable { onAccountClick() }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), 
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    PlayerHead(currentAccount?.username ?: "Steve", currentAccount?.isPremium == true, Modifier.size(32.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    FittedSingleLineText(
                        text = currentAccount?.username ?: "Zaloguj",
                        color = TextPrimary,
                        maxFontSp = 14f,
                        minFontSp = 9f,
                        modifier = Modifier.width(74.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun PlayerHead(username: String, isPremium: Boolean, modifier: Modifier = Modifier) {
    val url = "https://minotar.net/helm/${if (isPremium) username else "MHF_Steve"}/64.png"
    var bitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    LaunchedEffect(url) { 
        withContext(Dispatchers.IO) { 
            try { 
                val conn = URL(url).openConnection() as HttpURLConnection
                conn.connectTimeout = 5000
                bitmap = android.graphics.BitmapFactory.decodeStream(conn.inputStream) 
            } catch (e: Exception) {} 
        } 
    }
    if (bitmap != null) {
        Image(
            bitmap = bitmap!!.asImageBitmap(), 
            contentDescription = null, 
            modifier = modifier.clip(RoundedCornerShape(4.dp)), 
            contentScale = ContentScale.Crop
        )
    } else {
        Box(modifier = modifier.background(Color.Gray, RoundedCornerShape(4.dp)))
    }
}

@Composable
fun HomeScreen(instances: List<InstanceData>, onAddClick: () -> Unit, onInstanceClick: (InstanceData) -> Unit, currentScreen: String) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth(), 
            horizontalArrangement = Arrangement.SpaceBetween, 
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = stringResource(R.string.home_your_installations), color = TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Button(
                onClick = onAddClick, 
                colors = ButtonDefaults.buttonColors(containerColor = AccentColor), 
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(4.dp))
                Text(text = stringResource(R.string.home_add_button))
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        LazyVerticalGrid(
            columns = GridCells.Fixed(2), 
            horizontalArrangement = Arrangement.spacedBy(12.dp), 
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(instances) { ins ->
                Surface(
                    color = CardBg, 
                    shape = RoundedCornerShape(16.dp), 
                    border = BorderStroke(1.dp, StrokeColor),
                    modifier = Modifier.clickable { onInstanceClick(ins) }
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp), 
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        var profileIcon by remember(ins.id) { mutableStateOf<android.graphics.Bitmap?>(null) }
                        LaunchedEffect(ins.id) {
                            withContext(Dispatchers.IO) {
                                profileIcon = getProfileIcon(ins.id)
                            }
                        }
                        Box(
                            modifier = Modifier.size(64.dp).background(AccentColor.copy(alpha = 0.2f), RoundedCornerShape(12.dp)), 
                            contentAlignment = Alignment.Center
                        ) {
                            if (profileIcon != null) {
                                Image(
                                    bitmap = profileIcon!!.asImageBitmap(),
                                    contentDescription = null,
                                    modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(12.dp)),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Icon(Icons.Default.Home, contentDescription = null, tint = AccentColor, modifier = Modifier.size(36.dp))
                            }
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        FittedSingleLineText(
                            text = ins.name,
                            color = TextPrimary,
                            maxFontSp = 14f,
                            minFontSp = 9f,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                        var fileCount by remember(ins.id) { mutableStateOf(0) }
                        LaunchedEffect(ins.id, currentScreen) {
                            withContext(Dispatchers.IO) {
                                val instanceDir = getInstanceDir(ins.id)
                                val mods = File(instanceDir, "mods").listFiles { f -> f.isFile && f.name.endsWith(".jar", ignoreCase = true) }?.filter { isValidZip(it) }?.size ?: 0
                                val resourcepacks = File(instanceDir, "resourcepacks").listFiles { f -> f.isFile && f.name.endsWith(".zip", ignoreCase = true) }?.filter { isValidZip(it) }?.size ?: 0
                                val shaderpacks = File(instanceDir, "shaderpacks").listFiles { f -> f.isFile && f.name.endsWith(".zip", ignoreCase = true) }?.filter { isValidZip(it) }?.size ?: 0
                                fileCount = mods + resourcepacks + shaderpacks
                            }
                        }
                        FittedSingleLineText(
                            text = stringResource(R.string.instance_version_files_count, ins.mcVersion, fileCount),
                            color = TextSecondary,
                            maxFontSp = 11f,
                            minFontSp = 8f,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun AddInstanceScreen(
    versions: List<MinecraftVersion>, 
    onCreate: (String, String) -> Unit,
    onModpackInstalled: (String, String, String) -> Unit
) {
    var activeMainTab by remember { mutableStateOf(0) } // 0 = Czysty Minecraft, 1 = Paczki Modów
    
    Column(modifier = Modifier.fillMaxSize().background(BgDark).padding(16.dp)) {
        TabRow(
            selectedTabIndex = activeMainTab,
            containerColor = CardBg,
            contentColor = AccentColor,
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)),
            divider = {}
        ) {
            Tab(selected = activeMainTab == 0, onClick = { activeMainTab = 0 }) {
                Text(text = stringResource(R.string.clean_minecraft), modifier = Modifier.padding(14.dp), fontWeight = FontWeight.Bold, color = if (activeMainTab == 0) AccentColor else TextSecondary)
            }
            Tab(selected = activeMainTab == 1, onClick = { activeMainTab = 1 }) {
                Text(text = stringResource(R.string.tab_modpacks), modifier = Modifier.padding(14.dp), fontWeight = FontWeight.Bold, color = if (activeMainTab == 1) AccentColor else TextSecondary)
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        if (activeMainTab == 0) {
            VanillaAddInstanceLayout(versions, onCreate)
        } else {
            ModpacksBrowserLayout(onModpackInstalled)
        }
    }
}

@Composable
fun VanillaAddInstanceLayout(versions: List<MinecraftVersion>, onCreate: (String, String) -> Unit) {
    var name by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf(0) }
    var selectedVersion by remember { mutableStateOf<MinecraftVersion?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    
    val categories = listOf(
        stringResource(R.string.category_release),
        stringResource(R.string.category_snapshot),
        stringResource(R.string.category_beta),
        stringResource(R.string.category_alpha)
    )
    val filteredVersions = versions.filter { v ->
        val typeMatch = when(selectedCategory) {
            0 -> v.type == "release"
            1 -> v.type == "snapshot"
            2 -> v.type == "old_beta"
            3 -> v.type == "old_alpha"
            else -> true
        }
        val searchMatch = v.id.contains(searchQuery, ignoreCase = true)
        typeMatch && searchMatch
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Text(text = stringResource(R.string.create_new_clean_instance), color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = name, 
            onValueChange = { name = it }, 
            label = { Text(stringResource(R.string.installation_name_label)) },
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary)
        )
        Spacer(modifier = Modifier.height(12.dp))
        TabRow(
            selectedTabIndex = selectedCategory, 
            containerColor = Color.Transparent, 
            contentColor = AccentColor, 
            divider = {}
        ) {
            categories.forEachIndexed { index, title ->
                Tab(selected = selectedCategory == index, onClick = { selectedCategory = index }) {
                    Text(
                        text = title, 
                        modifier = Modifier.padding(12.dp), 
                        color = if (selectedCategory == index) AccentColor else TextSecondary
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = searchQuery, 
            onValueChange = { searchQuery = it },
            placeholder = { Text(stringResource(R.string.search_version_placeholder)) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            modifier = Modifier.fillMaxWidth().height(56.dp),
            colors = OutlinedTextFieldDefaults.colors(focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary)
        )
        Spacer(modifier = Modifier.height(8.dp))
        LazyColumn(modifier = Modifier.weight(1f).border(1.dp, StrokeColor, RoundedCornerShape(8.dp))) {
            items(filteredVersions) { v ->
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { selectedVersion = v }
                        .background(if (selectedVersion == v) AccentColor.copy(alpha = 0.2f) else Color.Transparent)
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FittedSingleLineText(
                        text = v.id,
                        color = TextPrimary,
                        maxFontSp = 14f,
                        minFontSp = 9f,
                        modifier = Modifier.weight(1f)
                    )
                    Text(text = v.type.uppercase(), color = TextSecondary, fontSize = 10.sp)
                }
                HorizontalDivider(color = StrokeColor)
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = { if (name.isNotBlank() && selectedVersion != null) onCreate(name, selectedVersion!!.id) },
            modifier = Modifier.fillMaxWidth().height(50.dp),
            colors = ButtonDefaults.buttonColors(containerColor = AccentColor),
            enabled = name.isNotBlank() && selectedVersion != null
        ) {
            Text(text = stringResource(R.string.button_create_instance), fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun ModpacksBrowserLayout(onModpackInstalled: (String, String, String) -> Unit) {
    val context = LocalContext.current
    var searchQuery by remember { mutableStateOf("") }
    var selectedSource by remember { mutableStateOf(Constants.SOURCE_CURSEFORGE) }
    
    var isSearching by remember { mutableStateOf(false) }
    var modpacksList by remember { mutableStateOf<List<ModItem>>(emptyList()) }
    var selectedModpackForDetail by remember { mutableStateOf<ModItem?>(null) }
    
    val sources = listOf(
        Constants.SOURCE_CURSEFORGE to "CurseForge",
        Constants.SOURCE_MODRINTH to "Modrinth",
        Constants.SOURCE_TECHNIC to "Technic",
        Constants.SOURCE_ATLAUNCHER to "ATLauncher",
        Constants.SOURCE_FTB_LEGACY to "FTB Legacy"
    )

    LaunchedEffect(searchQuery, selectedSource) {
        isSearching = true
        withContext(Dispatchers.IO) {
            try {
                val filters = SearchFilters().apply {
                    name = searchQuery
                    isModpack = true
                    projectType = "modpack"
                }
                val results = when (selectedSource) {
                    Constants.SOURCE_CURSEFORGE -> CurseforgeApi(context.getString(R.string.curseforge_api_key)).searchMod(filters)
                    Constants.SOURCE_MODRINTH -> ModrinthApi().searchMod(filters)
                    Constants.SOURCE_TECHNIC -> TechnicApi().searchMod(filters)
                    Constants.SOURCE_ATLAUNCHER -> ATLauncherApi().searchMod(filters)
                    Constants.SOURCE_FTB_LEGACY -> FtbLegacyApi().searchMod(filters)
                    else -> null
                }
                modpacksList = results?.results?.toList() ?: emptyList()
            } catch (e: Exception) {
                Log.e("OnyxLauncher", "Error searching modpacks", e)
            } finally {
                isSearching = false
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Text(text = stringResource(R.string.browse_and_download_modpacks), color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))
        
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text(
                if (selectedSource == Constants.SOURCE_TECHNIC) "Wpisz slug paczki (np. attack-of-the-bteam)"
                else stringResource(R.string.search_modpack_placeholder)
            ) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            modifier = Modifier.fillMaxWidth().height(56.dp),
            colors = OutlinedTextFieldDefaults.colors(focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary)
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(sources) { pair ->
                val sourceId = pair.first
                val sourceName = pair.second
                val isSelected = selectedSource == sourceId
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isSelected) AccentColor else CardBg)
                        .clickable { selectedSource = sourceId }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = sourceName,
                        color = if (isSelected) BgDark else TextPrimary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        if (isSearching) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = AccentColor)
            }
        } else if (modpacksList.isEmpty()) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text(
                    text = if (selectedSource == Constants.SOURCE_TECHNIC) stringResource(R.string.search_technic_hint)
                           else stringResource(R.string.search_no_results),
                    color = TextSecondary,
                    fontSize = 13.sp
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f).border(1.dp, StrokeColor, RoundedCornerShape(8.dp))
            ) {
                items(modpacksList) { item ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedModpackForDetail = item }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OnyxAsyncImage(
                            url = item.imageUrl,
                            modifier = Modifier.size(48.dp),
                            roundedCorners = 8.dp
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = item.title, color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            Text(
                                text = item.description,
                                color = TextSecondary,
                                fontSize = 11.sp,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    HorizontalDivider(color = StrokeColor)
                }
            }
        }
    }

    if (selectedModpackForDetail != null) {
        ModpackDetailDialog(
            item = selectedModpackForDetail!!,
            onDismiss = { selectedModpackForDetail = null },
            onInstallComplete = { name, versionId, profileId ->
                selectedModpackForDetail = null
                onModpackInstalled(name, versionId, profileId)
            }
        )
    }
}

@Composable
fun ModpackDetailDialog(
    item: ModItem,
    onDismiss: () -> Unit,
    onInstallComplete: (String, String, String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var detail by remember { mutableStateOf<ModDetail?>(null) }
    var translatedDesc by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(true) }
    var selectedVersionIndex by remember { mutableStateOf(0) }
    var installProgress by remember { mutableStateOf<Float?>(null) }
    var installStatusText by remember { mutableStateOf("") }

    LaunchedEffect(item.id) {
        withContext(Dispatchers.IO) {
            try {
                val api = CommonApi(context.getString(R.string.curseforge_api_key))
                val loadedDetail = api.getModDetails(item)
                detail = loadedDetail
                if (loadedDetail != null) {
                    val targetLang = Locale.getDefault().language
                    translatedDesc = translateText(loadedDetail.description, targetLang)
                }
            } catch (e: Exception) {
                Log.e("OnyxLauncher", "Error loading modpack details", e)
            } finally {
                isLoading = false
            }
        }
    }

    Dialog(onDismissRequest = { if (installProgress == null) onDismiss() }) {
        Card(
            modifier = Modifier.fillMaxWidth().fillMaxHeight(0.85f),
            colors = CardDefaults.cardColors(containerColor = CardBg),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, StrokeColor)
        ) {
            Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                if (isLoading) {
                    Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = AccentColor)
                    }
                } else if (detail == null) {
                    Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Text(text = stringResource(R.string.failed_to_load_modpack_details), color = TextPrimary)
                    }
                } else {
                    val bannerUrl = detail?.screenshotUrls?.firstOrNull() ?: item.imageUrl
                    Box(
                        modifier = Modifier.fillMaxWidth().height(120.dp).clip(RoundedCornerShape(12.dp))
                    ) {
                        OnyxAsyncImage(url = bannerUrl, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                        Box(
                            modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.4f))
                        )
                        Row(
                            modifier = Modifier.fillMaxSize().padding(12.dp),
                            verticalAlignment = Alignment.Bottom
                        ) {
                            OnyxAsyncImage(url = item.imageUrl, modifier = Modifier.size(48.dp), roundedCorners = 8.dp)
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(text = item.title, color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    Text(
                        text = if (translatedDesc.isNotEmpty()) translatedDesc else detail!!.description,
                        color = TextSecondary,
                        fontSize = 12.sp,
                        modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())
                    )
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    if (installProgress != null) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            LinearProgressIndicator(progress = installProgress!!, color = AccentColor, modifier = Modifier.fillMaxWidth())
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(text = installStatusText, color = TextSecondary, fontSize = 12.sp)
                        }
                    } else {
                        var expanded by remember { mutableStateOf(false) }
                        Box(modifier = Modifier.fillMaxWidth()) {
                            OutlinedButton(
                                onClick = { expanded = true },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary),
                                border = BorderStroke(1.dp, StrokeColor)
                            ) {
                                Text(text = detail!!.versionNames.getOrNull(selectedVersionIndex) ?: stringResource(R.string.select_version))
                            }
                            DropdownMenu(
                                expanded = expanded,
                                onDismissRequest = { expanded = false },
                                modifier = Modifier.background(CardBg)
                            ) {
                                detail!!.versionNames.forEachIndexed { idx, name ->
                                    DropdownMenuItem(
                                        text = { Text(text = name, color = TextPrimary) },
                                        onClick = {
                                            selectedVersionIndex = idx
                                            expanded = false
                                        }
                                    )
                                }
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            OutlinedButton(
                                onClick = onDismiss,
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = TextSecondary),
                                border = BorderStroke(1.dp, StrokeColor)
                            ) {
                                Text(text = stringResource(R.string.button_cancel))
                            }
                            Button(
                                onClick = {
                                    scope.launch(Dispatchers.IO) {
                                        try {
                                            withContext(Dispatchers.Main) {
                                                installProgress = 0.05f
                                                installStatusText = context.getString(R.string.status_starting_download)
                                            }

                                            val progressListener = object : com.cannon.onyxlauncher.progresskeeper.ProgressListener {
                                                override fun onProgressStarted() {}
                                                override fun onProgressUpdated(progress: Int, resid: Int, vararg varArg: Any?) {
                                                    scope.launch(Dispatchers.Main) {
                                                        installProgress = progress / 100f
                                                        installStatusText = try { context.getString(resid, *varArg) } catch (e: Exception) { context.getString(R.string.status_installing) }
                                                    }
                                                }
                                                override fun onProgressEnded() {}
                                            }
                                            com.cannon.onyxlauncher.progresskeeper.ProgressKeeper.addListener(ProgressLayout.INSTALL_MODPACK, progressListener)

                                            val api = CommonApi(context.getString(R.string.curseforge_api_key))
                                            val modLoader = api.installMod(detail!!, selectedVersionIndex)

                                            com.cannon.onyxlauncher.progresskeeper.ProgressKeeper.removeListener(ProgressLayout.INSTALL_MODPACK, progressListener)

                                            if (modLoader != null) {
                                                if (modLoader.requiresGuiInstallation()) {
                                                    withContext(Dispatchers.Main) {
                                                        installStatusText = context.getString(R.string.status_downloading_forge_installer)
                                                    }
                                                    var forgeInstalled = false
                                                    var forgeError: Exception? = null
                                                    val forgeListener = object : com.cannon.onyxlauncher.modloaders.ModloaderDownloadListener {
                                                        override fun onDownloadFinished(downloadedFile: File?) {
                                                            forgeInstalled = true
                                                        }
                                                        override fun onDataNotAvailable() {
                                                            forgeError = IOException(context.getString(R.string.no_forge_installer_for_version))
                                                        }
                                                        override fun onDownloadError(e: Exception) {
                                                            forgeError = e
                                                        }
                                                    }
                                                    modLoader.getDownloadTask(forgeListener).run()
                                                    if (forgeError != null) throw forgeError!!
                                                    
                                                    withContext(Dispatchers.Main) {
                                                        installStatusText = context.getString(R.string.status_launching_forge_gui_installer)
                                                    }
                                                    val forgeInstallFile = File(Tools.DIR_CACHE, "forge-" + modLoader.getVersionId() + "-installer.jar")
                                                    val installerJar = File(Tools.DIR_CACHE, "forge-installer.jar")
                                                    val intent = modLoader.getInstallationIntent(context, forgeInstallFile.takeIf { it.exists() } ?: installerJar)
                                                    if (intent != null) {
                                                        context.startActivity(intent)
                                                    }
                                                } else {
                                                    withContext(Dispatchers.Main) {
                                                        installStatusText = context.getString(R.string.status_downloading_fabric_quilt_loader)
                                                    }
                                                    var loaderInstalled = false
                                                    var loaderError: Exception? = null
                                                    val loaderListener = object : com.cannon.onyxlauncher.modloaders.ModloaderDownloadListener {
                                                        override fun onDownloadFinished(downloadedFile: File?) {
                                                            loaderInstalled = true
                                                        }
                                                        override fun onDataNotAvailable() {
                                                            loaderError = IOException(context.getString(R.string.no_loader_for_version_err))
                                                        }
                                                        override fun onDownloadError(e: Exception) {
                                                            loaderError = e
                                                        }
                                                    }
                                                    modLoader.getDownloadTask(loaderListener).run()
                                                    if (loaderError != null) throw loaderError!!
                                                }

                                                withContext(Dispatchers.Main) {
                                                    installProgress = 1f
                                                    installStatusText = context.getString(R.string.modpack_installed_successfully)
                                                    onInstallComplete(modLoader.displayName, modLoader.getVersionId(), modLoader.profileId)
                                                }
                                            } else {
                                                throw java.io.IOException("Installer did not return valid loader data")
                                            }
                                        } catch (e: Exception) {
                                            Log.e("OnyxLauncher", "Modpack install failed", e)
                                            withContext(Dispatchers.Main) {
                                                installProgress = null
                                                installStatusText = ""
                                                Toast.makeText(context, context.getString(R.string.error_prefix, e.message), Toast.LENGTH_LONG).show()
                                            }
                                        }
                                    }
                                },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = AccentColor)
                            ) {
                                 Text(text = stringResource(R.string.button_download))
                            }
                        }
                    }
                }
            }
        }
    }
}

fun getProfileIcon(profileId: String): android.graphics.Bitmap? {
    try {
        LauncherProfiles.load()
        val p = LauncherProfiles.mainProfileJson.profiles.get(profileId) ?: return null
        val iconStr = p.icon ?: return null
        val base64Str = if (iconStr.contains(",")) iconStr.substringAfter(",") else iconStr
        val decodedBytes = android.util.Base64.decode(base64Str, android.util.Base64.DEFAULT)
        return android.graphics.BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)
    } catch (e: Exception) {
        return null
    }
}

fun safeModpackFileName(title: String, versionName: String, versionHash: String?): String {
    val source = (title.lowercase(Locale.ROOT) + " " + versionName).trim()
    var cleanName = source.replace(Regex("[\\\\/:*?\\\"<>|\\p{Cntrl}\\s]+"), "_")
        .replace(Regex("_+"), "_")
        .replace(Regex("^_+|_+$"), "")
    if (cleanName.isEmpty()) cleanName = "modpack"
    var hash = if (versionHash != null && versionHash.isNotEmpty()) versionHash
               else Integer.toHexString(source.hashCode())
    if (hash.length > 12) hash = hash.substring(0, 12)
    val suffix = "_" + hash
    val maxBaseBytes = Math.max(16, 120 - suffix.toByteArray().size)
    cleanName = shortenUtf8(cleanName, maxBaseBytes).replace(Regex("_+$"), "")
    if (cleanName.isEmpty()) cleanName = "modpack"
    return cleanName + suffix
}




@Composable
fun InstanceDetailsScreen(
    instance: InstanceData,
    selectedAccount: AccountInfo?,
    onLaunch: (String) -> Unit,
    onShowLogs: () -> Unit,
    onShowSettings: () -> Unit,
    onDelete: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var downloadState by remember { mutableStateOf(DownloadState()) }
    var ready by remember { mutableStateOf(false) }

    // refreshTrigger increments on every ON_RESUME so Java/launch version re-reads from disk
    var refreshTrigger by remember { mutableStateOf(0) }
    val lifecycleOwnerForDetails = androidx.compose.ui.platform.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwnerForDetails, instance.id) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                refreshTrigger++
            }
        }
        lifecycleOwnerForDetails.lifecycle.addObserver(observer)
        onDispose { lifecycleOwnerForDetails.lifecycle.removeObserver(observer) }
    }

    val profile = remember(instance.id, refreshTrigger) {
        LauncherProfiles.load()
        var p = LauncherProfiles.mainProfileJson.profiles.get(instance.id)
        if (p == null) {
            p = MinecraftProfile.getDefaultProfile()
            LauncherProfiles.mainProfileJson.profiles.put(instance.id, p)
            p.name = instance.name
            p.lastVersionId = instance.mcVersion
            p.gameDir = "instances/" + instance.id
            LauncherProfiles.write()
        } else if (p.gameDir == null) {
            p.gameDir = "instances/" + instance.id
            LauncherProfiles.write()
        }
        if (!isLaunchVersionForBase(p.lastVersionId, instance.mcVersion)) {
            p.lastVersionId = instance.mcVersion
            LauncherProfiles.write()
        }
        p
    }

    var selectedJavaVersion by remember(instance.id, refreshTrigger) { mutableStateOf(Tools.getConfiguredRuntime(profile)) }
    var launchVersion by remember(instance.id, refreshTrigger) { mutableStateOf(resolveLaunchVersion(profile, instance.mcVersion)) }
    var requiredJavaVersion by remember { mutableStateOf<Int?>(null) }
    var versionFilesInstalled by remember { mutableStateOf(false) }
    var selectedJavaInstalled by remember { mutableStateOf(false) }
    
    // Tab and sub-tab selection states
    var activeTab by remember { mutableStateOf(0) } // 0 = Wersja, 1 = Mody, 2 = Zasoby/Shadery
    
    // Tab 0 (Wersja) states
    var versionDetails by remember { mutableStateOf<VersionDetails?>(null) }
    var totalSizeOccupied by remember { mutableStateOf(0L) }
    
    // Tab 1 (Mody) states
    var modSubTab by remember { mutableStateOf(0) } // 0 = Zainstalowane, 1 = Nie zainstalowane
    val modsFolder = File(getInstanceDir(instance.id), "mods")
    var installedMods by remember { mutableStateOf(listOf<File>()) }
    var onlineMods by remember { mutableStateOf(listOf<ModItem>()) }
    var isSearchingOnlineMods by remember { mutableStateOf(false) }
    var onlineModsSearchQuery by remember { mutableStateOf("") }
    var modMetadataCache by remember { mutableStateOf(mapOf<String, ModItem>()) }
    var translatedDescCache by remember { mutableStateOf(mapOf<String, String>()) }
    var selectedModForDialog by remember { mutableStateOf<ModItem?>(null) }
    var isModDialogInstalled by remember { mutableStateOf(false) }

    // Tab 2 (Zasoby/Shadery) states
    var packType by remember { mutableStateOf(0) } // 0 = Zasoby, 1 = Shadery
    var packSubTab by remember { mutableStateOf(0) } // 0 = Zainstalowane, 1 = Nie zainstalowane
    val resourcePacksFolder = File(getInstanceDir(instance.id), "resourcepacks")
    val shaderPacksFolder = File(getInstanceDir(instance.id), "shaderpacks")
    var installedPacks by remember { mutableStateOf(listOf<File>()) }
    var onlinePacks by remember { mutableStateOf(listOf<ModItem>()) }
    var isSearchingOnlinePacks by remember { mutableStateOf(false) }
    var onlinePacksSearchQuery by remember { mutableStateOf("") }
    var selectedPackForDialog by remember { mutableStateOf<ModItem?>(null) }
    var isPackDialogInstalled by remember { mutableStateOf(false) }

    // Refresh function for installed directories
    fun refreshLists() {
        if (!modsFolder.exists()) modsFolder.mkdirs()
        val pFolder = if (packType == 0) resourcePacksFolder else shaderPacksFolder
        if (!pFolder.exists()) pFolder.mkdirs()

        scope.launch(Dispatchers.IO) {
            val validMods = modsFolder.listFiles { f -> f.isFile && f.name.endsWith(".jar", ignoreCase = true) }
                ?.filter { isValidZip(it) } ?: emptyList()

            val validPacks = pFolder.listFiles { f -> f.isFile && f.name.endsWith(".zip", ignoreCase = true) }
                ?.filter { isValidZip(it) } ?: emptyList()

            withContext(Dispatchers.Main) {
                installedMods = validMods
                installedPacks = validPacks
            }

            // Recalculate total size occupied
            val jarFile = File(Tools.DIR_HOME_VERSION, "${instance.mcVersion}/${instance.mcVersion}.jar")
            var size = if (jarFile.exists()) jarFile.length() else 0L
            val instFolder = getInstanceDir(instance.id)
            size += getDirectorySize(instFolder)
            withContext(Dispatchers.Main) {
                totalSizeOccupied = size
            }
        }
    }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch(Dispatchers.IO) {
                try {
                    val displayName = (Tools.getFileName(context, uri) ?: "").trim().ifEmpty {
                        "file_${System.currentTimeMillis()}"
                    }
                    val destinationFolder = when {
                        activeTab == 1 -> modsFolder
                        activeTab == 2 && packType == 0 -> resourcePacksFolder
                        activeTab == 2 && packType == 1 -> shaderPacksFolder
                        else -> null
                    }
                    if (destinationFolder != null) {
                        if (!destinationFolder.exists()) destinationFolder.mkdirs()
                        val destFile = File(destinationFolder, displayName)
                        context.contentResolver.openInputStream(uri)?.use { input ->
                            destFile.outputStream().use { output ->
                                input.copyTo(output)
                            }
                        }
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, context.getString(R.string.import_file_success, displayName), Toast.LENGTH_SHORT).show()
                            refreshLists()
                        }
                    }
                } catch (e: Exception) {
                    Log.e("OnyxLauncher", "Failed to import file", e)
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, context.getString(R.string.import_file_error, e.message), Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, instance.id, packType) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                refreshLists()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(instance.id) {
        withContext(Dispatchers.IO) {
            try {
                requiredJavaVersion = getRequiredJavaVersion(instance.mcVersion)
            } catch (e: Exception) {
                Log.e("OnyxLauncher", "Error loading required JRE version", e)
            }
        }
    }
    LaunchedEffect(instance.mcVersion, selectedJavaVersion, requiredJavaVersion, launchVersion, refreshTrigger) {
        val status = withContext(Dispatchers.IO) {
            val jar = File(Tools.DIR_HOME_VERSION, "${instance.mcVersion}/${instance.mcVersion}.jar")
            val json = File(Tools.DIR_HOME_VERSION, "${instance.mcVersion}/${instance.mcVersion}.json")
            val currentJreName = selectedJavaVersion.ifEmpty { Tools.getConfiguredRuntime(profile) }
            val effectiveJreName = runtimeForRequirement(currentJreName, requiredJavaVersion)
            val jreInstalled = try {
                Tools.isRuntimeInstalled(effectiveJreName)
            } catch (e: Exception) {
                false
            }
            val loaderInstalled = isLoaderInstalled(launchVersion, instance.mcVersion)
            Triple(jar.exists() && json.exists(), jreInstalled, loaderInstalled)
        }
        versionFilesInstalled = status.first
        selectedJavaInstalled = status.second
        val loaderInstalled = status.third
        ready = versionFilesInstalled && selectedJavaInstalled && loaderInstalled
    }
    // Version details loader
    LaunchedEffect(instance.mcVersion) {
        withContext(Dispatchers.IO) {
            try {
                val jsonFile = File(Tools.DIR_HOME_VERSION, "${instance.mcVersion}/${instance.mcVersion}.json")
                var type = ""
                var releaseDate = ""
                var reqJava = getRequiredJavaVersion(instance.mcVersion)
                var mainClass = ""
                var sizeBytes = 0L

                if (jsonFile.exists()) {
                    val versionObj = Tools.GLOBAL_GSON.fromJson(jsonFile.readText(), JMinecraftVersionList.Version::class.java)
                    type = versionObj.type?.uppercase() ?: ""
                    releaseDate = versionObj.releaseTime?.substringBefore("T") ?: ""
                    if (versionObj.javaVersion?.majorVersion != null) {
                        reqJava = versionObj.javaVersion.majorVersion
                    }
                    mainClass = versionObj.mainClass ?: ""
                    val clientDownload = versionObj.downloads?.get("client")
                    sizeBytes = clientDownload?.size?.toLong() ?: 0L
                }

                val normalizedVersionId = com.cannon.onyxlauncher.tasks.AsyncMinecraftDownloader.normalizeVersionId(instance.mcVersion)
                val listedVersion = com.cannon.onyxlauncher.tasks.AsyncMinecraftDownloader.getListedVersion(normalizedVersionId)
                if (listedVersion != null) {
                    if (type.isEmpty()) type = listedVersion.type?.uppercase() ?: ""
                    if (listedVersion.javaVersion != null) {
                        reqJava = listedVersion.javaVersion.majorVersion
                    }
                }

                if (sizeBytes == 0L) {
                    val jarFile = File(Tools.DIR_HOME_VERSION, "${instance.mcVersion}/${instance.mcVersion}.jar")
                    if (jarFile.exists()) {
                        sizeBytes = jarFile.length()
                    }
                }

                if (type.isEmpty()) type = "RELEASE"
                if (releaseDate.isEmpty()) releaseDate = ""
                if (mainClass.isEmpty()) mainClass = "net.minecraft.client.main.Main"

                versionDetails = VersionDetails(
                    id = instance.mcVersion,
                    releaseType = type,
                    releaseDate = releaseDate,
                    requiredJava = reqJava,
                    mainClass = mainClass,
                    fileSizeBytes = sizeBytes
                )

                // Initialize total size occupied
                val jarFile = File(Tools.DIR_HOME_VERSION, "${instance.mcVersion}/${instance.mcVersion}.jar")
                var initialSize = if (jarFile.exists()) jarFile.length() else 0L
                val instFolder = getInstanceDir(instance.id)
                initialSize += getDirectorySize(instFolder)
                totalSizeOccupied = initialSize
            } catch (e: Exception) {
                Log.e("OnyxLauncher", "Error loading version details", e)
            }
        }
    }


    LaunchedEffect(instance.id, packType) {
        refreshLists()
    }

    // Installed mods metadata background searcher
    LaunchedEffect(installedMods) {
        withContext(Dispatchers.IO) {
            val api = CommonApi(context.getString(R.string.curseforge_api_key))
            installedMods.forEach { file ->
                val cleanName = cleanModName(file.name)
                if (!modMetadataCache.containsKey(file.name)) {
                    try {
                        val localMetadata = parseLocalModMetadata(file, context)
                        if (localMetadata != null) {
                            modMetadataCache = modMetadataCache + (file.name to localMetadata)
                        } else {
                            val filters = SearchFilters().apply {
                                name = cleanName
                                mcVersion = instance.mcVersion
                                isModpack = false
                                projectType = "mod"
                            }
                            val results = api.searchMod(filters)
                            if (results != null && results.results.isNotEmpty()) {
                                val bestMatch = results.results.firstOrNull()
                                if (bestMatch != null) {
                                    modMetadataCache = modMetadataCache + (file.name to bestMatch)
                                    val targetLang = Locale.getDefault().language
                                    val translated = translateText(bestMatch.description, targetLang)
                                    translatedDescCache = translatedDescCache + (file.name to translated)
                                }
                            } else {
                                modMetadataCache = modMetadataCache + (file.name to ModItem(
                                    com.cannon.onyxlauncher.modloaders.modpacks.models.Constants.SOURCE_MODRINTH,
                                    false,
                                    file.name,
                                    file.name,
                                    context.getString(R.string.local_mod_file_desc),
                                    ""
                                ))
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("OnyxLauncher", "Error loading installed mod details: $cleanName", e)
                    }
                    delay(250)
                }
            }
        }
    }

    // Online mods searcher
    LaunchedEffect(instance.id, instance.mcVersion, onlineModsSearchQuery, launchVersion) {
        isSearchingOnlineMods = true
        val launchVer = launchVersion ?: instance.mcVersion
        val instanceLoader = modLoaderLabel(launchVer, instance.mcVersion).lowercase(Locale.ROOT)
        withContext(Dispatchers.IO) {
            try {
                val api = CommonApi(context.getString(R.string.curseforge_api_key))
                val filters = SearchFilters().apply {
                    name = onlineModsSearchQuery
                    mcVersion = instance.mcVersion
                    isModpack = false
                    projectType = "mod"
                    modLoader = if (instanceLoader != "vanilla") instanceLoader else null
                }
                val results = api.searchMod(filters)
                onlineMods = results?.results?.toList() ?: emptyList()
            } catch (e: Exception) {
                Log.e("OnyxLauncher", "Error searching online mods", e)
            } finally {
                isSearchingOnlineMods = false
            }
        }
    }

    // Online packs searcher
    LaunchedEffect(instance.mcVersion, onlinePacksSearchQuery, packType) {
        isSearchingOnlinePacks = true
        withContext(Dispatchers.IO) {
            try {
                val api = CommonApi(context.getString(R.string.curseforge_api_key))
                val filters = SearchFilters().apply {
                    name = onlinePacksSearchQuery
                    mcVersion = instance.mcVersion
                    isModpack = false
                    projectType = if (packType == 0) "resourcepack" else "shader"
                }
                val results = api.searchMod(filters)
                onlinePacks = results?.results?.filter { !it.isModpack }?.toList() ?: emptyList()
            } catch (e: Exception) {
                Log.e("OnyxLauncher", "Error searching online packs", e)
            } finally {
                isSearchingOnlinePacks = false
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth(), 
            horizontalArrangement = Arrangement.SpaceBetween, 
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                var profileIcon by remember(instance.id) { mutableStateOf<android.graphics.Bitmap?>(null) }
                LaunchedEffect(instance.id) {
                    withContext(Dispatchers.IO) {
                        profileIcon = getProfileIcon(instance.id)
                    }
                }
                Box(
                    modifier = Modifier.size(56.dp).background(AccentColor.copy(alpha = 0.2f), RoundedCornerShape(12.dp)), 
                    contentAlignment = Alignment.Center
                ) {
                    if (profileIcon != null) {
                        Image(
                            bitmap = profileIcon!!.asImageBitmap(),
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(12.dp)),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Icon(Icons.Default.Home, contentDescription = null, tint = AccentColor, modifier = Modifier.size(32.dp))
                    }
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(text = instance.name, color = TextPrimary, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    Text(text = "Minecraft ${instance.mcVersion}", color = TextSecondary, fontSize = 14.sp)
                }
            }
            IconButton(onClick = onDelete) { 
                Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.global_delete_confirm), tint = Color.Red) 
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Tab Row
        TabRow(
            selectedTabIndex = activeTab,
            containerColor = Color.Transparent,
            contentColor = AccentColor,
            divider = {}
        ) {
            listOf(
                stringResource(R.string.tab_version),
                stringResource(R.string.tab_mods),
                stringResource(R.string.tab_resources_shaders)
            ).forEachIndexed { index, title ->
                Tab(
                    selected = activeTab == index,
                    onClick = { activeTab = index }
                ) {
                    Text(
                        text = title,
                        modifier = Modifier.padding(12.dp),
                        color = if (activeTab == index) AccentColor else TextSecondary,
                        fontWeight = if (activeTab == index) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))

        // Main Tab Content
        Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
            when (activeTab) {
                0 -> { // WERSJA TAB
                    Column(modifier = Modifier.fillMaxSize()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = stringResource(R.string.game_environment_details), color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                            IconButton(onClick = onShowSettings) {
                                Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.instance_settings_title), tint = AccentColor)
                            }
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            color = CardBg,
                            shape = RoundedCornerShape(16.dp),
                            border = BorderStroke(1.dp, StrokeColor)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                DetailRow(stringResource(R.string.detail_version_id), instance.mcVersion, isFirst = true)
                                DetailRow(stringResource(R.string.detail_release_type), versionDetails?.releaseType ?: "RELEASE")
                                DetailRow(stringResource(R.string.detail_release_date), versionDetails?.let { if (it.releaseDate.isEmpty()) stringResource(R.string.no_data_placeholder) else it.releaseDate } ?: stringResource(R.string.loading_placeholder))
                                
                                val javaStatusString = remember(versionDetails, selectedJavaVersion, refreshTrigger) {
                                    versionDetails?.let { details ->
                                        val reqVersion = details.requiredJava
                                        val recommendedJre = NewJREUtil.getRecommendedInternalRuntimeName(reqVersion)
                                        val isJreInstalled = try {
                                            Tools.isRuntimeInstalled(recommendedJre)
                                        } catch (e: Exception) {
                                            false
                                        }
                                        if (isJreInstalled) {
                                            context.getString(R.string.java_status_installed, reqVersion)
                                        } else {
                                            context.getString(R.string.java_status_not_installed, reqVersion)
                                        }
                                    } ?: context.getString(R.string.loading_placeholder)
                                }
                                DetailRow(stringResource(R.string.detail_required_java), javaStatusString)
                                DetailRow(stringResource(R.string.detail_loader), modLoaderLabel(launchVersion, instance.mcVersion))
                                DetailRow(stringResource(R.string.detail_main_class), versionDetails?.mainClass ?: stringResource(R.string.loading_placeholder))
                                
                                val formattedJarSize = remember(totalSizeOccupied) {
                                     if (totalSizeOccupied > 0L) {
                                         String.format("%.2f MB", totalSizeOccupied.toDouble() / (1024.0 * 1024.0))
                                     } else {
                                         ""
                                     }
                                 }
                                 DetailRow(stringResource(R.string.detail_occupied_size), if (formattedJarSize.isEmpty()) stringResource(R.string.no_data_placeholder) else formattedJarSize)
                            }
                        }
                    }
                }
                1 -> { // MODY TAB
                    Column(modifier = Modifier.fillMaxSize()) {
                        // Sub Tab selection: Installed vs Not Installed
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf(
                                stringResource(R.string.tab_installed),
                                stringResource(R.string.tab_not_installed)
                            ).forEachIndexed { index, name ->
                                val selected = modSubTab == index
                                Surface(
                                    color = if (selected) AccentColor.copy(alpha = 0.2f) else Color.Transparent,
                                    shape = RoundedCornerShape(8.dp),
                                    border = BorderStroke(1.dp, if (selected) AccentColor else StrokeColor),
                                    modifier = Modifier.weight(1f).clickable { modSubTab = index }
                                ) {
                                    Text(
                                        text = name,
                                        color = if (selected) AccentColor else TextSecondary,
                                        modifier = Modifier.padding(vertical = 10.dp),
                                        textAlign = TextAlign.Center,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp
                                    )
                                }
                            }
                        }

                        if (modSubTab == 0) { // Installed mods
                            if (installedMods.isEmpty()) {
                                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Text(text = stringResource(R.string.no_installed_mods), color = TextSecondary)
                                }
                            } else {
                                LazyColumn(modifier = Modifier.fillMaxSize()) {
                                    items(installedMods) { file ->
                                        val metadata = modMetadataCache[file.name]
                                        val desc = translatedDescCache[file.name] ?: metadata?.description ?: stringResource(R.string.loading_description)
                                        Surface(
                                            modifier = Modifier.fillMaxWidth().clickable {
                                                if (metadata != null) {
                                                    selectedModForDialog = metadata
                                                    isModDialogInstalled = true
                                                } else {
                                                    selectedModForDialog = ModItem(
                                                        com.cannon.onyxlauncher.modloaders.modpacks.models.Constants.SOURCE_MODRINTH,
                                                        false,
                                                        file.name,
                                                        file.name,
                                                        context.getString(R.string.local_mod_file_desc),
                                                        ""
                                                    )
                                                    isModDialogInstalled = true
                                                }
                                            },
                                            color = Color.Transparent
                                        ) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Box(
                                                    modifier = Modifier.size(40.dp).background(AccentColor.copy(alpha = 0.1f), RoundedCornerShape(8.dp)),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    if (metadata?.imageUrl != null && metadata.imageUrl.isNotEmpty()) {
                                                        OnyxAsyncImage(url = metadata.imageUrl, modifier = Modifier.fillMaxSize(), roundedCorners = 8.dp)
                                                    } else {
                                                        Icon(Icons.Default.Build, contentDescription = null, tint = AccentColor, modifier = Modifier.size(24.dp))
                                                    }
                                                }
                                                Spacer(modifier = Modifier.width(12.dp))
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(text = metadata?.title ?: file.name, color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                                    Text(text = desc, color = TextSecondary, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                                }
                                                Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.button_uninstall), tint = Color.Red.copy(alpha = 0.6f), modifier = Modifier.size(20.dp).clickable {
                                                    val lowerName = file.name.lowercase(Locale.ROOT)
                                                    if (lowerName.contains("fabric") && lowerName.contains("language") && lowerName.contains("kotlin")) {
                                                        Toast.makeText(context, context.getString(R.string.required_mod_for_support), Toast.LENGTH_LONG).show()
                                                    } else {
                                                        file.delete()
                                                        refreshLists()
                                                        Toast.makeText(context, context.getString(R.string.mod_uninstalled_toast), Toast.LENGTH_SHORT).show()
                                                    }
                                                })
                                            }
                                        }
                                        HorizontalDivider(color = StrokeColor)
                                    }
                                }
                            }
                        } else { // Not Installed online mods
                            Column(modifier = Modifier.fillMaxSize()) {
                                OutlinedTextField(
                                    value = onlineModsSearchQuery,
                                    onValueChange = { onlineModsSearchQuery = it },
                                    placeholder = { Text(stringResource(R.string.search_mods_placeholder)) },
                                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = TextSecondary) },
                                    modifier = Modifier.fillMaxWidth().height(52.dp),
                                    shape = RoundedCornerShape(8.dp),
                                    colors = OutlinedTextFieldDefaults.colors(focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                if (isSearchingOnlineMods) {
                                    Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                                        CircularProgressIndicator(color = AccentColor)
                                    }
                                } else if (onlineMods.isEmpty()) {
                                    Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                                        Text(text = stringResource(R.string.no_compatible_mods_found), color = TextSecondary)
                                    }
                                } else {
                                    LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f)) {
                                        items(onlineMods) { mod ->
                                            val isDownloaded = installedMods.any { cleanModName(it.name).equals(cleanModName(mod.title), ignoreCase = true) || it.name.contains(mod.title, ignoreCase = true) }
                                            Row(
                                                modifier = Modifier.fillMaxWidth().clickable {
                                                    selectedModForDialog = mod
                                                    isModDialogInstalled = isDownloaded
                                                }.padding(vertical = 12.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                OnyxAsyncImage(url = mod.imageUrl, modifier = Modifier.size(40.dp), roundedCorners = 8.dp)
                                                Spacer(modifier = Modifier.width(12.dp))
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(text = mod.title, color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                                    Text(text = mod.description, color = TextSecondary, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                                }
                                                if (isDownloaded) {
                                                    Icon(Icons.Default.Check, contentDescription = stringResource(R.string.tab_installed), tint = MicrosoftGreen, modifier = Modifier.size(20.dp))
                                                } else {
                                                    Icon(Icons.Default.PlayArrow, contentDescription = stringResource(R.string.button_install), tint = AccentColor, modifier = Modifier.size(20.dp))
                                                }
                                            }
                                            HorizontalDivider(color = StrokeColor)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                2 -> { // ZASOBY/SHADERY TAB
                    Column(modifier = Modifier.fillMaxSize()) {
                        // Pack type selector: Resource Packs vs Shaders
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf(stringResource(R.string.tab_resources), stringResource(R.string.tab_shaders)).forEachIndexed { index, name ->
                                val selected = packType == index
                                Surface(
                                    color = if (selected) AccentColor.copy(alpha = 0.2f) else Color.Transparent,
                                    shape = RoundedCornerShape(8.dp),
                                    border = BorderStroke(1.dp, if (selected) AccentColor else StrokeColor),
                                    modifier = Modifier.weight(1f).clickable { packType = index }
                                ) {
                                    Text(
                                        text = name,
                                        color = if (selected) AccentColor else TextSecondary,
                                        modifier = Modifier.padding(vertical = 10.dp),
                                        textAlign = TextAlign.Center,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp
                                    )
                                }
                            }
                        }

                        // Sub Tab selection: Installed vs Not Installed
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf(stringResource(R.string.tab_installed), stringResource(R.string.tab_not_installed)).forEachIndexed { index, name ->
                                val selected = packSubTab == index
                                Surface(
                                    color = if (selected) AccentColor.copy(alpha = 0.2f) else Color.Transparent,
                                    shape = RoundedCornerShape(8.dp),
                                    border = BorderStroke(1.dp, if (selected) AccentColor else StrokeColor),
                                    modifier = Modifier.weight(1f).clickable { packSubTab = index }
                                ) {
                                    Text(
                                        text = name,
                                        color = if (selected) AccentColor else TextSecondary,
                                        modifier = Modifier.padding(vertical = 10.dp),
                                        textAlign = TextAlign.Center,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp
                                    )
                                }
                            }
                        }

                        if (packSubTab == 0) { // Installed packs
                            if (installedPacks.isEmpty()) {
                                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Text(text = if (packType == 0) stringResource(R.string.no_installed_resource_packs) else stringResource(R.string.no_installed_shaders), color = TextSecondary)
                                }
                            } else {
                                LazyColumn(modifier = Modifier.fillMaxSize()) {
                                    items(installedPacks) { file ->
                                        val metadata = remember(file.name, packType) {
                                            getPackMetadata(file, packType == 1)
                                        }
                                        Surface(
                                            modifier = Modifier.fillMaxWidth().clickable {
                                                selectedPackForDialog = ModItem(
                                                    com.cannon.onyxlauncher.modloaders.modpacks.models.Constants.SOURCE_MODRINTH,
                                                    false,
                                                    file.name,
                                                    metadata.title,
                                                    metadata.description,
                                                    ""
                                                )
                                                isPackDialogInstalled = true
                                            },
                                            color = Color.Transparent
                                        ) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Box(
                                                    modifier = Modifier.size(40.dp).clip(RoundedCornerShape(8.dp)).background(AccentColor.copy(alpha = 0.1f)),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    if (metadata.iconBitmap != null) {
                                                        Image(
                                                            bitmap = metadata.iconBitmap.asImageBitmap(),
                                                            contentDescription = null,
                                                            modifier = Modifier.fillMaxSize(),
                                                            contentScale = ContentScale.Crop
                                                        )
                                                    } else {
                                                        Icon(
                                                            imageVector = if (packType == 0) Icons.Default.Folder else Icons.Default.WbSunny,
                                                            contentDescription = null,
                                                            tint = AccentColor,
                                                            modifier = Modifier.size(24.dp)
                                                        )
                                                    }
                                                }
                                                Spacer(modifier = Modifier.width(12.dp))
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(text = metadata.title, color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                                    Text(
                                                        text = "${metadata.description} • ${String.format("%.2f MB", file.length().toDouble() / (1024.0 * 1024.0))}",
                                                        color = TextSecondary,
                                                        fontSize = 12.sp,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                }
                                                Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.button_uninstall), tint = Color.Red.copy(alpha = 0.6f), modifier = Modifier.size(20.dp).clickable {
                                                    file.delete()
                                                    refreshLists()
                                                    Toast.makeText(context, context.getString(R.string.modpack_uninstalled_toast), Toast.LENGTH_SHORT).show()
                                                })
                                            }
                                        }
                                        HorizontalDivider(color = StrokeColor)
                                    }
                                }
                            }
                        } else { // Not Installed online packs
                            Column(modifier = Modifier.fillMaxSize()) {
                                OutlinedTextField(
                                    value = onlinePacksSearchQuery,
                                    onValueChange = { onlinePacksSearchQuery = it },
                                    placeholder = { Text(if (packType == 0) stringResource(R.string.search_resource_packs_placeholder) else stringResource(R.string.search_shaders_placeholder)) },
                                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = TextSecondary) },
                                    modifier = Modifier.fillMaxWidth().height(52.dp),
                                    shape = RoundedCornerShape(8.dp),
                                    colors = OutlinedTextFieldDefaults.colors(focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                if (isSearchingOnlinePacks) {
                                    Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                                        CircularProgressIndicator(color = AccentColor)
                                    }
                                } else if (onlinePacks.isEmpty()) {
                                    Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                                        Text(text = stringResource(R.string.search_no_results), color = TextSecondary)
                                    }
                                } else {
                                    LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f)) {
                                        items(onlinePacks) { pack ->
                                            val isDownloaded = installedPacks.any { it.name.contains(pack.title, ignoreCase = true) || cleanModName(it.name).equals(cleanModName(pack.title), ignoreCase = true) }
                                            Row(
                                                modifier = Modifier.fillMaxWidth().clickable {
                                                    selectedPackForDialog = pack
                                                    isPackDialogInstalled = isDownloaded
                                                }.padding(vertical = 12.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                OnyxAsyncImage(url = pack.imageUrl, modifier = Modifier.size(40.dp), roundedCorners = 8.dp)
                                                Spacer(modifier = Modifier.width(12.dp))
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(text = pack.title, color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                                    Text(text = pack.description, color = TextSecondary, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                                }
                                                if (isDownloaded) {
                                                    Icon(Icons.Default.Check, contentDescription = stringResource(R.string.tab_installed), tint = MicrosoftGreen, modifier = Modifier.size(20.dp))
                                                } else {
                                                    Icon(Icons.Default.PlayArrow, contentDescription = stringResource(R.string.button_install), tint = AccentColor, modifier = Modifier.size(20.dp))
                                                }
                                            }
                                            HorizontalDivider(color = StrokeColor)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            if ((activeTab == 1 && modSubTab == 0) || (activeTab == 2 && packSubTab == 0)) {
                FloatingActionButton(
                    onClick = {
                        filePickerLauncher.launch("*/*")
                    },
                    containerColor = AccentColor,
                    contentColor = Color.White,
                    modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = stringResource(R.string.content_desc_add_file))
                }
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Launch Game Buttons
        Row(
            modifier = Modifier.fillMaxWidth().height(64.dp), 
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = {
                    val activity = context as? Activity
                    if (activity == null) {
                        Toast.makeText(context, context.getString(R.string.error_missing_activity_context), Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    val downloadListener = object : com.cannon.onyxlauncher.progresskeeper.ProgressListener {
                        override fun onProgressStarted() {
                            downloadState = DownloadState(isDownloading = true, fileName = "Rozpoczynanie pobierania...", progress = 0f)
                        }
                        override fun onProgressUpdated(progress: Int, resid: Int, vararg va: Any?) {
                            val message = if (resid != -1 && resid != 0) {
                                context.getString(resid, *va)
                            } else if (va.isNotEmpty() && va[0] != null) {
                                va[0].toString()
                            } else {
                                context.getString(R.string.downloading_files)
                            }
                            downloadState = DownloadState(isDownloading = true, fileName = message, progress = progress.toFloat() / 100f)
                        }
                        override fun onProgressEnded() {
                        }
                    }

                    fun removeDownloadListeners() {
                        com.cannon.onyxlauncher.progresskeeper.ProgressKeeper.removeListener(com.kdt.mcgui.ProgressLayout.DOWNLOAD_MINECRAFT, downloadListener)
                        com.cannon.onyxlauncher.progresskeeper.ProgressKeeper.removeListener(com.kdt.mcgui.ProgressLayout.UNPACK_RUNTIME, downloadListener)
                    }

                    if (!selectedJavaInstalled) {
                        val selectedRuntimeForInstall = selectedJavaVersion.ifEmpty { Tools.getConfiguredRuntime(profile) }
                        val runtimeToInstall = runtimeForRequirement(selectedRuntimeForInstall, requiredJavaVersion)
                        com.cannon.onyxlauncher.progresskeeper.ProgressKeeper.addListener(com.kdt.mcgui.ProgressLayout.DOWNLOAD_MINECRAFT, downloadListener)
                        com.cannon.onyxlauncher.progresskeeper.ProgressKeeper.addListener(com.kdt.mcgui.ProgressLayout.UNPACK_RUNTIME, downloadListener)
                        scope.launch {
                            downloadState = DownloadState(isDownloading = true, fileName = context.getString(R.string.status_installing_runtime, runtimeToInstall), progress = 0f)
                            val installed = withContext(Dispatchers.IO) {
                                try {
                                    NewJREUtil.installInternalRuntime(activity, runtimeToInstall)
                                    true
                                } catch (e: Exception) {
                                    Log.e("OnyxLauncher", "Java install failed: $runtimeToInstall", e)
                                    withContext(Dispatchers.Main) {
                                        Toast.makeText(context, context.getString(R.string.java_install_error, e.message), Toast.LENGTH_LONG).show()
                                    }
                                    false
                                }
                            }
                            removeDownloadListeners()
                            downloadState = DownloadState(isDownloading = false)
                            if (installed) {
                                LauncherProfiles.load()
                                val p = LauncherProfiles.mainProfileJson.profiles.get(instance.id) ?: profile
                                p.javaDir = Tools.LAUNCHERPROFILES_RTPREFIX + runtimeToInstall
                                LauncherProfiles.mainProfileJson.profiles.put(instance.id, p)
                                LauncherProfiles.write()
                                selectedJavaVersion = runtimeToInstall
                                selectedJavaInstalled = Tools.isRuntimeInstalled(runtimeToInstall)
                                ready = versionFilesInstalled && selectedJavaInstalled
                                Toast.makeText(context, "$runtimeToInstall zainstalowana dla tej wersji.", Toast.LENGTH_SHORT).show()
                            }
                        }
                        return@Button
                    }

                    if (selectedAccount == null || !selectedAccount.isPremium) {
                        Toast.makeText(context, context.getString(R.string.login_microsoft_hint), Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    val versionList = com.cannon.onyxlauncher.extra.ExtraCore.getValue(com.cannon.onyxlauncher.extra.ExtraConstants.RELEASE_TABLE) as? com.cannon.onyxlauncher.JMinecraftVersionList
                    if (versionList == null) {
                        Toast.makeText(context, context.getString(R.string.loading_versions_retry), Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    LauncherProfiles.load()
                    val profileForLaunch = LauncherProfiles.mainProfileJson.profiles.get(instance.id) ?: profile
                    val launchVersionForStart = resolveLaunchVersion(profileForLaunch, instance.mcVersion)
                    val normalizedVersionId = com.cannon.onyxlauncher.tasks.AsyncMinecraftDownloader.normalizeVersionId(launchVersionForStart)
                    val mcVersion = com.cannon.onyxlauncher.tasks.AsyncMinecraftDownloader.getListedVersion(normalizedVersionId)

                    fun runDownloadAndLaunch() {
                        com.cannon.onyxlauncher.progresskeeper.ProgressKeeper.addListener(com.kdt.mcgui.ProgressLayout.DOWNLOAD_MINECRAFT, downloadListener)
                        com.cannon.onyxlauncher.progresskeeper.ProgressKeeper.addListener(com.kdt.mcgui.ProgressLayout.UNPACK_RUNTIME, downloadListener)

                        scope.launch {
                            val accountReady = withContext(Dispatchers.IO) {
                                val acc = MinecraftAccount.load(selectedAccount.username)
                                if (acc == null) {
                                    withContext(Dispatchers.Main) {
                                        Toast.makeText(context, context.getString(R.string.account_load_error), Toast.LENGTH_LONG).show()
                                    }
                                    return@withContext false
                                }
                                OnyxProfile.setCurrentProfile(context, acc)
                                
                                LauncherProfiles.load()
                                var profile = LauncherProfiles.mainProfileJson.profiles.get(instance.id)
                                if (profile == null) {
                                    profile = MinecraftProfile.getDefaultProfile()
                                    LauncherProfiles.mainProfileJson.profiles.put(instance.id, profile)
                                }
                                val launchVersionToSave = resolveLaunchVersion(profile, instance.mcVersion)
                                profile.name = instance.name
                                profile.lastVersionId = launchVersionToSave
                                if (selectedJavaVersion.isNotEmpty()) {
                                    // Verify the selected JRE is actually installed before committing it
                                    val jreToUse = if (Tools.isRuntimeInstalled(selectedJavaVersion)) {
                                        selectedJavaVersion
                                    } else {
                                        // JRE not installed — fall back to Internal-21 which is stable
                                        Log.w("OnyxLauncher", "JRE $selectedJavaVersion not installed, falling back to Internal-21")
                                        "Internal-21"
                                    }
                                    profile.javaDir = Tools.LAUNCHERPROFILES_RTPREFIX + jreToUse
                                    /* if ("vulkan_zink" == profile.pojavRendererName) {
                                        val glInfo = com.cannon.onyxlauncher.utils.GLInfoUtils.getGlInfo()
                                        if (glInfo != null && glInfo.renderer != null && (
                                            glInfo.renderer.contains("Adreno (TM) 7") ||
                                            glInfo.renderer.contains("Adreno (TM) 8") ||
                                            glInfo.renderer.contains("Adreno (TM) 9")) &&
                                            LauncherPreferences.PREF_ZINK_PREFER_SYSTEM_DRIVER) {
                                            Log.w("OnyxLauncher", "Forcing opengles2 — Zink crashes on system driver for ${glInfo.renderer}")
                                            profile.pojavRendererName = "opengles2"
                                        }
                                    } */
                                }

                                if (profile.ramAllocation == null) {
                                    profile.ramAllocation = LauncherPreferences.PREF_RAM_ALLOCATION
                                }
                                if (profile.resolutionScale == null) {
                                    profile.resolutionScale = (LauncherPreferences.PREF_SCALE_FACTOR * 100).toInt()
                                }
                                if (profile.alternateSurface == null) {
                                    profile.alternateSurface = false
                                }
                                if (profile.forceVsync == null) {
                                    profile.forceVsync = LauncherPreferences.PREF_FORCE_VSYNC
                                }
                                
                                LauncherPreferences.DEFAULT_PREF.edit()
                                    .putString(LauncherPreferences.PREF_KEY_CURRENT_PROFILE, instance.id)
                                    .commit()
                                    
                                if (modLoaderLabel(launchVersionToSave, instance.mcVersion) != "Vanilla") {
                                    try {
                                        ensureFabricLanguageKotlin(context, instance.id, instance.mcVersion)
                                    } catch (e: Exception) {
                                        Log.e("OnyxLauncher", "Failed to auto-download Fabric Language Kotlin during launch", e)
                                    }
                                    try {
                                        ensureSodiumForIris(context, instance.id, instance.mcVersion)
                                    } catch (e: Exception) {
                                        Log.e("OnyxLauncher", "Failed to auto-download Sodium during launch", e)
                                    }
                                }

                                LauncherProfiles.write()
                                true
                            }
                            if (!accountReady) {
                                com.cannon.onyxlauncher.progresskeeper.ProgressKeeper.removeListener(com.kdt.mcgui.ProgressLayout.DOWNLOAD_MINECRAFT, downloadListener)
                                com.cannon.onyxlauncher.progresskeeper.ProgressKeeper.removeListener(com.kdt.mcgui.ProgressLayout.UNPACK_RUNTIME, downloadListener)
                                downloadState = DownloadState(isDownloading = false)
                                return@launch
                            }

                            val downloader = com.cannon.onyxlauncher.tasks.MinecraftDownloader()
                            downloader.start(activity, mcVersion, normalizedVersionId, object : com.cannon.onyxlauncher.tasks.AsyncMinecraftDownloader.DoneListener {
                                override fun onDownloadDone() {
                                    scope.launch(Dispatchers.IO) {
                                        try {
                                            val loader = modLoaderLabel(launchVersionForStart, instance.mcVersion)
                                            if (loader == "Quilt" || loader == "Fabric") {
                                                generateFabricLoaderOverrides(instance.id)
                                            }
                                        } catch (e: Exception) {
                                            Log.e("OnyxLauncher", "Error generating Fabric loader overrides", e)
                                        }
                                        withContext(Dispatchers.Main) {
                                            com.cannon.onyxlauncher.progresskeeper.ProgressKeeper.removeListener(com.kdt.mcgui.ProgressLayout.DOWNLOAD_MINECRAFT, downloadListener)
                                            com.cannon.onyxlauncher.progresskeeper.ProgressKeeper.removeListener(com.kdt.mcgui.ProgressLayout.UNPACK_RUNTIME, downloadListener)
                                            downloadState = DownloadState(isDownloading = false)

                                            LauncherProfiles.load()
                                            val p = LauncherProfiles.mainProfileJson.profiles.get(instance.id)
                                            if (p != null) {
                                                selectedJavaVersion = Tools.getConfiguredRuntime(p)
                                                launchVersion = resolveLaunchVersion(p, instance.mcVersion)
                                            }

                                            onLaunch(launchVersionForStart)
                                        }
                                    }
                                }


                                override fun onDownloadFailed(throwable: Throwable) {
                                    activity.runOnUiThread {
                                        com.cannon.onyxlauncher.progresskeeper.ProgressKeeper.removeListener(com.kdt.mcgui.ProgressLayout.DOWNLOAD_MINECRAFT, downloadListener)
                                        com.cannon.onyxlauncher.progresskeeper.ProgressKeeper.removeListener(com.kdt.mcgui.ProgressLayout.UNPACK_RUNTIME, downloadListener)
                                        downloadState = DownloadState(isDownloading = false)
                                        Toast.makeText(context, context.getString(R.string.game_prepare_error, throwable.message), Toast.LENGTH_LONG).show()
                                    }
                                }
                            })
                        }
                    }

                    fun triggerLaunch() {
                        scope.launch {
                            val acc = withContext(Dispatchers.IO) {
                                MinecraftAccount.load(selectedAccount.username)
                            }
                            if (acc != null && acc.isMicrosoft && System.currentTimeMillis() > acc.expiresAt) {
                                downloadState = DownloadState(isDownloading = true, fileName = context.getString(R.string.refreshing_microsoft_session), progress = 0f)
                                val bgLogin = MicrosoftBackgroundLogin(true, acc.msaRefreshToken)
                                bgLogin.performLogin(
                                    { step ->
                                        downloadState = DownloadState(isDownloading = true, fileName = context.getString(R.string.refreshing_microsoft_session), progress = step.toFloat() / 5f)
                                    },
                                    { refreshedAcc ->
                                        downloadState = DownloadState(isDownloading = false)
                                        runDownloadAndLaunch()
                                    },
                                    { error ->
                                        downloadState = DownloadState(isDownloading = false)
                                        val errMsg = if (error is PresentedException) error.toString(context) else error.message ?: context.getString(R.string.unknown_error)
                                        Toast.makeText(context, context.getString(R.string.session_expired_login_microsoft, errMsg), Toast.LENGTH_SHORT).show()
                                    }
                                )
                            } else {
                                runDownloadAndLaunch()
                            }
                        }
                    }

                    val loader = modLoaderLabel(launchVersionForStart, instance.mcVersion)
                    if (loader != "Vanilla" && !isLoaderInstalled(launchVersionForStart, instance.mcVersion)) {
                        scope.launch {
                            downloadState = DownloadState(isDownloading = true, fileName = context.getString(R.string.preparing_engine, loader), progress = 0f)
                            try {
                                var installerFile: File? = null
                                var targetLaunchVersion = launchVersionForStart
                                withContext(Dispatchers.IO) {
                                    when (loader) {
                                        "Fabric" -> {
                                            targetLaunchVersion = installFabricLoaderForInstance(instance.id, instance.mcVersion)
                                        }
                                        "Quilt" -> {
                                            targetLaunchVersion = installQuiltLoaderForInstance(instance.id, instance.mcVersion)
                                        }
                                        "Forge" -> {
                                            val prep = prepareForgeLoaderForInstance(instance.id, instance.mcVersion)
                                            targetLaunchVersion = prep.launchVersion
                                            installerFile = prep.installerFile
                                        }
                                        "NeoForge" -> {
                                            val prep = prepareNeoForgeLoaderForInstance(instance.id, instance.mcVersion)
                                            targetLaunchVersion = prep.launchVersion
                                            installerFile = prep.installerFile
                                        }
                                    }
                                }
                                downloadState = DownloadState(isDownloading = false)

                                installerFile?.let { file ->
                                    val targetMcJava = try {
                                        com.cannon.onyxlauncher.Tools.getVersionInfo(instance.mcVersion)?.javaVersion?.majorVersion ?: 8
                                    } catch (e: Exception) {
                                        8
                                    }
                                    ensureJreForInstaller(context, file, targetMcJava)
                                    val installIntent = Intent(context, JavaGUILauncherActivity::class.java)
                                    installIntent.putExtra("targetJavaVersion", targetMcJava)
                                    if (loader == "NeoForge" || targetMcJava >= 17) {
                                        com.cannon.onyxlauncher.modloaders.ForgeUtils.addCliInstallArgs(
                                            installIntent,
                                            file,
                                            com.cannon.onyxlauncher.Tools.DIR_GAME_NEW
                                        )
                                    } else {
                                        com.cannon.onyxlauncher.modloaders.ForgeUtils.addAutoInstallArgs(
                                            installIntent,
                                            file,
                                            targetLaunchVersion
                                        )
                                    }
                                    context.startActivity(installIntent)
                                } ?: run {
                                    triggerLaunch()
                                }
                            } catch (e: Exception) {
                                Log.e("OnyxLauncher", "Failed to auto-install loader $loader", e)
                                Toast.makeText(context, context.getString(R.string.engine_install_error, e.message), Toast.LENGTH_LONG).show()
                                downloadState = DownloadState(isDownloading = false)
                            }
                        }
                    } else {
                        triggerLaunch()
                    }

                }, 
                modifier = Modifier.weight(1f).fillMaxHeight(), 
                colors = ButtonDefaults.buttonColors(containerColor = if (ready) MicrosoftGreen else AccentColor),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text(
                    text = when {
                        !selectedJavaInstalled -> stringResource(R.string.button_install_java)
                        ready -> stringResource(R.string.button_play_game)
                        else -> stringResource(R.string.button_download_play)
                    },
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 18.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            OutlinedButton(
                onClick = onShowLogs, 
                modifier = Modifier.width(60.dp).fillMaxHeight(), 
                shape = RoundedCornerShape(16.dp)
            ) { 
                Icon(Icons.Default.Info, contentDescription = stringResource(R.string.game_logs_title)) 
            }
        }
    }

    // Dialog details
    selectedModForDialog?.let { mod ->
        ModOrPackDetailDialog(
            item = mod,
            instanceId = instance.id,
            instanceMcVersion = instance.mcVersion,
            destinationDir = modsFolder,
            isInstalled = isModDialogInstalled,
            onDismiss = { selectedModForDialog = null },
            onRefresh = {
                refreshLists()
                LauncherProfiles.load()
                launchVersion = resolveLaunchVersion(LauncherProfiles.mainProfileJson.profiles[instance.id], instance.mcVersion)
            },
            modMetadataCache = modMetadataCache
        )
    }

    selectedPackForDialog?.let { pack ->
        ModOrPackDetailDialog(
            item = pack,
            instanceId = instance.id,
            instanceMcVersion = instance.mcVersion,
            destinationDir = if (packType == 0) resourcePacksFolder else shaderPacksFolder,
            isInstalled = isPackDialogInstalled,
            onDismiss = { selectedPackForDialog = null },
            onRefresh = {
                refreshLists()
                LauncherProfiles.load()
                launchVersion = resolveLaunchVersion(LauncherProfiles.mainProfileJson.profiles[instance.id], instance.mcVersion)
            }
        )
    }

    if (downloadState.isDownloading) {
        DownloadProgressDialog(downloadState)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountManagerDialog(
    accs: List<AccountInfo>, 
    sel: AccountInfo?, 
    onDismiss: () -> Unit, 
    onMicrosoftLoginClick: () -> Unit,
    onChange: (List<AccountInfo>, AccountInfo?) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss, 
        containerColor = CardBg, 
        title = { Text(text = stringResource(R.string.account_management_title), color = TextPrimary) }, 
        text = {
            Column {
                accs.forEach { a -> 
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { onChange(accs, a) }.padding(12.dp), 
                        horizontalArrangement = Arrangement.SpaceBetween, 
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            PlayerHead(a.username, a.isPremium, Modifier.size(32.dp))
                            Spacer(modifier = Modifier.width(12.dp))
                            FittedSingleLineText(
                                text = a.username,
                                color = if (a == sel) AccentColor else TextPrimary,
                                maxFontSp = 16f,
                                minFontSp = 9f,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        IconButton(onClick = { onChange(accs.filter { it != a }, if (a == sel) null else sel) }) { 
                            Icon(Icons.Default.Delete, contentDescription = null, tint = Color.Red) 
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = {
                        onDismiss()
                        onMicrosoftLoginClick()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = MicrosoftGreen)
                ) {
                    Text(text = stringResource(R.string.login_via_microsoft))
                }
            }
        }, 
        confirmButton = { 
            TextButton(onClick = onDismiss) { 
                Text(text = stringResource(R.string.button_close), color = AccentColor) 
            } 
        }
    )
}

@Composable
fun MicrosoftLoginScreen(onSuccess: (AccountInfo) -> Unit, onCancel: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isAuthenticating by remember { mutableStateOf(false) }
    var authProgressMessage by remember { mutableStateOf("") }

    if (isAuthenticating) {
        Box(modifier = Modifier.fillMaxSize().background(BgDark), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(color = AccentColor)
                Spacer(modifier = Modifier.height(16.dp))
                Text(text = authProgressMessage, color = TextPrimary, fontWeight = FontWeight.Bold)
            }
        }
    } else {
        Column(modifier = Modifier.fillMaxSize().background(BgDark)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onCancel) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.button_cancel), tint = Color.White)
                }
                Text(text = stringResource(R.string.login_microsoft_title), color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            }
            AndroidView(
                factory = { ctx ->
                    WebView(ctx).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        
                        webViewClient = object : WebViewClient() {
                            override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean {
                                return handleRedirectUrl(url)
                            }

                            override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                                super.onPageStarted(view, url, favicon)
                                url?.let { handleRedirectUrl(it) }
                            }

                            private fun handleRedirectUrl(url: String): Boolean {
                                Log.d("OnyxMSAuth", "Loading URL: $url")
                                if (url.startsWith("https://login.live.com/oauth20_desktop.srf") || url.startsWith("ms-xal-00000000402b5328")) {
                                    val uri = Uri.parse(url)
                                    val code = uri.getQueryParameter("code")
                                    if (code != null) {
                                        isAuthenticating = true
                                        authProgressMessage = context.getString(R.string.logging_in_microsoft)
                                        
                                        val bgLogin = MicrosoftBackgroundLogin(false, code)
                                        bgLogin.performLogin(
                                            { step ->
                                                authProgressMessage = when(step) {
                                                    1 -> context.getString(R.string.retrieving_access_token)
                                                    2 -> context.getString(R.string.authenticating_xbox_live)
                                                    3 -> context.getString(R.string.auth_retrieving_xsts)
                                                    4 -> context.getString(R.string.auth_logging_minecraft_services)
                                                    5 -> context.getString(R.string.auth_checking_profile_game)
                                                    else -> context.getString(R.string.auth_processing)
                                                }
                                            },
                                            { account ->
                                                val info = AccountInfo(account.username, true, account.profileId)
                                                onSuccess(info)
                                            },
                                            { error ->
                                                isAuthenticating = false
                                                val errMsg = if (error is PresentedException) {
                                                    error.toString(context)
                                                } else {
                                                    error.message ?: context.getString(R.string.unknown_error)
                                                }
                                                Toast.makeText(context, context.getString(R.string.login_error_msg, errMsg), Toast.LENGTH_LONG).show()
                                                onCancel()
                                            }
                                        )
                                        return true
                                    }
                                }
                                if (url.contains("res=cancel")) {
                                    onCancel()
                                    return true
                                }
                                return false
                            }
                        }
                        
                        android.webkit.CookieManager.getInstance().removeAllCookies {
                            clearHistory()
                            clearCache(true)
                            clearFormData()
                            loadUrl("https://login.live.com/oauth20_authorize.srf" +
                                    "?client_id=00000000402b5328" +
                                    "&response_type=code" +
                                    "&scope=service%3A%3Auser.auth.xboxlive.com%3A%3AMBI_SSL" +
                                    "&redirect_uri=https%3A%2F%2Flogin.live.com%2Foauth20_desktop.srf")
                        }
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@Composable
fun SettingsScreen(p: SharedPreferences) {
    val context = LocalContext.current
    var ram by remember { mutableStateOf(p.getInt("allocated_ram", 2048).toFloat()) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedLanguage by remember { mutableStateOf(p.getString("app_language", "default") ?: "default") }
    var isExpanded by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()

    // Retrieve and parse available languages
    val localesList = remember {
        val rawLocales = context.assets.locales ?: emptyArray()
        val list = mutableListOf<Pair<String, String>>()
        list.add("default" to context.getString(R.string.language_default))
        val added = mutableSetOf<String>()
        val parsedLocales = mutableListOf<Locale>()

        for (localeStr in rawLocales) {
            if (localeStr.isNullOrEmpty() || localeStr.equals("default", ignoreCase = true)) continue
            val locale = if (localeStr.contains("-")) {
                val parts = localeStr.split("-")
                if (parts.size > 1 && parts[1].startsWith("r")) {
                    Locale(parts[0], parts[1].substring(1))
                } else {
                    Locale.forLanguageTag(localeStr)
                }
            } else {
                Locale(localeStr)
            }
            val lang = locale.language
            if (lang.isEmpty() || added.contains(lang)) continue
            added.add(lang)
            parsedLocales.add(locale)
        }

        // Sort by native display name
        parsedLocales.sortBy { it.getDisplayName(it).lowercase(Locale.ROOT) }

        for (locale in parsedLocales) {
            var valueCode = locale.language
            if (!locale.country.isNullOrEmpty()) {
                valueCode = "${locale.language}-r${locale.country}"
            }
            var displayName = locale.getDisplayName(locale)
            if (!displayName.isNullOrEmpty()) {
                displayName = displayName.substring(0, 1).uppercase(locale) + displayName.substring(1)
            } else {
                displayName = locale.language
            }
            list.add(valueCode to displayName)
        }
        list
    }

    val filteredLocales = remember(searchQuery, localesList) {
        if (searchQuery.isBlank()) {
            localesList
        } else {
            localesList.filter {
                it.second.contains(searchQuery, ignoreCase = true) || it.first.contains(searchQuery, ignoreCase = true)
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
    ) {
        Text(text = stringResource(R.string.settings_title), color = TextPrimary, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(16.dp))

        // RAM Allocation Card
        Card(
            colors = CardDefaults.cardColors(containerColor = CardBg),
            border = BorderStroke(1.dp, StrokeColor),
            modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(text = stringResource(R.string.ram_allocation_label, ram.toInt()), color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Spacer(modifier = Modifier.height(8.dp))
                Slider(
                    value = ram,
                    onValueChange = { ram = it },
                    onValueChangeFinished = { p.edit().putInt("allocated_ram", ram.toInt()).apply() },
                    valueRange = 1024f..8192f,
                    colors = SliderDefaults.colors(thumbColor = AccentColor, activeTrackColor = AccentColor)
                )
            }
        }

        // Collapsible Language Selector Header
        val currentLangName = localesList.find { it.first == selectedLanguage }?.second ?: localesList.first().second
        Card(
            colors = CardDefaults.cardColors(containerColor = CardBg),
            border = BorderStroke(1.dp, StrokeColor),
            modifier = Modifier.fillMaxWidth().clickable { isExpanded = !isExpanded }
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(text = stringResource(R.string.preference_app_language_title), color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(text = currentLangName, color = AccentColor, fontSize = 14.sp)
                }
                Icon(
                    imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = "Expand",
                    tint = TextSecondary
                )
            }
        }

        if (isExpanded) {
            Spacer(modifier = Modifier.height(16.dp))
            // Count of detected languages
            Text(
                text = stringResource(R.string.detected_languages_count, localesList.size),
                color = TextSecondary,
                fontSize = 14.sp
            )
            Spacer(modifier = Modifier.height(12.dp))

            // Search Language TextField
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text(text = stringResource(R.string.search_language_placeholder), color = TextSecondary) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = TextSecondary) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Close, contentDescription = "Clear", tint = TextSecondary)
                        }
                    }
                },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary,
                    focusedBorderColor = AccentColor,
                    unfocusedBorderColor = StrokeColor,
                    focusedContainerColor = CardBg,
                    unfocusedContainerColor = CardBg
                ),
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
            )

            // Languages List Column (Dynamic Height, wrapping items)
            Card(
                colors = CardDefaults.cardColors(containerColor = CardBg),
                border = BorderStroke(1.dp, StrokeColor),
                modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    filteredLocales.forEach { (code, name) ->
                        val isSelected = selectedLanguage == code
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if (selectedLanguage != code) {
                                        selectedLanguage = code
                                        p.edit().putString("app_language", code).apply()
                                        // Update static property for instant check
                                        com.cannon.onyxlauncher.prefs.LauncherPreferences.PREF_APP_LANGUAGE = code
                                        // Recreate activity to apply new locale context
                                        (context as? android.app.Activity)?.recreate()
                                    }
                                }
                                .background(if (isSelected) AccentColor.copy(alpha = 0.15f) else Color.Transparent)
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = name,
                                color = if (isSelected) AccentColor else TextPrimary,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                fontSize = 15.sp
                            )
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Selected",
                                    tint = AccentColor,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        HorizontalDivider(color = StrokeColor.copy(alpha = 0.5f), thickness = 0.5.dp)
                    }
                }
            }
        } else {
            Spacer(modifier = Modifier.height(24.dp)) // padding at bottom when collapsed
        }
    }
}

@Composable
fun DownloadProgressDialog(s: DownloadState) {
    Dialog(onDismissRequest = {}) { 
        Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(progress = { s.progress }, color = AccentColor)
            Spacer(modifier = Modifier.height(16.dp))
            Text(text = s.fileName, color = TextPrimary, fontWeight = FontWeight.Bold)
            Text(text = s.downloadedMb, color = TextSecondary, fontSize = 12.sp)
        }
    }
}

fun translateLogLine(line: String): String {
    val langPref = com.cannon.onyxlauncher.prefs.LauncherPreferences.PREF_APP_LANGUAGE
    val lang = if (langPref == null || langPref == "default") {
        java.util.Locale.getDefault().language
    } else {
        langPref.split("-")[0]
    }
    
    if (lang != "pl" && lang != "de") return line

    val rules = listOf(
        Regex("(?i)Unsupported class file major version (\\d+)") to mapOf(
            "pl" to "Niewspierana wersja pliku klasy (major version $1) - prawdopodobnie używasz złej wersji Javy dla tej wersji gry.",
            "de" to "Nicht unterstützte Klassendateiversion (Major-Version $1) - wahrscheinlich verwendest du die falsche Java-Version für diese Spielversion."
        ),
        Regex("(?i)java\\.lang\\.OutOfMemoryError.*") to mapOf(
            "pl" to "Brak pamięci (java.lang.OutOfMemoryError) - spróbuj zwiększyć ilość przydzielonego RAM-u w ustawieniach launchera.",
            "de" to "Nicht genügend Arbeitsspeicher (java.lang.OutOfMemoryError) - versuche, den zugewiesenen RAM in den Launcher-Einstellungen zu erhöhen."
        ),
        Regex("(?i)Failed to create the GLFW window|glfwCreateWindow failed") to mapOf(
            "pl" to "Nie udało się utworzyć okna GLFW - najczęściej jest to spowodowane błędem sterownika graficznego lub niekompatybilnym rendererem.",
            "de" to "GLFW-Fenster konnte nicht erstellt werden - meistens verursacht durch einen Grafiktreiberfehler oder einen inkompatiblen Renderer."
        ),
        Regex("(?i)Pixel format not accelerated") to mapOf(
            "pl" to "Format pikseli nie jest akcelerowany sprzętowo - błąd inicjalizacji OpenGL.",
            "de" to "Pixelformat nicht beschleunigt - OpenGL-Initialisierungsfehler."
        ),
        Regex("(?i)OpenGL ES 3\\.0.*|(?i)OpenGL ES 3.*not supported") to mapOf(
            "pl" to "Wymagany profil OpenGL ES 3.0 nie jest wspierany przez urządzenie/sterownik.",
            "de" to "Das erforderliche OpenGL ES 3.0-Profil wird vom Gerät/Treiber nicht unterstützt."
        ),
        Regex("(?i)Failed to load library: (.+)") to mapOf(
            "pl" to "Nie udało się załadować biblioteki: $1",
            "de" to "Bibliothek konnte nicht geladen werden: $1"
        )
    )

    var result = line
    for ((regex, translations) in rules) {
        val matchResult = regex.find(result)
        if (matchResult != null) {
            val translation = translations[lang]
            if (translation != null) {
                var replaced: String = translation
                for (i in 1 until matchResult.groups.size) {
                    val groupVal = matchResult.groups[i]?.value ?: ""
                    replaced = replaced.replace("$$i", groupVal)
                }
                result = result.substring(0, matchResult.range.first) + replaced + result.substring(matchResult.range.last + 1)
            }
        }
    }

    return result
}

@Composable
fun CrashLogScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val logs = readLogFromFile(context)
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { 
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, tint = Color.White) 
                }
                Text(text = stringResource(R.string.game_logs_title), color = Color.White, fontWeight = FontWeight.Bold)
            }
            IconButton(onClick = {
                try {
                    val logFile = File(Tools.DIR_GAME_HOME, "latestlog.txt")
                    if (logFile.exists() && logFile.length() > 0) {
                        val fileUri = androidx.core.content.FileProvider.getUriForFile(
                            context,
                            context.packageName + ".provider",
                            logFile
                        )
                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_STREAM, fileUri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        val chooserIntent = Intent.createChooser(shareIntent, context.getString(R.string.share_game_logs_title)).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(chooserIntent)
                    } else {
                        Toast.makeText(context, context.getString(R.string.log_empty_error), Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    Toast.makeText(context, context.getString(R.string.share_log_error, e.message), Toast.LENGTH_LONG).show()
                }
            }) {
                Icon(Icons.Default.Share, contentDescription = stringResource(R.string.share_action), tint = Color.White)
            }
        }
        val logLines = remember(logs) { logs.lines() }
        Surface(
            modifier = Modifier.fillMaxSize().padding(top = 8.dp), 
            color = Color.Black, 
            shape = RoundedCornerShape(8.dp)
        ) {
            LazyColumn(modifier = Modifier.fillMaxSize().padding(8.dp)) {
                items(logLines) { line ->
                    Text(
                        text = translateLogLine(line),
                        color = logLineColor(line),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

suspend fun fetchMinecraftVersions(): List<MinecraftVersion> = withContext(Dispatchers.IO) {
    try {
        val manifestStr = URL("https://launchermeta.mojang.com/mc/game/version_manifest.json").readText()
        val json = JSONObject(manifestStr)
        val arr = json.getJSONArray("versions")
        val list = mutableListOf<MinecraftVersion>()
        for(i in 0 until arr.length()) { 
            val o = arr.getJSONObject(i)
            list.add(MinecraftVersion(o.getString("id"), o.getString("type"), o.getString("url"))) 
        }
        list
    } catch (e: Exception) { emptyList() }
}

suspend fun downloadAndExtract(c: Context, url: String, df: String, t: File, n: String, p: (DownloadState) -> Unit): Boolean = withContext(Dispatchers.IO) {
    try {
        val z = File(c.cacheDir, df)
        downloadFile(c, url, z, n, p)
        withContext(Dispatchers.Main) { 
            p(DownloadState(isDownloading = true, fileName = c.getString(R.string.status_unpacking, n), progress = 1f)) 
        }
        ZipInputStream(FileInputStream(z)).use { zis -> 
            var e = zis.nextEntry
            while (e != null) { 
                val f = File(t, e.name)
                if (e.isDirectory) {
                    f.mkdirs()
                } else { 
                    f.parentFile?.mkdirs()
                    FileOutputStream(f).use { zis.copyTo(it) } 
                }
                zis.closeEntry()
                e = zis.nextEntry 
            } 
        }
        z.delete()
        true
    } catch (e: Exception) { false }
}

suspend fun downloadFile(c: Context, u: String, d: File, n: String, p: (DownloadState) -> Unit) {
    val conn = URL(u).openConnection() as HttpURLConnection
    conn.instanceFollowRedirects = true
    val len = conn.contentLength.toLong()
    conn.inputStream.use { input -> 
        FileOutputStream(d).use { output -> 
            val buf = ByteArray(8192)
            var t = 0L
            var readCount: Int
            while (input.read(buf).also { readCount = it } != -1) { 
                t += readCount.toLong()
                withContext(Dispatchers.Main) { 
                    p(DownloadState(isDownloading = true, fileName = c.getString(R.string.status_downloading_file, n), progress = if(len > 0) t.toFloat() / len.toFloat() else 0f, downloadedMb = String.format(Locale.US, "%.1f MB", t.toFloat() / 1048576f))) 
                }
                output.write(buf, 0, readCount)
            } 
        } 
    }
}

suspend fun downloadGame(c: Context, v: String, p: (DownloadState) -> Unit): Boolean = withContext(Dispatchers.IO) {
    try {
        val mStr = URL("https://launchermeta.mojang.com/mc/game/version_manifest.json").readText()
        val m = JSONObject(mStr)
        val arr = m.getJSONArray("versions")
        var u = ""
        for (i in 0 until arr.length()) {
            if (arr.getJSONObject(i).getString("id") == v) {
                u = arr.getJSONObject(i).getString("url")
            }
        }
        val detStr = URL(u).readText()
        val det = JSONObject(detStr)
        val clu = det.getJSONObject("downloads").getJSONObject("client").getString("url")
        val vdir = File(Tools.DIR_HOME_VERSION, v).apply { mkdirs() }
        File(vdir, "$v.json").writeText(detStr)
        downloadFile(c, clu, File(vdir, "$v.jar"), c.getString(R.string.status_client, v), p)
        true
    } catch (e: Exception) { false }
}

fun saveAccounts(p: SharedPreferences, a: List<AccountInfo>) { 
    val arr = JSONArray()
    a.forEach { 
        val o = JSONObject()
        o.put("u", it.username)
        o.put("id", it.uuid)
        o.put("p", it.isPremium)
        arr.put(o)
    }
    p.edit().putString("acc", arr.toString()).apply() 
}

fun loadAccounts(p: SharedPreferences): List<AccountInfo> = try { 
    val s = p.getString("acc", "[]") ?: "[]"
    val arr = JSONArray(s)
    val list = mutableListOf<AccountInfo>()
    for (i in 0 until arr.length()) {
        val o = arr.getJSONObject(i)
        if (o.optBoolean("p", false)) {
            list.add(AccountInfo(o.getString("u"), true, o.getString("id")))
        }
    }
    list
} catch (e: Exception) { emptyList() }

fun saveInstances(p: SharedPreferences, i: List<InstanceData>) { 
    val arr = JSONArray()
    i.forEach { 
        val o = JSONObject()
        o.put("id", it.id)
        o.put("n", it.name)
        o.put("v", it.mcVersion)
        arr.put(o)
    }
    p.edit().putString("ins", arr.toString()).apply() 
}

fun loadInstances(p: SharedPreferences): List<InstanceData> = try { 
    val s = p.getString("ins", "[]") ?: "[]"
    val arr = JSONArray(s)
    val list = mutableListOf<InstanceData>()
    for (i in 0 until arr.length()) {
        val o = arr.getJSONObject(i)
        list.add(InstanceData(o.getString("id"), o.getString("n"), o.getString("v")))
    }
    list
} catch (e: Exception) { emptyList() }

@Composable
fun InstanceSettingsScreen(instance: InstanceData, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isInstallingLoader by remember { mutableStateOf(false) }

    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    var refreshTrigger by remember { mutableStateOf(0) }
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                refreshTrigger++
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val profile = remember(instance.id, refreshTrigger) {
        LauncherProfiles.load()
        var p = LauncherProfiles.mainProfileJson.profiles[instance.id]
        if (p == null) {
            p = MinecraftProfile.getDefaultProfile()
            LauncherProfiles.mainProfileJson.profiles[instance.id] = p
        }
        p.name = instance.name
        if (!isLaunchVersionForBase(p.lastVersionId, instance.mcVersion)) {
            p.lastVersionId = instance.mcVersion
        }
        if (p.gameDir == null) {
            p.gameDir = "instances/${instance.id}"
        }
        if (p.alternateSurface == null) p.alternateSurface = false
        if (p.ramAllocation == null) p.ramAllocation = LauncherPreferences.PREF_RAM_ALLOCATION
        if (p.resolutionScale == null) p.resolutionScale = (LauncherPreferences.PREF_SCALE_FACTOR * 100).toInt()
        if (p.forceVsync == null) p.forceVsync = LauncherPreferences.PREF_FORCE_VSYNC
        if (p.pojavRendererName == null) p.pojavRendererName = LauncherPreferences.PREF_RENDERER
        LauncherProfiles.write()
        p
    }

    fun saveProfile() {
        LauncherProfiles.write()
    }

    fun runtimeLabel(runtimeName: String): String = when (runtimeName) {
        "Internal-8" -> "Java 8 (Legacy)"
        "Internal-17" -> "Java 17"
        "Internal-21" -> "Java 21"
        "Internal-25" -> "Java 25"
        else -> runtimeName.replace("Internal-", "Java ")
    }

    var selectedJavaVersion by remember(instance.id, refreshTrigger) { mutableStateOf(Tools.getConfiguredRuntime(profile)) }
    var requiredJavaVersion by remember(instance.id, refreshTrigger) { mutableStateOf<Int?>(null) }
    var pendingJavaVersion by remember { mutableStateOf<String?>(null) }
    val maxRam = remember { (Tools.getTotalDeviceMemory(context) - 1024).coerceAtLeast(1024).coerceAtMost(8192) }
    var ramMb by remember(instance.id, refreshTrigger) { mutableStateOf((profile.ramAllocation ?: 2048).coerceIn(512, maxRam)) }
    var javaArgs by remember(instance.id, refreshTrigger) { mutableStateOf(profile.javaArgs ?: "") }
    var resolutionScale by remember(instance.id, refreshTrigger) { mutableStateOf((profile.resolutionScale ?: 100).coerceIn(50, 100)) }
    var alternateSurface by remember(instance.id, refreshTrigger) { mutableStateOf(profile.alternateSurface ?: false) }
    var forceVsync by remember(instance.id, refreshTrigger) { mutableStateOf(profile.forceVsync ?: false) }
    val renderers = remember { Tools.getCompatibleRenderers(context) }
    var selectedRenderer by remember(instance.id, refreshTrigger) { mutableStateOf(profile.pojavRendererName ?: LauncherPreferences.PREF_RENDERER) }
    var activeLoader by remember(instance.id, refreshTrigger) { mutableStateOf(modLoaderLabel(profile.lastVersionId ?: instance.mcVersion, instance.mcVersion)) }

    LaunchedEffect(instance.id) {
        withContext(Dispatchers.IO) {
            try {
                requiredJavaVersion = getRequiredJavaVersion(instance.mcVersion)
            } catch (e: Exception) {
                Log.e("OnyxLauncher", "Error loading required JRE version", e)
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.global_back), tint = Color.White)
            }
            Spacer(modifier = Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = stringResource(R.string.instance_settings_title), color = TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                FittedSingleLineText(
                    text = instance.name,
                    color = TextSecondary,
                    maxFontSp = 14f,
                    minFontSp = 9f
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        LazyColumn(
            modifier = Modifier.fillMaxSize().weight(1f),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Surface(
                    color = CardBg,
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, StrokeColor),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(text = stringResource(R.string.select_java_environment), color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Spacer(modifier = Modifier.height(8.dp))
                        requiredJavaVersion?.let {
                            Text(text = stringResource(R.string.required_java_version_label, it), color = TextSecondary, fontSize = 13.sp)
                            Spacer(modifier = Modifier.height(6.dp))
                        }

                        var expanded by remember { mutableStateOf(false) }
                        val isInstalled = Tools.isRuntimeInstalled(selectedJavaVersion)
                        val statusText = if (isInstalled) stringResource(R.string.java_installed) else stringResource(R.string.java_download_on_launch)
                        val statusColor = if (isInstalled) MicrosoftGreen else AccentColor

                        Box(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                            OutlinedButton(
                                onClick = { expanded = true },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary),
                                border = BorderStroke(1.dp, StrokeColor)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(horizontalAlignment = Alignment.Start) {
                                        Text(text = runtimeLabel(selectedJavaVersion), color = TextPrimary, fontWeight = FontWeight.Medium)
                                        Text(text = statusText, color = statusColor, fontSize = 11.sp)
                                    }
                                    Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = TextSecondary)
                                }
                            }

                            DropdownMenu(
                                expanded = expanded,
                                onDismissRequest = { expanded = false },
                                modifier = Modifier.fillMaxWidth(0.9f).background(CardBg)
                            ) {
                                listOf("Internal-8", "Internal-17", "Internal-21", "Internal-25").forEach { rt ->
                                    val rtInstalled = Tools.isRuntimeInstalled(rt)
                                    DropdownMenuItem(
                                        text = {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(text = runtimeLabel(rt), color = TextPrimary)
                                                Text(text = if (rtInstalled) stringResource(R.string.java_installed) else stringResource(R.string.java_to_download), color = if (rtInstalled) MicrosoftGreen else AccentColor, fontSize = 12.sp)
                                            }
                                        },
                                        onClick = {
                                            expanded = false
                                            pendingJavaVersion = rt
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            item {
                Surface(
                    color = CardBg,
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, StrokeColor),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(text = stringResource(R.string.game_engine_title), color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(text = stringResource(R.string.game_engine_description), color = TextSecondary, fontSize = 13.sp)
                        Spacer(modifier = Modifier.height(8.dp))

                        var expanded by remember { mutableStateOf(false) }
                        Box(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                            OutlinedButton(
                                onClick = { expanded = true },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary),
                                border = BorderStroke(1.dp, StrokeColor)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(horizontalAlignment = Alignment.Start) {
                                        Text(text = activeLoader, color = TextPrimary, fontWeight = FontWeight.Medium)
                                        Text(text = stringResource(R.string.active_version_label, profile.lastVersionId ?: instance.mcVersion), color = TextSecondary, fontSize = 11.sp)
                                    }
                                    Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = TextSecondary)
                                }
                            }

                            DropdownMenu(
                                expanded = expanded,
                                onDismissRequest = { expanded = false },
                                modifier = Modifier.fillMaxWidth(0.9f).background(CardBg)
                            ) {
                                listOf("Vanilla", "Forge", "NeoForge", "Fabric", "Quilt").forEach { loader ->
                                    DropdownMenuItem(
                                        text = { Text(text = loader, color = TextPrimary) },
                                        onClick = {
                                            expanded = false
                                            if (loader != activeLoader) {
                                                isInstallingLoader = true
                                                scope.launch {
                                                    try {
                                                        var installerFile: File? = null
                                                        var targetLaunchVersion = instance.mcVersion
                                                        withContext(Dispatchers.IO) {
                                                            when (loader) {
                                                                "Vanilla" -> {
                                                                    saveInstanceLaunchVersion(instance.id, instance.mcVersion, instance.mcVersion)
                                                                }
                                                                "Fabric" -> {
                                                                    targetLaunchVersion = installFabricLoaderForInstance(instance.id, instance.mcVersion)
                                                                }
                                                                "Quilt" -> {
                                                                    targetLaunchVersion = installQuiltLoaderForInstance(instance.id, instance.mcVersion)
                                                                }
                                                                "Forge" -> {
                                                                    val prep = prepareForgeLoaderForInstance(instance.id, instance.mcVersion)
                                                                    targetLaunchVersion = prep.launchVersion
                                                                    installerFile = prep.installerFile
                                                                }
                                                                "NeoForge" -> {
                                                                    val prep = prepareNeoForgeLoaderForInstance(instance.id, instance.mcVersion)
                                                                    targetLaunchVersion = prep.launchVersion
                                                                    installerFile = prep.installerFile
                                                                }
                                                            }
                                                        }

                                                        activeLoader = loader
                                                        if (loader != "Vanilla") {
                                                            withContext(Dispatchers.IO) {
                                                                try {
                                                                    ensureFabricLanguageKotlin(context, instance.id, instance.mcVersion)
                                                                } catch (e: Exception) {
                                                                    Log.e("OnyxLauncher", "Failed to auto-download Fabric Language Kotlin on engine select", e)
                                                                }
                                                                try {
                                                                    ensureSodiumForIris(context, instance.id, instance.mcVersion)
                                                                } catch (e: Exception) {
                                                                    Log.e("OnyxLauncher", "Failed to auto-download Sodium on engine select", e)
                                                                }
                                                            }
                                                        }

                                                        installerFile?.let { file ->
                                                             val targetMcJava = try {
                                                                 com.cannon.onyxlauncher.Tools.getVersionInfo(instance.mcVersion)?.javaVersion?.majorVersion ?: 8
                                                             } catch (e: Exception) {
                                                                 8
                                                             }
                                                             ensureJreForInstaller(context, file, targetMcJava)
                                                             val installIntent = Intent(context, JavaGUILauncherActivity::class.java)
                                                             installIntent.putExtra("targetJavaVersion", targetMcJava)
                                                             if (loader == "NeoForge" || targetMcJava >= 17) {
                                                                 com.cannon.onyxlauncher.modloaders.ForgeUtils.addCliInstallArgs(
                                                                     installIntent,
                                                                     file,
                                                                     Tools.DIR_GAME_NEW
                                                                 )
                                                             } else {
                                                                 com.cannon.onyxlauncher.modloaders.ForgeUtils.addAutoInstallArgs(
                                                                     installIntent,
                                                                     file,
                                                                     targetLaunchVersion
                                                                 )
                                                             }
                                                             context.startActivity(installIntent)
                                                        } ?: run {
                                                            Toast.makeText(context, context.getString(R.string.engine_set_success, loader), Toast.LENGTH_SHORT).show()
                                                        }
                                                        
                                                        profile.lastVersionId = targetLaunchVersion
                                                        saveProfile()
                                                    } catch (e: Exception) {
                                                        Log.e("OnyxLauncher", "Failed to install loader $loader", e)
                                                        Toast.makeText(context, context.getString(R.string.engine_install_error, e.message), Toast.LENGTH_LONG).show()

                                                    } finally {
                                                        isInstallingLoader = false
                                                    }
                                                }
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            item {
                Surface(
                    color = CardBg,
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, StrokeColor),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text(text = stringResource(R.string.launch_parameters_title), color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)

                        Column {
                            Text(text = stringResource(R.string.ram_allocation_label, ramMb), color = TextPrimary, fontWeight = FontWeight.Medium)
                            Slider(
                                value = ramMb.toFloat(),
                                onValueChange = { value ->
                                    ramMb = ((value.toInt() / 128) * 128).coerceIn(512, maxRam)
                                },
                                onValueChangeFinished = {
                                    profile.ramAllocation = ramMb
                                    saveProfile()
                                },
                                valueRange = 512f..maxRam.toFloat(),
                                colors = SliderDefaults.colors(thumbColor = AccentColor, activeTrackColor = AccentColor)
                            )
                        }

                        Column {
                            Text(text = stringResource(R.string.resolution_scale_label, resolutionScale), color = TextPrimary, fontWeight = FontWeight.Medium)
                            Slider(
                                value = resolutionScale.toFloat(),
                                onValueChange = { value ->
                                    resolutionScale = ((value.toInt() / 5) * 5).coerceIn(50, 100)
                                },
                                onValueChangeFinished = {
                                    profile.resolutionScale = resolutionScale
                                    saveProfile()
                                },
                                valueRange = 50f..100f,
                                colors = SliderDefaults.colors(thumbColor = AccentColor, activeTrackColor = AccentColor)
                            )
                        }

                        var rendererExpanded by remember { mutableStateOf(false) }
                        val rendererIndex = renderers.rendererIds.indexOf(selectedRenderer)
                        val rendererName = if (rendererIndex >= 0) renderers.rendererDisplayNames[rendererIndex] else selectedRenderer
                        Box(modifier = Modifier.fillMaxWidth()) {
                            OutlinedButton(
                                onClick = { rendererExpanded = true },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary),
                                border = BorderStroke(1.dp, StrokeColor)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(horizontalAlignment = Alignment.Start) {
                                        Text(text = stringResource(R.string.pedit_renderer), color = TextSecondary, fontSize = 12.sp)
                                        Text(text = rendererName, color = TextPrimary, fontWeight = FontWeight.Medium)
                                    }
                                    Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = TextSecondary)
                                }
                            }

                            DropdownMenu(
                                expanded = rendererExpanded,
                                onDismissRequest = { rendererExpanded = false },
                                modifier = Modifier.fillMaxWidth(0.9f).background(CardBg)
                            ) {
                                renderers.rendererIds.forEachIndexed { index, rendererId ->
                                    DropdownMenuItem(
                                        text = { Text(text = renderers.rendererDisplayNames[index], color = TextPrimary) },
                                        onClick = {
                                            rendererExpanded = false
                                            selectedRenderer = rendererId
                                            profile.pojavRendererName = rendererId
                                            saveProfile()
                                        }
                                    )
                                }
                            }
                        }

                        OutlinedTextField(
                            value = javaArgs,
                            onValueChange = {
                                javaArgs = it
                                profile.javaArgs = it.ifBlank { null }
                                saveProfile()
                            },
                            label = { Text(stringResource(R.string.jvm_arguments_label)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary)
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(text = "VSync", color = TextPrimary, fontWeight = FontWeight.Medium)
                                Text(text = if (forceVsync) stringResource(R.string.state_enabled_masc) else stringResource(R.string.state_disabled_masc), color = TextSecondary, fontSize = 12.sp)
                            }
                            Switch(
                                checked = forceVsync,
                                onCheckedChange = {
                                    forceVsync = it
                                    profile.forceVsync = it
                                    saveProfile()
                                }
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(text = stringResource(R.string.alternative_surface_label), color = TextPrimary, fontWeight = FontWeight.Medium)
                                Text(text = if (alternateSurface) stringResource(R.string.state_enabled_fem) else stringResource(R.string.state_disabled_fem), color = TextSecondary, fontSize = 12.sp)
                            }
                            Switch(
                                checked = alternateSurface,
                                onCheckedChange = {
                                    alternateSurface = it
                                    profile.alternateSurface = it
                                    saveProfile()
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    pendingJavaVersion?.let { runtimeName ->
        val requestedMajor = runtimeName.removePrefix("Internal-").toIntOrNull()
        val warning = buildString {
            append(context.getString(R.string.change_java_warning_prefix))
            append(context.getString(R.string.invalid_version_warning))
            requiredJavaVersion?.let { req ->
                if (requestedMajor != null && requestedMajor < req) {
                    append(context.getString(R.string.change_java_warning_suffix, req))
                }
            }
        }
        AlertDialog(
            onDismissRequest = { pendingJavaVersion = null },
            containerColor = CardBg,
            title = { Text(text = stringResource(R.string.change_java_dialog_title), color = TextPrimary, fontWeight = FontWeight.Bold) },
            text = { Text(text = warning, color = TextSecondary) },
            dismissButton = {
                TextButton(onClick = { pendingJavaVersion = null }) {
                    Text(text = stringResource(R.string.button_cancel), color = TextSecondary)
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        selectedJavaVersion = runtimeName
                        profile.javaDir = Tools.LAUNCHERPROFILES_RTPREFIX + runtimeName
                        saveProfile()
                        pendingJavaVersion = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AccentColor)
                ) {
                    Text(text = stringResource(R.string.global_change), color = Color.White)
                }
            }
        )
    }

    if (isInstallingLoader) {
        AlertDialog(
            onDismissRequest = {},
            containerColor = CardBg,
            title = { Text(text = stringResource(R.string.engine_installation_title), color = TextPrimary, fontWeight = FontWeight.Bold) },
            text = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.padding(vertical = 8.dp)
                ) {
                    CircularProgressIndicator(color = AccentColor)
                    Text(text = stringResource(R.string.downloading_engine_files_background), color = TextSecondary)
                }
            },
            confirmButton = {}
        )
    }
}


/*
@Composable
fun InstanceSettingsScreenLegacy(instance: InstanceData, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val profile = remember(instance.id) {
        LauncherProfiles.load()
        var p = LauncherProfiles.mainProfileJson.profiles.get(instance.id)
        if (p == null) {
            p = MinecraftProfile.getDefaultProfile()
            LauncherProfiles.mainProfileJson.profiles.put(instance.id, p)
            p.name = instance.name
            p.lastVersionId = instance.mcVersion
            p.gameDir = "instances/" + instance.id
            LauncherProfiles.write()
        } else if (p.gameDir == null) {
            p.gameDir = "instances/" + instance.id
            LauncherProfiles.write()
        }
        p
    }

    var selectedJavaVersion by remember(instance.id) { mutableStateOf(Tools.getConfiguredRuntime(profile)) }
    var requiredJavaVersion by remember { mutableStateOf<Int?>(null) }

    LaunchedEffect(instance.id) {
        withContext(Dispatchers.IO) {
            try {
                requiredJavaVersion = getRequiredJavaVersion(instance.mcVersion)
            } catch (e: Exception) {
                Log.e("OnyxLauncher", "Error loading required JRE version", e)
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.global_back), tint = Color.White)
            }
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text(text = "Ustawienia instancji", color = TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text(text = instance.name, color = TextSecondary, fontSize = 14.sp)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        LazyColumn(
            modifier = Modifier.fillMaxSize().weight(1f),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // JAVA SETTING CARD
            item {
                Surface(
                    color = CardBg,
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, StrokeColor),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(text = stringResource(R.string.select_java_environment), color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Spacer(modifier = Modifier.height(8.dp))
                        requiredJavaVersion?.let { reqVersion ->
                            Text(text = stringResource(R.string.required_java_version_label, reqVersion), color = TextSecondary, fontSize = 13.sp)
                            Spacer(modifier = Modifier.height(6.dp))
                        }

                        var expanded by remember { mutableStateOf(false) }
                        val isInstalled = try {
                            com.cannon.onyxlauncher.multirt.MultiRTUtils.read(selectedJavaVersion).versionString != null
                        } catch (e: Exception) {
                            false
                        }
                        val statusText = if (isInstalled) stringResource(R.string.java_installed) else stringResource(R.string.java_download_on_launch)
                        val statusColor = if (isInstalled) MicrosoftGreen else AccentColor
                        val displayName = when (selectedJavaVersion) {
                            "Internal-8" -> "Java 8 (Legacy)"
                            "Internal-17" -> "Java 17"
                            "Internal-21" -> "Java 21"
                            "Internal-25" -> "Java 25"
                            else -> selectedJavaVersion.replace("Internal-", "Java ")
                        }

                        Box(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                            OutlinedButton(
                                onClick = { expanded = true },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary),
                                border = BorderStroke(1.dp, StrokeColor)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(horizontalAlignment = Alignment.Start) {
                                        Text(text = displayName, color = TextPrimary, fontWeight = FontWeight.Medium)
                                        Text(text = statusText, color = statusColor, fontSize = 11.sp)
                                    }
                                    Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = TextSecondary)
                                }
                            }

                            DropdownMenu(
                                expanded = expanded,
                                onDismissRequest = { expanded = false },
                                modifier = Modifier.fillMaxWidth(0.9f).background(CardBg)
                            ) {
                                listOf("Internal-8", "Internal-17", "Internal-21", "Internal-25").forEach { rt ->
                                    val rtInstalled = try {
                                        com.cannon.onyxlauncher.multirt.MultiRTUtils.read(rt).versionString != null
                                    } catch (e: Exception) {
                                        false
                                    }
                                    val rtStatus = if (rtInstalled) stringResource(R.string.java_installed) else stringResource(R.string.java_to_download)
                                    val rtColor = if (rtInstalled) MicrosoftGreen else AccentColor
                                    val rtDisplay = when (rt) {
                                        "Internal-8" -> "Java 8 (Legacy)"
                                        "Internal-17" -> "Java 17"
                                        "Internal-21" -> "Java 21"
                                        "Internal-25" -> "Java 25"
                                        else -> rt.replace("Internal-", "Java ")
                                    }

                                    DropdownMenuItem(
                                        text = {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(text = rtDisplay, color = TextPrimary, fontWeight = FontWeight.Normal)
                                                Text(text = rtStatus, color = rtColor, fontSize = 12.sp)
                                            }
                                        },
                                        onClick = {
                                            expanded = false
                                            selectedJavaVersion = rt
                                            profile.javaDir = Tools.LAUNCHERPROFILES_RTPREFIX + rt
                                            LauncherProfiles.write()
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // MODS AND PACKS CLEANUP CARD
            item {
                var showDeleteConfirmType by remember { mutableStateOf<Int?>(null) } // 1=mods, 2=resourcepacks, 3=shaders
                
                Surface(
                    color = CardBg,
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, StrokeColor),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(text = stringResource(R.string.manage_additional_files_title), color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(text = stringResource(R.string.manage_additional_files_desc), color = TextSecondary, fontSize = 13.sp)
                        Spacer(modifier = Modifier.height(16.dp))

                        // Clear mods button
                        Button(
                            onClick = { showDeleteConfirmType = 1 },
                            colors = ButtonDefaults.buttonColors(containerColor = Color.Red.copy(alpha = 0.8f)),
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(text = stringResource(R.string.delete_additional_mods), fontWeight = FontWeight.Bold)
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Clear resourcepacks button
                        Button(
                            onClick = { showDeleteConfirmType = 2 },
                            colors = ButtonDefaults.buttonColors(containerColor = Color.Red.copy(alpha = 0.8f)),
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(text = stringResource(R.string.delete_resource_packs), fontWeight = FontWeight.Bold)
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Clear shaders button
                        Button(
                            onClick = { showDeleteConfirmType = 3 },
                            colors = ButtonDefaults.buttonColors(containerColor = Color.Red.copy(alpha = 0.8f)),
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(text = stringResource(R.string.delete_shader_packs), fontWeight = FontWeight.Bold)
                        }
                    }
                }

                showDeleteConfirmType?.let { type ->
                    val typeName = when(type) {
                        1 -> context.getString(R.string.mods_plural)
                        2 -> context.getString(R.string.resource_packs_plural)
                        3 -> context.getString(R.string.shader_packs_plural)
                        else -> ""
                    }
                    val folderName = when(type) {
                        1 -> "mods"
                        2 -> "resourcepacks"
                        3 -> "shaderpacks"
                        else -> ""
                    }

                    AlertDialog(
                        onDismissRequest = { showDeleteConfirmType = null },
                        containerColor = CardBg,
                        title = { Text(text = stringResource(R.string.confirm_deletion_title), color = TextPrimary, fontWeight = FontWeight.Bold) },
                        text = { Text(text = stringResource(R.string.confirm_deletion_desc, typeName), color = TextSecondary) },
                        dismissButton = {
                            TextButton(onClick = { showDeleteConfirmType = null }) {
                                Text(text = stringResource(R.string.button_cancel), color = TextSecondary)
                            }
                        },
                        confirmButton = {
                            Button(
                                onClick = {
                                    showDeleteConfirmType = null
                                    scope.launch(Dispatchers.IO) {
                                        try {
                                            val dir = File(getInstanceDir(instance.id), folderName)
                                            if (dir.exists()) {
                                                dir.deleteRecursively()
                                                dir.mkdirs()
                                            }
                                            withContext(Dispatchers.Main) {
                                                Toast.makeText(context, context.getString(R.string.delete_success_msg, typeName), Toast.LENGTH_SHORT).show()
                                            }
                                        } catch (e: Exception) {
                                            Log.e("OnyxLauncher", "Error clearing folder $folderName", e)
                                            withContext(Dispatchers.Main) {
                                                Toast.makeText(context, context.getString(R.string.delete_error_msg, e.message), Toast.LENGTH_LONG).show()
                                            }
                                        }
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
                            ) {
                                Text(text = stringResource(R.string.global_delete_confirm), color = Color.White)
                            }
                        }
                    )
                }
            }
        }
    }
}
*/

data class VersionDetails(
    val id: String,
    val releaseType: String,
    val releaseDate: String,
    val requiredJava: Int,
    val mainClass: String,
    val fileSizeBytes: Long
)

fun findTomlStringValue(text: String, key: String): String? {
    val regex = Regex("(?m)^\\s*$key\\s*=\\s*(?:\"([^\"]*)\"|'([^']*)')", RegexOption.IGNORE_CASE)
    val match = regex.find(text) ?: return null
    val val1 = match.groupValues[1]
    if (val1.isNotEmpty()) return val1
    val val2 = match.groupValues[2]
    if (val2.isNotEmpty()) return val2
    return null
}

fun findTomlMultilineStringValue(text: String, key: String): String? {
    val regex = Regex("(?m)^\\s*$key\\s*=\\s*(?:'''([\\s\\S]*?)'''|\"\"\"([\\s\\S]*?)\"\"\")", RegexOption.IGNORE_CASE)
    val match = regex.find(text) ?: return null
    val val1 = match.groupValues[1]
    if (val1.isNotEmpty()) return val1.trim()
    val val2 = match.groupValues[2]
    if (val2.isNotEmpty()) return val2.trim()
    return null
}

fun parseLocalModMetadata(file: File, context: Context): ModItem? {
    var id = file.name
    var name = cleanModName(file.name)
    var description = context.getString(R.string.local_mod_file_desc)
    var localIconPath = ""
    var iconPathInZip: String? = null
    var foundMetadata = false
    
    try {
        java.util.zip.ZipFile(file).use { zip ->
            val fabricEntry = zip.getEntry("fabric.mod.json")
            val quiltEntry = zip.getEntry("quilt.mod.json")
            val neoforgeEntry = zip.getEntry("META-INF/neoforge.mods.toml")
            val forgeEntry = zip.getEntry("META-INF/mods.toml")
            
            if (fabricEntry != null) {
                val jsonText = zip.getInputStream(fabricEntry).bufferedReader(Charsets.UTF_8).use { it.readText() }
                val json = org.json.JSONObject(jsonText)
                id = json.optString("id", file.name)
                name = json.optString("name", cleanModName(file.name))
                description = json.optString("description", context.getString(R.string.local_mod_file_desc))
                
                if (json.has("icon")) {
                    val iconObj = json.get("icon")
                    if (iconObj is String) {
                        iconPathInZip = iconObj
                    } else if (iconObj is org.json.JSONObject) {
                        val keys = iconObj.keys()
                        if (keys.hasNext()) {
                            iconPathInZip = iconObj.optString(keys.next())
                        }
                    }
                }
                foundMetadata = true
            } else if (quiltEntry != null) {
                val jsonText = zip.getInputStream(quiltEntry).bufferedReader(Charsets.UTF_8).use { it.readText() }
                val json = org.json.JSONObject(jsonText)
                val quiltLoader = json.optJSONObject("quilt_loader")
                if (quiltLoader != null) {
                    id = quiltLoader.optString("id", file.name)
                    val metadata = quiltLoader.optJSONObject("metadata")
                    name = metadata?.optString("name", cleanModName(file.name)) ?: cleanModName(file.name)
                    description = metadata?.optString("description", context.getString(R.string.local_mod_file_desc)) ?: context.getString(R.string.local_mod_file_desc)
                    
                    if (metadata != null && metadata.has("icon")) {
                        val iconObj = metadata.get("icon")
                        if (iconObj is String) {
                            iconPathInZip = iconObj
                        } else if (iconObj is org.json.JSONObject) {
                            val keys = iconObj.keys()
                            if (keys.hasNext()) {
                                iconPathInZip = iconObj.optString(keys.next())
                            }
                        }
                    }
                }
                foundMetadata = true
            } else if (neoforgeEntry != null || forgeEntry != null) {
                val tomlEntry = neoforgeEntry ?: forgeEntry
                if (tomlEntry != null) {
                    val tomlText = zip.getInputStream(tomlEntry).bufferedReader(Charsets.UTF_8).use { it.readText() }
                    val modsSection = tomlText.substringAfter("[[mods]]", "")
                    val targetText = if (modsSection.isNotEmpty()) modsSection else tomlText
                    
                    val tomlId = findTomlStringValue(targetText, "modId") ?: findTomlStringValue(targetText, "modid")
                    val tomlName = findTomlStringValue(targetText, "displayName") ?: findTomlStringValue(targetText, "name")
                    val tomlDesc = findTomlMultilineStringValue(targetText, "description") ?: findTomlStringValue(targetText, "description")
                    val tomlLogo = findTomlStringValue(targetText, "logoFile") ?: findTomlStringValue(targetText, "logofile")
                    
                    if (!tomlId.isNullOrEmpty()) id = tomlId
                    if (!tomlName.isNullOrEmpty()) name = tomlName
                    if (!tomlDesc.isNullOrEmpty()) description = tomlDesc
                    if (!tomlLogo.isNullOrEmpty()) iconPathInZip = tomlLogo
                    
                    foundMetadata = true
                }
            }
            
            // Extract the icon if we found an icon path or try fallbacks
            var iconEntry: java.util.zip.ZipEntry? = null
            if (!iconPathInZip.isNullOrEmpty()) {
                val cleanPath = iconPathInZip!!.replace("\\", "/").trimStart('/')
                iconEntry = zip.getEntry(cleanPath)
                if (iconEntry == null) {
                    iconEntry = zip.entries().asSequence().firstOrNull { entry ->
                        entry.name.equals(cleanPath, ignoreCase = true) || 
                        entry.name.endsWith("/$cleanPath", ignoreCase = true)
                    }
                }
            }
            
            // Fallback search: look for icon.png or logo.png in zip root
            if (iconEntry == null) {
                iconEntry = zip.getEntry("icon.png") ?: zip.getEntry("logo.png")
                if (iconEntry == null) {
                    iconEntry = zip.entries().asSequence().firstOrNull { entry ->
                        (entry.name.equals("icon.png", ignoreCase = true) || entry.name.equals("logo.png", ignoreCase = true)) && 
                        !entry.name.contains("/")
                    }
                }
            }
            
            if (iconEntry != null) {
                val cacheDir = File(context.cacheDir, "mod_icons")
                if (!cacheDir.exists()) cacheDir.mkdirs()
                val cacheFile = File(cacheDir, "${file.name}.png")
                zip.getInputStream(iconEntry).use { input ->
                    cacheFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                localIconPath = cacheFile.absolutePath
            }
        }
        
        if (foundMetadata) {
            return ModItem(
                com.cannon.onyxlauncher.modloaders.modpacks.models.Constants.SOURCE_MODRINTH,
                false,
                id,
                name,
                description,
                localIconPath
            )
        }
    } catch (e: Exception) {
        Log.e("OnyxLauncher", "Error parsing local mod: ${file.name}", e)
    }
    return null
}


fun cleanModName(fileName: String): String {
    var name = fileName.removeSuffix(".jar").removeSuffix(".JAR")
        .removeSuffix(".zip").removeSuffix(".ZIP")
    name = name.replace(Regex("[-_][0-9a-fA-F]{8}$"), "")
    // Replace common version suffixes (e.g. -1.20.1, -forge, -fabric)
    name = name.replace(Regex("[+_-](mc)?v?([0-9]+[.\\d]*).*"), "")
    name = name.replace("-fabric", "", ignoreCase = true)
    name = name.replace("-forge", "", ignoreCase = true)
    name = name.replace("-quilt", "", ignoreCase = true)
    name = name.replace(Regex("[-_]"), " ")
    return name.trim()
}

fun translateText(text: String, targetLang: String): String {
    if (targetLang.equals("en", ignoreCase = true) || text.isBlank()) return text
    // Strip HTML tags if present (CurseForge API descriptions may contain HTML tags)
    val plainText = text.replace(Regex("<[^>]*>"), "")
    return try {
        val url = URL("https://api.mymemory.translated.net/get?q=" + URLEncoder.encode(plainText, "UTF-8") + "&langpair=en|" + targetLang)
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "GET"
        conn.connectTimeout = 5000
        conn.readTimeout = 5000
        val response = conn.inputStream.bufferedReader().use { it.readText() }
        val json = JSONObject(response)
        json.getJSONObject("responseData").getString("translatedText")
    } catch (e: Exception) {
        plainText // Fallback to original text on failure
    }
}

@Composable
fun OnyxAsyncImage(url: String, modifier: Modifier = Modifier, contentScale: ContentScale = ContentScale.Fit, roundedCorners: androidx.compose.ui.unit.Dp = 0.dp) {
    var bitmap by remember(url) { mutableStateOf<android.graphics.Bitmap?>(null) }
    var failed by remember(url) { mutableStateOf(false) }

    LaunchedEffect(url) {
        withContext(Dispatchers.IO) {
            try {
                if (url.startsWith("http://") || url.startsWith("https://")) {
                    val conn = URL(url).openConnection() as HttpURLConnection
                    conn.connectTimeout = 5000
                    conn.readTimeout = 5000
                    conn.inputStream.use { stream ->
                        bitmap = android.graphics.BitmapFactory.decodeStream(stream)
                    }
                } else {
                    val filePath = if (url.startsWith("file://")) {
                        url.substring(7)
                    } else {
                        url
                    }
                    val imgFile = File(filePath)
                    if (imgFile.exists()) {
                        bitmap = android.graphics.BitmapFactory.decodeFile(imgFile.absolutePath)
                    } else {
                        failed = true
                    }
                }
            } catch (e: Exception) {
                Log.e("OnyxLauncher", "Failed loading image: $url", e)
                failed = true
            }
        }
    }

    if (bitmap != null) {
        Image(
            bitmap = bitmap!!.asImageBitmap(),
            contentDescription = null,
            modifier = modifier.then(if (roundedCorners > 0.dp) Modifier.clip(RoundedCornerShape(roundedCorners)) else Modifier),
            contentScale = contentScale
        )
    } else {
        Box(
            modifier = modifier.background(Color.Gray.copy(alpha = 0.1f), if (roundedCorners > 0.dp) RoundedCornerShape(roundedCorners) else RoundedCornerShape(0.dp)),
            contentAlignment = Alignment.Center
        ) {
            if (failed) {
                Icon(Icons.Default.Warning, contentDescription = null, tint = Color.Red.copy(alpha = 0.5f))
            } else {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), color = AccentColor, strokeWidth = 2.dp)
            }
        }
    }
}

fun downloadModFile(
    downloadUrl: String,
    destinationFile: File,
    onProgress: (Float) -> Unit,
    onComplete: () -> Unit,
    onError: (Throwable) -> Unit
) {
    kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
        try {
            destinationFile.parentFile?.mkdirs()
            val url = URL(downloadUrl)
            val connection = url.openConnection() as HttpURLConnection
            connection.connectTimeout = 10000
            connection.readTimeout = 15000
            connection.connect()
            
            val fileLength = connection.contentLength
            connection.inputStream.use { input ->
                FileOutputStream(destinationFile).use { output ->
                    val data = ByteArray(4096)
                    var total: Long = 0
                    var count: Int
                    while (input.read(data).also { count = it } != -1) {
                        total += count
                        if (fileLength > 0) {
                            onProgress(total.toFloat() / fileLength.toFloat())
                        }
                        output.write(data, 0, count)
                    }
                }
            }
            onComplete()
        } catch (e: Exception) {
            Log.e("OnyxLauncher", "Error downloading file", e)
            onError(e)
        }
    }
}

@Composable
fun ModOrPackDetailDialog(
    item: ModItem,
    instanceId: String,
    instanceMcVersion: String,
    destinationDir: File,
    isInstalled: Boolean,
    onDismiss: () -> Unit,
    onRefresh: () -> Unit,
    modMetadataCache: Map<String, ModItem> = emptyMap()
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var displayItem by remember { mutableStateOf(item) }
    var detail by remember { mutableStateOf<ModDetail?>(null) }
    var translatedDesc by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(true) }
    var downloadProgress by remember { mutableStateOf<Float?>(null) }

    LaunchedEffect(item.id) {
        withContext(Dispatchers.IO) {
            try {
                val api = CommonApi(context.getString(R.string.curseforge_api_key))
                var actualItem = item
                if (item.id.endsWith(".jar", ignoreCase = true) || item.id.endsWith(".zip", ignoreCase = true)) {
                    val cached = modMetadataCache[item.id]
                    if (cached != null) {
                        actualItem = cached
                        displayItem = cached
                    } else {
                        val cleanName = cleanModName(item.title)
                        val projectType = if (destinationDir.name.contains("mods")) "mod"
                                          else if (destinationDir.name.contains("resourcepacks")) "resourcepack"
                                          else "shader"
                        val filters = SearchFilters().apply {
                            name = cleanName
                            mcVersion = instanceMcVersion
                            isModpack = false
                            this.projectType = projectType
                        }
                        val searchResult = api.searchMod(filters)
                        if (searchResult != null && searchResult.results.isNotEmpty()) {
                            actualItem = searchResult.results.first()
                            displayItem = actualItem
                        }
                    }
                }
                
                val loadedDetail = api.getModDetails(actualItem)
                detail = loadedDetail
                if (loadedDetail != null) {
                    val targetLang = Locale.getDefault().language
                    translatedDesc = translateText(loadedDetail.description, targetLang)
                }
            } catch (e: Exception) {
                Log.e("OnyxLauncher", "Error loading details for dialog", e)
            } finally {
                isLoading = false
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CardBg,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OnyxAsyncImage(url = displayItem.imageUrl, modifier = Modifier.size(48.dp), roundedCorners = 8.dp)
                Spacer(modifier = Modifier.width(12.dp))
                Text(text = displayItem.title, color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (isLoading) {
                    Box(modifier = Modifier.fillMaxWidth().height(150.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = AccentColor)
                    }
                } else {
                    // Full translated description
                    Text(text = translatedDesc.ifEmpty { displayItem.description }, color = TextPrimary, fontSize = 14.sp)

                    // Screenshots gallery
                    detail?.screenshotUrls?.let { screenshots ->
                        if (screenshots.isNotEmpty()) {
                            Text(text = stringResource(R.string.screenshots_label), color = TextSecondary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth().height(120.dp)
                            ) {
                                items(screenshots.toList()) { url ->
                                    OnyxAsyncImage(
                                        url = url,
                                        modifier = Modifier.width(200.dp).fillMaxHeight(),
                                        contentScale = ContentScale.Crop,
                                        roundedCorners = 8.dp
                                    )
                                }
                            }
                        }
                    }

                    if (downloadProgress != null) {
                        Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                            LinearProgressIndicator(progress = downloadProgress!!, color = AccentColor, modifier = Modifier.fillMaxWidth())
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(text = stringResource(R.string.downloading_progress_label, (downloadProgress!! * 100).toInt()), color = TextSecondary, fontSize = 12.sp)
                        }
                    }
                }
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onDismiss) {
                    Text(text = stringResource(R.string.button_close), color = TextSecondary)
                }
                if (!isLoading && downloadProgress == null) {
                    if (isInstalled) {
                        Button(
                            onClick = {
                                scope.launch(Dispatchers.IO) {
                                    try {
                                        val filesToDelete = mutableListOf<File>()
                                        val cachedFileName = modMetadataCache.entries.firstOrNull { it.value.id == item.id }?.key
                                        if (cachedFileName != null) {
                                            filesToDelete.add(File(destinationDir, cachedFileName))
                                        }
                                        val cleanName = cleanModName(item.title)
                                        val files = destinationDir.listFiles() ?: emptyArray()
                                        for (file in files) {
                                            if (file.name == item.id || 
                                                file.name == item.title || 
                                                cleanModName(file.name).equals(cleanName, ignoreCase = true) ||
                                                file.name.contains(item.title, ignoreCase = true)
                                            ) {
                                                if (!filesToDelete.contains(file)) {
                                                    filesToDelete.add(file)
                                                }
                                            }
                                        }
                                        filesToDelete.forEach { it.delete() }
                                        withContext(Dispatchers.Main) {
                                            Toast.makeText(context, context.getString(R.string.uninstall_success), Toast.LENGTH_SHORT).show()
                                            onRefresh()
                                            onDismiss()
                                        }
                                    } catch (e: Exception) {
                                        Log.e("OnyxLauncher", "Failed to delete file", e)
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
                        ) {
                            Text(text = stringResource(R.string.button_uninstall), color = Color.White)
                        }
                    } else {
                        detail?.let { modDetail ->
Button(
                                onClick = {
                                    val isModFile = destinationDir.name.contains("mods")
                                    val isResourcePack = destinationDir.name.contains("resourcepacks")
                                    val isShaderPack = destinationDir.name.contains("shaderpacks")
                                    LauncherProfiles.load()
                                    val currentLaunchVersion = LauncherProfiles.mainProfileJson.profiles[instanceId]?.lastVersionId
                                    val activeLoader = modLoaderLabel(currentLaunchVersion ?: "", instanceMcVersion)
                                    val preferForge = activeLoader == "Forge"
                                    val preferNeoForge = activeLoader == "NeoForge"
                                    val preferQuilt = activeLoader == "Quilt"
                                    val preferFabric = activeLoader == "Fabric"

                                    val selectedVersion = chooseBestVersionIndex(
                                        modDetail,
                                        instanceMcVersion,
                                        preferFabric = preferFabric || (!preferForge && !preferNeoForge && !preferQuilt && (isModFile || isShaderPack)),
                                        preferForge = preferForge,
                                        preferNeoForge = preferNeoForge,
                                        preferQuilt = preferQuilt
                                    )
                                    if (selectedVersion < 0 || modDetail.versionUrls[selectedVersion].isBlank()) {
                                        Toast.makeText(context, context.getString(R.string.no_compatible_version_found, instanceMcVersion), Toast.LENGTH_LONG).show()
                                        return@Button
                                    }

                                    val versionName = modDetail.versionNames[selectedVersion]
                                    val loaderText = versionLoaderText(modDetail, selectedVersion)
                                    val isNeoForgeMod = isModFile && loaderText.contains("neoforge")
                                    val isForgeMod = isModFile && loaderText.contains("forge") && !isNeoForgeMod
                                    if (false && isModFile && !loaderText.contains("fabric") && (loaderText.contains("forge") || loaderText.contains("neoforge"))) {
                                        Toast.makeText(context, context.getString(R.string.wrong_modloader_toast), Toast.LENGTH_LONG).show()
                                        return@Button
                                    }

                                    val extension = if (isModFile) ".jar" else ".zip"
                                    val destinationFile = File(destinationDir, safeDownloadFileName(versionName, instanceMcVersion, extension))
                                    val versionUrl = modDetail.versionUrls[selectedVersion]
                                    val instanceDir = destinationDir.parentFile ?: destinationDir

                                    downloadProgress = 0.02f
                                    scope.launch {
                                        try {
                                            var forgePreparation: ForgeLoaderPreparation? = null
                                            withContext(Dispatchers.IO) {
                                                if (isNeoForgeMod) {
                                                    withContext(Dispatchers.Main) { downloadProgress = 0.12f }
                                                    forgePreparation = prepareNeoForgeLoaderForInstance(instanceId, instanceMcVersion)
                                                } else if (isForgeMod) {
                                                    withContext(Dispatchers.Main) { downloadProgress = 0.12f }
                                                    forgePreparation = prepareForgeLoaderForInstance(instanceId, instanceMcVersion)
                                                } else if (isModFile || isShaderPack) {
                                                    withContext(Dispatchers.Main) { downloadProgress = 0.12f }
                                                    if (activeLoader == "Quilt") {
                                                        installQuiltLoaderForInstance(instanceId, instanceMcVersion)
                                                    } else {
                                                        installFabricLoaderForInstance(instanceId, instanceMcVersion)
                                                    }
                                                }
                                                if (isModFile) {
                                                    withContext(Dispatchers.Main) { downloadProgress = 0.22f }
                                                    val unresolvedDependencies = installRequiredDependencyMods(
                                                        context = context,
                                                        api = CommonApi(context.getString(R.string.curseforge_api_key)),
                                                        baseVersion = instanceMcVersion,
                                                        modsDir = File(instanceDir, "mods"),
                                                        parentDetail = modDetail,
                                                        parentVersionIndex = selectedVersion,
                                                        preferFabric = preferFabric || (!preferForge && !preferNeoForge && !preferQuilt),
                                                        preferForge = preferForge,
                                                        preferNeoForge = preferNeoForge,
                                                        preferQuilt = preferQuilt
                                                    )
                                                    if (unresolvedDependencies.isNotEmpty()) {
                                                        throw java.io.IOException(context.getString(R.string.mod_requires_dependencies, unresolvedDependencies.joinToString(", ")))
                                                    }
                                                }
                                                if (isShaderPack) {
                                                    withContext(Dispatchers.Main) { downloadProgress = 0.25f }
                                                    if (activeLoader == "Forge" || activeLoader == "NeoForge") {
                                                        downloadDependencyMod(
                                                            context = context,
                                                            baseVersion = instanceMcVersion,
                                                            instanceDir = instanceDir,
                                                            query = "Oculus",
                                                            preferFabric = false,
                                                            preferForge = activeLoader == "Forge",
                                                            preferNeoForge = activeLoader == "NeoForge"
                                                        )
                                                        downloadDependencyMod(
                                                            context = context,
                                                            baseVersion = instanceMcVersion,
                                                            instanceDir = instanceDir,
                                                            query = "Embeddium",
                                                            preferFabric = false,
                                                            preferForge = activeLoader == "Forge",
                                                            preferNeoForge = activeLoader == "NeoForge"
                                                        )
                                                    } else {
                                                        downloadDependencyMod(
                                                            context = context,
                                                            baseVersion = instanceMcVersion,
                                                            instanceDir = instanceDir,
                                                            query = "Iris Shaders",
                                                            preferFabric = true,
                                                            preferForge = false
                                                        )
                                                        downloadDependencyMod(
                                                            context = context,
                                                            baseVersion = instanceMcVersion,
                                                            instanceDir = instanceDir,
                                                            query = "Sodium",
                                                            preferFabric = true,
                                                            preferForge = false
                                                        )
                                                    }
                                                }
                                                withContext(Dispatchers.Main) { downloadProgress = 0.55f }
                                                downloadFileBlocking(versionUrl, destinationFile)
                                                if (isResourcePack) enableResourcePack(instanceDir, destinationFile.name)
                                                if (isShaderPack) enableShaderPack(instanceDir, destinationFile.name)
                                                withContext(Dispatchers.Main) {
                                                    downloadProgress = 1f
                                                    val message = when {
                                                        (isForgeMod || isNeoForgeMod) && forgePreparation?.installerFile != null -> context.getString(R.string.mod_downloaded_forge_installer_warning)
                                                        isForgeMod -> context.getString(R.string.mod_installed_forge)
                                                        isNeoForgeMod -> context.getString(R.string.mod_installed_neoforge)
                                                        isShaderPack -> context.getString(R.string.shader_installed_enabled)
                                                        isResourcePack -> context.getString(R.string.resourcepack_installed_enabled)
                                                        else -> context.getString(R.string.mod_installed_default)
                                                    }
                                                    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                                                    onRefresh()
                                                    onDismiss()
                                                    forgePreparation?.installerFile?.let { installerFile ->
                                                        val targetMcJava = try {
                                                            com.cannon.onyxlauncher.Tools.getVersionInfo(instanceMcVersion)?.javaVersion?.majorVersion ?: 8
                                                        } catch (e: Exception) {
                                                            8
                                                        }
                                                        ensureJreForInstaller(context, installerFile, targetMcJava)
                                                        val installIntent = Intent(context, JavaGUILauncherActivity::class.java)
                                                        installIntent.putExtra("targetJavaVersion", targetMcJava)
                                                        com.cannon.onyxlauncher.modloaders.ForgeUtils.addAutoInstallArgs(
                                                            installIntent,
                                                            installerFile,
                                                            forgePreparation!!.launchVersion
                                                        )
                                                        context.startActivity(installIntent)
                                                    }
                                                }
                                            }
                                        } catch (err: Throwable) {
                                            Log.e("OnyxLauncher", "Failed to install content", err)
                                            Toast.makeText(context, context.getString(R.string.install_error_msg, err.message), Toast.LENGTH_LONG).show()
                                            downloadProgress = null
                                        }
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = MicrosoftGreen)
                            ) {
                                Text(text = stringResource(R.string.button_install), color = Color.White)
                            }
                        }
                    }
                }
            }
        }
    )
}

@Composable
fun DetailRow(label: String, value: String, isFirst: Boolean = false) {
    if (!isFirst) {
        HorizontalDivider(color = StrokeColor, modifier = Modifier.padding(vertical = 12.dp))
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, color = TextSecondary, fontSize = 14.sp)
        Text(text = value, color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

fun getDirectorySize(directory: File): Long {
    var size = 0L
    if (directory.exists() && directory.isDirectory) {
        val files = directory.listFiles()
        if (files != null) {
            for (file in files) {
                if (file.isFile) {
                    size += file.length()
                } else if (file.isDirectory) {
                    size += getDirectorySize(file)
                }
            }
        }
    }
    return size
}

data class PackMetadata(
    val title: String,
    val description: String,
    val iconBitmap: android.graphics.Bitmap?
)

fun getPackMetadata(file: File, isShader: Boolean): PackMetadata {
    val cleanName = file.name.removeSuffix(".zip").removeSuffix(".ZIP")
        .replace("_", " ")
        .replace("-", " ")
        .replace("\\s+".toRegex(), " ")
        .trim()

    var title = cleanName.split(" ").joinToString(" ") { it.replaceFirstChar { char -> char.uppercase() } }

    title = title.replace("(?i)\\b(v?\\d+(\\.\\d+)*[a-z]?)\\b".toRegex(), "")
        .replace("(?i)\\b(1\\.\\d+(\\.\\d+)?)\\b".toRegex(), "")
        .replace("\\s+".toRegex(), " ")
        .trim()

    if (title.isEmpty()) {
        title = file.name.removeSuffix(".zip")
    }

    var description = "Paczka lokalna"
    var iconBitmap: android.graphics.Bitmap? = null

    try {
        java.util.zip.ZipFile(file).use { zip ->
            val mcMetaEntry = zip.getEntry("pack.mcmeta")
            if (mcMetaEntry != null) {
                zip.getInputStream(mcMetaEntry).bufferedReader().use { reader ->
                    val content = reader.readText()
                    val json = org.json.JSONObject(content)
                    if (json.has("pack")) {
                        val packObj = json.getJSONObject("pack")
                        if (packObj.has("description")) {
                            description = packObj.getString("description")
                                .replace("§[0-9a-fk-or]".toRegex(), "")
                        }
                    }
                }
            }

            val iconEntry = zip.getEntry("pack.png")
            if (iconEntry != null) {
                zip.getInputStream(iconEntry).use { input ->
                    iconBitmap = android.graphics.BitmapFactory.decodeStream(input)
                }
            }
        }
    } catch (e: Exception) {
        // Fallback
    }

    if (isShader && description == "Paczka lokalna") {
        description = "Kolekcja shaderów OpenGL"
    }

    return PackMetadata(title, description, iconBitmap)
}

