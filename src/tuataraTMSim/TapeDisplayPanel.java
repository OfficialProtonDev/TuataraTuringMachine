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
import java.awt.geom.*;
import java.io.File;
import javax.swing.*;
import tuataraTMSim.machine.Tape;

/**
 * A panel for displaying a Turing machine tape. Does not include any buttons, just the tape.
 *
 * Cells are drawn as discrete rounded tiles with a ruler above them, and the cell under the
 * read/write head is filled with the accent colour and flagged with a marker, so that the head
 * position is readable at a glance while a machine runs.
 */
public class TapeDisplayPanel extends JPanel
{
    /**
     * Width of a tape cell, in pixels.
     */
    protected static final int CELL_WIDTH = 26;

    /**
     * Height of a tape cell, in pixels.
     */
    protected static final int CELL_HEIGHT = 30;

    /**
     * Gap between adjacent tape cells, in pixels.
     */
    protected static final int CELL_GAP = 3;

    /**
     * Distance between the left edges of adjacent cells, in pixels.
     */
    protected static final int CELL_PITCH = CELL_WIDTH + CELL_GAP;

    /**
     * Corner radius of a tape cell, in pixels.
     */
    protected static final int CELL_RADIUS = 6;

    /**
     * Height of the index ruler drawn above the tape, in pixels.
     */
    protected static final int RULER_HEIGHT = 18;

    /**
     * Vertical space immediately above the tape reserved for the head marker, in pixels. Ruler
     * labels are centred in the band above this rather than in the ruler as a whole, so that the
     * marker never runs into the head's own index.
     */
    protected static final int HEAD_MARKER_ZONE = 7;

    /**
     * Horizontal padding around the entire tape.
     */
    protected static final int TAPEPADDING_X = 6;

    /**
     * Vertical padding around the entire tape.
     */
    protected static final int TAPEPADDING_Y = 4;

    /**
     * Cell indices which are a multiple of this are labelled on the ruler.
     */
    protected static final int RULER_INTERVAL = 5;

    /**
     * Creates a new instance of TapeDisplayPanel.
     * @param tape The underlying tape.
     */
    public TapeDisplayPanel(Tape tape)
    {
        this(tape, null);
    }

    /**
     * Creates a new instance of TapeDisplayPanel.
     * @param tape The underlying tape.
     * @param file The file associated with the tape.
     */
    public TapeDisplayPanel(Tape tape, File file)
    {
        m_tape = tape;
        m_file = file;
        initComponents();
    }

    /**
     * Initialization routine.
     */
    public void initComponents()
    {
        setFocusable(false);
        setOpaque(true);
        setPreferredSize(new Dimension(500, RULER_HEIGHT + CELL_HEIGHT + TAPEPADDING_Y * 2));
        setCursor(Cursor.getPredefinedCursor(Cursor.TEXT_CURSOR));
        setToolTipText("Click a cell to move the read/write head; type to write");

        addMouseListener(new MouseAdapter()
        {
            public void mouseClicked(MouseEvent e)
            {
                if (!m_isEditingEnabled)
                {
                    return;
                }

                // Shift the r/w head to the cell that was clicked on. The arithmetic here must
                // mirror paintTape: when the head is near the left end of the tape, the leftmost
                // slot is occupied by the end stop rather than by a cell, which is what the
                // startPos of -1 accounts for.
                int cellsFromLeft = (e.getX() - TAPEPADDING_X) / CELL_PITCH;
                int visibleCells = numCellsViewable();
                int startPos = m_tape.headLocation() - (visibleCells / 2);
                if (startPos < 0)
                {
                    startPos = -1;
                    visibleCells--;
                }
                int newCell = Math.max(cellsFromLeft + startPos, 0);

                while (m_tape.headLocation() < newCell)
                {
                    m_tape.headRight();
                }

                while (m_tape.headLocation() > newCell)
                {
                    try { m_tape.headLeft(); }
                    catch (Exception e2) { break; }
                }
                repaint();
                MainWindow.getInstance().refreshStatus();
            }
        });
    }

    /**
     * Paint this component to the given graphics object.
     * @param g The graphics object to render onto.
     */
    protected void paintComponent(Graphics g)
    {
        Graphics2D g2d = Theme.prepare(g.create());
        Theme.Palette p = Theme.palette();

        g2d.setColor(p.tapeBg);
        g2d.fillRect(0, 0, getWidth(), getHeight());

        paintTape(g2d, TAPEPADDING_X, TAPEPADDING_Y + RULER_HEIGHT);
        g2d.dispose();
    }

