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

import com.intellij.openapi.util.*;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.JBColor;
import com.intellij.util.Function;
import com.intellij.util.NotNullProducer;
import com.intellij.util.ReflectionUtil;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import javax.swing.*;
import javax.swing.Timer;
import javax.swing.border.Border;
import javax.swing.plaf.ComboBoxUI;
import javax.swing.plaf.basic.BasicComboBoxUI;
import javax.swing.plaf.basic.ComboPopup;
import javax.swing.text.DefaultEditorKit;
import javax.swing.text.DefaultFormatterFactory;
import javax.swing.text.JTextComponent;
import javax.swing.text.NumberFormatter;
import javax.swing.text.html.HTMLEditorKit;
import javax.swing.text.html.StyleSheet;
import javax.swing.undo.UndoManager;
import java.awt.*;
import java.awt.event.*;
import java.awt.font.FontRenderContext;
import java.awt.font.GlyphVector;
import java.awt.image.BufferedImage;
import java.awt.image.PixelGrabber;
import java.beans.PropertyChangeListener;
import java.lang.ref.WeakReference;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.text.NumberFormat;
import java.util.*;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.regex.Pattern;

/**
 * @author max
 */
@SuppressWarnings("StaticMethodOnlyUsedInOneClass")
public class UIUtil extends DrawUtil {

    public static final String BORDER_LINE = "<hr size=1 noshade>";

    private static final StyleSheet DEFAULT_HTML_KIT_CSS;

    static {
        blockATKWrapper();
        // save the default JRE CSS and ..
        HTMLEditorKit kit = new HTMLEditorKit();
        DEFAULT_HTML_KIT_CSS = kit.getStyleSheet();
        // .. erase global ref to this CSS so no one can alter it
        kit.setStyleSheet(null);
    }

    private static void blockATKWrapper() {
    /*
     * The method should be called before java.awt.Toolkit.initAssistiveTechnologies()
     * which is called from Toolkit.getDefaultToolkit().
     */
        if (!(SystemInfo.isLinux && Registry.is("linux.jdk.accessibility.atkwrapper.block"))) return;


    }

    public static void invokeLaterIfNeeded(@NotNull Runnable runnable) {
        if (SwingUtilities.isEventDispatchThread()) {
            runnable.run();
        } else {
            SwingUtilities.invokeLater(runnable);
        }
    }

    public static int getMultiClickInterval() {
        Object property = Toolkit.getDefaultToolkit().getDesktopProperty("awt.multiClickInterval");
        if (property instanceof Integer) {
            return (Integer) property;
        }
        return 500;
    }

    private static final String[] STANDARD_FONT_SIZES =
            {"8", "9", "10", "11", "12", "14", "16", "18", "20", "22", "24", "26", "28", "36", "48", "72"};

    public static void applyStyle(@NotNull ComponentStyle componentStyle, @NotNull Component comp) {
        if (!(comp instanceof JComponent)) return;

        JComponent c = (JComponent) comp;

        if (isUnderAquaBasedLookAndFeel()) {
            c.putClientProperty("JComponent.sizeVariant", StringUtil.toLowerCase(componentStyle.name()));
        }
        FontSize fontSize = componentStyle == ComponentStyle.MINI
                ? FontSize.MINI
                : componentStyle == ComponentStyle.SMALL
                ? FontSize.SMALL
                : FontSize.NORMAL;
        c.setFont(getFont(fontSize, c.getFont()));
        Container p = c.getParent();
        if (p != null) {
            SwingUtilities.updateComponentTreeUI(p);
        }
    }


    private static final GrayFilter DEFAULT_GRAY_FILTER = new GrayFilter(true, 50);
    private static final GrayFilter DARCULA_GRAY_FILTER = new GrayFilter(true, 30);

    public static GrayFilter getGrayFilter() {
        return isUnderDarcula() ? DARCULA_GRAY_FILTER : DEFAULT_GRAY_FILTER;
    }

    public enum FontSize {NORMAL, SMALL, MINI}

    public enum ComponentStyle {LARGE, REGULAR, SMALL, MINI}

    public enum FontColor {NORMAL, BRIGHTER}

    @NonNls
    public static final String HTML_MIME = "text/html";
    @NonNls
    public static final String JSLIDER_ISFILLED = "JSlider.isFilled";
    @NonNls
    public static final String ARIAL_FONT_NAME = "Arial";
    @NonNls
    public static final String TABLE_FOCUS_CELL_BACKGROUND_PROPERTY = "Table.focusCellBackground";
    @NonNls
    public static final String CENTER_TOOLTIP_DEFAULT = "ToCenterTooltip";
    @NonNls
    public static final String CENTER_TOOLTIP_STRICT = "ToCenterTooltip.default";

    public static final Pattern CLOSE_TAG_PATTERN = Pattern.compile("<\\s*([^<>/ ]+)([^<>]*)/\\s*>", Pattern.CASE_INSENSITIVE);

    @NonNls
    public static final String FOCUS_PROXY_KEY = "isFocusProxy";

    public static Key<Integer> KEEP_BORDER_SIDES = Key.create("keepBorderSides");
    private static Key<UndoManager> UNDO_MANAGER = Key.create("undoManager");
    private static final AbstractAction REDO_ACTION = new AbstractAction() {
        @Override
        public void actionPerformed(ActionEvent e) {
            UndoManager manager = getClientProperty(e.getSource(), UNDO_MANAGER);
            if (manager != null && manager.canRedo()) {
                manager.redo();
            }
        }
    };
    private static final AbstractAction UNDO_ACTION = new AbstractAction() {
        @Override
        public void actionPerformed(ActionEvent e) {
            UndoManager manager = getClientProperty(e.getSource(), UNDO_MANAGER);
            if (manager != null && manager.canUndo()) {
                manager.undo();
            }
        }
    };

    private static final Color UNFOCUSED_SELECTION_COLOR = Gray._212;
    private static final Color ACTIVE_HEADER_COLOR = new Color(160, 186, 213);
    private static final Color INACTIVE_HEADER_COLOR = Gray._128;
    private static final Color BORDER_COLOR = Color.LIGHT_GRAY;

    public static final Color CONTRAST_BORDER_COLOR = new JBColor(new NotNullProducer<Color>() {
        final Color color = new JBColor(0x9b9b9b, 0x282828);

        @NotNull
        @Override
        public Color produce() {
            if (SystemInfo.isMac && isUnderIntelliJLaF()) {
                return Gray.xC9;
            }
            return color;
        }
    });

    public static final Color SIDE_PANEL_BACKGROUND = new JBColor(new NotNullProducer<Color>() {
        final JBColor myDefaultValue = new JBColor(new Color(0xE6EBF0), new Color(0x3E434C));

        @NotNull
        @Override
        public Color produce() {
            Color color = UIManager.getColor("SidePanel.background");
            return color == null ? myDefaultValue : color;
        }
    });

    public static final Color AQUA_SEPARATOR_FOREGROUND_COLOR = new JBColor(Gray._190, Gray.x51);
    public static final Color AQUA_SEPARATOR_BACKGROUND_COLOR = new JBColor(Gray._240, Gray.x51);
    public static final Color TRANSPARENT_COLOR = new Color(0, 0, 0, 0);

    public static final int DEFAULT_HGAP = 10;
    public static final int DEFAULT_VGAP = 4;
    public static final int LARGE_VGAP = 12;

    public static final Insets PANEL_REGULAR_INSETS = new Insets(8, 12, 8, 12);
    public static final Insets PANEL_SMALL_INSETS = new Insets(5, 8, 5, 8);


    public static final Border DEBUG_MARKER_BORDER = new Border() {
        @Override
        public Insets getBorderInsets(Component c) {
            return new Insets(0, 0, 0, 0);
        }

        @Override
        public void paintBorder(Component c, Graphics g, int x, int y, int width, int height) {
            Graphics g2 = g.create();
            try {
                g2.setColor(JBColor.RED);
                drawDottedRectangle(g2, x, y, x + width - 1, y + height - 1);
            } finally {
                g2.dispose();
            }
        }

        @Override
        public boolean isBorderOpaque() {
            return true;
        }
    };

    private static volatile Pair<String, Integer> ourSystemFontData;

    public static final float DEF_SYSTEM_FONT_SIZE = 12f; // TODO: consider 12 * 1.33 to compensate JDK's 72dpi font scale

    @NonNls
    private static final String ROOT_PANE = "JRootPane.future";

    private UIUtil() {
    }

    //public static boolean isMacRetina(Graphics2D g) {
    //  return DetectRetinaKit.isMacRetina(g);
    //}

    public static boolean hasLeakingAppleListeners() {
        // in version 1.6.0_29 Apple introduced a memory leak in JViewport class - they add a PropertyChangeListeners to the CToolkit
        // but never remove them:
        // JViewport.java:
        // public JViewport() {
        //   ...
        //   final Toolkit toolkit = Toolkit.getDefaultToolkit();
        //   if(toolkit instanceof CToolkit)
        //   {
        //     final boolean isRunningInHiDPI = ((CToolkit)toolkit).runningInHiDPI();
        //     if(isRunningInHiDPI) setScrollMode(0);
        //     toolkit.addPropertyChangeListener("apple.awt.contentScaleFactor", new PropertyChangeListener() { ... });
        //   }
        // }

        return SystemInfo.isMac && System.getProperty("java.runtime.version").startsWith("1.6.0_29");
    }

