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

import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.geom.CubicCurve2D;
import java.awt.geom.Point2D;
import java.awt.geom.QuadCurve2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import tuataraTMSim.Spline;
import tuataraTMSim.machine.Machine;
import tuataraTMSim.machine.PreAction;
import tuataraTMSim.machine.State;
import tuataraTMSim.machine.Transition;

/**
 * How readable a drawing actually is.
 *
 * A machine can be correct, validate cleanly, and still be unreadable, and none of the other checks
 * here would notice: {@link Diagnosis} looks at what the machine means and {@link Layout} at where
 * the states went, but neither looks at the labels, which is where a diagram usually falls apart.
 * Two transitions between the same pair of states put their text in the same place; an arrow bowed
 * around an obstacle sweeps its label across a third state; a long arrow passes between some other
 * label and the line it belongs to, so the reader can no longer tell which is which.
 *
 * So this class measures the picture rather than the machine. Everything it reports is derived from
 * the same geometry the renderer draws from -- the spline built from the control point, the action
 * text measured in the font it is painted in -- so a complaint here corresponds to something
 * genuinely visible, and cannot drift out of step with the drawing.
 *
 * It is used two ways. {@link #score} drives the search in {@link Layout#tidy}, which nudges control
 * points until the number stops falling. {@link #problems} says the same thing in words, and rides
 * along with the tool results so that an assistant building a machine a few states at a time is
 * told when the thing it is building has stopped being legible.
 */
public final class Legibility
{
    /**
     * How close an arrow may pass to a state it has nothing to do with.
     */
    private static final double STATE_CLEARANCE = 22;

    /**
     * Clear space wanted around a label, so that text does not merely fail to overlap but reads as
     * separate from what is beside it.
     */
    private static final double LABEL_PADDING = 4;

    /**
     * How near a state a crossing has to be before it stops reading as an ordinary crossing and
     * starts looking like an arrow ending there. Mirrors the same idea in the FSM designer.
     */
    private static final double CROSSING_CLEARANCE = 46;

    /**
     * How close two arrows have to be before they read as one line rather than two.
     */
    private static final double CORRIDOR = 11;

    /**
     * How far two arrows may run alongside each other before it counts against them. Two arrows
     * joining the same pair of states have to converge at both ends, so some shared run is in the
     * nature of the drawing and only a sustained one is a fault.
     */
    private static final double TOLERATED_RUN = 34;

    /**
     * How many straight segments an arc is chopped into for intersection tests. An arc between two
     * states is shallow, so its chords follow it closely at this many.
     */
    private static final int ARC_SEGMENTS = 20;

    /**
     * How many straight segments a self-loop is chopped into.
     *
     * More than an arc needs, because a loop is a tight cubic doubling back on itself over a short
     * distance. Sampled as coarsely as an arc its chords cut the corners badly enough to step over
     * a label entirely, which showed up as drawings reported clean while a loop ran through text.
     */
    private static final int LOOP_SEGMENTS = 36;

    /**
     * Font metrics for the action font, kept because measuring text is the slow part of scoring and
     * the search asks for it thousands of times. A one pixel image is enough to obtain them, and
     * works with no display attached.
     */
    private static FontMetrics s_metrics;

    /**
     * Not instantiable.
     */
    private Legibility() { }

    /* ---------------------------------------------------------------- *
     * Geometry
     * ---------------------------------------------------------------- */

    /**
     * A rectangle, with the little bit of arithmetic the checks below need.
     */
    public static final class Box
    {
        public final double x;
        public final double y;
        public final double width;
        public final double height;

        /**
         * Build a box. Public so that checks written against the drawing, including the stress
         * harness, can construct one without going through a transition.
         * @param x The centre.
         * @param y The centre.
         * @param width How wide.
         * @param height How tall.
         */
        public Box(double x, double y, double width, double height)
        {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
        }

