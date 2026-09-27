package com.v2ray.ang.ui.compose

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.v2ray.ang.AppConfig
import com.v2ray.ang.handler.MmkvManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

private val LightColor = lightColorScheme(
    primary = Color(0xFFFED766), // Gold
    onPrimary = Color(0xFF272727), // Charcoal
    primaryContainer = Color(0xFF6B5A2B), // Gold, deep
    onPrimaryContainer = Color(0xFFEFF1F3), // Mist
    secondary = Color(0xFF009FB7), // Teal
    onSecondary = Color(0xFF272727), // Charcoal
    secondaryContainer = Color(0xFFEFE0B4), // Gold, pale
    onSecondaryContainer = Color(0xFF272727), // Charcoal
    tertiary = Color(0xFF009FB7), // Teal
    onTertiary = Color(0xFF272727), // Charcoal
    tertiaryContainer = Color(0xFFBDE7EC), // Teal, pale
    onTertiaryContainer = Color(0xFF272727), // Charcoal
    error = Color(0xFFD32F2F), // Red
    errorContainer = Color(0xFFF3D6D4), // Red
    onError = Color(0xFFEFF1F3), // Mist
    onErrorContainer = Color(0xFF272727), // Charcoal
    background = Color(0xFFFFFFFF), // Mist
    onBackground = Color(0xFF272727), // Charcoal
    surface = Color(0xFFFFFFFF), // Mist
    onSurface = Color(0xFF272727), // Charcoal
    surfaceVariant = Color(0xFFDCDCE6), // Slate
    onSurfaceVariant = Color(0xFF595862), // Slate
    outline = Color(0xFF4A4850), // Slate, deep
    outlineVariant = Color(0xFF8E8C99), // Slate, pale
    inverseSurface = Color(0xFF272727), // Charcoal
    inverseOnSurface = Color(0xFFEFF1F3), // Mist
    inversePrimary = Color(0xFFFED766), // Gold
    scrim = Color(0xFF272727), // Charcoal
    surfaceTint = Color(0xFF009FB7), // Teal
    surfaceContainerLowest = Color(0xFFFFFFFF), // Mist
    surfaceContainerLow = Color(0xFFE8EAEC), // Mist
    surfaceContainer = Color(0xFFE1E3E4), // Mist
    surfaceContainerHigh = Color(0xFFD7D9DB), // Mist
    surfaceContainerHighest = Color(0xFFD8D9DE), // Mist
)

private val DarkColor = darkColorScheme(
    primary = Color(0xFFFED766), // Gold
    onPrimary = Color(0xFF272727), // Charcoal
    primaryContainer = Color(0xFF6B5A2B), // Gold, deep
    onPrimaryContainer = Color(0xFFEFF1F3), // Mist
    secondary = Color(0xFF009FB7), // Teal
    onSecondary = Color(0xFF272727), // Charcoal
    secondaryContainer = Color(0xFF72612E), // Gold
    onSecondaryContainer = Color(0xFFEFF1F3), // Mist
    tertiary = Color(0xFF009FB7), // Teal
    onTertiary = Color(0xFF272727), // Charcoal
    tertiaryContainer = Color(0xFF003840), // Teal, deep
    onTertiaryContainer = Color(0xFFEFF1F3), // Mist
    error = Color(0xFFD32F2F), // Red
    errorContainer = Color(0xFF9E2323), // Red
    onError = Color(0xFFEFF1F3), // Mist
    onErrorContainer = Color(0xFFF3D6D4), // Red
    background = Color(0xFF272727), // Charcoal
    onBackground = Color(0xFFEFF1F3), // Mist
    surface = Color(0xFF272727), // Charcoal
    onSurface = Color(0xFFEFF1F3), // Mist
    surfaceVariant = Color(0xFF3A393F), // Slate
    onSurfaceVariant = Color(0xFFDCDCE6), // Slate, pale
    outline = Color(0xFF8E8C99), // Slate, pale
    outlineVariant = Color(0xFF4A4850), // Slate, deep
    inverseSurface = Color(0xFFEFF1F3), // Mist
    inverseOnSurface = Color(0xFF272727), // Charcoal
    inversePrimary = Color(0xFFFED766), // Gold
    scrim = Color(0xFF272727), // Charcoal
    surfaceTint = Color(0xFF009FB7), // Teal
    surfaceContainerLowest = Color(0xFF1D1D1D), // Charcoal
    surfaceContainerLow = Color(0xFF212121), // Charcoal
    surfaceContainer = Color(0xFF252525), // Charcoal
    surfaceContainerHigh = Color(0xFF2D2D2D), // Charcoal
    surfaceContainerHighest = Color(0xFF3A393F), // Slate
)

