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
import javax.swing.plaf.basic.BasicTabbedPaneUI;

/**
 * The tabbed container holding every open machine.
 *
 * Replaces the JDesktopPane the program used to use, where machines opened as floating internal
 * windows scattered at pseudo-random offsets, stacked on top of one another, and had to be tiled by
 * hand. Tabs make every open machine visible and reachable in one click.
 */
public class MachineTabPane extends JTabbedPane
{
    /**
     * Creates a new instance of MachineTabPane.
     */
    public MachineTabPane()
    {
        super(JTabbedPane.TOP, JTabbedPane.SCROLL_TAB_LAYOUT);
        setFocusable(false);
        setUI(new FlatTabbedPaneUI());
        setBorder(BorderFactory.createEmptyBorder());

        Theme.onChange(new Runnable()
        {
            public void run()
            {
                // The delegate caches palette-derived values, so it is rebuilt wholesale.
                setUI(new FlatTabbedPaneUI());
                for (int i = 0; i < getTabCount(); i++)
                {
                    Component c = getTabComponentAt(i);
                    if (c instanceof TabHeader)
                    {
                        ((TabHeader)c).applyTheme();
                    }
                }
                repaint();
            }
        });
    }

    /**
     * Add a document as a new tab, and select it.
     * @param doc The document to add.
     * @param onClose Callback invoked when the user presses the tab's close button.
     */
    public void addDocument(MachineInternalFrame doc, ActionListener onClose)
    {
        addTab(doc.getTitle(), doc);
        int index = indexOfComponent(doc);
        setTabComponentAt(index, new TabHeader(doc, onClose));
        setSelectedIndex(index);
    }

    /**
     * Refresh the tab showing the given document, after its title or modified state changed.
     * @param doc The document whose tab should be refreshed.
     */
    public void refreshTab(MachineInternalFrame doc)
    {
        int index = indexOfComponent(doc);
        if (index < 0)
        {
            return;
        }
        Component c = getTabComponentAt(index);
        if (c instanceof TabHeader)
        {
            ((TabHeader)c).refresh();
        }
    }

    /**
     * Get the document currently selected, or null if no document is open.
     * @return The selected document, or null.
     */
    public MachineInternalFrame getSelectedDocument()
    {
        Component c = getSelectedComponent();
        return c instanceof MachineInternalFrame? (MachineInternalFrame)c : null;
    }

    /**
     * The component rendered inside a tab: a type icon, the machine's title, a marker for unsaved
     * changes, and a close button.
     */
    private class TabHeader extends JPanel
    {
        /**
         * Creates a new instance of TabHeader.
         * @param doc The document this tab represents.
         * @param onClose Callback invoked when the close button is pressed.
         */
        TabHeader(MachineInternalFrame doc, final ActionListener onClose)
        {
            super(new FlowLayout(FlowLayout.LEFT, 6, 0));
            m_doc = doc;
            setOpaque(false);

            m_icon = new JLabel();
            m_label = new JLabel(doc.getTitle());
            m_label.setFont(Theme.ui(Font.PLAIN, 13));

            m_close = new FlatButton(null, "close", FlatButton.Style.TOOL, false);
            m_close.setIconSize(11);
            m_close.setToolTipText("Close this machine");
            m_close.setBorder(BorderFactory.createEmptyBorder());
            m_close.addActionListener(new ActionListener()
            {
                public void actionPerformed(ActionEvent e)
                {
                    onClose.actionPerformed(new ActionEvent(m_doc, ActionEvent.ACTION_PERFORMED, "close"));
                }
            });
            // An unsaved machine shows a dot where the close button would be, which becomes the
            // close button on hover. This is the convention used by most editors, and avoids
            // spending tab width on a separate marker.
            m_close.addMouseListener(new java.awt.event.MouseAdapter()
            {
                public void mouseEntered(java.awt.event.MouseEvent e)
                {
                    applyTheme();
                }

                public void mouseExited(java.awt.event.MouseEvent e)
                {
                    applyTheme();
                }
            });

            add(m_icon);
            add(m_label);
            add(m_close);

            refresh();
        }

        /**
         * Update the tab from the document's current title and modified state.
         */
        void refresh()
        {
            String type = m_doc.getGfxPanel() != null? m_doc.getGfxPanel().getMachineType() : "";
            m_iconName = type.toLowerCase().contains("acceptor") || type.toLowerCase().contains("dfsa")
                       ? "dfsa" : "machine";
            m_label.setText(m_doc.getTitle());
            applyTheme();
            revalidate();
            repaint();
        }

