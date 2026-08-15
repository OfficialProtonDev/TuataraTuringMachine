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

package tuataraTMSim.agent;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import tuataraTMSim.machine.Machine;
import tuataraTMSim.machine.State;
import tuataraTMSim.machine.Transition;

/**
 * Positions for machines nobody drew.
 *
 * States go in columns by distance from the start, so reading left to right follows the machine
 * consuming input; a general force layout scatters that ordering and makes the picture harder to
 * follow rather than easier. Within a column the rows are reordered to pull connected states level
 * with one another, which is most of what stops a diagram looking tangled.
 *
 * The other half of this class matters more in practice: placing a state or two into a machine
 * somebody has already arranged, without moving anything they positioned themselves.
 */
public final class Layout
{
    /**
     * A coordinate meaning "not placed yet". Documents leave positions out, and this is how that
     * travels as far as the layout pass. No state keeps this value once a layout has run.
     */
    public static final int UNPLACED = Integer.MIN_VALUE;

    /**
     * The width of a state as drawn. Mirrors State.STATE_RENDERING_WIDTH.
     */
    private static final int STATE_W = State.STATE_RENDERING_WIDTH;

    /**
     * The canvas grid, which hand-placed states also snap to.
     */
    private static final int GRID = 24;

    /**
     * Clear space between one column of states and the next.
     */
    private static final int COLUMN_GAP = 144;

    /**
     * Vertical distance between states in the same column.
     */
    private static final int ROW_GAP = 120;

    /**
     * Space left at the top and left of the canvas.
     */
    private static final int MARGIN = 72;

    /**
     * The canvas a machine has to fit inside. Mirrors MainWindow.MACHINE_CANVAS_SIZE_X.
     */
    private static final int CANVAS = 2000;

    /**
     * How far apart two states must sit before they stop looking like one state.
     */
    private static final int SEPARATION = 72;

    /**
     * How far a curved arrow bows away from the straight line.
     */
    private static final int BOW = 44;

    /**
     * How far a self-loop reaches from the middle of its state.
     */
    private static final int LOOP_REACH = (int)(STATE_W * 1.5);

    /**
     * How close an arrow may pass to a state it has nothing to do with.
     */
    private static final int CLEARANCE = 40;

    /**
     * Not instantiable.
     */
    private Layout() { }

    /**
     * Determine whether a state has been given a position.
     * @param state The state to test.
     * @return true if it has coordinates.
     */
    public static boolean isPlaced(State state)
    {
        return state.getX() != UNPLACED && state.getY() != UNPLACED;
    }

    /* ---------------------------------------------------------------- *
     * Laying a whole machine out
     * ---------------------------------------------------------------- */

    /**
     * Position every state in a machine, discarding whatever positions they had.
     * @param machine The machine to arrange.
     * @return The number of states moved.
     */
    public static int all(Machine machine)
    {
        List<State> states = states(machine);
        if (states.isEmpty())
        {
            return 0;
        }

        List<List<State>> columns = columns(machine, states);
        orderRows(machine, columns);

        int x = MARGIN;
        for (List<State> column : columns)
        {
            int height = (column.size() - 1) * ROW_GAP;
            for (int row = 0; row < column.size(); row++)
            {
                State state = column.get(row);
                state.setPosition(snap(x), snap(MARGIN + 400 + row * ROW_GAP - height / 2));
            }
            x += COLUMN_GAP;
        }

        pullIntoCanvas(states);
        for (Object o : machine.getTransitions())
        {
            route(machine, (Transition)o);
        }
        return states.size();
    }