        public double left()   { return x - width / 2; }
        public double right()  { return x + width / 2; }
        public double top()    { return y - height / 2; }
        public double bottom() { return y + height / 2; }

        /**
         * Grow the box on every side, for checks that want clear space rather than bare contact.
         * @param by How far to grow it.
         * @return The larger box.
         */
        Box pad(double by)
        {
            return new Box(x, y, width + by * 2, height + by * 2);
        }

        /**
         * How much of this box another one covers.
         * @param other The box to test against.
         * @return The area shared by the two, or zero if they are apart.
         */
        double overlap(Box other)
        {
            double w = Math.min(right(), other.right()) - Math.max(left(), other.left());
            double h = Math.min(bottom(), other.bottom()) - Math.max(top(), other.top());
            return w <= 0 || h <= 0? 0 : w * h;
        }

        public boolean contains(double px, double py)
        {
            return px >= left() && px <= right() && py >= top() && py <= bottom();
        }
    }

    /**
     * Where a transition's label is drawn, and how big it is.
     *
     * This follows the renderer exactly: the text sits on a rounded plate a fixed distance from the
     * midpoint of the spline, so its position is decided entirely by the control point. That is why
     * {@link Layout#tidy} can move a label at all -- there is no separate offset to move, and
     * bending the arrow is the only lever there is.
     * @param transition The transition whose label to locate.
     * @return The box the label occupies, or null if it has no states to hang from.
     */
    public static Box labelBox(Transition transition)
    {
        State from = (State)transition.getFromState();
        State to = (State)transition.getToState();
        if (from == null || to == null || !Layout.isPlaced(from) || !Layout.isPlaced(to))
        {
            return null;
        }

        Point2D at = actionLocation(transition, from, to);
        FontMetrics metrics = metrics();
        String text = String.valueOf(transition.getAction());
        double width = metrics.stringWidth(text) + 10;
        double height = metrics.getAscent() + metrics.getDescent() + 4;
        return new Box(at.getX(), at.getY(), width, height);
    }

    /**
     * The point the action text is centred on. Mirrors Transition.getActionLocation, which is
     * protected and so cannot be called from here.
     * @param transition The transition.
     * @param from The state it leaves.
     * @param to The state it arrives at.
     * @return Where the text is centred.
     */
    private static Point2D actionLocation(Transition transition, State from, State to)
    {
        Point2D mid = Spline.getMidPointFromControlPoint(transition.getControlPoint(), from, to);
        if (from != to)
        {
            return new Point2D.Double(mid.getX(), mid.getY() - Transition.ACTION_TEXT_DISTANCE);
        }
        double dx = mid.getX() - (from.getX() + State.STATE_RENDERING_WIDTH / 2.0);
        double dy = mid.getY() - (from.getY() + State.STATE_RENDERING_WIDTH / 2.0);
        double angle = Math.atan2(dy, dx);
        return new Point2D.Double(
                mid.getX() + Transition.ACTION_TEXT_DISTANCE * Math.cos(angle),
                mid.getY() + Transition.ACTION_TEXT_DISTANCE * Math.sin(angle));
    }

    /**
     * Build a box, for callers checking a drawing against something other than a transition.
     * @param x The centre.
     * @param y The centre.
     * @param width How wide.
     * @param height How tall.
     * @return The box.
     */
    public static Box box(double x, double y, double width, double height)
    {
        return new Box(x, y, width, height);
    }

