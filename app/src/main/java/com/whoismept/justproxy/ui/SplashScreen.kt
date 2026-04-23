package com.whoismept.justproxy.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.ui.res.painterResource
import com.whoismept.justproxy.R
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.whoismept.justproxy.data.ProxyMode
import com.whoismept.justproxy.utils.RootHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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
                1 -> WelcomeStep { step = 2 }
                2 -> {
                    LaunchedEffect(Unit) {
                        rootAvailable = withContext(Dispatchers.IO) { RootHelper.isRootAvailable() }
                    }
                    ModeSelectStep(rootAvailable, onFinish)
                }
            }
        }
    }
}

@Composable
private fun WelcomeStep(onNext: () -> Unit) {
    val s = LocalStrings.current
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(24.dp)
    ) {
        Icon(painterResource(R.drawable.ic_launcher_foreground), null, modifier = Modifier.size(120.dp), tint = Color.Unspecified)
        Spacer(modifier = Modifier.height(24.dp))
        Text(s.splashWelcome, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Text(s.splashSubtitle, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
        Spacer(modifier = Modifier.height(48.dp))
        Button(
            onClick = onNext,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Text(s.splashGetStarted, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun ModeSelectStep(rootAvailable: Boolean?, onFinish: (ProxyMode) -> Unit) {
    val s = LocalStrings.current
    Column(
        modifier = Modifier.padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(s.splashSystemCheck, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(16.dp))

        StatusRow(
            title = s.splashRootAccess,
            status = when (rootAvailable) {
                true  -> s.splashRootFound
                false -> s.splashRootNotFound
                null  -> s.splashChecking
            },
            icon  = if (rootAvailable == true) Icons.Default.CheckCircle else Icons.Default.Cancel,
            color = when (rootAvailable) {
                true  -> Color.Green
                false -> Color.Red
                else  -> Color.Gray
            }
        )

        Spacer(modifier = Modifier.height(32.dp))
        Text(s.splashSelectMode, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(16.dp))

        ModeCard(
            title = s.splashRootMode,
            description = s.splashRootModeDesc,
            icon = Icons.Default.Bolt,
            enabled = rootAvailable == true,
            onClick = { onFinish(ProxyMode.ROOT) }
        )
        Spacer(modifier = Modifier.height(16.dp))
        ModeCard(
            title = s.splashVpnMode,
            description = s.splashVpnModeDesc,
            icon = Icons.Default.VpnLock,
            enabled = true,
            onClick = { onFinish(ProxyMode.VPN) }
        )
    }
}

@Composable
fun StatusRow(title: String, status: String, icon: ImageVector, color: Color) {
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
        onClick = { if (enabled) onClick() },
        modifier = Modifier.fillMaxWidth().alpha(if (enabled) 1f else 0.5f),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
    ) {
        Row(modifier = Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(48.dp).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f), CircleShape),
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