// Semantic Colors
val colorPing = Color(0xFF009966) // Teal
val colorPingRed = Color(0xFFFF0099) // Pink Red
val colorConfigType = Color(0xFF009FB7) // Teal
val colorFabActive = Color(0xFF009FB7) // Teal
val colorFabInactiveLight = Color(0xFF4F4D56) // Slate
val colorFabInactiveDark = Color(0xFF696773) // Slate
val dividerColorLight = Color(0xFF4C401F) // Mist
val dividerColorDark = Color(0xFF3A393F) // Slate

// Toast Colors 85%
val toastNormalBgLight = Color(0xD93A393F) // Charcoal, 85%
val toastNormalBgDark = Color(0xD94F4D56) // Slate, 85%
val toastSuccessBg = Color(0xD9009FB7) // Teal, 85%
val toastErrorBg = Color(0xFFD50000) // Red
val toastInfoBg = Color(0xD9009FB7) // Teal, 85%
val toastIconCircleBg = Color(0x33EFF1F3) // Mist, 20%
val toastTextColor = Color.White // Mist

object ThemeManager {
    private val _themeMode = MutableStateFlow(
        MmkvManager.decodeSettingsString(AppConfig.PREF_UI_MODE_NIGHT, "0") ?: "0"
    )
    val themeMode: StateFlow<String> = _themeMode.asStateFlow()

    private val _dynamicColorEnabled = MutableStateFlow(
        MmkvManager.decodeSettingsBool(AppConfig.PREF_DYNAMIC_COLOR, true)
    )
    val dynamicColorEnabled: StateFlow<Boolean> = _dynamicColorEnabled.asStateFlow()

    fun setThemeMode(mode: String) {
        MmkvManager.encodeSettings(AppConfig.PREF_UI_MODE_NIGHT, mode)
        _themeMode.value = mode
    }

    fun setDynamicColorEnabled(enabled: Boolean) {
        MmkvManager.encodeSettings(AppConfig.PREF_DYNAMIC_COLOR, enabled)
        _dynamicColorEnabled.value = enabled
    }

    fun refresh() {
        _themeMode.value =
            MmkvManager.decodeSettingsString(AppConfig.PREF_UI_MODE_NIGHT, "0") ?: "0"
        _dynamicColorEnabled.value =
            MmkvManager.decodeSettingsBool(AppConfig.PREF_DYNAMIC_COLOR, true)
    }
}

@Composable
fun resolveDarkTheme(): Boolean {
    val mode by ThemeManager.themeMode.collectAsState()
    return when (mode) {
        "1" -> false
        "2" -> true
        else -> isSystemInDarkTheme()
    }
}

val LocalDarkTheme = compositionLocalOf { false }

@Composable
fun AppTheme(
    darkTheme: Boolean = resolveDarkTheme(),
    content: @Composable () -> Unit
) {
    val dynamicColor by ThemeManager.dynamicColorEnabled.collectAsState()
    val context = LocalContext.current
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColor
        else -> LightColor
    }
    val snackbarController = rememberAppSnackbarController()

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val activity = view.context as? Activity ?: return@SideEffect
            val window = activity.window
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    CompositionLocalProvider(
        LocalDarkTheme provides darkTheme,
        LocalAppSnackbar provides snackbarController
    ) {
        MaterialTheme(
            colorScheme = colorScheme
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                AppSnackbarBridge(controller = snackbarController)
                content()
                AppSnackbarHost(hostState = snackbarController.hostState)
            }
        }
    }
}
