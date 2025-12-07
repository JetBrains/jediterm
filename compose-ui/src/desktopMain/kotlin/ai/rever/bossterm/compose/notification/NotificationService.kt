package ai.rever.bossterm.compose.notification

import com.sun.jna.*
import com.sun.jna.ptr.PointerByReference
import org.slf4j.LoggerFactory
import java.io.IOException

/**
 * Service for displaying system notifications.
 * Uses native macOS notifications via Objective-C runtime (JNA).
 * Falls back to osascript if native approach fails.
 */
object NotificationService {
    private val LOG = LoggerFactory.getLogger(NotificationService::class.java)
    private val isMacOS = System.getProperty("os.name").lowercase().contains("mac")

    /**
     * Objective-C runtime interface with specific method signatures.
     * Using specific signatures instead of varargs for stability.
     */
    private interface ObjC : Library {
        fun objc_getClass(name: String): Pointer?
        fun sel_registerName(name: String): Pointer?

        // No-arg message send
        fun objc_msgSend(receiver: Pointer?, selector: Pointer?): Pointer?

        companion object {
            val INSTANCE: ObjC? = try {
                Native.load("objc", ObjC::class.java)
            } catch (e: Throwable) {
                null
            }
        }
    }

    /**
     * Separate interface for message sends with one pointer argument.
     * JNA needs different interfaces for different signatures.
     */
    private interface ObjCWithPointerArg : Library {
        fun objc_msgSend(receiver: Pointer?, selector: Pointer?, arg: Pointer?): Pointer?

        companion object {
            val INSTANCE: ObjCWithPointerArg? = try {
                Native.load("objc", ObjCWithPointerArg::class.java)
            } catch (e: Throwable) {
                null
            }
        }
    }

    /**
     * Interface for creating NSString from C string.
     */
    private interface ObjCWithStringArg : Library {
        fun objc_msgSend(receiver: Pointer?, selector: Pointer?, arg: String?): Pointer?

        companion object {
            val INSTANCE: ObjCWithStringArg? = try {
                Native.load("objc", ObjCWithStringArg::class.java)
            } catch (e: Throwable) {
                null
            }
        }
    }

    /**
     * Display a system notification.
     */
    fun showNotification(
        title: String,
        message: String,
        subtitle: String? = null,
        withSound: Boolean = true
    ) {
        if (!isMacOS) {
            LOG.info("Notification (non-macOS): $title - $message")
            return
        }

        // Try native notification first, fall back to osascript
        val nativeSuccess = try {
            showNativeNotification(title, message, subtitle, withSound)
        } catch (e: Throwable) {
            LOG.debug("Native notification failed: ${e.message}", e)
            false
        }

        if (!nativeSuccess) {
            showOsascriptNotification(title, message, subtitle, withSound)
        }
    }

    /**
     * Show notification using native Objective-C runtime.
     */
    private fun showNativeNotification(
        title: String,
        message: String,
        subtitle: String?,
        withSound: Boolean
    ): Boolean {
        val objc = ObjC.INSTANCE ?: return false
        val objcPtr = ObjCWithPointerArg.INSTANCE ?: return false
        val objcStr = ObjCWithStringArg.INSTANCE ?: return false

        // Get classes
        val nsUserNotificationClass = objc.objc_getClass("NSUserNotification") ?: return false
        val nsUserNotificationCenterClass = objc.objc_getClass("NSUserNotificationCenter") ?: return false
        val nsStringClass = objc.objc_getClass("NSString") ?: return false

        // Get selectors
        val allocSel = objc.sel_registerName("alloc") ?: return false
        val initSel = objc.sel_registerName("init") ?: return false
        val setTitleSel = objc.sel_registerName("setTitle:") ?: return false
        val setInformativeTextSel = objc.sel_registerName("setInformativeText:") ?: return false
        val setSubtitleSel = objc.sel_registerName("setSubtitle:") ?: return false
        val setSoundNameSel = objc.sel_registerName("setSoundName:") ?: return false
        val defaultCenterSel = objc.sel_registerName("defaultUserNotificationCenter") ?: return false
        val deliverSel = objc.sel_registerName("deliverNotification:") ?: return false
        val stringWithUTF8Sel = objc.sel_registerName("stringWithUTF8String:") ?: return false

        // Helper to create NSString
        fun createNSString(str: String): Pointer? {
            return objcStr.objc_msgSend(nsStringClass, stringWithUTF8Sel, str)
        }

        // Create notification: [[NSUserNotification alloc] init]
        val allocated = objc.objc_msgSend(nsUserNotificationClass, allocSel) ?: return false
        val notification = objc.objc_msgSend(allocated, initSel) ?: return false

        // Set title
        val titleNS = createNSString(title) ?: return false
        objcPtr.objc_msgSend(notification, setTitleSel, titleNS)

        // Set message (informativeText)
        val messageNS = createNSString(message) ?: return false
        objcPtr.objc_msgSend(notification, setInformativeTextSel, messageNS)

        // Set subtitle if provided
        if (subtitle != null) {
            val subtitleNS = createNSString(subtitle) ?: return false
            objcPtr.objc_msgSend(notification, setSubtitleSel, subtitleNS)
        }

        // Set sound if enabled
        if (withSound) {
            val soundNS = createNSString("NSUserNotificationDefaultSoundName") ?: return false
            objcPtr.objc_msgSend(notification, setSoundNameSel, soundNS)
        }

        // Get default notification center and deliver
        val center = objc.objc_msgSend(nsUserNotificationCenterClass, defaultCenterSel) ?: return false
        objcPtr.objc_msgSend(center, deliverSel, notification)

        LOG.debug("Native notification displayed: $title - $message")
        return true
    }

    /**
     * Fallback: Display notification using macOS osascript.
     */
    private fun showOsascriptNotification(
        title: String,
        message: String,
        subtitle: String?,
        withSound: Boolean
    ) {
        try {
            val escapedTitle = title.replace("\"", "\\\"")
            val escapedMessage = message.replace("\"", "\\\"")
            val escapedSubtitle = subtitle?.replace("\"", "\\\"")

            val script = buildString {
                append("display notification \"$escapedMessage\"")
                append(" with title \"$escapedTitle\"")
                if (escapedSubtitle != null) {
                    append(" subtitle \"$escapedSubtitle\"")
                }
                if (withSound) {
                    append(" sound name \"default\"")
                }
            }

            val process = ProcessBuilder("osascript", "-e", script)
                .redirectErrorStream(true)
                .start()

            val exitCode = process.waitFor()
            if (exitCode != 0) {
                val error = process.inputStream.bufferedReader().readText()
                LOG.warn("osascript notification failed (exit $exitCode): $error")
            } else {
                LOG.debug("osascript notification displayed: $title - $message")
            }
        } catch (e: IOException) {
            LOG.error("Failed to display notification", e)
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            LOG.warn("Notification interrupted", e)
        }
    }
}