    /**
     * The line a transition is drawn as, chopped into straight pieces.
     *
     * Only the visible part. The splines this program builds run centre to centre, so every arrow
     * out of a state starts at the same point as every other arrow out of it, and a naive reading
     * of the geometry finds them all crossing one another inside every state they touch. What a
     * reader sees is the part outside the circles, so that is what is returned.
     * @param transition The transition to trace.
     * @return Points along the visible part of the curve, or an empty list if it cannot be drawn.
     */
    public static List<Point2D> path(Transition transition)
    {
        List<Point2D> points = new ArrayList<Point2D>();
        State from = (State)transition.getFromState();
        State to = (State)transition.getToState();
        if (from == null || to == null || !Layout.isPlaced(from) || !Layout.isPlaced(to))
        {
            return points;
        }

        if (from == to)
        {
            CubicCurve2D curve = Spline.buildLoopSpline(transition.getControlPoint(), from);
            for (int i = 0; i <= LOOP_SEGMENTS; i++)
            {
                points.add(cubic(curve, i / (double)LOOP_SEGMENTS));
            }
        }
        else
        {
            QuadCurve2D curve = Spline.buildArcSpline(transition.getControlPoint(), from, to);
            for (int i = 0; i <= ARC_SEGMENTS; i++)
            {
                points.add(quadratic(curve, i / (double)ARC_SEGMENTS));
            }
        }
        return trim(points, from, to);
    }

    /**
     * Drop the ends of a path that are hidden underneath the states it joins.
     * @param points The whole curve.
     * @param from The state it leaves.
     * @param to The state it arrives at.
     * @return The part that is actually drawn.
     */
    private static List<Point2D> trim(List<Point2D> points, State from, State to)
    {
        int first = 0;
        while (first < points.size() && inside(points.get(first), from))
        {
            first++;
        }
        int last = points.size() - 1;
        while (last >= first && inside(points.get(last), to))
        {
            last--;
        }
        return first > last? new ArrayList<Point2D>() : points.subList(first, last + 1);
    }

    private static boolean inside(Point2D p, State s)
    {
        double cx = s.getX() + State.STATE_RENDERING_WIDTH / 2.0;
        double cy = s.getY() + State.STATE_RENDERING_WIDTH / 2.0;
        // A shade more than the circle, because a final state carries a second ring outside it.
        return Math.hypot(p.getX() - cx, p.getY() - cy) < State.STATE_RENDERING_WIDTH / 2.0 + 4;
    }

    private static Point2D quadratic(QuadCurve2D c, double t)
    {
        double u = 1 - t;
        return new Point2D.Double(
                u * u * c.getX1() + 2 * u * t * c.getCtrlX() + t * t * c.getX2(),
                u * u * c.getY1() + 2 * u * t * c.getCtrlY() + t * t * c.getY2());
    }

    private static Point2D cubic(CubicCurve2D c, double t)
    {
        double u = 1 - t;
        return new Point2D.Double(
                u * u * u * c.getX1() + 3 * u * u * t * c.getCtrlX1()
                    + 3 * u * t * t * c.getCtrlX2() + t * t * t * c.getX2(),
                u * u * u * c.getY1() + 3 * u * u * t * c.getCtrlY1()
                    + 3 * u * t * t * c.getCtrlY2() + t * t * t * c.getY2());
    }

    /**
     * The box a state occupies, including the ring drawn around a final state.
     * @param state The state.
     * @return Its box.
     */
    public static Box stateBox(State state)
    {
        double w = State.STATE_RENDERING_WIDTH;
        return new Box(state.getX() + w / 2, state.getY() + w / 2, w, w);
    }

    /* ---------------------------------------------------------------- *
     * Scoring
     * ---------------------------------------------------------------- */

    /**
     * One thing wrong with the picture. Carries both a sentence and a cost so that the search and
     * the report are driven by exactly the same findings, and cannot disagree.
     */
    public static final class Problem
    {
        /**
         * What is wrong, in words.
         */
        public final String description;

        /**
         * How much this counts against the layout.
         */
        public final double cost;

        Problem(String description, double cost)
        {
            this.description = description;
            this.cost = cost;
        }
    }

    /**
     * Everything wrong with how a machine is drawn, worst first.
     * @param machine The machine to inspect.
     * @return The problems found; empty if the drawing reads cleanly.
     */
    public static List<Problem> problems(Machine machine)
    {
        return problems(machine, null);
    }

