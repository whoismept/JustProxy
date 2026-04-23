package com.whoismept.justproxy.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.whoismept.justproxy.data.ProxyProfile
import com.whoismept.justproxy.data.ProxyType
import com.whoismept.justproxy.ui.ProxyViewModel

private val PROFILE_ICONS = listOf(
    "🔒", "🔐", "🛡", "🌐", "🔗", "⚡", "🔑", "📡",
    "🏠", "💎", "🚀", "🎯", "🔵", "🔴", "🟢", "🟡"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProxyEditDialog(
    viewModel      : ProxyViewModel,
    showSystemApps : Boolean,
    initialProfile : ProxyProfile? = null,
    onDismiss      : () -> Unit,
    onConfirm      : (name: String, host: String, port: Int, type: ProxyType, isHttp: Boolean, packages: String?, icon: String) -> Unit
) {
    var name            by remember { mutableStateOf(initialProfile?.name ?: "") }
    var host            by remember { mutableStateOf(initialProfile?.host ?: "") }
    var port            by remember { mutableStateOf(initialProfile?.port?.toString() ?: "") }
    var type            by remember { mutableStateOf(initialProfile?.type ?: ProxyType.HTTP) }
    var isHttp          by remember { mutableStateOf(initialProfile?.isHttp ?: true) }
    var icon            by remember { mutableStateOf(initialProfile?.icon ?: "") }
    var showCustomize   by remember { mutableStateOf(false) }
    var showPicker      by remember { mutableStateOf(false) }
    var selected        by remember {
        mutableStateOf(
            initialProfile?.targetPackages
                ?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() }
                ?: emptyList()
        )
    }

    val portInt        = port.toIntOrNull()
    val portError      = port.isNotEmpty() && (portInt == null || portInt !in 1..65535)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initialProfile == null) "Add Profile" else "Edit Profile") },
        text  = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // ── Basic fields ─────────────────────────────────────────────
                OutlinedTextField(
                    value         = name,
                    onValueChange = { name = it },
                    label         = { Text("Name") },
                    modifier      = Modifier.fillMaxWidth(),
                    singleLine    = true
                )
                OutlinedTextField(
                    value         = host,
                    onValueChange = { host = it },
                    label         = { Text("Host") },
                    modifier      = Modifier.fillMaxWidth(),
                    singleLine    = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii)
                )
                OutlinedTextField(
                    value         = port,
                    onValueChange = { port = it.filter(Char::isDigit).take(5) },
                    label         = { Text("Port") },
                    modifier      = Modifier.fillMaxWidth(),
                    singleLine    = true,
                    isError       = portError,
                    supportingText = if (portError) ({ Text("Enter a valid port (1–65535)") }) else null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )

                // ── Customize (collapsible) ──────────────────────────────────
                Surface(
                    onClick = { showCustomize = !showCustomize },
                    shape   = RoundedCornerShape(8.dp),
                    color   = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Row(
                        modifier          = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Customize Icon",
                            style      = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Medium,
                            modifier   = Modifier.weight(1f)
                        )
                        if (icon.isNotBlank()) Text(icon, fontSize = 18.sp)
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            if (showCustomize) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                AnimatedVisibility(visible = showCustomize) {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding        = PaddingValues(vertical = 4.dp)
                    ) {
                        items(PROFILE_ICONS, key = { it }) { emoji ->
                            val isSelected = icon == emoji
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(
                                        if (isSelected) MaterialTheme.colorScheme.primaryContainer
                                        else MaterialTheme.colorScheme.surfaceVariant
                                    )
                                    .then(
                                        if (isSelected) Modifier.border(
                                            2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp)
                                        ) else Modifier
                                    )
                                    .clickable { icon = if (icon == emoji) "" else emoji },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(emoji, fontSize = 20.sp, textAlign = TextAlign.Center)
                            }
                        }
                    }
                }

                // ── App picker trigger ───────────────────────────────────────
                Text("Target Apps", style = MaterialTheme.typography.labelLarge)
                Surface(
                    onClick  = { showPicker = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape    = RoundedCornerShape(8.dp),
                    color    = MaterialTheme.colorScheme.surfaceVariant,
                    border   = CardDefaults.outlinedCardBorder()
                ) {
                    Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Apps, contentDescription = null)
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            if (selected.isEmpty()) "Global (all apps)"
                            else "${selected.size} app${if (selected.size != 1) "s" else ""} selected",
                            modifier = Modifier.weight(1f)
                        )
                        Icon(Icons.Default.ChevronRight, contentDescription = null)
                    }
                }

                // ── Proxy type chips ─────────────────────────────────────────
                Text("Proxy Type", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ProxyType.entries.forEach { t ->
                        FilterChip(
                            selected = type == t,
                            onClick  = { type = t; if (t != ProxyType.HTTP) isHttp = false },
                            label    = { Text(t.name) }
                        )
                    }
                }

                // ── System proxy mode ────────────────────────────────────────
                if (type == ProxyType.HTTP) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = isHttp, onCheckedChange = { isHttp = it })
                        Column {
                            Text("System Proxy Mode", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                            Text(
                                "(Root only — sets Android global HTTP proxy)",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick  = {
                    val p = portInt ?: 8080
                    onConfirm(name, host, p, type, isHttp, selected.takeIf { it.isNotEmpty() }?.joinToString(","), icon)
                },
                enabled = name.isNotBlank() && host.isNotBlank() && !portError
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )

    if (showPicker) {
        AppPickerDialog(
            viewModel      = viewModel,
            showSystemApps = showSystemApps,
            selectedPkgs   = selected,
            onDismiss      = { showPicker = false },
            onConfirm      = { selected = it; showPicker = false }
        )
    }
}
