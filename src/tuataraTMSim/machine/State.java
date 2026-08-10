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
import java.awt.geom.*;
import java.io.Serializable;
import java.util.Collection;
import tuataraTMSim.Theme;

/**
 * Represents a state in a machine.
 *
 * NOTE: This type is serialized to disk as part of a saved machine, and declares
 * serialVersionUID = 1L. Its fields must not change. Appearance is derived entirely from
 * {@link Theme} at paint time, so that saved machines stay loadable and so that states follow a
 * change of palette.
 */
public abstract class State<
    PREACTION extends PreAction,
    TRANSITION extends Transition<PREACTION, ?, MACHINE, SIMULATOR>,
    MACHINE extends Machine<PREACTION, TRANSITION, ?, SIMULATOR>,
    SIMULATOR extends Simulator<PREACTION, TRANSITION, ?, MACHINE>> implements Serializable
{
    /**
     * Serialization version.
     */
    public static final long serialVersionUID = 1L;

    /**
     * Width of the graphical representation of the state, in pixels.
     */
    public static final int STATE_RENDERING_WIDTH = 30;

    /**
     * Distance of graphical text below picture of the state.
     */
    public static final int TEXT_DISTANCE = 15; 

    public State(String label, boolean startState, boolean finalState, int windowX, int windowY)
    {
        m_label = label;
        m_startState = startState;
        m_finalState = finalState;
        m_windowX = windowX;
        m_windowY = windowY;
    }

    /**
     * Determine if this state is the start state. The result of this alone does not guarantee uniqueness.
     * @return true if this is the start state, false otherwise.
     */
    public boolean isStartState()
    {
        return m_startState;
    }

    /**
     * Change whether this state is a start state or not.
     * @param value true if this state should be the start state, false otherwise.
     */
    public void setStartState(boolean value)
    {
        m_startState = value;
    }

    /**
     * Determine if this state is a final state.
     * @return true if this is the final state, false otherwise.
     */
    public boolean isFinalState()
    {
        return m_finalState;
    }

    /**
     * Change whether this state is a final state or not.
     * @param value true if this state should be the final state, false otherwise.
     */
    public void setFinalState(boolean value)
    {
        m_finalState = value;
    }

    /** 
     * Gets this state's label.
     * @return This state's label.
     */
    public String getLabel()
    {
        return m_label;
    }

    /**
     * Sets this state's label.
     * @param name The new label.
     */
    public void setLabel(String name)
    {
        m_label = name;
    }

    /**
     * Get the spatial X ordinate of the object on the viewing plane.
     * @return The X ordinate of this object on the viewing plane.
     */
    public int getX()
    {
        return m_windowX;
    }

    /**
     * Get the spatial Y ordinate of the object on the viewing plane.
     * @return The Y ordinate of this object on the viewing plane.
     */
    public int getY()
    {
        return m_windowY;
    }

    /**
     * Move the graphical representation of the state to (x, y) on the viewing plane.
     * @param x The new X ordinate.
     * @param y The new Y ordinate.
     */
    public void setPosition(int x, int  y)
    {
        m_windowX = x;
        m_windowY = y;
    }

    /**
     * Get the color to paint the interior of this object.
     * @return The color to paint this object.
     */
    protected Paint getPaint()
    {
        return Theme.palette().stateFill;
    }

    /**
     * Get the font used to render the state's label.
     * @return The label font.
     */
    protected static Font labelFont()
    {
        return Theme.ui(Font.BOLD, 12);
    }

    /**
     * Compute the bounding box of this state's label in viewplane space.
     *
     * The label is placed inside the state when it is short enough to fit, and immediately below it
     * otherwise.
     *
     * @param g The graphics object, used to measure the label's dimensions.
     * @return The bounding box of the label.
     */
    protected Rectangle2D labelBounds(Graphics g)
    {
        FontMetrics metrics = g.getFontMetrics(labelFont());
        int width = metrics.stringWidth(m_label);
        int height = metrics.getHeight();

        double cx = m_windowX + STATE_RENDERING_WIDTH / 2.0;
        double cy = width <= STATE_RENDERING_WIDTH - 7
                  ? m_windowY + STATE_RENDERING_WIDTH / 2.0
                  : m_windowY + STATE_RENDERING_WIDTH + TEXT_DISTANCE - metrics.getAscent() / 2.0;

        return new Rectangle2D.Double(cx - width / 2.0, cy - height / 2.0, width, height);
    }

    /**
     * Render the state to a graphics object.
     * @param g The graphics object to render onto.
     * @param selectedStates The set of states selected by the user.
     */
    public void paint(Graphics g, Collection<? extends State> selectedStates)
    {
        Graphics2D g2d = Theme.prepare(g);
        Theme.Palette p = Theme.palette();
        boolean selected = selectedStates.contains(this);

        Ellipse2D.Float stateCircle = new Ellipse2D.Float(
                m_windowX, m_windowY, STATE_RENDERING_WIDTH, STATE_RENDERING_WIDTH);

        // If start, draw a marker entering the state from the left. Drawn first so the state body
        // covers where the marker meets the circle.
        if (isStartState())
        {
            float midY = m_windowY + STATE_RENDERING_WIDTH / 2f;
            float tipX = m_windowX - 3;
            g2d.setColor(p.stateStart);
            g2d.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2d.draw(new Line2D.Float(m_windowX - STATE_RENDERING_WIDTH * 0.6f, midY, tipX - 4, midY));

            Path2D.Float head = new Path2D.Float();
            head.moveTo(tipX, midY);
            head.lineTo(tipX - 6, midY - 4.2f);
            head.lineTo(tipX - 6, midY + 4.2f);
            head.closePath();
            g2d.fill(head);
        }

        // A soft shadow lifts the state off the canvas and separates overlapping nodes.
        Theme.shadow(g2d, stateCircle, 3);

        g2d.setPaint(getPaint());
        g2d.fill(stateCircle);

        g2d.setColor(selected? p.stateSelected : p.stateStroke);
        g2d.setStroke(new BasicStroke(selected? 2.5f : 2f));
        g2d.draw(stateCircle);

        // If final, draw a concentric rim on the interior, the standard notation for an
        // accepting state.
        if (isFinalState())
        {
            g2d.setStroke(new BasicStroke(1.6f));
            g2d.draw(new Ellipse2D.Float(m_windowX + 4, m_windowY + 4,
                        STATE_RENDERING_WIDTH - 8, STATE_RENDERING_WIDTH - 8));
        }

        // Draw the state name, inside the state where it fits.
        Rectangle2D bounds = labelBounds(g);
        FontMetrics metrics = g.getFontMetrics(labelFont());
        g2d.setFont(labelFont());
        g2d.setColor(p.stateLabel);
        g2d.drawString(m_label, (float)bounds.getX(), (float)(bounds.getY() + metrics.getAscent()));
    }

    /**
     * Determine if this state contains the point (x, y)
     * @param x The X ordinate.
     * @param y The Y ordinate.
     * @return true if this tate contains the specified point, false otherwise.
     */
    public boolean containsPoint(int x, int y)
    {
        return new Ellipse2D.Float(m_windowX, m_windowY, STATE_RENDERING_WIDTH, STATE_RENDERING_WIDTH)
            .contains(x, y);
    }

    /**
     * Get the outgoing transitions of this state.
     * @return An array list of all transitions which leave this state.
     */
    public abstract Collection<TRANSITION> getTransitions();

    /**
     * Adds an outgoing transition from this state. It is suggested to use the Machine object's
     * methods to modify the machine, instead of calling this directly.
     * @param tr The transition to add to the state.
     */
    public abstract void addTransition(TRANSITION tr);

    /**
     * Removes an outgoing transition from this state. It is suggested to use the Machine object's
     * methods to modify the machine, instead of calling this directly.
     * @param tr The transition to remove from the state.
     */
    public abstract void removeTransition(TRANSITION tr);

    /**
     * Removes all outgoing transition from this state. Does not update the machine itself, so
     * generally this is only useful to remove any transitions before adding a copy of a state to
     * the machine.
     */
    public abstract void removeAllTransitions();

    /**
     * The label for this state.
     */
    protected String m_label;

    /**
     * Whether or not this state is a start state.
     */
    protected boolean m_startState;

    /**
     * Whether or not this state is a final state.
     */
    protected boolean m_finalState;

    /**
     * The X ordinate of this state, representing the upper-left, relative to the window.
     */
    protected int m_windowX;

    /**
     * The Y ordinate of this state, representing the upper-left, relative to the window.
     */
    protected int m_windowY;
}
