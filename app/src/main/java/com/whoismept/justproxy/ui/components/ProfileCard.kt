package com.whoismept.justproxy.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.whoismept.justproxy.data.ProxyProfile
import com.whoismept.justproxy.data.ProxyType

@Suppress("UNUSED_VALUE")
@Composable
fun ProfileCard(
    profile  : ProxyProfile,
    onToggle : () -> Unit,
    onDelete : () -> Unit,
    onEdit   : () -> Unit
) {
    var confirmDelete by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable { onEdit() },
        colors = CardDefaults.cardColors(
            containerColor = if (profile.isActive)
                MaterialTheme.colorScheme.primaryContainer
            else
                MaterialTheme.colorScheme.surface
        )
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            // Icon badge — emoji if set, otherwise first letter of proxy type
            val hasEmoji = profile.icon.isNotBlank()
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f), RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                if (hasEmoji) {
                    Text(profile.icon, fontSize = 22.sp)
                } else {
                    Text(
                        profile.type.name.take(1),
                        fontWeight = FontWeight.Bold,
                        color      = MaterialTheme.colorScheme.primary,
                        fontSize   = 18.sp
                    )
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(profile.name, fontWeight = FontWeight.Bold)
                Text("${profile.host}:${profile.port}", style = MaterialTheme.typography.bodySmall)
                if (!profile.targetPackages.isNullOrBlank()) {
                    val count = profile.targetPackages.split(",").filter { it.isNotBlank() }.size
                    Text("$count app${if (count != 1) "s" else ""} selected",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary)
                }
                Text(
                    text  = proxyLabel(profile),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }

            Switch(checked = profile.isActive, onCheckedChange = { onToggle() })
            Spacer(modifier = Modifier.width(8.dp))

            IconButton(onClick = { confirmDelete = true }, modifier = Modifier.size(40.dp)) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Delete",
                    tint     = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title   = { Text("Delete Profile?") },
            text    = { Text("'${profile.name}' will be permanently removed.") },
            confirmButton = {
                Button(
                    onClick = { onDelete(); confirmDelete = false },
                    colors  = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("Cancel") }
            }
        )
    }
}

private fun proxyLabel(profile: ProxyProfile): String = when (profile.type) {
    ProxyType.HTTP   -> if (profile.isHttp) "HTTP · System Proxy" else "HTTP · Transparent"
    ProxyType.SOCKS4 -> "SOCKS4"
    ProxyType.SOCKS5 -> "SOCKS5"
}