    public static void removeLeakingAppleListeners() {
        if (!hasLeakingAppleListeners()) return;

        Toolkit toolkit = Toolkit.getDefaultToolkit();
        String name = "apple.awt.contentScaleFactor";
        for (PropertyChangeListener each : toolkit.getPropertyChangeListeners(name)) {
            toolkit.removePropertyChangeListener(name, each);
        }
    }

    /**
     * @param component a Swing component that may hold a client property value
     * @param key       the client property key
     * @return {@code true} if the property of the specified component is set to {@code true}
     */
    public static boolean isClientPropertyTrue(Object component, @NotNull Object key) {
        return Boolean.TRUE.equals(getClientProperty(component, key));
    }

    /**
     * @param component a Swing component that may hold a client property value
     * @param key       the client property key that specifies a return type
     * @return the property value from the specified component or {@code null}
     */
    public static Object getClientProperty(Object component, @NotNull Object key) {
        return component instanceof JComponent ? ((JComponent) component).getClientProperty(key) : null;
    }


    /**
     * @param component a Swing component that may hold a client property value
     * @param key       the client property key that specifies a return type
     * @return the property value from the specified component or {@code null}
     */
    public static <T> T getClientProperty(Object component, @NotNull Key<T> key) {
        //noinspection unchecked
        return (T) getClientProperty(component, (Object) key);
    }

    public static <T> void putClientProperty(@NotNull JComponent component, @NotNull Key<T> key, T value) {
        component.putClientProperty(key, value);
    }

    public static String getHtmlBody(@NotNull String text) {
        int htmlIndex = 6 + text.indexOf("<html>");
        if (htmlIndex < 6) {
            return text.replaceAll("\n", "<br>");
        }
        int htmlCloseIndex = text.indexOf("</html>", htmlIndex);
        if (htmlCloseIndex < 0) {
            htmlCloseIndex = text.length();
        }
        int bodyIndex = 6 + text.indexOf("<body>", htmlIndex);
        if (bodyIndex < 6) {
            return text.substring(htmlIndex, htmlCloseIndex);
        }
        int bodyCloseIndex = text.indexOf("</body>", bodyIndex);
        if (bodyCloseIndex < 0) {
            bodyCloseIndex = text.length();
        }
        return text.substring(bodyIndex, Math.min(bodyCloseIndex, htmlCloseIndex));
    }

    public static boolean isReallyTypedEvent(KeyEvent e) {
        char c = e.getKeyChar();
        if (c < 0x20 || c == 0x7F) return false;

        if (SystemInfo.isMac) {
            return !e.isMetaDown() && !e.isControlDown();
        }

        return !e.isAltDown() && !e.isControlDown();
    }

    public static int getStringY(@NotNull final String string, @NotNull final Rectangle bounds, @NotNull final Graphics2D g) {
        final int centerY = bounds.height / 2;
        final Font font = g.getFont();
        final FontRenderContext frc = g.getFontRenderContext();
        final Rectangle stringBounds = font.getStringBounds(string, frc).getBounds();

        return (int) (centerY - stringBounds.height / 2.0 - stringBounds.y);
    }

    /**
     * @param string   {@code String} to examine
     * @param font     {@code Font} that is used to render the string
     * @param graphics {@link Graphics} that should be used to render the string
     * @return height of the tallest glyph in a string. If string is empty, returns 0
     */
    public static int getHighestGlyphHeight(@NotNull String string, @NotNull Font font, @NotNull Graphics graphics) {
        FontRenderContext frc = ((Graphics2D) graphics).getFontRenderContext();
        GlyphVector gv = font.createGlyphVector(frc, string);
        int maxHeight = 0;
        for (int i = 0; i < string.length(); i++) {
            maxHeight = Math.max(maxHeight, (int) gv.getGlyphMetrics(i).getBounds2D().getHeight());
        }
        return maxHeight;
    }


    public static void setActionNameAndMnemonic(@NotNull String text, @NotNull Action action) {
        assignMnemonic(text, action);

        text = text.replaceAll("&", "");
        action.putValue(Action.NAME, text);
    }

    public static void assignMnemonic(@NotNull String text, @NotNull Action action) {
        int mnemoPos = text.indexOf('&');
        if (mnemoPos >= 0 && mnemoPos < text.length() - 2) {
            String mnemoChar = text.substring(mnemoPos + 1, mnemoPos + 2).trim();
            if (mnemoChar.length() == 1) {
                action.putValue(Action.MNEMONIC_KEY, Integer.valueOf(mnemoChar.charAt(0)));
            }
        }
    }


    public static Font getLabelFont(@NotNull FontSize size) {
        return getFont(size, null);
    }

    @NotNull
    public static Font getFont(@NotNull FontSize size, @Nullable Font base) {
        if (base == null) base = getLabelFont();

        return base.deriveFont(getFontSize(size));
    }

    public static float getFontSize(FontSize size) {
        int defSize = getLabelFont().getSize();
        switch (size) {
            case SMALL:
                return Math.max(defSize - JBUI.scale(2f), JBUI.scale(11f));
            case MINI:
                return Math.max(defSize - JBUI.scale(4f), JBUI.scale(9f));
            default:
                return defSize;
        }
    }

    public static Color getLabelFontColor(FontColor fontColor) {
        Color defColor = getLabelForeground();
        if (fontColor == FontColor.BRIGHTER) {
            return new JBColor(new Color(Math.min(defColor.getRed() + 50, 255), Math.min(defColor.getGreen() + 50, 255), Math.min(
                    defColor.getBlue() + 50, 255)), defColor.darker());
        }
        return defColor;
    }

    private static final Map<Class, Ref<Method>> ourDefaultIconMethodsCache = new ConcurrentHashMap<Class, Ref<Method>>();

    public static int getScrollBarWidth() {
        return UIManager.getInt("ScrollBar.width");
    }

    public static Font getLabelFont() {
        return UIManager.getFont("Label.font");
    }

    public static Color getLabelBackground() {
        return UIManager.getColor("Label.background");
    }

    public static Color getLabelForeground() {
        return UIManager.getColor("Label.foreground");
    }

    public static Color getLabelDisabledForeground() {
        final Color color = UIManager.getColor("Label.disabledForeground");
        if (color != null) return color;
        return UIManager.getColor("Label.disabledText");
    }


    public static Color getTableHeaderBackground() {
        return UIManager.getColor("TableHeader.background");
    }

    public static Color getTreeTextForeground() {
        return UIManager.getColor("Tree.textForeground");
    }

    public static Color getTreeSelectionBackground() {
        if (isUnderNimbusLookAndFeel()) {
            Color color = UIManager.getColor("Tree.selectionBackground");
            if (color != null) return color;
            color = UIManager.getColor("nimbusSelectionBackground");
            if (color != null) return color;
        }
        return UIManager.getColor("Tree.selectionBackground");
    }

    public static Color getTreeTextBackground() {
        return UIManager.getColor("Tree.textBackground");
    }

    public static Color getListSelectionForeground() {
        final Color color = UIManager.getColor("List.selectionForeground");
        if (color == null) {
            return UIManager.getColor("List[Selected].textForeground");  // Nimbus
        }
        return color;
    }

    public static Color getFieldForegroundColor() {
        return UIManager.getColor("field.foreground");
    }

    public static Color getTableSelectionBackground() {
        if (isUnderNimbusLookAndFeel()) {
            Color color = UIManager.getColor("Table[Enabled+Selected].textBackground");
            if (color != null) return color;
            color = UIManager.getColor("nimbusSelectionBackground");
            if (color != null) return color;
        }
        return UIManager.getColor("Table.selectionBackground");
    }

    public static Color getActiveTextColor() {
        return UIManager.getColor("textActiveText");
    }

    public static Color getInactiveTextColor() {
        return UIManager.getColor("textInactiveText");
    }

    public static Color getSlightlyDarkerColor(Color c) {
        float[] hsl = Color.RGBtoHSB(c.getRed(), c.getGreen(), c.getBlue(), new float[3]);
        return new Color(Color.HSBtoRGB(hsl[0], hsl[1], hsl[2] - .08f > 0 ? hsl[2] - .08f : hsl[2]));
    }

    /**
     * @deprecated use com.intellij.util.ui.UIUtil#getTextFieldBackground()
     */
    public static Color getActiveTextFieldBackgroundColor() {
        return getTextFieldBackground();
    }

    public static Color getInactiveTextFieldBackgroundColor() {
        return UIManager.getColor("TextField.inactiveBackground");
    }

    public static Font getTreeFont() {
        return UIManager.getFont("Tree.font");
    }

    public static Font getListFont() {
        return UIManager.getFont("List.font");
    }

    public static Color getTreeSelectionForeground() {
        return UIManager.getColor("Tree.selectionForeground");
    }

