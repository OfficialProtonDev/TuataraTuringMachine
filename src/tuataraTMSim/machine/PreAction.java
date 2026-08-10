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

package tuataraTMSim.machine;

import java.awt.*;
import java.awt.geom.Rectangle2D;
import java.io.*;
import tuataraTMSim.exceptions.ComputationFailedException;
import tuataraTMSim.Theme;

/**
 * An abstraction of an action with a precondition for a state transition of a machine.
 *
 * NOTE: This type is serialized to disk as part of a saved machine, and declares
 * serialVersionUID = 1L. Its fields must not change; appearance is derived from {@link Theme} at
 * paint time.
 */
public abstract class PreAction implements Serializable
{
    /**
     * Serialization version.
     */
    public static final long serialVersionUID = 1L;

    /**
     * Horizontal padding between the action text and the edge of its plate.
     */
    protected static final int PILL_PAD_X = 5;

    /**
     * Vertical padding between the action text and the edge of its plate.
     */
    protected static final int PILL_PAD_Y = 2;

    /**
     * Creates an instance of PreAction.
     * @param dir The direction the read/write head should move.
     * @param input The value for which if read from the tape, the action will occur.
     * @param output The value which will be written to the tape.
     */
    public PreAction(int dir, char input, char output)
    {
        m_direction = dir;
        m_inputChar = input;
        m_outputChar = output;
    }

    /**
     * Gets the character that must be read from the tape in order for the action to occur.
     * @return The input character for this action.
     */
    public char getInputChar()
    {
        return m_inputChar;
    }

    /**
     * Set the input character for this action.
     * @param c The new input character.
     */
    public void setInputChar(char c)
    {
        m_inputChar = c;
    }

    /**
     * Get the character that this action writes to the tape, if this action writes to the tape.
     * @return The value to be written to the tape.
     */
    public char getOutputChar()
    {
        return m_outputChar;
    }

    /**
     * Set the character that this action will write to the tape, if this action writes to the tape.
     * @param c The new value to be written to the tape.
     */
    public void setOutputChar(char c)
    {
        m_outputChar = c;
    }

    /**
     * Get the direction the head moves for this action.
     * @return The direction the head moves for this action.
     */
    public int getDirection()
    {
        return m_direction;
    }

    /**
     * Set the direction the head moves for this action.
     * @param dir The direction the head should move.
     */
    public void setDirection(int dir)
    {
        m_direction = dir;
    }

    /**
     * Determine if this action moves the read/write head of the tape.
     * @return true if the action moves the read/write head of the tape, false otherwise.
     */
    public boolean movesHead()
    {
        return m_direction != 0;
    }
 
    /**
     * The font used to render an action's text. Transition subclasses measure their symbol
     * bounding boxes with this same font, so that hit-testing matches what is drawn.
     * @return The action font.
     */
    public static Font actionFont()
    {
        return Theme.mono(Font.PLAIN, 12);
    }

    /**
     * Render the action to a graphics object. The text will be centered at (x, y).
     *
     * The text sits on a rounded plate in the canvas colour, so that it stays legible where it
     * overlaps other transitions, which is common in a dense diagram.
     *
     * @param g The graphics object to render to.
     * @param x The X ordinate to render to.
     * @param y The Y ordinate to render to.
     */
    public void paint(Graphics g, int x, int y)
    {
        Graphics2D g2d = Theme.prepare(g);
        Theme.Palette p = Theme.palette();

        String outputStr = toString();
        Font font = actionFont();
        FontMetrics metrics = g.getFontMetrics(font);
        Rectangle2D bounds = metrics.getStringBounds(outputStr, g);

        double w = bounds.getWidth() + PILL_PAD_X * 2;
        double h = metrics.getAscent() + metrics.getDescent() + PILL_PAD_Y * 2;
        double px = x - w / 2;
        double py = y - h / 2;

        Shape pill = Theme.round(px, py, w, h, h / 2);
        g2d.setColor(Theme.alpha(p.actionPill, 235));
        g2d.fill(pill);
        g2d.setColor(Theme.alpha(p.border, 190));
        g2d.setStroke(new BasicStroke(1f));
        g2d.draw(pill);

        g2d.setFont(font);
        g2d.setColor(p.actionText);
        Theme.drawCentered(g2d, outputStr, x, y);
    }
 
    /** 
     * Perform the action specified by this object on the given tape.
     * @param t The tape to be modified.
     * @throws ComputationFailedException If the read/write head falls off the tape.
     */
    public abstract void performAction(Tape t) throws ComputationFailedException;

    /**
     * Get a String representation of this action.
     * @return A String representation of this action.
     */
    public abstract String toString();

    /**
     * The directoin the read/write head should move.
     */
    protected int m_direction;

    /**
     * The character that must be read from the tape for this action to occur.
     */
    protected char m_inputChar;

    /**
     * The character that should be written to the tape.
     */
    protected char m_outputChar;
}
