package com.whoismept.justproxy.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.VpnLock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.whoismept.justproxy.data.ProxyMode
import com.whoismept.justproxy.data.ProxyProfile
import com.whoismept.justproxy.ui.LocalStrings
import com.whoismept.justproxy.ui.ProxyViewModel
import com.whoismept.justproxy.ui.components.ProfileCard
import com.whoismept.justproxy.ui.components.ProxyEditDialog

@Suppress("UNUSED_VALUE")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfilesScreen(
    viewModel: ProxyViewModel,
    showSystemApps: Boolean,
    proxyMode: ProxyMode,
    onStartProxy: (ProxyProfile) -> Unit,
    onStopProxy: () -> Unit,
    onAboutClick: () -> Unit
) {
    val s = LocalStrings.current
    val profiles by viewModel.allProfiles.collectAsState()
    var editTarget by remember { mutableStateOf<ProxyProfile?>(null) }
    var showAddDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title          = { Text("JustProxy", fontWeight = FontWeight.ExtraBold) },
                navigationIcon = { ModeBadge(proxyMode) },
                actions        = {
                    IconButton(onClick = onAboutClick) {
                        Icon(Icons.Default.Info, contentDescription = s.navAbout, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Color.Transparent)
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = s.addProfile)
            }
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            if (profiles.isEmpty()) EmptyState()
            else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(profiles, key = { it.id }) { profile ->
                        ProfileCard(
                            profile  = profile,
                            onToggle = {
                                if (!profile.isActive) onStartProxy(profile) else onStopProxy()
                                viewModel.toggleProfile(profile)
                            },
                            onDelete = {
                                if (profile.isActive) onStopProxy()
                                viewModel.delete(profile)
                            },
                            onEdit = { editTarget = profile }
                        )
                    }
                }
            }
        }

        if (showAddDialog) {
            ProxyEditDialog(
                viewModel      = viewModel,
                showSystemApps = showSystemApps,
                onDismiss      = { showAddDialog = false },
                onConfirm      = { name, host, port, type, isHttp, packages, icon ->
                    viewModel.insert(ProxyProfile(name = name, host = host, port = port, type = type, isHttp = isHttp, targetPackages = packages, icon = icon))
                    showAddDialog = false
                }
            )
        }

        editTarget?.let { profile ->
            ProxyEditDialog(
                viewModel      = viewModel,
                showSystemApps = showSystemApps,
                initialProfile = profile,
                onDismiss      = { editTarget = null },
                onConfirm      = { name, host, port, type, isHttp, packages, icon ->
                    viewModel.update(profile.copy(name = name, host = host, port = port, type = type, isHttp = isHttp, targetPackages = packages, icon = icon))
                    editTarget = null
                }
            )
        }
    }
}

@Composable
private fun ModeBadge(mode: ProxyMode) {
    val isRoot = mode == ProxyMode.ROOT
    Surface(
        shape = MaterialTheme.shapes.small,
        color = if (isRoot) MaterialTheme.colorScheme.primaryContainer
                else        MaterialTheme.colorScheme.tertiaryContainer,
        modifier = Modifier.padding(start = 12.dp, end = 16.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = if (isRoot) Icons.Default.Bolt else Icons.Default.VpnLock,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = if (isRoot) MaterialTheme.colorScheme.onPrimaryContainer
                       else        MaterialTheme.colorScheme.onTertiaryContainer
            )
            Text(
                text  = if (isRoot) "ROOT" else "VPN",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = if (isRoot) MaterialTheme.colorScheme.onPrimaryContainer
                        else        MaterialTheme.colorScheme.onTertiaryContainer
            )
        }
    }
}

@Composable
fun EmptyState() {
    val s = LocalStrings.current
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(Icons.Default.CloudQueue, contentDescription = null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.outline)
        Spacer(modifier = Modifier.height(8.dp))
        Text(s.noProfilesTitle, color = MaterialTheme.colorScheme.outline)
        Text(s.noProfilesSubtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
    }
}
