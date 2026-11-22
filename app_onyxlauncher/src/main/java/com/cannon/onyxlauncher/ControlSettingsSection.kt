package com.cannon.onyxlauncher

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import com.cannon.onyxlauncher.customcontrols.ControlData
import com.cannon.onyxlauncher.customcontrols.ControlJoystickData
import com.cannon.onyxlauncher.customcontrols.ControlLayout
import com.cannon.onyxlauncher.customcontrols.CustomControls
import com.cannon.onyxlauncher.prefs.LauncherPreferences
import com.cannon.onyxlauncher.utils.LocaleUtils
import org.lwjgl.glfw.CallbackBridge
import java.io.File
import java.util.Locale

data class ControlLayoutOption(
    val id: String,
    val displayName: String,
    val filePath: String,
    val isBuiltIn: Boolean = false
)

private val EmeraldActiveBg = Color(0xFF0D3B2E)
private val EmeraldActiveText = Color(0xFF6EE7B7)
private val EmeraldActiveBadge = Color(0xFF059669)
private val DeleteDangerColor = Color(0xFFEF4444)

// Standard 1:1 PojavLauncher default.json colors & styling
private const val POJAV_BG_COLOR: Int = 1291845632 // 0x4D000000
private const val POJAV_STROKE_COLOR: Int = -1     // 0xFFFFFFFF

private fun normalizeControlPath(path: String?): String {
    if (path.isNullOrBlank()) return ""
    return try {
        File(path).canonicalPath
    } catch (_: Exception) {
        File(path).absolutePath
    }
}

internal fun isSameControlPath(a: String?, b: String?): Boolean {
    val normA = normalizeControlPath(a)
    val normB = normalizeControlPath(b)
    return normA.isNotEmpty() && normA.equals(normB, ignoreCase = false)
}

fun ensureControlPreferencesReady(context: Context) {
    if (LauncherPreferences.DEFAULT_PREF == null) {
        LauncherPreferences.loadPreferences(context)
    }
    if (Tools.currentDisplayMetrics == null) {
        Tools.currentDisplayMetrics = context.resources.displayMetrics
    }
}

private data class BuiltInPresetSpec(
    val id: String,
    val fileName: String,
    val titleResId: Int,
    val builder: () -> CustomControls
)

private fun makePojavBtn(
    name: String,
    key1: Int,
    key2: Int = 0,
    x: String,
    y: String,
    wDp: Float = 50f,
    hDp: Float = 50f,
    isToggle: Boolean = false,
    opacity: Float = 1f,
    bgColor: Int = POJAV_BG_COLOR,
    strokeColor: Int = POJAV_STROKE_COLOR,
    strokeWidth: Float = 0f,
    cornerRadius: Float = 0f,
    displayInGame: Boolean = true,
    displayInMenu: Boolean = true,
    isSwipeable: Boolean = false,
    passThru: Boolean = false
): ControlData {
    return ControlData(
        name,
        intArrayOf(key1, key2, 0, 0),
        x,
        y,
        wDp,
        hDp,
        isToggle,
        opacity,
        bgColor,
        strokeColor,
        strokeWidth,
        cornerRadius,
        displayInGame,
        displayInMenu,
        isSwipeable,
        passThru
    )
}

private fun makePojavJoystick(
    x: String,
    y: String,
    sizeDp: Float = 120f,
    forwardLock: Boolean = true,
    absolute: Boolean = false,
    opacity: Float = 1f,
    bgColor: Int = POJAV_BG_COLOR,
    strokeColor: Int = POJAV_STROKE_COLOR,
    strokeWidth: Float = 1f
): ControlJoystickData {
    val base = ControlData(
        "Joystick",
        intArrayOf(0, 0, 0, 0),
        x,
        y,
        sizeDp,
        sizeDp,
        false,
        opacity,
        bgColor,
        strokeColor,
        strokeWidth,
        50f,
        true,
        false,
        false,
        false
    )
    return ControlJoystickData(base, forwardLock, absolute)
}

/**
 * 1. Onyx Modern (Joystick + Actions)
 * 1:1 PojavLauncher appearance (80x30 top buttons, 50x50 square grid buttons, 0x4D000000 bg, 0 cornerRadius).
 */
private fun buildOnyxModernJoystickLayout(): CustomControls {
    val buttons = arrayListOf(
        // Top bar (all uniform 80x30 like Pojav default.json)
        makePojavBtn("Debug", LwjglGlfwKeycode.GLFW_KEY_F3.toInt(), x = "\${margin}", y = "\${margin}", wDp = 80f, hDp = 30f),
        makePojavBtn("Chat", LwjglGlfwKeycode.GLFW_KEY_T.toInt(), x = "\${margin} * 2 + \${width}", y = "\${margin}", wDp = 80f, hDp = 30f),
        makePojavBtn("Keyboard", ControlData.SPECIALBTN_KEYBOARD, x = "\${margin} * 3 + \${width} * 2", y = "\${margin}", wDp = 80f, hDp = 30f),
        makePojavBtn("Tab", LwjglGlfwKeycode.GLFW_KEY_TAB.toInt(), x = "\${margin} * 4 + \${width} * 3", y = "\${margin}", wDp = 80f, hDp = 30f),
        makePojavBtn("3rd", LwjglGlfwKeycode.GLFW_KEY_F5.toInt(), x = "\${margin}", y = "\${margin} * 2 + \${height}", wDp = 80f, hDp = 30f),
        makePojavBtn("GUI", ControlData.SPECIALBTN_TOGGLECTRL, x = "\${right} - \${margin} * 2 - \${width}", y = "\${margin}", wDp = 80f, hDp = 30f),
        makePojavBtn("Mouse", ControlData.SPECIALBTN_VIRTUALMOUSE, x = "\${right} - \${margin}", y = "\${margin}", wDp = 80f, hDp = 30f),

        // Bottom-left auxiliaries next to 120dp joystick (all uniform 50x50)
        makePojavBtn("◇", LwjglGlfwKeycode.GLFW_KEY_LEFT_SHIFT.toInt(), x = "\${margin} * 5 + \${width} * 2.5", y = "\${bottom} - \${margin}", wDp = 50f, hDp = 50f, isToggle = true),
        makePojavBtn("F", LwjglGlfwKeycode.GLFW_KEY_F.toInt(), x = "\${margin} * 5 + \${width} * 2.5", y = "\${bottom} - \${margin} * 2 - \${height}", wDp = 50f, hDp = 50f),

        // Bottom-right 3x2 Pojav action grid (all uniform 50x50)
        makePojavBtn("Inv", LwjglGlfwKeycode.GLFW_KEY_E.toInt(), x = "\${right} - \${margin} * 3 - \${width} * 2", y = "\${bottom} - \${margin}", wDp = 50f, hDp = 50f),
        makePojavBtn("PRI", ControlData.SPECIALBTN_MOUSEPRI, x = "\${right} - \${margin} * 2 - \${width}", y = "\${bottom} - \${margin}", wDp = 50f, hDp = 50f),
        makePojavBtn("⬛", LwjglGlfwKeycode.GLFW_KEY_SPACE.toInt(), x = "\${right} - \${margin}", y = "\${bottom} - \${margin}", wDp = 50f, hDp = 50f),

        makePojavBtn("Q", LwjglGlfwKeycode.GLFW_KEY_Q.toInt(), x = "\${right} - \${margin} * 3 - \${width} * 2", y = "\${bottom} - \${margin} * 2 - \${height}", wDp = 50f, hDp = 50f),
        makePojavBtn("SEC", ControlData.SPECIALBTN_MOUSESEC, x = "\${right} - \${margin} * 2 - \${width}", y = "\${bottom} - \${margin} * 2 - \${height}", wDp = 50f, hDp = 50f),
        makePojavBtn("Sprint", LwjglGlfwKeycode.GLFW_KEY_LEFT_CONTROL.toInt(), x = "\${right} - \${margin}", y = "\${bottom} - \${margin} * 2 - \${height}", wDp = 50f, hDp = 50f, isToggle = true)
    )
    val joysticks = arrayListOf(
        makePojavJoystick(x = "\${margin} * 2", y = "\${bottom} - \${margin} * 2", sizeDp = 120f, forwardLock = true)
    )
    return CustomControls(buttons, arrayListOf(), joysticks).apply { version = 8; scaledAt = 100f }
}

