/**
 *
 */
package com.jediterm.terminal.model

import com.jediterm.terminal.TextStyle
import com.jediterm.terminal.emulator.charset.CharacterSet
import com.jediterm.terminal.emulator.charset.GraphicSetState

class StoredCursor(//Cursor position
    val cursorX: Int,
    val cursorY: Int,
    //Character attributes set by the SGR command
    val textStyle: TextStyle,
    //Wrap ﬂag (autowrap or no autowrap)
    val isAutoWrap: Boolean,
    //State of origin mode (DECOM)
    val isOriginMode: Boolean,
    graphicSetState: GraphicSetState
) {
    //Character sets (G0, G1, G2, or G3) currently in GL and GR
    val gLMapping: Int
    val gRMapping: Int

    //Selective erase attribute
    //Any single shift 2 (SS2) or single shift 3 (SS3) functions sent
    val gLOverride: Int

    val designations: Array<CharacterSet?> = arrayOfNulls<CharacterSet>(4)

    init {
        this.gLMapping = graphicSetState.gL.index
        this.gRMapping = graphicSetState.gR.index
        this.gLOverride = graphicSetState.gLOverrideIndex
        for (i in 0..3) {
            this.designations[i] = graphicSetState.getGraphicSet(i).designation
        }
    }
}