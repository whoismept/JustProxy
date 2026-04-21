package com.proxydegil.proxyapp

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.net.VpnService
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.viewmodel.compose.viewModel
import com.proxydegil.proxyapp.data.ProxyMode
import com.proxydegil.proxyapp.data.ProxyProfile
import com.proxydegil.proxyapp.data.ProxyType
import com.proxydegil.proxyapp.service.ProxyService
import com.proxydegil.proxyapp.service.ProxyVpnService
import com.proxydegil.proxyapp.ui.AppInfo
import com.proxydegil.proxyapp.ui.ProxyViewModel
import com.proxydegil.proxyapp.ui.ProxyViewModelFactory
import com.proxydegil.proxyapp.utils.RootHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {

    private var pendingProfile: ProxyProfile? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val context = LocalContext.current
            val prefs = remember { context.getSharedPreferences("settings", Context.MODE_PRIVATE) }
            
            // App state management
            var isDarkMode by remember { mutableStateOf(prefs.getBoolean("dark_mode", true)) }
            var accentColor by remember { mutableStateOf(Color(prefs.getInt("accent_color", Color(0xFFBB86FC).toArgb()))) }
            var showSystemApps by remember { mutableStateOf(prefs.getBoolean("show_system_apps", false)) }
            var proxyMode by remember { 
                mutableStateOf(ProxyMode.valueOf(prefs.getString("proxy_mode", ProxyMode.ROOT.name) ?: ProxyMode.ROOT.name)) 
            }
            
            var showSplash by remember { mutableStateOf(prefs.getBoolean("first_run", true)) }

            // Permission request launcher
            val permissionLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestPermission()
            ) { /* Handle result */ }

            // VPN Permission launcher
            val vpnPermissionLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.StartActivityForResult()
            ) { result ->
                if (result.resultCode == RESULT_OK) {
                    pendingProfile?.let { startVpn(context, it) }
                }
                pendingProfile = null
            }

            LaunchedEffect(Unit) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                        permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                }
            }
            
            JustProxyTheme(isDarkMode = isDarkMode, accentColor = accentColor) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    if (showSplash) {
                        SplashScreen(
                            onFinish = { mode ->
                                proxyMode = mode
                                prefs.edit().apply {
                                    putString("proxy_mode", mode.name)
                                    putBoolean("first_run", false)
                                }.apply()
                                showSplash = false
                            }
                        )
                    } else {
                        val repository = (application as ProxyApplication).repository
                        val viewModel: ProxyViewModel = viewModel(factory = ProxyViewModelFactory(repository))
                        MainNavigationScreen(
                            viewModel = viewModel, 
                            isDarkMode = isDarkMode, 
                            accentColor = accentColor,
                            showSystemApps = showSystemApps,
                            proxyMode = proxyMode,
                            onThemeToggle = { 
                                isDarkMode = it
                                prefs.edit().putBoolean("dark_mode", it).apply()
                            },
                            onAccentChange = { 
                                accentColor = it
                                prefs.edit().putInt("accent_color", it.toArgb()).apply()
                            },
                            onShowSystemAppsToggle = {
                                showSystemApps = it
                                prefs.edit().putBoolean("show_system_apps", it).apply()
                            },
                            onProxyModeChange = {
                                proxyMode = it
                                prefs.edit().putString("proxy_mode", it.name).apply()
                            },
                            onStartProxy = { profile ->
                                if (proxyMode == ProxyMode.ROOT) {
                                    ProxyService.startService(context, profile)
                                } else {
                                    val intent = VpnService.prepare(context)
                                    if (intent != null) {
                                        pendingProfile = profile
                                        vpnPermissionLauncher.launch(intent)
                                    } else {
                                        startVpn(context, profile)
                                    }
                                }
                            },
                            onStopProxy = {
                                if (proxyMode == ProxyMode.ROOT) {
                                    ProxyService.stopService(context)
                                } else {
                                    stopVpn(context)
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    private fun startVpn(context: Context, profile: ProxyProfile) {
        val intent = Intent(context, ProxyVpnService::class.java).apply {
            putExtra("host", profile.host)
            putExtra("port", profile.port)
            putExtra(ProxyVpnService.EXTRA_PROXY_TYPE, profile.type.name)
            putExtra("packages", profile.targetPackages?.split(",")?.toTypedArray())
        }
        context.startService(intent)
    }

    private fun stopVpn(context: Context) {
        val intent = Intent(context, ProxyVpnService::class.java).apply {
            action = ProxyVpnService.ACTION_STOP
        }
        context.startService(intent)
    }
}

@Composable
fun SplashScreen(onFinish: (ProxyMode) -> Unit) {
    var step by remember { mutableStateOf(1) }
    var rootAvailable by remember { mutableStateOf<Boolean?>(null) }

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        AnimatedContent(
            targetState = step,
            label = "splash_transition",
            transitionSpec = { fadeIn() togetherWith fadeOut() }
        ) { currentStep ->
            when (currentStep) {
                1 -> {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(24.dp)
                    ) {
                        Icon(Icons.Default.Shield, null, modifier = Modifier.size(120.dp), tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.height(24.dp))
                        Text("Welcome to JustProxy", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                        Text("Advanced network debugging for everyone", style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
                        Spacer(modifier = Modifier.height(48.dp))
                        Button(
                            onClick = { step = 2 },
                            modifier = Modifier.fillMaxWidth().height(56.dp),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Text("Get Started", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                2 -> {
                    LaunchedEffect(Unit) {
                        rootAvailable = withContext(Dispatchers.IO) { RootHelper.isRootAvailable() }
                    }

                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("System Check", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        CheckItem(
                            title = "Root Access",
                            status = when(rootAvailable) {
                                true -> "Found"
                                false -> "Not Found"
                                null -> "Checking..."
                            },
                            icon = if (rootAvailable == true) Icons.Default.CheckCircle else Icons.Default.Cancel,
                            color = if (rootAvailable == true) Color.Green else if (rootAvailable == false) Color.Red else Color.Gray
                        )

                        Spacer(modifier = Modifier.height(32.dp))
                        Text("Select Operation Mode", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        ModeCard(
                            title = "Root Mode",
                            description = "Transparent proxying using iptables. (Requires Root)",
                            icon = Icons.Default.Bolt,
                            enabled = rootAvailable == true,
                            onClick = { onFinish(ProxyMode.ROOT) }
                        )
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        ModeCard(
                            title = "VPN Mode",
                            description = "Capture traffic using VpnService. (No Root Required)",
                            icon = Icons.Default.VpnLock,
                            enabled = true,
                            onClick = { onFinish(ProxyMode.VPN) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun CheckItem(title: String, status: String, icon: ImageVector, color: Color) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(8.dp)
    ) {
        Icon(icon, null, tint = color)
        Spacer(modifier = Modifier.width(12.dp))
        Text(title, modifier = Modifier.weight(1f))
        Text(status, fontWeight = FontWeight.Bold, color = color)
    }
}

@Composable
fun ModeCard(title: String, description: String, icon: ImageVector, enabled: Boolean = true, onClick: () -> Unit) {
    Surface(
        onClick = { if(enabled) onClick() },
        modifier = Modifier.fillMaxWidth().alpha(if(enabled) 1f else 0.5f),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
    ) {
        Row(modifier = Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, null, modifier = Modifier.size(28.dp), tint = MaterialTheme.colorScheme.primary)
            }
            Spacer(modifier = Modifier.width(20.dp))
            Column {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/**
 * Main Theme configuration for JustProxy.
 */
@Composable
fun JustProxyTheme(isDarkMode: Boolean, accentColor: Color, content: @Composable () -> Unit) {
    val colorScheme = if (isDarkMode) {
        darkColorScheme(
            primary = accentColor,
            secondary = Color(0xFF03DAC6),
            background = Color(0xFF121212),
            surface = Color(0xFF1E1E1E),
            onPrimary = Color.Black,
            onSurface = Color.White
        )
    } else {
        lightColorScheme(
            primary = accentColor,
            secondary = Color(0xFF03DAC6),
            background = Color(0xFFF5F5F5),
            surface = Color.White,
            onPrimary = Color.White,
            onSurface = Color.Black
        )
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}

sealed class Screen(val route: String, val title: String, val icon: ImageVector) {
    object Profiles : Screen("profiles", "Profiles", Icons.Default.List)
    object Settings : Screen("settings", "Settings", Icons.Default.Settings)
    object Info : Screen("info", "About", Icons.Default.Info)
}

@Composable
fun MainNavigationScreen(
    viewModel: ProxyViewModel, 
    isDarkMode: Boolean, 
    accentColor: Color,
    showSystemApps: Boolean,
    proxyMode: ProxyMode,
    onThemeToggle: (Boolean) -> Unit,
    onAccentChange: (Color) -> Unit,
    onShowSystemAppsToggle: (Boolean) -> Unit,
    onProxyModeChange: (ProxyMode) -> Unit,
    onStartProxy: (ProxyProfile) -> Unit,
    onStopProxy: () -> Unit
) {
    var currentScreen by remember { mutableStateOf<Screen>(Screen.Profiles) }
    val items = listOf(Screen.Profiles, Screen.Settings, Screen.Info)

    Scaffold(
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                items.forEach { screen ->
                    NavigationBarItem(
                        icon = { Icon(screen.icon, contentDescription = screen.title) },
                        label = { Text(screen.title) },
                        selected = currentScreen == screen,
                        onClick = { currentScreen = screen }
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding)) {
            when (currentScreen) {
                Screen.Profiles -> ProxyAppScreen(viewModel, isDarkMode, showSystemApps, onStartProxy, onStopProxy)
                Screen.Settings -> SettingsScreen(viewModel, isDarkMode, accentColor, showSystemApps, proxyMode, onThemeToggle, onAccentChange, onShowSystemAppsToggle, onProxyModeChange)
                Screen.Info -> InfoScreen()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProxyAppScreen(
    viewModel: ProxyViewModel, 
    isDarkMode: Boolean,
    showSystemApps: Boolean,
    onStartProxy: (ProxyProfile) -> Unit,
    onStopProxy: () -> Unit
) {
    val profiles by viewModel.allProfiles.collectAsState()
    var showEditDialog by remember { mutableStateOf<ProxyProfile?>(null) }
    var isAddingNew by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("JustProxy", fontWeight = FontWeight.ExtraBold) },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Color.Transparent)
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { isAddingNew = true }) {
                Icon(Icons.Default.Add, contentDescription = "Add")
            }
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            if (profiles.isEmpty()) {
                EmptyState()
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(profiles) { profile ->
                        ProfileCard(
                            profile = profile,
                            isDarkMode = isDarkMode,
                            onToggle = { 
                                if (!profile.isActive) onStartProxy(profile)
                                else onStopProxy()
                                viewModel.toggleProfile(profile)
                            },
                            onDelete = { 
                                if (profile.isActive) onStopProxy()
                                viewModel.delete(profile) 
                            },
                            onEdit = { showEditDialog = profile }
                        )
                    }
                }
            }
        }

        if (isAddingNew) {
            ProxyEditDialog(
                viewModel = viewModel,
                showSystemApps = showSystemApps,
                onDismiss = { isAddingNew = false },
                onConfirm = { name, host, port, type, isHttp, packages ->
                    viewModel.insert(ProxyProfile(name = name, host = host, port = port, type = type, isHttp = isHttp, targetPackages = packages))
                    isAddingNew = false
                }
            )
        }

        showEditDialog?.let { profile ->
            ProxyEditDialog(
                viewModel = viewModel,
                showSystemApps = showSystemApps,
                initialProfile = profile,
                onDismiss = { showEditDialog = null },
                onConfirm = { name, host, port, type, isHttp, packages ->
                    viewModel.update(profile.copy(name = name, host = host, port = port, type = type, isHttp = isHttp, targetPackages = packages))
                    showEditDialog = null
                }
            )
        }
    }
}

@Composable
fun InfoScreen() {
    val context = LocalContext.current
    var isRooted by remember { mutableStateOf<Boolean?>(null) }

    LaunchedEffect(Unit) {
        isRooted = withContext(Dispatchers.IO) {
            RootHelper.isRootAvailable()
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Default.Shield,
            contentDescription = null,
            modifier = Modifier.size(100.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Text(
            "JustProxy",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold
        )
        Text("v1.0.0-beta", style = MaterialTheme.typography.bodyMedium)
        
        Spacer(modifier = Modifier.height(32.dp))
        
        InfoCard(
            title = "Root Access",
            value = when(isRooted) {
                true -> "Granted"
                false -> "Denied"
                null -> "Checking..."
            },
            icon = Icons.Default.VerifiedUser,
            color = when(isRooted) {
                true -> Color(0xFF4CAF50)
                false -> Color(0xFFF44336)
                null -> MaterialTheme.colorScheme.outline
            }
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Clickable Developer Section
        Surface(
            modifier = Modifier.fillMaxWidth().clickable {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/mertdegil"))
                context.startActivity(intent)
            },
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Code, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text("Developer", style = MaterialTheme.typography.labelLarge)
                    Text("mert / ProxyDegil", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("Click to visit GitHub", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                }
            }
        }

        Spacer(modifier = Modifier.weight(1f))
        
        Text(
            "This application is designed for security research and network debugging. Use responsibly.",
            style = MaterialTheme.typography.labelSmall,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.outline
        )
    }
}

@Composable
fun SettingsScreen(
    viewModel: ProxyViewModel, 
    isDarkMode: Boolean, 
    accentColor: Color,
    showSystemApps: Boolean,
    proxyMode: ProxyMode,
    onThemeToggle: (Boolean) -> Unit,
    onAccentChange: (Color) -> Unit,
    onShowSystemAppsToggle: (Boolean) -> Unit,
    onProxyModeChange: (ProxyMode) -> Unit
) {
    val scrollState = rememberScrollState()
    val colors = listOf(
        Color(0xFFBB86FC), // Purple
        Color(0xFF2196F3), // Blue
        Color(0xFF4CAF50), // Green
        Color(0xFFFF9800), // Orange
        Color(0xFFF44336), // Red
        Color(0xFFE91E63), // Pink
        Color(0xFF00BCD4), // Cyan
        Color(0xFF9C27B0)  // Deep Purple
    )

    Column(modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(scrollState)) {
        Text("General Settings", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(16.dp))
        
        // Theme Switcher
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Dark Mode", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                Text("Toggle between light and dark theme", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
            }
            Switch(checked = isDarkMode, onCheckedChange = onThemeToggle)
        }

        Spacer(modifier = Modifier.height(16.dp))
        Text("Accent Color", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
        Spacer(modifier = Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            colors.forEach { color ->
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(color)
                        .clickable { onAccentChange(color) }
                        .border(
                            width = if (accentColor == color) 3.dp else 0.dp,
                            color = if (accentColor == color) Color.White else Color.Transparent,
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (accentColor == color) {
                        Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(24.dp))
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
        
        Text("Operation Mode", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
        Text("Current: ${if(proxyMode == ProxyMode.ROOT) "Root Based" else "VPN Based"}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
            FilterChip(
                selected = proxyMode == ProxyMode.ROOT,
                onClick = { onProxyModeChange(ProxyMode.ROOT) },
                label = { Text("Root") },
                leadingIcon = { if(proxyMode == ProxyMode.ROOT) Icon(Icons.Default.Bolt, null) }
            )
            Spacer(modifier = Modifier.width(8.dp))
            FilterChip(
                selected = proxyMode == ProxyMode.VPN,
                onClick = { onProxyModeChange(ProxyMode.VPN) },
                label = { Text("VPN") },
                leadingIcon = { if(proxyMode == ProxyMode.VPN) Icon(Icons.Default.VpnLock, null) }
            )
        }

        Spacer(modifier = Modifier.height(24.dp))
        SettingsToggle(title = "Show System Apps", description = "Include system applications in app picker", initialValue = showSystemApps, onCheckedChange = onShowSystemAppsToggle)
        SettingsToggle(title = "Start on Boot", description = "Automatically start last active proxy on device boot", initialValue = false)
        SettingsToggle(title = "Persistent Notification", description = "Keep service running in background more reliably", initialValue = true)
        
        Spacer(modifier = Modifier.height(24.dp))
        Text("Advanced Features", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        SettingsToggle(title = "Auto DNS", description = "Redirect DNS queries to proxy (Experimental)", initialValue = false)
        SettingsToggle(title = "UDP Forwarding", description = "Enable UDP traffic redirection (Requires SOCKS5)", initialValue = false)

        Spacer(modifier = Modifier.height(32.dp))
        
        // Experimental Section
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.1f)),
            border = CardDefaults.outlinedCardBorder()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Science, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Experimental", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.error)
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "Use these tools if you experience network issues after stopping a proxy profile.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(16.dp))
                
                Button(
                    onClick = { 
                        viewModel.resetNetwork()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Flush Proxy & Reset Network")
                }
            }
        }
    }
}

@Composable
fun InfoCard(title: String, value: String, icon: ImageVector, color: Color) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = color)
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(title, style = MaterialTheme.typography.labelLarge)
                Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = color)
            }
        }
    }
}

@Composable
fun SettingsToggle(title: String, description: String, initialValue: Boolean, onCheckedChange: (Boolean) -> Unit = {}) {
    var checked by remember { mutableStateOf(initialValue) }
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
        }
        Switch(checked = checked, onCheckedChange = { 
            checked = it
            onCheckedChange(it)
        })
    }
}

@Composable
fun ProfileCard(profile: ProxyProfile, isDarkMode: Boolean, onToggle: () -> Unit, onDelete: () -> Unit, onEdit: () -> Unit) {
    var showDeleteConfirm by remember { mutableStateOf(false) }

    val activeColor = if (profile.isActive) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
    Card(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable { onEdit() },
        colors = CardDefaults.cardColors(containerColor = activeColor)
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(40.dp).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f), RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(profile.type.name.take(1), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(profile.name, fontWeight = FontWeight.Bold)
                Text("${profile.host}:${profile.port}", style = MaterialTheme.typography.bodySmall)
                if (!profile.targetPackages.isNullOrBlank()) {
                    val count = profile.targetPackages.split(",").size
                    Text("$count apps selected", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                }
                Text(
                    text = if (profile.type != ProxyType.HTTP) "Protocol: ${profile.type}" else if (profile.isHttp) "Mode: System" else "Mode: Transparent",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
            Switch(checked = profile.isActive, onCheckedChange = { onToggle() })
            Spacer(modifier = Modifier.width(12.dp))
            
            // CLEAN DELETE BUTTON: No background, color based on theme (White for Dark, Black for Light)
            IconButton(
                onClick = { showDeleteConfirm = true },
                modifier = Modifier.size(40.dp)
            ) { 
                Icon(
                    imageVector = Icons.Default.Delete, 
                    contentDescription = "Delete Profile", 
                    tint = if (isDarkMode) Color.White else Color.Black,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete Profile?") },
            text = { Text("Are you sure you want to delete '${profile.name}'? This action cannot be undone.") },
            confirmButton = {
                Button(
                    onClick = { 
                        onDelete()
                        showDeleteConfirm = false 
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProxyEditDialog(
    viewModel: ProxyViewModel,
    showSystemApps: Boolean,
    initialProfile: ProxyProfile? = null, 
    onDismiss: () -> Unit, 
    onConfirm: (String, String, Int, ProxyType, Boolean, String?) -> Unit
) {
    var name by remember { mutableStateOf(initialProfile?.name ?: "") }
    var host by remember { mutableStateOf(initialProfile?.host ?: "") }
    var port by remember { mutableStateOf(initialProfile?.port?.toString() ?: "") }
    var type by remember { mutableStateOf(initialProfile?.type ?: ProxyType.HTTP) }
    var isHttp by remember { mutableStateOf(initialProfile?.isHttp ?: true) }
    var selectedPackages by remember { 
        mutableStateOf(initialProfile?.targetPackages?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList()) 
    }
    
    var showAppPicker by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initialProfile == null) "Add Profile" else "Edit Profile") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = host, onValueChange = { host = it }, label = { Text("Host") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = port, onValueChange = { port = it }, label = { Text("Port") }, modifier = Modifier.fillMaxWidth())
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text("App Selection", style = MaterialTheme.typography.labelLarge)
                Surface(
                    onClick = { showAppPicker = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    border = CardDefaults.outlinedCardBorder()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Apps, contentDescription = null)
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = if (selectedPackages.isEmpty()) "Global (All Apps)" else "${selectedPackages.size} apps selected",
                            modifier = Modifier.weight(1f)
                        )
                        Icon(Icons.Default.ChevronRight, contentDescription = null)
                    }
                }
                
                Text("Proxy Type", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ProxyType.values().forEach { t ->
                        FilterChip(
                            selected = type == t,
                            onClick = { 
                                type = t
                                if (t != ProxyType.HTTP) isHttp = false 
                            },
                            label = { Text(t.name) }
                        )
                    }
                }
                
                if (type == ProxyType.HTTP) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = isHttp, onCheckedChange = { isHttp = it })
                        Text("Only Web (System Proxy)", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { 
                val pkgString = if (selectedPackages.isEmpty()) null else selectedPackages.joinToString(",")
                onConfirm(name, host, port.toIntOrNull() ?: 80, type, isHttp, pkgString) 
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )

    if (showAppPicker) {
        AppPickerSelectionDialog(
            viewModel = viewModel,
            showSystemApps = showSystemApps,
            selectedPackages = selectedPackages,
            onDismiss = { showAppPicker = false },
            onSelected = { selectedPackages = it }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppPickerSelectionDialog(
    viewModel: ProxyViewModel,
    showSystemApps: Boolean,
    selectedPackages: List<String>,
    onDismiss: () -> Unit,
    onSelected: (List<String>) -> Unit
) {
    val context = LocalContext.current
    val apps by viewModel.installedApps.collectAsState()
    var currentSelected by remember { mutableStateOf(selectedPackages) }
    var searchQuery by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        viewModel.loadInstalledApps(context, showSystemApps)
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier.fillMaxWidth().fillMaxHeight(0.85f),
            shape = RoundedCornerShape(24.dp)
        ) {
            Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                Text("Select Apps", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(12.dp))
                
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search apps...") },
                    leadingIcon = { Icon(Icons.Default.Search, null) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true
                )
                
                Spacer(modifier = Modifier.height(12.dp))
                
                val filteredApps = apps.filter { 
                    it.name.contains(searchQuery, ignoreCase = true) || 
                    it.packageName.contains(searchQuery, ignoreCase = true) 
                }

                LazyColumn(modifier = Modifier.weight(1f)) {
                    items(filteredApps) { app ->
                        AppListItem(
                            app = app,
                            isSelected = currentSelected.contains(app.packageName),
                            onToggle = {
                                currentSelected = if (currentSelected.contains(app.packageName)) {
                                    currentSelected - app.packageName
                                } else {
                                    currentSelected + app.packageName
                                }
                            }
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = { currentSelected = emptyList() }) { Text("Clear All") }
                    Spacer(modifier = Modifier.weight(1f))
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    Button(onClick = { 
                        onSelected(currentSelected)
                        onDismiss()
                    }) { Text("Done") }
                }
            }
        }
    }
}

@Composable
fun AppListItem(app: AppInfo, isSelected: Boolean, onToggle: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable { onToggle() }.padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (app.icon != null) {
            Image(
                bitmap = app.icon.toBitmap().asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.size(40.dp)
            )
        } else {
            Box(modifier = Modifier.size(40.dp).background(Color.Gray.copy(alpha = 0.2f), RoundedCornerShape(8.dp)))
        }
        
        Spacer(modifier = Modifier.width(16.dp))
        
        Column(modifier = Modifier.weight(1f)) {
            Text(app.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(app.packageName, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline, maxLines = 1)
        }
        
        Checkbox(checked = isSelected, onCheckedChange = { onToggle() })
    }
}

@Composable
fun EmptyState() {
    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.Default.CloudQueue, contentDescription = null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.outline)
        Text("No Profiles Yet", color = MaterialTheme.colorScheme.outline)
    }
}