    public static Color getTreeForeground(boolean selected, boolean hasFocus) {
        if (!selected) {
            return getTreeForeground();
        }
        Color fg = UIManager.getColor("Tree.selectionInactiveForeground");
        if (!hasFocus && fg != null) {
            return fg;
        }
        return getTreeSelectionForeground();
    }

    /**
     * @deprecated use com.intellij.util.ui.UIUtil#getInactiveTextColor()
     */
    public static Color getTextInactiveTextColor() {
        return getInactiveTextColor();
    }

    public static void installPopupMenuColorAndFonts(final JComponent contentPane) {
        LookAndFeel.installColorsAndFont(contentPane, "PopupMenu.background", "PopupMenu.foreground", "PopupMenu.font");
    }

    public static void installPopupMenuBorder(final JComponent contentPane) {
        LookAndFeel.installBorder(contentPane, "PopupMenu.border");
    }

    public static Color getTreeSelectionBorderColor() {
        return UIManager.getColor("Tree.selectionBorderColor");
    }

    public static int getTreeRightChildIndent() {
        return UIManager.getInt("Tree.rightChildIndent");
    }

    public static int getTreeLeftChildIndent() {
        return UIManager.getInt("Tree.leftChildIndent");
    }

    public static Color getToolTipBackground() {
        return UIManager.getColor("ToolTip.background");
    }

    public static Color getToolTipForeground() {
        return UIManager.getColor("ToolTip.foreground");
    }

    public static Color getComboBoxDisabledForeground() {
        return UIManager.getColor("ComboBox.disabledForeground");
    }

    public static Color getComboBoxDisabledBackground() {
        return UIManager.getColor("ComboBox.disabledBackground");
    }

    public static Color getButtonSelectColor() {
        return UIManager.getColor("Button.select");
    }

    public static Integer getPropertyMaxGutterIconWidth(final String propertyPrefix) {
        return (Integer) UIManager.get(propertyPrefix + ".maxGutterIconWidth");
    }

    public static Color getMenuItemDisabledForeground() {
        return UIManager.getColor("MenuItem.disabledForeground");
    }

    public static Object getMenuItemDisabledForegroundObject() {
        return UIManager.get("MenuItem.disabledForeground");
    }

    public static Object getTabbedPanePaintContentBorder(final JComponent c) {
        return c.getClientProperty("TabbedPane.paintContentBorder");
    }

    public static boolean isMenuCrossMenuMnemonics() {
        return UIManager.getBoolean("Menu.crossMenuMnemonic");
    }

    public static Color getTableBackground() {
        // Under GTK+ L&F "Table.background" often has main panel color, which looks ugly
        return isUnderGTKLookAndFeel() ? getTreeTextBackground() : UIManager.getColor("Table.background");
    }

    public static Color getTableBackground(final boolean isSelected) {
        return isSelected ? getTableSelectionBackground() : getTableBackground();
    }

    public static Color getTableSelectionForeground() {
        if (isUnderNimbusLookAndFeel()) {
            return UIManager.getColor("Table[Enabled+Selected].textForeground");
        }
        return UIManager.getColor("Table.selectionForeground");
    }

    public static Color getTableForeground() {
        return UIManager.getColor("Table.foreground");
    }

    public static Color getTableForeground(final boolean isSelected) {
        return isSelected ? getTableSelectionForeground() : getTableForeground();
    }

    public static Color getTableGridColor() {
        return UIManager.getColor("Table.gridColor");
    }

    public static Color getListBackground() {
        if (isUnderNimbusLookAndFeel()) {
            final Color color = UIManager.getColor("List.background");
            //noinspection UseJBColor
            return new Color(color.getRed(), color.getGreen(), color.getBlue(), color.getAlpha());
        }
        // Under GTK+ L&F "Table.background" often has main panel color, which looks ugly
        return isUnderGTKLookAndFeel() ? getTreeTextBackground() : UIManager.getColor("List.background");
    }

    public static Color getListBackground(boolean isSelected) {
        return isSelected ? getListSelectionBackground() : getListBackground();
    }

    public static Color getListForeground() {
        return UIManager.getColor("List.foreground");
    }

    public static Color getListForeground(boolean isSelected) {
        return isSelected ? getListSelectionForeground() : getListForeground();
    }

    public static Color getEditorPaneBackground() {
        return UIManager.getColor("EditorPane.background");
    }

    public static Color getTreeBackground() {
        return UIManager.getColor("Tree.background");
    }

    public static Color getTreeForeground() {
        return UIManager.getColor("Tree.foreground");
    }

    public static Color getTableFocusCellBackground() {
        return UIManager.getColor(TABLE_FOCUS_CELL_BACKGROUND_PROPERTY);
    }

    public static Color getListSelectionBackground() {
        if (isUnderNimbusLookAndFeel()) {
            return UIManager.getColor("List[Selected].textBackground");  // Nimbus
        }
        return UIManager.getColor("List.selectionBackground");
    }

    public static Color getListUnfocusedSelectionBackground() {
        return new JBColor(UNFOCUSED_SELECTION_COLOR, new Color(13, 41, 62));
    }

    public static Color getTreeSelectionBackground(boolean focused) {
        return focused ? getTreeSelectionBackground() : getTreeUnfocusedSelectionBackground();
    }

    public static Color getTreeUnfocusedSelectionBackground() {
        Color background = getTreeTextBackground();
        return ColorUtil.isDark(background) ? new JBColor(Gray._30, new Color(13, 41, 62)) : UNFOCUSED_SELECTION_COLOR;
    }

    public static Color getTextFieldForeground() {
        return UIManager.getColor("TextField.foreground");
    }

    public static Color getTextFieldBackground() {
        return isUnderGTKLookAndFeel() ? UIManager.getColor("EditorPane.background") : UIManager.getColor("TextField.background");
    }

    public static Font getButtonFont() {
        return UIManager.getFont("Button.font");
    }

    public static Font getToolTipFont() {
        return UIManager.getFont("ToolTip.font");
    }

    public static Color getTabbedPaneBackground() {
        return UIManager.getColor("TabbedPane.background");
    }

    public static void setSliderIsFilled(final JSlider slider, final boolean value) {
        slider.putClientProperty("JSlider.isFilled", Boolean.valueOf(value));
    }

    public static Color getLabelTextForeground() {
        return UIManager.getColor("Label.textForeground");
    }

    public static Color getControlColor() {
        return UIManager.getColor("control");
    }

    public static Font getOptionPaneMessageFont() {
        return UIManager.getFont("OptionPane.messageFont");
    }

    public static Font getMenuFont() {
        return UIManager.getFont("Menu.font");
    }

    public static Color getSeparatorForeground() {
        return UIManager.getColor("Separator.foreground");
    }

    public static Color getSeparatorBackground() {
        return UIManager.getColor("Separator.background");
    }

    public static Color getSeparatorShadow() {
        return UIManager.getColor("Separator.shadow");
    }

    public static Color getSeparatorHighlight() {
        return UIManager.getColor("Separator.highlight");
    }

    public static Color getSeparatorColorUnderNimbus() {
        return UIManager.getColor("nimbusBlueGrey");
    }

    public static Color getSeparatorColor() {
        Color separatorColor = getSeparatorForeground();
        if (isUnderAlloyLookAndFeel()) {
            separatorColor = getSeparatorShadow();
        }
        if (isUnderNimbusLookAndFeel()) {
            separatorColor = getSeparatorColorUnderNimbus();
        }
        //under GTK+ L&F colors set hard
        if (isUnderGTKLookAndFeel()) {
            separatorColor = Gray._215;
        }
        return separatorColor;
    }

    public static Border getTableFocusCellHighlightBorder() {
        return UIManager.getBorder("Table.focusCellHighlightBorder");
    }

    public static void setLineStyleAngled(final JTree component) {
        component.putClientProperty("JTree.lineStyle", "Angled");
    }

    public static Color getTableFocusCellForeground() {
        return UIManager.getColor("Table.focusCellForeground");
    }

    /**
     * @deprecated use com.intellij.util.ui.UIUtil#getPanelBackground() instead
     */
    public static Color getPanelBackgound() {
        return getPanelBackground();
    }

    public static Border getTextFieldBorder() {
        return UIManager.getBorder("TextField.border");
    }

    public static Border getButtonBorder() {
        return UIManager.getBorder("Button.border");
    }

    public static Icon getTreeCollapsedIcon() {
        return UIManager.getIcon("Tree.collapsedIcon");
    }

    public static Icon getTreeExpandedIcon() {
        return UIManager.getIcon("Tree.expandedIcon");
    }

    public static Icon getTreeIcon(boolean expanded) {
        return expanded ? getTreeExpandedIcon() : getTreeCollapsedIcon();
    }


    public static Border getTableHeaderCellBorder() {
        return UIManager.getBorder("TableHeader.cellBorder");
    }

    public static Color getWindowColor() {
        return UIManager.getColor("window");
    }

    public static Color getTextAreaForeground() {
        return UIManager.getColor("TextArea.foreground");
    }

    public static Color getOptionPaneBackground() {
        return UIManager.getColor("OptionPane.background");
    }

    @SuppressWarnings({"HardCodedStringLiteral"})
    public static boolean isUnderAlloyLookAndFeel() {
        return UIManager.getLookAndFeel().getName().contains("Alloy");
    }