/**
 * 2. Modpack & Quests (Tech / RPG)
 * 1:1 PojavLauncher appearance with a clean second top row (70x30) for Map, Backpack, Quests, Zoom, JEI R/U, Skill.
 */
private fun buildModpackQuestsLayout(): CustomControls {
    val buttons = arrayListOf(
        // Top row 1: standard Pojav bar (all uniform 80x30)
        makePojavBtn("Debug", LwjglGlfwKeycode.GLFW_KEY_F3.toInt(), x = "\${margin}", y = "\${margin}", wDp = 80f, hDp = 30f),
        makePojavBtn("Chat", LwjglGlfwKeycode.GLFW_KEY_T.toInt(), x = "\${margin} * 2 + \${width}", y = "\${margin}", wDp = 80f, hDp = 30f),
        makePojavBtn("Keyboard", ControlData.SPECIALBTN_KEYBOARD, x = "\${margin} * 3 + \${width} * 2", y = "\${margin}", wDp = 80f, hDp = 30f),
        makePojavBtn("Tab", LwjglGlfwKeycode.GLFW_KEY_TAB.toInt(), x = "\${margin} * 4 + \${width} * 3", y = "\${margin}", wDp = 80f, hDp = 30f),
        makePojavBtn("GUI", ControlData.SPECIALBTN_TOGGLECTRL, x = "\${right} - \${margin} * 2 - \${width}", y = "\${margin}", wDp = 80f, hDp = 30f),
        makePojavBtn("Mouse", ControlData.SPECIALBTN_VIRTUALMOUSE, x = "\${right} - \${margin}", y = "\${margin}", wDp = 80f, hDp = 30f),

        // Top row 2: Modpack & JEI shortcuts (all uniform 70x30 so ${width} and ${height} math is exact)
        makePojavBtn("Mapa", LwjglGlfwKeycode.GLFW_KEY_M.toInt(), x = "\${margin}", y = "\${margin} * 2 + \${height}", wDp = 70f, hDp = 30f),
        makePojavBtn("Plecak", LwjglGlfwKeycode.GLFW_KEY_B.toInt(), x = "\${margin} * 2 + \${width}", y = "\${margin} * 2 + \${height}", wDp = 70f, hDp = 30f),
        makePojavBtn("Quest", LwjglGlfwKeycode.GLFW_KEY_GRAVE_ACCENT.toInt(), x = "\${margin} * 3 + \${width} * 2", y = "\${margin} * 2 + \${height}", wDp = 70f, hDp = 30f),
        makePojavBtn("Zoom", LwjglGlfwKeycode.GLFW_KEY_C.toInt(), x = "\${margin} * 4 + \${width} * 3", y = "\${margin} * 2 + \${height}", wDp = 70f, hDp = 30f),

        makePojavBtn("Skill", LwjglGlfwKeycode.GLFW_KEY_G.toInt(), x = "\${right} - \${margin} * 3 - \${width} * 2", y = "\${margin} * 2 + \${height}", wDp = 70f, hDp = 30f),
        makePojavBtn("JEI U", LwjglGlfwKeycode.GLFW_KEY_U.toInt(), x = "\${right} - \${margin} * 2 - \${width}", y = "\${margin} * 2 + \${height}", wDp = 70f, hDp = 30f),
        makePojavBtn("JEI R", LwjglGlfwKeycode.GLFW_KEY_R.toInt(), x = "\${right} - \${margin}", y = "\${margin} * 2 + \${height}", wDp = 70f, hDp = 30f),

        // Bottom-left auxiliaries next to joystick (all uniform 50x50)
        makePojavBtn("◇", LwjglGlfwKeycode.GLFW_KEY_LEFT_SHIFT.toInt(), x = "\${margin} * 5 + \${width} * 2.5", y = "\${bottom} - \${margin}", wDp = 50f, hDp = 50f, isToggle = true),
        makePojavBtn("F", LwjglGlfwKeycode.GLFW_KEY_F.toInt(), x = "\${margin} * 5 + \${width} * 2.5", y = "\${bottom} - \${margin} * 2 - \${height}", wDp = 50f, hDp = 50f),

        // Bottom-right 3x2 Pojav action grid (all uniform 50x50)
        makePojavBtn("Inv", LwjglGlfwKeycode.GLFW_KEY_E.toInt(), x = "\${right} - \${margin} * 3 - \${width} * 2", y = "\${bottom} - \${margin}", wDp = 50f, hDp = 50f),
        makePojavBtn("PRI", ControlData.SPECIALBTN_MOUSEPRI, x = "\${right} - \${margin} * 2 - \${width}", y = "\${bottom} - \${margin}", wDp = 50f, hDp = 50f),
        makePojavBtn("⬛", LwjglGlfwKeycode.GLFW_KEY_SPACE.toInt(), x = "\${right} - \${margin}", y = "\${bottom} - \${margin}", wDp = 50f, hDp = 50f),

        makePojavBtn("Q", LwjglGlfwKeycode.GLFW_KEY_Q.toInt(), x = "\${right} - \${margin} * 3 - \${width} * 2", y = "\${bottom} - \${margin} * 2 - \${height}", wDp = 50f, hDp = 50f),
        makePojavBtn("SEC", ControlData.SPECIALBTN_MOUSESEC, x = "\${right} - \${margin} * 2 - \${width}", y = "\${bottom} - \${margin} * 2 - \${height}", wDp = 50f, hDp = 50f),
        makePojavBtn("3rd", LwjglGlfwKeycode.GLFW_KEY_F5.toInt(), x = "\${right} - \${margin}", y = "\${bottom} - \${margin} * 2 - \${height}", wDp = 50f, hDp = 50f)
    )
    val joysticks = arrayListOf(
        makePojavJoystick(x = "\${margin} * 2", y = "\${bottom} - \${margin} * 2", sizeDp = 120f, forwardLock = true)
    )
    return CustomControls(buttons, arrayListOf(), joysticks).apply { version = 8; scaledAt = 100f }
}