    /**
     * Group states into columns by how far they are from the start state. Anything the start cannot
     * reach still needs somewhere to sit, and goes in a column of its own past the end.
     * @param machine The machine being arranged.
     * @param states Every state in it.
     * @return The columns, nearest the start first.
     */
    private static List<List<State>> columns(Machine machine, List<State> states)
    {
        Map<State, Integer> depth = new HashMap<State, Integer>();
        State start = null;
        for (State s : states)
        {
            if (s.isStartState())
            {
                start = s;
                break;
            }
        }
        if (start == null)
        {
            start = states.get(0);
        }

        depth.put(start, Integer.valueOf(0));
        List<State> queue = new ArrayList<State>();
        queue.add(start);
        for (int i = 0; i < queue.size(); i++)
        {
            State current = queue.get(i);
            int next = depth.get(current).intValue() + 1;
            for (Object o : current.getTransitions())
            {
                State to = (State)((Transition)o).getToState();
                if (!depth.containsKey(to))
                {
                    depth.put(to, Integer.valueOf(next));
                    queue.add(to);
                }
            }
        }

        int orphan = 0;
        for (Integer d : depth.values())
        {
            orphan = Math.max(orphan, d.intValue() + 1);
        }
        for (State s : states)
        {
            if (!depth.containsKey(s))
            {
                depth.put(s, Integer.valueOf(orphan));
            }
        }

        Map<Integer, List<State>> grouped = new LinkedHashMap<Integer, List<State>>();
        int deepest = 0;
        for (Integer d : depth.values())
        {
            deepest = Math.max(deepest, d.intValue());
        }
        List<List<State>> columns = new ArrayList<List<State>>();
        for (int d = 0; d <= deepest; d++)
        {
            List<State> column = new ArrayList<State>();
            for (State s : states)
            {
                if (depth.get(s).intValue() == d)
                {
                    column.add(s);
                }
            }
            if (!column.isEmpty())
            {
                columns.add(column);
            }
        }
        return columns;
    }

    /**
     * Reorder the states within each column so connected states end up level with each other.
     *
     * This is the barycentre heuristic: repeatedly move each state to the average height of its
     * neighbours in the column alongside, sweeping forwards and then back. It is the standard way
     * to cut crossings in a layered drawing, and crossings are most of what makes one of these
     * look like a knot.
     * @param machine The machine being arranged.
     * @param columns The columns to reorder in place.
     */
    private static void orderRows(Machine machine, List<List<State>> columns)
    {
        Map<State, List<State>> neighbours = new HashMap<State, List<State>>();
        for (Object o : machine.getStates())
        {
            neighbours.put((State)o, new ArrayList<State>());
        }
        for (Object o : machine.getTransitions())
        {
            Transition t = (Transition)o;
            State from = (State)t.getFromState();
            State to = (State)t.getToState();
            if (from == to)
            {
                continue;
            }
            neighbours.get(from).add(to);
            neighbours.get(to).add(from);
        }

        Map<State, Integer> rank = new HashMap<State, Integer>();
        for (List<State> column : columns)
        {
            for (int i = 0; i < column.size(); i++)
            {
                rank.put(column.get(i), Integer.valueOf(i));
            }
        }

        for (int pass = 0; pass < 4; pass++)
        {
            boolean forwards = pass % 2 == 0;
            for (int c = 0; c < columns.size(); c++)
            {
                int index = forwards? c : columns.size() - 1 - c;
                List<State> column = columns.get(index);
                List<State> beside = index > 0? columns.get(index - 1)
                                  : (columns.size() > 1? columns.get(1) : column);
                Set<State> fixed = new HashSet<State>(beside);

                final Map<State, Double> score = new HashMap<State, Double>();
                for (State s : column)
                {
                    double total = 0;
                    int count = 0;
                    for (State n : neighbours.get(s))
                    {
                        if (fixed.contains(n) && rank.containsKey(n))
                        {
                            total += rank.get(n).intValue();
                            count++;
                        }
                    }
                    // No neighbour to follow: stay put, so the ordering does not churn.
                    score.put(s, Double.valueOf(count == 0? rank.get(s).intValue() : total / count));
                }
                java.util.Collections.sort(column, new java.util.Comparator<State>()
                {
                    public int compare(State a, State b)
                    {
                        return Double.compare(score.get(a).doubleValue(), score.get(b).doubleValue());
                    }
                });
                for (int i = 0; i < column.size(); i++)
                {
                    rank.put(column.get(i), Integer.valueOf(i));
                }
            }
        }
    }

    /* ---------------------------------------------------------------- *
     * Adding to a machine somebody else arranged
     * ---------------------------------------------------------------- */