    /**
     * The problems with a drawing, or only those one transition is party to.
     *
     * The focused form exists for the search in {@link Layout#tidy}, which moves one arrow at a
     * time. Everything not involving that arrow is unchanged by the move, so recomputing it is
     * wasted: comparing two candidate positions by the findings the arrow is party to picks the
     * same winner as comparing whole-diagram scores, and costs one pass over the transitions
     * rather than one over every pair of them.
     * @param machine The machine to inspect.
     * @param focus The only transition of interest, or null for the whole drawing.
     * @return The problems found, worst first.
     */
    static List<Problem> problems(Machine machine, Transition focus)
    {
        List<Transition> transitions = transitions(machine);
        List<State> states = states(machine);

        List<Box> labels = new ArrayList<Box>();
        List<List<Point2D>> paths = new ArrayList<List<Point2D>>();
        for (Transition t : transitions)
        {
            labels.add(labelBox(t));
            paths.add(path(t));
        }

        List<Problem> found = new ArrayList<Problem>();

        // Two labels on top of each other. The reader cannot tell which arrow either belongs to,
        // which is the worst thing that can happen to a diagram that is mostly labels.
        for (int i = 0; i < transitions.size(); i++)
        {
            for (int j = i + 1; j < transitions.size(); j++)
            {
                if (focus != null && transitions.get(i) != focus && transitions.get(j) != focus)
                {
                    continue;
                }
                Box a = labels.get(i);
                Box b = labels.get(j);
                if (a == null || b == null)
                {
                    continue;
                }
                double area = a.pad(LABEL_PADDING).overlap(b.pad(LABEL_PADDING));
                if (area > 0)
                {
                    found.add(new Problem("the labels on " + name(transitions.get(i)) + " and "
                                + name(transitions.get(j)) + " overlap", 40 + area / 12));
                }
            }
        }

        // A label lying over a state hides the state and is itself hard to read.
        for (int i = 0; i < transitions.size(); i++)
        {
            Box label = labels.get(i);
            if (label == null || (focus != null && transitions.get(i) != focus))
            {
                continue;
            }
            for (State s : states)
            {
                if (label.pad(LABEL_PADDING).overlap(stateBox(s)) > 0)
                {
                    found.add(new Problem("the label on " + name(transitions.get(i))
                                + " sits over state \"" + s.getLabel() + "\"", 45));
                }
            }
        }

        for (int i = 0; i < transitions.size(); i++)
        {
            Box label = labels.get(i);
            if (label == null)
            {
                continue;
            }
            for (int j = 0; j < transitions.size(); j++)
            {
                if (i == j || (focus != null
                               && transitions.get(i) != focus && transitions.get(j) != focus))
                {
                    continue;
                }
                List<Point2D> other = paths.get(j);

                // An arrow drawn straight through somebody else's text, or close enough along its
                // edge to touch the plate the text sits on. The padding matters: a line grazing
                // the rim of a label is exactly as hard to read as one through the middle, and
                // without it the check passes on drawings that plainly look wrong.
                if (crossesBox(other, label.pad(LABEL_PADDING)))
                {
                    found.add(new Problem("the arrow " + name(transitions.get(j))
                                + " runs through the label on " + name(transitions.get(i)), 30));
                    continue;
                }
                // A label is read as belonging to whatever it sits beside. An arrow through that
                // gap breaks the association just as thoroughly as covering the text would.
                if (crossesTether(other, label, transitions.get(i)))
                {
                    found.add(new Problem("the arrow " + name(transitions.get(j))
                                + " passes between " + name(transitions.get(i))
                                + " and its label", 25));
                }
            }
        }

        // An arrow grazing a state it has nothing to do with reads as though it stops there.
        for (int i = 0; i < transitions.size(); i++)
        {
            Transition t = transitions.get(i);
            if (focus != null && t != focus)
            {
                continue;
            }
            for (State s : states)
            {
                if (s == t.getFromState() || s == t.getToState())
                {
                    continue;
                }
                if (touches(paths.get(i), s))
                {
                    found.add(new Problem("the arrow " + name(t) + " passes through state \""
                                + s.getLabel() + "\"", 35));
                }
            }
        }

        // Two arrows running alongside one another for a stretch. Worth its own finding, because
        // it is invisible to every other check here: the lines never cross, no label is covered,
        // and the drawing still cannot be read, since there is no point along the shared run where
        // a reader can tell which line is which or which label belongs to which. Arrows between
        // the same pair of states are the usual culprit and must be fanned apart rather than left
        // stacked.
        for (int i = 0; i < transitions.size(); i++)
        {
            for (int j = i + 1; j < transitions.size(); j++)
            {
                if (focus != null && transitions.get(i) != focus && transitions.get(j) != focus)
                {
                    continue;
                }
                double run = sharedRun(paths.get(i), paths.get(j));
                if (run > TOLERATED_RUN)
                {
                    found.add(new Problem("the arrows " + name(transitions.get(i)) + " and "
                                + name(transitions.get(j))
                                + " run alongside each other instead of separating",
                                22 + (run - TOLERATED_RUN) * 0.35));
                }
            }
        }

        // Crossings, but only the ones beside a state: two arrows crossing in open space is
        // unavoidable in most machines and reads perfectly well.
        for (int i = 0; i < transitions.size(); i++)
        {
            for (int j = i + 1; j < transitions.size(); j++)
            {
                if (focus != null && transitions.get(i) != focus && transitions.get(j) != focus)
                {
                    continue;
                }
                Point2D hit = firstCrossing(paths.get(i), paths.get(j));
                if (hit == null)
                {
                    continue;
                }
                double clearance = Double.MAX_VALUE;
                for (State s : states)
                {
                    clearance = Math.min(clearance,
                            Math.hypot(hit.getX() - (s.getX() + State.STATE_RENDERING_WIDTH / 2.0),
                                       hit.getY() - (s.getY() + State.STATE_RENDERING_WIDTH / 2.0)));
                }
                if (clearance < CROSSING_CLEARANCE)
                {
                    found.add(new Problem("the arrows " + name(transitions.get(i)) + " and "
                                + name(transitions.get(j)) + " cross close to a state", 20));
                }
            }
        }

        // States near enough to read as one. Nothing to do with any single arrow, so the focused
        // form skips it entirely rather than reporting it against whichever arrow was being moved.
        for (int i = 0; focus == null && i < states.size(); i++)
        {
            for (int j = i + 1; j < states.size(); j++)
            {
                State a = states.get(i);
                State b = states.get(j);
                double gap = Math.hypot(a.getX() - b.getX(), a.getY() - b.getY());
                if (gap < State.STATE_RENDERING_WIDTH * 2)
                {
                    found.add(new Problem("states \"" + a.getLabel() + "\" and \"" + b.getLabel()
                                + "\" are too close together to tell apart", 60));
                }
            }
        }

        java.util.Collections.sort(found, new java.util.Comparator<Problem>()
        {
            public int compare(Problem a, Problem b)
            {
                return Double.compare(b.cost, a.cost);
            }
        });
        return found;
    }

