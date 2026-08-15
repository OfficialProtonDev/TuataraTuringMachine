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

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import tuataraTMSim.agent.Workspace;
import tuataraTMSim.commands.TMCommand;
import tuataraTMSim.machine.Machine;
import tuataraTMSim.machine.State;
import tuataraTMSim.machine.Transition;

/**
 * The strip that appears when an assistant suggests rearranging a diagram.
 *
 * An assistant may add to somebody's machine freely, because one Ctrl+Z puts it back. Moving every
 * state at once is different: it is not a change to the machine but to the drawing, and a drawing
 * is somebody's own. So it is offered here rather than done, and the offer sits quietly at the top
 * of the tab until it is answered or goes stale. Nothing waits on it -- the assistant is told the
 * banner exists and carries on, which is the only arrangement that does not leave one party stuck
 * waiting on the other.
 */
public class ProposalBanner extends JPanel
{
    /**
     * Build a banner for an offer.
     * @param frame The tab the offer applies to.
     * @param proposal The offer.
     */
    public ProposalBanner(final MachineInternalFrame frame, final Workspace.Proposal proposal)
    {
        super(new BorderLayout(12, 0));
        m_frame = frame;
        m_proposal = proposal;

        setOpaque(true);
        setBorder(BorderFactory.createEmptyBorder(8, 12, 8, 8));

        m_message = new JLabel("Claude suggests a tidier layout — " + proposal.summary);
        m_message.setFont(Theme.ui(Font.PLAIN, 12));

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        buttons.setOpaque(false);

        m_previewButton = new FlatButton(new AbstractAction("Preview")
        {
            public void actionPerformed(ActionEvent e)
            {
                setPreviewing(!m_previewing);
            }
        }, null, FlatButton.Style.TOOL, true);
        m_previewButton.setToolTipText("Show where the states would move to, without moving them");

        FlatButton apply = new FlatButton(new AbstractAction("Apply")
        {
            public void actionPerformed(ActionEvent e)
            {
                applyProposal();
            }
        }, null, FlatButton.Style.PRIMARY, true);
        apply.setToolTipText("Move the states. One Ctrl+Z undoes it");

        FlatButton dismiss = new FlatButton(new AbstractAction("Dismiss")
        {
            public void actionPerformed(ActionEvent e)
            {
                proposal.status = "dismissed";
                close();
            }
        }, null, FlatButton.Style.TOOL, true);
        dismiss.setToolTipText("Leave the layout alone");

        buttons.add(m_previewButton);
        buttons.add(dismiss);
        buttons.add(apply);

        add(m_message, BorderLayout.CENTER);
        add(buttons, BorderLayout.EAST);

        Theme.onChange(new Runnable()
        {
            public void run()
            {
                applyTheme();
            }
        });
        applyTheme();
    }

    /**
     * Paint the banner's own background, which the theme's accent tint marks out from the canvas
     * below it without shouting.
     * @param g The graphics object to render to.
     */
    protected void paintComponent(Graphics g)
    {
        Graphics2D g2d = Theme.prepare(g);
        Theme.Palette p = Theme.palette();
        g2d.setColor(p.accentSoft);
        g2d.fillRect(0, 0, getWidth(), getHeight());
        g2d.setColor(p.border);
        g2d.drawLine(0, getHeight() - 1, getWidth(), getHeight() - 1);
    }

    private void applyTheme()
    {
        m_message.setForeground(Theme.palette().text);
        repaint();
    }

    /**
     * Show or hide the ghosted positions the offer would move states to.
     * @param on Whether to show them.
     */
    private void setPreviewing(boolean on)
    {
        m_previewing = on;
        m_frame.getGfxPanel().setLayoutPreview(on? m_proposal.positions : null);
        // FlatButton paints its own caption; setText is inherited but ignored.
        m_previewButton.setCaption(on? "Hide preview" : "Preview");
    }

    /**
     * Move the states, as one undoable step.
     */
    private void applyProposal()
    {
        MachineGraphicsPanel panel = m_frame.getGfxPanel();
        Machine machine = panel.getSimulator().getMachine();

        final ArrayList<State> states = new ArrayList<State>();
        final ArrayList<int[]> before = new ArrayList<int[]>();
        final ArrayList<int[]> after = new ArrayList<int[]>();
        for (Object o : machine.getStates())
        {
            State state = (State)o;
            int[] to = m_proposal.positions.get(state.getLabel());
            if (to == null || (to[0] == state.getX() && to[1] == state.getY()))
            {
                continue;
            }
            states.add(state);
            before.add(new int[] { state.getX(), state.getY() });
            after.add(new int[] { to[0], to[1] });
        }

        final Machine target = machine;
        panel.doCommand(new TMCommand()
        {
            public void doCommand()
            {
                move(target, states, after);
            }

            public void undoCommand()
            {
                move(target, states, before);
            }

            public String getName()
            {
                return "Claude: tidy the layout";
            }
        });

        m_proposal.status = "applied";
        if (MainWindow.getInstance() != null)
        {
            MainWindow.getInstance().getConsole().log(
                    "Applied Claude's layout for %s — Ctrl+Z to put it back", m_frame.getTitle());
        }
        close();
    }

    /**
     * Put a set of states at a set of positions, dragging the arrows at either end with them.
     * @param machine The machine being rearranged.
     * @param states The states to move.
     * @param positions Where to put them, in the same order.
     */
    private static void move(Machine machine, ArrayList<State> states, ArrayList<int[]> positions)
    {
        for (int i = 0; i < states.size(); i++)
        {
            states.get(i).setPosition(positions.get(i)[0], positions.get(i)[1]);
        }
        // Every arrow touching a moved state needs its curve recomputed, or it points at where the
        // state used to be.
        Collection<State> moved = states;
        for (Object o : machine.getTransitions())
        {
            Transition t = (Transition)o;
            if (moved.contains(t.getFromState()) || moved.contains(t.getToState()))
            {
                tuataraTMSim.agent.Layout.route(machine, t);
            }
        }
    }

    /**
     * Take the banner down, and any preview with it.
     */
    public void close()
    {
        setPreviewing(false);
        m_frame.setBanner(null);
    }

    /**
     * The offer this banner is about.
     * @return The proposal.
     */
    public Workspace.Proposal getProposal()
    {
        return m_proposal;
    }

    /**
     * Keeps the strip a sensible height whatever the buttons want.
     * @return The preferred size.
     */
    public Dimension getPreferredSize()
    {
        Dimension size = super.getPreferredSize();
        return new Dimension(size.width, Math.max(size.height, 38));
    }

    /**
     * The tab this belongs to.
     */
    private final MachineInternalFrame m_frame;

    /**
     * The offer.
     */
    private final Workspace.Proposal m_proposal;

    /**
     * What the offer says.
     */
    private final JLabel m_message;

    /**
     * Toggles the ghosted preview.
     */
    private final FlatButton m_previewButton;

    /**
     * Whether the preview is showing.
     */
    private boolean m_previewing = false;
}
