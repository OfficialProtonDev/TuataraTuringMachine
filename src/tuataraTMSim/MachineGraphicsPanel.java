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
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.util.*;
import javax.swing.*;
import tuataraTMSim.commands.*;
import tuataraTMSim.exceptions.*;
import tuataraTMSim.machine.*;

/**
 * The canvas for drawing a machine state diagram.
 */
public abstract class MachineGraphicsPanel<
    PREACTION extends PreAction,
    TRANSITION extends Transition<PREACTION, STATE, MACHINE, SIMULATOR>,
    STATE extends State<PREACTION, TRANSITION, MACHINE, SIMULATOR>,
    MACHINE extends Machine<PREACTION, TRANSITION, STATE, SIMULATOR>,
    SIMULATOR extends Simulator<PREACTION, TRANSITION, STATE, MACHINE>> extends JPanel
{
    /**
     * Dashed stroke used for displaying the marquee selection.
     */
    protected final BasicStroke DASHED_STROKE =
        new BasicStroke( 1.5f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10.0f, new float[] { 4.0f, 3.0f }, 0.0f);

    /**
     * Width of the pre-rendered grid patch, in grid cells. Larger patches mean fewer blits per
     * repaint at the cost of a little more memory.
     */
    protected static final int PATCH_CELLS = 12;

    /**
     * Smallest permitted zoom factor.
     */
    public static final double ZOOM_MIN = 0.25;

    /**
     * Largest permitted zoom factor.
     */
    public static final double ZOOM_MAX = 4.0;

    /**
     * Ratio between adjacent zoom steps.
     */
    public static final double ZOOM_STEP = 1.2;

    /**
     * Distance in pixels between dots on the canvas grid.
     */
    protected static final int GRID_SPACING = 24;

    /**
     * Radius in pixels, beyond the edge of a state, of the halo drawn around the current state.
     */
    protected static final int CURRENT_STATE_HALO = 13;

    /**
     * Trigger indicating an event should never be enabled.
     */
    protected static final int TRIGGER_NONE = 0;

    /**
     * Trigger indicating an event should be enabled when the context menu is on the panel, and the
     * underlying machine is empty. This guarantees that m_contextState and m_contextTransition are
     * both null.
     */
    protected static final int TRIGGER_EMPTY = 1 << 0;

    /**
     * Trigger indicating an event should be enabled when the context menu is on the panel, and the
     * underlying machine is nonempty. This does not give any guarantee about the values of
     * m_contextState or m_contextTransition.
     */
    protected static final int TRIGGER_NONEMPTY = 1 << 1;

    /**
     * Trigger indicating an event should be enabled when the context menu is on the panel. This
     * does not give any guarantee about the values of m_contextState or m_contextTransition.
     */
    protected static final int TRIGGER_PANEL = TRIGGER_EMPTY | TRIGGER_NONEMPTY;

    /**
     * Trigger indicating an event should be enabled when the context menu is on a state. Guarantees
     * that m_contextState is non-null.
     */
    protected static final int TRIGGER_STATE = 1 << 2;

    /**
     * Trigger indicating an event should be enabled when the context menu is on a transition.
     * Guarantees that m_contextTransition is non-null.
     */
    protected static final int TRIGGER_TRANSITION = 1 << 3;

    /**
     * Trigger indicating an event should always be enabled. This does not give any guarantee about
     * the values of m_contextState or m_contextTransition.
     */
    protected static final int TRIGGER_ALL = (1 << 4) - 1;

    /**
     * Creates a new instance of MachineGraphicsPanel.
     * @param sim The simulator, containing the machine to render, and tape.
     * @param file The file the machine is associated with.
     */
    public MachineGraphicsPanel(SIMULATOR sim, File file)
    {
        // Setup
        m_sim  = sim;
        m_file = file;
        m_labelsUsed = m_sim.getMachine().getLabelHashset();

        // Create our context menu
        m_contextMenu = new JPopupMenu();
        m_contextMenu.add(m_addStateAction);
        m_contextMenu.add(m_renameStateAction);
        m_contextMenu.add(m_toggleStartAction);
        m_contextMenu.add(m_toggleFinalAction);
        m_contextMenu.add(m_deleteStateAction);
        m_contextMenu.addSeparator();
        m_contextMenu.add(m_resetLabelsAction);
        m_contextMenu.add(m_validateAction);
   }

    /**
     * Determine if this panel is opaque; allows for optomization by Swing.
     * @return true in all cases.
     */
    public final boolean isOpaque()
    {
        return true;
    }

    /**
     * Get the internal frame for this panel.
     * @return The internal frame for this panel.
     */
    public MachineInternalFrame getFrame()
    {
        return m_iFrame;
    }

    /**
     * Set the internal frame for this panel.
     * @param iFrame The new internal frame.
     */
    public void setFrame(MachineInternalFrame iFrame)
    {
        m_iFrame = iFrame;
        updateTitle();
    }

    /**
     * Get the simulator associated with this panel.
     * @return The simulator for this panel.
     */
    public SIMULATOR getSimulator()
    {
        return m_sim;
    }

    /**
     * Get the file associated with the machine.
     * @return The file associated with the machine.
     */
    public File getFile()
    {
        return m_file;
    }

    /**
     * Set the file associated with the machine.
     * @param f The new file associated with the machine.
     */
    public void setFile(File f)
    {
        m_file = f;
        updateTitle();
    }

    /**
     * Get the filename associated with the machine. If getFile() is null, then this value is a
     * temporary name for the machine.
     * @return The filename associated with the machine.
     */
    public String getFilename()
    {
        if (m_file == null)
        {
            return String.format("untitled-%d", m_iFrame.getIndex()); 
        }
        else
        {
            return m_file.getName();
        }
    }       

    /**
     * Determine if the machine has been modified since its last save.
     * @return true if it has been modified since its last save, false otherwise.
     */
    public boolean isModifiedSinceSave()
    {
        return m_modifiedSinceSave;
    }

    /**
     * Set whether the machine has been modified since its last save.
     * @param isModified true if it has been modified since its last save, false otherwise.
     */
    public void setModifiedSinceSave(boolean isModified)
    {
        m_modifiedSinceSave = isModified;
        if (m_iFrame != null)
        {
            m_iFrame.updateTitle();
        }
    }

    /**
     * Set the user interface interaction mode for this panel. This determines the result of a click
     * in the panel.
     * @param currentMode The new GUI mode.
     */
    public void setUIMode(GUI_Mode currentMode)
    {
        m_currentMode = currentMode;
        setCursor(Cursor.getPredefinedCursor(cursorForMode(currentMode)));
    }

    /**
     * Map an interaction mode to the mouse cursor which best signals what a click will do. Showing
     * the active tool at the pointer means the user does not have to look back at the toolbar to
     * remember which mode they are in.
     * @param mode The interaction mode.
     * @return One of the Cursor type constants.
     */
    protected static int cursorForMode(GUI_Mode mode)
    {
        if (mode == null)
        {
            return Cursor.DEFAULT_CURSOR;
        }
        switch (mode)
        {
            case ADDNODES:           return Cursor.CROSSHAIR_CURSOR;
            case ADDTRANSITIONS:     return Cursor.HAND_CURSOR;
            case SELECTION:          return Cursor.DEFAULT_CURSOR;
            case ERASER:             return Cursor.HAND_CURSOR;
            case CHOOSESTART:        return Cursor.HAND_CURSOR;
            case CHOOSEFINAL:        return Cursor.HAND_CURSOR;
            case CHOOSECURRENTSTATE: return Cursor.HAND_CURSOR;
            default:                 return Cursor.DEFAULT_CURSOR;
        }
    }

    /**
     * Get the set of states selected by the user.
     * @return The set of states selected by the user.
     */
    public HashSet<STATE> getSelectedStates()
    {
        return m_selectedStates;
    }

    /**
     * Set which states are selected by the user.
     * @param states The states selected by the user.
     */
    public void setSelectedStates(HashSet<STATE> states)
    {
        m_selectedStates = states;
    }

    /**
     * Get the set of transitions selected by the user.
     * @return The set of transitions selected by the user.
     */
    public HashSet<TRANSITION> getSelectedTransitions()
    {
        return m_selectedTransitions;
    }

    /**
     * Set which transtions are selected by the user.
     * @param transitions The transitions selected by the user.
     */
    public void setSelectedTransitions(HashSet<TRANSITION> transitions)
    {
        m_selectedTransitions = transitions;
    }

    /**
     * Get the current transition selected by the user for modification of its input/output symbols,
     * or null if there is no selected transition one.
     * @return The current transition selected by the user for modification.
     */
    public TRANSITION getSelectedTransition()
    {
        return m_selectedTransition;
    }

    /** 
     * Find the first unused standard state label in the machine. Standard labels are 'q' followed
     * by a non-negative integer.
     * NOTE: Potentially should be abstract.
     * @return The first unused standard state label.
     */
    public String getFirstFreeName()
    {
        int current = 0;
        while (m_labelsUsed.contains("q" + current))
        {
            current++;
        }
        return "q" + current;
    }

    /**
     * Get the alphabet for the machine associated with this panel.
     * @return The alphabet for the machine.
     */
    public Alphabet getAlphabet()
    {
        return getSimulator().getMachine().getAlphabet();
    }

    /**
     * Determine if the keyboard is enabled.
     * @return true if the keyboard is enabled, false otherwise.
     */
    public boolean getKeyboardEnabled()
    {
        return m_keyboardEnabled;
    }

    /**
     * Determine if editing of the machine is enabled.
     * @return true if editing is enabled, false otherwise.
     */
    public boolean isEditingEnabled()
    {
        return m_editingEnabled;
    }

    /** 
     * Set if editing of the machine is enabled.
     * @param enabled true if editing is enabled, false otherwise.
     */
    public void setEditingEnabled(boolean enabled)
    {
        m_editingEnabled = enabled;
        m_keyboardEnabled = enabled;
    }

    /**
     * Get the location of the last place we pasted to.
     * @return The location where the last pasted item was placed.
     */
    public Point2D getLastPastedLocation()
    {
        return m_lastPastedLocation;
    }

    /** 
     * Set the location where the last pasted item was placed. This is to prevent pasting multiple
     * items in the same place.
     * @param location The location where the last pasted item was placed.
     */
    public void setLastPastedLocation(Point2D location)
    {
        m_lastPastedLocation = location;
        m_numPastesToSameLocation = 1;
    }

    /**
     * Get the count of the number of times we've pasted to the same location on the canvas.
     * @return The number of times an item has been pasted to the same location on the canvas.
     */
    public int getNumPastesToSameLocation()
    {
        return m_numPastesToSameLocation;
    }

    /**
     * Increase the count of the number of times we've pasted to the same location on the canvas.
     */
    public void incrementNumPastesToSameLocation()
    {
        m_numPastesToSameLocation++;
    }

    /**
     * Render to a graphics object.
     * @param g The graphics object to render onto.
     */
    protected void paintComponent(Graphics g)
    {
        Graphics2D g2d = Theme.prepare(g);
        Theme.Palette p = Theme.palette();

        // Background and grid are laid down in device pixels, before the zoom is applied.
        paintBackdrop(g2d);

        // Everything below is expressed in unscaled diagram coordinates; the zoom is applied once,
        // here, and mouse input is mapped back through it in toDiagram.
        g2d.scale(m_zoom, m_zoom);
        g2d.setFont(Global.FONT_MONOSPACE);

        STATE currentState = getSimulator().getCurrentState();
        if (currentState != null)
        {
            // A soft halo behind the state the machine is currently in. Drawn as concentric
            // translucent rings rather than as a hard disc, so the state itself stays readable.
            double cx = currentState.getX() + STATE.STATE_RENDERING_WIDTH / 2.0;
            double cy = currentState.getY() + STATE.STATE_RENDERING_WIDTH / 2.0;
            for (int i = CURRENT_STATE_HALO; i > 0; i -= 3)
            {
                double r = STATE.STATE_RENDERING_WIDTH / 2.0 + i;
                g2d.setColor(Theme.alpha(p.stateCurrentGlow, 22));
                g2d.fill(new Ellipse2D.Double(cx - r, cy - r, r * 2, r * 2));
            }
            double r = STATE.STATE_RENDERING_WIDTH / 2.0 + 4;
            g2d.setColor(p.stateCurrent);
            g2d.setStroke(new BasicStroke(2.5f));
            g2d.draw(new Ellipse2D.Double(cx - r, cy - r, r * 2, r * 2));
        }

        getSimulator().getMachine().paint(g, m_selectedStates, m_selectedTransitions, getSimulator());
        if (m_currentMode == GUI_Mode.ADDTRANSITIONS && m_mousePressedState != null)
        {
            if (!(m_drawPosX == Integer.MIN_VALUE) || !(m_drawPosY == Integer.MIN_VALUE))
            {
                g2d.setColor(p.accent);
                g2d.setStroke(DASHED_STROKE);
                g2d.draw(new Line2D.Float(m_mousePressedState.getX() + STATE.STATE_RENDERING_WIDTH/2,m_mousePressedState.getY()+ STATE.STATE_RENDERING_WIDTH/2, m_drawPosX, m_drawPosY));
            }
        }

        if (m_selectedSymbolBoundingBox != null)
        {
            Stroke current = g2d.getStroke();
            g2d.setStroke(DASHED_STROKE);
            g2d.setColor(p.accent);
            g2d.draw(m_selectedSymbolBoundingBox);
            g2d.setStroke(current);
        }

        // A marquee selection is taking place
        if (m_selectionBox != null)
        {
            int topLeftX = Math.min(m_selectionBox.x, m_selectionBox.x + m_selectionBox.width),
                topLeftY = Math.min(m_selectionBox.y, m_selectionBox.y + m_selectionBox.height),
                width    = Math.abs(m_selectionBox.width),
                height   = Math.abs(m_selectionBox.height);
            Stroke current = g2d.getStroke();

            Shape marquee = new Rectangle2D.Float(topLeftX, topLeftY, width, height);
            g2d.setColor(Theme.alpha(p.accent, 26));
            g2d.fill(marquee);
            g2d.setStroke(DASHED_STROKE);
            g2d.setColor(p.accent);
            g2d.draw(marquee);
            g2d.setStroke(current);
        }
    }

    /**
     * Paint the canvas background and its dot grid, which give the otherwise featureless canvas a
     * sense of scale and make it obvious that states can be dragged around. Must be called before
     * the zoom transform is applied.
     * @param g2d The graphics object to render onto.
     */
    protected void paintBackdrop(Graphics2D g2d)
    {
        // The background and its dots are drawn by blitting a pre-rendered patch across the damaged
        // region. Filling a dot at a time meant thousands of one-pixel draws per repaint of the
        // 2000x2000 canvas, most of them off-screen: cheap under an accelerated pipeline, but a
        // flood of requests for an X server while a machine runs or a state is dragged.
        //
        // This runs before the zoom is applied, in device pixels, so that the dots stay a crisp
        // single pixel instead of being resampled. Their spacing still tracks the zoom, so the grid
        // looks as it did.
        Theme.Palette p = Theme.palette();
        int spacing = Math.max(4, (int)Math.round(GRID_SPACING * m_zoom));

        if (m_gridPatch == null || m_gridSpacing != spacing
                || !p.canvas.equals(m_canvasColour) || !p.canvasGrid.equals(m_gridColour))
        {
            int side = spacing * PATCH_CELLS;
            BufferedImage patch = new BufferedImage(side, side, BufferedImage.TYPE_INT_RGB);
            Graphics2D pg = patch.createGraphics();
            pg.setColor(p.canvas);
            pg.fillRect(0, 0, side, side);
            pg.setColor(p.canvasGrid);
            for (int x = 0; x < side; x += spacing)
            {
                for (int y = 0; y < side; y += spacing)
                {
                    pg.fillRect(x, y, 1, 1);
                }
            }
            pg.dispose();

            m_gridPatch = patch;
            m_gridSpacing = spacing;
            m_canvasColour = p.canvas;
            m_gridColour = p.canvasGrid;
        }

        Rectangle clip = g2d.getClipBounds();
        if (clip == null)
        {
            clip = new Rectangle(0, 0, getWidth(), getHeight());
        }
        int side = m_gridPatch.getWidth();
        int startX = Math.floorDiv(clip.x, side) * side;
        int startY = Math.floorDiv(clip.y, side) * side;
        for (int x = startX; x < clip.x + clip.width; x += side)
        {
            for (int y = startY; y < clip.y + clip.height; y += side)
            {
                g2d.drawImage(m_gridPatch, x, y, null);
            }
        }
    }

    /** 
     * Set up the panel. Should only be called by the constructor.
     */
    protected void initialization()
    {
        setFocusable(false);

        // Ctrl and the wheel zooms about the pointer; a plain wheel belongs to the scroll pane, so
        // pass it up rather than swallowing it.
        addMouseWheelListener(new MouseWheelListener()
        {
            public void mouseWheelMoved(MouseWheelEvent e)
            {
                if (!e.isControlDown())
                {
                    Container parent = getParent();
                    if (parent != null)
                    {
                        parent.dispatchEvent(SwingUtilities.convertMouseEvent(
                                    MachineGraphicsPanel.this, e, parent));
                    }
                    return;
                }
                double factor = Math.pow(ZOOM_STEP, -e.getWheelRotation());
                setZoom(m_zoom * factor, e.getPoint());
            }
        });

        // Set up event listeners and their corresponding actions.
        addMouseListener(new MouseAdapter()
        {
            public void mouseClicked(MouseEvent e)
            {   
                // Do nothing if editing is disabled, or this is not a left-click
                if (!m_editingEnabled ||
                    e.getButton() != MouseEvent.BUTTON1)
                {
                    return;
                }

                // Deselect any selected action symbol
                m_selectedSymbolBoundingBox = null;
                m_selectedTransition = null;

                // Selecting a transition action
                if (m_currentMode != GUI_Mode.ERASER && selectCharacterByClicking(e))
                {
                    repaint();
                    return;
                }

                // Handle GUI mode events
                switch (m_currentMode)
                {
                    case ADDNODES:
                        handleAddNodesClick(e);
                        break;

                    case ADDTRANSITIONS:
                        if ((e.isControlDown() || e.isShiftDown()))
                        {
                            handleSelectionClick(e);
                        }
                        break;

                    case ERASER:
                        handleEraserClick(e);
                        break;

                    case CHOOSESTART:
                        handleChooseStartClick(e);
                        break;

                    case CHOOSEFINAL:
                        handleChooseFinalClick(e);
                        break;

                    case SELECTION:
                        handleSelectionClick(e);
                        break;

                    case CHOOSECURRENTSTATE:
                        handleChooseCurrentState(e);
                        break;
                }

                // Update modified if anything is clicked on
                MACHINE mac = getSimulator().getMachine();
                if (mac.getStateClickedOn(e.getX(), e.getY())!= null ||
                    mac.getTransitionClickedOn(e.getX(), e.getY(), measuringGraphics()) != null)
                {
                    setModifiedSinceSave(true);
                }
                repaint();

            }

            public void mousePressed(MouseEvent e)
            {
                if (!m_editingEnabled)
                {
                    return;
                }
                else if (e.getButton() == MouseEvent.BUTTON1)
                {
                    handleMousePressed(e);
                }
                else if (e.getButton() == MouseEvent.BUTTON3)
                {
                    tryShowPopup(e);
                }
                repaint();
            }

            public void mouseReleased(MouseEvent e)
            {
                if (!m_editingEnabled)
                {
                    return;
                }
                else if (e.getButton() == MouseEvent.BUTTON1)
                {
                    handleMouseReleased(e);
                }
                else if (e.getButton() == MouseEvent.BUTTON3)
                {
                    tryShowPopup(e);
                }
                repaint();
            }

            private void tryShowPopup(MouseEvent e)
            {
                int event = TRIGGER_NONE;
                if (e.isPopupTrigger())
                {
                    m_contextLocX = e.getX();
                    m_contextLocY = e.getY();

                    // Does the machine contain anything
                    if (getSimulator().getMachine().getStates().size() == 0)
                    {
                        event |= TRIGGER_EMPTY;
                    }
                    else
                    {
                        event |= TRIGGER_NONEMPTY;
                    }

                    // Try to grab a state first
                    if ((m_contextState = getSimulator().getMachine().getStateClickedOn(e.getX(), e.getY())) != null)
                    {
                        event |= TRIGGER_STATE;
                    }
                    // Otherwise try to grab a transition
                    else if ((m_contextTransition = getSimulator().getMachine().getTransitionClickedOn(
                                    e.getX(), e.getY(), measuringGraphics())) != null)
                    {
                        event |= TRIGGER_TRANSITION;
                    }

                    // Enable events based off of their triggers
                    for (Component c : m_contextMenu.getComponents())
                    {
                        if (!(c instanceof JMenuItem))
                        {
                            // Not what we're looking for
                            continue;
                        }
                        JMenuItem item = (JMenuItem) c;
                        if (!(item.getAction() instanceof TriggerAction))
                        {
                            // Ditto
                            continue;
                        }
                        // Enable/disable the action based off of the event
                        ((TriggerAction) item.getAction()).triggerEvent(event);
                    }

                    // The context location is kept in diagram coordinates, since that is what
                    // placing a new state from this menu needs. Showing the menu, on the other
                    // hand, positions it within the component, so it has to be mapped back.
                    m_contextMenu.show(e.getComponent(), toView(m_contextLocX), toView(m_contextLocY));
                }
            }
        });

        addMouseMotionListener(new MouseMotionAdapter()
        {
            public void mouseDragged(MouseEvent e)
            {
                // Deselect any selected action symbol
                m_selectedSymbolBoundingBox = null;
                m_selectedTransition = null;

                boolean repaintNeeded = false;
                if (m_currentMode != GUI_Mode.ADDTRANSITIONS)
                {
                    if (m_mousePressedState != null)
                    {
                        handleStateDrag(e);
                        repaintNeeded = true;
                    }
                    else if (m_currentMode == GUI_Mode.SELECTION && m_selectionBox != null)
                    {
                        m_selectionBox.width  = e.getX() - m_selectionBox.x;
                        m_selectionBox.height = e.getY() - m_selectionBox.y;
                        repaintNeeded = true;
                    }
                }
                // Add transitions mode
                else
                {
                    m_drawPosX = e.getX();
                    m_drawPosY = e.getY();
                    repaintNeeded = true;
                }

                if (m_mousePressedTransition != null)
                {
                    handleTransitionDrag(e);
                    repaintNeeded = true;
                }

                if (repaintNeeded)
                {
                    repaint();
                }
            }
        });
    }

    /**
     * Handle when a mouse click occurs over the action of a transition, by selecting the
     * appropriate symbol for editing.
     * @param e The generating event.
     * @return true if the action was clicked, false otherwise.
     */
    protected boolean selectCharacterByClicking(MouseEvent e)
    {
        TRANSITION transitionClicked = getSimulator().getMachine().getTransitionClickedOn(e.getX(), e.getY(), measuringGraphics());
        if (transitionClicked != null)
        {
            Rectangle2D s1 = transitionClicked.getInputSymbolBoundingBox(measuringGraphics());
            Rectangle2D s2 = transitionClicked.getOutputSymbolBoundingBox(measuringGraphics());
            if (s1.contains(e.getX(), e.getY()))
            {
                m_selectedSymbolBoundingBox = s1;
                m_inputSymbolSelected = true;
            }
            else if (s2.contains(e.getX(), e.getY()))
            {
                m_selectedSymbolBoundingBox = s2;
                m_inputSymbolSelected = false;
            }
            m_selectedTransition = transitionClicked;
            return true;
        }
        else
        {
            m_selectedSymbolBoundingBox = null;
            m_selectedTransition = null;
            return false;
        }
    }

    /**
     * Delete a state from the machine.
     * @param s The state to delete.
     */
    public void deleteState(STATE s)
    {
        doCommand(new DeleteStateCommand(this, s));
    }

    /**
     * Delete a transition from the machine.
     * @param t The transition to delete.
     */
    public void deleteTransition(TRANSITION t)
    {
        doCommand(new DeleteTransitionCommand(this, t));
    }

    /** 
     * Handle when a mouse button is pressed. Determines selected states or transitions.
     * @param e The generating event.
     */
    protected void handleMousePressed(MouseEvent e)
    {
        if ((e.isControlDown() || e.isShiftDown()))
        {
            // We don't want to start creating a new transition in this case
            m_mousePressedState = null;
        }
        else
        {
            m_mousePressedState = getSimulator().getMachine().getStateClickedOn(e.getX(), e.getY());
        }
        if (m_mousePressedState != null) // Mouse press on a state
        {
            setModifiedSinceSave(true);
            m_moveStateClickOffsetX = m_mousePressedState.getX() - e.getX();
            m_moveStateClickOffsetY = m_mousePressedState.getY() - e.getY();
            m_moveStateLastLocationX = m_mousePressedState.getX();
            m_moveStateLastLocationY = m_mousePressedState.getY();
            m_moveStateStartLocationX = m_mousePressedState.getX();
            m_moveStateStartLocationY = m_mousePressedState.getY();
            m_transitionsToMoveState = getSimulator().getMachine().getTransitionsTo(m_mousePressedState);
            precomputeSelectedTransitionsToDrag();
            return;
        }
        else
        {
            m_mousePressedTransition = getSimulator().getMachine().getTransitionClickedOn(e.getX(), e.getY(), measuringGraphics());
            if (m_mousePressedTransition != null) // Mouse press on a transition
            {
                setModifiedSinceSave(true);
                m_transitionMidPointBeforeMove = m_mousePressedTransition.getMidpoint();
                m_moveTransitionClickOffsetX = (int)m_transitionMidPointBeforeMove.getX() - e.getX();
                m_moveTransitionClickOffsetY = (int)m_transitionMidPointBeforeMove.getY() - e.getY();

                return;
            }
        }
        // No state or transition clicked on
        if (m_currentMode == GUI_Mode.SELECTION)
        {
            // Start building a selection bounding box
            m_selectionBox = new Rectangle(e.getX(), e.getY(), 0, 0);
            m_selectionConcatenateMode = (e.isControlDown() || e.isShiftDown());

        }
    }

    /**
     * Handle when a mouse drag occurs while in selection mode. Moves a transition relative to mouse
     * movement.
     * @param e The generating event.
     */
    protected void handleTransitionDrag(MouseEvent e)
    {
        // Update control point location
        // TODO: enforce area bounds?
        m_movedTransition = true;

        // Find the midpoint by correcting for the offset of where the user clicked
        double correctedMidpointX = e.getX() + m_moveTransitionClickOffsetX; 
        double correctedMidpointY = e.getY() + m_moveTransitionClickOffsetY;

        Point2D newCP = Spline.getControlPointFromMidPoint(
                new Point2D.Double(correctedMidpointX, correctedMidpointY),
                m_mousePressedTransition.getFromState(), m_mousePressedTransition.getToState());

        m_mousePressedTransition.setControlPoint((int)newCP.getX(), (int)newCP.getY());

        // Move the bounding box for the selected symbol if it is on this transition action
        if (m_mousePressedTransition == m_selectedTransition)
        {
            updateSelectedSymbolBoundingBox();
        }
    }

    /**
     * Handle when a mouse drag occurs while in selection mode. Moves a state relative to mouse
     * movement.
     * @param e The generating event.
     */
    protected void handleStateDrag(MouseEvent e)
    {
        int newX = e.getX() + m_moveStateClickOffsetX;
        int newY = e.getY() + m_moveStateClickOffsetY;

        // Check that this is within panel bounds.
        // This is complicated in the case where multiple items are selected
        Dimension boundaries = getSize();
        int minY = 0;
        int minX = 0;
        int maxX = (int)boundaries.getWidth() - STATE.STATE_RENDERING_WIDTH;
        int maxY = (int)boundaries.getHeight() - STATE.STATE_RENDERING_WIDTH;

        if (m_selectedStates.contains(m_mousePressedState)) // Clicked on a selected state
        {
            int rightMostX = m_mousePressedState.getX();
            int leftMostX = m_mousePressedState.getX();
            int bottomMostY = m_mousePressedState.getY();
            int topMostY = m_mousePressedState.getY();
            for (STATE s : m_selectedStates)
            {
                if (s.getX() > rightMostX)
                {
                    rightMostX = s.getX();
                }
                if (s.getY() > bottomMostY)
                {
                    bottomMostY = s.getY();
                }
                if (s.getX() < leftMostX)
                {
                    leftMostX = s.getX();
                }
                if (s.getY() < topMostY)
                {
                    topMostY = s.getY();
                }
            }
            maxX -= rightMostX - m_mousePressedState.getX();
            maxY -= bottomMostY - m_mousePressedState.getY();
            minX += m_mousePressedState.getX() - leftMostX;
            minY += m_mousePressedState.getY() - topMostY;
        }
        newX = Math.min(newX, maxX);
        newY = Math.min(newY, maxY);
        newX = Math.max(minX, newX); // Constrain to accessable bounds of view plane
        newY = Math.max(minY, newY);

        int translateX = newX - m_moveStateLastLocationX;
        int translateY = newY - m_moveStateLastLocationY;

        if (translateX == 0 && translateY == 0)
        {
            return;
        }
        m_movedState = true;
        if (!m_selectedStates.contains(m_mousePressedState))
        {
            // Just move the one state
            updateTransitionLocations(m_mousePressedState,translateX, translateY,
                    m_transitionsToMoveState, m_mousePressedState.getTransitions());
            m_mousePressedState.setPosition(m_mousePressedState.getX() + translateX,
                    m_mousePressedState.getY() + translateY);
        }
        else
        {
            // Move all selected states
            pullSelectedTransitionsWithState(translateX, translateY);            
            for (STATE s : m_selectedStates)
            {
                s.setPosition(s.getX() + translateX,
                        s.getY() + translateY);
            }
        }
        m_moveStateLastLocationX = m_mousePressedState.getX();
        m_moveStateLastLocationY = m_mousePressedState.getY();
        updateSelectedSymbolBoundingBox();
    }

    // ------------------------------------------------------------------ Zoom and pan

    /**
     * Get the current zoom factor, where 1.0 is actual size.
     * @return The zoom factor.
     */
    public double getZoom()
    {
        return m_zoom;
    }

    /**
     * Set the zoom factor, keeping a given point of the diagram pinned under the same place on
     * screen so that zooming does not throw away the reader's place.
     * @param zoom The requested factor, clamped to the permitted range.
     * @param anchor The point in view coordinates to hold steady, or null to hold the centre of the
     *               visible area.
     */
    public void setZoom(double zoom, Point anchor)
    {
        double target = Math.max(ZOOM_MIN, Math.min(ZOOM_MAX, zoom));
        if (Math.abs(target - m_zoom) < 1e-9)
        {
            return;
        }

        JViewport vp = (JViewport)SwingUtilities.getAncestorOfClass(JViewport.class, this);
        Point view = vp == null? null : vp.getViewPosition();
        if (anchor == null && vp != null)
        {
            anchor = new Point(view.x + vp.getWidth() / 2, view.y + vp.getHeight() / 2);
        }

        // The diagram coordinate sitting under the anchor, which must not move.
        double worldX = anchor == null? 0 : anchor.x / m_zoom;
        double worldY = anchor == null? 0 : anchor.y / m_zoom;

        m_zoom = target;
        revalidate();

        if (vp != null && anchor != null)
        {
            int offsetX = anchor.x - view.x;
            int offsetY = anchor.y - view.y;
            Dimension size = getPreferredSize();
            int x = (int)Math.round(worldX * m_zoom) - offsetX;
            int y = (int)Math.round(worldY * m_zoom) - offsetY;
            x = Math.max(0, Math.min(x, size.width - vp.getWidth()));
            y = Math.max(0, Math.min(y, size.height - vp.getHeight()));
            // The viewport still holds the old geometry until layout runs, so move it afterwards.
            final Point destination = new Point(Math.max(0, x), Math.max(0, y));
            final JViewport port = vp;
            SwingUtilities.invokeLater(new Runnable()
            {
                public void run()
                {
                    port.setViewPosition(destination);
                }
            });
        }
        repaint();
        MainWindow inst = MainWindow.getInstance();
        if (inst != null)
        {
            inst.refreshStatus();
        }
    }

    /**
     * Magnify the diagram by one step, about the centre of the visible area.
     */
    public void zoomIn()
    {
        setZoom(m_zoom * ZOOM_STEP, null);
    }

    /**
     * Shrink the diagram by one step, about the centre of the visible area.
     */
    public void zoomOut()
    {
        setZoom(m_zoom / ZOOM_STEP, null);
    }

    /**
     * Return the diagram to actual size.
     */
    public void resetZoom()
    {
        setZoom(1.0, null);
    }

    /**
     * Scale the panel's footprint with the zoom, so that the enclosing scroll pane offers the right
     * amount of scrolling for the magnified diagram.
     * @return The preferred size of this panel.
     */
    public Dimension getPreferredSize()
    {
        Dimension d = super.getPreferredSize();
        return new Dimension((int)Math.ceil(d.width * m_zoom), (int)Math.ceil(d.height * m_zoom));
    }

    /**
     * A graphics context used solely to measure text while hit testing. Component.getGraphics
     * allocates a fresh context per call, and these were never disposed; under X11 that leaks a
     * server-side resource on every mouse movement over the canvas. Hit testing needs nothing from
     * the screen -- each caller passes the font it cares about and reads only its metrics -- so one
     * scratch context is kept for the life of the panel instead. Its rendering hints are left at
     * their defaults, matching what Component.getGraphics returned, so measurements are unchanged.
     * @return A graphics context for measurement. It must not be disposed.
     */
    protected Graphics measuringGraphics()
    {
        if (m_measuringGraphics == null)
        {
            m_measuringGraphics =
                new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB).createGraphics();
        }
        return m_measuringGraphics;
    }

    /**
     * Map a diagram ordinate back into the view, for the few places which position a Swing
     * component rather than draw on the canvas.
     * @param diagram An ordinate in diagram coordinates.
     * @return The corresponding ordinate in view coordinates.
     */
    protected int toView(int diagram)
    {
        return (int)Math.round(diagram * m_zoom);
    }

    /**
     * Rewrite a mouse event's coordinates from the view into the diagram, so that everything
     * downstream -- hit testing, dragging, marquee selection -- keeps working in unscaled diagram
     * coordinates and needs no knowledge of the zoom.
     * @param e The event to rewrite in place.
     * @return The same event.
     */
    private MouseEvent toDiagram(MouseEvent e)
    {
        if (m_zoom != 1.0)
        {
            e.translatePoint((int)Math.round(e.getX() / m_zoom) - e.getX(),
                             (int)Math.round(e.getY() / m_zoom) - e.getY());
        }
        return e;
    }

    /**
     * Pan the view by a drag of the middle mouse button, which is otherwise unused by the editor.
     * @param e The generating event.
     * @return true if the event was used for panning and should not be handled further.
     */
    private boolean handlePan(MouseEvent e)
    {
        if (e.getID() == MouseEvent.MOUSE_PRESSED && e.getButton() == MouseEvent.BUTTON2)
        {
            m_panFrom = e.getPoint();
            setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));
            return true;
        }
        if (e.getID() == MouseEvent.MOUSE_RELEASED && m_panFrom != null)
        {
            m_panFrom = null;
            setCursor(Cursor.getDefaultCursor());
            return true;
        }
        if (e.getID() == MouseEvent.MOUSE_DRAGGED && m_panFrom != null)
        {
            JViewport vp = (JViewport)SwingUtilities.getAncestorOfClass(JViewport.class, this);
            if (vp != null)
            {
                Point p = vp.getViewPosition();
                p.translate(m_panFrom.x - e.getX(), m_panFrom.y - e.getY());
                p.x = Math.max(0, Math.min(p.x, getPreferredSize().width - vp.getWidth()));
                p.y = Math.max(0, Math.min(p.y, getPreferredSize().height - vp.getHeight()));
                vp.setViewPosition(p);
            }
            return true;
        }
        return false;
    }

    protected void processMouseEvent(MouseEvent e)
    {
        if (handlePan(e))
        {
            return;
        }
        super.processMouseEvent(toDiagram(e));
    }

    protected void processMouseMotionEvent(MouseEvent e)
    {
        if (handlePan(e))
        {
            return;
        }
        super.processMouseMotionEvent(toDiagram(e));
    }

    /**
     * Move the selection between the two halves of the selected transition's label, so that the
     * input and output symbols can be edited in turn without reaching for the mouse.
     * @return true if the selection moved, false if no symbol is currently selected.
     */
    protected boolean toggleSelectedSymbol()
    {
        if (m_selectedSymbolBoundingBox == null || m_selectedTransition == null)
        {
            return false;
        }
        m_inputSymbolSelected = !m_inputSymbolSelected;
        updateSelectedSymbolBoundingBox();
        return true;
    }

    /**
     * Move the bounding box for the transition symbol currently selected by the user, if any, in
     * the case where a transition has been moved.
     */
    protected void updateSelectedSymbolBoundingBox()
    {
        if (m_selectedSymbolBoundingBox == null || m_selectedTransition == null)
        {
            return;
        }
        if (m_inputSymbolSelected)
        {
            m_selectedSymbolBoundingBox = m_selectedTransition.getInputSymbolBoundingBox(measuringGraphics());
        }
        else
        {
            m_selectedSymbolBoundingBox = m_selectedTransition.getOutputSymbolBoundingBox(measuringGraphics());
        }
    }

    /** 
     * Update our sets of selected states and transitions. Should be called when the user selected
     * region has been moved.
     */
    protected void updateSelectedStatesAndTransitions()
    {
        int topLeftX = Math.min(m_selectionBox.x, m_selectionBox.x + m_selectionBox.width),
            topLeftY = Math.min(m_selectionBox.y, m_selectionBox.y + m_selectionBox.height),
            width    = Math.abs(m_selectionBox.width),
            height   = Math.abs(m_selectionBox.height);
 
        HashSet<STATE> states = getSimulator().getMachine().getSelectedStates(topLeftX, topLeftY, width, height);

        if (m_selectionConcatenateMode)
        {
            m_selectedStates.addAll(states);
        }
        else
        {
            m_selectedStates = states;
        }
        m_selectedTransitions = getSimulator().getMachine().getSelectedTransitions(m_selectedStates);
    }

    /**
     * Precompute transitions that should be moved in a drag event.
     */
    protected void precomputeSelectedTransitionsToDrag()
    {
        m_inTransitionsToMove = new HashSet<TRANSITION>();
        m_outTransitionsToMove = new HashSet<TRANSITION>();
        calcMovedTransitionSets(m_inTransitionsToMove, m_outTransitionsToMove);

        m_transitionsToMoveintersection = new HashSet<TRANSITION>();
        m_transitionsToMoveintersection.addAll(m_inTransitionsToMove);
        m_transitionsToMoveintersection.retainAll(m_outTransitionsToMove);
        m_inTransitionsToMove.removeAll(m_transitionsToMoveintersection);
        m_outTransitionsToMove.removeAll(m_transitionsToMoveintersection);
    }

    /**
     * Move transitions relative to their connected states movement.
     * @param translateX The amount of pixels in the X direction the state moved.
     * @param translateY The amount of pixels in the Y direction the state moved.
     */
    protected void pullSelectedTransitionsWithState(int translateX, int translateY)
    {
        double halfOfTranslatedX = translateX / 2.0;
        double halfOfTranslatedY = translateY / 2.0;

        for (TRANSITION t : m_transitionsToMoveintersection)
        {
            if (t.getFromState() == t.getToState())
            {
                Point2D cp = t.getControlPoint();
                t.setControlPoint((int)(cp.getX() + translateX), (int)(cp.getY() + translateY));
                continue;
            }
            Point2D cp = t.getControlPoint();
            Point2D midpoint = t.getMidpoint();
            midpoint.setLocation(midpoint.getX() + halfOfTranslatedX, midpoint.getY()+ halfOfTranslatedY);

            Point2D newCP = Spline.getControlPointFromMidPoint(midpoint, t.getFromState(), t.getToState());
            t.setControlPoint((int)(newCP.getX()), (int)(newCP.getY()));  
        }

        for (TRANSITION t : m_inTransitionsToMove)
        {
            updateTransitionLocationWhenStateMoved(t, translateX, translateY);
        }
        for (TRANSITION t : m_outTransitionsToMove)
        {
            updateTransitionLocationWhenStateMoved(t, translateX, translateY);
        }
    }

    /** 
     * Update transitions associated with a moved state. Must be called before the actual state
     * location is updated.
     * @param mousePressedState The state being moved.
     * @param translateX The change in X ordinate.
     * @param translateY The change in Y ordinate.
     * @param transitionsInto The transitions coming into mousePressedState.
     * @param transitionsOut The transitions leaving mousePressedState.
     */
    public static void updateTransitionLocations(State mousePressedState, int translateX, int translateY,
            Collection<? extends Transition> transitionsInto, Collection<? extends Transition> transitionsOut)
    {
        for (Transition t : transitionsOut)
        {
            if (t.getFromState() == t.getToState())
            {
                Point2D cp = t.getControlPoint();
                t.setControlPoint((int)(cp.getX() + translateX), (int)(cp.getY() + translateY));
                continue;
            }
            updateTransitionLocationWhenStateMoved(t, translateX, translateY);
        }
        for (Transition t : transitionsInto)
        {
            if (t.getFromState() == t.getToState())
            {
                continue; // Already handled
            }
            updateTransitionLocationWhenStateMoved(t, translateX, translateY);
        }
    }

    /** 
     * When one end of a transition is moved, update the control point correctly.
     * Will not handle loops, or other cases where both ends of the transition are moved.
     * Must be called before the actual state locations are updated.
     * @param t The transition being moved.
     * @param translateX The change in X ordinate.
     * @param translateY The change in Y ordinate.
     */
    public static void updateTransitionLocationWhenStateMoved(Transition t, int translateX, int translateY)
    {
        double middleOfLineX = (t.getFromState().getX() +  t.getToState().getX()) / 2;
        double middleOfLineY = (t.getFromState().getY() +  t.getToState().getY()) / 2;

        double arcCPDisplacementVectorX = t.getControlPoint().getX() - middleOfLineX;
        double arcCPDisplacementVectorY = t.getControlPoint().getY() - middleOfLineY;
        double newFromX = t.getFromState().getX();
        double newFromY = t.getFromState().getY();
        double newToX = t.getToState().getX();
        double newToY = t.getToState().getY();

        double newMiddleOfLineX = (newFromX + newToX + translateX) / 2;
        double newMiddleOfLineY = (newFromY + newToY + translateY) / 2;
        t.setControlPoint((int)(newMiddleOfLineX + arcCPDisplacementVectorX),
                (int)(newMiddleOfLineY + arcCPDisplacementVectorY));
    }

    /**
     * Determine all transitions associated with all selected states. Results are stored in the two
     * arguments.
     * @param inTransitions The collection of transitions coming into all selected states.
     * @param outTransitions The collection of transitions leaving all selected states.
     */
    protected void calcMovedTransitionSets(HashSet<TRANSITION> inTransitions,
            HashSet<TRANSITION> outTransitions)
    {
        for (STATE s : m_selectedStates)
        {
            Collection<TRANSITION> out = s.getTransitions();
            outTransitions.addAll(out);
            Collection<TRANSITION> in = getSimulator().getMachine().getTransitionsTo(s);
            inTransitions.addAll(in);
        }
    }

    /**
     * Update the title for the frame.
     */
    protected void updateTitle()
    {
        if (m_iFrame != null)
        {
            m_iFrame.updateTitle();
        }
    }

    /**
     * Determine if the label dictionary contains a given label.
     * @param name The label to check.
     * @return true if name is a used label, false otherwise.
     */
    public boolean dictionaryContainsName(String name)
    {
        return m_labelsUsed.contains(name);
    }

    /** 
     * Adds a state name label to the dictionary of labels that have been used and cannot be used
     * again, unless deleted.
     * @param label The label to be add to the used list.
     */
    public void addLabelToDictionary(String label)
    {
        m_labelsUsed.add(label);
    }

    /**
     * Removes a state name label from the dictionary of labels that have been used and cannot be
     * used again, unless deleted.
     * @param label The label to be removed from the used list.
     */
    public void removeLabelFromDictionary(String label)
    {
        m_labelsUsed.remove(label);
    }

    /**
     * Deselect any transition action character currently selected by the user. Causes a repaint.
     */
    public void deselectSymbol()
    {
        m_selectedSymbolBoundingBox = null;
        m_selectedTransition = null;
        repaint();
    }

   /**
     * Delete all states and transitions that the user has currently selected.
     */
    public void deleteAllSelected()
    {
        HashSet<STATE> selectedStatesCopy = (HashSet<STATE>)m_selectedStates.clone();
        HashSet<TRANSITION> selectedTransitionsCopy = (HashSet<TRANSITION>)m_selectedTransitions.clone();
        doCommand(new DeleteAllSelectedCommand(this, selectedStatesCopy,
                    selectedTransitionsCopy));
        repaint();
    }

    /**
     * Copy the states and transitions currently selected by the user into a byte array via Java
     * serialization. The intent of this method is to provide a copy of a partial machine where the
     * graph structure of the partial machine is preserved, but no references to the original
     * machine are maintained. Returns null if the process fails, which should not occur.
     * @return A byte array representation of the states and transitions.
     */
    protected byte[] copySelectedToByteArray() 
    {
        try
        {
            ByteArrayOutputStream returner = new ByteArrayOutputStream();
            ObjectOutputStream oos = new ObjectOutputStream(returner);
            oos.writeObject(m_selectedStates);
            oos.writeObject(m_selectedTransitions);
            oos.flush();

            return returner.toByteArray();
        }
        catch (IOException e)
        {
            return null;
        }
    }

    // Command handing:
    /**
     * Executes a command and adds it to the undo stack. Clears the redo stack also.
     * @param command The command to execute.
     */
    public void doCommand(TMCommand command)
    {
        command.doCommand();
        m_undoStack.add(command);
        m_redoStack.clear();
        setModifiedSinceSave(true);
        MainWindow.getInstance().updateUndoActions();
        repaint();
    }

    /** 
     * Adds a command to the undo stack and clears the redo stack, but doesn't execute the command.
     * This is useful when the command has already been executed at the time of adding to the stack.
     * @param command The command to add to the stack.
     */
    public void addCommand(TMCommand command)
    {
        m_undoStack.add(command);
        m_redoStack.clear();
        MainWindow.getInstance().updateUndoActions();
        repaint();
    }

    /** 
     * Undoes a command.
     */
    public void undoCommand()
    {
        try
        {
            TMCommand c = m_undoStack.removeLast();
            c.undoCommand();
            m_redoStack.add(c);
            setModifiedSinceSave(true);
            MainWindow.getInstance().updateUndoActions();
            repaint();
        }
        catch (NoSuchElementException e) { }
    }

    /**
     * Redoes a command.
     */
    public void redoCommand()
    {
        try
        {
            TMCommand c = m_redoStack.removeLast();
            c.doCommand();
            m_undoStack.add(c);
            setModifiedSinceSave(true);
            MainWindow.getInstance().updateUndoActions();
            repaint();
        }
        catch (NoSuchElementException e) { }
    }

    /** 
     * Returns the name of the command at the top of the undo stack or null if the stack is empty.
     * @return The name of the command at the top of the undo stack.
     */
    public String undoCommandName()
    {
        if (!m_undoStack.isEmpty())
        {
            return m_undoStack.getLast().getName();
        }
        return null;
    }

    /** 
     * Returns the name of the command at the top of the redo stack or null if the stack is empty.
     * @return The name at the top of the redo stack
     */
    public String redoCommandName()
    {
        if (!m_redoStack.isEmpty())
        {
            return m_redoStack.getLast().getName();
        }
        return null;
    }

    /**
     * Handle when a mouse button is released. Creates any new transitions if a transition creating
     * drag has occured.
     * @param e The generating event.
     */
    protected void handleMouseReleased(MouseEvent e)
    {
        if (m_currentMode == GUI_Mode.ADDTRANSITIONS && m_mousePressedState != null)
        {
            STATE mouseReleasedState = m_sim.getMachine().getStateClickedOn(e.getX(), e.getY());
            if (mouseReleasedState != null)
            {
                TRANSITION newTrans = makeTransition(m_mousePressedState, mouseReleasedState);
                doCommand(new AddTransitionCommand(this, newTrans));
                repaint();
            }
        }
        else if (m_currentMode == GUI_Mode.SELECTION && m_selectionBox != null)
        {
            if (m_selectionBox.width != 0 && m_selectionBox.height != 0)
            {
                updateSelectedStatesAndTransitions();
            }
            repaint();
        }

        if (m_mousePressedState != null && m_movedState)
        {
            // Create an undo/redo command object for the move of a state/set of states/transitions.
            int translateX = m_mousePressedState.getX() - m_moveStateStartLocationX,
                translateY = m_mousePressedState.getY() - m_moveStateStartLocationY;

            if (translateX != 0 || translateY != 0)
            {
                if (m_selectedStates.contains(m_mousePressedState))
                {
                    // Moved a set of states
                    Collection<State> statesCopy = (Collection<State>)m_selectedStates.clone();
                    Collection<Transition> transitionsCopy = (Collection<Transition>)m_selectedTransitions.clone();
                    addCommand(new MoveSelectedCommand(this, statesCopy, transitionsCopy, translateX, translateY));
                }
                else
                {
                    // Moved one state
                    Collection<Transition> transitions = new ArrayList<Transition>();
                    transitions.addAll(m_transitionsToMoveState);
                    addCommand(new MoveStateCommand(this, m_mousePressedState, translateX, translateY, transitions));
                }
            }
        }

        if (m_mousePressedTransition != null && m_movedTransition)
        {
            // Create an undo/redo command object for the move of a transition
            int translateX = (int)(m_mousePressedTransition.getMidpoint().getX() - m_transitionMidPointBeforeMove.getX()),
                translateY = (int)(m_mousePressedTransition.getMidpoint().getY() - m_transitionMidPointBeforeMove.getY());
            addCommand(new MoveTransitionCommand(this, m_mousePressedTransition, translateX, translateY));
        }

        m_selectionBox = null;
        m_mousePressedState = null;
        m_mousePressedTransition = null;
        m_transitionMidPointBeforeMove = null;
        m_movedTransition = false;
        m_movedState = false;
        m_drawPosX = Integer.MIN_VALUE;
        m_drawPosY = Integer.MIN_VALUE;
    }

    /**
     * Handle when a mouse click occurs over a state, by either selecting the existing underlying
     * state, or creating a new state.
     * @param e The generating event.
     */
    protected void handleAddNodesClick(MouseEvent e)
    {
        // Clicking on another state should just select the existing state
        if (m_sim.getMachine().getStateClickedOn(e.getX(), e.getY()) != null)
        {
            // Adding states on top of states is not allowed
            handleSelectionClick(e);
            return;
        }

        int x = e.getX() - STATE.STATE_RENDERING_WIDTH / 2,
            y = e.getY() - STATE.STATE_RENDERING_WIDTH / 2;
                String label = getFirstFreeName();
                doCommand(new AddStateCommand(this, makeState(label, x, y)));
    }

    /** 
     * Handle when a mouse click occurs while in eraser mode. If the mouse click occurs over a
     * state, it is deleted, and if it is over a transition, that is deleted.
     * @param e The generating event.
     */
    protected void handleEraserClick(MouseEvent e)
    {
        STATE stateClickedOn = m_sim.getMachine().getStateClickedOn(e.getX(), e.getY());
        if (stateClickedOn != null)
        {
            deleteState(stateClickedOn);
        }
        else
        {
            TRANSITION transitionClickedOn = m_sim.getMachine()
                .getTransitionClickedOn(e.getX(), e.getY(), measuringGraphics());
            if (transitionClickedOn != null)
            {
                deleteTransition(transitionClickedOn);
            }
        }
    }

    /**
     * Handle when a mouse click occurs while in select start state mode. If the mouse click occurs
     * over a state, the start state of the machine is changed.
     * @param e The generating event.
     */
    protected void handleChooseStartClick(MouseEvent e)
    {
        STATE stateClickedOn = m_sim.getMachine().getStateClickedOn(e.getX(), e.getY());
        if (stateClickedOn != null)
        {
            doCommand(new ToggleStartStateCommand(this, stateClickedOn));
        }
    }

    /**
     * Handle when a mouse click occurs while in select final state mode. If the mouse click
     * occurs over a state, the final state of the machine is changed.
     * @param e The generating event.
     */
    protected void handleChooseFinalClick(MouseEvent e)
    {
        STATE stateClickedOn = m_sim.getMachine().getStateClickedOn(e.getX(), e.getY());
        if (stateClickedOn != null)
        {
            doCommand(new ToggleFinalStateCommand(this, stateClickedOn));
        }
    }

    /**
     * Handle when a mouse click occurs while in selection mode. If the mouse click occurs over a
     * state, the state is either added or removed from the selected state set, depending on context.
     * @param e The generating event.
     */
    protected void handleSelectionClick(MouseEvent e)
    {
        STATE stateClickedOn = m_sim.getMachine().getStateClickedOn(e.getX(), e.getY());
        if (!(e.isControlDown() || e.isShiftDown()))
        {
            m_selectedStates.clear();
            m_selectedTransitions.clear();
        }
        if (stateClickedOn != null && !m_selectedStates.remove(stateClickedOn))
        {
            m_selectedStates.add(stateClickedOn);
        }
        m_selectedTransitions = m_sim.getMachine().getSelectedTransitions(m_selectedStates);
    }

    /**
     * Handle when a mouse click occurs while in current state selection mode. If the mouse click
     * occurs over a state, the state is made to be the current state.
     * @param e The generating event.
     */
    protected void handleChooseCurrentState(MouseEvent e)
    {
        STATE stateClickedOn = m_sim.getMachine().getStateClickedOn(e.getX(), e.getY());
        if (stateClickedOn != null)
        {
            m_sim.setCurrentState(stateClickedOn);
        }
    }

    /**
     * Build an error message for the given Exception. Automatically checks the type of the
     * exception to see if it matches any existing definitions of getErrorMessage and dispatches
     * accordingly.
     * @param e The exception to build a message for.
     * @return An error message for the given exception.
     */
    public String getErrorMessage(Exception e)
    {
        if (e instanceof ComputationCompletedException)
        {
            return e.getMessage();
        }
        else if (e instanceof ComputationFailedException)
        {
            return e.getMessage();
        }
        else 
        { 
            return String.format("An unknown error occurred [%s] - %s", e.getClass().getSimpleName(), e.getMessage());
        }
    }

    /** 
     * Accept a KeyEvent detected in the main window, and use it to update any transition action
     * selected by the user.
     * @param e The generating event.
     * @return true if a transition action was selected and updated, false otherwise.
     */
    public abstract boolean handleKeyEvent(KeyEvent e);

    /**
     * Create a STATE object with the given label at the specified location.
     * @param label The state label.
     * @param x The x-ordinate of the state.
     * @param y The y-ordinate of the state.
     * @return A new STATE object.
     */
    protected abstract STATE makeState(String label, int x, int y);

    /**
     * Create a TRANSITION object with a default action, attached to the two specified states.
     * @param start The state the transition leaves.
     * @param end The state the transition arrives at.
     * @return A new TRANSITION object.
     */
    protected abstract TRANSITION makeTransition(STATE start, STATE end);

    /**
     * Get the file extension associated with this type of machine. Should return a value from a
     * symbol named MACHINE_EXT.
     * @return The file extension associated with this type of machine.
     */
    public abstract String getMachineExt();

    /**
     * Get a friendly name for the type of machine this graphics panel renders. Should return a
     * value from a symbol named MACHINE_TYPE.
     * @return A friendly name for the type of machine being stored.
     */
    public abstract String getMachineType();

    /**
     * The owning frame.
     */
    protected MachineInternalFrame m_iFrame;

    /**
     * The associated simulator
     */
    protected SIMULATOR m_sim;

    /**
     * The underlying file.
     */
    protected File m_file;

    /**
     * Whether or not the machine has been modified since the last save.
     */
    protected boolean m_modifiedSinceSave = false;

    /**
     * The current GUI mode.
     */
    protected GUI_Mode m_currentMode;

    /**
     * The right-click context menu associated with this panel.
     * Specialized panels should take this menu and add new actions after a separator.
     */
    protected JPopupMenu m_contextMenu;

    /**
     * The state currently selected by the right-click context menu.
     */
    protected STATE m_contextState;

    /**
     * The transition currently selected by the right-click context menu.
     */
    protected TRANSITION m_contextTransition;

    /**
     * The x-ordinate where the context menu was shown.
     */
    protected int m_contextLocX;

    /**
     * The y-ordinate where the context menu was shown.
     */
    protected int m_contextLocY;

    /**
     * Stack containing commands which can be undone.
     */
    protected LinkedList<TMCommand> m_undoStack = new LinkedList<TMCommand>();

    /**
     * Stack containing commands which can be redone.
     */
    protected LinkedList<TMCommand> m_redoStack = new LinkedList<TMCommand>();

    /**
     * The set of selected states.
     */
    protected HashSet<STATE> m_selectedStates = new HashSet<STATE>();

    /**
     * The set of selected transitions.
     */
    protected HashSet<TRANSITION> m_selectedTransitions = new HashSet<TRANSITION>();

    /**
     * The bounds of the selection marquee. If null, then no selection is in progress.
     * If width and height are nonzero, then a selection has been made.
     */
    protected Rectangle m_selectionBox;

    /**
     * Whether or not selected items will be concatenated to the list of selected items, or the
     * previous selected items overwritten.
     */
    protected boolean m_selectionConcatenateMode = false;

    /**
     *  The state we last pressed a mouse button on.
     */
    protected STATE m_mousePressedState = null;

    /**
     * The transition we last pressed a mouse button on
     */
    protected TRANSITION m_mousePressedTransition = null;

    /**
     * The X ordinate of the temporary transition to be drawn when the mouse is dragged.
     */
    protected int m_drawPosX = Integer.MIN_VALUE;

    /**
     * The Y ordinate of the temporary transition to be drawn when the mouse is dragged.
     */
    protected int m_drawPosY = Integer.MIN_VALUE;

    /**
     * X ordinate from the mouse click to the control point.
     */
    protected int m_moveTransitionClickOffsetX = Integer.MIN_VALUE;

    /**
     * Y ordinate from the mouse click to the control point.
     */
    protected int m_moveTransitionClickOffsetY = Integer.MIN_VALUE;

    /**
     * X ordinate from the mouse click to state location.
     */
    protected int m_moveStateClickOffsetX = Integer.MIN_VALUE;

    /**
     * Y ordinate from the mouse click to state location.
     */
    protected int m_moveStateClickOffsetY = Integer.MIN_VALUE;

    /**
     * X ordinate of the last location of the moved state.
     */
    protected int m_moveStateLastLocationX = Integer.MIN_VALUE;

    /**
     * Y ordinate of the last location of the moved state.
     */
    protected int m_moveStateLastLocationY = Integer.MIN_VALUE;

    /**
     * X ordinate of the original position of the state, before movement.
     */
    protected int m_moveStateStartLocationX = Integer.MIN_VALUE;

    /**
     * Y ordinate of the original position of the state, before movement.
     */
    protected int m_moveStateStartLocationY = Integer.MIN_VALUE;

    /**
     * Whether a state has been moved.
     */
    protected boolean m_movedState = false;

    /**
     * Cached list of transitions that finish at the state we are dragging.
     */
    protected ArrayList<TRANSITION> m_transitionsToMoveState = null;

    /**
     * Cached list of transitions coming into all selected states.
     */
    protected HashSet<TRANSITION> m_inTransitionsToMove = new HashSet<TRANSITION>();

    /**
     * Cached list of transitions leaving all selected states.
     */
    protected HashSet<TRANSITION> m_outTransitionsToMove = new HashSet<TRANSITION>();

    /**
     * Intersection of m_inTransitionsToMove and m_outTransitionsToMove.
     */
    protected HashSet<TRANSITION> m_transitionsToMoveintersection = new HashSet<TRANSITION>();

    /**
     * Midpoint of the currently selected transition, before movement.
     */
    protected Point2D m_transitionMidPointBeforeMove = null;

    /**
     * Whether a transition has been moved.
     */
    protected boolean m_movedTransition = false;

    /**
     * Whether the keyboard is enabled.
     */
    protected boolean m_keyboardEnabled = true;

    /**
     * Whether editing is enabled.
     */
    protected boolean m_editingEnabled = true;

    /**
     * Set of labels in use.
     */
    protected HashSet<String> m_labelsUsed = new HashSet<String>();

    /**
     * Scratch context used to measure text while hit testing, or null before it is first needed.
     */
    protected Graphics2D m_measuringGraphics = null;

    /**
     * Pre-rendered block of background and grid dots, blitted across the canvas, or null before it
     * is first built.
     */
    protected BufferedImage m_gridPatch = null;

    /**
     * Dot spacing in device pixels that {@link #m_gridPatch} was built for.
     */
    protected int m_gridSpacing = 0;

    /**
     * Grid colour {@link #m_gridPatch} was built for, so that it is rebuilt when the theme changes.
     */
    protected Color m_gridColour = null;

    /**
     * Canvas colour {@link #m_gridPatch} was built for.
     */
    protected Color m_canvasColour = null;

    /**
     * Current zoom factor, where 1.0 is actual size.
     */
    protected double m_zoom = 1.0;

    /**
     * Point the middle mouse button was last seen at while panning, or null when not panning.
     */
    protected Point m_panFrom = null;

    /**
     * Bounding box of the currently selected transition action.
     */
    protected Rectangle2D m_selectedSymbolBoundingBox = null;

    /**
     * The selected transition.
     */
    protected TRANSITION m_selectedTransition = null;

    /**
     * Whether a transition action has been selected.
     */
    protected boolean m_inputSymbolSelected = false;

    /**
     * Last location a value was pasted.
     */
    protected Point2D m_lastPastedLocation = null;

    /**
     * How many times an object was pasted to the last pasted location.
     */
    protected int m_numPastesToSameLocation = 0;

    /**
     * A wrapper around AbstractAction which allows actions to be 'tagged' with a mask, which
     * determines if they should be active or not when an event is triggered.
     */
    protected static abstract class TriggerAction extends AbstractAction
    {
        /**
         * Creates a new instance of TriggerAction.
         * @param text Description of the action.
         * @param trigger The events for which this action should be enabled.
         */
        public TriggerAction(String text, int trigger)
        {
            super(text);
            m_trigger = trigger;
        }

        /**
         * Enable or disable this action based off of the type of event that has occured.
         * @param event The type of event that has occured. Should be one of the constant TRIGGER
         *              values, but not TRIGGER_NONE or TRIGGER_ALL, i.e. exactly one flag set.
         */
        public void triggerEvent(int event)
        {
            setEnabled((m_trigger & event) != TRIGGER_NONE);
        }

        /**
         * The trigger for this action.
         */
        protected int m_trigger;
    }

    /**
     * Action which creates a new state at the specified location.
     */
    protected Action m_addStateAction = 
        new TriggerAction("Add State", TRIGGER_PANEL)
        {
            public void actionPerformed(ActionEvent e)
            {
                // Give a phony MouseEvent object to the addnodes handler
                handleAddNodesClick(new MouseEvent(MachineGraphicsPanel.this, 0, 0, 0, 
                                                   m_contextLocX, m_contextLocY, 0, false));
            }
        };

    /**
     * Action which prompts the user to rename the selected state.
     */
    protected Action m_renameStateAction = 
        new TriggerAction("Rename State", TRIGGER_STATE)
        {
            public void actionPerformed(ActionEvent e)
            {
                // Should be fired by m_contextMenu, which in turn is only open if we have right clicked
                // on a state. Hence we have a non-null state to work with.

                // Disable the keyboard while we prompt for user input
                m_keyboardEnabled = false;
                String result = (String) JOptionPane.showInputDialog(null, "Please enter the new state label",
                        "Rename State", JOptionPane.QUESTION_MESSAGE,
                        null, null, m_contextState.getLabel());
                m_keyboardEnabled = true;

                // User cancelled, or no change
                if (result == null || result.equals(m_contextState.getLabel()))
                {
                    // Do nothing
                }
                // Blank label not allowed
                else if (result.equals(""))
                {
                    Global.showWarningMessage("Rename State", "Empty labels are not allowed");
                }
                // Label already in use
                else if (m_labelsUsed.contains(result))
                {
                    Global.showWarningMessage("Rename State", "Label '%s' is already in use", result);
                }
                // Otherwise rename
                else
                {
                    doCommand(new RenameStateCommand(MachineGraphicsPanel.this, m_contextState, result));
                }
            }
        };

    /**
     * Action which toggles whether or not the selected state is the start state.
     */
    protected Action m_toggleStartAction = 
        new TriggerAction("Toggle Start", TRIGGER_STATE)
        {
            public void actionPerformed(ActionEvent e)
            {
                MACHINE mac = m_sim.getMachine();
                doCommand(new ToggleStartStateCommand(MachineGraphicsPanel.this, m_contextState));
            }
        };

    /**
     * Action which toggles whether or not the selected state is final.
     */
    protected Action m_toggleFinalAction = 
        new TriggerAction("Toggle Final", TRIGGER_STATE)
        {
            public void actionPerformed(ActionEvent e)
            {
                doCommand(new ToggleFinalStateCommand(MachineGraphicsPanel.this, m_contextState));
            }
        };

    /**
     * Action which deletes the selected state or transition.
     */
    protected Action m_deleteStateAction =
        new TriggerAction("Delete", TRIGGER_STATE | TRIGGER_TRANSITION)
        {
            public void actionPerformed(ActionEvent e)
            {
                // Since we fire on state|transition, we have a guarantee that exactly one is non-null
                if (m_contextState != null)
                {
                    deleteState(m_contextState);
                }
                else
                {
                    deleteTransition(m_contextTransition);
                }
            }
        };

    /**
     * Action which resets the name of all the states in the machine.
     */
    protected Action m_resetLabelsAction =
        new TriggerAction("Reset All Labels", TRIGGER_NONEMPTY)
        {
            public void actionPerformed(ActionEvent e)
            {
                doCommand(new ResetLabelCommand(MachineGraphicsPanel.this));
            }
        };

    /**
     * Action which validates the current machine.
     */
    protected Action m_validateAction =
        new TriggerAction("Validate", TRIGGER_NONEMPTY)
        {
            public void actionPerformed(ActionEvent e)
            {
                String result = m_sim.getMachine().isDeterministic();
                if (result == null)
                {
                    MainWindow.getInstance().getConsole().log("%s is deterministic", 
                                                              m_iFrame.getTitle());
                    Global.showInfoMessage("Validation", "Machine is deterministic");
                }
                else
                {
                    MainWindow.getInstance().getConsole().log("%s is nondeterministic: %s", 
                                                              m_iFrame.getTitle(), result);
                    Global.showErrorMessage("Validation", "Machine is nondeterministic: %s", result);
                }
            }
        };
}
