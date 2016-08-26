/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.util.ui;

import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import com.google.common.collect.Maps;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.ui.GraphicsConfig;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.ui.AntialiasingType;
import com.intellij.util.Consumer;
import com.intellij.util.JBHiDPIScaledImage;
import com.intellij.util.ReflectionUtil;
import com.intellij.util.RetinaImage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import sun.swing.SwingUtilities2;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.image.BufferedImageOp;
import java.awt.image.ImageObserver;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.Optional;

public class DrawUtil {
    protected static final Logger LOG = Logger.getInstance("#com.intellij.util.ui.UIUtil");
    private static final Ref<Boolean> ourRetina = Ref.create(SystemInfo.isMac ? null : false);

    /**
     * Draws two horizontal lines, the first at {@code topY}, the second at {@code bottomY}.
     * The purpose of this method (and the ground of the name) is to draw two lines framing a horizontal filled rectangle.
     *
     * @param g       Graphics context to draw with.
     * @param startX  x-start point.
     * @param endX    x-end point.
     * @param topY    y-coordinate of the first line.
     * @param bottomY y-coordinate of the second line.
     * @param color   color of the lines.
     */
    public static void drawFramingLines(@NotNull Graphics2D g, int startX, int endX, int topY, int bottomY, @NotNull Color color) {
        drawLine(g, startX, topY, endX, topY, null, color);
        drawLine(g, startX, bottomY, endX, bottomY, null, color);
    }

    public static boolean isRetina(Graphics2D graphics) {
        if (SystemInfo.isMac && SystemInfo.isJavaVersionAtLeast("1.7")) {
            return DetectRetinaKit.isMacRetina(graphics);
        } else {
            return isRetina();
        }
    }

    public static boolean isRetina() {
        if (GraphicsEnvironment.isHeadless()) return false;

        //Temporary workaround for HiDPI on Windows/Linux
        if ("true".equalsIgnoreCase(System.getProperty("is.hidpi"))) {
            return true;
        }

        if (Registry.is("new.retina.detection")) {
            return DetectRetinaKit.isRetina();
        } else {
            synchronized (ourRetina) {
                if (ourRetina.isNull()) {
                    ourRetina.set(false); // in case HiDPIScaledImage.drawIntoImage is not called for some reason

                    if (SystemInfo.isJavaVersionAtLeast("1.6.0_33") && SystemInfo.isAppleJvm) {
                        if (!"false".equals(System.getProperty("ide.mac.retina"))) {
                            ourRetina.set(IsRetina.isRetina());
                            return ourRetina.get();
                        }
                    } else if (SystemInfo.isJavaVersionAtLeast("1.7.0_40") /*&& !SystemInfo.isOracleJvm*/) {
                        try {
                            GraphicsEnvironment env = GraphicsEnvironment.getLocalGraphicsEnvironment();
                            final GraphicsDevice device = env.getDefaultScreenDevice();
                            Integer scale = ReflectionUtil.getField(device.getClass(), device, int.class, "scale");
                            if (scale != null && scale.intValue() == 2) {
                                ourRetina.set(true);
                                return true;
                            }
                        } catch (AWTError ignore) {
                        } catch (Exception ignore) {
                        }
                    }
                    ourRetina.set(false);
                }

                return ourRetina.get();
            }
        }
    }

    public static void drawLinePickedOut(Graphics graphics, int x, int y, int x1, int y1) {
        if (x == x1) {
            int minY = Math.min(y, y1);
            int maxY = Math.max(y, y1);
            graphics.drawLine(x, minY + 1, x1, maxY - 1);
        } else if (y == y1) {
            int minX = Math.min(x, x1);
            int maxX = Math.max(x, x1);
            graphics.drawLine(minX + 1, y, maxX - 1, y1);
        } else {
            drawLine(graphics, x, y, x1, y1);
        }
    }

    public static void drawLine(Graphics g, int x1, int y1, int x2, int y2) {
        g.drawLine(x1, y1, x2, y2);
    }