    /**
     * Give a position to every state that has none, without moving anything that already has one.
     *
     * A new state goes near the states it connects to and is then pushed outwards until it clears
     * everything else. That keeps an addition legible without rearranging work the user did by
     * hand, which is the whole point: an edit should look like an edit, not like the diagram was
     * replaced.
     * @param machine The machine to place into.
     * @return The states that were placed, in the order they were placed.
     */
    public static List<State> placeNew(Machine machine)
    {
        List<State> placed = new ArrayList<State>();
        List<State> taken = new ArrayList<State>();
        List<State> pending = new ArrayList<State>();
        for (Object o : machine.getStates())
        {
            State s = (State)o;
            (isPlaced(s)? taken : pending).add(s);
        }
        if (pending.isEmpty())
        {
            return placed;
        }
        // Nothing to anchor to: this is really a fresh machine, so arrange the whole thing.
        if (taken.isEmpty())
        {
            all(machine);
            for (State s : pending)
            {
                placed.add(s);
            }
            return placed;
        }

        Map<State, List<State>> neighbours = neighbours(machine);
        for (State state : pending)
        {
            double sumX = 0;
            double sumY = 0;
            int count = 0;
            for (State n : neighbours.get(state))
            {
                if (isPlaced(n))
                {
                    sumX += n.getX();
                    sumY += n.getY();
                    count++;
                }
            }
            double anchorX;
            double anchorY;
            if (count > 0)
            {
                anchorX = sumX / count;
                anchorY = sumY / count;
            }
            else
            {
                // Unconnected: sit it clear to the right of everything, rather than in the middle
                // of a diagram it has nothing to do with.
                anchorX = right(taken) + COLUMN_GAP;
                anchorY = middleY(taken);
            }

            int[] spot = freeSpot(anchorX, anchorY, taken);
            state.setPosition(spot[0], spot[1]);
            taken.add(state);
            placed.add(state);
        }

        // Only the arrows touching a new state are (re)drawn. Every other transition keeps the
        // curve it has, including any the user bent by hand.
        Set<State> fresh = new HashSet<State>(placed);
        for (Object o : machine.getTransitions())
        {
            Transition t = (Transition)o;
            if (fresh.contains(t.getFromState()) || fresh.contains(t.getToState()))
            {
                route(machine, t);
            }
        }
        return placed;
    }

    /**
     * Find somewhere near a point with room for a state, spiralling outwards until one is found.
     * @param anchorX Where the state would ideally sit.
     * @param anchorY Where the state would ideally sit.
     * @param taken Every state already placed.
     * @return An x and y on the grid, inside the canvas, clear of everything in taken.
     */
    private static int[] freeSpot(double anchorX, double anchorY, List<State> taken)
    {
        int[] best = null;
        for (int ring = 0; ring <= 40 && best == null; ring++)
        {
            int steps = ring == 0? 1 : Math.min(8 + ring * 4, 48);
            for (int step = 0; step < steps; step++)
            {
                double angle = (step / (double)steps) * Math.PI * 2;
                double radius = ring * (SEPARATION * 0.9);
                int x = snap(clamp((int)Math.round(anchorX + Math.cos(angle) * radius)));
                int y = snap(clamp((int)Math.round(anchorY + Math.sin(angle) * radius)));
                if (clear(x, y, taken))
                {
                    best = new int[] { x, y };
                    break;
                }
            }
        }
        if (best == null)
        {
            // Every ring was occupied, which takes a remarkable diagram. Put it past the right-hand
            // edge of everything rather than on top of another state.
            best = new int[] { snap(clamp((int)right(taken) + COLUMN_GAP)), snap(clamp((int)anchorY)) };
        }
        return best;
    }

    private static boolean clear(int x, int y, List<State> taken)
    {
        for (State s : taken)
        {
            if (Math.abs(s.getX() - x) < SEPARATION && Math.abs(s.getY() - y) < SEPARATION)
            {
                return false;
            }
        }
        return true;
    }

    /* ---------------------------------------------------------------- *
     * Arrows
     * ---------------------------------------------------------------- */

    /**
     * Give a transition a sensible curve: straight where that reads cleanly, bowed where a straight
     * line would sit on top of the arrow coming back the other way or pass through an uninvolved
     * state, and parked below the state for a self-loop.
     * @param machine The machine the transition belongs to.
     * @param transition The transition to route.
     */
    public static void route(Machine machine, Transition transition)
    {
        State from = (State)transition.getFromState();
        State to = (State)transition.getToState();
        if (from == null || to == null || !isPlaced(from) || !isPlaced(to))
        {
            return;
        }

        if (from == to)
        {
            selfLoop(machine, transition, from);
            return;
        }

        double midX = (from.getX() + to.getX()) / 2.0;
        double midY = (from.getY() + to.getY()) / 2.0;
        double dx = to.getX() - from.getX();
        double dy = to.getY() - from.getY();
        double length = Math.hypot(dx, dy);
        if (length < 1)
        {
            transition.setControlPoint((int)midX, (int)midY);
            return;
        }
        // Unit normal to the line, which is the direction a bow displaces the control point along.
        double normalX = -dy / length;
        double normalY = dx / length;

        double bow = 0;

        // An arrow coming back the other way: bow both, by the same amount. Because they run in
        // opposite directions the same displacement puts them on opposite sides.
        if (hasReverse(machine, from, to))
        {
            bow = BOW;
        }

        // Several arrows between the same two states: fan them out rather than stack them.
        int index = 0;
        int siblings = 0;
        for (Object o : machine.getTransitions())
        {
            Transition t = (Transition)o;
            if (t.getFromState() == from && t.getToState() == to)
            {
                if (t == transition)
                {
                    index = siblings;
                }
                siblings++;
            }
        }
        if (siblings > 1)
        {
            bow = BOW * (index - (siblings - 1) / 2.0) * 0.9 + (bow == 0? 0 : bow);
        }

        // An arrow passing through a state it has nothing to do with: push it around.
        if (bow == 0)
        {
            double push = detour(machine, from, to, midX, midY, normalX, normalY);
            bow = push;
        }

        transition.setControlPoint((int)Math.round(midX + normalX * bow),
                                   (int)Math.round(midY + normalY * bow));
    }