    @SuppressWarnings({"HardCodedStringLiteral"})
    public static boolean isUnderAlloyIDEALookAndFeel() {
        return isUnderAlloyLookAndFeel() && UIManager.getLookAndFeel().getName().contains("IDEA");
    }

    @SuppressWarnings({"HardCodedStringLiteral"})
    public static boolean isUnderWindowsLookAndFeel() {
        return SystemInfo.isWindows && UIManager.getLookAndFeel().getName().equals("Windows");
    }

    @SuppressWarnings({"HardCodedStringLiteral"})
    public static boolean isUnderWindowsClassicLookAndFeel() {
        return UIManager.getLookAndFeel().getName().equals("Windows Classic");
    }

    @SuppressWarnings({"HardCodedStringLiteral"})
    public static boolean isUnderNimbusLookAndFeel() {
        return UIManager.getLookAndFeel().getName().contains("Nimbus");
    }

    @SuppressWarnings({"HardCodedStringLiteral"})
    public static boolean isUnderAquaLookAndFeel() {
        return SystemInfo.isMac && UIManager.getLookAndFeel().getName().contains("Mac OS X");
    }

    @SuppressWarnings({"HardCodedStringLiteral"})
    public static boolean isUnderJGoodiesLookAndFeel() {
        return UIManager.getLookAndFeel().getName().contains("JGoodies");
    }

    @SuppressWarnings({"HardCodedStringLiteral"})
    public static boolean isUnderAquaBasedLookAndFeel() {
        return SystemInfo.isMac && (isUnderAquaLookAndFeel() || isUnderDarcula() || isUnderIntelliJLaF());
    }

    @SuppressWarnings({"HardCodedStringLiteral"})
    public static boolean isUnderGTKLookAndFeel() {
        return SystemInfo.isXWindow && UIManager.getLookAndFeel().getName().contains("GTK");
    }

    public static final Color GTK_AMBIANCE_TEXT_COLOR = new Color(223, 219, 210);
    public static final Color GTK_AMBIANCE_BACKGROUND_COLOR = new Color(67, 66, 63);

    @SuppressWarnings({"HardCodedStringLiteral"})
    @Nullable
    public static String getGtkThemeName() {
        final LookAndFeel laf = UIManager.getLookAndFeel();
        if (laf != null && "GTKLookAndFeel".equals(laf.getClass().getSimpleName())) {
            try {
                final Method method = laf.getClass().getDeclaredMethod("getGtkThemeName");
                method.setAccessible(true);
                final Object theme = method.invoke(laf);
                if (theme != null) {
                    return theme.toString();
                }
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    @SuppressWarnings({"HardCodedStringLiteral"})
    public static boolean isMurrineBasedTheme() {
        final String gtkTheme = getGtkThemeName();
        return "Ambiance".equalsIgnoreCase(gtkTheme) ||
                "Radiance".equalsIgnoreCase(gtkTheme) ||
                "Dust".equalsIgnoreCase(gtkTheme) ||
                "Dust Sand".equalsIgnoreCase(gtkTheme);
    }

    public static Color shade(final Color c, final double factor, final double alphaFactor) {
        assert factor >= 0 : factor;
        //noinspection UseJBColor
        return new Color(
                Math.min((int) Math.round(c.getRed() * factor), 255),
                Math.min((int) Math.round(c.getGreen() * factor), 255),
                Math.min((int) Math.round(c.getBlue() * factor), 255),
                Math.min((int) Math.round(c.getAlpha() * alphaFactor), 255)
        );
    }

    public static Color mix(final Color c1, final Color c2, final double factor) {
        assert 0 <= factor && factor <= 1.0 : factor;
        final double backFactor = 1.0 - factor;
        //noinspection UseJBColor
        return new Color(
                Math.min((int) Math.round(c1.getRed() * backFactor + c2.getRed() * factor), 255),
                Math.min((int) Math.round(c1.getGreen() * backFactor + c2.getGreen() * factor), 255),
                Math.min((int) Math.round(c1.getBlue() * backFactor + c2.getBlue() * factor), 255)
        );
    }

    public static boolean isFullRowSelectionLAF() {
        return isUnderGTKLookAndFeel();
    }

    public static boolean isUnderNativeMacLookAndFeel() {
        return isUnderAquaLookAndFeel() || isUnderDarcula();
    }

    public static int getListCellHPadding() {
        return isUnderNativeMacLookAndFeel() ? 7 : 2;
    }

    public static int getListCellVPadding() {
        return 1;
    }

    public static Insets getListCellPadding() {
        return new Insets(getListCellVPadding(), getListCellHPadding(), getListCellVPadding(), getListCellHPadding());
    }

    public static Insets getListViewportPadding() {
        return isUnderNativeMacLookAndFeel() ? new Insets(1, 0, 1, 0) : new Insets(5, 5, 5, 5);
    }

    public static boolean isToUseDottedCellBorder() {
        return !isUnderNativeMacLookAndFeel();
    }

    public static boolean isControlKeyDown(MouseEvent mouseEvent) {
        return SystemInfo.isMac ? mouseEvent.isMetaDown() : mouseEvent.isControlDown();
    }


    public static String[] getStandardFontSizes() {
        return STANDARD_FONT_SIZES;
    }

    public static boolean isValidFont(@NotNull Font font) {
        try {
            return font.canDisplay('a') &&
                    font.canDisplay('z') &&
                    font.canDisplay('A') &&
                    font.canDisplay('Z') &&
                    font.canDisplay('0') &&
                    font.canDisplay('1');
        } catch (Exception e) {
            // JRE has problems working with the font. Just skip.
            return false;
        }
    }

    public static void setupEnclosingDialogBounds(final JComponent component) {
        component.revalidate();
        component.repaint();
        final Window window = SwingUtilities.windowForComponent(component);
        if (window != null &&
                (window.getSize().height < window.getMinimumSize().height || window.getSize().width < window.getMinimumSize().width)) {
            window.pack();
        }
    }

    public static String displayPropertiesToCSS(Font font, Color fg) {
        @NonNls StringBuilder rule = new StringBuilder("body {");
        if (font != null) {
            rule.append(" font-family: ");
            rule.append(font.getFamily());
            rule.append(" ; ");
            rule.append(" font-size: ");
            rule.append(font.getSize());
            rule.append("pt ;");
            if (font.isBold()) {
                rule.append(" font-weight: 700 ; ");
            }
            if (font.isItalic()) {
                rule.append(" font-style: italic ; ");
            }
        }
        if (fg != null) {
            rule.append(" color: #");
            appendColor(fg, rule);
            rule.append(" ; ");
        }
        rule.append(" }");
        return rule.toString();
    }

    public static void appendColor(final Color color, final StringBuilder sb) {
        if (color.getRed() < 16) sb.append('0');
        sb.append(Integer.toHexString(color.getRed()));
        if (color.getGreen() < 16) sb.append('0');
        sb.append(Integer.toHexString(color.getGreen()));
        if (color.getBlue() < 16) sb.append('0');
        sb.append(Integer.toHexString(color.getBlue()));
    }

    /**
     * This method is intended to use when user settings are not accessible yet.
     * Use it to set up default RenderingHints.
     *
     * @param g
     */
    public static void applyRenderingHints(final Graphics g) {
        Graphics2D g2d = (Graphics2D) g;
        Toolkit tk = Toolkit.getDefaultToolkit();
        //noinspection HardCodedStringLiteral
        Map map = (Map) tk.getDesktopProperty("awt.font.desktophints");
        if (map != null) {
            g2d.addRenderingHints(map);
        }
    }


    /**
     * @see #pump()
     */
    @TestOnly
    public static void dispatchAllInvocationEvents() {
        //noinspection StatementWithEmptyBody
        while (dispatchInvocationEvent()) ;
    }

    @TestOnly
    public static boolean dispatchInvocationEvent() {
        final EventQueue eventQueue = Toolkit.getDefaultToolkit().getSystemEventQueue();
        AWTEvent event = eventQueue.peekEvent();
        if (event == null) return false;
        try {
            event = eventQueue.getNextEvent();
            if (event instanceof InvocationEvent) {
                eventQueue.getClass().getDeclaredMethod("dispatchEvent", AWTEvent.class).invoke(eventQueue, event);
            }
        } catch (Exception e) {
            LOG.error(e);
        }
        return true;
    }


    /**
     * @see #dispatchAllInvocationEvents()
     */
    @TestOnly
    public static void pump() {
        assert !SwingUtilities.isEventDispatchThread();
        final BlockingQueue<Object> queue = new LinkedBlockingQueue<Object>();
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                queue.offer(queue);
            }
        });
        try {
            queue.take();
        } catch (InterruptedException e) {
            LOG.error(e);
        }
    }


    public static void addParentChangeListener(@NotNull Component component, @NotNull PropertyChangeListener listener) {
        component.addPropertyChangeListener("ancestor", listener);
    }

    public static void removeParentChangeListener(@NotNull Component component, @NotNull PropertyChangeListener listener) {
        component.removePropertyChangeListener("ancestor", listener);
    }

