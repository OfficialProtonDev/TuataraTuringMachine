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
import java.util.List;
import tuataraTMSim.exceptions.*;
import tuataraTMSim.machine.*;

/**
 * Running inputs through a machine with nobody watching.
 *
 * The machine is copied first, the tape has no listeners, and the simulator is told to stop rather
 * than ask when it meets a choice. That combination is what makes a run invisible: nothing on
 * screen moves, the user's tape is untouched, and there is no dialog waiting for an answer nobody
 * is there to give. It also makes it quick -- around twenty-five million steps a second, against
 * roughly forty thousand if the configuration string is built at every step, which is why traces
 * are bounded.
 *
 * The stepping itself is the program's own {@link Simulator}. Reimplementing it faster would mean
 * reporting verdicts for a machine subtly unlike the one the user is looking at.
 */
public final class Sandbox
{
    /**
     * How many steps a run is allowed before it is called off, unless the caller says otherwise.
     */
    public static final long DEFAULT_MAX_STEPS = 1000000L;

    /**
     * The largest step budget a caller may ask for.
     */
    public static final long MAX_MAX_STEPS = 20000000000L;

    /**
     * How long a batch of runs may take in total, unless the caller says otherwise.
     */
    public static final long DEFAULT_TIMEOUT_MS = 30000L;

    /**
     * The longest a batch of runs may take.
     */
    public static final long MAX_TIMEOUT_MS = 600000L;

    /**
     * Steps kept at each end of a trace. A trace exists to be read, and the middle of a long run
     * is not.
     */
    private static final int TRACE_HEAD = 40;

    /**
     * See {@link #TRACE_HEAD}.
     */
    private static final int TRACE_TAIL = 12;

    /**
     * How much tape to show either side of the head in a trace line.
     */
    private static final int TRACE_WINDOW = 24;

    /**
     * What happened when a machine was given an input.
     */
    public static final class Result
    {
        /**
         * One of accept, reject, stuck, failed, fell_off, nondeterministic, over_budget, timeout
         * or error.
         */
        public String outcome;

        /**
         * Why, in words, when the outcome needs explaining. Null when it does not.
         */
        public String reason;

        /**
         * How many steps were taken, counted the way the Step button counts them.
         */
        public long steps;

        /**
         * The tape when the run ended, trailing blanks trimmed.
         */
        public String finalTape = "";

        /**
         * Where the head finished.
         */
        public int head;

        /**
         * A readable trace, trimmed in the middle for a long run. Empty unless one was asked for.
         */
        public List<String> trace = new ArrayList<String>();

        /**
         * Whether the machine ended in a state the program calls accepting.
         * @return true if the outcome was an acceptance.
         */
        public boolean accepted()
        {
            return "accept".equals(outcome);
        }
    }

    /**
     * Not instantiable.
     */
    private Sandbox() { }

    /**
     * Run one input through a copy of a machine.
     * @param machine The machine to run. It is copied, and never modified.
     * @param input What to write on the tape before starting.
     * @param maxSteps How many steps to allow.
     * @param deadline When to give up, as a System.nanoTime value; pass Long.MAX_VALUE for none.
     * @param wantTrace Whether to record a trace.
     * @return What happened.
     */
    public static Result run(Machine machine, String input, long maxSteps, long deadline,
                             boolean wantTrace)
    {
        Result result = new Result();
        Machine copy = Machines.copy(machine);
        CA_Tape tape = new CA_Tape(normalise(input));
        Simulator sim = Machines.simulator(copy, tape);
        sim.setChooser(Simulator.REFUSE);

        boolean turing = !Doc.isAcceptor(copy);
        List<String> head = new ArrayList<String>();
        List<String> tail = new ArrayList<String>();
        long steps = 0;
        long checkedAt = 0;

        try
        {
            while (steps < maxSteps)
            {
                // Checking the clock is cheap but not free, and at twenty-five million steps a
                // second it would otherwise be most of what the loop does.
                if (steps - checkedAt >= 65536)
                {
                    checkedAt = steps;
                    if (System.nanoTime() > deadline)
                    {
                        return finish(result, "timeout", String.format(
                                    "gave up after %,d steps; the run was taking too long", steps),
                                steps, tape, head, tail);
                    }
                }

                // A Turing machine halts on arriving at its final state, whatever the head is
                // doing; whether that counts as success is a separate question about the head.
                State current = sim.getCurrentState();
                if (turing && current != null && current.isFinalState())
                {
                    if (tape.isParked())
                    {
                        return finish(result, "accept", null, steps, tape, head, tail);
                    }
                    return finish(result, "failed", String.format(
                                "reached the final state '%s' with the head at cell %d, not parked "
                              + "at cell 0. A run only succeeds with the head back at the start, so "
                              + "the machine needs a phase that walks home.",
                                current.getLabel(), tape.headLocation()),
                            steps, tape, head, tail);
                }

                if (wantTrace)
                {
                    record(head, tail, steps, sim, tape, turing);
                }

                sim.step();
                steps++;
            }
            return finish(result, "over_budget", String.format(
                        "still running after %,d steps", maxSteps), steps, tape, head, tail);
        }
        catch (NondeterminismException e)
        {
            return finish(result, "nondeterministic", e.getMessage(), steps + 1, tape, head, tail);
        }
        catch (ComputationCompletedException e)
        {
            // Only an acceptor gets here: it reached the end of its input. The message says which
            // way it went, and the head has already been sent back to the start.
            State current = sim.getCurrentState();
            boolean ok = current != null && current.isFinalState();
            return finish(result, ok? "accept" : "reject",
                    ok? null : String.format("the input ran out in state '%s', which is not a final state",
                            current == null? "?" : current.getLabel()),
                    steps + 1, tape, head, tail);
        }
        catch (ComputationFailedException e)
        {
            String message = e.getMessage() == null? "" : e.getMessage();
            if (message.contains("fell off"))
            {
                return finish(result, "fell_off",
                        "the read/write head moved left from cell 0 and fell off the tape",
                        steps + 1, tape, head, tail);
            }
            return finish(result, "stuck", stuckReason(sim, tape, turing, message),
                    steps + 1, tape, head, tail);
        }
        catch (Throwable t)
        {
            return finish(result, "error", t.getClass().getSimpleName()
                    + (t.getMessage() == null? "" : ": " + t.getMessage()),
                    steps, tape, head, tail);
        }
    }