        /**
         * Apply the current palette to this tab header.
         */
        void applyTheme()
        {
            Theme.Palette p = Theme.palette();
            boolean selected = getSelectedComponent() == m_doc;

            m_icon.setIcon(Icons.get(m_iconName, 14, selected? p.accent : p.textMuted));
            m_label.setForeground(selected? p.text : p.textMuted);
            m_label.setFont(Theme.ui(selected? Font.BOLD : Font.PLAIN, 13));

            boolean showDot = m_doc.isModified() && !m_close.getModel().isRollover();
            m_close.setIconName(showDot? "dot" : "close");
            m_close.setToolTipText(m_doc.isModified()
                    ? "Close this machine (unsaved changes)" : "Close this machine");
        }

        /**
         * Render the tab header, keeping its styling in step with the selected tab.
         * @param g The graphics object to render onto.
         */
        protected void paintComponent(Graphics g)
        {
            applyTheme();
            super.paintComponent(g);
        }

        /**
         * The document this tab represents.
         */
        private MachineInternalFrame m_doc;

        /**
         * The machine type icon.
         */
        private JLabel m_icon;

        /**
         * The title label.
         */
        private JLabel m_label;

        /**
         * The close button.
         */
        private FlatButton m_close;

        /**
         * Name of the machine type icon.
         */
        private String m_iconName = "machine";
    }

    /**
     * A tabbed pane delegate which paints flat tabs with an accent underline on the selected tab,
     * in place of Metal's bevelled default.
     */
    private static class FlatTabbedPaneUI extends BasicTabbedPaneUI
    {
        /**
         * Thickness of the indicator drawn under the selected tab.
         */
        private static final int INDICATOR = 2;

        /**
         * Give tabs generous padding; the tab component supplies the content.
         * @param tabPlacement The placement of the tabs.
         * @param tabIndex The index of the tab.
         * @return The insets for the tab.
         */
        protected Insets getTabInsets(int tabPlacement, int tabIndex)
        {
            return new Insets(7, 12, 7, 8);
        }

        /**
         * Remove the inset Metal leaves around the content area.
         * @param tabPlacement The placement of the tabs.
         * @return The insets for the content border.
         */
        protected Insets getContentBorderInsets(int tabPlacement)
        {
            return new Insets(0, 0, 0, 0);
        }

        /**
         * Paint a tab as a flat fill, with an accent underline when selected.
         * @param g The graphics object to render onto.
         * @param tabPlacement The placement of the tabs.
         * @param tabIndex The index of the tab.
         * @param x The X ordinate of the tab.
         * @param y The Y ordinate of the tab.
         * @param w The width of the tab.
         * @param h The height of the tab.
         * @param isSelected Whether this tab is selected.
         */
        protected void paintTabBackground(Graphics g, int tabPlacement, int tabIndex,
                                          int x, int y, int w, int h, boolean isSelected)
        {
            Graphics2D g2d = Theme.prepare(g.create());
            Theme.Palette p = Theme.palette();

            g2d.setColor(isSelected? p.canvas : p.background);
            g2d.fillRect(x, y, w, h);

            if (isSelected)
            {
                g2d.setColor(p.accent);
                g2d.fillRect(x, y + h - INDICATOR, w, INDICATOR);
            }
            else
            {
                g2d.setColor(p.border);
                g2d.fillRect(x + w - 1, y + 6, 1, h - 12);
            }
            g2d.dispose();
        }

        /**
         * Tabs carry no border of their own; the background painting handles all of it.
         * @param g The graphics object to render onto.
         * @param tabPlacement The placement of the tabs.
         * @param tabIndex The index of the tab.
         * @param x The X ordinate of the tab.
         * @param y The Y ordinate of the tab.
         * @param w The width of the tab.
         * @param h The height of the tab.
         * @param isSelected Whether this tab is selected.
         */
        protected void paintTabBorder(Graphics g, int tabPlacement, int tabIndex,
                                      int x, int y, int w, int h, boolean isSelected)
        {
        }

        /**
         * Paint a hairline beneath the tab strip in place of Metal's content border.
         * @param g The graphics object to render onto.
         * @param tabPlacement The placement of the tabs.
         * @param selectedIndex The index of the selected tab.
         */
        protected void paintContentBorder(Graphics g, int tabPlacement, int selectedIndex)
        {
            g.setColor(Theme.palette().border);
            int y = rects.length > 0? rects[0].y + rects[0].height - 1 : 0;
            g.fillRect(0, y, tabPane.getWidth(), 1);
        }

        /**
         * Suppress the focus rectangle.
         * @param g The graphics object to render onto.
         * @param tabPlacement The placement of the tabs.
         * @param rects The tab rectangles.
         * @param tabIndex The index of the tab.
         * @param iconRect The icon rectangle.
         * @param textRect The text rectangle.
         * @param isSelected Whether this tab is selected.
         */
        protected void paintFocusIndicator(Graphics g, int tabPlacement, Rectangle[] rects,
                                           int tabIndex, Rectangle iconRect, Rectangle textRect,
                                           boolean isSelected)
        {
        }
    }
}
