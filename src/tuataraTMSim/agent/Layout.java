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

import java.awt.geom.Point2D;
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
     * Clear space between one column of states and the next. Wide enough that the label on an arrow
     * running between two columns has room to sit without touching either end.
     */
    private static final int COLUMN_GAP = 168;

    /**
     * Vertical distance between states in the same column. A label sits ACTION_TEXT_DISTANCE clear
     * of the line it belongs to, so states stacked closer than this have their arrows' text landing
     * on whichever state is above.
     */
    private static final int ROW_GAP = 132;

    /**
     * Space left at the top and left of the canvas.
     */
    private static final int MARGIN = 72;

    /**
     * The canvas a machine has to fit inside. Mirrors MainWindow.MACHINE_CANVAS_SIZE_X.
     */
    private static final int CANVAS = 2000;

    /**
     * How far apart two states must sit before the picture between them stops being cramped.
     *
     * Not merely far enough that the circles miss each other: the arrow joining them carries a
     * label a fixed distance off the line, and that text needs somewhere to go. Two states a circle
     * apart do not collide and still produce a diagram nobody can read, which is what this being
     * too small used to cause.
     */
    private static final int SEPARATION = 120;

    /**
     * The most transitions worth running the legibility search over. Past this the diagram is too
     * dense for nudging arrows to rescue, and the search stops being cheap.
     */
    private static final int TIDY_LIMIT = 60;

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
        // Routing gets each arrow clear of the states; this gets the labels clear of each other.
        tidy(machine, allTransitions(machine));
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
     * Making the result legible
     * ---------------------------------------------------------------- */

    /**
     * Nudge arrows until the labels stop colliding with things.
     *
     * Placing states well is only half of a readable diagram. The other half is the text, and in
     * this program the text cannot be moved on its own: a label is drawn a fixed distance from the
     * midpoint of its curve, so the only way to shift one is to reshape the arrow it belongs to.
     * That is what this does. Each arrow is offered a handful of alternative control points --
     * bowed further out, bowed the other way, or slid along the line so the label travels with it
     * -- and keeps whichever leaves the whole picture reading best, as scored by
     * {@link Legibility}.
     *
     * A plain hill climb, because it is enough: the moves are a dozen per arrow, the machines are
     * small, and a search that occasionally settles for second best is not worth the complexity of
     * avoiding here. It stops early the moment a round changes nothing.
     * @param machine The machine to tidy.
     * @param movable The transitions allowed to move. Anything left out keeps the curve it has,
     *                which is how a user's own bends survive an edit elsewhere.
     * @return The number of arrows that were moved.
     */
    public static int tidy(Machine machine, Collection<Transition> movable)
    {
        List<Transition> candidates = new ArrayList<Transition>();
        for (Transition t : movable)
        {
            State from = (State)t.getFromState();
            State to = (State)t.getToState();
            if (from != null && to != null && isPlaced(from) && isPlaced(to))
            {
                candidates.add(t);
            }
        }
        if (candidates.isEmpty() || machine.getTransitions().size() > TIDY_LIMIT)
        {
            return 0;
        }

        int moved = 0;
        for (int round = 0; round < 4; round++)
        {
            boolean improved = false;
            for (Transition t : candidates)
            {
                Point2D original = t.getControlPoint();
                // Only the findings this arrow is party to. Everything else is untouched by moving
                // it, so it would contribute the same constant to every candidate.
                double bestScore = Legibility.scoreFor(machine, t);
                Point2D bestPoint = original;
                if (bestScore == 0)
                {
                    continue;
                }

                for (Point2D option : options(machine, t))
                {
                    t.setControlPoint((int)Math.round(option.getX()), (int)Math.round(option.getY()));
                    double score = Legibility.scoreFor(machine, t);
                    if (score < bestScore - 1e-6)
                    {
                        bestScore = score;
                        bestPoint = option;
                        if (score == 0)
                        {
                            break;
                        }
                    }
                }

                t.setControlPoint((int)Math.round(bestPoint.getX()),
                                  (int)Math.round(bestPoint.getY()));
                if (bestPoint != original)
                {
                    improved = true;
                    moved++;
                }
            }
            if (!improved)
            {
                break;
            }
        }
        return moved;
    }

    /**
     * Every control point worth trying for one transition.
     *
     * For an arc these are displacements from the midpoint of the straight line: sideways, which
     * bends the arrow away from whatever it is colliding with, and lengthways, which slides the
     * label along the arrow without changing how far it bows. The second matters more than it
     * sounds -- two arrows running side by side usually need their labels staggered rather than
     * their lines pulled apart.
     *
     * For a self-loop the control point is a direction and a reach from the middle of the state, so
     * the options are the eight compass points at two distances.
     * @param machine The machine being tidied.
     * @param transition The transition to offer alternatives for.
     * @return The points to try, current position included.
     */
    private static List<Point2D> options(Machine machine, Transition transition)
    {
        List<Point2D> out = new ArrayList<Point2D>();
        State from = (State)transition.getFromState();
        State to = (State)transition.getToState();

        if (from == to)
        {
            double centreX = from.getX() + STATE_W / 2.0;
            double centreY = from.getY() + STATE_W / 2.0;
            int[][] directions =
            {
                { 0, -1 }, { 1, -1 }, { -1, -1 }, { 1, 0 }, { -1, 0 }, { 1, 1 }, { -1, 1 }, { 0, 1 }
            };
            for (double reach : new double[] { LOOP_REACH, LOOP_REACH * 1.4 })
            {
                for (int[] d : directions)
                {
                    double length = Math.hypot(d[0], d[1]);
                    out.add(new Point2D.Double(centreX + d[0] / length * reach,
                                               centreY + d[1] / length * reach));
                }
            }
            return out;
        }

        double midX = (from.getX() + to.getX()) / 2.0 + STATE_W / 2.0;
        double midY = (from.getY() + to.getY()) / 2.0 + STATE_W / 2.0;
        double dx = to.getX() - from.getX();
        double dy = to.getY() - from.getY();
        double length = Math.hypot(dx, dy);
        if (length < 1)
        {
            out.add(transition.getControlPoint());
            return out;
        }
        double alongX = dx / length;
        double alongY = dy / length;
        double normalX = -alongY;
        double normalY = alongX;

        double[] bows = { 0, BOW, -BOW, BOW * 1.8, -BOW * 1.8, BOW * 2.7, -BOW * 2.7 };
        // Sliding is proportional to the arrow's length, so a short arrow does not fling its label
        // off the end of itself.
        double slide = Math.min(length * 0.22, 54);
        double[] slides = { 0, slide, -slide };

        out.add(transition.getControlPoint());
        for (double bow : bows)
        {
            for (double along : slides)
            {
                // The midpoint of a quadratic moves half as far as its control point, so the
                // displacement is doubled to make the label travel the distance asked for.
                out.add(new Point2D.Double(
                            midX + normalX * bow + alongX * along * 2,
                            midY + normalY * bow + alongY * along * 2));
            }
        }
        return out;
    }

    /**
     * Every transition in a machine, for the callers that want to tidy the lot.
     * @param machine The machine.
     * @return Its transitions.
     */
    private static List<Transition> allTransitions(Machine machine)
    {
        List<Transition> out = new ArrayList<Transition>();
        for (Object o : machine.getTransitions())
        {
            out.add((Transition)o);
        }
        return out;
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

            int[] spot = bestSpot(machine, state, anchorX, anchorY, taken);
            state.setPosition(spot[0], spot[1]);
            taken.add(state);
            placed.add(state);
        }

        // Only the arrows touching a new state are (re)drawn. Every other transition keeps the
        // curve it has, including any the user bent by hand.
        Set<State> fresh = new HashSet<State>(placed);
        List<Transition> touched = new ArrayList<Transition>();
        for (Object o : machine.getTransitions())
        {
            Transition t = (Transition)o;
            if (fresh.contains(t.getFromState()) || fresh.contains(t.getToState()))
            {
                route(machine, t);
                touched.add(t);
            }
        }
        // An addition has to read as well as a whole arrangement does, or building a machine a few
        // states at a time ends in a mess that no single edit is to blame for. The scoring looks at
        // the entire picture, so a new arrow is fitted around what is already there, but only the
        // new arrows are allowed to move.
        tidy(machine, touched);
        return placed;
    }

    /**
     * Choose where a new state goes by looking at the diagram each candidate would produce.
     *
     * Room to sit is a weak test. A state can be clear of every other state and still be in a
     * thoroughly bad place -- across the diagram from the states it connects to, so that its arrows
     * have to sweep back over everything in between, dragging their labels through other people's.
     * Nudging those arrows afterwards cannot undo it, because the problem is where the state is,
     * not how the line to it is curved.
     *
     * So every free spot is tried: the state is put there, its arrows are routed, and the whole
     * picture is scored. The one that reads best wins, and ties go to the spot nearest the states
     * it connects to, which keeps an addition where the user would expect to find it.
     * @param machine The machine being added to.
     * @param state The state to place.
     * @param anchorX Where it would ideally sit.
     * @param anchorY Where it would ideally sit.
     * @param taken Every state already placed.
     * @return The chosen position.
     */
    private static int[] bestSpot(Machine machine, State state,
                                  double anchorX, double anchorY, List<State> taken)
    {
        List<int[]> options = freeSpots(anchorX, anchorY, taken);
        if (options.size() == 1 || machine.getTransitions().size() > TIDY_LIMIT)
        {
            return options.get(0);
        }

        // Only the arrows touching this state are re-routed while trying spots; everything else is
        // left alone, so the comparison is between candidate positions and nothing else.
        List<Transition> mine = new ArrayList<Transition>();
        for (Object o : machine.getTransitions())
        {
            Transition t = (Transition)o;
            if (t.getFromState() == state || t.getToState() == state)
            {
                mine.add(t);
            }
        }

        int[] best = options.get(0);
        double bestScore = Double.MAX_VALUE;
        double bestDistance = Double.MAX_VALUE;
        for (int[] option : options)
        {
            state.setPosition(option[0], option[1]);
            // Routed but not tidied. Tidying every candidate multiplies the work by the number of
            // spots for a judgement it barely changes: a position that is wrong is wrong however
            // its arrows are bent, and the winner gets tidied properly by the caller anyway.
            for (Transition t : mine)
            {
                route(machine, t);
            }

            double score = Legibility.score(machine);
            double distance = Math.hypot(option[0] - anchorX, option[1] - anchorY);
            if (score < bestScore - 1e-6 || (Math.abs(score - bestScore) < 1e-6
                                             && distance < bestDistance))
            {
                bestScore = score;
                bestDistance = distance;
                best = option;
            }
            if (bestScore == 0 && best == option)
            {
                // Nothing wrong with the picture and nearest so far: no later ring can beat it,
                // because they are generated outwards.
                break;
            }
        }
        state.setPosition(best[0], best[1]);
        return best;
    }

    /**
     * Somewhere near a point with room for a state, nearest first.
     * @param anchorX Where the state would ideally sit.
     * @param anchorY Where the state would ideally sit.
     * @param taken Every state already placed.
     * @return Positions on the grid, inside the canvas, clear of everything in taken.
     */
    private static List<int[]> freeSpots(double anchorX, double anchorY, List<State> taken)
    {
        List<int[]> found = new ArrayList<int[]>();
        for (int ring = 0; ring <= 40 && found.size() < 24; ring++)
        {
            int steps = ring == 0? 1 : Math.min(8 + ring * 4, 48);
            for (int step = 0; step < steps; step++)
            {
                double angle = (step / (double)steps) * Math.PI * 2;
                double radius = ring == 0? 0 : SEPARATION + (ring - 1) * (double)GRID * 2;
                int x = snap(clamp((int)Math.round(anchorX + Math.cos(angle) * radius)));
                int y = snap(clamp((int)Math.round(anchorY + Math.sin(angle) * radius)));
                if (!clear(x, y, taken))
                {
                    continue;
                }
                boolean already = false;
                for (int[] seen : found)
                {
                    already |= seen[0] == x && seen[1] == y;
                }
                if (!already)
                {
                    found.add(new int[] { x, y });
                }
            }
            // Enough of a choice to be worth judging, and all from the innermost rings that had
            // any room, so nothing far away is considered while somewhere close is free.
            if (found.size() >= 8)
            {
                break;
            }
        }
        if (found.isEmpty())
        {
            // Every ring was occupied, which takes a remarkable diagram. Put it past the right-hand
            // edge of everything rather than on top of another state.
            found.add(new int[] { snap(clamp((int)right(taken) + COLUMN_GAP)),
                                  snap(clamp((int)anchorY)) });
        }
        return found;
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