/**
 * 3. PvP & Claw (4 Fingers + Top Index Triggers)
 * 1:1 PojavLauncher appearance with 80x50 top triggers for index fingers and 50x50 D-pad & combat grid.
 */
private fun buildPvpClawLayout(): CustomControls {
    val buttons = arrayListOf(
        // Top row 1: utility bar (all uniform 80x30)
        makePojavBtn("Debug", LwjglGlfwKeycode.GLFW_KEY_F3.toInt(), x = "\${margin}", y = "\${margin}", wDp = 80f, hDp = 30f),
        makePojavBtn("Chat", LwjglGlfwKeycode.GLFW_KEY_T.toInt(), x = "\${margin} * 2 + \${width}", y = "\${margin}", wDp = 80f, hDp = 30f),
        makePojavBtn("Keyboard", ControlData.SPECIALBTN_KEYBOARD, x = "\${margin} * 3 + \${width} * 2", y = "\${margin}", wDp = 80f, hDp = 30f),
        makePojavBtn("Tab", LwjglGlfwKeycode.GLFW_KEY_TAB.toInt(), x = "\${margin} * 4 + \${width} * 3", y = "\${margin}", wDp = 80f, hDp = 30f),
        makePojavBtn("GUI", ControlData.SPECIALBTN_TOGGLECTRL, x = "\${right} - \${margin} * 2 - \${width}", y = "\${margin}", wDp = 80f, hDp = 30f),
        makePojavBtn("Mouse", ControlData.SPECIALBTN_VIRTUALMOUSE, x = "\${right} - \${margin}", y = "\${margin}", wDp = 80f, hDp = 30f),

        // Top index-finger Claw triggers (all uniform 80x50 at y = ${margin} * 2 + ${height} * 0.65)
        makePojavBtn("PRI", ControlData.SPECIALBTN_MOUSEPRI, x = "\${margin}", y = "\${margin} * 3 + \${height} * 0.65", wDp = 80f, hDp = 50f),
        makePojavBtn("Sprint", LwjglGlfwKeycode.GLFW_KEY_LEFT_CONTROL.toInt(), x = "\${margin} * 2 + \${width}", y = "\${margin} * 3 + \${height} * 0.65", wDp = 80f, hDp = 50f, isToggle = true),
        makePojavBtn("3rd", LwjglGlfwKeycode.GLFW_KEY_F5.toInt(), x = "\${right} - \${margin} * 2 - \${width}", y = "\${margin} * 3 + \${height} * 0.65", wDp = 80f, hDp = 50f),
        makePojavBtn("SEC", ControlData.SPECIALBTN_MOUSESEC, x = "\${right} - \${margin}", y = "\${margin} * 3 + \${height} * 0.65", wDp = 80f, hDp = 50f),

        // Bottom-left Pojav D-Pad grid (all uniform 50x50)
        makePojavBtn("▲", LwjglGlfwKeycode.GLFW_KEY_W.toInt(), x = "\${margin} * 2 + \${width}", y = "\${bottom} - \${margin} * 3 - \${height} * 2", wDp = 50f, hDp = 50f, isSwipeable = true),
        makePojavBtn("◀", LwjglGlfwKeycode.GLFW_KEY_A.toInt(), x = "\${margin}", y = "\${bottom} - \${margin} * 2 - \${height}", wDp = 50f, hDp = 50f, isSwipeable = true),
        makePojavBtn("◇", LwjglGlfwKeycode.GLFW_KEY_LEFT_SHIFT.toInt(), x = "\${margin} * 2 + \${width}", y = "\${bottom} - \${margin} * 2 - \${height}", wDp = 50f, hDp = 50f, isToggle = true),
        makePojavBtn("▶", LwjglGlfwKeycode.GLFW_KEY_D.toInt(), x = "\${margin} * 3 + \${width} * 2", y = "\${bottom} - \${margin} * 2 - \${height}", wDp = 50f, hDp = 50f, isSwipeable = true),
        makePojavBtn("▼", LwjglGlfwKeycode.GLFW_KEY_S.toInt(), x = "\${margin} * 2 + \${width}", y = "\${bottom} - \${margin}", wDp = 50f, hDp = 50f, isSwipeable = true),
        makePojavBtn("Inv", LwjglGlfwKeycode.GLFW_KEY_E.toInt(), x = "\${margin} * 3 + \${width} * 2", y = "\${bottom} - \${margin}", wDp = 50f, hDp = 50f),

        // Bottom-right combat grid (all uniform 50x50)
        makePojavBtn("Q", LwjglGlfwKeycode.GLFW_KEY_Q.toInt(), x = "\${right} - \${margin} * 3 - \${width} * 2", y = "\${bottom} - \${margin}", wDp = 50f, hDp = 50f),
        makePojavBtn("F", LwjglGlfwKeycode.GLFW_KEY_F.toInt(), x = "\${right} - \${margin} * 2 - \${width}", y = "\${bottom} - \${margin}", wDp = 50f, hDp = 50f),
        makePojavBtn("⬛", LwjglGlfwKeycode.GLFW_KEY_SPACE.toInt(), x = "\${right} - \${margin}", y = "\${bottom} - \${margin}", wDp = 50f, hDp = 50f),

        makePojavBtn("1", LwjglGlfwKeycode.GLFW_KEY_1.toInt(), x = "\${right} - \${margin} * 3 - \${width} * 2", y = "\${bottom} - \${margin} * 2 - \${height}", wDp = 50f, hDp = 50f),
        makePojavBtn("2", LwjglGlfwKeycode.GLFW_KEY_2.toInt(), x = "\${right} - \${margin} * 2 - \${width}", y = "\${bottom} - \${margin} * 2 - \${height}", wDp = 50f, hDp = 50f),
        makePojavBtn("3", LwjglGlfwKeycode.GLFW_KEY_3.toInt(), x = "\${right} - \${margin}", y = "\${bottom} - \${margin} * 2 - \${height}", wDp = 50f, hDp = 50f)
    )
    return CustomControls(buttons, arrayListOf(), arrayListOf()).apply { version = 8; scaledAt = 100f }
}

