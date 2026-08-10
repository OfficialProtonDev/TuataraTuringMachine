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
import javax.swing.*;

/**
 * The panel shown when no machine is open.
 *
 * Previously this space was an empty desktop pane: a new user was given a blank expanse and no
 * indication of what to do next. This offers the three ways into the program, and a short summary
 * of how building a machine works.
 */
public class WelcomePanel extends JPanel
{
    /**
     * Creates a new instance of WelcomePanel.
     * @param newTM Action creating a new Turing machine.
     * @param newDFSA Action creating a new finite-state acceptor.
     * @param open Action opening an existing machine.
     */
    public WelcomePanel(Action newTM, Action newDFSA, Action open)
    {
        setLayout(new GridBagLayout());
        setOpaque(true);

        JPanel card = new JPanel();
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setOpaque(false);

        JLabel mark = new JLabel(Icons.get("machine", 52, Theme.palette().accent));
        mark.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel title = new JLabel("Tuatara Turing Machine");
        title.setFont(Theme.ui(Font.BOLD, 24));
        title.setAlignmentX(Component.CENTER_ALIGNMENT);
        title.setBorder(BorderFactory.createEmptyBorder(14, 0, 0, 0));

        JLabel subtitle = new JLabel("Build and simulate Turing machines and finite-state acceptors");
        subtitle.setFont(Theme.ui(Font.PLAIN, 14));
        subtitle.setAlignmentX(Component.CENTER_ALIGNMENT);
        subtitle.setBorder(BorderFactory.createEmptyBorder(6, 0, 26, 0));

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 0));
        actions.setOpaque(false);
        actions.setAlignmentX(Component.CENTER_ALIGNMENT);
        actions.add(big(newTM, "new-machine", FlatButton.Style.PRIMARY));
        actions.add(big(newDFSA, "dfsa", FlatButton.Style.TOOL));
        actions.add(big(open, "open", FlatButton.Style.TOOL));

        JPanel steps = new JPanel();
        steps.setLayout(new BoxLayout(steps, BoxLayout.Y_AXIS));
        steps.setOpaque(false);
        steps.setAlignmentX(Component.CENTER_ALIGNMENT);
        steps.setBorder(BorderFactory.createEmptyBorder(34, 0, 0, 0));

        steps.add(step("state", "Place states on the canvas, then drag between them to add transitions"));
        steps.add(step("alphabet", "Configure the alphabet to declare which symbols the machine may use"));
        steps.add(step("tape", "Click the tape at the bottom of the window and type an input"));
        steps.add(step("run", "Step through the computation, or run it and watch the head move"));

        card.add(mark);
        card.add(title);
        card.add(subtitle);
        card.add(actions);
        card.add(steps);

        add(card, new GridBagConstraints());

        applyTheme();
        Theme.onChange(new Runnable()
        {
            public void run()
            {
                applyTheme();
            }
        });
    }

    /**
     * Build one of the large entry-point buttons.
     * @param act The action the button performs.
     * @param icon The name of the icon to render.
     * @param style The visual treatment to apply.
     * @return The button.
     */
    private FlatButton big(Action act, String icon, FlatButton.Style style)
    {
        FlatButton b = new FlatButton(act, icon, style, true);
        b.setIconSize(18);
        b.setFont(Theme.ui(Font.PLAIN, 14));
        b.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
        return b;
    }

    /**
     * Build one line of the summary of how the program is used.
     * @param icon The name of the icon to render beside the line.
     * @param text The text of the line.
     * @return The row.
     */
    private JPanel step(String icon, String text)
    {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 3));
        row.setOpaque(false);
        row.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel iconLabel = new JLabel(Icons.get(icon, 16, Theme.palette().textMuted));
        JLabel textLabel = new JLabel(text);
        textLabel.setFont(Theme.ui(Font.PLAIN, 13));
        textLabel.setForeground(Theme.palette().textMuted);

        row.add(iconLabel);
        row.add(textLabel);
        m_hints.add(textLabel);
        m_icons.add(iconLabel);
        m_iconNames.add(icon);
        return row;
    }

    /**
     * Apply the current palette to this panel.
     */
    private void applyTheme()
    {
        Theme.Palette p = Theme.palette();
        setBackground(p.canvas);

        for (int i = 0; i < m_hints.size(); i++)
        {
            m_hints.get(i).setForeground(p.textMuted);
            m_icons.get(i).setIcon(Icons.get(m_iconNames.get(i), 16, p.textMuted));
        }
        repaint();
    }

    /**
     * Labels making up the summary text, retained so they can follow a palette change.
     */
    private java.util.ArrayList<JLabel> m_hints = new java.util.ArrayList<JLabel>();

    /**
     * Icons beside the summary text, retained so they can follow a palette change.
     */
    private java.util.ArrayList<JLabel> m_icons = new java.util.ArrayList<JLabel>();

    /**
     * Names of the summary icons, needed to rebuild them on a palette change.
     */
    private java.util.ArrayList<String> m_iconNames = new java.util.ArrayList<String>();
}