    /**
     * Explain a machine stopping, naming the state and symbol rather than repeating the program's
     * own wording, which describes the halt and not its cause.
     * @param sim The simulator that stopped.
     * @param tape Its tape.
     * @param turing Whether this is a Turing machine.
     * @param fallback The program's own message.
     * @return A description of why the machine stopped.
     */
    private static String stuckReason(Simulator sim, Tape tape, boolean turing, String fallback)
    {
        State current = sim.getCurrentState();
        if (current == null)
        {
            return fallback.isEmpty()? "the machine could not start" : fallback;
        }
        char symbol = tape.read();
        if (turing)
        {
            return String.format("no transition from '%s' on '%c'", current.getLabel(), symbol);
        }
        return String.format(
                "no transition from '%s' on '%c' -- Tuatara reports \"Undefined transition\". An "
              + "acceptor must have a transition for every alphabet symbol in every state.",
                current.getLabel(), symbol);
    }

    private static Result finish(Result result, String outcome, String reason, long steps,
                                 Tape tape, List<String> head, List<String> tail)
    {
        result.outcome = outcome;
        result.reason = reason;
        result.steps = steps;
        result.head = tape.headLocation();
        result.finalTape = tape.getPartialString(0, tape.getLength());
        result.trace = joinTrace(head, tail);
        return result;
    }

    private static void record(List<String> head, List<String> tail, long steps,
                               Simulator sim, Tape tape, boolean turing)
    {
        State current = sim.getCurrentState();
        if (current == null)
        {
            // The machine has not started. The first step only enters the start state, so a line
            // here would say nothing the next one does not.
            return;
        }
        String line = String.format("%d: %s  %s%s", steps, current.getLabel(), window(tape),
                turing? nextAction(sim) : "");
        if (head.size() < TRACE_HEAD)
        {
            head.add(line);
            return;
        }
        tail.add(line);
        if (tail.size() > TRACE_TAIL)
        {
            tail.remove(0);
        }
    }

    /**
     * Say what the machine is about to do, when there is one obvious answer.
     * @param sim The simulator.
     * @return A rendering of the next action, or an empty string.
     */
    private static String nextAction(Simulator sim)
    {
        List<?> next = sim.getNextTransitions();
        if (next.size() != 1)
        {
            return "";
        }
        return "  -> " + Doc.writeAction(((Transition)next.get(0)).getAction());
    }

    /**
     * The tape around the head, with the cell under the head marked.
     * @param tape The tape to render.
     * @return A window onto it.
     */
    private static String window(Tape tape)
    {
        int at = tape.headLocation();
        int from = Math.max(0, at - TRACE_WINDOW / 2);
        int length = Math.max(TRACE_WINDOW, Math.min(tape.getLength(), at + TRACE_WINDOW / 2) - from + 1);
        String text = tape.getPartialString(from, length);
        int mark = at - from;
        StringBuilder sb = new StringBuilder();
        if (from > 0)
        {
            sb.append("...");
        }
        sb.append(text, 0, Math.min(mark, text.length()));
        sb.append('[').append(mark < text.length()? text.charAt(mark) : Tape.BLANK_SYMBOL).append(']');
        if (mark + 1 < text.length())
        {
            sb.append(text, mark + 1, text.length());
        }
        return sb.toString();
    }

    private static List<String> joinTrace(List<String> head, List<String> tail)
    {
        List<String> result = new ArrayList<String>(head);
        if (!tail.isEmpty())
        {
            result.add("... (the middle of the run is not shown)");
            result.addAll(tail);
        }
        return result;
    }

    /**
     * Fold an input the way the alphabet does, so a lower case tape behaves as it reads.
     * @param input The input to fold.
     * @return The input in the form the machine will see.
     */
    public static String normalise(String input)
    {
        if (input == null)
        {
            return "";
        }
        StringBuilder sb = new StringBuilder(input.length());
        for (int i = 0; i < input.length(); i++)
        {
            sb.append(Doc.normalise(input.charAt(i)));
        }
        return sb.toString();
    }
}
