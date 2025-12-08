package ai.rever.bossterm.compose.window

import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.platform.mac.CoreFoundation
import java.awt.GraphicsDevice
import java.awt.GraphicsEnvironment
import java.awt.Window
import javax.swing.JDialog
import javax.swing.JFrame
import javax.swing.SwingUtilities

/**
 * Native macOS Objective-C runtime interface via JNA.
 */
private interface ObjectiveC : Library {
    companion object {
        val INSTANCE: ObjectiveC? = try {
            Native.load("objc", ObjectiveC::class.java)
        } catch (e: Exception) {
            null
        }
    }

    fun objc_getClass(name: String): Pointer?
    fun sel_registerName(name: String): Pointer?
    fun objc_msgSend(receiver: Pointer?, selector: Pointer?, vararg args: Any?): Pointer?
    fun objc_msgSend_stret(result: Pointer?, receiver: Pointer?, selector: Pointer?, vararg args: Any?)
}

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

    // NOTE: Native blur requires a Swift/ObjC helper library - not currently supported
    // The JNA approach has compatibility issues with modern macOS/Java
    // configureMacOSBlur(window) // Disabled due to JNA compatibility issues
}

/**
 * Configure macOS-specific blur effect using NSVisualEffectView.
 * This creates the frosted glass effect like iTerm2.
 */
private fun configureMacOSBlur(window: Window) {
    SwingUtilities.invokeLater {
        try {
            val objc = ObjectiveC.INSTANCE ?: return@invokeLater

            // Try to get NSWindow from AWT window
            var nsWindow = getNSWindow(window)

            // If direct approach failed, try via NSApplication
            if (nsWindow == null) {
                nsWindow = findNSWindowByTitle(objc, window.name ?: "BossTerm")
            }

            if (nsWindow == null) {
                System.err.println("Could not find NSWindow for blur effect")
                return@invokeLater
            }

            // Get the content view
            val contentViewSel = objc.sel_registerName("contentView")
            val contentView = objc.objc_msgSend(nsWindow, contentViewSel) ?: return@invokeLater

            // Create NSVisualEffectView
            val visualEffectClass = objc.objc_getClass("NSVisualEffectView") ?: return@invokeLater
            val allocSel = objc.sel_registerName("alloc")
            val initSel = objc.sel_registerName("init")

            val visualEffectView = objc.objc_msgSend(visualEffectClass, allocSel)
            objc.objc_msgSend(visualEffectView, initSel)

            if (visualEffectView != null) {
                // Set material to dark (for terminal-like appearance)
                // NSVisualEffectMaterialDark = 4
                val setMaterialSel = objc.sel_registerName("setMaterial:")
                objc.objc_msgSend(visualEffectView, setMaterialSel, 4L)

                // Set blending mode to behindWindow
                val setBlendingModeSel = objc.sel_registerName("setBlendingMode:")
                objc.objc_msgSend(visualEffectView, setBlendingModeSel, 0L)

                // Set state to active
                val setStateSel = objc.sel_registerName("setState:")
                objc.objc_msgSend(visualEffectView, setStateSel, 1L)

                // Get content view's frame
                val frameSel = objc.sel_registerName("frame")
                val bounds = objc.objc_msgSend(contentView, frameSel)

                // Set frame on visual effect view
                val setFrameSel = objc.sel_registerName("setFrame:")
                if (bounds != null) {
                    objc.objc_msgSend(visualEffectView, setFrameSel, bounds)
                }

                // Set autoresizing mask to fill container
                val setAutoresizingMaskSel = objc.sel_registerName("setAutoresizingMask:")
                objc.objc_msgSend(visualEffectView, setAutoresizingMaskSel, 18L)

                // Add as subview at index 0 (behind all content)
                val insertSubviewAtIndexSel = objc.sel_registerName("addSubview:positioned:relativeTo:")
                objc.objc_msgSend(contentView, insertSubviewAtIndexSel, visualEffectView, -1L, null)

                println("macOS blur effect configured successfully")
            }
        } catch (e: Exception) {
            System.err.println("macOS blur configuration failed: ${e.message}")
        }
    }
}

/**
 * Find NSWindow by searching through NSApplication's windows.
 */
