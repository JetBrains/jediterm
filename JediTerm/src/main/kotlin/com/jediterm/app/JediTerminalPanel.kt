package com.jediterm.app

import com.intellij.openapi.Disposable
import com.intellij.util.JBHiDPIScaledImage
import com.intellij.util.RetinaImage
import com.intellij.util.ui.DrawUtil
import com.jediterm.terminal.model.StyleState
import com.jediterm.terminal.model.TerminalTextBuffer
import com.jediterm.terminal.ui.TerminalPanel
import com.jediterm.terminal.ui.UIUtil
import com.jediterm.terminal.ui.settings.SettingsProvider
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.Image
import java.awt.image.BufferedImage
import java.awt.image.ImageObserver


class JediTerminalPanel(private val mySettingsProvider: SettingsProvider,
                        styleState: StyleState,
                        backBuffer: TerminalTextBuffer) : TerminalPanel(mySettingsProvider, backBuffer, styleState), Disposable {
    override fun dispose() {
        //TODO
    }

    override fun setupAntialiasing(graphics: Graphics) {
        DrawUtil.setupComposite(graphics as Graphics2D)
        UIUtil.applyRenderingHints(graphics)
    }

    override fun drawImage(gfx: Graphics2D, image: BufferedImage, x: Int, y: Int, observer: ImageObserver) {
        DrawUtil.drawImage(gfx, image, x, y, observer)
    }

    override fun drawImage(g: Graphics2D, image: BufferedImage, dx1: Int, dy1: Int, dx2: Int, dy2: Int, sx1: Int, sy1: Int, sx2: Int, sy2: Int) {
        drawImage(g, image, dx1, dy1, dx2, dy2, sx1, sy1, sx2, sy2, null)
    }

    override fun isRetina(): Boolean {
        return DrawUtil.isRetina()
    }


    override fun createBufferedImage(width: Int, height: Int): BufferedImage {
        return DrawUtil.createImage(width, height, BufferedImage.TYPE_INT_ARGB)
    }


    companion object {
        fun drawImage(g: Graphics,
                      image: Image,
                      dx1: Int,
                      dy1: Int,
                      dx2: Int,
                      dy2: Int,
                      sx1: Int,
                      sy1: Int,
                      sx2: Int,
                      sy2: Int,
                      observer: ImageObserver?) {
            if (image is JBHiDPIScaledImage) {
                val newG = g.create(0, 0, image.getWidth(observer), image.getHeight(observer)) as Graphics2D
                newG.scale(0.5, 0.5)
                var img = image.getDelegate()
                if (img == null) {
                    img = image
                }
                newG.drawImage(img, 2 * dx1, 2 * dy1, 2 * dx2, 2 * dy2, sx1 * 2, sy1 * 2, sx2 * 2, sy2 * 2, observer)
                newG.scale(1.0, 1.0)
                newG.dispose()
            } else if (RetinaImage.isAppleHiDPIScaledImage(image)) {
                g.drawImage(image, dx1, dy1, dx2, dy2, sx1 * 2, sy1 * 2, sx2 * 2, sy2 * 2, observer)
            } else {
                g.drawImage(image, dx1, dy1, dx2, dy2, sx1, sy1, sx2, sy2, observer)
            }
        }

    }
}


