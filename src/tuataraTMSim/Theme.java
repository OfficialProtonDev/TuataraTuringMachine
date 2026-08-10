//  ------------------------------------------------------------------
//
//  Copyright (c) 2006-2007 James Foulds and the University of Waikato
//
//  ------------------------------------------------------------------
//  This file is part of Tuatara Turing Machine Simulator.
//
//  Tuatara Turing Machine Simulator is free software: you can redistribute
//  it and/or modify it under the terms of the GNU General Public License as
//  published by the Free Software Foundation, either version 3 of the License,
//  or (at your option) any later version.
//
//  Tuatara Turing Machine Simulator is distributed in the hope that it will be
//  useful, but WITHOUT ANY WARRANTY; without even the implied warranty of
//  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
//  GNU General Public License for more details.
//
//  You should have received a copy of the GNU General Public License
//  along with Tuatara Turing Machine Simulator.  If not, see
//  <http://www.gnu.org/licenses/>.
//
//  author email: jf47 (at) waikato (dot) ac (dot) nz
//
//  ------------------------------------------------------------------

package tuataraTMSim;

import java.awt.*;
import java.awt.geom.*;
import java.util.ArrayList;
import javax.swing.*;
import javax.swing.plaf.ColorUIResource;
import javax.swing.plaf.FontUIResource;
import javax.swing.plaf.basic.BasicScrollBarUI;
import javax.swing.plaf.metal.DefaultMetalTheme;
import javax.swing.plaf.metal.MetalLookAndFeel;

/**
 * The single source of truth for the appearance of the program. Owns the colour palettes, the font
 * stack, the look-and-feel installation, and a handful of Java2D helpers used by the custom-painted
 * components.
 *
 * Colours must always be read through {@link #palette} at paint time rather than cached in fields.
 * The types in the machine package are serialized to disk, so they cannot hold colour state, and
 * caching would in any case break live switching between the light and dark palettes.
 */
public final class Theme
{
    // Undocumented intentionally. This class should not be instantiated.
    private Theme() { }

    /**
     * A named set of colour roles. Components refer to roles rather than to literal colours, so
     * that a second palette is purely a matter of data.
     */
    public static final class Palette
    {
        /**
         * Creates a new palette. Package-private; the two instances below are the only ones.
         */
        private Palette(String name, boolean dark)
        {
            this.name = name;
            this.dark = dark;
        }

        /**
         * Human-readable name of the palette, as shown in the appearance menu.
         */
        public final String name;

        /**
         * Whether this is a dark palette. Used where a shadow or highlight must flip direction.
         */
        public final boolean dark;

        // Application chrome.

        /** Window and toolbar background. */
        public Color background;
        /** Raised surfaces: cards, popups, the selected tab. */
        public Color surface;
        /** Recessed surfaces: gutters, table stripes. */
        public Color surfaceAlt;
        /** Background of a control under the mouse. */
        public Color surfaceHover;
        /** Background of a control being pressed. */
        public Color surfacePressed;
        /** Hairline separators and control outlines. */
        public Color border;
        /** Outlines needing more emphasis than {@link #border}. */
        public Color borderStrong;

        // Text.

        /** Primary text. */
        public Color text;
        /** Secondary text: captions, timestamps, disabled labels. */
        public Color textMuted;

        // Accent and status.

        /** Primary accent, used for the active tool and the run action. */
        public Color accent;
        /** Accent under the mouse. */
        public Color accentHover;
        /** Text drawn on top of {@link #accent}. */
        public Color onAccent;
        /** A wash of the accent, for selection backgrounds. */
        public Color accentSoft;
        /** Success, used for validation passes and the halted-as-expected state. */
        public Color success;
        /** Warning. */
        public Color warning;
        /** Error, and destructive actions. */
        public Color danger;

        // Machine canvas.

        /** Canvas background. */
        public Color canvas;
        /** Canvas dot grid. */
        public Color canvasGrid;
        /** Fill of an ordinary state. */
        public Color stateFill;
        /** Outline of an ordinary state. */
        public Color stateStroke;
        /** State label text. */
        public Color stateLabel;
        /** Outline of a selected state. */
        public Color stateSelected;
        /** Fill of the state the simulator is currently in. */
        public Color stateCurrent;
        /** Text drawn inside the current state. */
        public Color onStateCurrent;
        /** Halo drawn around the current state. */
        public Color stateCurrentGlow;
        /** The start-state marker. */
        public Color stateStart;
        /** An ordinary transition arc. */
        public Color transition;
        /** The transition the simulator will take next. */
        public Color transitionActive;
        /** A selected transition. */
        public Color transitionSelected;
        /** Background of the pill behind a transition's action text. */
        public Color actionPill;
        /** Transition action text. */
        public Color actionText;

