package ai.rever.bossterm.compose.update

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ai.rever.bossterm.compose.settings.SettingsTheme.AccentColor
import ai.rever.bossterm.compose.settings.SettingsTheme.BackgroundColor
import ai.rever.bossterm.compose.settings.SettingsTheme.BorderColor
import ai.rever.bossterm.compose.settings.SettingsTheme.SurfaceColor
import ai.rever.bossterm.compose.settings.SettingsTheme.TextMuted
import ai.rever.bossterm.compose.settings.SettingsTheme.TextPrimary
import ai.rever.bossterm.compose.settings.SettingsTheme.TextSecondary
import kotlinx.coroutines.launch
import java.awt.Desktop
import java.io.File

/**
 * State holder for version selection in About section.
 */
class VersionSelectorState {
    var releases by mutableStateOf<List<GitHubRelease>>(emptyList())
    var isLoading by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
    var selectedRelease by mutableStateOf<GitHubRelease?>(null)
    var downloadProgress by mutableStateOf<Float?>(null)
    var downloadedFile by mutableStateOf<File?>(null)
    var showPreReleases by mutableStateOf(false)
    var showDowngradeWarning by mutableStateOf(false)
    var showInstallInstructions by mutableStateOf(false)
}

/**
 * Version Management section for the About page.
 */
@Composable
fun VersionManagementSection(
    modifier: Modifier = Modifier
) {
    val state = remember { VersionSelectorState() }
    val updateService = remember { DesktopUpdateService() }
    val scope = rememberCoroutineScope()
    val currentVersion = Version.CURRENT

    // Fetch releases on first load and when pre-release toggle changes
    LaunchedEffect(state.showPreReleases) {
        state.isLoading = true
        state.error = null
        val result = updateService.getAllReleases(state.showPreReleases)
        result.fold(
            onSuccess = { releases ->
                state.releases = releases
                state.isLoading = false
            },
            onFailure = { e ->
                state.error = e.message ?: "Failed to fetch releases"
                state.isLoading = false
            }
        )
    }

    Column(modifier = modifier) {
        // Current version info
        InfoRowStyled("Current Version", currentVersion.toString())

        Spacer(modifier = Modifier.height(12.dp))

        // Pre-release toggle
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(6.dp))
                .background(SurfaceColor)
                .clickable { state.showPreReleases = !state.showPreReleases }
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Show pre-release versions",
                color = TextPrimary,
                fontSize = 13.sp
            )
            Switch(
                checked = state.showPreReleases,
                onCheckedChange = { state.showPreReleases = it },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = AccentColor,
                    checkedTrackColor = AccentColor.copy(alpha = 0.5f)
                )
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Version dropdown
        VersionDropdown(
            releases = state.releases,
            selectedRelease = state.selectedRelease,
            currentVersion = currentVersion,
            isLoading = state.isLoading,
            error = state.error,
            onSelect = { state.selectedRelease = it },
            onRetry = {
                scope.launch {
                    state.isLoading = true
                    state.error = null
                    val result = updateService.getAllReleases(state.showPreReleases)
                    result.fold(
                        onSuccess = { releases ->
                            state.releases = releases
                            state.isLoading = false
                        },
                        onFailure = { e ->
                            state.error = e.message ?: "Failed to fetch releases"
                            state.isLoading = false
                        }
                    )
                }
            }
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Download button and progress
        val selectedVersion = state.selectedRelease?.let { Version.parse(it.tag_name) }
        val isDowngrading = selectedVersion != null && selectedVersion < currentVersion
        val isSameVersion = selectedVersion != null && selectedVersion == currentVersion

        if (state.downloadProgress != null) {
            // Show progress
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .background(SurfaceColor)
                    .padding(12.dp)
            ) {
                Text(
                    text = "Downloading ${state.selectedRelease?.tag_name}...",
                    color = TextPrimary,
                    fontSize = 13.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = state.downloadProgress!!,
                    modifier = Modifier.fillMaxWidth(),
                    color = AccentColor
                )
                Text(
                    text = "${(state.downloadProgress!! * 100).toInt()}%",
                    color = TextSecondary,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        } else {
            // Show download button
            Button(
                onClick = {
                    if (isDowngrading) {
                        state.showDowngradeWarning = true
                    } else {
                        scope.launch {
                            downloadVersion(state, updateService)
                        }
                    }
                },
                enabled = state.selectedRelease != null && !isSameVersion && !state.isLoading,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    backgroundColor = if (isDowngrading) Color(0xFFB71C1C) else AccentColor,
                    contentColor = Color.White,
                    disabledBackgroundColor = SurfaceColor,
                    disabledContentColor = TextMuted
                )
            ) {
                Text(
                    text = when {
                        isSameVersion -> "Current Version"
                        isDowngrading -> "Downgrade to ${state.selectedRelease?.tag_name}"
                        state.selectedRelease != null -> "Download ${state.selectedRelease?.tag_name}"
                        else -> "Select a Version"
                    },
                    fontSize = 13.sp
                )
            }
        }

        // Downgrade warning dialog
        if (state.showDowngradeWarning) {
            DowngradeWarningDialog(
                currentVersion = currentVersion,
                targetVersion = selectedVersion!!,
                onConfirm = {
                    state.showDowngradeWarning = false
                    scope.launch {
                        downloadVersion(state, updateService)
                    }
                },
                onDismiss = { state.showDowngradeWarning = false }
            )
        }

        // Install instructions dialog
        if (state.showInstallInstructions && state.downloadedFile != null) {
            InstallInstructionsDialog(
                downloadedFile = state.downloadedFile!!,
                platform = updateService.getCurrentPlatform(),
                onDismiss = {
                    state.showInstallInstructions = false
                    state.downloadedFile = null
                    state.selectedRelease = null
                }
            )
        }
    }
}