    public static boolean isFocusAncestor(@NotNull final JComponent component) {
        final Component owner = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
        if (owner == null) return false;
        if (owner == component) return true;
        return SwingUtilities.isDescendingFrom(owner, component);
    }


    public static boolean isCloseClick(MouseEvent e) {
        return isCloseClick(e, MouseEvent.MOUSE_PRESSED);
    }

    public static boolean isCloseClick(MouseEvent e, int effectiveType) {
        if (e.isPopupTrigger() || e.getID() != effectiveType) return false;
        return e.getButton() == MouseEvent.BUTTON2 || e.getButton() == MouseEvent.BUTTON1 && e.isShiftDown();
    }

    public static boolean isActionClick(MouseEvent e) {
        return isActionClick(e, MouseEvent.MOUSE_PRESSED);
    }

    public static boolean isActionClick(MouseEvent e, int effectiveType) {
        return isActionClick(e, effectiveType, false);
    }

    public static boolean isActionClick(MouseEvent e, int effectiveType, boolean allowShift) {
        if (!allowShift && isCloseClick(e) || e.isPopupTrigger() || e.getID() != effectiveType) return false;
        return e.getButton() == MouseEvent.BUTTON1;
    }

    @NotNull
    public static Color getBgFillColor(@NotNull Component c) {
        final Component parent = findNearestOpaque(c);
        return parent == null ? c.getBackground() : parent.getBackground();
    }

    @Nullable
    public static Component findNearestOpaque(Component c) {
        return findParentByCondition(c, new Condition<Component>() {
            @Override
            public boolean value(Component component) {
                return component.isOpaque();
            }
        });
    }

    @Nullable
    public static Component findParentByCondition(@NotNull Component c, Condition<Component> condition) {
        Component eachParent = c;
        while (eachParent != null) {
            if (condition.value(eachParent)) return eachParent;
            eachParent = eachParent.getParent();
        }
        return null;
    }

    @Deprecated
    public static <T extends Component> T findParentByClass(@NotNull Component c, Class<T> cls) {
        return getParentOfType(cls, c);
    }

    @Language("HTML")
    public static String getCssFontDeclaration(@NotNull Font font) {
        return getCssFontDeclaration(font, null, null, null);
    }

    @Language("HTML")
    public static String getCssFontDeclaration(@NotNull Font font, @Nullable Color fgColor, @Nullable Color linkColor, @Nullable String liImg) {
        StringBuilder builder = new StringBuilder().append("<style>\n");
        String familyAndSize = "font-family:'" + font.getFamily() + "'; font-size:" + font.getSize() + "pt;";

        builder.append("body, div, td, p {").append(familyAndSize);
        if (fgColor != null) builder.append(" color:#").append(ColorUtil.toHex(fgColor)).append(';');
        builder.append("}\n");

        builder.append("a {").append(familyAndSize);
        if (linkColor != null) builder.append(" color:#").append(ColorUtil.toHex(linkColor)).append(';');
        builder.append("}\n");

        builder.append("code {font-size:").append(font.getSize()).append("pt;}\n");

        URL resource = liImg != null ? SystemInfo.class.getResource(liImg) : null;
        if (resource != null) {
            builder.append("ul {list-style-image:url('").append(StringUtil.escapeCharCharacters(resource.toExternalForm())).append("');}\n");
        }

        return builder.append("</style>").toString();
    }

    public static boolean isWinLafOnVista() {
        return SystemInfo.isWinVistaOrNewer && "Windows".equals(UIManager.getLookAndFeel().getName());
    }

    public static boolean isStandardMenuLAF() {
        return isWinLafOnVista() ||
                isUnderNimbusLookAndFeel() ||
                isUnderGTKLookAndFeel();
    }

    public static Color getFocusedFillColor() {
        return toAlpha(getListSelectionBackground(), 100);
    }

    public static Color getFocusedBoundsColor() {
        return getBoundsColor();
    }

    public static Color getBoundsColor() {
        return getBorderColor();
    }

    public static Color getBoundsColor(boolean focused) {
        return focused ? getFocusedBoundsColor() : getBoundsColor();
    }

    public static Color toAlpha(final Color color, final int alpha) {
        Color actual = color != null ? color : Color.black;
        return new Color(actual.getRed(), actual.getGreen(), actual.getBlue(), alpha);
    }

    /**
     * @param component to check whether it can be focused or not
     * @return {@code true} if component is not {@code null} and can be focused
     * @see Component#isRequestFocusAccepted(boolean, boolean, sun.awt.CausedFocusEvent.Cause)
     */
    public static boolean isFocusable(JComponent component) {
        return component != null && component.isFocusable() && component.isEnabled() && component.isShowing();
    }

    public static void requestFocus(@NotNull final JComponent c) {
        if (c.isShowing()) {
            c.requestFocus();
        } else {
            SwingUtilities.invokeLater(new Runnable() {
                @Override
                public void run() {
                    c.requestFocus();
                }
            });
        }
    }

    //todo maybe should do for all kind of listeners via the AWTEventMulticaster class

    public static void dispose(final Component c) {
        if (c == null) return;

        final MouseListener[] mouseListeners = c.getMouseListeners();
        for (MouseListener each : mouseListeners) {
            c.removeMouseListener(each);
        }

        final MouseMotionListener[] motionListeners = c.getMouseMotionListeners();
        for (MouseMotionListener each : motionListeners) {
            c.removeMouseMotionListener(each);
        }

        final MouseWheelListener[] mouseWheelListeners = c.getMouseWheelListeners();
        for (MouseWheelListener each : mouseWheelListeners) {
            c.removeMouseWheelListener(each);
        }

        if (c instanceof AbstractButton) {
            final ActionListener[] listeners = ((AbstractButton) c).getActionListeners();
            for (ActionListener listener : listeners) {
                ((AbstractButton) c).removeActionListener(listener);
            }
        }
    }


    @Nullable
    public static Component findUltimateParent(Component c) {
        if (c == null) return null;

        Component eachParent = c;
        while (true) {
            if (eachParent.getParent() == null) return eachParent;
            eachParent = eachParent.getParent();
        }
    }

    public static Color getHeaderActiveColor() {
        return ACTIVE_HEADER_COLOR;
    }

    public static Color getHeaderInactiveColor() {
        return INACTIVE_HEADER_COLOR;
    }

    /**
     * @use JBColor.border()
     * @deprecated
     */
    public static Color getBorderColor() {
        return isUnderDarcula() ? Gray._50 : BORDER_COLOR;
    }

    public static Font getTitledBorderFont() {
        Font defFont = getLabelFont();
        return defFont.deriveFont(defFont.getSize() - 1f);
    }

    /**
     * @deprecated use getBorderColor instead
     */
    public static Color getBorderInactiveColor() {
        return getBorderColor();
    }

    /**
     * @deprecated use getBorderColor instead
     */
    public static Color getBorderActiveColor() {
        return getBorderColor();
    }

    /**
     * @deprecated use getBorderColor instead
     */
    public static Color getBorderSeparatorColor() {
        return getBorderColor();
    }


    public static HTMLEditorKit getHTMLEditorKit() {
        return getHTMLEditorKit(true);
    }

    public static HTMLEditorKit getHTMLEditorKit(boolean noGapsBetweenParagraphs) {
        Font font = getLabelFont();
        @NonNls String family = !SystemInfo.isWindows && font != null ? font.getFamily() : "Tahoma";
        int size = font != null ? font.getSize() : JBUI.scale(11);

        String customCss = String.format("body, div, p { font-family: %s; font-size: %s; }", family, size);
        if (noGapsBetweenParagraphs) {
            customCss += " p { margin-top: 0; }";
        }

        final StyleSheet style = new StyleSheet();
        style.addStyleSheet(isUnderDarcula() ? (StyleSheet) UIManager.getDefaults().get("StyledEditorKit.JBDefaultStyle") : DEFAULT_HTML_KIT_CSS);
        style.addRule(customCss);

        return new HTMLEditorKit() {
            @Override
            public StyleSheet getStyleSheet() {
                return style;
            }
        };
    }


    public static Point getCenterPoint(Dimension container, Dimension child) {
        return getCenterPoint(new Rectangle(container), child);
    }

    public static Point getCenterPoint(Rectangle container, Dimension child) {
        return new Point(
                container.x + (container.width - child.width) / 2,
                container.y + (container.height - child.height) / 2
        );
    }

    public static String toHtml(String html) {
        return toHtml(html, 0);
    }

    @NonNls
    public static String toHtml(String html, final int hPadding) {
        html = CLOSE_TAG_PATTERN.matcher(html).replaceAll("<$1$2></$1>");
        Font font = getLabelFont();
        @NonNls String family = font != null ? font.getFamily() : "Tahoma";
        int size = font != null ? font.getSize() : JBUI.scale(11);
        return "<html><style>body { font-family: "
                + family + "; font-size: "
                + size + ";} ul li {list-style-type:circle;}</style>"
                + addPadding(html, hPadding) + "</html>";
    }

    public static String addPadding(final String html, int hPadding) {
        return String.format("<p style=\"margin: 0 %dpx 0 %dpx;\">%s</p>", hPadding, hPadding, html);
    }

