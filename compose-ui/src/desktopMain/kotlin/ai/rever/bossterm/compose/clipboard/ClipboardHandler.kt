package ai.rever.bossterm.compose.clipboard

import ai.rever.bossterm.compose.settings.TerminalSettings
import ai.rever.bossterm.terminal.TerminalClipboardListener
import org.slf4j.LoggerFactory
import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.StringSelection

/**
 * Handler for OSC 52 clipboard operations.
 * Implements security controls based on settings.
 */
class ClipboardHandler(
    private val settings: TerminalSettings
) : TerminalClipboardListener {

    companion object {
        private val LOG = LoggerFactory.getLogger(ClipboardHandler::class.java)
    }

    override fun onClipboardSet(selection: Char, content: String) {
        if (!settings.clipboardOsc52Enabled || !settings.clipboardOsc52AllowWrite) {
            LOG.debug("OSC 52 clipboard write blocked by settings")
            return
        }

        try {
            val clipboard = Toolkit.getDefaultToolkit().systemClipboard
            clipboard.setContents(StringSelection(content), null)
            LOG.debug("OSC 52: Set clipboard content (${content.length} chars)")
        } catch (e: Exception) {
            LOG.error("Failed to set clipboard content", e)
        }
    }

    override fun onClipboardGet(selection: Char): String? {
        if (!settings.clipboardOsc52Enabled || !settings.clipboardOsc52AllowRead) {
            LOG.debug("OSC 52 clipboard read blocked by settings")
            return null
        }

        return try {
            val clipboard = Toolkit.getDefaultToolkit().systemClipboard
            if (clipboard.isDataFlavorAvailable(DataFlavor.stringFlavor)) {
                val content = clipboard.getData(DataFlavor.stringFlavor) as String
                LOG.debug("OSC 52: Get clipboard content (${content.length} chars)")
                content
            } else {
                LOG.debug("OSC 52: Clipboard does not contain string data")
                null
            }
        } catch (e: Exception) {
            LOG.error("Failed to get clipboard content", e)
            null
        }
    }

    override fun onClipboardClear(selection: Char) {
        if (!settings.clipboardOsc52Enabled || !settings.clipboardOsc52AllowWrite) {
            LOG.debug("OSC 52 clipboard clear blocked by settings")
            return
        }

        try {
            val clipboard = Toolkit.getDefaultToolkit().systemClipboard
            clipboard.setContents(StringSelection(""), null)
            LOG.debug("OSC 52: Cleared clipboard")
        } catch (e: Exception) {
            LOG.error("Failed to clear clipboard", e)
        }
    }
}
