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
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Deque;
import javax.swing.*;
import javax.swing.event.HyperlinkEvent;
import javax.swing.event.HyperlinkListener;
import javax.swing.text.html.HTMLDocument;
import javax.swing.text.html.HTMLEditorKit;
import javax.swing.text.html.HTMLFrameHyperlinkEvent;
import javax.swing.text.html.StyleSheet;

/**
 * A window which displays the bundled help documentation, stored as HTML.
 *
 * Replaces an internal frame which had no navigation at all: following a link left the reader with
 * no way back short of closing and reopening the window.
 */
public class HelpDialog extends JDialog
{
    /**
     * The page shown when the window opens, and when Home is pressed.
     */
    protected static final String HOME_PAGE = "help/index.html";

    /**
     * Creates a new instance of HelpDialog.
     * @param owner The frame owning this dialog.
     */
    public HelpDialog(Frame owner)
    {
        super(owner, "Help", false);
        initComponents();
    }

    /**
     * Build the window.
     */
    private void initComponents()
    {
        Theme.Palette p = Theme.palette();

        m_editor = new JEditorPane();
        m_editor.setEditable(false);
        HTMLEditorKit kit = new HTMLEditorKit();
        m_editor.setEditorKit(kit);
        m_editor.setBorder(BorderFactory.createEmptyBorder(4, 12, 12, 12));

        // Restyle the bundled pages to match the application rather than looking like a 1990s
        // browser default.
        StyleSheet css = kit.getStyleSheet();
        css.addRule("body { font-family: '" + Theme.ui(Font.PLAIN, 13).getFamily() + "'; font-size: 10pt; "
                  + "color: " + hex(p.text) + "; background: " + hex(p.surface) + "; margin: 8px; }");
        css.addRule("h1, h2, h3 { color: " + hex(p.text) + "; margin-bottom: 6px; }");
        css.addRule("h1 { font-size: 16pt; }");
        css.addRule("h2 { font-size: 13pt; }");
        css.addRule("a { color: " + hex(p.accent) + "; text-decoration: none; }");
        css.addRule("code, pre, tt { font-family: '" + Theme.mono(Font.PLAIN, 12).getFamily() + "'; "
                  + "background: " + hex(p.surfaceAlt) + "; }");
        css.addRule("td, th { padding: 3px 8px; }");

        m_editor.addHyperlinkListener(new Hyperactive());

        JScrollPane scroll = new JScrollPane(m_editor);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.getVerticalScrollBar().setUnitIncrement(18);

        // Navigation bar.
        JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 4));
        bar.setBackground(p.background);
        bar.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, p.border));

        m_back = new FlatButton(null, "tape-left", FlatButton.Style.TOOL, false);
        m_back.setToolTipText("Back");
        m_back.setEnabled(false);
        m_back.addActionListener(new ActionListener()
        {
            public void actionPerformed(ActionEvent e)
            {
                if (!m_history.isEmpty())
                {
                    navigate(m_history.pop(), false);
                }
            }
        });

        FlatButton home = new FlatButton(null, "help", FlatButton.Style.TOOL, false);
        home.setToolTipText("Contents");
        home.addActionListener(new ActionListener()
        {
            public void actionPerformed(ActionEvent e)
            {
                java.net.URL url = HelpDialog.class.getResource(HOME_PAGE);
                if (url != null)
                {
                    navigate(url, true);
                }
            }
        });

        bar.add(m_back);
        bar.add(home);

        JPanel root = new JPanel(new BorderLayout());
        root.add(bar, BorderLayout.NORTH);
        root.add(scroll, BorderLayout.CENTER);
        setContentPane(root);

        java.net.URL helpURL = HelpDialog.class.getResource(HOME_PAGE);
        if (helpURL != null)
        {
            navigate(helpURL, false);
        }

        setSize(760, 620);

        getRootPane().registerKeyboardAction(new ActionListener()
        {
            public void actionPerformed(ActionEvent e)
            {
                setVisible(false);
            }
        }, KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);
    }

    /**
     * Format a colour as a CSS hex literal.
     * @param c The colour to format.
     * @return The colour as #rrggbb.
     */
    private static String hex(Color c)
    {
        return String.format("#%02x%02x%02x", c.getRed(), c.getGreen(), c.getBlue());
    }

    /**
     * Show a page, optionally recording the page being left so that Back can return to it.
     * @param url The page to show.
     * @param record true to push the current page onto the history.
     */
    private void navigate(java.net.URL url, boolean record)
    {
        try
        {
            java.net.URL previous = m_editor.getPage();
            if (record && previous != null && !previous.equals(url))
            {
                m_history.push(previous);
            }
            m_editor.setPage(url);
        }
        catch (IOException e)
        {
            // The pages are bundled in the archive, so this should not occur; if it does, the
            // window simply keeps showing whatever it already had.
        }
        m_back.setEnabled(!m_history.isEmpty());
    }

    /**
     * Handle hyperlinks in the html help. Code borrowed from the Sun Java 1.4.2 API Guide:
     * http://java.sun.com/j2se/1.4.2/docs/api/javax/swing/JEditorPane.html
     */
    private class Hyperactive implements HyperlinkListener
    {
        /**
         * Called when a hyperlink is clicked.
         * @param e The event information associated with the click event.
         */
        public void hyperlinkUpdate(HyperlinkEvent e)
        {
            if (e.getEventType() != HyperlinkEvent.EventType.ACTIVATED)
            {
                return;
            }
            JEditorPane pane = (JEditorPane)e.getSource();
            if (e instanceof HTMLFrameHyperlinkEvent)
            {
                HTMLFrameHyperlinkEvent evt = (HTMLFrameHyperlinkEvent)e;
                HTMLDocument doc = (HTMLDocument)pane.getDocument();
                doc.processHTMLFrameHyperlinkEvent(evt);
            }
            else if (e.getURL() != null)
            {
                navigate(e.getURL(), true);
            }
        }
    }

    /**
     * The pane rendering the help pages.
     */
    private JEditorPane m_editor;

    /**
     * The button which returns to the previous page.
     */
    private FlatButton m_back;

    /**
     * Pages visited, most recent first.
     */
    private Deque<java.net.URL> m_history = new ArrayDeque<java.net.URL>();
}