    /**
     * Point a self-loop wherever there is room for it.
     *
     * Above by preference. A state whose name is too long to fit inside the circle has it drawn
     * underneath, which is where the editor's own default puts a fresh loop, and the two then sit
     * on top of each other. Below is therefore the last direction tried, not the first.
     * @param machine The machine being drawn.
     * @param transition The self-loop.
     * @param state The state it loops on.
     */
    private static void selfLoop(Machine machine, Transition transition, State state)
    {
        int centreX = state.getX() + STATE_W / 2;
        int centreY = state.getY() + STATE_W / 2;
        int reach = LOOP_REACH;

        // Above first, then the two upper diagonals, then out to the sides, and only then below.
        int[][] directions =
        {
            { 0, -1 }, { 1, -1 }, { -1, -1 }, { 1, 0 }, { -1, 0 }, { 1, 1 }, { -1, 1 }, { 0, 1 }
        };
        // How much each direction is disliked, in pixels of pretend crowding. Underneath the state
        // is where a long label goes, so it costs the most.
        double[] penalty = { 0, 12, 12, 20, 20, 70, 75, 80 };

        // Several loops on one state fan out rather than stacking, by each taking the next best
        // direction the ones before it did not.
        int rank = 0;
        for (Object o : machine.getTransitions())
        {
            Transition t = (Transition)o;
            if (t == transition)
            {
                break;
            }
            if (t.getFromState() == state && t.getToState() == state)
            {
                rank++;
            }
        }

        double best = -Double.MAX_VALUE;
        int bestX = centreX;
        int bestY = centreY - reach;
        int taken = 0;
        for (int i = 0; i < directions.length; i++)
        {
            double length = Math.hypot(directions[i][0], directions[i][1]);
            int x = centreX + (int)Math.round(directions[i][0] / length * reach);
            int y = centreY + (int)Math.round(directions[i][1] / length * reach);
            // The start marker comes in from the left, so a loop that way lands on top of it.
            double marker = state.isStartState() && directions[i][0] < 0? 60 : 0;
            double score = clearance(machine, state, x, y) - penalty[i] - marker;
            if (score > best)
            {
                best = score;
                bestX = x;
                bestY = y;
                taken = i;
            }
        }
        // Then step past the directions this state's earlier loops already took, skipping any the
        // first choice ruled out for the same reasons it did.
        for (int skip = 0; skip < rank; skip++)
        {
            for (int tries = 0; tries < directions.length; tries++)
            {
                taken = (taken + 1) % directions.length;
                if (!(state.isStartState() && directions[taken][0] < 0))
                {
                    break;
                }
            }
            double length = Math.hypot(directions[taken][0], directions[taken][1]);
            bestX = centreX + (int)Math.round(directions[taken][0] / length * reach);
            bestY = centreY + (int)Math.round(directions[taken][1] / length * reach);
        }
        transition.setControlPoint(bestX, bestY);
    }

    /**
     * How far a point is from the nearest state other than the one given.
     * @param machine The machine being drawn.
     * @param ignore The state not to count.
     * @param x The point.
     * @param y The point.
     * @return The distance to the nearest other state, capped so that empty space does not win by
     *         an arbitrary amount.
     */
    private static double clearance(Machine machine, State ignore, int x, int y)
    {
        double closest = 400;
        for (Object o : machine.getStates())
        {
            State s = (State)o;
            if (s == ignore || !isPlaced(s))
            {
                continue;
            }
            closest = Math.min(closest, Math.hypot(s.getX() + STATE_W / 2.0 - x,
                                                   s.getY() + STATE_W / 2.0 - y));
        }
        return closest;
    }

