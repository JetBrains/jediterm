package ai.rever.bossterm.compose.update

import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Update notification banner that appears at the top of the application.
 */
@Composable
fun UpdateBanner(
    updateState: UpdateState,
    onCheckForUpdates: () -> Unit = {},
    onDownloadUpdate: (UpdateInfo) -> Unit = {},
    onInstallUpdate: (String) -> Unit = {},
    onDismiss: () -> Unit = {}
) {
    when (updateState) {
        is UpdateState.UpdateAvailable -> {
            UpdateAvailableBanner(
                updateInfo = updateState.updateInfo,
                onDownload = { onDownloadUpdate(updateState.updateInfo) },
                onDismiss = onDismiss
            )
        }
        is UpdateState.Downloading -> {
            DownloadProgressBanner(progress = updateState.progress)
        }
        is UpdateState.ReadyToInstall -> {
            ReadyToInstallBanner(
                onInstall = { onInstallUpdate(updateState.downloadPath) }
            )
        }
        is UpdateState.RestartRequired -> {
            RestartRequiredBanner()
        }
        is UpdateState.Error -> {
            ErrorBanner(
                message = updateState.message,
                onRetry = onCheckForUpdates,
                onDismiss = onDismiss
            )
        }
        else -> { /* No banner for other states */ }
    }
}

// Colors matching TabBar
private val BannerBackground = Color(0xFF1E1E1E)
private val AccentBlue = Color(0xFF4A90E2)
private val AccentGreen = Color(0xFF4CAF50)
private val AccentOrange = Color(0xFFFF9800)
private val AccentRed = Color(0xFFF44336)

@Composable
private fun UpdateAvailableBanner(
    updateInfo: UpdateInfo,
    onDownload: () -> Unit,
    onDismiss: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = BannerBackground
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    Icons.Default.KeyboardArrowDown,
                    contentDescription = "Update Available",
                    tint = AccentBlue,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    "Update v${updateInfo.latestVersion} available",
                    color = Color.White,
                    fontSize = 12.sp
                )
                Text(
                    " (current: v${updateInfo.currentVersion})",
                    color = Color(0xFF808080),
                    fontSize = 12.sp
                )
            }

            Row {
                TextButton(
                    onClick = onDownload,
                    colors = ButtonDefaults.textButtonColors(contentColor = AccentBlue),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                    modifier = Modifier.height(28.dp)
                ) {
                    Text("Download", fontSize = 11.sp)
                }
                TextButton(
                    onClick = onDismiss,
                    colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFF808080)),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                    modifier = Modifier.height(28.dp)
                ) {
                    Text("Dismiss", fontSize = 11.sp)
                }
            }
        }
    }
}

@Composable
private fun DownloadProgressBanner(progress: Float) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = BannerBackground
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.KeyboardArrowDown,
                contentDescription = "Downloading",
                tint = AccentGreen,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                "Downloading... ${(progress * 100).toInt()}%",
                color = Color.White,
                fontSize = 12.sp
            )
            Spacer(modifier = Modifier.width(12.dp))
            LinearProgressIndicator(
                progress = progress,
                modifier = Modifier.weight(1f).height(4.dp),
                color = AccentGreen,
                backgroundColor = Color(0xFF404040)
            )
        }
    }
}

@Composable
private fun ReadyToInstallBanner(onInstall: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = BannerBackground
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Info,
                    contentDescription = "Ready to Install",
                    tint = AccentOrange,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    "Update ready to install",
                    color = Color.White,
                    fontSize = 12.sp
                )
            }

            TextButton(
                onClick = onInstall,
                colors = ButtonDefaults.textButtonColors(contentColor = AccentOrange),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                modifier = Modifier.height(28.dp)
            ) {
                Text("Install Now", fontSize = 11.sp)
            }
        }
    }
}

@Composable
private fun RestartRequiredBanner() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = BannerBackground
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Refresh,
                contentDescription = "Restart Required",
                tint = AccentBlue,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                "Installing update... Please wait.",
                color = Color.White,
                fontSize = 12.sp
            )
        }
    }
}

@Composable
private fun ErrorBanner(
    message: String,
    onRetry: () -> Unit,
    onDismiss: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = BannerBackground
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    Icons.Default.Warning,
                    contentDescription = "Error",
                    tint = AccentRed,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    "Update error: $message",
                    color = Color.White,
                    fontSize = 12.sp,
                    maxLines = 1
                )
            }

            Row {
                TextButton(
                    onClick = onRetry,
                    colors = ButtonDefaults.textButtonColors(contentColor = AccentBlue),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                    modifier = Modifier.height(28.dp)
                ) {
                    Text("Retry", fontSize = 11.sp)
                }
                TextButton(
                    onClick = onDismiss,
                    colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFF808080)),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                    modifier = Modifier.height(28.dp)
                ) {
                    Text("Dismiss", fontSize = 11.sp)
                }
            }
        }
    }
}

/**
 * Format timestamp as human-readable time ago string.
 */
fun formatTimeAgo(timestampMs: Long?): String {
    if (timestampMs == null) return "Never"

    val now = System.currentTimeMillis()
    val diffMs = now - timestampMs
    val diffMinutes = diffMs / (1000 * 60)
    val diffHours = diffMinutes / 60
    val diffDays = diffHours / 24

    return when {
        diffMinutes < 5 -> "Just now"
        diffMinutes < 60 -> "$diffMinutes minutes ago"
        diffHours == 1L -> "1 hour ago"
        diffHours < 24 -> "$diffHours hours ago"
        diffDays == 1L -> "Yesterday"
        diffDays < 7 -> "$diffDays days ago"
        else -> "${diffDays / 7} weeks ago"
    }
}
