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
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import javax.swing.*;

/**
 * A button which paints itself flat: a rounded fill that responds to hover, press and selection,
 * with a vector icon and optional label. Replaces the bevelled buttons the toolbar previously used,
 * where the only signal of the active tool was which way a bevel pointed.
 *
 * The button renders its own content rather than deferring to the look-and-feel, so that the icon
 * can be recoloured per state, which is what makes the active tool legible.
 */
public class FlatButton extends JButton
{
    /**
     * Visual treatments a button may take.
     */
    public enum Style
    {
        /**
         * Transparent until hovered. Used for the bulk of the toolbar.
         */
        TOOL,

        /**
         * Filled with the accent colour. Reserved for the single primary action in a group.
         */
        PRIMARY,

        /**
         * Transparent, but tinted with the danger colour when hovered.
         */
        DANGER
    }

    /**
     * Horizontal padding either side of the content.
     */
    protected static final int PAD_X = 7;

    /**
     * Vertical padding above and below the content.
     */
    protected static final int PAD_Y = 7;

    /**
     * Gap between the icon and the label.
     */
    protected static final int GAP = 7;

    /**
     * Corner radius of the button fill.
     */
    protected static final int RADIUS = 7;

    /**
     * Creates a new instance of FlatButton.
     * @param act The action the button performs. Its name is used as the tooltip.
     * @param iconName The name of the icon to render, or null for a text-only button.
     * @param style The visual treatment to apply.
     * @param showText true to render the action's name beside the icon, false for icon only.
     */
    public FlatButton(Action act, String iconName, Style style, boolean showText)
    {
        m_iconName = iconName;
        m_style = style;

        if (act != null)
        {
            setAction(act);
        }
        // setAction copies the action's name into the button text; the label is painted by this
        // class instead, so the inherited text is cleared to keep the superclass from laying it out.
        m_label = showText && act != null? String.valueOf(act.getValue(Action.NAME)) : null;
        if (act != null && getToolTipText() == null)
        {
            setToolTipText(describe(act));
        }
        super.setText("");

        setOpaque(false);
        setContentAreaFilled(false);
        setBorderPainted(false);
        setFocusPainted(false);
        setFocusable(false);
        setBorder(BorderFactory.createEmptyBorder());
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        setRolloverEnabled(true);
        setFont(Theme.ui(Font.PLAIN, 13));
    }

    /**
     * Build a tooltip for an action, appending its accelerator when it has one, so that the
     * keyboard shortcuts are discoverable without opening the menus.
     * @param act The action to describe.
     * @return The tooltip text.
     */
    public static String describe(Action act)
    {
        Object desc = act.getValue(Action.SHORT_DESCRIPTION);
        Object name = act.getValue(Action.NAME);
        String text = String.valueOf(desc != null? desc : name);

        KeyStroke ks = (KeyStroke)act.getValue(Action.ACCELERATOR_KEY);
        if (ks != null)
        {
            String mods = InputEvent.getModifiersExText(ks.getModifiers());
            String key = KeyEvent.getKeyText(ks.getKeyCode());
            text += "  (" + (mods.isEmpty()? key : mods + "+" + key) + ")";
        }
        return text;
    }

    /**
     * Set the caption rendered beside the icon. Named to avoid overriding the deprecated
     * AbstractButton.setLabel.
     * @param label The caption, or null for an icon-only button.
     */
    public void setCaption(String label)
    {
        m_label = label;
        revalidate();
        repaint();
    }

    /**
     * Set the icon rendered by this button.
     * @param iconName The name of the icon, as understood by {@link Icons}.
     */
    public void setIconName(String iconName)
    {
        // Guarded, so that repeatedly setting the same icon does not leave the button permanently
        // dirty and drive an endless repaint.
        if (m_iconName == null? iconName == null : m_iconName.equals(iconName))
        {
            return;
        }
        m_iconName = iconName;
        repaint();
    }

    /**
     * Set the size the icon is rendered at.
     * @param size The icon size, in pixels.
     */
    public void setIconSize(int size)
    {
        m_iconSize = size;
        revalidate();
        repaint();
    }

    /**
     * Determine whether this button is showing as selected.
     * @return true if the button is selected, false otherwise.
     */
    public boolean isChosen()
    {
        return m_chosen;
    }

    /**
     * Set whether this button shows as selected. Used for the tool buttons, exactly one of which is
     * active at a time.
     * @param chosen true if the button should show as selected, false otherwise.
     */
    public void setChosen(boolean chosen)
    {
        if (m_chosen != chosen)
        {
            m_chosen = chosen;
            repaint();
        }
    }