        // Tape.

        /** Tape strip background. */
        public Color tapeBg;
        /** Fill of an ordinary tape cell. */
        public Color tapeCell;
        /** Outline of an ordinary tape cell. */
        public Color tapeCellBorder;
        /** Tape cell contents. */
        public Color tapeText;
        /** Fill of the cell under the read/write head. */
        public Color tapeHead;
        /** Contents of the cell under the read/write head. */
        public Color onTapeHead;
        /** The cell-index ruler above the tape. */
        public Color tapeRuler;

        // Console.

        /** Console background. */
        public Color consoleBg;
        /** Console body text. */
        public Color consoleText;
        /** Console timestamps. */
        public Color consoleMuted;

        /** Drop shadow colour, already carrying its own alpha. */
        public Color shadow;
    }

    /**
     * The light palette.
     */
    public static final Palette LIGHT = new Palette("Light", false);

    /**
     * The dark palette.
     */
    public static final Palette DARK = new Palette("Dark", true);

    static
    {
        LIGHT.background     = new Color(0xF4F5F7);
        LIGHT.surface        = new Color(0xFFFFFF);
        LIGHT.surfaceAlt     = new Color(0xEDEFF3);
        LIGHT.surfaceHover   = new Color(0xE4E7ED);
        LIGHT.surfacePressed = new Color(0xD8DCE4);
        LIGHT.border         = new Color(0xDCE0E6);
        LIGHT.borderStrong   = new Color(0xBFC5CF);
        LIGHT.text           = new Color(0x1A1D23);
        LIGHT.textMuted      = new Color(0x6B7280);
        LIGHT.accent         = new Color(0x3B6FF5);
        LIGHT.accentHover    = new Color(0x2F5FDC);
        LIGHT.onAccent       = new Color(0xFFFFFF);
        LIGHT.accentSoft     = new Color(0xE3EAFD);
        LIGHT.success        = new Color(0x14914B);
        LIGHT.warning        = new Color(0xC2740A);
        LIGHT.danger         = new Color(0xD3372B);
        LIGHT.canvas         = new Color(0xFAFBFC);
        LIGHT.canvasGrid     = new Color(0xDFE3E9);
        LIGHT.stateFill      = new Color(0xE8EEFC);
        LIGHT.stateStroke    = new Color(0x3B6FF5);
        LIGHT.stateLabel     = new Color(0x16305C);
        LIGHT.stateSelected  = new Color(0xD3372B);
        LIGHT.stateCurrent   = new Color(0xF5A524);
        LIGHT.onStateCurrent = new Color(0x3B2708);
        LIGHT.stateCurrentGlow = new Color(0xF5A524);
        LIGHT.stateStart     = new Color(0x14914B);
        LIGHT.transition     = new Color(0x7A8394);
        LIGHT.transitionActive = new Color(0xF5A524);
        LIGHT.transitionSelected = new Color(0xD3372B);
        LIGHT.actionPill     = new Color(0xFFFFFF);
        LIGHT.actionText     = new Color(0x39404E);
        LIGHT.tapeBg         = new Color(0xF4F5F7);
        LIGHT.tapeCell       = new Color(0xFFFFFF);
        LIGHT.tapeCellBorder = new Color(0xCED4DE);
        LIGHT.tapeText       = new Color(0x1A1D23);
        LIGHT.tapeHead       = new Color(0x3B6FF5);
        LIGHT.onTapeHead     = new Color(0xFFFFFF);
        LIGHT.tapeRuler      = new Color(0x99A1AE);
        LIGHT.consoleBg      = new Color(0xFFFFFF);
        LIGHT.consoleText    = new Color(0x2B3038);
        LIGHT.consoleMuted   = new Color(0x9AA2AF);
        LIGHT.shadow         = new Color(0, 0, 0, 28);

        DARK.background      = new Color(0x15171C);
        DARK.surface         = new Color(0x1D2027);
        DARK.surfaceAlt      = new Color(0x23272F);
        DARK.surfaceHover    = new Color(0x2C313A);
        DARK.surfacePressed  = new Color(0x363C47);
        DARK.border          = new Color(0x2E333C);
        DARK.borderStrong    = new Color(0x454C58);
        DARK.text            = new Color(0xE7EAF0);
        DARK.textMuted       = new Color(0x939BA8);
        DARK.accent          = new Color(0x5B8DEF);
        DARK.accentHover     = new Color(0x74A0F5);
        DARK.onAccent        = new Color(0x0B1220);
        DARK.accentSoft      = new Color(0x223251);
        DARK.success         = new Color(0x35C77B);
        DARK.warning         = new Color(0xE0A23A);
        DARK.danger          = new Color(0xF06A5D);
        DARK.canvas          = new Color(0x131519);
        DARK.canvasGrid      = new Color(0x262B33);
        DARK.stateFill       = new Color(0x243352);
        DARK.stateStroke     = new Color(0x5B8DEF);
        DARK.stateLabel      = new Color(0xDCE6FA);
        DARK.stateSelected   = new Color(0xF06A5D);
        DARK.stateCurrent    = new Color(0xE0A23A);
        DARK.onStateCurrent  = new Color(0x241A05);
        DARK.stateCurrentGlow = new Color(0xE0A23A);
        DARK.stateStart      = new Color(0x35C77B);
        DARK.transition      = new Color(0x8A93A3);
        DARK.transitionActive = new Color(0xE0A23A);
        DARK.transitionSelected = new Color(0xF06A5D);
        DARK.actionPill      = new Color(0x1D2027);
        DARK.actionText      = new Color(0xC3CAD6);
        DARK.tapeBg          = new Color(0x15171C);
        DARK.tapeCell        = new Color(0x1D2027);
        DARK.tapeCellBorder  = new Color(0x363C47);
        DARK.tapeText        = new Color(0xE7EAF0);
        DARK.tapeHead        = new Color(0x5B8DEF);
        DARK.onTapeHead      = new Color(0x0B1220);
        DARK.tapeRuler       = new Color(0x6C7482);
        DARK.consoleBg       = new Color(0x15171C);
        DARK.consoleText     = new Color(0xC9D0DB);
        DARK.consoleMuted    = new Color(0x6C7482);
        DARK.shadow          = new Color(0, 0, 0, 90);
    }

