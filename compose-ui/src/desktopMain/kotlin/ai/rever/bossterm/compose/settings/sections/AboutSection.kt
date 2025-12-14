package ai.rever.bossterm.compose.settings.sections

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ai.rever.bossterm.compose.settings.SettingsTheme.AccentColor
import ai.rever.bossterm.compose.settings.SettingsTheme.SurfaceColor
import ai.rever.bossterm.compose.settings.SettingsTheme.TextMuted
import ai.rever.bossterm.compose.settings.SettingsTheme.TextPrimary
import ai.rever.bossterm.compose.settings.SettingsTheme.TextSecondary
import ai.rever.bossterm.compose.settings.components.SettingsSection
import ai.rever.bossterm.compose.update.Version
import ai.rever.bossterm.compose.update.VersionManagementSection
import java.awt.Desktop
import java.net.URI

/**
 * About section: application info, system info, links, shortcuts, license, acknowledgments.
 */
@Composable
fun AboutSection(modifier: Modifier = Modifier) {
    val isMacOS = remember {
        System.getProperty("os.name")?.lowercase()?.contains("mac") == true
    }

    // Derive version display and release channel from Version.CURRENT
    val version = Version.CURRENT
    val versionDisplay = remember {
        val base = version.toString()
        // Show "dev" indicator if version is the fallback value (1.0.0)
        if (version.major == 1 && version.minor == 0 && version.patch == 0 && version.preRelease == null) {
            "$base (dev)"
        } else {
            base
        }
    }
    val releaseChannel = remember {
        version.preRelease?.lowercase() ?: "stable"
    }

    Column(modifier = modifier) {
        // Section 1: Application Info
        SettingsSection(title = "Application") {
            InfoRow("Version", versionDisplay)
            InfoRow("Release Channel", releaseChannel)
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Section 2: Version Management
        SettingsSection(title = "Version Management") {
            VersionManagementSection()
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Section 3: System Info
        SettingsSection(title = "System") {
            InfoRow("Operating System", "${System.getProperty("os.name")} ${System.getProperty("os.version")}")
            InfoRow("Architecture", System.getProperty("os.arch") ?: "Unknown")
            // Show Java runtime name (helps identify JBR, OpenJDK, Temurin, etc.)
            InfoRow("Java Runtime", System.getProperty("java.runtime.name") ?: "Unknown")
            InfoRow("Java Version", System.getProperty("java.version") ?: "Unknown")
            InfoRow("Java Vendor", System.getProperty("java.vendor") ?: "Unknown")
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Section 3: Links
        SettingsSection(title = "Links") {
            LinkRow("GitHub Repository", "https://github.com/kshivang/BossTerm")
            LinkRow("Release Notes", "https://github.com/kshivang/BossTerm/releases")
            LinkRow("Report an Issue", "https://github.com/kshivang/BossTerm/issues")
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Section 4: Keyboard Shortcuts
        SettingsSection(title = "Keyboard Shortcuts") {
            ShortcutTable(isMacOS)
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Section 5: License
        SettingsSection(title = "License") {
            Text(
                text = "BossTerm is dual licensed under Apache License 2.0 and GNU Lesser General Public License v3 (LGPLv3).",
                color = TextSecondary,
                fontSize = 13.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .background(SurfaceColor)
                    .padding(12.dp)
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Section 6: Acknowledgments
        SettingsSection(title = "Acknowledgments") {
            AcknowledgmentRow("JediTerm", "Terminal emulator foundation by JetBrains")
            AcknowledgmentRow("Compose Multiplatform", "UI framework by JetBrains")
            AcknowledgmentRow("ICU4J", "Unicode support library")
            AcknowledgmentRow("Meslo Nerd Font", "Terminal font with icon support")
        }
    }
}

/**
 * Displays a key-value info row.
 */
@Composable
private fun InfoRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(SurfaceColor)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = TextPrimary,
            fontSize = 13.sp
        )
        Text(
            text = value,
            color = TextSecondary,
            fontSize = 13.sp
        )
    }
}

/**
 * Displays a clickable link row that opens URL in browser.
 */
@Composable
private fun LinkRow(
    label: String,
    url: String,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(SurfaceColor)
            .hoverable(interactionSource)
            .clickable {
                try {
                    Desktop.getDesktop().browse(URI(url))
                } catch (e: Exception) {
                    System.err.println("Failed to open URL '$url': ${e.message}")
                }
            }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = TextPrimary,
            fontSize = 13.sp
        )
        Text(
            text = url.removePrefix("https://"),
            color = AccentColor,
            fontSize = 13.sp,
            textDecoration = if (isHovered) TextDecoration.Underline else TextDecoration.None
        )
    }
}

/**
 * Displays the keyboard shortcuts table.
 */
@Composable
private fun ShortcutTable(isMacOS: Boolean) {
    val modKey = if (isMacOS) "Cmd" else "Ctrl"

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(SurfaceColor)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // Tab Management
        ShortcutCategory("Tab Management")
        ShortcutRow("$modKey+T", "New Tab")
        ShortcutRow("$modKey+W", "Close Tab")
        ShortcutRow("$modKey+N", "New Window")
        ShortcutRow("Ctrl+Tab", "Next Tab")
        ShortcutRow("Ctrl+Shift+Tab", "Previous Tab")
        ShortcutRow("$modKey+1-9", "Jump to Tab")

        Spacer(modifier = Modifier.height(8.dp))

        // Editing
        ShortcutCategory("Editing")
        ShortcutRow("$modKey+C", "Copy")
        ShortcutRow("$modKey+V", "Paste")
        if (isMacOS) {
            ShortcutRow("Cmd+A", "Select All")
        }
        ShortcutRow("Escape", "Clear Selection")

        Spacer(modifier = Modifier.height(8.dp))

        // Tools
        ShortcutCategory("Tools")
        ShortcutRow("$modKey+F", "Search")
        ShortcutRow("$modKey+Shift+D", "Debug Panel")
        ShortcutRow("Ctrl+Space", "Toggle IME")
    }
}

/**
 * Category header for shortcuts table.
 */
@Composable
private fun ShortcutCategory(title: String) {
    Text(
        text = title,
        color = TextMuted,
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)
    )
}

/**
 * Single shortcut row.
 */
@Composable
private fun ShortcutRow(shortcut: String, action: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = shortcut,
            color = AccentColor,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium
        )
        Text(
            text = action,
            color = TextSecondary,
            fontSize = 12.sp
        )
    }
}

/**
 * Acknowledgment row with project name and description.
 */
@Composable
private fun AcknowledgmentRow(
    project: String,
    description: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(SurfaceColor)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = project,
            color = TextPrimary,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium
        )
        Text(
            text = description,
            color = TextMuted,
            fontSize = 12.sp
        )
    }
}
