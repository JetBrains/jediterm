package ai.rever.bossterm.terminal.model.image

/**
 * Listener interface for terminal image events.
 * Implementations handle image placement and removal notifications.
 */
interface TerminalImageListener {
    /**
     * Called when a new image is placed in the terminal.
     *
     * @param placement The image placement details including position and dimensions
     */
    fun onImageAdded(placement: TerminalImagePlacement)

    /**
     * Called when an image is removed from the terminal
     * (e.g., scrolled out of history buffer, or explicitly cleared).
     *
     * @param imageId The ID of the removed image
     */
    fun onImageRemoved(imageId: Long)

    /**
     * Called when all images are cleared (e.g., terminal reset).
     */
    fun onAllImagesCleared()

    /**
     * Called when images need to be updated due to terminal resize.
     * Images may need recalculation of their cell dimensions.
     *
     * @param placements Updated list of all current image placements
     */
    fun onImagesUpdated(placements: List<TerminalImagePlacement>)
}