    /**
     * The palette currently in force.
     */
    private static Palette s_current = LIGHT;

    /**
     * Callbacks run after the palette changes, so that custom-painted components can rebuild any
     * derived resources (icons, in particular) and repaint.
     */
    private static final ArrayList<Runnable> s_listeners = new ArrayList<Runnable>();

    /**
     * Get the palette currently in force.
     * @return The current palette.
     */
    public static Palette palette()
    {
        return s_current;
    }

    /**
     * Determine whether the dark palette is in force.
     * @return true if the current palette is dark, false otherwise.
     */
    public static boolean isDark()
    {
        return s_current.dark;
    }

    /**
     * Register a callback to be run whenever the palette changes.
     * @param r The callback to run.
     */
    public static void onChange(Runnable r)
    {
        s_listeners.add(r);
    }

    /**
     * Switch to the given palette, reinstall the look-and-feel, and refresh every open window.
     * @param p The palette to switch to.
     */
    public static void set(Palette p)
    {
        if (p == s_current)
        {
            return;
        }
        s_current = p;
        install();

        for (Window w : Window.getWindows())
        {
            SwingUtilities.updateComponentTreeUI(w);
        }
        for (Runnable r : s_listeners)
        {
            r.run();
        }
        for (Window w : Window.getWindows())
        {
            w.repaint();
        }
    }

    /**
     * Switch between the light and dark palettes.
     */
    public static void toggle()
    {
        set(isDark()? LIGHT : DARK);
    }

    // ----------------------------------------------------------------- Fonts

    /**
     * Candidate UI font families, most preferred first.
     */
    private static final String[] UI_FAMILIES =
        { "Segoe UI", "Inter", "SF Pro Text", "Roboto", "DejaVu Sans", Font.DIALOG };

    /**
     * Candidate monospaced font families, most preferred first.
     */
    private static final String[] MONO_FAMILIES =
        { "Cascadia Mono", "Consolas", "JetBrains Mono", "Menlo", "DejaVu Sans Mono", Font.MONOSPACED };

