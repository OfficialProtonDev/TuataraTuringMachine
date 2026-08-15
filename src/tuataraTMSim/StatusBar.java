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
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import javax.swing.*;

/**
 * The strip along the bottom of the main window, reporting the active tool, the shape of the
 * current machine, the state of any running simulation, and the tape head position.
 *
 * The program previously gave no persistent feedback at all: the only indication of which editing
 * tool was active was the direction of a button's bevel.
 */
public class StatusBar extends JPanel
{
    /**
     * Creates a new instance of StatusBar.
     */
    public StatusBar()
    {
        setLayout(new BorderLayout());

        m_tool = segment("select", "");
        m_machine = segment("machine", "");
        m_run = segment("run", "");
        m_tapeInfo = segment("tape", "");

        // The right-hand group is laid out separately so it can be pinned to the far edge.
        m_theme = new FlatButton(null, Theme.isDark()? "dark" : "light", FlatButton.Style.TOOL, false);
        m_theme.setIconSize(15);
        m_theme.setToolTipText("Switch between the light and dark appearance");
        m_theme.addActionListener(new ActionListener()
        {
            public void actionPerformed(ActionEvent e)
            {
                Theme.toggle();
            }
        });

        m_agent = segment("validate", "");
        m_agent.setVisible(false);

        m_right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        m_right.setOpaque(false);
        m_right.add(m_agent);
        m_right.add(m_tapeInfo);
        m_right.add(m_theme);

        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        left.setOpaque(false);
        left.add(m_tool);
        left.add(sep());
        left.add(m_machine);
        left.add(sep());
        left.add(m_run);

        add(left, BorderLayout.WEST);
        add(m_right, BorderLayout.EAST);

        applyTheme();
        Theme.onChange(new Runnable()
        {
            public void run()
            {
                m_theme.setIconName(Theme.isDark()? "dark" : "light");
                applyTheme();
            }
        });
    }

    /**
     * Build one labelled segment of the bar.
     * @param icon The name of the icon to render.
     * @param text The initial text.
     * @return The segment.
     */
    private JLabel segment(String icon, String text)
    {
        JLabel l = new JLabel(text);
        l.setFont(Theme.ui(Font.PLAIN, 12));
        l.setIcon(Icons.get(icon, 14, Theme.palette().textMuted));
        l.setIconTextGap(6);
        l.setBorder(BorderFactory.createEmptyBorder(2, 2, 2, 2));
        l.putClientProperty("iconName", icon);
        return l;
    }

    /**
     * Build a spacer between segments.
     * @return The spacer.
     */
    private Component sep()
    {
        return Box.createHorizontalStrut(18);
    }

    /**
     * Set the segment describing the active editing tool.
     * @param iconName The name of the tool's icon.
     * @param label The name of the tool.
     */
    public void setTool(String iconName, String label)
    {
        m_tool.putClientProperty("iconName", iconName);
        m_tool.setIcon(Icons.get(iconName, 14, Theme.palette().accent));
        m_tool.setText(label);
    }

    /**
     * Set the segment describing the current machine.
     * @param text The description, or an empty string to clear it.
     */
    public void setMachineInfo(String text)
    {
        m_machine.setText(text);
        m_machine.setVisible(!text.isEmpty());
    }

    /**
     * Set the segment describing the running simulation.
     * @param iconName The name of the icon to render.
     * @param text The description, or an empty string to clear it.
     * @param tint The colour to render the segment in, or null for the default.
     */
    public void setRunInfo(String iconName, String text, Color tint)
    {
        m_run.putClientProperty("iconName", iconName);
        m_run.setIcon(Icons.get(iconName, 14, tint != null? tint : Theme.palette().textMuted));
        m_run.setForeground(tint != null? tint : Theme.palette().textMuted);
        m_run.setText(text);
        m_run.setVisible(!text.isEmpty());
    }

    /**
     * Set the segment describing the tape.
     * @param text The description.
     */
    public void setTapeInfo(String text)
    {
        m_tapeInfo.setText(text);
    }

    /**
     * Show whether an assistant can see this window. Worth a permanent place rather than a
     * notification: the point is that it should never be a surprise.
     * @param text What to say, or an empty string to show nothing.
     * @param tint The colour to say it in, or null for the usual muted one.
     */
    public void setAgentInfo(String text, Color tint)
    {
        m_agent.setIcon(Icons.get("validate", 14, tint != null? tint : Theme.palette().textMuted));
        m_agent.setForeground(tint != null? tint : Theme.palette().textMuted);
        m_agent.setText(text);
        m_agent.setVisible(!text.isEmpty());
    }

    /**
     * Apply the current palette to this bar.
     */
    private void applyTheme()
    {
        Theme.Palette p = Theme.palette();
        setBackground(p.background);
        setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createMatteBorder(1, 0, 0, 0, p.border),
                    BorderFactory.createEmptyBorder(4, 10, 4, 6)));

        m_tool.setForeground(p.text);
        m_tool.setIcon(Icons.get(String.valueOf(m_tool.getClientProperty("iconName")), 14, p.accent));

        for (JLabel l : new JLabel[] { m_machine, m_tapeInfo })
        {
            l.setForeground(p.textMuted);
            l.setIcon(Icons.get(String.valueOf(l.getClientProperty("iconName")), 14, p.textMuted));
        }
        repaint();
    }

    /**
     * Segment describing the active editing tool.
     */
    private JLabel m_tool;

    /**
     * Segment describing the current machine.
     */
    private JLabel m_machine;

    /**
     * Segment describing the running simulation.
     */
    private JLabel m_run;

    /**
     * Segment describing the tape.
     */
    private JLabel m_tapeInfo;

    /**
     * Shows whether an assistant is connected.
     */
    private JLabel m_agent;

    /**
     * Button toggling between the light and dark palettes.
     */
    private FlatButton m_theme;

    /**
     * The right-aligned group of segments.
     */
    private JPanel m_right;
}