    /**
     * How bad a drawing is overall. Zero means nothing worth complaining about.
     * @param machine The machine to score.
     * @return The total cost of every problem found.
     */
    public static double score(Machine machine)
    {
        double total = 0;
        for (Problem p : problems(machine))
        {
            total += p.cost;
        }
        return total;
    }

    /**
     * How bad the part of a drawing one transition is party to is.
     * @param machine The machine to score.
     * @param focus The transition of interest.
     * @return The total cost of the findings involving it.
     */
    static double scoreFor(Machine machine, Transition focus)
    {
        double total = 0;
        for (Problem p : problems(machine, focus))
        {
            total += p.cost;
        }
        return total;
    }

    /**
     * The problems with a drawing, as sentences, for a tool result.
     * @param machine The machine to inspect.
     * @param limit The most to report.
     * @return The descriptions, worst first.
     */
    public static List<String> describe(Machine machine, int limit)
    {
        List<String> out = new ArrayList<String>();
        for (Problem p : problems(machine))
        {
            if (out.size() >= limit)
            {
                out.add("...and more");
                break;
            }
            out.add(p.description);
        }
        return out;
    }

    /**
     * A short verdict on a drawing, to travel with the results of the tools that change one.
     *
     * This exists because an assistant adding states a few at a time never sees the picture unless
     * it asks, and so does not find out that the diagram has become a knot until the user says so.
     * Reporting it on every edit is what turns the layout from something checked at the end into
     * something checked as you go.
     * @param machine The machine to inspect.
     * @return A JSON object saying whether the drawing reads cleanly, and what is wrong if not.
     */
    public static Object report(Machine machine)
    {
        List<String> problems = describe(machine, 6);
        if (problems.isEmpty())
        {
            return Json.object("readable", Boolean.TRUE);
        }
        return Json.object(
                "readable", Boolean.FALSE,
                "problems", problems,
                "hint", "Call arrange_machine to have these worked out again, or move_state to place "
                      + "them yourself. render_machine shows what the user is looking at.");
    }