    /**
     * Paint the tape on the graphics object at the given location.
     * @param g The graphics object to render onto.
     * @param x The X ordinate of the upper-left corner of the first cell.
     * @param y The Y ordinate of the upper-left corner of the first cell.
     */
    public void paintTape(Graphics g, int x, int y)
    {
        Graphics2D g2d = Theme.prepare(g);

        // Figure out what cells to render, keeping the head near the middle of the strip.
        int visibleCells = numCellsViewable();
        boolean drawTapeEnd = false;
        int startPos = m_tape.headLocation() - (visibleCells / 2);
        if (startPos < 0)
        {
            startPos = 0;
            drawTapeEnd = true;
            visibleCells--;
        }

        String tapeStr = m_tape.getPartialString(startPos, visibleCells + 1);

        int slot = 0;
        if (drawTapeEnd)
        {
            paintEndStop(g2d, x, y);
            slot++;
        }

        for (int i = 0; i < tapeStr.length(); i++)
        {
            int index = i + startPos;
            boolean isHeadLoc = m_tape.headLocation() == index;
            int cellX = x + slot * CELL_PITCH;

            paintTapeCell(g2d, tapeStr.charAt(i), isHeadLoc, cellX, y);
            paintRulerLabel(g2d, index, isHeadLoc, cellX, y);
            slot++;
        }
    }

    /**
     * Paint the marker shown at the very start of the tape, indicating that there is nothing to the
     * left of this point.
     * @param g2d The graphics object to render onto.
     * @param x The X ordinate of the upper-left corner of the slot.
     * @param y The Y ordinate of the upper-left corner of the slot.
     */
    protected void paintEndStop(Graphics2D g2d, int x, int y)
    {
        Theme.Palette p = Theme.palette();
        g2d.setColor(p.tapeRuler);
        g2d.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        int cx = x + CELL_WIDTH / 2;
        g2d.draw(new Line2D.Float(cx - 3, y + 5, cx - 3, y + CELL_HEIGHT - 5));
        g2d.draw(new Line2D.Float(cx - 3, y + CELL_HEIGHT / 2f, cx + 4, y + CELL_HEIGHT / 2f));
    }

    /**
     * Paint the index of a cell on the ruler above the tape. Only multiples of the ruler interval
     * are labelled, along with the head position, which is always labelled.
     * @param g2d The graphics object to render onto.
     * @param index The index of the cell.
     * @param isHeadLocation true if the read/write head is in this cell, false otherwise.
     * @param x The X ordinate of the upper-left corner of the cell.
     * @param y The Y ordinate of the upper-left corner of the cell.
     */
    protected void paintRulerLabel(Graphics2D g2d, int index, boolean isHeadLocation, int x, int y)
    {
        if (!isHeadLocation && index % RULER_INTERVAL != 0)
        {
            return;
        }
        Theme.Palette p = Theme.palette();
        g2d.setFont(Theme.ui(isHeadLocation? Font.BOLD : Font.PLAIN, 10));
        g2d.setColor(isHeadLocation? p.tapeHead : p.tapeRuler);
        // Centred in the ruler above the marker zone, so that every label -- including the head's,
        // which is the only one drawn over a marker -- sits on a common baseline.
        double centreY = y - HEAD_MARKER_ZONE - (RULER_HEIGHT - HEAD_MARKER_ZONE) / 2.0;
        Theme.drawCentered(g2d, String.valueOf(index), x + CELL_WIDTH / 2.0, centreY);
    }

    /**
     * Paint a single cell of the tape.
     * @param g2d The graphics object to render onto.
     * @param c The character contained in this cell.
     * @param isHeadLocation true if the read/write head is in this cell, false otherwise.
     * @param x The X ordinate of the upper-left corner of the cell.
     * @param y The Y ordinate of the upper-left corner of the cell.
     */
    public void paintTapeCell(Graphics2D g2d, char c, boolean isHeadLocation, int x, int y)
    {
        Theme.Palette p = Theme.palette();
        Shape cell = Theme.round(x, y, CELL_WIDTH, CELL_HEIGHT, CELL_RADIUS);

        g2d.setColor(isHeadLocation? p.tapeHead : p.tapeCell);
        g2d.fill(cell);

        if (!isHeadLocation)
        {
            g2d.setColor(p.tapeCellBorder);
            g2d.setStroke(new BasicStroke(1f));
            g2d.draw(cell);
        }

        // The blank symbol is rendered as a muted placeholder rather than as a literal character,
        // so that occupied cells stand out from empty ones.
        boolean blank = c == Tape.BLANK_SYMBOL;
        g2d.setFont(Theme.mono(blank? Font.PLAIN : Font.BOLD, 15));
        g2d.setColor(isHeadLocation? p.onTapeHead : (blank? p.tapeRuler : p.tapeText));
        Theme.drawCentered(g2d, String.valueOf(c), x + CELL_WIDTH / 2.0, y + CELL_HEIGHT / 2.0);

        if (isHeadLocation)
        {
            // A downward wedge above the cell, marking the head. It is confined to the marker zone
            // so that it cannot reach the index printed above it.
            Path2D.Float wedge = new Path2D.Float();
            float cx = x + CELL_WIDTH / 2f;
            wedge.moveTo(cx - 4, y - HEAD_MARKER_ZONE + 1);
            wedge.lineTo(cx + 4, y - HEAD_MARKER_ZONE + 1);
            wedge.lineTo(cx, y - 1);
            wedge.closePath();
            g2d.setColor(p.tapeHead);
            g2d.fill(wedge);
        }
    }