/**
 * 4. Bedrock / Pocket Classic
 * 1:1 PojavLauncher appearance with 3x3 D-pad (including diagonal strafe ↖ / ↗) on left and Jump/PRI/SEC on right.
 */
private fun buildBedrockClassicLayout(): CustomControls {
    val buttons = arrayListOf(
        // Top bar (all uniform 80x30)
        makePojavBtn("ESC", LwjglGlfwKeycode.GLFW_KEY_ESCAPE.toInt(), x = "\${margin}", y = "\${margin}", wDp = 80f, hDp = 30f),
        makePojavBtn("3rd", LwjglGlfwKeycode.GLFW_KEY_F5.toInt(), x = "\${margin} * 2 + \${width}", y = "\${margin}", wDp = 80f, hDp = 30f),
        makePojavBtn("Chat", LwjglGlfwKeycode.GLFW_KEY_T.toInt(), x = "\${margin} * 3 + \${width} * 2", y = "\${margin}", wDp = 80f, hDp = 30f),
        makePojavBtn("Keyboard", ControlData.SPECIALBTN_KEYBOARD, x = "\${margin} * 4 + \${width} * 3", y = "\${margin}", wDp = 80f, hDp = 30f),
        makePojavBtn("GUI", ControlData.SPECIALBTN_TOGGLECTRL, x = "\${right} - \${margin} * 2 - \${width}", y = "\${margin}", wDp = 80f, hDp = 30f),
        makePojavBtn("Mouse", ControlData.SPECIALBTN_VIRTUALMOUSE, x = "\${right} - \${margin}", y = "\${margin}", wDp = 80f, hDp = 30f),

        // Bottom-left 3x3 D-Pad with diagonals (all uniform 50x50)
        makePojavBtn("↖", LwjglGlfwKeycode.GLFW_KEY_W.toInt(), LwjglGlfwKeycode.GLFW_KEY_A.toInt(), x = "\${margin}", y = "\${bottom} - \${margin} * 3 - \${height} * 2", wDp = 50f, hDp = 50f, isSwipeable = true),
        makePojavBtn("▲", LwjglGlfwKeycode.GLFW_KEY_W.toInt(), x = "\${margin} * 2 + \${width}", y = "\${bottom} - \${margin} * 3 - \${height} * 2", wDp = 50f, hDp = 50f, isSwipeable = true),
        makePojavBtn("↗", LwjglGlfwKeycode.GLFW_KEY_W.toInt(), LwjglGlfwKeycode.GLFW_KEY_D.toInt(), x = "\${margin} * 3 + \${width} * 2", y = "\${bottom} - \${margin} * 3 - \${height} * 2", wDp = 50f, hDp = 50f, isSwipeable = true),

        makePojavBtn("◀", LwjglGlfwKeycode.GLFW_KEY_A.toInt(), x = "\${margin}", y = "\${bottom} - \${margin} * 2 - \${height}", wDp = 50f, hDp = 50f, isSwipeable = true),
        makePojavBtn("◇", LwjglGlfwKeycode.GLFW_KEY_LEFT_SHIFT.toInt(), x = "\${margin} * 2 + \${width}", y = "\${bottom} - \${margin} * 2 - \${height}", wDp = 50f, hDp = 50f, isToggle = true),
        makePojavBtn("▶", LwjglGlfwKeycode.GLFW_KEY_D.toInt(), x = "\${margin} * 3 + \${width} * 2", y = "\${bottom} - \${margin} * 2 - \${height}", wDp = 50f, hDp = 50f, isSwipeable = true),

        makePojavBtn("▼", LwjglGlfwKeycode.GLFW_KEY_S.toInt(), x = "\${margin} * 2 + \${width}", y = "\${bottom} - \${margin}", wDp = 50f, hDp = 50f, isSwipeable = true),
        makePojavBtn("Inv", LwjglGlfwKeycode.GLFW_KEY_E.toInt(), x = "\${margin} * 3 + \${width} * 2", y = "\${bottom} - \${margin}", wDp = 50f, hDp = 50f),

        // Bottom-right 2x2 action grid (all uniform 50x50)
        makePojavBtn("PRI", ControlData.SPECIALBTN_MOUSEPRI, x = "\${right} - \${margin} * 2 - \${width}", y = "\${bottom} - \${margin} * 2 - \${height}", wDp = 50f, hDp = 50f),
        makePojavBtn("SEC", ControlData.SPECIALBTN_MOUSESEC, x = "\${right} - \${margin}", y = "\${bottom} - \${margin} * 2 - \${height}", wDp = 50f, hDp = 50f),
        makePojavBtn("F", LwjglGlfwKeycode.GLFW_KEY_F.toInt(), x = "\${right} - \${margin} * 2 - \${width}", y = "\${bottom} - \${margin}", wDp = 50f, hDp = 50f),
        makePojavBtn("⬛", LwjglGlfwKeycode.GLFW_KEY_SPACE.toInt(), x = "\${right} - \${margin}", y = "\${bottom} - \${margin}", wDp = 50f, hDp = 50f)
    )
    return CustomControls(buttons, arrayListOf(), arrayListOf()).apply { version = 8; scaledAt = 100f }
}

/**
 * 5. Minimalist (Clean Screen)
 * 1:1 PojavLauncher appearance & button proportions (80x30 top, 50x50 bottom), with only essential controls.
 */