    /* ---------------------------------------------------------------- *
     * The tests behind the findings
     * ---------------------------------------------------------------- */

    /**
     * Does an arrow run through a rectangle?
     *
     * Whole segments, not the points they are sampled at. Testing the points alone misses the case
     * that matters most: a long arrow steps tens of pixels at a time, so it can pass clean through
     * a label without a single sample landing inside it, and the check then reports a diagram as
     * readable while an arrow is drawn straight across the text.
     * @param path The arrow.
     * @param box The rectangle.
     * @return true if any part of the arrow is inside the rectangle.
     */
    private static boolean crossesBox(List<Point2D> path, Box box)
    {
        for (int i = 0; i < path.size(); i++)
        {
            Point2D p = path.get(i);
            if (box.contains(p.getX(), p.getY()))
            {
                return true;
            }
            if (i + 1 < path.size() && segmentCrossesBox(p, path.get(i + 1), box))
            {
                return true;
            }
        }
        return false;
    }

    private static boolean segmentCrossesBox(Point2D a, Point2D b, Box box)
    {
        Point2D topLeft     = new Point2D.Double(box.left(),  box.top());
        Point2D topRight    = new Point2D.Double(box.right(), box.top());
        Point2D bottomLeft  = new Point2D.Double(box.left(),  box.bottom());
        Point2D bottomRight = new Point2D.Double(box.right(), box.bottom());
        return segmentsCross(a, b, topLeft, topRight) != null
            || segmentsCross(a, b, topRight, bottomRight) != null
            || segmentsCross(a, b, bottomRight, bottomLeft) != null
            || segmentsCross(a, b, bottomLeft, topLeft) != null;
    }

    /**
     * Does an arrow pass between a label and the line it labels?
     *
     * The gap is treated as the segment from the point on the curve the text hangs off to the near
     * edge of the text itself. That strip is what the eye reads as tying the two together, and
     * anything drawn across it breaks the tie.
     * @param path The arrow that might be in the way.
     * @param label The label being tethered.
     * @param owner The transition the label belongs to.
     * @return true if the arrow cuts the tether.
     */
    private static boolean crossesTether(List<Point2D> path, Box label, Transition owner)
    {
        State from = (State)owner.getFromState();
        State to = (State)owner.getToState();
        if (from == null || to == null)
        {
            return false;
        }
        Point2D anchor = Spline.getMidPointFromControlPoint(owner.getControlPoint(), from, to);

        double dx = label.x - anchor.getX();
        double dy = label.y - anchor.getY();
        double length = Math.hypot(dx, dy);
        if (length < 1)
        {
            return false;
        }
        // Stop at the edge of the text; inside it is already counted as running through the label.
        double half = Math.min(length, Math.abs(dx) / length * label.width / 2
                                     + Math.abs(dy) / length * label.height / 2);
        Point2D end = new Point2D.Double(label.x - dx / length * half, label.y - dy / length * half);

        for (int i = 0; i < path.size() - 1; i++)
        {
            if (segmentsCross(anchor, end, path.get(i), path.get(i + 1)) != null)
            {
                return true;
            }
        }
        return false;
    }

