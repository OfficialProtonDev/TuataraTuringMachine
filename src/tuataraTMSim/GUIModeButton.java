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

import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import javax.swing.Action;

/**
 * A toolbar button which selects one of the mutually exclusive editing tools. Exactly one is
 * selected at a time; the selected one is tinted with the accent colour and underscored, so that
 * the active tool is visible at a glance.
 */
public class GUIModeButton extends FlatButton
{
    /**
     * Thickness of the indicator drawn beneath the selected tool.
     */
    protected static final int INDICATOR_HEIGHT = 2;

    /**
     * Creates a new instance of GUIModeButton.
     * @param act The abstract action associated with this button.
     * @param mode The mode to switch to after pressing this button.
     * @param iconName The name of the icon to render.
     */
    public GUIModeButton(Action act, GUI_Mode mode, String iconName)
    {
        super(act, iconName, Style.TOOL, false);
        m_mode = mode;
    }

    /**
     * Get the GUI mode associated with this button.
     * @return The GUI mode associated with this button.
     */
    public GUI_Mode getGUI_Mode()
    {
        return m_mode;
    }

    /**
     * Render the button, adding an accent indicator beneath it when it is the active tool.
     * @param g The graphics object to render onto.
     */
    protected void paintComponent(Graphics g)
    {
        super.paintComponent(g);

        if (isChosen() && isEnabled())
        {
            Graphics2D g2d = Theme.prepare(g.create());
            Color accent = Theme.palette().accent;
            g2d.setColor(accent);
            int inset = PAD_X - 2;
            g2d.fill(Theme.round(inset, getHeight() - INDICATOR_HEIGHT - 2,
                        getWidth() - inset * 2, INDICATOR_HEIGHT, INDICATOR_HEIGHT / 2.0));
            g2d.dispose();
        }
    }

    /**
     * The GUI mode associated with this button.
     */
    private GUI_Mode m_mode;
}
