package org.jetbrains.jediterm.compose.hyperlinks

import androidx.compose.ui.geometry.Rect

/**
 * Callback interface for hyperlink hover events.
 * External clients can implement this to receive hover boundary notifications
 * for tooltip positioning or custom hover effects.
 */
interface HyperlinkHoverConsumer {
    /**
     * Called when the mouse cursor enters a hyperlink's bounds.
     * @param bounds Link's bounds in pixel coordinates relative to terminal canvas
     * @param url The URL of the hovered hyperlink
     */
    fun onMouseEntered(bounds: Rect, url: String)

    /**
     * Called when the mouse cursor exits the hyperlink's bounds.
     */
    fun onMouseExited()
}
