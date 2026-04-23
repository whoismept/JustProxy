package com.whoismept.justproxy.ui.screens

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ClearAll
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.whoismept.justproxy.data.ConnectionLog
import com.whoismept.justproxy.data.ConnectionStatus
import com.whoismept.justproxy.ui.LocalStrings
import com.whoismept.justproxy.ui.ProxyViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogScreen(viewModel: ProxyViewModel) {
    val s            = LocalStrings.current
    val logs        by viewModel.connectionLogs.collectAsState()
    val timeFmt      = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }
    val dateFmt      = remember { SimpleDateFormat("HH:mm:ss · dd MMM yyyy", Locale.getDefault()) }
    val scope        = rememberCoroutineScope()
    val context      = LocalContext.current
    var expandedIds by remember { mutableStateOf(setOf<Long>()) }
    val iconRotation = remember { Animatable(0f) }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(s.logTitle, fontWeight = FontWeight.ExtraBold) },
                actions = {
                    if (logs.isNotEmpty()) {
                        IconButton(onClick = {
                            val count = logs.size
                            viewModel.clearLogs()
                            if (expandedIds.isNotEmpty()) expandedIds = emptySet()
                            scope.launch {
                                iconRotation.animateTo(
                                    targetValue  = 360f,
                                    animationSpec = tween(700, easing = FastOutSlowInEasing)
                                )
                                iconRotation.snapTo(0f)
                            }
                            scope.launch {
                                delay(1_000)
                                Toast.makeText(context, s.logClearedToast(count), Toast.LENGTH_SHORT).show()
                            }
                        }) {
                            Icon(
                                Icons.Default.ClearAll,
                                contentDescription = s.logClearDesc,
                                modifier = Modifier.rotate(iconRotation.value)
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Color.Transparent)
            )
        }
    ) { padding ->
        if (logs.isEmpty()) {
            LogEmptyState(modifier = Modifier.padding(padding))
        } else {
            LazyColumn(
                modifier        = Modifier.padding(padding),
                contentPadding  = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(logs, key = { it.id }) { log ->
                    val expanded = log.id in expandedIds
                    LogEntryCard(
                        log      = log,
                        timeFmt  = timeFmt,
                        dateFmt  = dateFmt,
                        expanded = expanded,
                        onClick  = {
                            expandedIds = if (expanded) expandedIds - log.id else expandedIds + log.id
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun LogEntryCard(
    log     : ConnectionLog,
    timeFmt : SimpleDateFormat,
    dateFmt : SimpleDateFormat,
    expanded: Boolean,
    onClick : () -> Unit
) {
    val (labelColor, badgeBg) = statusColors(log.status)

    Surface(
        shape  = RoundedCornerShape(12.dp),
        color  = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Column {
            Row(
                modifier          = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ProtocolBadge(log.protocol)
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(log.host, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, maxLines = 1)
                    Text(
                        "${timeFmt.format(Date(log.timestamp))}  ·  ${log.durationMs}ms",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                StatusBadge(log.status.label, labelColor, badgeBg)
            }

            AnimatedVisibility(
                visible = expanded,
                enter   = expandVertically(),
                exit    = shrinkVertically()
            ) {
                HorizontalDivider(
                    modifier  = Modifier.padding(horizontal = 14.dp),
                    color     = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                    thickness = 0.5.dp
                )
                Column(
                    modifier            = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val chips = buildList {
                        if (log.tlsVersion != null) add(log.tlsVersion to MaterialTheme.colorScheme.primary)
                        if (log.hasEch)    add("ECH ⚠" to Color(0xFF9C27B0))
                        if (log.hasGrease) add("GREASE" to MaterialTheme.colorScheme.secondary)
                        if (log.via.isNotBlank()) add(log.via to MaterialTheme.colorScheme.outline)
                    }
                    if (chips.isNotEmpty()) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            chips.forEach { (label, color) -> DetailChip(label, color) }
                        }
                    }
                    if (log.alertCode >= 0) {
                        DetailRow(label = LocalStrings.current.logDetailTlsAlert, value = "${tlsAlertName(log.alertCode)} (${log.alertCode})")
                    }
                    DetailRow(label = LocalStrings.current.logDetailTime,     value = dateFmt.format(Date(log.timestamp)))
                    DetailRow(label = LocalStrings.current.logDetailDuration, value = "${log.durationMs} ms")
                }
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            "$label:",
            style    = MaterialTheme.typography.labelSmall,
            color    = MaterialTheme.colorScheme.outline,
            modifier = Modifier.width(72.dp)
        )
        Text(value, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun DetailChip(label: String, color: Color) {
    Surface(shape = RoundedCornerShape(6.dp), color = color.copy(alpha = 0.12f)) {
        Text(
            label,
            modifier   = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            style      = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color      = color
        )
    }
}

@Composable
private fun ProtocolBadge(protocol: String) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = if (protocol == "HTTPS") MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                else                     MaterialTheme.colorScheme.secondary.copy(alpha = 0.14f)
    ) {
        Text(
            protocol,
            modifier   = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
            style      = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color      = if (protocol == "HTTPS") MaterialTheme.colorScheme.primary
                         else                     MaterialTheme.colorScheme.secondary
        )
    }
}

@Composable
private fun StatusBadge(label: String, textColor: Color, bgColor: Color) {
    Surface(shape = RoundedCornerShape(6.dp), color = bgColor) {
        Text(
            label,
            modifier   = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            style      = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color      = textColor
        )
    }
}

@Composable
private fun statusColors(status: ConnectionStatus): Pair<Color, Color> = when (status) {
    ConnectionStatus.CONNECTED  -> MaterialTheme.colorScheme.primary to MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
    ConnectionStatus.SSL_PINNED -> Color(0xFFF44336) to Color(0xFFF44336).copy(alpha = 0.12f)
    ConnectionStatus.UNKNOWN_CA -> Color(0xFFFF9800) to Color(0xFFFF9800).copy(alpha = 0.12f)
    ConnectionStatus.ECH        -> Color(0xFF9C27B0) to Color(0xFF9C27B0).copy(alpha = 0.12f)
    ConnectionStatus.FAILED     -> MaterialTheme.colorScheme.outline to MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)
}

private fun tlsAlertName(code: Int) = when (code) {
    0   -> "close_notify";      10  -> "unexpected_message"
    20  -> "bad_record_mac";    40  -> "handshake_failure"
    42  -> "bad_certificate";   43  -> "unsupported_certificate"
    44  -> "certificate_revoked"; 45 -> "certificate_expired"
    46  -> "certificate_unknown"; 47 -> "illegal_parameter"
    48  -> "unknown_ca";         49 -> "access_denied"
    50  -> "decode_error";       51 -> "decrypt_error"
    70  -> "protocol_version";   71 -> "insufficient_security"
    80  -> "internal_error";     86 -> "inappropriate_fallback"
    90  -> "user_canceled";     110 -> "unsupported_extension"
    112 -> "unrecognized_name"; 116 -> "unknown_psk_identity"
    else -> "unknown"
}

@Composable
private fun LogEmptyState(modifier: Modifier = Modifier) {
    val s = LocalStrings.current
    Column(
        modifier             = modifier.fillMaxSize(),
        verticalArrangement  = Arrangement.Center,
        horizontalAlignment  = Alignment.CenterHorizontally
    ) {
        Icon(Icons.Default.Timeline, contentDescription = null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.outline)
        Spacer(modifier = Modifier.height(8.dp))
        Text(s.logEmptyTitle, color = MaterialTheme.colorScheme.outline)
        Text(
            s.logEmptySubtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.outline
        )
    }
}
