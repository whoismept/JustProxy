package com.whoismept.justproxy.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.core.graphics.drawable.toBitmap
import com.whoismept.justproxy.ui.AppInfo
import com.whoismept.justproxy.ui.ProxyViewModel

@Composable
fun AppPickerDialog(
    viewModel      : ProxyViewModel,
    showSystemApps : Boolean,
    selectedPkgs   : List<String>,
    onDismiss      : () -> Unit,
    onConfirm      : (List<String>) -> Unit
) {
    val context  = LocalContext.current
    val apps     by viewModel.installedApps.collectAsState()
    var current  by remember { mutableStateOf(selectedPkgs) }
    var query    by remember { mutableStateOf("") }

    LaunchedEffect(Unit) { viewModel.loadInstalledApps(context, showSystemApps) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier.fillMaxWidth().fillMaxHeight(0.85f),
            shape    = RoundedCornerShape(24.dp)
        ) {
            Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                Text("Select Apps", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value    = query,
                    onValueChange = { query = it },
                    placeholder   = { Text("Search…") },
                    leadingIcon   = { Icon(Icons.Default.Search, null) },
                    modifier = Modifier.fillMaxWidth(),
                    shape    = RoundedCornerShape(12.dp),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(10.dp))

                val filtered = apps.filter {
                    it.name.contains(query, ignoreCase = true) ||
                    it.packageName.contains(query, ignoreCase = true)
                }

                LazyColumn(modifier = Modifier.weight(1f)) {
                    items(filtered, key = { it.packageName }) { app ->
                        AppItem(
                            app        = app,
                            isSelected = app.packageName in current,
                            onToggle   = {
                                current = if (app.packageName in current)
                                    current - app.packageName
                                else
                                    current + app.packageName
                            }
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = { current = emptyList() }) { Text("Clear All") }
                    Spacer(modifier = Modifier.weight(1f))
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    Button(onClick = { onConfirm(current) }) { Text("Done") }
                }
            }
        }
    }
}

@Composable
private fun AppItem(app: AppInfo, isSelected: Boolean, onToggle: () -> Unit) {
    // Convert Drawable → Bitmap once per app entry, not on every recomposition.
    val bitmap = remember(app.packageName) { app.icon?.toBitmap()?.asImageBitmap() }
    Row(
        modifier  = Modifier.fillMaxWidth().clickable { onToggle() }.padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (bitmap != null) {
            Image(
                bitmap             = bitmap,
                contentDescription = null,
                modifier           = Modifier.size(40.dp)
            )
        } else {
            Box(modifier = Modifier.size(40.dp).background(Color.Gray.copy(alpha = 0.2f), RoundedCornerShape(8.dp)))
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(app.name,        style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(app.packageName, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Checkbox(checked = isSelected, onCheckedChange = { onToggle() })
    }
}
