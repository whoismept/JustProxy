package com.whoismept.justproxy

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.Surface
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.lifecycle.viewmodel.compose.viewModel
import com.whoismept.justproxy.data.ProxyMode
import com.whoismept.justproxy.data.ProxyProfile
import com.whoismept.justproxy.service.ProxyService
import com.whoismept.justproxy.service.ProxyVpnService
import androidx.compose.runtime.CompositionLocalProvider
import com.whoismept.justproxy.ui.*

class MainActivity : ComponentActivity() {

    private var pendingProfile: ProxyProfile? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val context = LocalContext.current
            val prefs   = remember { context.getSharedPreferences("settings", MODE_PRIVATE) }

            var isDarkMode              by remember { mutableStateOf(prefs.getBoolean("dark_mode", true)) }
            var accentColor             by remember { mutableStateOf(Color(prefs.getInt("accent_color", Color(0xFFBB86FC).toArgb()))) }
            var showSystemApps          by remember { mutableStateOf(prefs.getBoolean("show_system_apps", false)) }
            var persistentNotification  by remember { mutableStateOf(prefs.getBoolean("persistent_notification", true)) }
            var showLogTab              by remember { mutableStateOf(prefs.getBoolean("show_log_tab", false)) }
            var proxyMode               by remember {
                mutableStateOf(ProxyMode.valueOf(prefs.getString("proxy_mode", ProxyMode.ROOT.name) ?: ProxyMode.ROOT.name))
            }
            var showSplash by remember { mutableStateOf(prefs.getBoolean("first_run", true)) }
            var language   by remember { mutableStateOf(prefs.getString("language", "en") ?: "en") }

            val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}

            val vpnPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                if (result.resultCode == RESULT_OK) pendingProfile?.let { startVpn(context, it) }
                pendingProfile = null
            }

            LaunchedEffect(Unit) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                    ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                ) {
                    permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }

            JustProxyTheme(isDarkMode = isDarkMode, accentColor = accentColor) {
                CompositionLocalProvider(LocalStrings provides stringsForLanguage(language)) {
                Surface(color = MaterialTheme.colorScheme.background) {
                    if (showSplash) {
                        SplashScreen { mode ->
                            proxyMode = mode
                            prefs.edit { putString("proxy_mode", mode.name); putBoolean("first_run", false) }
                            showSplash = false
                        }
                    } else {
                        val repository = (application as ProxyApplication).repository
                        val vm: ProxyViewModel = viewModel(factory = ProxyViewModelFactory(repository))
                        MainNavigationScreen(
                            viewModel                      = vm,
                            isDarkMode                     = isDarkMode,
                            accentColor                    = accentColor,
                            showSystemApps                 = showSystemApps,
                            proxyMode                      = proxyMode,
                            persistentNotification         = persistentNotification,
                            onThemeToggle                  = { isDarkMode = it;  prefs.edit { putBoolean("dark_mode", it) } },
                            onAccentChange                 = { accentColor = it; prefs.edit { putInt("accent_color", it.toArgb()) } },
                            onShowSystemAppsToggle         = { showSystemApps = it; prefs.edit { putBoolean("show_system_apps", it) } },
                            onProxyModeChange              = { proxyMode = it;  prefs.edit { putString("proxy_mode", it.name) } },
                            onPersistentNotificationToggle = { persistentNotification = it; prefs.edit { putBoolean("persistent_notification", it) } },
                            showLogTab                     = showLogTab,
                            language                       = language,
                            onShowLogTabToggle             = { showLogTab = it; prefs.edit { putBoolean("show_log_tab", it) } },
                            onLanguageChange               = { language = it; prefs.edit { putString("language", it) } },
                            onStartProxy                   = { profile ->
                                if (proxyMode == ProxyMode.ROOT) {
                                    ProxyService.startService(context, profile, persistentNotification)
                                } else {
                                    val vpnIntent = VpnService.prepare(context)
                                    if (vpnIntent != null) {
                                        pendingProfile = profile
                                        vpnPermissionLauncher.launch(vpnIntent)
                                    } else {
                                        startVpn(context, profile)
                                    }
                                }
                            },
                            onStopProxy = {
                                if (proxyMode == ProxyMode.ROOT) ProxyService.stopService(context)
                                else stopVpn(context)
                            }
                        )
                    }
                }
                } // CompositionLocalProvider
            }
        }
    }

    private fun startVpn(context: Context, profile: ProxyProfile) {
        context.startService(Intent(context, ProxyVpnService::class.java).apply {
            putExtra("host",                     profile.host)
            putExtra("port",                     profile.port)
            putExtra(ProxyVpnService.EXTRA_PROXY_TYPE, profile.type.name)
            putExtra("packages",                 profile.targetPackages?.split(",")?.toTypedArray())
        })
    }

    private fun stopVpn(context: Context) {
        context.startService(Intent(context, ProxyVpnService::class.java).apply { action = ProxyVpnService.ACTION_STOP })
    }
}
