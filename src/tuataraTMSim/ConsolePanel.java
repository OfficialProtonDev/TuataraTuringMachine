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
import java.time.format.DateTimeFormatter;
import java.time.LocalDateTime;
import javax.swing.*;
import javax.swing.text.*;

/**
 * A panel for machines to log information to. This panel may be written to by every machine
 * currently loaded due to the fact that the accesses are mutually exclusive.
 *
 * Messages carry a severity so that failures are visually distinct from ordinary progress, and the
 * view follows the end of the log as it is written, which matters while a machine is running.
 */
public class ConsolePanel extends JPanel
{
    /**
     * Text displayed when the console is opened, or cleared.
     */
    protected static final String SPLASH_TEXT = "Tuatara Turing Machine Simulator " + Global.VERSION;

    /**
     * Longest log kept, in characters. Older output is discarded beyond this.
     */
    protected static final int MAX_CHARACTERS = 120000;

    /**
     * Number of configurations recorded on one line before the trace wraps onto the next.
     */
    protected static final int CONFIGURATIONS_PER_LINE = 8;

    /**
     * Severity of a logged message.
     */
    public enum Level
    {
        /**
         * Ordinary progress.
         */
        INFO,

        /**
         * Something the user should notice, but which did not stop the operation.
         */
        WARNING,

        /**
         * An operation failed.
         */
        ERROR
    }

    /**
     * Creates a new instance of ConsolePanel.
     */
    public ConsolePanel()
    {
        super();
        initComponents();
        Theme.onChange(new Runnable()
        {
            public void run()
            {
                applyTheme();
            }
        });
    }

    /**
     * Initialize every component in this panel.
     */
    private void initComponents()
    {
        setLayout(new BorderLayout());

        // Header, giving the panel an identity and somewhere to hang the clear action.
        JPanel header = new JPanel(new BorderLayout());
        header.setBorder(BorderFactory.createEmptyBorder(4, 10, 4, 6));

        m_title = new JLabel("Console");
        m_title.setFont(Theme.ui(Font.BOLD, 11));

        m_clear = new FlatButton(null, "delete", FlatButton.Style.TOOL, false);
        m_clear.setIconSize(14);
        m_clear.setToolTipText("Clear the console");
        m_clear.addActionListener(new ActionListener()
        {
            public void actionPerformed(ActionEvent e)
            {
                clear();
            }
        });

        header.add(m_title, BorderLayout.WEST);
        header.add(m_clear, BorderLayout.EAST);
        add(header, BorderLayout.NORTH);

        // Body.
        m_text = new JTextPane();
        m_text.setEditable(false);
        m_text.setBorder(BorderFactory.createEmptyBorder(2, 10, 8, 10));
        m_doc = m_text.getStyledDocument();

        // Keep the view pinned to the end of the log as new text arrives.
        DefaultCaret caret = (DefaultCaret)m_text.getCaret();
        caret.setUpdatePolicy(DefaultCaret.ALWAYS_UPDATE);

        m_scroll = new JScrollPane(m_text);
        m_scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        m_scroll.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);
        m_scroll.setBorder(BorderFactory.createEmptyBorder());
        add(m_scroll, BorderLayout.CENTER);