    @NotNull
    public static String convertSpace2Nbsp(@NotNull String html) {
        @NonNls StringBuilder result = new StringBuilder();
        int currentPos = 0;
        int braces = 0;
        while (currentPos < html.length()) {
            String each = html.substring(currentPos, currentPos + 1);
            if ("<".equals(each)) {
                braces++;
            } else if (">".equals(each)) {
                braces--;
            }

            if (" ".equals(each) && braces == 0) {
                result.append("&nbsp;");
            } else {
                result.append(each);
            }
            currentPos++;
        }

        return result.toString();
    }


    public static boolean isFocusProxy(@Nullable Component c) {
        return c instanceof JComponent && Boolean.TRUE.equals(((JComponent) c).getClientProperty(FOCUS_PROXY_KEY));
    }

    public static void setFocusProxy(JComponent c, boolean isProxy) {
        c.putClientProperty(FOCUS_PROXY_KEY, isProxy ? Boolean.TRUE : null);
    }

    public static void maybeInstall(InputMap map, String action, KeyStroke stroke) {
        if (map.get(stroke) == null) {
            map.put(stroke, action);
        }
    }

    /**
     * Avoid blinking while changing background.
     *
     * @param component  component.
     * @param background new background.
     */
    public static void changeBackGround(final Component component, final Color background) {
        final Color oldBackGround = component.getBackground();
        if (background == null || !background.equals(oldBackGround)) {
            component.setBackground(background);
        }
    }

    private static String systemLaFClassName;

    public static String getSystemLookAndFeelClassName() {
        if (systemLaFClassName != null) {
            return systemLaFClassName;
        } else if (SystemInfo.isLinux) {
            // Normally, GTK LaF is considered "system" when:
            // 1) Gnome session is run
            // 2) gtk lib is available
            // Here we weaken the requirements to only 2) and force GTK LaF
            // installation in order to let it properly scale default font
            // based on Xft.dpi value.
            try {
                String name = "com.sun.java.swing.plaf.gtk.GTKLookAndFeel";
                Class cls = Class.forName(name);
                LookAndFeel laf = (LookAndFeel) cls.newInstance();
                if (laf.isSupportedLookAndFeel()) { // if gtk lib is available
                    return systemLaFClassName = name;
                }
            } catch (Exception ignore) {
            }
        }
        return systemLaFClassName = UIManager.getSystemLookAndFeelClassName();
    }

    public static void initDefaultLAF() {
        try {
            UIManager.setLookAndFeel(getSystemLookAndFeelClassName());
            initSystemFontData();
        } catch (Exception ignore) {
        }
    }

    public static void initSystemFontData() {
        if (ourSystemFontData != null) return;

        // With JB Linux JDK the label font comes properly scaled based on Xft.dpi settings.
        Font font = getLabelFont();

        Float forcedScale = null;

        if (SystemInfo.isLinux && !SystemInfo.isJetbrainsJvm) {
            // With Oracle JDK: derive scale from X server DPI
            float scale = getScreenScale();
            if (scale > 1f) {
                forcedScale = Float.valueOf(scale);
            }
            // Or otherwise leave the detected font. It's undetermined if it's scaled or not.
            // If it is (likely with GTK DE), then the UI scale will be derived from it,
            // if it's not, then IDEA will start unscaled. This lets the users of GTK DEs
            // not to bother about X server DPI settings. Users of other DEs (like KDE)
            // will have to set X server DPI to meet their display.
        } else if (SystemInfo.isWindows) {
            //noinspection HardCodedStringLiteral
            Font winFont = (Font) Toolkit.getDefaultToolkit().getDesktopProperty("win.messagebox.font");
            if (winFont != null) {
                font = winFont; // comes scaled
            }
        }
        if (forcedScale != null) {
            // With forced scale, we derive font from a hard-coded value as we cannot be sure
            // the system font comes unscaled.
            font = font.deriveFont(DEF_SYSTEM_FONT_SIZE * forcedScale.floatValue());
        }
        ourSystemFontData = Pair.create(font.getName(), font.getSize());
    }

    @Nullable
    public static Pair<String, Integer> getSystemFontData() {
        return ourSystemFontData;
    }

    private static float getScreenScale() {
        int dpi = 96;
        try {
            dpi = Toolkit.getDefaultToolkit().getScreenResolution();
        } catch (HeadlessException e) {
        }
        float scale = 1f;
        if (dpi < 120) scale = 1f;
        else if (dpi < 144) scale = 1.25f;
        else if (dpi < 168) scale = 1.5f;
        else if (dpi < 192) scale = 1.75f;
        else scale = 2f;

        return scale;
    }

    public static void addKeyboardShortcut(final JComponent target, final AbstractButton button, final KeyStroke keyStroke) {
        target.registerKeyboardAction(
                new ActionListener() {
                    @Override
                    public void actionPerformed(ActionEvent e) {
                        if (button.isEnabled()) {
                            button.doClick();
                        }
                    }
                },
                keyStroke,
                JComponent.WHEN_FOCUSED
        );
    }

    public static void installComboBoxCopyAction(JComboBox comboBox) {
        final ComboBoxEditor editor = comboBox.getEditor();
        final Component editorComponent = editor != null ? editor.getEditorComponent() : null;
        if (!(editorComponent instanceof JTextComponent)) return;
        final InputMap inputMap = ((JTextComponent) editorComponent).getInputMap();
        for (KeyStroke keyStroke : inputMap.allKeys()) {
            if (DefaultEditorKit.copyAction.equals(inputMap.get(keyStroke))) {
                comboBox.getInputMap().put(keyStroke, DefaultEditorKit.copyAction);
            }
        }
        comboBox.getActionMap().put(DefaultEditorKit.copyAction, new AbstractAction() {
            @Override
            public void actionPerformed(final ActionEvent e) {
                if (!(e.getSource() instanceof JComboBox)) return;
                final JComboBox comboBox = (JComboBox) e.getSource();
                final String text;
                final Object selectedItem = comboBox.getSelectedItem();
                if (selectedItem instanceof String) {
                    text = (String) selectedItem;
                } else {
                    final Component component =
                            comboBox.getRenderer().getListCellRendererComponent(new JList(), selectedItem, 0, false, false);
                    if (component instanceof JLabel) {
                        text = ((JLabel) component).getText();
                    } else if (component != null) {
                        final String str = component.toString();
                        // skip default Component.toString and handle SimpleColoredComponent case
                        text = str == null || str.startsWith(component.getClass().getName() + "[") ? null : str;
                    } else {
                        text = null;
                    }
                }
                if (text != null) {
                    final JTextField textField = new JTextField(text);
                    textField.selectAll();
                    textField.copy();
                }
            }
        });
    }

    @Nullable
    public static ComboPopup getComboBoxPopup(@NotNull JComboBox comboBox) {
        final ComboBoxUI ui = comboBox.getUI();
        if (ui instanceof BasicComboBoxUI) {
            return ReflectionUtil.getField(BasicComboBoxUI.class, ui, ComboPopup.class, "popup");
        }

        return null;
    }

    @SuppressWarnings({"HardCodedStringLiteral"})
    public static void fixFormattedField(JFormattedTextField field) {
        if (SystemInfo.isMac) {
            final Toolkit toolkit = Toolkit.getDefaultToolkit();
            final int commandKeyMask = toolkit.getMenuShortcutKeyMask();
            final InputMap inputMap = field.getInputMap();
            final KeyStroke copyKeyStroke = KeyStroke.getKeyStroke(KeyEvent.VK_C, commandKeyMask);
            inputMap.put(copyKeyStroke, "copy-to-clipboard");
            final KeyStroke pasteKeyStroke = KeyStroke.getKeyStroke(KeyEvent.VK_V, commandKeyMask);
            inputMap.put(pasteKeyStroke, "paste-from-clipboard");
            final KeyStroke cutKeyStroke = KeyStroke.getKeyStroke(KeyEvent.VK_X, commandKeyMask);
            inputMap.put(cutKeyStroke, "cut-to-clipboard");
        }
    }

    public static boolean isPrinting(Graphics g) {
        return g instanceof PrintGraphics;
    }

    public static int getSelectedButton(ButtonGroup group) {
        Enumeration<AbstractButton> enumeration = group.getElements();
        int i = 0;
        while (enumeration.hasMoreElements()) {
            AbstractButton button = enumeration.nextElement();
            if (group.isSelected(button.getModel())) {
                return i;
            }
            i++;
        }
        return -1;
    }

    public static void setSelectedButton(ButtonGroup group, int index) {
        Enumeration<AbstractButton> enumeration = group.getElements();
        int i = 0;
        while (enumeration.hasMoreElements()) {
            AbstractButton button = enumeration.nextElement();
            group.setSelected(button.getModel(), index == i);
            i++;
        }
    }

    public static boolean isSelectionButtonDown(MouseEvent e) {
        return e.isShiftDown() || e.isControlDown() || e.isMetaDown();
    }