private fun findNSWindowByTitle(objc: ObjectiveC, title: String): Pointer? {
    return try {
        // Get NSApplication shared instance
        val nsAppClass = objc.objc_getClass("NSApplication") ?: return null
        val sharedAppSel = objc.sel_registerName("sharedApplication")
        val nsApp = objc.objc_msgSend(nsAppClass, sharedAppSel) ?: return null

        // Get windows array
        val windowsSel = objc.sel_registerName("windows")
        val windowsArray = objc.objc_msgSend(nsApp, windowsSel) ?: return null

        // Get count
        val countSel = objc.sel_registerName("count")
        val count = objc.objc_msgSend(windowsArray, countSel)
        val windowCount = Pointer.nativeValue(count).toInt()

        // Iterate through windows
        val objectAtIndexSel = objc.sel_registerName("objectAtIndex:")
        val titleSel = objc.sel_registerName("title")
        val utf8StringSel = objc.sel_registerName("UTF8String")

        for (i in 0 until windowCount) {
            val nsWindow = objc.objc_msgSend(windowsArray, objectAtIndexSel, i.toLong()) ?: continue
            val nsTitle = objc.objc_msgSend(nsWindow, titleSel)
            if (nsTitle != null) {
                val titleCStr = objc.objc_msgSend(nsTitle, utf8StringSel)
                if (titleCStr != null) {
                    val windowTitle = titleCStr.getString(0)
                    if (windowTitle.contains(title) || title.contains(windowTitle)) {
                        return nsWindow
                    }
                }
            }
        }
        null
    } catch (e: Exception) {
        System.err.println("findNSWindowByTitle failed: ${e.message}")
        null
    }
}

/**
 * Get the native NSWindow pointer from an AWT Window.
 * Tries multiple approaches for different Java versions.
 */
private fun getNSWindow(window: Window): Pointer? {
    return try {
        // Try to find the NSWindow pointer using various approaches

        // Approach 1: Try through component peer (older Java)
        try {
            val peerField = java.awt.Component::class.java.getDeclaredField("peer")
            peerField.isAccessible = true
            val peer = peerField.get(window)
            if (peer != null) {
                val platformWindowMethod = peer.javaClass.getMethod("getPlatformWindow")
                val platformWindow = platformWindowMethod.invoke(peer)
                if (platformWindow != null) {
                    val getNSWindowPtrMethod = platformWindow.javaClass.getMethod("getNSWindowPtr")
                    val nsWindowPtr = getNSWindowPtrMethod.invoke(platformWindow) as Long
                    if (nsWindowPtr != 0L) {
                        return Pointer(nsWindowPtr)
                    }
                }
            }
        } catch (e: Exception) {
            // Try next approach
        }

        // Approach 2: Try LWWindowPeer directly
        try {
            val lwWindowPeerClass = Class.forName("sun.lwawt.LWWindowPeer")
            val getPeerMethod = java.awt.Component::class.java.getDeclaredMethod("getPeer")
            getPeerMethod.isAccessible = true
            val peer = getPeerMethod.invoke(window)
            if (peer != null && lwWindowPeerClass.isInstance(peer)) {
                val getPlatformWindowMethod = lwWindowPeerClass.getMethod("getPlatformWindow")
                val platformWindow = getPlatformWindowMethod.invoke(peer)
                if (platformWindow != null) {
                    val getNSWindowPtrMethod = platformWindow.javaClass.getMethod("getNSWindowPtr")
                    val nsWindowPtr = getNSWindowPtrMethod.invoke(platformWindow) as Long
                    if (nsWindowPtr != 0L) {
                        return Pointer(nsWindowPtr)
                    }
                }
            }
        } catch (e: Exception) {
            // Try next approach
        }

        // Approach 3: Use toolkit to get window ID
        try {
            val toolkit = java.awt.Toolkit.getDefaultToolkit()
            val getWindowPeerMethod = toolkit.javaClass.getDeclaredMethod("getWindowPeer", Window::class.java)
            getWindowPeerMethod.isAccessible = true
            val peer = getWindowPeerMethod.invoke(toolkit, window)
            if (peer != null) {
                val getNSWindowPtrMethod = peer.javaClass.getMethod("getNSWindowPtr")
                val nsWindowPtr = getNSWindowPtrMethod.invoke(peer) as Long
                if (nsWindowPtr != 0L) {
                    return Pointer(nsWindowPtr)
                }
            }
        } catch (e: Exception) {
            // All approaches failed
        }

        System.err.println("Could not get NSWindow pointer - blur effect unavailable")
        null
    } catch (e: Exception) {
        System.err.println("Failed to get NSWindow: ${e.message}")
        null
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