    /**
     * The resolved UI font family.
     */
    private static String s_uiFamily;

    /**
     * The resolved monospaced font family.
     */
    private static String s_monoFamily;

    /**
     * Pick the first family in the given list which is actually installed.
     * @param candidates Candidate family names, most preferred first.
     * @param fallback Family to use when none of the candidates are installed.
     * @return The name of an installed font family.
     */
    private static String resolveFamily(String[] candidates, String fallback)
    {
        java.util.HashSet<String> installed = new java.util.HashSet<String>();
        for (String f : GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames())
        {
            installed.add(f);
        }
        for (String c : candidates)
        {
            if (installed.contains(c))
            {
                return c;
            }
        }
        return fallback;
    }

    /**
     * Get a UI font.
     * @param style One of the {@link Font} style constants.
     * @param size The point size.
     * @return The requested font, in the best available UI family.
     */
    public static Font ui(int style, int size)
    {
        if (s_uiFamily == null)
        {
            s_uiFamily = resolveFamily(UI_FAMILIES, Font.DIALOG);
        }
        return new Font(s_uiFamily, style, size);
    }

    /**
     * Get a monospaced font.
     * @param style One of the {@link Font} style constants.
     * @param size The point size.
     * @return The requested font, in the best available monospaced family.
     */
    public static Font mono(int style, int size)
    {
        if (s_monoFamily == null)
        {
            s_monoFamily = resolveFamily(MONO_FAMILIES, Font.MONOSPACED);
        }
        return new Font(s_monoFamily, style, size);
    }

    // ------------------------------------------------------- Java2D helpers

    /**
     * Turn on the rendering hints used throughout the program: antialiasing for shapes, and
     * subpixel-positioned antialiased text.
     * @param g The graphics object to configure.
     * @return The same object, cast to Graphics2D for convenience.
     */
    public static Graphics2D prepare(Graphics g)
    {
        Graphics2D g2d = (Graphics2D)g;
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2d.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        return g2d;
    }

    /**
     * Derive a translucent version of a colour.
     * @param c The base colour.
     * @param a The alpha value, from 0 to 255.
     * @return The base colour at the given alpha.
     */
    public static Color alpha(Color c, int a)
    {
        return new Color(c.getRed(), c.getGreen(), c.getBlue(), a);
    }

    /**
     * Linearly interpolate between two colours.
     * @param a The colour returned when t is 0.
     * @param b The colour returned when t is 1.
     * @param t The interpolation factor, from 0 to 1.
     * @return The interpolated colour.
     */
    public static Color mix(Color a, Color b, float t)
    {
        return new Color(
                (int)(a.getRed()   + (b.getRed()   - a.getRed())   * t),
                (int)(a.getGreen() + (b.getGreen() - a.getGreen()) * t),
                (int)(a.getBlue()  + (b.getBlue()  - a.getBlue())  * t));
    }

    /**
     * Build a rounded rectangle.
     * @param x The X ordinate of the upper-left corner.
     * @param y The Y ordinate of the upper-left corner.
     * @param w The width.
     * @param h The height.
     * @param r The corner radius.
     * @return The rounded rectangle.
     */
    public static RoundRectangle2D.Float round(double x, double y, double w, double h, double r)
    {
        return new RoundRectangle2D.Float((float)x, (float)y, (float)w, (float)h, (float)(r * 2), (float)(r * 2));
    }

    /**
     * Paint a soft drop shadow beneath a shape, by stamping the shape several times at increasing
     * offsets and decreasing alpha. Cheap, and close enough to a blur at these sizes.
     * @param g2d The graphics object to render onto.
     * @param s The shape to cast the shadow.
     * @param depth How far the shadow falls, in pixels.
     */
    public static void shadow(Graphics2D g2d, Shape s, int depth)
    {
        Color base = palette().shadow;
        for (int i = depth; i >= 1; i--)
        {
            g2d.setColor(alpha(base, base.getAlpha() / (i + 1)));
            g2d.translate(0, i);
            g2d.fill(s);
            g2d.translate(0, -i);
        }
    }

