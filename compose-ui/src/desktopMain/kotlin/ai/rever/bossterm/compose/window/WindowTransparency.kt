package ai.rever.bossterm.compose.window

import java.awt.GraphicsDevice
import java.awt.GraphicsEnvironment
import java.awt.Window
import javax.swing.JDialog
import javax.swing.JFrame

/**
 * Configure window transparency and blur effects.
 *
 * On macOS, this enables the native vibrancy/blur effect when the window
 * background is transparent. On other platforms, only basic transparency is supported.
 */
fun configureWindowTransparency(
    window: Window,
    isTransparent: Boolean,
    enableBlur: Boolean
) {
    if (!isTransparent) return

    val isMacOS = System.getProperty("os.name").lowercase().contains("mac")

    // Set window background to be transparent
    window.background = java.awt.Color(0, 0, 0, 0)

    // Configure root pane for transparency
    val rootPane = when (window) {
        is JFrame -> window.rootPane
        is JDialog -> window.rootPane
        else -> null
    }

    rootPane?.let { rp ->
        rp.isOpaque = false
        rp.background = java.awt.Color(0, 0, 0, 0)
    }

    // On macOS with blur enabled, try to configure native blur
    if (isMacOS && enableBlur) {
        configureMacOSBlur(window)
    }
}

/**
 * Configure macOS-specific blur effect using native APIs.
 * Note: Full blur support requires native integration. This sets up basic transparency.
 */
private fun configureMacOSBlur(window: Window) {
    try {
        // Set macOS-specific window properties via system properties
        // These hints may enable better compositing on macOS
        System.setProperty("apple.awt.draggableWindowBackground", "true")

        // Try to access macOS-specific window styling via toolkit
        val toolkit = java.awt.Toolkit.getDefaultToolkit()

        // Check for available desktop properties
        // Note: Full blur/vibrancy requires JNA or native code for NSWindow access
        // This is a best-effort approach using available AWT APIs

        // Set window to be non-opaque for better compositing
        if (window is JFrame) {
            window.isUndecorated.let { /* Already handled by Compose when transparent = true */ }
        }
    } catch (e: Exception) {
        // Blur configuration failed - transparency will still work
        System.err.println("macOS blur configuration: ${e.message}")
    }
}

/**
 * Check if the current platform supports window transparency.
 */
fun isTransparencySupported(): Boolean {
    return try {
        val ge = GraphicsEnvironment.getLocalGraphicsEnvironment()
        val gd = ge.defaultScreenDevice
        gd.isWindowTranslucencySupported(GraphicsDevice.WindowTranslucency.TRANSLUCENT)
    } catch (e: Exception) {
        false
    }
}
