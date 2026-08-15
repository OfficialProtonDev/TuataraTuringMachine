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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import tuataraTMSim.machine.*;

/**
 * What is wrong with a machine.
 *
 * Two separate things, kept separate on purpose. The program's own Validate answer is the one the
 * user gets from the Machine menu and is reported word for word. The structural checks below are
 * extra: they catch things that are legal but almost certainly not meant, like a state nothing can
 * reach. Blurring the two would have an agent telling a user their machine fails validation when
 * the program says it passes.
 */
public final class Diagnosis
{
    /**
     * Not instantiable.
     */
    private Diagnosis() { }

    /**
     * The program's own verdict, exactly as the Validate menu item would give it.
     * @param machine The machine to check.
     * @return null if the machine is deterministic, otherwise the program's description of why not.
     */
    public static String verdict(Machine machine)
    {
        return machine.isDeterministic();
    }

    /**
     * States the start state cannot reach.
     * @param machine The machine to check.
     * @return The labels of unreachable states.
     */
    public static List<String> unreachable(Machine machine)
    {
        Set<State> seen = new HashSet<State>();
        State start = null;
        for (Object o : machine.getStates())
        {
            if (((State)o).isStartState())
            {
                start = (State)o;
                break;
            }
        }
        if (start != null)
        {
            List<State> queue = new ArrayList<State>();
            queue.add(start);
            seen.add(start);
            for (int i = 0; i < queue.size(); i++)
            {
                for (Object o : queue.get(i).getTransitions())
                {
                    State to = (State)((Transition)o).getToState();
                    if (seen.add(to))
                    {
                        queue.add(to);
                    }
                }
            }
        }
        List<String> result = new ArrayList<String>();
        for (Object o : machine.getStates())
        {
            if (!seen.contains(o))
            {
                result.add(((State)o).getLabel());
            }
        }
        return result;
    }

    /**
     * States from which no final state can ever be reached. A machine that ends up in one of these
     * can no longer succeed, whatever it does next.
     * @param machine The machine to check.
     * @return The labels of those states.
     */
    public static List<String> cannotReachFinal(Machine machine)
    {
        Set<State> good = new HashSet<State>();
        for (Object o : machine.getStates())
        {
            if (((State)o).isFinalState())
            {
                good.add((State)o);
            }
        }
        boolean changed = true;
        while (changed)
        {
            changed = false;
            for (Object o : machine.getTransitions())
            {
                Transition t = (Transition)o;
                if (good.contains(t.getToState()) && good.add((State)t.getFromState()))
                {
                    changed = true;
                }
            }
        }
        List<String> result = new ArrayList<String>();
        for (Object o : machine.getStates())
        {
            if (!good.contains(o))
            {
                result.add(((State)o).getLabel());
            }
        }
        return result;
    }

    /**
     * Symbols a state has no transition for, and so would stop on.
     *
     * What counts depends on the kind of machine. An acceptor never reads the blank as input -- it
     * is how the input ends -- so reporting it there is noise. A Turing machine does read it, and a
     * state with an "anything else" transition covers everything by definition.
     * @param machine The machine to check.
     * @return One entry per state with gaps, mapping "state" to its label and "symbols" to a list.
     */
    public static List<Object> missingSymbols(Machine machine)
    {
        boolean acceptor = Doc.isAcceptor(machine);
        List<Object> result = new ArrayList<Object>();

        List<Character> alphabet = new ArrayList<Character>();
        for (char c : machine.getAlphabet().getSymbols())
        {
            alphabet.add(Character.valueOf(c));
        }
        if (!acceptor)
        {
            alphabet.add(Character.valueOf(Tape.BLANK_SYMBOL));
        }

        for (Object o : machine.getStates())
        {
            State state = (State)o;
            if (state.isFinalState() && !acceptor)
            {
                // Nothing may leave a Turing machine's final state, so gaps there are the point.
                continue;
            }
            Set<Character> covered = new HashSet<Character>();
            boolean catchAll = false;
            for (Object p : state.getTransitions())
            {
                char on = ((Transition)p).getAction().getInputChar();
                if (on == Machine.OTHERWISE_SYMBOL)
                {
                    catchAll = true;
                }
                covered.add(Character.valueOf(on));
            }
            if (catchAll)
            {
                continue;
            }
            List<Object> gaps = new ArrayList<Object>();
            for (Character c : alphabet)
            {
                if (!covered.contains(c))
                {
                    gaps.add(String.valueOf(c.charValue()));
                }
            }
            if (!gaps.isEmpty())
            {
                result.add(Json.object("state", state.getLabel(), "symbols", gaps));
            }
        }
        return result;
    }