        applyTheme();
        clear();
    }

    /**
     * Apply the current palette to this panel and rebuild the text styles.
     */
    private void applyTheme()
    {
        Theme.Palette p = Theme.palette();

        setBackground(p.consoleBg);
        setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, p.border));
        if (m_title != null)
        {
            m_title.setForeground(p.textMuted);
            m_title.getParent().setBackground(p.consoleBg);
        }
        m_text.setBackground(p.consoleBg);
        m_text.setForeground(p.consoleText);
        m_scroll.getViewport().setBackground(p.consoleBg);

        style("timestamp", Theme.mono(Font.PLAIN, 12), p.consoleMuted);
        style("info",      Theme.ui(Font.PLAIN, 12),   p.consoleText);
        style("warning",   Theme.ui(Font.PLAIN, 12),   p.warning);
        style("error",     Theme.ui(Font.PLAIN, 12),   p.danger);
        style("trace",     Theme.mono(Font.PLAIN, 12), p.consoleText);
        style("splash",    Theme.ui(Font.BOLD, 12),    p.accent);

        repaint();
    }

    /**
     * Define or redefine a named text style.
     * @param name The name of the style.
     * @param font The font the style uses.
     * @param color The colour the style uses.
     */
    private void style(String name, Font font, Color color)
    {
        Style s = m_text.getStyle(name);
        if (s == null)
        {
            s = m_text.addStyle(name, null);
        }
        StyleConstants.setFontFamily(s, font.getFamily());
        StyleConstants.setFontSize(s, font.getSize());
        StyleConstants.setBold(s, font.isBold());
        StyleConstants.setForeground(s, color);
    }

    /**
     * Get the current timestamp formatted as a string.
     * @return The current timestamp.
     */
    private String timestamp()
    {
        return DateTimeFormatter.ofPattern("HH:mm:ss").format(LocalDateTime.now());
    }

    /**
     * Append text to the console in the given style.
     * @param styleName The name of the style to append in.
     * @param fmt The format string to be appended.
     * @param args Arguments for the format string.
     */
    private void append(String styleName, String fmt, Object... args)
    {
        try
        {
            m_doc.insertString(m_doc.getLength(), String.format(fmt, args), m_text.getStyle(styleName));
            trim();
        }
        catch (BadLocationException e)
        {
            // The insert is always at the end of the document, so this cannot occur.
        }
    }

    /**
     * Discard the oldest part of the log once it grows past {@link #MAX_CHARACTERS}. A machine left
     * to run records a configuration per step without limit, and the cost of laying the document
     * out climbs with its length, so an unbounded log makes the whole program crawl exactly when
     * the trace is longest.
     */
    private void trim()
    {
        int excess = m_doc.getLength() - MAX_CHARACTERS;
        if (excess <= 0)
        {
            return;
        }
        try
        {
            // Cut back further than strictly needed, so this runs rarely rather than on almost
            // every append, and drop the remainder of the partial line so the log does not open
            // mid-configuration.
            int cut = excess + MAX_CHARACTERS / 4;
            String head = m_doc.getText(cut, Math.min(512, m_doc.getLength() - cut));
            int newline = head.indexOf('\n');
            if (newline >= 0)
            {
                cut += newline + 1;
            }
            m_doc.remove(0, cut);
        }
        catch (BadLocationException e)
        {
            // Bounded by the document length above, so this cannot occur.
        }
    }

    /**
     * Log a partial message to the console. Subsequent calls to logPartial will continue on the
     * same line, without adding a new timestamp.
     * @param panel The panel logging the text.
     * @param fmt The format string to be logged.
     * @param args Arguments for the format string.
     */
    public void logPartial(MachineGraphicsPanel panel, String fmt, Object... args)
    {
        // Last message was not partial; timestamp and log
        if (!m_partial)
        {
            // Do not add a newline, so we can continue logging after this message.
            append("timestamp", "%s  ", timestamp());
            append("trace", fmt, args);
            m_partial = true;
            m_panel = panel;
        }
        // Last message was a partial message; did the given panel send it?
        else if (m_panel == panel)
        {
            // Continue logging on the same line, but not indefinitely: a run of any length would
            // otherwise become one enormous paragraph, and laying that out gets slower the longer
            // it grows. Wrapping onto a fresh line every so often keeps the cost flat and the
            // trace easier to follow.
            if (++m_partialCount % CONFIGURATIONS_PER_LINE == 0)
            {
                append("trace", "\n");
                append("timestamp", "%s  ", timestamp());
            }
            append("trace", fmt, args);
        }
        // Last message was partial, and the panel did not send it.
        else
        {
            append("warning", " -- Interrupted\n");
            // Begin logging on a new line
            append("timestamp", "%s  ", timestamp());
            append("trace", fmt, args);
            m_partial = true;
            m_panel = panel;
        }
    }

    /**
     * End partial logging. This prints a newline to the end of the current log text.
     */
    public void endPartial()
    {
        if (m_partial) append("trace", "\n");
        m_panel = null;
        m_partial = false;
    }

    /**
     * Log text to the console. Forces text to appear on a new line.
     * @param fmt The format string to be logged.
     * @param args Arguments for the format string.
     */
    public void log(String fmt, Object... args)
    {
        log(Level.INFO, fmt, args);
    }

    /**
     * Log a warning to the console.
     * @param fmt The format string to be logged.
     * @param args Arguments for the format string.
     */
    public void logWarning(String fmt, Object... args)
    {
        log(Level.WARNING, fmt, args);
    }

    /**
     * Log an error to the console.
     * @param fmt The format string to be logged.
     * @param args Arguments for the format string.
     */
    public void logError(String fmt, Object... args)
    {
        log(Level.ERROR, fmt, args);
    }

    /**
     * Log text to the console at the given severity. Forces text to appear on a new line.
     * @param level The severity of the message.
     * @param fmt The format string to be logged.
     * @param args Arguments for the format string.
     */
    public void log(Level level, String fmt, Object... args)
    {
        // Finish any partial messages, then log
        endPartial();
        append("timestamp", "%s  ", timestamp());
        append(level == Level.ERROR? "error" : level == Level.WARNING? "warning" : "info", fmt, args);
        append("info", "\n");
    }

    /**
     * Clear the text area, and draw the splash text.
     */
    public void clear()
    {
        endPartial();
        m_text.setText("");
        append("splash", "%s\n", SPLASH_TEXT);
    }

    /**
     * The underlying text pane used to store the logged text.
     */
    private JTextPane m_text;

    /**
     * The document backing the text pane.
     */
    private StyledDocument m_doc;

    /**
     * The scroll pane containing the text pane.
     */
    private JScrollPane m_scroll;

    /**
     * The header title label.
     */
    private JLabel m_title;

    /**
     * The button which clears the console.
     */
    private FlatButton m_clear;

    /**
     * Panel currently logging a partial message.
     */
    private MachineGraphicsPanel m_panel;

    /**
     * Whether or not we are waiting for more information to add to the log.
     */
    private boolean m_partial;

    /**
     * Configurations written on the current trace line.
     */
    private int m_partialCount;
}
