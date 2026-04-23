package com.whoismept.justproxy.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.whoismept.justproxy.data.ProxyMode
import com.whoismept.justproxy.ui.LocalStrings
import com.whoismept.justproxy.ui.ProxyViewModel
import kotlinx.coroutines.launch

private val ACCENT_COLORS = listOf(
    Color(0xFFBB86FC),
    Color(0xFF2196F3),
    Color(0xFF4CAF50),
    Color(0xFFFF9800),
    Color(0xFFF44336),
    Color(0xFFE91E63),
    Color(0xFF00BCD4),
    Color(0xFF9C27B0)
)

private val LANGUAGES = listOf("en" to "English", "tr" to "Türkçe")

@Composable
fun SettingsScreen(
    viewModel: ProxyViewModel,
    isDarkMode: Boolean,
    accentColor: Color,
    showSystemApps: Boolean,
    proxyMode: ProxyMode,
    persistentNotification: Boolean,
    showLogTab: Boolean,
    language: String,
    onThemeToggle: (Boolean) -> Unit,
    onAccentChange: (Color) -> Unit,
    onShowSystemAppsToggle: (Boolean) -> Unit,
    onProxyModeChange: (ProxyMode) -> Unit,
    onPersistentNotificationToggle: (Boolean) -> Unit,
    onShowLogTabToggle: (Boolean) -> Unit,
    onLanguageChange: (String) -> Unit
) {
    val s = LocalStrings.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // ── Appearance ───────────────────────────────────────────────────────
        SettingsCard(title = s.settingsAppearance, icon = Icons.Default.Palette) {
            SettingsToggle(
                title       = s.settingsDarkMode,
                description = s.settingsDarkModeDesc,
                checked     = isDarkMode,
                onChecked   = onThemeToggle
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                s.settingsAccentColor,
                style      = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier             = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                ACCENT_COLORS.forEach { color ->
                    val selected = accentColor == color
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(color)
                            .clickable { onAccentChange(color) }
                            .then(
                                if (selected) Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f), CircleShape)
                                else Modifier
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        if (selected) {
                            Icon(
                                imageVector        = Icons.Default.Check,
                                contentDescription = null,
                                tint               = Color.White,
                                modifier           = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                s.settingsLanguage,
                style      = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
            )
            Text(
                s.settingsLanguageDesc,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LANGUAGES.forEach { (code, name) ->
                    FilterChip(
                        selected    = language == code,
                        onClick     = { onLanguageChange(code) },
                        label       = { Text(name) },
                        leadingIcon = {
                            if (language == code) Icon(Icons.Default.Check, null, Modifier.size(18.dp))
                        }
                    )
                }
            }
        }

        // ── Advanced ─────────────────────────────────────────────────────────
        SettingsCard(title = s.settingsAdvanced, icon = Icons.Default.Notifications) {
            SettingsToggle(
                title       = s.settingsPersistentNotif,
                description = s.settingsPersistentNotifDesc,
                checked     = persistentNotification,
                onChecked   = onPersistentNotificationToggle
            )
            SettingsToggle(
                title       = s.settingsLogTab,
                description = s.settingsLogTabDesc,
                checked     = showLogTab,
                onChecked   = onShowLogTabToggle
            )
            SettingsToggle(
                title       = s.settingsSystemApps,
                description = s.settingsSystemAppsDesc,
                checked     = showSystemApps,
                onChecked   = onShowSystemAppsToggle
            )
        }

        // ── Proxy ────────────────────────────────────────────────────────────
        SettingsCard(title = s.settingsProxy, icon = Icons.Default.Bolt) {
            Text(
                s.settingsInterceptionMode,
                style      = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
            )
            Text(
                if (proxyMode == ProxyMode.ROOT) s.settingsRootModeDesc else s.settingsVpnModeDesc,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline
            )
            Row(
                modifier              = Modifier.padding(top = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected    = proxyMode == ProxyMode.ROOT,
                    onClick     = { onProxyModeChange(ProxyMode.ROOT) },
                    label       = { Text("Root") },
                    leadingIcon = { if (proxyMode == ProxyMode.ROOT) Icon(Icons.Default.Bolt, null, Modifier.size(18.dp)) }
                )
                FilterChip(
                    selected    = proxyMode == ProxyMode.VPN,
                    onClick     = { onProxyModeChange(ProxyMode.VPN) },
                    label       = { Text("VPN") },
                    leadingIcon = { if (proxyMode == ProxyMode.VPN) Icon(Icons.Default.VpnLock, null, Modifier.size(18.dp)) }
                )
            }
        }

        // ── Experimental ─────────────────────────────────────────────────────
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape    = RoundedCornerShape(16.dp),
            colors   = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.15f)
            ),
            border = CardDefaults.outlinedCardBorder()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Science, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        s.settingsExperimental,
                        style      = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color      = MaterialTheme.colorScheme.error
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    s.settingsExperimentalDesc,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))
                val refreshRotation = remember { Animatable(0f) }
                val scope = rememberCoroutineScope()
                OutlinedButton(
                    onClick  = {
                        viewModel.resetNetwork()
                        scope.launch {
                            refreshRotation.animateTo(
                                targetValue   = 360f,
                                animationSpec = tween(700, easing = FastOutSlowInEasing)
                            )
                            refreshRotation.snapTo(0f)
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors   = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    border   = ButtonDefaults.outlinedButtonBorder(enabled = true).copy(
                        brush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.error.copy(alpha = 0.5f))
                    )
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp).rotate(refreshRotation.value))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(s.settingsFlushButton)
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
    }
}

@Composable
fun SettingsToggle(title: String, description: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Row(
        modifier          = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
        }
        Spacer(modifier = Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onChecked)
    }
}

@Composable
private fun SettingsCard(
    title: String,
    icon: ImageVector,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape    = RoundedCornerShape(16.dp),
        colors   = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector        = icon,
                    contentDescription = null,
                    tint               = MaterialTheme.colorScheme.primary,
                    modifier           = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    title,
                    style      = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color      = MaterialTheme.colorScheme.primary
                )
            }
            Spacer(modifier = Modifier.height(14.dp))
            content()
        }
    }
}