    /**
     * The structural checks, gathered.
     * @param machine The machine to check.
     * @return A JSON object with one member per check.
     */
    public static Map<String, Object> checks(Machine machine)
    {
        return Json.object(
                "unreachable_states", unreachable(machine),
                "cannot_reach_final", cannotReachFinal(machine),
                "missing_symbols", missingSymbols(machine));
    }

    /**
     * Everything worth saying about a machine, as prose, for an agent to pass on.
     * @param machine The machine to describe.
     * @return Readable notes; empty when there is nothing to say.
     */
    public static List<Object> notes(Machine machine)
    {
        List<Object> notes = new ArrayList<Object>();

        // Symbols a transition uses that the alphabet does not hold. The program's own validation
        // reports this, but only one instance and without saying what to do about it, and an agent
        // that has just added a transition wants the fix rather than the diagnosis.
        Set<Character> outside = new java.util.LinkedHashSet<Character>();
        Alphabet alphabet = machine.getAlphabet();
        for (Object o : machine.getTransitions())
        {
            Transition t = (Transition)o;
            char in = t.getAction().getInputChar();
            if (in != Machine.OTHERWISE_SYMBOL && in != Machine.EMPTY_INPUT_SYMBOL
                    && in != Machine.UNDEFINED_SYMBOL && !alphabet.containsSymbol(in))
            {
                outside.add(Character.valueOf(in));
            }
            char out = t.getAction().getOutputChar();
            if (t.getAction().getDirection() == 0 && out != Machine.EMPTY_ACTION_SYMBOL
                    && out != Machine.UNDEFINED_SYMBOL && !alphabet.containsSymbol(out))
            {
                outside.add(Character.valueOf(out));
            }
        }
        if (!outside.isEmpty())
        {
            StringBuilder symbols = new StringBuilder();
            StringBuilder wanted = new StringBuilder();
            for (char c : alphabet.getSymbols())
            {
                wanted.append(wanted.length() == 0? "" : ", ").append('"').append(c).append('"');
            }
            for (Character c : outside)
            {
                symbols.append(symbols.length() == 0? "" : ", ").append('\'').append(c).append('\'');
                wanted.append(wanted.length() == 0? "" : ", ").append('"').append(c).append('"');
            }
            notes.add(String.format(
                        "%s used by a transition but not in the alphabet, so the machine will not "
                      + "validate. Add %s with the set_alphabet operation: {\"op\": \"set_alphabet\", "
                      + "\"symbols\": [%s]}",
                        outside.size() == 1? symbols + " is" : symbols + " are",
                        outside.size() == 1? "it" : "them", wanted));
        }
        for (Object entry : missingSymbols(machine))
        {
            StringBuilder symbols = new StringBuilder();
            for (Object symbol : Json.arr(entry, "symbols"))
            {
                symbols.append(symbols.length() == 0? "" : ", ").append('\'').append(symbol).append('\'');
            }
            notes.add(String.format(
                        "state '%s' has no transition for %s; a run reading one there will stop",
                        Json.str(entry, "state", "?"), symbols));
        }
        for (String label : unreachable(machine))
        {
            notes.add(String.format("state '%s' cannot be reached from the start state", label));
        }
        for (String label : cannotReachFinal(machine))
        {
            notes.add(String.format("state '%s' can never reach the final state", label));
        }
        return notes;
    }

    /**
     * The short form used wherever a tool returns a machine: does it validate, and what else is
     * worth knowing.
     * @param machine The machine to describe.
     * @return A JSON object.
     */
    public static Map<String, Object> summary(Machine machine)
    {
        String verdict = verdict(machine);
        List<Object> problems = new ArrayList<Object>();
        if (verdict != null)
        {
            problems.add(verdict);
        }
        return Json.object(
                "validates", Boolean.valueOf(verdict == null),
                "problems", problems,
                "notes", notes(machine));
    }
}