    @SuppressWarnings("deprecation")
    public static void setComboBoxEditorBounds(int x, int y, int width, int height, JComponent editor) {
        if (SystemInfo.isMac && isUnderAquaLookAndFeel()) {
            // fix for too wide combobox editor, see AquaComboBoxUI.layoutContainer:
            // it adds +4 pixels to editor width. WTF?!
            editor.reshape(x, y, width - 4, height - 1);
        } else {
            editor.reshape(x, y, width, height);
        }
    }

    public static int fixComboBoxHeight(final int height) {
        return SystemInfo.isMac && isUnderAquaLookAndFeel() ? 28 : height;
    }

    public static final int LIST_FIXED_CELL_HEIGHT = 20;

    /**
     * The main difference from javax.swing.SwingUtilities#isDescendingFrom(Component, Component) is that this method
     * uses getInvoker() instead of getParent() when it meets JPopupMenu
     *
     * @param child  child component
     * @param parent parent component
     * @return true if parent if a top parent of child, false otherwise
     * @see SwingUtilities#isDescendingFrom(Component, Component)
     */
    public static boolean isDescendingFrom(@Nullable Component child, @NotNull Component parent) {
        while (child != null && child != parent) {
            child = child instanceof JPopupMenu ? ((JPopupMenu) child).getInvoker()
                    : child.getParent();
        }
        return child == parent;
    }

    /**
     * Searches above in the component hierarchy starting from the specified component.
     * Note that the initial component is also checked.
     *
     * @param type      expected class
     * @param component initial component
     * @return a component of the specified type, or {@code null} if the search is failed
     * @see SwingUtilities#getAncestorOfClass
     */
    @Nullable
    public static <T> T getParentOfType(@NotNull Class<? extends T> type, Component component) {
        while (component != null) {
            if (type.isInstance(component)) {
                //noinspection unchecked
                return (T) component;
            }
            component = component.getParent();
        }
        return null;
    }


    private static final Function.Mono<Component> COMPONENT_PARENT = new Function.Mono<Component>() {
        @Override
        public Component fun(Component c) {
            return c.getParent();
        }
    };