    public static void drawLine(Graphics2D g, int x1, int y1, int x2, int y2, @Nullable Color bgColor, @Nullable Color fgColor) {
        Color oldFg = g.getColor();
        Color oldBg = g.getBackground();
        if (fgColor != null) {
            g.setColor(fgColor);
        }
        if (bgColor != null) {
            g.setBackground(bgColor);
        }
        drawLine(g, x1, y1, x2, y2);
        if (fgColor != null) {
            g.setColor(oldFg);
        }
        if (bgColor != null) {
            g.setBackground(oldBg);
        }
    }


    public static Color getPanelBackground() {
        return UIManager.getColor("Panel.background");
    }

    @SuppressWarnings({"HardCodedStringLiteral"})
    public static boolean isUnderDarcula() {
        return UIManager.getLookAndFeel().getName().contains("Darcula");
    }

    @SuppressWarnings({"HardCodedStringLiteral"})
    public static boolean isUnderIntelliJLaF() {
        return UIManager.getLookAndFeel().getName().contains("IntelliJ");
    }

    /**
     * @param g  graphics.
     * @param x  top left X coordinate.
     * @param y  top left Y coordinate.
     * @param x1 right bottom X coordinate.
     * @param y1 right bottom Y coordinate.
     */
    public static void drawDottedRectangle(Graphics g, int x, int y, int x1, int y1) {
        int i1;
        for (i1 = x; i1 <= x1; i1 += 2) {
            drawLine(g, i1, y, i1, y);
        }

        for (i1 = i1 != x1 + 1 ? y + 2 : y + 1; i1 <= y1; i1 += 2) {
            drawLine(g, x1, i1, x1, i1);
        }

        for (i1 = i1 != y1 + 1 ? x1 - 2 : x1 - 1; i1 >= x; i1 -= 2) {
            drawLine(g, i1, y1, i1, y1);
        }

        for (i1 = i1 != x - 1 ? y1 - 2 : y1 - 1; i1 >= y; i1 -= 2) {
            drawLine(g, x, i1, x, i1);
        }
    }

    /**
     * Should be invoked only in EDT.
     *
     * @param g       Graphics surface
     * @param startX  Line start X coordinate
     * @param endX    Line end X coordinate
     * @param lineY   Line Y coordinate
     * @param bgColor Background color (optional)
     * @param fgColor Foreground color (optional)
     * @param opaque  If opaque the image will be dr
     */
    public static void drawBoldDottedLine(final Graphics2D g,
                                          final int startX,
                                          final int endX,
                                          final int lineY,
                                          final Color bgColor,
                                          final Color fgColor,
                                          final boolean opaque) {
        if ((SystemInfo.isMac && !isRetina()) || SystemInfo.isLinux) {
            drawAppleDottedLine(g, startX, endX, lineY, bgColor, fgColor, opaque);
        }
        else {
            drawBoringDottedLine(g, startX, endX, lineY, bgColor, fgColor, opaque);
        }
    }

    private static void drawAppleDottedLine(final Graphics2D g,
                                            final int startX,
                                            final int endX,
                                            final int lineY,
                                            final Color bgColor,
                                            final Color fgColor,
                                            final boolean opaque) {
        final Color oldColor = g.getColor();

        // Fill 3 lines with background color
        if (opaque && bgColor != null) {
            g.setColor(bgColor);

            drawLine(g, startX, lineY, endX, lineY);
            drawLine(g, startX, lineY + 1, endX, lineY + 1);
            drawLine(g, startX, lineY + 2, endX, lineY + 2);
        }

        AppleBoldDottedPainter painter = AppleBoldDottedPainter.forColor(Optional.of(fgColor).orElse(oldColor));
        painter.paint(g, startX, endX, lineY);
    }


    public static void drawSearchMatch(final Graphics2D g,
                                       final int startX,
                                       final int endX,
                                       final int height) {
        Color c1 = new Color(255, 234, 162);
        Color c2 = new Color(255, 208, 66);
        drawSearchMatch(g, startX, endX, height, c1, c2);
    }

