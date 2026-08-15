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
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import tuataraTMSim.machine.*;
import tuataraTMSim.machine.DFSA.*;
import tuataraTMSim.machine.TM.*;

/**
 * Machines as documents an agent can read and write.
 *
 * A document is deliberately not a transcription of the model. It names states rather than
 * referring to them, folds the direction and the written symbol into one field -- because a
 * transition does one or the other and never both -- and leaves coordinates out unless they are
 * asked for. Reading a machine back is text rather than JSON: a five hundred state machine is
 * around forty-eight thousand tokens of JSON and five thousand of this.
 */
public final class Doc
{
    /**
     * The direction and symbol a transition may carry, unpacked from a document's action field.
     */
    public static final class Action
    {
        /**
         * -1 for left, 1 for right, 0 to write instead of moving.
         */
        public final int direction;

        /**
         * The symbol written when the direction is 0; the do-nothing symbol otherwise.
         */
        public final char output;

        /**
         * Creates an instance of Action.
         * @param direction Which way the head moves, or 0 to write.
         * @param output The symbol to write.
         */
        public Action(int direction, char output)
        {
            this.direction = direction;
            this.output = output;
        }
    }

    /**
     * Not instantiable.
     */
    private Doc() { }

    /* ---------------------------------------------------------------- *
     * Symbols
     * ---------------------------------------------------------------- */

    /**
     * Fold a symbol the way the alphabet does. Tuatara's alphabet holds upper case letters, digits
     * and the blank, and treats 'a' and 'A' as one symbol; saying so here means a document written
     * in lower case behaves the way it reads.
     * @param c The symbol to fold.
     * @return The symbol as the alphabet would store it.
     */
    public static char normalise(char c)
    {
        return Character.isLetter(c)? Character.toUpperCase(c) : c;
    }

