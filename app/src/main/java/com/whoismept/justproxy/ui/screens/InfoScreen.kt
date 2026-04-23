package com.whoismept.justproxy.ui.screens

import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.core.net.toUri
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.whoismept.justproxy.R
import com.whoismept.justproxy.ui.LocalStrings
import com.whoismept.justproxy.utils.RootHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun InfoScreen() {
    val s          = LocalStrings.current
    val context    = LocalContext.current
    var isRooted   by remember { mutableStateOf<Boolean?>(null) }

    LaunchedEffect(Unit) {
        isRooted = withContext(Dispatchers.IO) { RootHelper.isRootAvailable() }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            painter            = painterResource(R.drawable.ic_launcher_foreground),
            contentDescription = null,
            modifier           = Modifier.size(96.dp),
            tint               = Color.Unspecified
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text("JustProxy", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
        Text("v1.0.0", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.outline)

        Spacer(modifier = Modifier.height(28.dp))

        InfoRow(
            title = s.infoRootAccess,
            value = when (isRooted) {
                true  -> s.infoRootGranted
                false -> s.infoRootDenied
                null  -> s.infoRootChecking
            },
            icon  = Icons.Default.VerifiedUser,
            color = when (isRooted) {
                true  -> Color(0xFF4CAF50)
                false -> Color(0xFFF44336)
                null  -> MaterialTheme.colorScheme.outline
            }
        )

        Spacer(modifier = Modifier.height(12.dp))

        Surface(
            modifier = Modifier.fillMaxWidth().clickable {
                context.startActivity(Intent(Intent.ACTION_VIEW, "https://github.com/whoismept/justproxy".toUri()))
            },
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ) {
            Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Code, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(s.infoDeveloper, style = MaterialTheme.typography.labelLarge)
                    Text("whoismept", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("github.com/whoismept/justproxy", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape    = RoundedCornerShape(16.dp),
            color    = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Balance, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(s.infoOpenSource, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.height(10.dp))
                LicenseEntry("Jetpack Compose",           "Apache 2.0", "Google")
                LicenseEntry("Compose Material3",         "Apache 2.0", "Google")
                LicenseEntry("Compose Material Icons",    "Apache 2.0", "Google")
                LicenseEntry("AndroidX Core KTX",         "Apache 2.0", "Google")
                LicenseEntry("AndroidX Lifecycle",        "Apache 2.0", "Google")
                LicenseEntry("AndroidX Activity Compose", "Apache 2.0", "Google")
                LicenseEntry("Room",                      "Apache 2.0", "Google / Jetpack")
                LicenseEntry("Kotlin Coroutines",         "Apache 2.0", "JetBrains")
            }
        }

        Spacer(modifier = Modifier.weight(1f))
        Spacer(modifier = Modifier.height(24.dp))

        Text(
            s.infoFooter,
            style     = MaterialTheme.typography.labelSmall,
            textAlign = TextAlign.Center,
            color     = MaterialTheme.colorScheme.outline
        )
    }
}

@Composable
private fun LicenseEntry(name: String, license: String, author: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(name,   style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            Text(author, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
        }
        Surface(
            shape = RoundedCornerShape(6.dp),
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
        ) {
            Text(
                license,
                modifier   = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                style      = MaterialTheme.typography.labelSmall,
                color      = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun InfoRow(title: String, value: String, icon: ImageVector, color: Color) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape    = RoundedCornerShape(16.dp),
        color    = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = color)
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(title, style = MaterialTheme.typography.labelLarge)
                Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = color)
            }
        }
    }
}
