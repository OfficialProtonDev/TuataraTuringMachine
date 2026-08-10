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
import java.awt.event.*;
import javax.swing.*;
import tuataraTMSim.exceptions.ComputationFailedException;

/**
 * A panel containing a tape display panel and some buttons to move the read/write head.
 */
public class TapeDisplayControllerPanel extends JPanel
{
    /**
     * Number of pixels used for padding around the strip.
     */
    protected static final int PADDING = 6;

    /**
     * Size the control icons are rendered at.
     */
    protected static final int ICON_SIZE = 17;

    /**
     * Creates a new instance of TapeDisplayControllerPanel.
     * @param tapeDP The tape display panel.
     * @param headToStartAction Action used to move the read/write head to the start.
     * @param eraseTapeAction Action used to erase the entire tape.
     * @param reloadAction Action used to reload the tape.
     */
    public TapeDisplayControllerPanel(TapeDisplayPanel tapeDP, Action headToStartAction,
                                      Action eraseTapeAction, Action reloadAction)
    {
        m_tapeDP = tapeDP;
        initComponents(headToStartAction, eraseTapeAction, reloadAction);
        Theme.onChange(new Runnable()
        {
            public void run()
            {
                applyTheme();
            }
        });
    }

    /**
     * Initialization.
     * @param headToStartAction Action used to move the read/write head to the start.
     * @param eraseTapeAction Action used to erase the entire tape.
     * @param reloadAction Action used to reload the tape.
     */
    public void initComponents(Action headToStartAction, Action eraseTapeAction, Action reloadAction)
    {
        setFocusable(false);
        setLayout(new BorderLayout());
        setBorder(BorderFactory.createEmptyBorder(PADDING, PADDING, PADDING, PADDING));

        // NOTE: This is attached to all subcomponents so that when the mouse is clicked over this
        //       component, we receive the focus of the keyboard.
        MouseListener onClick = new MouseAdapter()
        {
            public void mousePressed(MouseEvent e)
            {
                MachineGraphicsPanel gfx = MainWindow.getInstance().getSelectedGraphicsPanel();
                if (gfx != null)
                {
                    gfx.deselectSymbol();
                }
            }
        };

        m_tapeDP.addMouseListener(onClick);

        m_BStart = control(headToStartAction, "tape-start", null);

        m_BLeft = control(null, "tape-left", "Move the read/write head to the left");
        m_BLeft.addActionListener(new ActionListener()
        {
             public void actionPerformed(ActionEvent e)
             {
                 try
                 {
                    // Move the head one cell to the left.
                    m_tapeDP.getTape().headLeft();
                    repaint();
                    MainWindow.getInstance().refreshStatus();
                 }
                 catch (ComputationFailedException e1) { }
             }
        });

        m_BRight = control(null, "tape-right", "Move the read/write head to the right");
        m_BRight.addActionListener(new ActionListener()
        {
             public void actionPerformed(ActionEvent e)
             {
                 // Move the head one cell to the right
                 m_tapeDP.getTape().headRight();
                 repaint();
                 MainWindow.getInstance().refreshStatus();
             }
        });

        m_BReloadTape = control(reloadAction, "tape-reload", null);
        m_BClearTape = control(eraseTapeAction, "tape-clear", null);

        for (FlatButton b : new FlatButton[] { m_BStart, m_BLeft, m_BRight, m_BReloadTape, m_BClearTape })
        {
            b.addMouseListener(onClick);
        }

        // Caption, so the strip identifies itself rather than being an unlabelled row of buttons.
        m_caption = new JLabel("TAPE");
        m_caption.setFont(Theme.ui(Font.BOLD, 10));
        m_caption.setBorder(BorderFactory.createEmptyBorder(0, 2, 0, 8));

        JPanel leftButtonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 0));
        leftButtonPanel.setOpaque(false);
        leftButtonPanel.setFocusable(false);
        leftButtonPanel.add(m_caption);
        leftButtonPanel.add(m_BStart);
        leftButtonPanel.add(m_BLeft);

        JPanel rightButtonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 2, 0));
        rightButtonPanel.setOpaque(false);
        rightButtonPanel.setFocusable(false);
        rightButtonPanel.add(m_BRight);
        rightButtonPanel.add(new FlatButton.Divider());
        rightButtonPanel.add(m_BReloadTape);
        rightButtonPanel.add(m_BClearTape);

        add(leftButtonPanel, BorderLayout.WEST);
        add(m_tapeDP, BorderLayout.CENTER);
        add(rightButtonPanel, BorderLayout.EAST);

        applyTheme();
   }

    /**
     * Build one of the tape control buttons.
     * @param act The action the button performs, or null if a listener will be attached instead.
     * @param iconName The name of the icon to render.
     * @param tip The tooltip to use, or null to derive one from the action.
     * @return The button.
     */
    private FlatButton control(Action act, String iconName, String tip)
    {
        FlatButton b = new FlatButton(act, iconName, FlatButton.Style.TOOL, false);
        b.setIconSize(ICON_SIZE);
        if (tip != null)
        {
            b.setToolTipText(tip);
        }
        return b;
    }

    /**
     * Apply the current palette to this panel.
     */
    private void applyTheme()
    {
        Theme.Palette p = Theme.palette();
        setBackground(p.tapeBg);
        setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createMatteBorder(1, 0, 0, 0, p.border),
                    BorderFactory.createEmptyBorder(PADDING, PADDING, PADDING, PADDING)));
        if (m_caption != null)
        {
            m_caption.setForeground(p.textMuted);
        }
        repaint();
    }

    /**
     * Enable/disable user editing operations/buttons etc.
     * @param isEnabled true if editing is enabled, false otherwise.
     */
    public void setEditingEnabled(boolean isEnabled)
    {
        m_BLeft.setEnabled(isEnabled);
        m_BRight.setEnabled(isEnabled);
        m_tapeDP.setEditingEnabled(isEnabled);
    }

    /**
     * The tape display panel.
     */
    private TapeDisplayPanel m_tapeDP;

    /**
     * Button for moving the read/write head left.
     */
    private FlatButton m_BLeft;

    /**
     * Button for moving the read/write head right.
     */
    private FlatButton m_BRight;

    /**
     * Button for moving the read/write head to the start.
     */
    private FlatButton m_BStart;

    /**
     * Button for clearing the tape.
     */
    private FlatButton m_BClearTape;

    /**
     * Button for reloading the tape.
     */
    private FlatButton m_BReloadTape;

    /**
     * Caption identifying the strip.
     */
    private JLabel m_caption;
}