    /**
     * Determine whether a symbol can go in an alphabet at all.
     * @param c The symbol to test.
     * @return true if the alphabet can hold it.
     */
    public static boolean isAlphabetSymbol(char c)
    {
        return (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9') || c == Tape.BLANK_SYMBOL;
    }

    /**
     * Read the symbol a transition matches on.
     * @param text The document's value for the symbol.
     * @param errors Collects a description of anything wrong.
     * @param where What to call this transition in an error message.
     * @return The symbol, or the undefined symbol if it could not be read.
     */
    public static char readSymbol(String text, List<String> errors, String where)
    {
        if (text == null || text.isEmpty())
        {
            errors.add(where + " does not say which symbol it reads");
            return Machine.UNDEFINED_SYMBOL;
        }
        String s = text.trim();
        if (s.equalsIgnoreCase("blank") || s.equals("_"))
        {
            return Tape.BLANK_SYMBOL;
        }
        if (s.equalsIgnoreCase("any") || s.equalsIgnoreCase("otherwise") || s.equals("?"))
        {
            return Machine.OTHERWISE_SYMBOL;
        }
        if (s.equalsIgnoreCase("lambda") || s.equals("λ"))
        {
            return Machine.EMPTY_INPUT_SYMBOL;
        }
        if (s.length() != 1)
        {
            errors.add(String.format("%s reads \"%s\", which is not a single symbol", where, text));
            return Machine.UNDEFINED_SYMBOL;
        }
        char c = normalise(s.charAt(0));
        if (!isAlphabetSymbol(c) && c != Machine.OTHERWISE_SYMBOL)
        {
            errors.add(String.format(
                        "%s reads '%c', which cannot be in a Tuatara alphabet -- only 0-9, A-Z and the blank _",
                        where, c));
            return Machine.UNDEFINED_SYMBOL;
        }
        return c;
    }

    /**
     * Read what a transition does: move the head, or write a symbol. A transition never does both,
     * so this is one field rather than two.
     * @param text The document's value for the action.
     * @param errors Collects a description of anything wrong.
     * @param where What to call this transition in an error message.
     * @return The action, never null.
     */
    public static Action readAction(String text, List<String> errors, String where)
    {
        if (text == null || text.trim().isEmpty())
        {
            errors.add(where + " does not say what it does. Use \"R\" or \"L\" to move the head, "
                     + "a single symbol to write it, or \"none\".");
            return new Action(0, Machine.UNDEFINED_SYMBOL);
        }
        String s = text.trim();
        String lower = s.toLowerCase();
        if (lower.equals("r") || lower.equals("right") || s.equals("→") || lower.equals("->"))
        {
            return new Action(1, Machine.EMPTY_ACTION_SYMBOL);
        }
        if (lower.equals("l") || lower.equals("left") || s.equals("←") || lower.equals("<-"))
        {
            return new Action(-1, Machine.EMPTY_ACTION_SYMBOL);
        }
        if (lower.equals("none") || lower.equals("nothing") || lower.equals("eps")
                || s.equals(String.valueOf(Machine.EMPTY_ACTION_SYMBOL)))
        {
            return new Action(0, Machine.EMPTY_ACTION_SYMBOL);
        }
        if (lower.equals("blank"))
        {
            return new Action(0, Tape.BLANK_SYMBOL);
        }
        if (s.length() != 1)
        {
            errors.add(String.format(
                        "%s has the action \"%s\". Use \"R\" or \"L\" to move the head, a single "
                      + "symbol to write it, or \"none\". A transition moves or writes, never both.",
                        where, text));
            return new Action(0, Machine.UNDEFINED_SYMBOL);
        }
        char c = normalise(s.charAt(0));
        if (!isAlphabetSymbol(c))
        {
            errors.add(String.format(
                        "%s writes '%c', which cannot be in a Tuatara alphabet -- only 0-9, A-Z and the blank _",
                        where, c));
            return new Action(0, Machine.UNDEFINED_SYMBOL);
        }
        return new Action(0, c);
    }

    /**
     * Render a symbol as a document would carry it.
     * @param c The symbol.
     * @return Its text form.
     */
    public static String writeSymbol(char c)
    {
        return String.valueOf(c);
    }

    /**
     * Render what a transition does, as a document would carry it.
     * @param action The action to render.
     * @return "R", "L", "none", or the symbol written.
     */
    public static String writeAction(PreAction action)
    {
        if (action == null)
        {
            return "none";
        }
        if (action.getDirection() < 0)
        {
            return "L";
        }
        if (action.getDirection() > 0)
        {
            return "R";
        }
        char out = action.getOutputChar();
        return out == Machine.EMPTY_ACTION_SYMBOL? "none" : String.valueOf(out);
    }

    /* ---------------------------------------------------------------- *
     * Document to machine
     * ---------------------------------------------------------------- */

    /**
     * Build a machine from a document. Nothing is placed; run it through {@link Layout} afterwards.
     * @param doc The document to read.
     * @param errors Collects a description of everything wrong with it. When this comes back
     *               non-empty the machine should be discarded rather than used.
     * @return A new machine, or null if the document could not be read at all.
     */
    public static Machine build(Object doc, List<String> errors)
    {
        String type = Json.str(doc, "type", "turing").trim().toLowerCase();
        boolean acceptor = type.equals("fsa") || type.equals("dfsa") || type.equals("acceptor");
        if (!acceptor && !type.equals("turing") && !type.equals("tm"))
        {
            errors.add("\"" + type + "\" is not a machine type. Use \"turing\" or \"fsa\".");
            return null;
        }

        List<Object> stateDocs = Json.arr(doc, "states");
        if (stateDocs.isEmpty())
        {
            errors.add("that machine has no states");
            return null;
        }

        // Gather the alphabet first: the transitions need it to be checked against, and deriving it
        // from what they use is the common case.
        Set<Character> symbols = new LinkedHashSet<Character>();
        boolean derive = !Json.has(doc, "alphabet");
        if (!derive)
        {
            for (Object symbol : Json.arr(doc, "alphabet"))
            {
                String s = String.valueOf(symbol).trim();
                if (s.length() != 1)
                {
                    errors.add("\"" + s + "\" in the alphabet is not a single symbol");
                    continue;
                }
                char c = normalise(s.charAt(0));
                if (!isAlphabetSymbol(c))
                {
                    errors.add(String.format(
                                "'%c' cannot be in a Tuatara alphabet -- only 0-9, A-Z and the blank _", c));
                    continue;
                }
                symbols.add(c);
            }
        }

        Machine machine = acceptor? (Machine)new DFSA_Machine() : (Machine)new TM_Machine();
        Map<String, State> byName = new LinkedHashMap<String, State>();

        for (Object stateDoc : stateDocs)
        {
            String name = Json.str(stateDoc, "name", "").trim();
            if (name.isEmpty())
            {
                errors.add("a state has no name");
                continue;
            }
            if (byName.containsKey(name))
            {
                errors.add("two states are both called \"" + name + "\"");
                continue;
            }
            boolean start = Json.bool(stateDoc, "start", false);
            boolean fin = Json.bool(stateDoc, "final", Json.bool(stateDoc, "accepting", false));
            int x = (int)Json.num(stateDoc, "x", Layout.UNPLACED);
            int y = (int)Json.num(stateDoc, "y", Layout.UNPLACED);
            State state = acceptor
                ? (State)new DFSA_State(name, start, fin, x, y)
                : (State)new TM_State(name, start, fin, x, y);
            byName.put(name, state);
            addState(machine, state);
        }

        for (Object transitionDoc : Json.arr(doc, "transitions"))
        {
            String from = Json.str(transitionDoc, "from", "").trim();
            String to = Json.str(transitionDoc, "to", "").trim();
            String where = String.format("the transition %s -> %s",
                    from.isEmpty()? "?" : from, to.isEmpty()? "?" : to);

            State fromState = byName.get(from);
            State toState = byName.get(to);
            if (fromState == null)
            {
                errors.add(where + " leaves \"" + from + "\", which is not a state in this machine");
                continue;
            }
            if (toState == null)
            {
                errors.add(where + " arrives at \"" + to + "\", which is not a state in this machine");
                continue;
            }

            char on = readSymbol(Json.str(transitionDoc, "on", null), errors, where);
            if (on == Machine.UNDEFINED_SYMBOL)
            {
                continue;
            }
            if (derive && on != Machine.OTHERWISE_SYMBOL && on != Machine.EMPTY_INPUT_SYMBOL)
            {
                symbols.add(on);
            }

            if (acceptor)
            {
                addTransition(machine, new DFSA_Transition(
                            (DFSA_State)fromState, (DFSA_State)toState, new DFSA_Action(on)));
            }
            else
            {
                Action action = readAction(Json.str(transitionDoc, "action", null), errors, where);
                if (action.output == Machine.UNDEFINED_SYMBOL && action.direction == 0)
                {
                    continue;
                }
                if (derive && action.direction == 0
                        && action.output != Machine.EMPTY_ACTION_SYMBOL)
                {
                    symbols.add(action.output);
                }
                addTransition(machine, new TM_Transition(
                            (TM_State)fromState, (TM_State)toState,
                            new TM_Action(action.direction, on, action.output)));
            }
        }

        Alphabet alphabet = new Alphabet();
        alphabet.setAlphabetical(false);
        alphabet.setDigits(false);
        alphabet.setBlank(true);
        for (Character c : symbols)
        {
            alphabet.setSymbol(c.charValue(), true);
        }
        machine.setAlphabet(alphabet);
        return machine;
    }

    /**
     * Add a state to a machine without the caller having to know which kind it is.
     * @param machine The machine to add to.
     * @param state The state to add.
     */
    @SuppressWarnings("unchecked")
    public static void addState(Machine machine, State state)
    {
        machine.addState(state);
    }

    /**
     * Add a transition to a machine without the caller having to know which kind it is.
     * @param machine The machine to add to.
     * @param transition The transition to add.
     */
    @SuppressWarnings("unchecked")
    public static void addTransition(Machine machine, Transition transition)
    {
        machine.addTransition(transition);
    }

    /* ---------------------------------------------------------------- *
     * Machine to document
     * ---------------------------------------------------------------- */

    /**
     * Determine whether a machine is an acceptor rather than a Turing machine.
     * @param machine The machine to test.
     * @return true if it is a finite-state acceptor.
     */
    public static boolean isAcceptor(Machine machine)
    {
        return machine instanceof DFSA_Machine;
    }

    /**
     * The word a document uses for a machine's kind.
     * @param machine The machine.
     * @return "fsa" or "turing".
     */
    public static String typeOf(Machine machine)
    {
        return isAcceptor(machine)? "fsa" : "turing";
    }

    /**
     * Render a machine as a document.
     * @param machine The machine to render.
     * @param name What the machine is called.
     * @param positions Whether to include each state's coordinates.
     * @return A JSON object.
     */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> toJson(Machine machine, String name, boolean positions)
    {
        Map<String, Object> doc = Json.object();
        doc.put("type", typeOf(machine));
        doc.put("name", name);

        List<Object> alphabet = new ArrayList<Object>();
        for (char c : machine.getAlphabet().getSymbols())
        {
            alphabet.add(String.valueOf(c));
        }
        alphabet.add(String.valueOf(Tape.BLANK_SYMBOL));
        doc.put("alphabet", alphabet);

        List<Object> states = new ArrayList<Object>();
        for (Object o : machine.getStates())
        {
            State state = (State)o;
            Map<String, Object> s = Json.object();
            s.put("name", state.getLabel());
            if (state.isStartState())
            {
                s.put("start", Boolean.TRUE);
            }
            if (state.isFinalState())
            {
                s.put("final", Boolean.TRUE);
            }
            if (positions)
            {
                s.put("x", Long.valueOf(state.getX()));
                s.put("y", Long.valueOf(state.getY()));
            }
            states.add(s);
        }
        doc.put("states", states);

        List<Object> transitions = new ArrayList<Object>();
        for (Object o : machine.getTransitions())
        {
            Transition t = (Transition)o;
            Map<String, Object> tr = Json.object();
            tr.put("from", ((State)t.getFromState()).getLabel());
            tr.put("to", ((State)t.getToState()).getLabel());
            tr.put("on", writeSymbol(t.getAction().getInputChar()));
            if (!isAcceptor(machine))
            {
                tr.put("action", writeAction(t.getAction()));
            }
            transitions.add(tr);
        }
        doc.put("transitions", transitions);
        return doc;
    }

    /**
     * Render a machine as compact text, grouped by the state each transition leaves.
     *
     * This is what an agent normally reads. The same machine as JSON is roughly ten times the size,
     * which for a machine of any interest is the difference between a readable answer and one that
     * fills the context it was meant to inform.
     * @param machine The machine to render.
     * @param name What the machine is called.
     * @return A readable rendering.
     */
    public static String toText(Machine machine, String name)
    {
        StringBuilder sb = new StringBuilder();
        Collection<?> states = machine.getStates();
        Collection<?> transitions = machine.getTransitions();

        sb.append(isAcceptor(machine)? "acceptor" : "turing machine");
        sb.append(" \"").append(name).append("\"   ");
        sb.append(states.size()).append(states.size() == 1? " state, " : " states, ");
        sb.append(transitions.size()).append(transitions.size() == 1? " transition" : " transitions");
        sb.append('\n');

        StringBuilder alpha = new StringBuilder();
        for (char c : machine.getAlphabet().getSymbols())
        {
            alpha.append(alpha.length() == 0? "" : " ").append(c);
        }
        alpha.append(alpha.length() == 0? "" : " ").append(Tape.BLANK_SYMBOL);
        sb.append("alphabet: ").append(alpha).append('\n');

        StringBuilder starts = new StringBuilder();
        StringBuilder finals = new StringBuilder();
        for (Object o : states)
        {
            State s = (State)o;
            if (s.isStartState())
            {
                starts.append(starts.length() == 0? "" : ", ").append(s.getLabel());
            }
            if (s.isFinalState())
            {
                finals.append(finals.length() == 0? "" : ", ").append(s.getLabel());
            }
        }
        sb.append("start: ").append(starts.length() == 0? "(none)" : starts);
        sb.append("    final: ").append(finals.length() == 0? "(none)" : finals);
        sb.append("\n\n");

        int width = 0;
        for (Object o : states)
        {
            width = Math.max(width, ((State)o).getLabel().length());
        }
        width = Math.min(width, 20);

        boolean acceptor = isAcceptor(machine);
        for (Object o : states)
        {
            State state = (State)o;
            sb.append(pad(state.getLabel(), width));
            Collection<?> out = state.getTransitions();
            if (out.isEmpty())
            {
                sb.append(state.isFinalState()
                        ? "  (final state; nothing may leave it)"
                        : "  (no transitions)");
            }
            else
            {
                for (Object p : out)
                {
                    Transition t = (Transition)p;
                    sb.append("  ");
                    sb.append(writeSymbol(t.getAction().getInputChar()));
                    if (!acceptor)
                    {
                        sb.append('/').append(writeAction(t.getAction()));
                    }
                    sb.append(" -> ").append(((State)t.getToState()).getLabel());
                }
            }
            sb.append('\n');
        }
        return sb.toString();
    }

    private static String pad(String s, int width)
    {
        StringBuilder sb = new StringBuilder(s);
        while (sb.length() < width)
        {
            sb.append(' ');
        }
        return sb.toString();
    }
}