private fun buildMinimalCleanLayout(): CustomControls {
    val buttons = arrayListOf(
        // Top essential buttons (all uniform 80x30)
        makePojavBtn("Chat", LwjglGlfwKeycode.GLFW_KEY_T.toInt(), x = "\${margin}", y = "\${margin}", wDp = 80f, hDp = 30f, opacity = 0.75f),
        makePojavBtn("Keyboard", ControlData.SPECIALBTN_KEYBOARD, x = "\${margin} * 2 + \${width}", y = "\${margin}", wDp = 80f, hDp = 30f, opacity = 0.75f),
        makePojavBtn("GUI", ControlData.SPECIALBTN_TOGGLECTRL, x = "\${right} - \${margin} * 2 - \${width}", y = "\${margin}", wDp = 80f, hDp = 30f, opacity = 0.75f),
        makePojavBtn("Mouse", ControlData.SPECIALBTN_VIRTUALMOUSE, x = "\${right} - \${margin}", y = "\${margin}", wDp = 80f, hDp = 30f, opacity = 0.75f),

        // Bottom-left sneak toggle next to joystick (50x50)
        makePojavBtn("◇", LwjglGlfwKeycode.GLFW_KEY_LEFT_SHIFT.toInt(), x = "\${margin} * 5 + \${width} * 2.4", y = "\${bottom} - \${margin}", wDp = 50f, hDp = 50f, isToggle = true, opacity = 0.75f),

        // Bottom-right essential actions (all uniform 50x50)
        makePojavBtn("Inv", LwjglGlfwKeycode.GLFW_KEY_E.toInt(), x = "\${right} - \${margin} * 3 - \${width} * 2", y = "\${bottom} - \${margin}", wDp = 50f, hDp = 50f, opacity = 0.75f),
        makePojavBtn("PRI", ControlData.SPECIALBTN_MOUSEPRI, x = "\${right} - \${margin} * 2 - \${width}", y = "\${bottom} - \${margin}", wDp = 50f, hDp = 50f, opacity = 0.75f),
        makePojavBtn("⬛", LwjglGlfwKeycode.GLFW_KEY_SPACE.toInt(), x = "\${right} - \${margin}", y = "\${bottom} - \${margin}", wDp = 50f, hDp = 50f, opacity = 0.75f),
        makePojavBtn("SEC", ControlData.SPECIALBTN_MOUSESEC, x = "\${right} - \${margin} * 2 - \${width}", y = "\${bottom} - \${margin} * 2 - \${height}", wDp = 50f, hDp = 50f, opacity = 0.75f)
    )
    val joysticks = arrayListOf(
        makePojavJoystick(x = "\${margin} * 2", y = "\${bottom} - \${margin} * 2", sizeDp = 115f, forwardLock = true, opacity = 0.75f)
    )
    return CustomControls(buttons, arrayListOf(), joysticks).apply { version = 8; scaledAt = 100f }
}

private val BUILT_IN_PRESET_SPECS = listOf(
    BuiltInPresetSpec("onyx_modern", "onyx_modern_joystick.json", R.string.controls_layout_onyx_modern, ::buildOnyxModernJoystickLayout),
    BuiltInPresetSpec("onyx_modpack", "onyx_modpack_quests.json", R.string.controls_layout_modpack_quests, ::buildModpackQuestsLayout),
    BuiltInPresetSpec("onyx_pvp", "onyx_pvp_claw.json", R.string.controls_layout_pvp_claw, ::buildPvpClawLayout),
    BuiltInPresetSpec("onyx_bedrock", "onyx_bedrock_dpad.json", R.string.controls_layout_bedrock_classic, ::buildBedrockClassicLayout),
    BuiltInPresetSpec("onyx_minimal", "onyx_minimal_clean.json", R.string.controls_layout_minimal_clean, ::buildMinimalCleanLayout)
)

private fun ensureBuiltInPresetsOnDisk(ctrlDir: File) {
    val markerFile = File(ctrlDir, ".onyx_builtin_presets_v2")
    val forceRefresh = !markerFile.isFile
    for (spec in BUILT_IN_PRESET_SPECS) {
        val targetFile = File(ctrlDir, spec.fileName)
        if (forceRefresh || !targetFile.exists() || targetFile.length() == 0L) {
            try {
                spec.builder().save(targetFile.absolutePath)
            } catch (e: Exception) {
                Log.w("OnyxControls", "Failed writing preset ${spec.fileName}", e)
            }
        }
    }
    if (forceRefresh) {
        try {
            markerFile.writeText("v2")
        } catch (_: Exception) {
        }
    }
}

fun loadAvailableControlLayouts(context: Context): List<ControlLayoutOption> {
    ensureControlPreferencesReady(context)

    val ctrlDir = File(Tools.CTRLMAP_PATH)
    if (!ctrlDir.exists()) {
        ctrlDir.mkdirs()
    }

    val defaultFile = File(Tools.CTRLDEF_FILE)
    if (!defaultFile.exists() || defaultFile.length() == 0L) {
        try {
            Tools.copyAssetFile(context, "default.json", Tools.CTRLMAP_PATH, false)
        } catch (e: Exception) {
            Log.w("OnyxControls", "Failed to unpack default.json", e)
        }
    }

    ensureBuiltInPresetsOnDisk(ctrlDir)

    val result = mutableListOf<ControlLayoutOption>()
    val builtInPaths = mutableSetOf<String>()
    val builtInFileNames = mutableSetOf("default.json")

    // 1. Built-in default layout (Pojav Classic)
    val defaultCanonical = normalizeControlPath(defaultFile.absolutePath)
    builtInPaths.add(defaultCanonical)
    result.add(
        ControlLayoutOption(
            id = "pojav_classic",
            displayName = context.getString(R.string.controls_layout_pojav_classic),
            filePath = defaultFile.absolutePath,
            isBuiltIn = true
        )
    )

    // 2. Built-in Onyx presets
    for (spec in BUILT_IN_PRESET_SPECS) {
        val presetFile = File(ctrlDir, spec.fileName)
        builtInFileNames.add(spec.fileName.lowercase(Locale.ROOT))
        builtInPaths.add(normalizeControlPath(presetFile.absolutePath))
        if (presetFile.exists()) {
            result.add(
                ControlLayoutOption(
                    id = spec.id,
                    displayName = context.getString(spec.titleResId),
                    filePath = presetFile.absolutePath,
                    isBuiltIn = true
                )
            )
        }
    }

    // 3. User custom layouts in controlmap/*.json (placed at the bottom, below default/built-in layouts)
    val userFiles = ctrlDir.listFiles { file ->
        file.isFile && file.name.endsWith(".json", ignoreCase = true)
    }?.sortedBy { it.nameWithoutExtension.lowercase(Locale.ROOT) } ?: emptyList()

    for (file in userFiles) {
        val canonical = normalizeControlPath(file.absolutePath)
        if (canonical in builtInPaths || file.name.lowercase(Locale.ROOT) in builtInFileNames) {
            continue
        }
        result.add(
            ControlLayoutOption(
                id = file.name,
                displayName = file.nameWithoutExtension,
                filePath = file.absolutePath,
                isBuiltIn = false
            )
        )
    }

    return result
}