    public static void scrollListToVisibleIfNeeded(@NotNull final JList list) {
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                final int selectedIndex = list.getSelectedIndex();
                if (selectedIndex >= 0) {
                    final Rectangle visibleRect = list.getVisibleRect();
                    final Rectangle cellBounds = list.getCellBounds(selectedIndex, selectedIndex);
                    if (!visibleRect.contains(cellBounds)) {
                        list.scrollRectToVisible(cellBounds);
                    }
                }
            }
        });
    }

    @Nullable
    public static <T extends JComponent> T findComponentOfType(JComponent parent, Class<T> cls) {
        if (parent == null || cls.isAssignableFrom(parent.getClass())) {
            @SuppressWarnings({"unchecked"}) final T t = (T) parent;
            return t;
        }
        for (Component component : parent.getComponents()) {
            if (component instanceof JComponent) {
                T comp = findComponentOfType((JComponent) component, cls);
                if (comp != null) return comp;
            }
        }
        return null;
    }

    public static <T extends JComponent> List<T> findComponentsOfType(JComponent parent, Class<T> cls) {
        final ArrayList<T> result = new ArrayList<T>();
        findComponentsOfType(parent, cls, result);
        return result;
    }

    private static <T extends JComponent> void findComponentsOfType(JComponent parent, Class<T> cls, ArrayList<T> result) {
        if (parent == null) return;
        if (cls.isAssignableFrom(parent.getClass())) {
            @SuppressWarnings({"unchecked"}) final T t = (T) parent;
            result.add(t);
        }
        for (Component c : parent.getComponents()) {
            if (c instanceof JComponent) {
                findComponentsOfType((JComponent) c, cls, result);
            }
        }
    }


    @Nullable
    public static JRootPane getRootPane(Component c) {
        JRootPane root = getParentOfType(JRootPane.class, c);
        if (root != null) return root;
        Component eachParent = c;
        while (eachParent != null) {
            if (eachParent instanceof JComponent) {
                @SuppressWarnings({"unchecked"}) WeakReference<JRootPane> pane =
                        (WeakReference<JRootPane>) ((JComponent) eachParent).getClientProperty(ROOT_PANE);
                if (pane != null) return pane.get();
            }
            eachParent = eachParent.getParent();
        }

        return null;
    }

    public static void setFutureRootPane(JComponent c, JRootPane pane) {
        c.putClientProperty(ROOT_PANE, new WeakReference<JRootPane>(pane));
    }

    public static boolean isMeaninglessFocusOwner(@Nullable Component c) {
        if (c == null || !c.isShowing()) return true;

        return c instanceof JFrame || c instanceof JDialog || c instanceof JWindow || c instanceof JRootPane || isFocusProxy(c);
    }

    @NotNull
    public static Timer createNamedTimer(@NonNls @NotNull final String name, int delay, @NotNull ActionListener listener) {
        return new Timer(delay, listener) {
            @Override
            public String toString() {
                return name;
            }
        };
    }

    @NotNull
    public static Timer createNamedTimer(@NonNls @NotNull final String name, int delay) {
        return new Timer(delay, null) {
            @Override
            public String toString() {
                return name;
            }
        };
    }

    public static boolean isDialogRootPane(JRootPane rootPane) {
        if (rootPane != null) {
            final Object isDialog = rootPane.getClientProperty("DIALOG_ROOT_PANE");
            return isDialog instanceof Boolean && ((Boolean) isDialog).booleanValue();
        }
        return false;
    }


    public static void setNotOpaqueRecursively(@NotNull Component component) {
        if (!isUnderAquaLookAndFeel()) return;

        if (component.getBackground().equals(getPanelBackground())
                || component instanceof JScrollPane
                || component instanceof JViewport
                || component instanceof JLayeredPane) {
            if (component instanceof JComponent) {
                ((JComponent) component).setOpaque(false);
            }
            if (component instanceof Container) {
                for (Component c : ((Container) component).getComponents()) {
                    setNotOpaqueRecursively(c);
                }
            }
        }
    }

    public static void setBackgroundRecursively(@NotNull Component component, @NotNull Color bg) {
        component.setBackground(bg);
        if (component instanceof Container) {
            for (Component c : ((Container) component).getComponents()) {
                setBackgroundRecursively(c, bg);
            }
        }
    }

    /**
     * Adds an empty border with the specified insets to the specified component.
     * If the component already has a border it will be preserved.
     *
     * @param component the component to which border added
     * @param top       the inset from the top
     * @param left      the inset from the left
     * @param bottom    the inset from the bottom
     * @param right     the inset from the right
     */
    public static void addInsets(@NotNull JComponent component, int top, int left, int bottom, int right) {
        addBorder(component, BorderFactory.createEmptyBorder(top, left, bottom, right));
    }

    /**
     * Adds an empty border with the specified insets to the specified component.
     * If the component already has a border it will be preserved.
     *
     * @param component the component to which border added
     * @param insets    the top, left, bottom, and right insets
     */
    public static void addInsets(@NotNull JComponent component, @NotNull Insets insets) {
        addInsets(component, insets.top, insets.left, insets.bottom, insets.right);
    }

    public static void adjustWindowToMinimumSize(final Window window) {
        if (window == null) return;
        final Dimension minSize = window.getMinimumSize();
        final Dimension size = window.getSize();
        final Dimension newSize = new Dimension(Math.max(size.width, minSize.width), Math.max(size.height, minSize.height));

        if (!newSize.equals(size)) {
            //noinspection SSBasedInspection
            SwingUtilities.invokeLater(new Runnable() {
                @Override
                public void run() {
                    if (window.isShowing()) {
                        window.setSize(newSize);
                    }
                }
            });
        }
    }

    @Nullable
    public static Color getColorAt(final Icon icon, final int x, final int y) {
        if (0 <= x && x < icon.getIconWidth() && 0 <= y && y < icon.getIconHeight()) {
            final BufferedImage image = createImage(icon.getIconWidth(), icon.getIconHeight(), BufferedImage.TYPE_INT_RGB);
            icon.paintIcon(null, image.getGraphics(), 0, 0);

            final int[] pixels = new int[1];
            final PixelGrabber pixelGrabber = new PixelGrabber(image, x, y, 1, 1, pixels, 0, 1);
            try {
                pixelGrabber.grabPixels();
                return new Color(pixels[0]);
            } catch (InterruptedException ignored) {
            }
        }

        return null;
    }


    /**
     * Adds the specified border to the specified component.
     * If the component already has a border it will be preserved.
     * If component or border is not specified nothing happens.
     *
     * @param component the component to which border added
     * @param border    the border to add to the component
     */
    public static void addBorder(JComponent component, Border border) {
        if (component != null && border != null) {
            Border old = component.getBorder();
            if (old != null) {
                border = BorderFactory.createCompoundBorder(border, old);
            }
            component.setBorder(border);
        }
    }

    private static final Color DECORATED_ROW_BG_COLOR = new JBColor(new Color(242, 245, 249), new Color(65, 69, 71));

    public static Color getDecoratedRowColor() {
        return DECORATED_ROW_BG_COLOR;
    }

    @Nullable
    public static Point getLocationOnScreen(@NotNull JComponent component) {
        int dx = 0;
        int dy = 0;
        for (Container c = component; c != null; c = c.getParent()) {
            if (c.isShowing()) {
                Point locationOnScreen = c.getLocationOnScreen();
                locationOnScreen.translate(dx, dy);
                return locationOnScreen;
            } else {
                Point location = c.getLocation();
                dx += location.x;
                dy += location.y;
            }
        }
        return null;
    }

    @NotNull
    public static Window getActiveWindow() {
        Window[] windows = Window.getWindows();
        for (Window each : windows) {
            if (each.isVisible() && each.isActive()) return each;
        }
        return JOptionPane.getRootFrame();
    }

    public static void suppressFocusStealing(Window window) {
        // Focus stealing is not a problem on Mac
        if (SystemInfo.isMac) return;
        if (Registry.is("suppress.focus.stealing")) {
            setAutoRequestFocus(window, false);
        }
    }

    public static void setAutoRequestFocus(final Window onWindow, final boolean set) {
        if (SystemInfo.isMac) return;
        if (SystemInfo.isJavaVersionAtLeast("1.7")) {
            try {
                Method setAutoRequestFocusMethod = onWindow.getClass().getMethod("setAutoRequestFocus", boolean.class);
                setAutoRequestFocusMethod.invoke(onWindow, set);
            } catch (NoSuchMethodException e) {
                LOG.debug(e);
            } catch (InvocationTargetException e) {
                LOG.debug(e);
            } catch (IllegalAccessException e) {
                LOG.debug(e);
            }
        }
    }

    //May have no usages but it's useful in runtime (Debugger "watches", some logging etc.)
    public static String getDebugText(Component c) {
        StringBuilder builder = new StringBuilder();
        getAllTextsRecursivelyImpl(c, builder);
        return builder.toString();
    }

    private static void getAllTextsRecursivelyImpl(Component component, StringBuilder builder) {
        String candidate = "";
        int limit = builder.length() > 60 ? 20 : 40;
        if (component instanceof JLabel) candidate = ((JLabel) component).getText();
        if (component instanceof JTextComponent) candidate = ((JTextComponent) component).getText();
        if (component instanceof AbstractButton) candidate = ((AbstractButton) component).getText();
        if (StringUtil.isNotEmpty(candidate)) {
            builder.append(candidate.length() > limit ? (candidate.substring(0, limit - 3) + "...") : candidate).append('|');
        }
        if (component instanceof Container) {
            Component[] components = ((Container) component).getComponents();
            for (Component child : components) {
                getAllTextsRecursivelyImpl(child, builder);
            }
        }
    }

    public static boolean isAncestor(@NotNull Component ancestor, @Nullable Component descendant) {
        while (descendant != null) {
            if (descendant == ancestor) {
                return true;
            }
            descendant = descendant.getParent();
        }
        return false;
    }


    private static Map<String, String> ourRealFontFamilies;

    //Experimental, seems to be reliable under MacOS X only

    public static String getRealFontFamily(String genericFontFamily) {
        if (ourRealFontFamilies != null && ourRealFontFamilies.get(genericFontFamily) != null) {
            return ourRealFontFamilies.get(genericFontFamily);
        }
        String pattern = "Real Font Family";
        List<String> GENERIC = Arrays.asList(Font.DIALOG, Font.DIALOG_INPUT, Font.MONOSPACED, Font.SANS_SERIF, Font.SERIF);
        int patternSize = 50;
        BufferedImage image = createImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        Graphics graphics = image.getGraphics();
        graphics.setFont(new Font(genericFontFamily, Font.PLAIN, patternSize));
        Object patternBounds = graphics.getFontMetrics().getStringBounds(pattern, graphics);
        for (String family : GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames()) {
            if (GENERIC.contains(family)) continue;
            graphics.setFont(new Font(family, Font.PLAIN, patternSize));
            if (graphics.getFontMetrics().getStringBounds(pattern, graphics).equals(patternBounds)) {
                if (ourRealFontFamilies == null) {
                    ourRealFontFamilies = new HashMap<String, String>();
                }
                ourRealFontFamilies.put(genericFontFamily, family);
                return family;
            }
        }
        return genericFontFamily;
    }

    /**
     * It is your responsibility to set correct horizontal align (left in case of UI Designer)
     */
    public static void configureNumericFormattedTextField(@NotNull JFormattedTextField textField) {
        NumberFormat format = NumberFormat.getIntegerInstance();
        format.setParseIntegerOnly(true);
        format.setGroupingUsed(false);
        NumberFormatter numberFormatter = new NumberFormatter(format);
        numberFormatter.setMinimum(0);
        textField.setFormatterFactory(new DefaultFormatterFactory(numberFormatter));
        textField.setHorizontalAlignment(SwingConstants.TRAILING);

        textField.setColumns(4);
    }

    /**
     * Returns the first window ancestor of the component.
     * Note that this method returns the component itself if it is a window.
     *
     * @param component the component used to find corresponding window
     * @return the first window ancestor of the component; or {@code null}
     * if the component is not a window and is not contained inside a window
     */
    public static Window getWindow(Component component) {
        return component instanceof Window ? (Window) component : SwingUtilities.getWindowAncestor(component);
    }

    /**
     * Places the specified window at the top of the stacking order and shows it in front of any other windows.
     * If the window is iconified it will be shown anyway.
     *
     * @param window the window to activate
     */
    public static void toFront(Window window) {
        if (window instanceof Frame) {
            Frame frame = (Frame) window;
            frame.setState(Frame.NORMAL);
        }
        if (window != null) {
            window.toFront();
        }
    }

    public static Image getDebugImage(Component component) {
        BufferedImage image = createImage(component.getWidth(), component.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(Color.RED);
        graphics.fillRect(0, 0, component.getWidth() + 1, component.getHeight() + 1);
        component.paint(graphics);
        return image;
    }

    /**
     * Indicates whether the specified component is scrollable or it contains a scrollable content.
     */
    public static boolean hasScrollPane(@NotNull Component component) {
        return hasComponentOfType(component, JScrollPane.class);
    }

    /**
     * Indicates whether the specified component is instance of one of the specified types
     * or it contains an instance of one of the specified types.
     */
    public static boolean hasComponentOfType(Component component, Class<?>... types) {
        for (Class<?> type : types) {
            if (type.isAssignableFrom(component.getClass())) {
                return true;
            }
        }
        if (component instanceof Container) {
            Container container = (Container) component;
            for (int i = 0; i < container.getComponentCount(); i++) {
                if (hasComponentOfType(container.getComponent(i), types)) {
                    return true;
                }
            }
        }
        return false;
    }

    public static void setColumns(JTextComponent textComponent, int columns) {
        if (textComponent instanceof JTextField) {
            ((JTextField) textComponent).setColumns(columns);
        }
        if (textComponent instanceof JTextArea) {
            ((JTextArea) textComponent).setColumns(columns);
        }
    }

    /**
     * Returns the first focusable component in the specified container.
     * This method returns {@code null} if container is {@code null},
     * or if focus traversal policy cannot be determined,
     * or if found focusable component is not a {@link JComponent}.
     *
     * @param container a container whose first focusable component is to be returned
     * @return the first focusable component or {@code null} if it cannot be found
     */
    public static JComponent getPreferredFocusedComponent(Container container) {
        Container parent = container;
        if (parent == null) return null;
        FocusTraversalPolicy policy = parent.getFocusTraversalPolicy();
        while (policy == null) {
            parent = parent.getParent();
            if (parent == null) return null;
            policy = parent.getFocusTraversalPolicy();
        }
        Component component = policy.getFirstComponent(container);
        return component instanceof JComponent ? (JComponent) component : null;
    }

    /**
     * Calculates a component style from the corresponding client property.
     * The key "JComponent.sizeVariant" is used by Apple's L&F to scale components.
     *
     * @param component a component to process
     * @return a component style of the specified component
     */
    public static ComponentStyle getComponentStyle(Component component) {
        if (component instanceof JComponent) {
            Object property = ((JComponent) component).getClientProperty("JComponent.sizeVariant");
            if ("large".equals(property)) return ComponentStyle.LARGE;
            if ("small".equals(property)) return ComponentStyle.SMALL;
            if ("mini".equals(property)) return ComponentStyle.MINI;
        }
        return ComponentStyle.REGULAR;
    }

    public static int getLcdContrastValue() {
        int lcdContrastValue = 0;

        // Evaluate the value depending on our current theme
        if (lcdContrastValue == 0) {
            if (SystemInfo.isMacIntel64) {
                lcdContrastValue = isUnderDarcula() ? 140 : 230;
            } else {
                Map map = (Map) Toolkit.getDefaultToolkit().getDesktopProperty("awt.font.desktophints");

                if (map == null) {
                    lcdContrastValue = 140;
                } else {
                    Object o = map.get(RenderingHints.KEY_TEXT_LCD_CONTRAST);
                    lcdContrastValue = (o == null) ? 140 : ((Integer) o);
                }
            }
        }

        if (lcdContrastValue < 100 || lcdContrastValue > 250) {
            // the default value
            lcdContrastValue = 140;
        }

        return lcdContrastValue;
    }

}