    /**
     * A helper function that calculates how many cells will fit on the viewing panel at one time,
     * rounded down to the nearest whole number.
     * @return The number of cells that will fit on the viewing panel.
     */
    private int numCellsViewable()
    {
        return Math.max(1, (getWidth() - TAPEPADDING_X * 2) / CELL_PITCH);
    }

    /**
     * Get the tape currently associated with this panel.
     * @return The tape associated with this panel.
     */
    public Tape getTape()
    {
        return m_tape;
    }

    /**
     * Change the tape currently associated with this panel.
     * @param t The new tape associated with this panel.
     */
    public void setTape(Tape t)
    {
        m_tape = t;
    }

    /**
     * Determine whether a symbol may be written to the tape. A symbol is writable when the machine
     * being edited declares it in its alphabet, matching the restriction already applied when
     * labelling transitions. With no machine open there is no alphabet to answer the question, so
     * the tape accepts anything.
     * @param c The symbol to test.
     * @return true if the symbol may be written, false otherwise.
     */
    private boolean isWritable(char c)
    {
        MainWindow inst = MainWindow.getInstance();
        MachineGraphicsPanel gfx = inst == null? null : inst.getSelectedGraphicsPanel();
        return gfx == null || gfx.getAlphabet().containsSymbol(c);
    }

    /**
     * Report an attempt to write a symbol outside the machine's alphabet, leaving the tape
     * untouched.
     * @param c The rejected symbol.
     * @return false, as the tape is unchanged.
     */
    private boolean rejectSymbol(char c)
    {
        Toolkit.getDefaultToolkit().beep();
        MainWindow inst = MainWindow.getInstance();
        if (inst != null && inst.getConsole() != null)
        {
            inst.getConsole().logWarning(
                    "'%c' is not in this machine's alphabet; configure the alphabet to use it", c);
        }
        return false;
    }

    /**
     * Handle a keystroke
     * @param e The generating event.
     * @return true if the event caused a change to the tape, false otherwise.
     */
    public boolean handleKeyEvent(KeyEvent e)
    {
       char c = e.getKeyChar();
       c = Character.toUpperCase(c);
       boolean handled = false;

       if (Character.isLetterOrDigit(c))
       {
            if (!isWritable(c))
            {
                return rejectSymbol(c);
            }
            getTape().write(c);
            getTape().headRight();
            handled = true;
       }
       else if (c == ' ' || c == Tape.BLANK_SYMBOL)
       {
            getTape().write(Tape.BLANK_SYMBOL);
            getTape().headRight();
            handled = true;
       }
       else if (e.getKeyCode() == KeyEvent.VK_LEFT)
       {
            try { getTape().headLeft(); }
            catch (Exception e2) { }
            handled = true;
       }
       else if (e.getKeyCode() == KeyEvent.VK_RIGHT)
       {
            getTape().headRight();
            handled = true;
       }
       else if (e.getKeyCode() == KeyEvent.VK_BACK_SPACE)
       {
           getTape().write(Tape.BLANK_SYMBOL);
           try { getTape().headLeft(); }
           catch (Exception e2) { }
           handled = true;
       }

       if (handled)
       {
           repaint();
           MainWindow.getInstance().refreshStatus();
       }
       return handled;
    }

    /**
     * Get the file associated with this tape.
     * @return The file associated with this tape.
     */
    public File getFile()
    {
        return m_file;
    }

    /**
     * Set the file associated with this tape.
     * @param file The new file associated with this tape.
     */
    public void setFile(File file)
    {
        m_file = file;
    }

    /**
     * Set whether editing is enabled.
     * @param isEnabled true if editing is enabled, false otherwise.
     */
    public void setEditingEnabled(boolean isEnabled)
    {
        m_isEditingEnabled = isEnabled;
    }

    /**
     * The underlying tape.
     */
    private Tape m_tape;

    /**
     * The associated file.
     */
    private File m_file = null;

    /**
     * Whether editing is enabled.
     */
    private boolean m_isEditingEnabled = true;
}