    /**
     * Draw a string centred horizontally on the given point, with its baseline placed so the text
     * is also centred vertically.
     * @param g2d The graphics object to render onto.
     * @param str The string to render.
     * @param cx The X ordinate of the centre.
     * @param cy The Y ordinate of the centre.
     */
    public static void drawCentered(Graphics2D g2d, String str, double cx, double cy)
    {
        FontMetrics fm = g2d.getFontMetrics();
        g2d.drawString(str,
                (float)(cx - fm.stringWidth(str) / 2.0),
                (float)(cy + (fm.getAscent() - fm.getDescent()) / 2.0));
    }

    // ------------------------------------------------- Look-and-feel install

    /**
     * Install the Metal look-and-feel configured for the current palette, then apply the overrides
     * which give the standard components their flat appearance. Metal is used rather than Nimbus
     * because it honours plain UIManager colour keys, which is what makes a second palette possible
     * without shipping a full look-and-feel.
     */
    /**
     * Store a colour in the UIManager defaults.
     *
     * Values must be wrapped in a ColorUIResource: when a look-and-feel is reinstalled,
     * LookAndFeel.installColors only overwrites a component's colour if the existing one is null or
     * a UIResource. Storing a plain Color makes the first palette stick and silently defeats every
     * later switch.
     *
     * @param key The UIManager key to set.
     * @param c The colour to store.
     */
    private static void put(String key, Color c)
    {
        UIManager.put(key, new ColorUIResource(c));
    }

    /**
     * Store a border in the UIManager defaults, wrapped so that it survives a look-and-feel change
     * for the same reason colours must be.
     * @param key The UIManager key to set.
     * @param b The border to store.
     */
    private static void put(String key, javax.swing.border.Border b)
    {
        UIManager.put(key, new javax.swing.plaf.BorderUIResource(b));
    }

    public static void install()
    {
        final Palette p = s_current;

        try
        {
            MetalLookAndFeel.setCurrentTheme(new DefaultMetalTheme()
            {
                public String getName() { return "Tuatara " + p.name; }

                // Metal derives a great deal from these eight colours.
                protected ColorUIResource getPrimary1()   { return new ColorUIResource(p.accentHover); }
                protected ColorUIResource getPrimary2()   { return new ColorUIResource(p.accent); }
                protected ColorUIResource getPrimary3()   { return new ColorUIResource(p.accentSoft); }
                protected ColorUIResource getSecondary1() { return new ColorUIResource(p.borderStrong); }
                protected ColorUIResource getSecondary2() { return new ColorUIResource(p.border); }
                protected ColorUIResource getSecondary3() { return new ColorUIResource(p.background); }
                protected ColorUIResource getBlack()      { return new ColorUIResource(p.text); }
                protected ColorUIResource getWhite()      { return new ColorUIResource(p.surface); }

                public FontUIResource getControlTextFont() { return new FontUIResource(ui(Font.PLAIN, 13)); }
                public FontUIResource getSystemTextFont()  { return new FontUIResource(ui(Font.PLAIN, 13)); }
                public FontUIResource getUserTextFont()    { return new FontUIResource(ui(Font.PLAIN, 13)); }
                public FontUIResource getMenuTextFont()    { return new FontUIResource(ui(Font.PLAIN, 13)); }
                public FontUIResource getWindowTitleFont() { return new FontUIResource(ui(Font.BOLD,  13)); }
                public FontUIResource getSubTextFont()     { return new FontUIResource(ui(Font.PLAIN, 11)); }
            });
            UIManager.setLookAndFeel(new MetalLookAndFeel());
        }
        catch (Exception e)
        {
            // Unable to change look-and-feel; the overrides below still improve matters.
        }

        // Metal paints a gradient on buttons, menu bars and toolbars by default. Removing the
        // gradients is what makes the result read as flat rather than as late-90s Java.
        UIManager.put("Button.gradient", null);
        UIManager.put("MenuBar.gradient", null);
        UIManager.put("MenuBarMenu.gradient", null);
        UIManager.put("InternalFrame.activeTitleGradient", null);
        UIManager.put("ScrollBar.gradient", null);
        UIManager.put("CheckBox.gradient", null);
        UIManager.put("RadioButton.gradient", null);
        UIManager.put("ToggleButton.gradient", null);
        put("Slider.altTrackColor", p.border);

        put("control", p.background);
        put("Panel.background", p.background);
        put("OptionPane.background", p.background);
        put("OptionPane.messageForeground", p.text);
        put("Label.foreground", p.text);
        put("Label.disabledForeground", p.textMuted);

        put("MenuBar.background", p.background);
        put("MenuBar.foreground", p.text);
        put("MenuBar.borderColor", p.border);
        put("Menu.background", p.background);
        put("Menu.foreground", p.text);
        put("Menu.selectionBackground", p.accent);
        put("Menu.selectionForeground", p.onAccent);
        put("Menu.disabledForeground", p.textMuted);
        put("Menu.border", BorderFactory.createEmptyBorder(5, 10, 5, 10));
        put("MenuItem.background", p.surface);
        put("MenuItem.foreground", p.text);
        put("MenuItem.selectionBackground", p.accent);
        put("MenuItem.selectionForeground", p.onAccent);
        put("MenuItem.disabledForeground", p.textMuted);
        put("MenuItem.acceleratorForeground", p.textMuted);
        put("MenuItem.acceleratorSelectionForeground", p.onAccent);
        put("MenuItem.border", BorderFactory.createEmptyBorder(5, 8, 5, 8));
        put("RadioButtonMenuItem.background", p.surface);
        put("RadioButtonMenuItem.foreground", p.text);
        put("RadioButtonMenuItem.selectionBackground", p.accent);
        put("RadioButtonMenuItem.selectionForeground", p.onAccent);
        put("RadioButtonMenuItem.border", BorderFactory.createEmptyBorder(5, 8, 5, 8));
        put("CheckBoxMenuItem.background", p.surface);
        put("CheckBoxMenuItem.foreground", p.text);
        put("CheckBoxMenuItem.selectionBackground", p.accent);
        put("CheckBoxMenuItem.selectionForeground", p.onAccent);
        put("CheckBoxMenuItem.border", BorderFactory.createEmptyBorder(5, 8, 5, 8));
        put("PopupMenu.background", p.surface);
        put("PopupMenu.border", BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(p.border), BorderFactory.createEmptyBorder(4, 0, 4, 0)));
        put("Separator.foreground", p.border);
        put("Separator.background", p.border);