    public static void drawSearchMatch(Graphics2D g, int startX, int endX, int height, Color c1, Color c2) {
        final boolean drawRound = endX - startX > 4;

        final Composite oldComposite = g.getComposite();
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.7f));
        g.setPaint(getGradientPaint(startX, 2, c1, startX, height - 5, c2));

        if (isRetina()) {
            g.fillRoundRect(startX - 1, 2, endX - startX + 1, height - 4, 5, 5);
            g.setComposite(oldComposite);
            return;
        }

        g.fillRect(startX, 3, endX - startX, height - 5);

        if (drawRound) {
            g.drawLine(startX - 1, 4, startX - 1, height - 4);
            g.drawLine(endX, 4, endX, height - 4);

            g.setColor(new Color(100, 100, 100, 50));
            g.drawLine(startX - 1, 4, startX - 1, height - 4);
            g.drawLine(endX, 4, endX, height - 4);

            g.drawLine(startX, 3, endX - 1, 3);
            g.drawLine(startX, height - 3, endX - 1, height - 3);
        }

        g.setComposite(oldComposite);
    }

    public static void drawRectPickedOut(Graphics2D g, int x, int y, int w, int h) {
        g.drawLine(x + 1, y, x + w - 1, y);
        g.drawLine(x + w, y + 1, x + w, y + h - 1);
        g.drawLine(x + w - 1, y + h, x + 1, y + h);
        g.drawLine(x, y + 1, x, y + h - 1);
    }

    private static void drawBoringDottedLine(final Graphics2D g,
                                             final int startX,
                                             final int endX,
                                             final int lineY,
                                             final Color bgColor,
                                             final Color fgColor,
                                             final boolean opaque) {
        final Color oldColor = g.getColor();

        // Fill 2 lines with background color
        if (opaque && bgColor != null) {
            g.setColor(bgColor);

            drawLine(g, startX, lineY, endX, lineY);
            drawLine(g, startX, lineY + 1, endX, lineY + 1);
        }

        // Draw dotted line:
        //
        // CCC CCC CCC ...
        // CCC CCC CCC ...
        //
        // (where "C" - colored pixel, " " - white pixel)

        final int step = 4;
        final int startPosCorrection = startX % step < 3 ? 0 : 1;

        g.setColor(fgColor != null ? fgColor : oldColor);
        // Now draw bold line segments
        for (int dotXi = (startX / step + startPosCorrection) * step; dotXi < endX; dotXi += step) {
            g.drawLine(dotXi, lineY, dotXi + 1, lineY);
            g.drawLine(dotXi, lineY + 1, dotXi + 1, lineY + 1);
        }

        // restore color
        g.setColor(oldColor);
    }

    public static void drawGradientHToolbarBackground(final Graphics g, final int width, final int height) {
        final Graphics2D g2d = (Graphics2D) g;
        g2d.setPaint(getGradientPaint(0, 0, Gray._215, 0, height, Gray._200));
        g2d.fillRect(0, 0, width, height);
    }

    public static void drawHeader(Graphics g, int x, int width, int height, boolean active, boolean drawTopLine) {
        drawHeader(g, x, width, height, active, false, drawTopLine, true);
    }

    public static void drawHeader(Graphics g,
                                  int x,
                                  int width,
                                  int height,
                                  boolean active,
                                  boolean toolWindow,
                                  boolean drawTopLine,
                                  boolean drawBottomLine) {
        height++;
        GraphicsConfig config = GraphicsUtil.disableAAPainting(g);
        try {
            g.setColor(getPanelBackground());
            g.fillRect(x, 0, width, height);

            if (isRetina()) {
                ((Graphics2D) g).setStroke(new BasicStroke(2f));
            }
            ((Graphics2D) g).setPaint(getGradientPaint(0, 0, Gray.x00.withAlpha(5), 0, height, Gray.x00.withAlpha(20)));
            g.fillRect(x, 0, width, height);

            if (active) {
                g.setColor(new Color(100, 150, 230, toolWindow ? 50 : 30));
                g.fillRect(x, 0, width, height);
            }
            g.setColor(SystemInfo.isMac && isUnderIntelliJLaF() ? Gray.xC9 : Gray.x00.withAlpha(toolWindow ? 90 : 50));
            if (drawTopLine) g.drawLine(x, 0, width, 0);
            if (drawBottomLine) g.drawLine(x, height - (isRetina() ? 1 : 2), width, height - (isRetina() ? 1 : 2));

            if (SystemInfo.isMac && isUnderIntelliJLaF()) {
                g.setColor(Gray.xC9);
            } else {
                g.setColor(isUnderDarcula() ? Gray._255.withAlpha(30) : Gray.xFF.withAlpha(100));
            }

            g.drawLine(x, 0, width, 0);
        } finally {
            config.restore();
        }
    }

    public static void drawDoubleSpaceDottedLine(final Graphics2D g,
                                                 final int start,
                                                 final int end,
                                                 final int xOrY,
                                                 final Color fgColor,
                                                 boolean horizontal) {

        g.setColor(fgColor);
        for (int dot = start; dot < end; dot += 3) {
            if (horizontal) {
                g.drawLine(dot, xOrY, dot, xOrY);
            } else {
                g.drawLine(xOrY, dot, xOrY, dot);
            }
        }
    }


    @NotNull
    public static BufferedImage createImage(int width, int height, int type) {
        if (isRetina()) {
            return RetinaImage.create(width, height, type);
        }
        //noinspection UndesirableClassUsage
        return new BufferedImage(width, height, type);
    }

    @NotNull
    public static BufferedImage createImageForGraphics(Graphics2D g, int width, int height, int type) {
        if (isRetina(g)) {
            return RetinaImage.create(width, height, type);
        }
        //noinspection UndesirableClassUsage
        return new BufferedImage(width, height, type);
    }

    public static void drawImage(Graphics g, Image image, int x, int y, ImageObserver observer) {
        drawImage(g, image, x, y, -1, -1, observer);
    }

    public static void drawImage(Graphics g, Image image, int x, int y, int width, int height, ImageObserver observer) {
        if (image instanceof JBHiDPIScaledImage) {
            final Graphics2D newG = (Graphics2D) g.create(x, y, image.getWidth(observer), image.getHeight(observer));
            newG.scale(0.5, 0.5);
            Image img = ((JBHiDPIScaledImage) image).getDelegate();
            if (img == null) {
                img = image;
            }
            if (width == -1 && height == -1) {
                newG.drawImage(img, 0, 0, observer);
            } else {
                newG.drawImage(img, 0, 0, width * 2, height * 2, 0, 0, width * 2, height * 2, observer);
            }
            //newG.scale(1, 1);
            newG.dispose();
        } else if (width == -1 && height == -1) {
            g.drawImage(image, x, y, observer);
        } else {
            g.drawImage(image, x, y, x + width, y + height, 0, 0, width, height, observer);
        }
    }

    public static void drawImage(Graphics g, BufferedImage image, BufferedImageOp op, int x, int y) {
        if (image instanceof JBHiDPIScaledImage) {
            final Graphics2D newG = (Graphics2D) g.create(x, y, image.getWidth(null), image.getHeight(null));
            newG.scale(0.5, 0.5);
            Image img = ((JBHiDPIScaledImage) image).getDelegate();
            if (img == null) {
                img = image;
            }
            newG.drawImage((BufferedImage) img, op, 0, 0);
            //newG.scale(1, 1);
            newG.dispose();
        } else {
            ((Graphics2D) g).drawImage(image, op, x, y);
        }
    }

    public static void paintWithXorOnRetina(@NotNull Dimension size, @NotNull Graphics g, Consumer<Graphics2D> paintRoutine) {
        paintWithXorOnRetina(size, g, true, paintRoutine);
    }

    /**
     * Direct painting into component's graphics with XORMode is broken on retina-mode so we need to paint into an intermediate buffer first.
     */
    public static void paintWithXorOnRetina(@NotNull Dimension size,
                                            @NotNull Graphics g,
                                            boolean useRetinaCondition,
                                            Consumer<Graphics2D> paintRoutine) {
        if (!useRetinaCondition || !isRetina() || Registry.is("ide.mac.retina.disableDrawingFix")) {
            paintRoutine.consume((Graphics2D) g);
        } else {
            Rectangle rect = g.getClipBounds();
            if (rect == null) rect = new Rectangle(size);

            //noinspection UndesirableClassUsage
            Image image = new BufferedImage(rect.width * 2, rect.height * 2, BufferedImage.TYPE_INT_RGB);
            Graphics2D imageGraphics = (Graphics2D) image.getGraphics();

            imageGraphics.scale(2, 2);
            imageGraphics.translate(-rect.x, -rect.y);
            imageGraphics.setClip(rect.x, rect.y, rect.width, rect.height);

            paintRoutine.consume(imageGraphics);
            image.flush();
            imageGraphics.dispose();

            ((Graphics2D) g).scale(0.5, 0.5);
            g.drawImage(image, rect.x * 2, rect.y * 2, null);
        }
    }

    public static void drawVDottedLine(Graphics2D g, int lineX, int startY, int endY, @Nullable final Color bgColor, final Color fgColor) {
        if (bgColor != null) {
            g.setColor(bgColor);
            drawLine(g, lineX, startY, lineX, endY);
        }

        g.setColor(fgColor);
        for (int i = (startY / 2) * 2; i < endY; i += 2) {
            g.drawRect(lineX, i, 0, 0);
        }
    }

    public static void drawHDottedLine(Graphics2D g, int startX, int endX, int lineY, @Nullable final Color bgColor, final Color fgColor) {
        if (bgColor != null) {
            g.setColor(bgColor);
            drawLine(g, startX, lineY, endX, lineY);
        }

        g.setColor(fgColor);

        for (int i = (startX / 2) * 2; i < endX; i += 2) {
            g.drawRect(i, lineY, 0, 0);
        }
    }

    public static void drawDottedLine(Graphics2D g, int x1, int y1, int x2, int y2, @Nullable final Color bgColor, final Color fgColor) {
        if (x1 == x2) {
            drawVDottedLine(g, x1, y1, y2, bgColor, fgColor);
        } else if (y1 == y2) {
            drawHDottedLine(g, x1, x2, y1, bgColor, fgColor);
        } else {
            throw new IllegalArgumentException("Only vertical or horizontal lines are supported");
        }
    }

    public static void drawStringWithHighlighting(Graphics g, String s, int x, int y, Color foreground, Color highlighting) {
        g.setColor(highlighting);
        boolean isRetina = isRetina();
        for (float i = x - 1; i <= x + 1; i += isRetina ? .5 : 1) {
            for (float j = y - 1; j <= y + 1; j += isRetina ? .5 : 1) {
                ((Graphics2D) g).drawString(s, i, j);
            }
        }
        g.setColor(foreground);
        g.drawString(s, x, y);
    }

    @NotNull
    public static Paint getGradientPaint(float x1, float y1, @NotNull Color c1, float x2, float y2, @NotNull Color c2) {
        return (Registry.is("ui.no.bangs.and.whistles")) ? ColorUtil.mix(c1, c2, .5) : new GradientPaint(x1, y1, c1, x2, y2, c2);
    }

    public static boolean isAppleRetina() {
        return isRetina() && SystemInfo.isAppleJvm;
    }

    /**
     * Utility class for retina routine
     */
    private final static class DetectRetinaKit {

        private final static Map<GraphicsDevice, Boolean> devicesToRetinaSupportCacheMap = Maps.newHashMap();

        /**
         * The best way to understand whether we are on a retina device is [NSScreen backingScaleFactor]
         * But we should not invoke it from any thread. We do not have access to the AppKit thread
         * on the other hand. So let's use a dedicated method. It is rather safe because it caches a
         * value that has been got on AppKit previously.
         */
        private static boolean isOracleMacRetinaDevice(GraphicsDevice device) {

            if (SystemInfo.isAppleJvm) return false;

            Boolean isRetina = devicesToRetinaSupportCacheMap.get(device);

            if (isRetina != null) {
                return isRetina;
            }

            Method getScaleFactorMethod = null;
            try {
                getScaleFactorMethod = Class.forName("sun.awt.CGraphicsDevice").getMethod("getScaleFactor");
            } catch (ClassNotFoundException e) {
                // not an Oracle Mac JDK or API has been changed
                LOG.debug("CGraphicsDevice.getScaleFactor(): not an Oracle Mac JDK or API has been changed");
            } catch (NoSuchMethodException e) {
                LOG.debug("CGraphicsDevice.getScaleFactor(): not an Oracle Mac JDK or API has been changed");
            }

            try {
                isRetina = getScaleFactorMethod == null || (Integer) getScaleFactorMethod.invoke(device) != 1;
            } catch (IllegalAccessException e) {
                LOG.debug("CGraphicsDevice.getScaleFactor(): Access issue");
                isRetina = false;
            } catch (InvocationTargetException e) {
                LOG.debug("CGraphicsDevice.getScaleFactor(): Invocation issue");
                isRetina = false;
            } catch (IllegalArgumentException e) {
                LOG.debug("object is not an instance of declaring class: " + device.getClass().getName());
                isRetina = false;
            }

            devicesToRetinaSupportCacheMap.put(device, isRetina);

            return isRetina;
        }

    /*
      Could be quite easily implemented with [NSScreen backingScaleFactor]
      and JNA
     */
        //private static boolean isAppleRetina (Graphics2D g2d) {
        //  return false;
        //}

        /**
         * For JDK6 we have a dedicated property which does not allow to understand anything
         * per device but could be useful for image creation. We will get true in case
         * if at least one retina device is present.
         */
        private static boolean hasAppleRetinaDevice() {
            return (Float) Toolkit.getDefaultToolkit()
                    .getDesktopProperty(
                            "apple.awt.contentScaleFactor") != 1.0f;
        }

        /**
         * This method perfectly detects retina Graphics2D for jdk7+
         * For Apple JDK6 it returns false.
         *
         * @param g graphics to be tested
         * @return false if the device of the Graphics2D is not a retina device,
         * jdk is an Apple JDK or Oracle API has been changed.
         */
        private static boolean isMacRetina(Graphics2D g) {
            GraphicsDevice device = g.getDeviceConfiguration().getDevice();
            return isOracleMacRetinaDevice(device);
        }

        /**
         * Checks that at least one retina device is present.
         * Do not use this method if your are going to make decision for a particular screen.
         * isRetina(Graphics2D) is more preferable
         *
         * @return true if at least one device is a retina device
         */
        private static boolean isRetina() {
            if (SystemInfo.isAppleJvm) {
                return hasAppleRetinaDevice();
            }

            // Oracle JDK

            if (SystemInfo.isMac) {
                GraphicsEnvironment e
                        = GraphicsEnvironment.getLocalGraphicsEnvironment();

                GraphicsDevice[] devices = e.getScreenDevices();

                //now get the configurations for each device
                for (GraphicsDevice device : devices) {
                    if (isOracleMacRetinaDevice(device)) {
                        return true;
                    }
                }
            }

            return false;
        }
    }

    private static final Supplier<Boolean> X_RENDER_ACTIVE = Suppliers.memoize(() -> {
        if (!SystemInfo.isXWindow) {
            return false;
        }
        try {
            final Class<?> clazz = ClassLoader.getSystemClassLoader().loadClass("sun.awt.X11GraphicsEnvironment");
            final Method method = clazz.getMethod("isXRenderAvailable");
            return (Boolean) method.invoke(null);
        } catch (Throwable e) {
            return false;
        }
    });

    /**
     * Configures composite to use for drawing text with the given graphics container.
     * <p/>
     * The whole idea is that <a href="http://en.wikipedia.org/wiki/X_Rendering_Extension">XRender-based</a> pipeline doesn't support
     * {@link AlphaComposite#SRC} and we should use {@link AlphaComposite#SRC_OVER} instead.
     *
     * @param g target graphics container
     */
    public static void setupComposite(@NotNull Graphics2D g) {
        g.setComposite(X_RENDER_ACTIVE.get() ? AlphaComposite.SrcOver : AlphaComposite.Src);
    }

    public static void setupComponentAntialiasing(JComponent component) {
        component.putClientProperty(SwingUtilities2.AA_TEXT_PROPERTY_KEY, AntialiasingType.getAAHintForSwingComponent());
    }
}