fun getActiveControlLayoutPath(context: Context): String {
    ensureControlPreferencesReady(context)
    val saved = LauncherPreferences.DEFAULT_PREF?.getString("defaultCtrl", Tools.CTRLDEF_FILE)
        ?: LauncherPreferences.PREF_DEFAULTCTRL_PATH
    if (saved.isNullOrBlank() || !File(saved).exists()) {
        return Tools.CTRLDEF_FILE
    }
    return saved
}

fun applyControlLayout(context: Context, option: ControlLayoutOption) {
    ensureControlPreferencesReady(context)
    LauncherPreferences.DEFAULT_PREF?.edit()?.putString("defaultCtrl", option.filePath)?.apply()
    LauncherPreferences.PREF_DEFAULTCTRL_PATH = option.filePath
    Toast.makeText(
        context,
        context.getString(R.string.controls_applied_toast, option.displayName),
        Toast.LENGTH_SHORT
    ).show()
}

fun deleteUserControlLayout(context: Context, option: ControlLayoutOption): Boolean {
    if (option.isBuiltIn) return false
    ensureControlPreferencesReady(context)
    val file = File(option.filePath)
    val wasActive = isSameControlPath(getActiveControlLayoutPath(context), option.filePath)
    val deleted = !file.exists() || file.delete()
    if (deleted) {
        if (wasActive) {
            LauncherPreferences.DEFAULT_PREF?.edit()?.putString("defaultCtrl", Tools.CTRLDEF_FILE)?.apply()
            LauncherPreferences.PREF_DEFAULTCTRL_PATH = Tools.CTRLDEF_FILE
        }
        Toast.makeText(
            context,
            context.getString(R.string.controls_deleted_toast, option.displayName),
            Toast.LENGTH_SHORT
        ).show()
    }
    return deleted
}