private suspend fun downloadVersion(state: VersionSelectorState, updateService: DesktopUpdateService) {
    val release = state.selectedRelease ?: return
    state.downloadProgress = 0f

    val result = updateService.downloadRelease(release) { progress ->
        state.downloadProgress = progress
    }

    result.fold(
        onSuccess = { path ->
            state.downloadProgress = null
            state.downloadedFile = File(path)
            state.showInstallInstructions = true
        },
        onFailure = { e ->
            state.downloadProgress = null
            state.error = e.message ?: "Download failed"
        }
    )
}

@Composable
private fun VersionDropdown(
    releases: List<GitHubRelease>,
    selectedRelease: GitHubRelease?,
    currentVersion: Version,
    isLoading: Boolean,
    error: String?,
    onSelect: (GitHubRelease) -> Unit,
    onRetry: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    // Settings-style dropdown row
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(SurfaceColor)
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Label on left
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Select Version",
                    color = if (isLoading) TextMuted else TextPrimary,
                    fontSize = 13.sp
                )
                if (error != null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(top = 2.dp)
                    ) {
                        Text(
                            text = error,
                            color = Color(0xFFE57373),
                            fontSize = 11.sp
                        )
                        Text(
                            text = "Retry",
                            color = AccentColor,
                            fontSize = 11.sp,
                            modifier = Modifier.clickable { onRetry() }
                        )
                    }
                }
            }

            // Dropdown selector on right (matching SettingsDropdown style)
            Box {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(BackgroundColor)
                        .border(1.dp, BorderColor, RoundedCornerShape(4.dp))
                        .clickable(enabled = !isLoading && error == null) { expanded = true }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            strokeWidth = 2.dp,
                            color = AccentColor
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Loading...",
                            color = TextMuted,
                            fontSize = 13.sp
                        )
                    } else {
                        Text(
                            text = selectedRelease?.tag_name ?: "Select...",
                            color = TextPrimary,
                            fontSize = 13.sp
                        )
                    }
                    Icon(
                        imageVector = Icons.Default.ArrowDropDown,
                        contentDescription = "Expand",
                        tint = TextSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }

                // Dropdown menu
                DropdownMenu(
                    expanded = expanded && !isLoading && error == null,
                    onDismissRequest = { expanded = false },
                    modifier = Modifier.background(SurfaceColor)
                ) {
                    if (releases.isEmpty()) {
                        DropdownMenuItem(onClick = {}) {
                            Text("No releases available", color = TextMuted, fontSize = 13.sp)
                        }
                    } else {
                        releases.forEach { release ->
                            val version = Version.parse(release.tag_name)
                            val isCurrent = version == currentVersion

                            DropdownMenuItem(
                                onClick = {
                                    onSelect(release)
                                    expanded = false
                                }
                            ) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = release.tag_name,
                                        color = if (isCurrent) AccentColor else TextPrimary,
                                        fontSize = 13.sp,
                                        fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal
                                    )
                                    if (release.prerelease) {
                                        VersionBadge("pre-release", Color(0xFFFFA726))
                                    }
                                    if (isCurrent) {
                                        VersionBadge("current", AccentColor)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun VersionBadge(text: String, color: Color) {
    Text(
        text = text,
        color = color,
        fontSize = 10.sp,
        fontWeight = FontWeight.Medium,
        modifier = Modifier
            .background(color.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    )
}

@Composable
private fun InfoRowStyled(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(SurfaceColor)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, color = TextPrimary, fontSize = 13.sp)
        Text(text = value, color = TextSecondary, fontSize = 13.sp)
    }
}

@Composable
fun DowngradeWarningDialog(
    currentVersion: Version,
    targetVersion: Version,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Downgrade Warning",
                color = TextPrimary,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column {
                Text(
                    text = "You are about to downgrade from $currentVersion to $targetVersion.",
                    color = TextPrimary,
                    fontSize = 14.sp
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "This may cause:",
                    color = TextSecondary,
                    fontSize = 13.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text("• Loss of features added in newer versions", color = TextMuted, fontSize = 12.sp)
                Text("• Potential settings incompatibility", color = TextMuted, fontSize = 12.sp)
                Text("• Data format changes may not be reversible", color = TextMuted, fontSize = 12.sp)
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(
                    backgroundColor = Color(0xFFB71C1C),
                    contentColor = Color.White
                )
            ) {
                Text("Downgrade Anyway")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("Cancel", color = TextPrimary)
            }
        },
        backgroundColor = Color(0xFF2D2D2D)
    )
}

@Composable
fun InstallInstructionsDialog(
    downloadedFile: File,
    platform: String,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Download Complete",
                color = TextPrimary,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column {
                Text(
                    text = "Downloaded: ${downloadedFile.name}",
                    color = AccentColor,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Installation instructions:",
                    color = TextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(8.dp))

                when (platform) {
                    "macOS" -> {
                        InstructionStep("1. Open the DMG file")
                        InstructionStep("2. Drag BossTerm to Applications")
                        InstructionStep("3. Restart BossTerm")
                    }
                    "Linux-deb" -> {
                        InstructionStep("Run in terminal:")
                        SelectableCommand("sudo dpkg -i ${downloadedFile.absolutePath}")
                    }
                    "Linux-rpm" -> {
                        InstructionStep("Run in terminal:")
                        SelectableCommand("sudo rpm -U ${downloadedFile.absolutePath}")
                    }
                    else -> {
                        InstructionStep("Run the downloaded file to install")
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    try {
                        Desktop.getDesktop().open(downloadedFile.parentFile)
                    } catch (e: Exception) {
                        // Ignore
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    backgroundColor = AccentColor,
                    contentColor = Color.White
                )
            ) {
                Text("Open Folder")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("Close", color = TextPrimary)
            }
        },
        backgroundColor = Color(0xFF2D2D2D)
    )
}

@Composable
private fun InstructionStep(text: String) {
    Text(
        text = text,
        color = TextSecondary,
        fontSize = 13.sp,
        modifier = Modifier.padding(vertical = 2.dp)
    )
}

@Composable
private fun SelectableCommand(command: String) {
    Text(
        text = command,
        color = AccentColor,
        fontSize = 12.sp,
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF1E1E1E), RoundedCornerShape(4.dp))
            .padding(8.dp)
    )
}
