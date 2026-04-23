package com.whoismept.justproxy.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.whoismept.justproxy.data.ProxyMode
import com.whoismept.justproxy.data.ProxyProfile
import com.whoismept.justproxy.ui.screens.InfoScreen
import com.whoismept.justproxy.ui.screens.LogScreen
import com.whoismept.justproxy.ui.screens.ProfilesScreen
import com.whoismept.justproxy.ui.screens.SettingsScreen

sealed class Screen(val route: String, val title: String, val icon: ImageVector) {
    object Profiles : Screen("profiles", "Profiles", Icons.AutoMirrored.Filled.List)
    object Log      : Screen("log",      "Log",      Icons.Default.History)
    object Settings : Screen("settings", "Settings", Icons.Default.Settings)
    object Info     : Screen("info",     "About",    Icons.Default.Info)
}

@Composable
fun MainNavigationScreen(
    viewModel: ProxyViewModel,
    isDarkMode: Boolean,
    accentColor: Color,
    showSystemApps: Boolean,
    proxyMode: ProxyMode,
    persistentNotification: Boolean,
    showLogTab: Boolean,
    onThemeToggle: (Boolean) -> Unit,
    onAccentChange: (Color) -> Unit,
    onShowSystemAppsToggle: (Boolean) -> Unit,
    onProxyModeChange: (ProxyMode) -> Unit,
    onPersistentNotificationToggle: (Boolean) -> Unit,
    onShowLogTabToggle: (Boolean) -> Unit,
    onStartProxy: (ProxyProfile) -> Unit,
    onStopProxy: () -> Unit
) {
    var current by remember { mutableStateOf<Screen>(Screen.Profiles) }
    val items = remember(showLogTab) {
        if (showLogTab) listOf(Screen.Profiles, Screen.Log, Screen.Settings)
        else            listOf(Screen.Profiles, Screen.Settings)
    }
    Scaffold(
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                items.forEach { screen ->
                    NavigationBarItem(
                        icon     = { Icon(screen.icon, contentDescription = screen.title) },
                        label    = { Text(screen.title) },
                        selected = current == screen,
                        onClick  = { current = screen }
                    )
                }
            }
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding)) {
            when (current) {
                Screen.Profiles -> ProfilesScreen(viewModel, showSystemApps, proxyMode, onStartProxy, onStopProxy, onAboutClick = { current = Screen.Info })
                Screen.Log      -> LogScreen(viewModel)
                Screen.Settings -> SettingsScreen(
                    viewModel                      = viewModel,
                    isDarkMode                     = isDarkMode,
                    accentColor                    = accentColor,
                    showSystemApps                 = showSystemApps,
                    proxyMode                      = proxyMode,
                    persistentNotification         = persistentNotification,
                    onThemeToggle                  = onThemeToggle,
                    onAccentChange                 = onAccentChange,
                    onShowSystemAppsToggle         = onShowSystemAppsToggle,
                    onProxyModeChange              = onProxyModeChange,
                    onPersistentNotificationToggle = onPersistentNotificationToggle,
                    showLogTab                     = showLogTab,
                    onShowLogTabToggle             = onShowLogTabToggle
                )
                Screen.Info     -> InfoScreen()
            }
        }
    }
}

@Composable
fun JustProxyTheme(isDarkMode: Boolean, accentColor: Color, content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = buildAccentColorScheme(accentColor, isDarkMode),
        content     = content
    )
}

private fun buildAccentColorScheme(accent: Color, dark: Boolean): ColorScheme {
    // Perceived luminance — determines if text on accent should be black or white
    val luminance = 0.2126f * accent.red + 0.7152f * accent.green + 0.0722f * accent.blue
    val onAccent  = if (luminance > 0.35f) Color(0xFF1C1B1F) else Color.White

    return if (dark) {
        // primaryContainer = very dark tint of accent (accent at ~25% brightness)
        val container = Color(accent.red * 0.28f, accent.green * 0.28f, accent.blue * 0.28f)
        // onPrimaryContainer = bright tint of accent
        val onContainer = Color(
            (accent.red   * 0.55f + 0.55f).coerceIn(0f, 1f),
            (accent.green * 0.55f + 0.55f).coerceIn(0f, 1f),
            (accent.blue  * 0.55f + 0.55f).coerceIn(0f, 1f)
        )
        darkColorScheme(
            primary              = accent,
            onPrimary            = onAccent,
            primaryContainer     = container,
            onPrimaryContainer   = onContainer,
            secondary            = onContainer,
            onSecondary          = Color(0xFF1C1B1F),
            secondaryContainer   = Color(0xFF2A2A2A),
            onSecondaryContainer = Color(0xFFCCC2DC),
            background           = Color(0xFF121212),
            surface              = Color(0xFF1E1E1E),
            surfaceVariant       = Color(0xFF2A2A2A),
            onSurface            = Color(0xFFE6E1E5),
            onSurfaceVariant     = Color(0xFFCAC4D0),
            outline              = Color(0xFF938F99),
            outlineVariant       = Color(0xFF444444)
        )
    } else {
        // primaryContainer = very light tint of accent
        val container = Color(
            (1f - (1f - accent.red)   * 0.25f).coerceIn(0f, 1f),
            (1f - (1f - accent.green) * 0.25f).coerceIn(0f, 1f),
            (1f - (1f - accent.blue)  * 0.25f).coerceIn(0f, 1f)
        )
        // onPrimaryContainer = darkened accent
        val onContainer = Color(
            (accent.red   * 0.45f).coerceIn(0f, 1f),
            (accent.green * 0.45f).coerceIn(0f, 1f),
            (accent.blue  * 0.45f).coerceIn(0f, 1f)
        )
        lightColorScheme(
            primary              = accent,
            onPrimary            = onAccent,
            primaryContainer     = container,
            onPrimaryContainer   = onContainer,
            secondary            = onContainer,
            onSecondary          = Color.White,
            secondaryContainer   = Color(0xFFEEDDFF),
            onSecondaryContainer = Color(0xFF21005E),
            background           = Color(0xFFF5F5F5),
            surface              = Color.White,
            surfaceVariant       = Color(0xFFEFEFF4),
            onSurface            = Color(0xFF1C1B1F),
            onSurfaceVariant     = Color(0xFF49454F),
            outline              = Color(0xFF79747E),
            outlineVariant       = Color(0xFFCAC4D0)
        )
    }
}