@Composable
fun ControlsSettingsSection() {
    val context = LocalContext.current
    var isExpanded by remember { mutableStateOf(false) }
    var layouts by remember { mutableStateOf(loadAvailableControlLayouts(context)) }
    var activeControlPath by remember { mutableStateOf(getActiveControlLayoutPath(context)) }
    var layoutPendingDelete by remember { mutableStateOf<ControlLayoutOption?>(null) }
    var showEditorBasePicker by remember { mutableStateOf(false) }

    // Refresh list and active selection when returning from ControlPreviewActivity or CustomControlsActivity
    val lifecycleOwner = context.findActivity() as? LifecycleOwner
    DisposableEffect(lifecycleOwner) {
        if (lifecycleOwner == null) {
            onDispose { }
        } else {
            val observer = LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) {
                    layouts = loadAvailableControlLayouts(context)
                    activeControlPath = getActiveControlLayoutPath(context)
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose {
                lifecycleOwner.lifecycle.removeObserver(observer)
            }
        }
    }

    val activeDisplayName = remember(layouts, activeControlPath) {
        layouts.find { isSameControlPath(it.filePath, activeControlPath) }?.displayName
            ?: File(activeControlPath).nameWithoutExtension.ifBlank {
                context.getString(R.string.controls_layout_pojav_classic)
            }
    }

    val builtInLayouts = remember(layouts) { layouts.filter { it.isBuiltIn } }
    val customLayouts = remember(layouts) { layouts.filter { !it.isBuiltIn } }

    // Collapsible Header Card
    Card(
        colors = CardDefaults.cardColors(containerColor = CardBg),
        border = BorderStroke(1.dp, StrokeColor),
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                if (!isExpanded) {
                    layouts = loadAvailableControlLayouts(context)
                    activeControlPath = getActiveControlLayoutPath(context)
                }
                isExpanded = !isExpanded
            }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.controls_settings_title),
                    color = TextPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = activeDisplayName,
                    color = AccentColor,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Icon(
                imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                contentDescription = "Expand",
                tint = TextSecondary
            )
        }
    }

    if (isExpanded) {
        Spacer(modifier = Modifier.height(12.dp))

        // Expanded Control Layouts List
        Card(
            colors = CardDefaults.cardColors(containerColor = CardBg),
            border = BorderStroke(1.dp, StrokeColor),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                // 1. Default / Built-in Layouts
                builtInLayouts.forEachIndexed { index, option ->
                    ControlLayoutRow(
                        option = option,
                        isApplied = isSameControlPath(activeControlPath, option.filePath),
                        onPreview = {
                            val intent = Intent(context, ControlPreviewActivity::class.java).apply {
                                putExtra(ControlPreviewActivity.EXTRA_LAYOUT_ID, option.id)
                                putExtra(ControlPreviewActivity.EXTRA_LAYOUT_NAME, option.displayName)
                                putExtra(ControlPreviewActivity.EXTRA_LAYOUT_PATH, option.filePath)
                            }
                            context.startActivity(intent)
                        },
                        onApply = {
                            applyControlLayout(context, option)
                            activeControlPath = option.filePath
                        },
                        onEdit = null,
                        onDelete = null
                    )

                    if (index < builtInLayouts.lastIndex || customLayouts.isNotEmpty()) {
                        HorizontalDivider(color = StrokeColor.copy(alpha = 0.5f), thickness = 0.5.dp)
                    }
                }

                // 2. User-created Custom Layouts at the bottom (below built-in layouts)
                if (customLayouts.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(BgDark.copy(alpha = 0.45f))
                            .padding(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.controls_custom_section_header),
                            color = AccentColor,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    HorizontalDivider(color = StrokeColor.copy(alpha = 0.5f), thickness = 0.5.dp)

                    customLayouts.forEachIndexed { index, option ->
                        ControlLayoutRow(
                            option = option,
                            isApplied = isSameControlPath(activeControlPath, option.filePath),
                            onPreview = {
                                val intent = Intent(context, ControlPreviewActivity::class.java).apply {
                                    putExtra(ControlPreviewActivity.EXTRA_LAYOUT_ID, option.id)
                                    putExtra(ControlPreviewActivity.EXTRA_LAYOUT_NAME, option.displayName)
                                    putExtra(ControlPreviewActivity.EXTRA_LAYOUT_PATH, option.filePath)
                                }
                                context.startActivity(intent)
                            },
                            onApply = {
                                applyControlLayout(context, option)
                                activeControlPath = option.filePath
                            },
                            onEdit = {
                                val intent = Intent(context, CustomControlsActivity::class.java).apply {
                                    putExtra(CustomControlsActivity.EXTRA_BASE_LAYOUT_PATH, option.filePath)
                                    putExtra(CustomControlsActivity.EXTRA_IS_BUILTIN_BASE, false)
                                }
                                context.startActivity(intent)
                            },
                            onDelete = {
                                layoutPendingDelete = option
                            }
                        )

                        if (index < customLayouts.lastIndex) {
                            HorizontalDivider(color = StrokeColor.copy(alpha = 0.5f), thickness = 0.5.dp)
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Open Pojav Custom Controls Editor (via Base Layout Picker)
        OutlinedButton(
            onClick = {
                layouts = loadAvailableControlLayouts(context)
                showEditorBasePicker = true
            },
            shape = RoundedCornerShape(10.dp),
            border = BorderStroke(1.dp, AccentColor.copy(alpha = 0.65f)),
            colors = ButtonDefaults.outlinedButtonColors(containerColor = CardBg),
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Tune,
                contentDescription = null,
                tint = AccentColor,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.controls_open_editor_button),
                color = AccentColor,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }

    // Delete Confirmation Dialog for user custom layouts
    val toDelete = layoutPendingDelete
    if (toDelete != null) {
        AlertDialog(
            onDismissRequest = { layoutPendingDelete = null },
            containerColor = CardBg,
            titleContentColor = TextPrimary,
            textContentColor = TextSecondary,
            title = {
                Text(
                    text = stringResource(R.string.controls_delete_confirm_title),
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp
                )
            },
            text = {
                Text(
                    text = stringResource(R.string.controls_delete_confirm_message, toDelete.displayName),
                    fontSize = 14.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        deleteUserControlLayout(context, toDelete)
                        layoutPendingDelete = null
                        layouts = loadAvailableControlLayouts(context)
                        activeControlPath = getActiveControlLayoutPath(context)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = DeleteDangerColor),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = stringResource(R.string.controls_delete_button),
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { layoutPendingDelete = null },
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, StrokeColor)
                ) {
                    Text(
                        text = stringResource(android.R.string.cancel),
                        color = TextPrimary
                    )
                }
            }
        )
    }

    // Base Layout Picker Dialog before launching CustomControlsActivity
    if (showEditorBasePicker) {
        AlertDialog(
            onDismissRequest = { showEditorBasePicker = false },
            containerColor = CardBg,
            titleContentColor = TextPrimary,
            textContentColor = TextSecondary,
            title = {
                Column {
                    Text(
                        text = stringResource(R.string.controls_pick_base_title),
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp,
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.controls_pick_base_subtitle),
                        fontSize = 13.sp,
                        color = TextSecondary,
                        fontWeight = FontWeight.Normal
                    )
                }
            },
            text = {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 360.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    item {
                        Text(
                            text = stringResource(R.string.controls_builtin_section_header),
                            color = TextSecondary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(vertical = 2.dp)
                        )
                    }
                    items(builtInLayouts, key = { it.id }) { option ->
                        EditorBaseOptionCard(
                            option = option,
                            onClick = {
                                showEditorBasePicker = false
                                val intent = Intent(context, CustomControlsActivity::class.java).apply {
                                    putExtra(CustomControlsActivity.EXTRA_BASE_LAYOUT_PATH, option.filePath)
                                    putExtra(CustomControlsActivity.EXTRA_IS_BUILTIN_BASE, true)
                                }
                                context.startActivity(intent)
                            }
                        )
                    }

                    if (customLayouts.isNotEmpty()) {
                        item {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = stringResource(R.string.controls_custom_section_header),
                                color = AccentColor,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(vertical = 2.dp)
                            )
                        }
                        items(customLayouts, key = { it.id }) { option ->
                            EditorBaseOptionCard(
                                option = option,
                                onClick = {
                                    showEditorBasePicker = false
                                    val intent = Intent(context, CustomControlsActivity::class.java).apply {
                                        putExtra(CustomControlsActivity.EXTRA_BASE_LAYOUT_PATH, option.filePath)
                                        putExtra(CustomControlsActivity.EXTRA_IS_BUILTIN_BASE, false)
                                    }
                                    context.startActivity(intent)
                                }
                            )
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                OutlinedButton(
                    onClick = { showEditorBasePicker = false },
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, StrokeColor)
                ) {
                    Text(
                        text = stringResource(android.R.string.cancel),
                        color = TextPrimary
                    )
                }
            }
        )
    }
}

@Composable
private fun ControlLayoutRow(
    option: ControlLayoutOption,
    isApplied: Boolean,
    onPreview: () -> Unit,
    onApply: () -> Unit,
    onEdit: (() -> Unit)?,
    onDelete: (() -> Unit)?
) {
    val isCustomRow = onEdit != null || onDelete != null
    if (!isCustomRow) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(if (isApplied) EmeraldActiveBg.copy(alpha = 0.5f) else Color.Transparent)
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = option.displayName,
                color = if (isApplied) EmeraldActiveText else TextPrimary,
                fontWeight = if (isApplied) FontWeight.Bold else FontWeight.Medium,
                fontSize = 14.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 8.dp)
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ControlActionButtons(
                    isApplied = isApplied,
                    onPreview = onPreview,
                    onApply = onApply,
                    onEdit = null,
                    onDelete = null
                )
            }
        }
    } else {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(if (isApplied) EmeraldActiveBg.copy(alpha = 0.5f) else Color.Transparent)
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Text(
                text = option.displayName,
                color = if (isApplied) EmeraldActiveText else TextPrimary,
                fontWeight = if (isApplied) FontWeight.Bold else FontWeight.SemiBold,
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ControlActionButtons(
                    isApplied = isApplied,
                    onPreview = onPreview,
                    onApply = onApply,
                    onEdit = onEdit,
                    onDelete = onDelete
                )
            }
        }
    }
}

