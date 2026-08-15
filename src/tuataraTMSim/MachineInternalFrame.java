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
import java.awt.Rectangle;
import java.awt.geom.Point2D;
import java.io.File;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JViewport;

/**
 * A document containing a panel for displaying a machine. One of these becomes one tab in the
 * main window.
 *
 * This was previously a JInternalFrame floating in a JDesktopPane. Machines are now shown as tabs,
 * but the type deliberately keeps its original name and method signatures, so that the parts of the
 * program which merely ask a panel for its frame's title -- ExecutionTimerTask, TMGraphicsPanel and
 * MachineGraphicsPanel -- did not have to change.
 */
public class MachineInternalFrame extends JPanel
{
    /**
     * Creates a new instance of MachineInternalFrame.
     * @param gfxPanel The current graphics panel.
     * @param windowIdx The index of this window, i.e. how many windows have been created before it.
     */
    public MachineInternalFrame(MachineGraphicsPanel gfxPanel, int windowIdx)
    {
        super(new BorderLayout());
        m_gfxPanel = gfxPanel;
        m_idx = windowIdx;
        m_title = "untitled";
    }

    /**
     * Show a strip above the diagram, or take the current one down.
     * @param banner The banner to show, or null to remove whatever is there.
     */
    public void setBanner(ProposalBanner banner)
    {
        if (m_banner != null)
        {
            remove(m_banner);
        }
        m_banner = banner;
        if (m_banner != null)
        {
            add(m_banner, BorderLayout.NORTH);
        }
        revalidate();
        repaint();
    }

    /**
     * The strip currently shown above the diagram.
     * @return The banner, or null if there is none.
     */
    public ProposalBanner getBanner()
    {
        return m_banner;
    }

    /**
     * Gets the current graphics panel.
     * @return The current graphics panel
     */
    public MachineGraphicsPanel getGfxPanel()
    {
        return m_gfxPanel;
    }

    /**
     * Get the frame index.
     * @return The frame index.
     */
    public int getIndex()
    {
        return m_idx;
    }

    /**
     * Get the title of this document, as shown on its tab.
     * @return The title.
     */
    public String getTitle()
    {
        return m_title;
    }

    /**
     * Set the title of this document, and refresh the tab showing it.
     * @param title The new title.
     */
    public void setTitle(String title)
    {
        m_title = title;
        MainWindow inst = MainWindow.getInstance();
        if (inst != null)
        {
            inst.refreshTab(this);
        }
    }

    /**
     * Determine whether this document has unsaved changes, which the tab marks with a dot.
     * @return true if the machine has been modified since its last save, false otherwise.
     */
    public boolean isModified()
    {
        return m_gfxPanel != null && m_gfxPanel.isModifiedSinceSave();
    }

    /**
     * Update the title of the frame to reflect the filename of the underlying machine.
     */
    public void updateTitle()
    {
        // The modified marker is rendered by the tab itself rather than being baked into the title
        // with a leading asterisk, as it was when this was a window title.
        setTitle(String.format("%s [%s]", m_gfxPanel.getFilename(), m_gfxPanel.getMachineType()));
    }

    /**
     * Sets this document's pointer to its scroll pane, but doesnt actually do anything in the
     * way of adding the scroll pane to the component.
     * @param sp The scroll panel to track.
     */
    public void setScrollPane(JScrollPane sp)
    {
        m_sp = sp;
    }

    /**
     * Get the center of the frame viewport.
     * @return The center of the frame viewport.
     */
    public Point2D getCenterOfViewPort()
    {
        if (m_sp == null)
        {
            return new Point2D.Float(0.0f,0.0f);
        }
        JViewport vp = m_sp.getViewport();
        Rectangle vpRect = vp.getViewRect();
        return new Point2D.Double(vpRect.getCenterX(), vpRect.getCenterY());
    }

    /**
     * Close this document, removing its tab from the main window.
     *
     * Retains the name the JInternalFrame version used, since callers throughout the program invoke
     * it to close a machine.
     */
    public void dispose()
    {
        MainWindow inst = MainWindow.getInstance();
        if (inst != null)
        {
            inst.removeFrame(this);
        }
    }

    /**
     * Register a callback to be run once this document has been closed. Replaces the
     * internalFrameClosed notification the JInternalFrame version provided, which TMGraphicsPanel
     * relies on to tidy up submachines.
     * @param r The callback to run.
     */
    public void addCloseListener(Runnable r)
    {
        m_closeListeners.add(r);
    }

    /**
     * Run every registered close callback. Called by the main window once the tab has been removed.
     */
    public void fireClosed()
    {
        for (Runnable r : m_closeListeners)
        {
            r.run();
        }
    }

    /**
     * Determine whether this document is currently open as a tab.
     * @return true if the document is open, false otherwise.
     */
    public boolean isOpen()
    {
        return getParent() != null;
    }

    /**
     * The current graphics panel.
     */
    private MachineGraphicsPanel m_gfxPanel;

    /**
     * The strip shown above the diagram, or null.
     */
    private ProposalBanner m_banner;

    /**
     * The window index.
     */
    private int m_idx;

    /**
     * The title of this document.
     */
    private String m_title;

    /**
     * The scroll panel to track.
     */
    private JScrollPane m_sp;

    /**
     * Callbacks to run once this document has been closed.
     */
    private java.util.ArrayList<Runnable> m_closeListeners = new java.util.ArrayList<Runnable>();
}