    private static boolean touches(List<Point2D> path, State state)
    {
        double cx = state.getX() + State.STATE_RENDERING_WIDTH / 2.0;
        double cy = state.getY() + State.STATE_RENDERING_WIDTH / 2.0;
        double limit = State.STATE_RENDERING_WIDTH / 2.0 + STATE_CLEARANCE;
        // Segments rather than their endpoints, for the same reason as crossesBox: a long arrow
        // could otherwise skip over a state between one sample and the next.
        for (int i = 0; i < path.size() - 1; i++)
        {
            if (distanceToSegment(cx, cy, path.get(i), path.get(i + 1)) < limit)
            {
                return true;
            }
        }
        return !path.isEmpty()
            && Math.hypot(path.get(0).getX() - cx, path.get(0).getY() - cy) < limit;
    }

    private static double distanceToSegment(double px, double py, Point2D a, Point2D b)
    {
        double dx = b.getX() - a.getX();
        double dy = b.getY() - a.getY();
        double lengthSquared = dx * dx + dy * dy;
        if (lengthSquared < 1e-9)
        {
            return Math.hypot(px - a.getX(), py - a.getY());
        }
        double t = ((px - a.getX()) * dx + (py - a.getY()) * dy) / lengthSquared;
        t = Math.max(0, Math.min(1, t));
        return Math.hypot(px - (a.getX() + t * dx), py - (a.getY() + t * dy));
    }

    /**
     * How much of one arrow is drawn close enough to another to be mistaken for it.
     *
     * Measured as the length of the first arrow whose points lie within a narrow corridor of the
     * second. Two arrows joining the same pair of states necessarily converge as they approach
     * either end, and that much is unavoidable and reads fine, which is why a short run is
     * tolerated by the caller rather than treated as a fault.
     * @param a One arrow.
     * @param b The other.
     * @return The length of a that runs alongside b, in pixels.
     */
    private static double sharedRun(List<Point2D> a, List<Point2D> b)
    {
        if (apart(a, b) || a.size() < 2 || b.size() < 2)
        {
            return 0;
        }
        double run = 0;
        for (int i = 0; i < a.size() - 1; i++)
        {
            Point2D p = a.get(i);
            Point2D q = a.get(i + 1);
            double midX = (p.getX() + q.getX()) / 2;
            double midY = (p.getY() + q.getY()) / 2;
            double nearest = Double.MAX_VALUE;
            for (int j = 0; j < b.size() - 1 && nearest > CORRIDOR; j++)
            {
                nearest = Math.min(nearest,
                        distanceToSegment(midX, midY, b.get(j), b.get(j + 1)));
            }
            if (nearest <= CORRIDOR)
            {
                run += Math.hypot(q.getX() - p.getX(), q.getY() - p.getY());
            }
        }
        return run;
    }

    private static Point2D firstCrossing(List<Point2D> a, List<Point2D> b)
    {
        // Most pairs of arrows are nowhere near each other, and this is the innermost loop of the
        // search in Layout.tidy. Rejecting those on their bounding boxes first costs a linear pass
        // and saves a quadratic one.
        if (apart(a, b))
        {
            return null;
        }
        for (int i = 0; i < a.size() - 1; i++)
        {
            for (int j = 0; j < b.size() - 1; j++)
            {
                Point2D hit = segmentsCross(a.get(i), a.get(i + 1), b.get(j), b.get(j + 1));
                if (hit != null)
                {
                    return hit;
                }
            }
        }
        return null;
    }