@Composable
private fun ControlActionButtons(
    isApplied: Boolean,
    onPreview: () -> Unit,
    onApply: () -> Unit,
    onEdit: (() -> Unit)?,
    onDelete: (() -> Unit)?
) {
    OutlinedButton(
        onClick = onPreview,
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, StrokeColor),
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
        modifier = Modifier.height(34.dp)
    ) {
        Text(
            text = stringResource(R.string.controls_preview_button),
            color = TextPrimary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium
        )
    }

    if (onEdit != null) {
        OutlinedButton(
            onClick = onEdit,
            shape = RoundedCornerShape(8.dp),
            border = BorderStroke(1.dp, AccentColor.copy(alpha = 0.7f)),
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
            modifier = Modifier.height(34.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Edit,
                contentDescription = stringResource(R.string.controls_edit_button),
                tint = AccentColor,
                modifier = Modifier.size(13.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = stringResource(R.string.controls_edit_button),
                color = AccentColor,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }

    if (isApplied) {
        Surface(
            color = EmeraldActiveBadge,
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.height(34.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(14.dp)
                )
                Text(
                    text = stringResource(R.string.controls_applied_badge),
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    } else {
        Button(
            onClick = onApply,
            colors = ButtonDefaults.buttonColors(containerColor = AccentColor),
            shape = RoundedCornerShape(8.dp),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
            modifier = Modifier.height(34.dp)
        ) {
            Text(
                text = stringResource(R.string.controls_apply_button),
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }

    if (onDelete != null) {
        OutlinedButton(
            onClick = onDelete,
            shape = RoundedCornerShape(8.dp),
            border = BorderStroke(1.dp, DeleteDangerColor.copy(alpha = 0.7f)),
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
            modifier = Modifier.height(34.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Delete,
                contentDescription = stringResource(R.string.controls_delete_button),
                tint = DeleteDangerColor,
                modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = stringResource(R.string.controls_delete_button),
                color = DeleteDangerColor,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun EditorBaseOptionCard(
    option: ControlLayoutOption,
    onClick: () -> Unit
) {
    Surface(
        color = BgDark.copy(alpha = 0.65f),
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, if (option.isBuiltIn) StrokeColor else AccentColor.copy(alpha = 0.55f)),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = option.displayName,
                color = TextPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 8.dp)
            )
            Icon(
                imageVector = Icons.Default.Edit,
                contentDescription = null,
                tint = AccentColor,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

class ControlPreviewActivity : ComponentActivity() {
    companion object {
        const val EXTRA_LAYOUT_ID = "extra_layout_id"
        const val EXTRA_LAYOUT_NAME = "extra_layout_name"
        const val EXTRA_LAYOUT_PATH = "extra_layout_path"
    }

    override fun attachBaseContext(newBase: Context?) {
        super.attachBaseContext(if (newBase != null) LocaleUtils.setLocale(newBase) else null)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        LocaleUtils.setLocale(this)
        ensureControlPreferencesReady(this)
        Tools.setFullscreen(this, true)
        Tools.ignoreNotch(LauncherPreferences.PREF_IGNORE_NOTCH, this)
        LauncherPreferences.computeNotchSize(this)
        Tools.updateWindowSize(this)

        val layoutId = intent.getStringExtra(EXTRA_LAYOUT_ID) ?: "preview"
        val layoutName = intent.getStringExtra(EXTRA_LAYOUT_NAME) ?: getString(R.string.controls_preview_title)
        val layoutPath = intent.getStringExtra(EXTRA_LAYOUT_PATH) ?: Tools.CTRLDEF_FILE
        val option = ControlLayoutOption(
            id = layoutId,
            displayName = layoutName,
            filePath = layoutPath,
            isBuiltIn = false
        )

        setContent {
            PojavNativeFullScreenPreview(
                activity = this,
                option = option,
                onClose = { finish() },
                onApply = {
                    applyControlLayout(this, option)
                    finish()
                }
            )
        }
    }

    override fun onPostResume() {
        super.onPostResume()
        Tools.setFullscreen(this, true)
        Tools.ignoreNotch(LauncherPreferences.PREF_IGNORE_NOTCH, this)
    }
}

@Composable
private fun PojavNativeFullScreenPreview(
    activity: Activity,
    option: ControlLayoutOption,
    onClose: () -> Unit,
    onApply: () -> Unit
) {
    val context = LocalContext.current
    var activeControlPath by remember { mutableStateOf(getActiveControlLayoutPath(context)) }
    val isApplied = remember(activeControlPath, option.filePath) {
        isSameControlPath(activeControlPath, option.filePath)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF181818))
    ) {
        // Real 1:1 PojavLauncher ControlLayout View (modifiable = false, interactive buttons & joysticks)
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                ControlLayout(ctx).apply {
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    setPreviewMode(true)
                    setModifiable(false)
                    post {
                        Tools.getDisplayMetrics(activity)
                        if (width > 0 && height > 0) {
                            CallbackBridge.physicalWidth = width
                            CallbackBridge.physicalHeight = height
                        }
                        try {
                            loadLayout(option.filePath)
                            setControlVisible(true)
                            refreshControlButtonPositions()
                        } catch (e: Exception) {
                            Log.e("OnyxControls", "Failed to load layout into ControlLayout preview", e)
                        }
                    }
                }
            },
            update = { view ->
                if (view.width > 0 && view.height > 0) {
                    CallbackBridge.physicalWidth = view.width
                    CallbackBridge.physicalHeight = view.height
                }
            }
        )

        // Compact floating header bar in upper-center so it never collides with edge Pojav buttons
        Surface(
            color = CardBg.copy(alpha = 0.92f),
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, StrokeColor),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 38.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.SportsEsports,
                    contentDescription = null,
                    tint = AccentColor,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = option.displayName,
                    color = TextPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = 220.dp)
                )

                OutlinedButton(
                    onClick = onClose,
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, StrokeColor),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                    modifier = Modifier.height(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = null,
                        tint = TextPrimary,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = stringResource(R.string.controls_preview_close),
                        color = TextPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                if (isApplied) {
                    Surface(
                        color = EmeraldActiveBadge,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .height(32.dp)
                            .clickable { onClose() }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = stringResource(R.string.controls_applied_badge),
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                } else {
                    Button(
                        onClick = {
                            activeControlPath = option.filePath
                            onApply()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = AccentColor),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = stringResource(R.string.controls_apply_button),
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}