    /**
     * Get the colour the icon and label should be drawn in for the current state.
     * @return The foreground colour.
     */
    protected Color foreground()
    {
        Theme.Palette p = Theme.palette();
        if (!isEnabled())
        {
            return p.textMuted;
        }
        switch (m_style)
        {
            case PRIMARY: return p.onAccent;
            case DANGER:  return getModel().isRollover()? p.danger : p.text;
            default:      return m_chosen? p.accent : p.text;
        }
    }

    /**
     * Get the colour the rounded fill should be drawn in for the current state, or null when the
     * button should render no fill at all.
     * @return The background colour, or null for no fill.
     */
    protected Color background()
    {
        Theme.Palette p = Theme.palette();
        boolean hover = getModel().isRollover() && isEnabled();
        boolean down = getModel().isPressed() && isEnabled();

        if (m_style == Style.PRIMARY)
        {
            return !isEnabled()? p.surfaceAlt
                 : down?  p.accentHover
                 : hover? p.accentHover
                 :        p.accent;
        }
        if (m_style == Style.DANGER && hover)
        {
            return Theme.alpha(p.danger, down? 60 : 34);
        }
        if (m_chosen)
        {
            return down? p.surfacePressed : p.accentSoft;
        }
        if (down)
        {
            return p.surfacePressed;
        }
        if (hover)
        {
            return p.surfaceHover;
        }
        return null;
    }

    /**
     * Compute the preferred size from the icon, label and padding.
     * @return The preferred size.
     */
    public Dimension getPreferredSize()
    {
        int w = PAD_X * 2;
        int h = PAD_Y * 2 + m_iconSize;

        if (m_iconName != null)
        {
            w += m_iconSize;
        }
        if (m_label != null)
        {
            FontMetrics fm = getFontMetrics(getFont());
            w += fm.stringWidth(m_label) + (m_iconName != null? GAP : 0);
            h = Math.max(h, PAD_Y * 2 + fm.getHeight());
        }
        return new Dimension(w, h);
    }

    /**
     * Get the minimum size, which is the preferred size; these buttons should not be squeezed.
     * @return The minimum size.
     */
    public Dimension getMinimumSize()
    {
        return getPreferredSize();
    }

    /**
     * Render the button.
     * @param g The graphics object to render onto.
     */
    protected void paintComponent(Graphics g)
    {
        Graphics2D g2d = Theme.prepare(g.create());

        Color bg = background();
        if (bg != null)
        {
            g2d.setColor(bg);
            g2d.fill(Theme.round(0, 0, getWidth(), getHeight(), RADIUS));
        }

        Color fg = foreground();

        // Centre the icon and label as a unit.
        int contentW = 0;
        FontMetrics fm = getFontMetrics(getFont());
        if (m_iconName != null)
        {
            contentW += m_iconSize;
        }
        if (m_label != null)
        {
            contentW += fm.stringWidth(m_label) + (m_iconName != null? GAP : 0);
        }

        int x = (getWidth() - contentW) / 2;
        if (m_iconName != null)
        {
            Icon icon = Icons.get(m_iconName, m_iconSize, fg);
            icon.paintIcon(this, g2d, x, (getHeight() - m_iconSize) / 2);
            x += m_iconSize + (m_label != null? GAP : 0);
        }
        if (m_label != null)
        {
            g2d.setColor(fg);
            g2d.setFont(getFont());
            g2d.drawString(m_label, x, (getHeight() + fm.getAscent() - fm.getDescent()) / 2);
        }

        g2d.dispose();
    }

    /**
     * A thin vertical rule used to separate groups of buttons in the toolbar.
     */
    public static class Divider extends JComponent
    {
        /**
         * Creates a new instance of Divider.
         */
        public Divider()
        {
            setPreferredSize(new Dimension(9, 26));
            setMaximumSize(new Dimension(9, 34));
        }

        /**
         * Render the rule.
         * @param g The graphics object to render onto.
         */
        protected void paintComponent(Graphics g)
        {
            g.setColor(Theme.palette().border);
            g.fillRect(getWidth() / 2, 4, 1, getHeight() - 8);
        }
    }

    /**
     * The name of the icon rendered by this button, or null for a text-only button.
     */
    protected String m_iconName;

    /**
     * The visual treatment applied to this button.
     */
    protected Style m_style;

    /**
     * The label rendered beside the icon, or null for an icon-only button.
     */
    protected String m_label;

    /**
     * The size the icon is rendered at, in pixels.
     */
    protected int m_iconSize = 18;

    /**
     * Whether this button currently shows as selected.
     */
    protected boolean m_chosen;
}