    /**
     * Do two paths occupy separate parts of the canvas?
     * @param a One path.
     * @param b The other.
     * @return true if their bounding boxes do not meet, so they cannot possibly cross.
     */
    private static boolean apart(List<Point2D> a, List<Point2D> b)
    {
        if (a.isEmpty() || b.isEmpty())
        {
            return true;
        }
        double aMinX = Double.MAX_VALUE;
        double aMaxX = -Double.MAX_VALUE;
        double aMinY = Double.MAX_VALUE;
        double aMaxY = -Double.MAX_VALUE;
        for (Point2D p : a)
        {
            aMinX = Math.min(aMinX, p.getX());
            aMaxX = Math.max(aMaxX, p.getX());
            aMinY = Math.min(aMinY, p.getY());
            aMaxY = Math.max(aMaxY, p.getY());
        }
        double bMinX = Double.MAX_VALUE;
        double bMaxX = -Double.MAX_VALUE;
        double bMinY = Double.MAX_VALUE;
        double bMaxY = -Double.MAX_VALUE;
        for (Point2D p : b)
        {
            bMinX = Math.min(bMinX, p.getX());
            bMaxX = Math.max(bMaxX, p.getX());
            bMinY = Math.min(bMinY, p.getY());
            bMaxY = Math.max(bMaxY, p.getY());
        }
        return aMaxX < bMinX || bMaxX < aMinX || aMaxY < bMinY || bMaxY < aMinY;
    }

    private static Point2D segmentsCross(Point2D p1, Point2D p2, Point2D p3, Point2D p4)
    {
        double d1x = p2.getX() - p1.getX();
        double d1y = p2.getY() - p1.getY();
        double d2x = p4.getX() - p3.getX();
        double d2y = p4.getY() - p3.getY();
        double denominator = d1x * d2y - d1y * d2x;
        if (Math.abs(denominator) < 1e-9)
        {
            return null;
        }
        double t = ((p3.getX() - p1.getX()) * d2y - (p3.getY() - p1.getY()) * d2x) / denominator;
        double u = ((p3.getX() - p1.getX()) * d1y - (p3.getY() - p1.getY()) * d1x) / denominator;
        if (t < 0 || t > 1 || u < 0 || u > 1)
        {
            return null;
        }
        return new Point2D.Double(p1.getX() + d1x * t, p1.getY() + d1y * t);
    }

    /* ---------------------------------------------------------------- *
     * Odds and ends
     * ---------------------------------------------------------------- */

    /**
     * Name a transition the way a user would say it out loud.
     * @param t The transition.
     * @return Something like {@code q0 -> q1 on '0'}.
     */
    private static String name(Transition t)
    {
        State from = (State)t.getFromState();
        State to = (State)t.getToState();
        return (from == null? "?" : from.getLabel()) + " -> " + (to == null? "?" : to.getLabel())
             + " on '" + t.getAction().getInputChar() + "'";
    }

    private static List<Transition> transitions(Machine machine)
    {
        List<Transition> out = new ArrayList<Transition>();
        for (Object o : machine.getTransitions())
        {
            Transition t = (Transition)o;
            if (Layout.isPlaced((State)t.getFromState()) && Layout.isPlaced((State)t.getToState()))
            {
                out.add(t);
            }
        }
        return out;
    }

    private static List<State> states(Machine machine)
    {
        List<State> out = new ArrayList<State>();
        for (Object o : machine.getStates())
        {
            State s = (State)o;
            if (Layout.isPlaced(s))
            {
                out.add(s);
            }
        }
        return out;
    }

    /**
     * Metrics for the font actions are painted in, obtained without a display.
     * @return The metrics.
     */
    private static synchronized FontMetrics metrics()
    {
        if (s_metrics == null)
        {
            Font font = PreAction.actionFont();
            BufferedImage scratch = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = scratch.createGraphics();
            try
            {
                s_metrics = g.getFontMetrics(font);
            }
            finally
            {
                g.dispose();
            }
        }
        return s_metrics;
    }
}