    /**
     * Work out how far an arrow needs to bow to miss any state it does not connect.
     * @param machine The machine being drawn.
     * @param from The state the arrow leaves.
     * @param to The state it arrives at.
     * @param midX The midpoint of the straight line.
     * @param midY The midpoint of the straight line.
     * @param normalX The unit normal to the line.
     * @param normalY The unit normal to the line.
     * @return How far to displace the control point; 0 if the straight line is clear.
     */
    private static double detour(Machine machine, State from, State to,
                                 double midX, double midY, double normalX, double normalY)
    {
        double worst = 0;
        for (Object o : machine.getStates())
        {
            State s = (State)o;
            if (s == from || s == to || !isPlaced(s))
            {
                continue;
            }
            double cx = s.getX() + STATE_W / 2.0;
            double cy = s.getY() + STATE_W / 2.0;
            double distance = pointToSegment(cx, cy,
                    from.getX() + STATE_W / 2.0, from.getY() + STATE_W / 2.0,
                    to.getX() + STATE_W / 2.0, to.getY() + STATE_W / 2.0);
            if (distance >= CLEARANCE)
            {
                continue;
            }
            // Bow away from whichever side the obstacle sits on.
            double side = (cx - midX) * normalX + (cy - midY) * normalY;
            double push = (CLEARANCE - distance) * 2 + BOW;
            if (side > 0)
            {
                push = -push;
            }
            if (Math.abs(push) > Math.abs(worst))
            {
                worst = push;
            }
        }
        return worst;
    }

    private static double pointToSegment(double px, double py,
                                         double ax, double ay, double bx, double by)
    {
        double dx = bx - ax;
        double dy = by - ay;
        double lengthSquared = dx * dx + dy * dy;
        if (lengthSquared < 1e-9)
        {
            return Math.hypot(px - ax, py - ay);
        }
        double t = ((px - ax) * dx + (py - ay) * dy) / lengthSquared;
        t = Math.max(0, Math.min(1, t));
        return Math.hypot(px - (ax + t * dx), py - (ay + t * dy));
    }

    private static boolean hasReverse(Machine machine, State from, State to)
    {
        for (Object o : machine.getTransitions())
        {
            Transition t = (Transition)o;
            if (t.getFromState() == to && t.getToState() == from)
            {
                return true;
            }
        }
        return false;
    }

    /* ---------------------------------------------------------------- *
     * Odds and ends
     * ---------------------------------------------------------------- */

    private static List<State> states(Machine machine)
    {
        List<State> result = new ArrayList<State>();
        for (Object o : machine.getStates())
        {
            result.add((State)o);
        }
        return result;
    }

    private static Map<State, List<State>> neighbours(Machine machine)
    {
        Map<State, List<State>> result = new HashMap<State, List<State>>();
        for (Object o : machine.getStates())
        {
            result.put((State)o, new ArrayList<State>());
        }
        for (Object o : machine.getTransitions())
        {
            Transition t = (Transition)o;
            State from = (State)t.getFromState();
            State to = (State)t.getToState();
            if (from == to || from == null || to == null)
            {
                continue;
            }
            result.get(from).add(to);
            result.get(to).add(from);
        }
        return result;
    }

    /**
     * Shift everything so the diagram sits inside the canvas with a margin.
     * @param states The states to shift.
     */
    private static void pullIntoCanvas(List<State> states)
    {
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        for (State s : states)
        {
            minX = Math.min(minX, s.getX());
            minY = Math.min(minY, s.getY());
        }
        int shiftX = MARGIN - minX;
        int shiftY = MARGIN - minY;
        for (State s : states)
        {
            s.setPosition(snap(clamp(s.getX() + shiftX)), snap(clamp(s.getY() + shiftY)));
        }
    }

    private static double right(Collection<State> states)
    {
        double result = MARGIN;
        for (State s : states)
        {
            result = Math.max(result, s.getX());
        }
        return result;
    }

    private static double middleY(Collection<State> states)
    {
        if (states.isEmpty())
        {
            return MARGIN;
        }
        double total = 0;
        for (State s : states)
        {
            total += s.getY();
        }
        return total / states.size();
    }

    private static int snap(int value)
    {
        return Math.round(value / (float)GRID) * GRID;
    }

    private static int clamp(int value)
    {
        return Math.max(MARGIN, Math.min(CANVAS - STATE_W - MARGIN, value));
    }
}