        put("ToolTip.background", p.dark? p.surfaceAlt : p.text);
        put("ToolTip.foreground", p.dark? p.text : p.surface);
        put("ToolTip.border", BorderFactory.createEmptyBorder(6, 9, 6, 9));

        put("ToolBar.background", p.background);
        put("ToolBar.foreground", p.text);
        put("ToolBar.border", BorderFactory.createEmptyBorder());
        UIManager.put("ToolBar.isRollover", Boolean.TRUE);

        put("Button.background", p.surface);
        put("Button.foreground", p.text);
        put("Button.select", p.surfacePressed);
        put("Button.focus", alpha(p.accent, 0));
        put("Button.disabledText", p.textMuted);
        UIManager.put("Button.margin", new Insets(6, 14, 6, 14));

        put("ToggleButton.background", p.surface);
        put("ToggleButton.foreground", p.text);
        put("ToggleButton.select", p.accentSoft);
        put("ToggleButton.focus", alpha(p.accent, 0));

        put("CheckBox.background", p.background);
        put("CheckBox.foreground", p.text);
        put("CheckBox.focus", alpha(p.accent, 0));
        put("RadioButton.background", p.background);
        put("RadioButton.foreground", p.text);
        put("RadioButton.focus", alpha(p.accent, 0));

        put("TextField.background", p.surface);
        put("TextField.foreground", p.text);
        put("TextField.caretForeground", p.accent);
        put("TextField.selectionBackground", p.accentSoft);
        put("TextField.selectionForeground", p.text);
        put("TextField.border", BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(p.border), BorderFactory.createEmptyBorder(4, 7, 4, 7)));
        put("TextArea.background", p.surface);
        put("TextArea.foreground", p.text);
        put("TextArea.selectionBackground", p.accentSoft);
        put("TextArea.selectionForeground", p.text);
        put("TextPane.background", p.consoleBg);
        put("TextPane.foreground", p.consoleText);
        put("TextPane.selectionBackground", p.accentSoft);
        put("TextPane.selectionForeground", p.text);
        put("EditorPane.background", p.surface);
        put("EditorPane.foreground", p.text);

        put("List.background", p.surface);
        put("List.foreground", p.text);
        put("List.selectionBackground", p.accent);
        put("List.selectionForeground", p.onAccent);
        put("ComboBox.background", p.surface);
        put("ComboBox.foreground", p.text);
        put("ComboBox.selectionBackground", p.accent);
        put("ComboBox.selectionForeground", p.onAccent);

        put("ScrollPane.background", p.background);
        put("ScrollPane.border", BorderFactory.createEmptyBorder());
        put("Viewport.background", p.canvas);
        put("ScrollBar.background", p.background);
        put("ScrollBar.track", p.background);
        put("ScrollBar.thumb", p.borderStrong);
        UIManager.put("ScrollBar.width", 12);
        UIManager.put("ScrollBarUI", FlatScrollBarUI.class.getName());

        put("SplitPane.background", p.background);
        UIManager.put("SplitPane.dividerSize", 6);
        put("SplitPane.border", BorderFactory.createEmptyBorder());
        put("SplitPaneDivider.border", BorderFactory.createEmptyBorder());

        put("TabbedPane.background", p.background);
        put("TabbedPane.foreground", p.text);
        put("TabbedPane.contentAreaColor", p.canvas);
        put("TabbedPane.selected", p.canvas);
        put("TabbedPane.tabAreaBackground", p.background);
        UIManager.put("TabbedPane.contentBorderInsets", new Insets(0, 0, 0, 0));
        UIManager.put("TabbedPane.tabAreaInsets", new Insets(4, 6, 0, 6));
        UIManager.put("TabbedPane.selectedTabPadInsets", new Insets(0, 0, 0, 0));
        UIManager.put("TabbedPane.tabInsets", new Insets(6, 12, 6, 8));
        put("TabbedPane.focus", alpha(p.accent, 0));

        put("FileChooser.listViewBackground", p.surface);
        UIManager.put("OptionPane.errorIcon", Icons.dialog("error", 40));
        UIManager.put("OptionPane.informationIcon", Icons.dialog("info", 40));
        UIManager.put("OptionPane.warningIcon", Icons.dialog("warning", 40));
        UIManager.put("OptionPane.questionIcon", Icons.dialog("question", 40));
    }

    /**
     * A scrollbar with no buttons and a flat rounded thumb, replacing Metal's bevelled default.
     */
    public static class FlatScrollBarUI extends BasicScrollBarUI
    {
        /**
         * Required by UIManager to instantiate the delegate.
         * @param c The component the delegate will be installed on.
         * @return A new delegate instance.
         */
        public static javax.swing.plaf.ComponentUI createUI(JComponent c)
        {
            return new FlatScrollBarUI();
        }

        /**
         * Suppress the increase button by giving it zero size.
         * @param orientation The scrollbar orientation.
         * @return A zero-sized button.
         */
        protected JButton createIncreaseButton(int orientation)
        {
            return zeroButton();
        }

        /**
         * Suppress the decrease button by giving it zero size.
         * @param orientation The scrollbar orientation.
         * @return A zero-sized button.
         */
        protected JButton createDecreaseButton(int orientation)
        {
            return zeroButton();
        }

        /**
         * Build a button which occupies no space.
         * @return A zero-sized button.
         */
        private JButton zeroButton()
        {
            JButton b = new JButton();
            b.setPreferredSize(new Dimension(0, 0));
            b.setMinimumSize(new Dimension(0, 0));
            b.setMaximumSize(new Dimension(0, 0));
            return b;
        }

        /**
         * Paint the track as a flat fill.
         * @param g The graphics object to render onto.
         * @param c The scrollbar.
         * @param r The bounds of the track.
         */
        protected void paintTrack(Graphics g, JComponent c, Rectangle r)
        {
            g.setColor(palette().background);
            g.fillRect(r.x, r.y, r.width, r.height);
        }

        /**
         * Paint the thumb as a flat rounded capsule, inset from the track.
         * @param g The graphics object to render onto.
         * @param c The scrollbar.
         * @param r The bounds of the thumb.
         */
        protected void paintThumb(Graphics g, JComponent c, Rectangle r)
        {
            if (r.isEmpty() || !scrollbar.isEnabled())
            {
                return;
            }
            Graphics2D g2d = prepare(g.create());
            g2d.setColor(palette().borderStrong);
            g2d.fill(round(r.x + 3, r.y + 3, r.width - 6, r.height - 6, (Math.min(r.width, r.height) - 6) / 2.0));
            g2d.dispose();
        }
    }
}
