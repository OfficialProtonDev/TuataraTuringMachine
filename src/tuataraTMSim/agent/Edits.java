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
import tuataraTMSim.MachineGraphicsPanel;
import tuataraTMSim.commands.TMCommand;
import tuataraTMSim.machine.*;
import tuataraTMSim.machine.DFSA.*;
import tuataraTMSim.machine.TM.*;

/**
 * Changing a machine that already exists.
 *
 * Two properties matter more than anything else here. The whole batch either applies or none of it
 * does, so a machine is never left half edited by a list of operations that turned out to be
 * wrong. And the whole batch is a single entry on the undo stack, so one Ctrl+Z puts the user back
 * where they were -- which is what makes it reasonable for an agent to edit somebody's work at all.
 *
 * The operations run against the live model rather than rebuilding it from a document. Rebuilding
 * would be less code and would quietly throw away the things a document does not carry: a state's
 * submachine, and the curve on every arrow the user has bent by hand.
 */
public final class Edits
{
    /**
     * What a batch of edits did.
     */
    public static final class Outcome
    {
        /**
         * Everything wrong with the batch. When this is non-empty nothing was changed.
         */
        public final List<String> errors = new ArrayList<String>();

        /**
         * How many operations were applied.
         */
        public int applied;

        /**
         * States that had no position and were given one, as JSON objects.
         */
        public final List<Object> placed = new ArrayList<Object>();

        /**
         * What this appears as in the undo menu.
         */
        public String undoLabel;

        /**
         * Whether the batch applied.
         * @return true if the machine was changed.
         */
        public boolean ok()
        {
            return errors.isEmpty();
        }
    }

    /**
     * Not instantiable.
     */
    private Edits() { }

    /**
     * Apply a list of operations to a machine.
     * @param machine The machine to change.
     * @param panel The panel showing it, or null for a machine nobody is looking at. When given,
     *              the batch lands on that panel's undo stack as one entry.
     * @param ops The operations, as documents.
     * @param label What to call the batch in the undo menu.
     * @return What happened. Check {@link Outcome#ok} before believing anything changed.
     */
    public static Outcome apply(Machine machine, MachineGraphicsPanel panel, List<Object> ops,
                                String label)
    {
        Outcome outcome = new Outcome();
        outcome.undoLabel = "Claude: " + (label == null || label.trim().isEmpty()
                ? "edit machine" : label.trim());

        List<TMCommand> done = new ArrayList<TMCommand>();
        for (Object op : ops)
        {
            int before = outcome.errors.size();
            List<TMCommand> commands = build(machine, panel, op, outcome.errors);
            if (outcome.errors.size() != before)
            {
                rollBack(done);
                return outcome;
            }
            for (TMCommand command : commands)
            {
                command.doCommand();
                done.add(command);
            }
            outcome.applied++;
        }

        // Anything added still needs somewhere to sit. Placing happens once, after every operation,
        // so a state added early and connected later lands next to what it connects to.
        List<State> placed = Layout.placeNew(machine);
        for (State state : placed)
        {
            outcome.placed.add(Json.object("name", state.getLabel(),
                        "x", Long.valueOf(state.getX()), "y", Long.valueOf(state.getY())));
        }

        if (panel != null)
        {
            panel.addCommand(new Batch(done, outcome.undoLabel));
            panel.setModifiedSinceSave(true);
        }
        return outcome;
    }

    private static void rollBack(List<TMCommand> done)
    {
        for (int i = done.size() - 1; i >= 0; i--)
        {
            done.get(i).undoCommand();
        }
    }

    /* ---------------------------------------------------------------- *
     * Turning one operation into commands
     * ---------------------------------------------------------------- */

    private static List<TMCommand> build(Machine machine, MachineGraphicsPanel panel, Object op,
                                         List<String> errors)
    {
        List<TMCommand> result = new ArrayList<TMCommand>();
        String kind = Json.str(op, "op", "").trim().toLowerCase();
        String name = Json.str(op, "name", null);

        if (kind.equals("add_state"))
        {
            if (name == null || name.trim().isEmpty())
            {
                errors.add("add_state needs a name");
                return result;
            }
            if (Machines.state(machine, name) != null)
            {
                errors.add("a state called \"" + name + "\" already exists");
                return result;
            }
            boolean start = Json.bool(op, "start", false);
            boolean fin = Json.bool(op, "final", false);
            State state = Doc.isAcceptor(machine)
                ? (State)new DFSA_State(name, start, fin, Layout.UNPLACED, Layout.UNPLACED)
                : (State)new TM_State(name, start, fin, Layout.UNPLACED, Layout.UNPLACED);
            if (Json.has(op, "x") && Json.has(op, "y"))
            {
                state.setPosition((int)Json.num(op, "x", 0), (int)Json.num(op, "y", 0));
            }
            result.add(new AddState(machine, panel, state));
            return result;
        }

        if (kind.equals("remove_state"))
        {
            State state = require(machine, name, errors, "remove_state");
            if (state == null)
            {
                return result;
            }
            // Deleting a state deletes the arrows touching it. Doing that as separate commands
            // keeps undo exact: the arrows come back with the state.
            for (Object o : new ArrayList<Object>(machine.getTransitions()))
            {
                Transition t = (Transition)o;
                if (t.getFromState() == state || t.getToState() == state)
                {
                    result.add(new RemoveTransition(machine, t));
                }
            }
            result.add(new RemoveState(machine, panel, state));
            return result;
        }

        if (kind.equals("rename_state"))
        {
            State state = require(machine, name, errors, "rename_state");
            if (state == null)
            {
                return result;
            }
            String to = Json.str(op, "to", null);
            if (to == null || to.trim().isEmpty())
            {
                errors.add("rename_state needs a new name in \"to\"");
                return result;
            }
            if (Machines.state(machine, to) != null)
            {
                errors.add("a state called \"" + to + "\" already exists");
                return result;
            }
            result.add(new Rename(panel, state, to));
            return result;
        }

        if (kind.equals("set_start") || kind.equals("set_final"))
        {
            boolean startKind = kind.equals("set_start");
            State state = require(machine, name, errors, kind);
            if (state == null)
            {
                return result;
            }
            boolean value = Json.bool(op, "value", true);
            if (value)
            {
                // Both roles are meant to be unique, so setting one clears whatever held it. The
                // program's validation would otherwise reject a machine the agent thought it had
                // just fixed.
                for (Object o : machine.getStates())
                {
                    State other = (State)o;
                    if (other != state && (startKind? other.isStartState() : other.isFinalState()))
                    {
                        result.add(new SetRole(other, startKind, false));
                    }
                }
            }
            if ((startKind? state.isStartState() : state.isFinalState()) != value)
            {
                result.add(new SetRole(state, startKind, value));
            }
            return result;
        }

        if (kind.equals("set_transition") || kind.equals("add_transition"))
        {
            String from = Json.str(op, "from", null);
            String to = Json.str(op, "to", null);
            State fromState = require(machine, from, errors, kind + " (\"from\")");
            State toState = require(machine, to, errors, kind + " (\"to\")");
            if (fromState == null || toState == null)
            {
                return result;
            }
            char on = Doc.readSymbol(Json.str(op, "on", null), errors,
                    String.format("the transition %s -> %s", from, to));
            if (on == Machine.UNDEFINED_SYMBOL)
            {
                return result;
            }
            Doc.Action action = Doc.isAcceptor(machine)
                ? new Doc.Action(1, Machine.EMPTY_ACTION_SYMBOL)
                : Doc.readAction(Json.str(op, "action", null), errors,
                        String.format("the transition %s -> %s", from, to));
            if (action.output == Machine.UNDEFINED_SYMBOL && action.direction == 0)
            {
                return result;
            }

            // A state may have only one transition per symbol, so this is an upsert: the existing
            // one is redirected and reactioned rather than joined by a second.
            Transition existing = Machines.transition(machine, fromState.getLabel(), null, on);
            if (existing != null)
            {
                result.add(new RemoveTransition(machine, existing));
            }
            result.add(new AddTransition(machine, newTransition(machine, fromState, toState, on, action)));
            return result;
        }

        if (kind.equals("remove_transition"))
        {
            String from = Json.str(op, "from", null);
            String to = Json.str(op, "to", null);
            State fromState = require(machine, from, errors, "remove_transition (\"from\")");
            if (fromState == null)
            {
                return result;
            }
            char on = Doc.readSymbol(Json.str(op, "on", null), errors,
                    String.format("the transition leaving %s", from));
            if (on == Machine.UNDEFINED_SYMBOL)
            {
                return result;
            }
            Transition existing = Machines.transition(machine, fromState.getLabel(), to, on);
            if (existing == null)
            {
                errors.add(String.format("there is no transition leaving %s on '%c'%s",
                            from, on, to == null? "" : " and arriving at " + to));
                return result;
            }
            result.add(new RemoveTransition(machine, existing));
            return result;
        }

        if (kind.equals("move_state"))
        {
            State state = require(machine, name, errors, "move_state");
            if (state == null)
            {
                return result;
            }
            if (!Json.has(op, "x") || !Json.has(op, "y"))
            {
                errors.add("move_state needs both x and y");
                return result;
            }
            result.add(new Move(machine, state, (int)Json.num(op, "x", 0), (int)Json.num(op, "y", 0)));
            return result;
        }

        if (kind.equals("set_alphabet"))
        {
            Alphabet after = new Alphabet();
            after.setAlphabetical(false);
            after.setDigits(false);
            after.setBlank(true);
            for (Object symbol : Json.arr(op, "symbols"))
            {
                String s = String.valueOf(symbol).trim();
                if (s.length() != 1)
                {
                    errors.add("\"" + s + "\" is not a single symbol");
                    continue;
                }
                char c = Doc.normalise(s.charAt(0));
                if (!Doc.isAlphabetSymbol(c))
                {
                    errors.add(String.format(
                                "'%c' cannot be in a Tuatara alphabet -- only 0-9, A-Z and the blank _", c));
                    continue;
                }
                after.setSymbol(c, true);
            }
            if (!errors.isEmpty())
            {
                return result;
            }
            result.add(new SetAlphabet(machine, machine.getAlphabet(), after));
            return result;
        }

        errors.add("\"" + Json.str(op, "op", "") + "\" is not an operation. Use one of: add_state, "
                 + "remove_state, rename_state, set_start, set_final, set_transition, "
                 + "remove_transition, move_state, set_alphabet.");
        return result;
    }

    private static Transition newTransition(Machine machine, State from, State to, char on,
                                            Doc.Action action)
    {
        if (Doc.isAcceptor(machine))
        {
            return new DFSA_Transition((DFSA_State)from, (DFSA_State)to, new DFSA_Action(on));
        }
        return new TM_Transition((TM_State)from, (TM_State)to,
                new TM_Action(action.direction, on, action.output));
    }

    private static State require(Machine machine, String name, List<String> errors, String what)
    {
        if (name == null || name.trim().isEmpty())
        {
            errors.add(what + " needs a state name");
            return null;
        }
        State state = Machines.state(machine, name);
        if (state == null)
        {
            StringBuilder known = new StringBuilder();
            for (Object o : machine.getStates())
            {
                known.append(known.length() == 0? "" : ", ").append(((State)o).getLabel());
                if (known.length() > 200)
                {
                    known.append(", ...");
                    break;
                }
            }
            errors.add(String.format("there is no state called \"%s\". States in this machine: %s",
                        name, known.length() == 0? "(none)" : known.toString()));
        }
        return state;
    }

    /* ---------------------------------------------------------------- *
     * The commands
     *
     * These do the same work as the ones in tuataraTMSim.commands, but reach the machine directly
     * instead of through a panel, because an agent's drafts have no panel to reach through. The
     * panel is still passed when there is one, so the editor's record of which names are taken
     * stays correct.
     * ---------------------------------------------------------------- */

    /**
     * A whole batch of edits, presented to the undo stack as one step.
     */
    private static final class Batch implements TMCommand
    {
        private final List<TMCommand> m_commands;
        private final String m_name;

        Batch(List<TMCommand> commands, String name)
        {
            m_commands = new ArrayList<TMCommand>(commands);
            m_name = name;
        }

        public void doCommand()
        {
            for (TMCommand command : m_commands)
            {
                command.doCommand();
            }
        }

        public void undoCommand()
        {
            for (int i = m_commands.size() - 1; i >= 0; i--)
            {
                m_commands.get(i).undoCommand();
            }
        }

        public String getName()
        {
            return m_name;
        }
    }

    private static final class AddState implements TMCommand
    {
        private final Machine m_machine;
        private final MachineGraphicsPanel m_panel;
        private final State m_state;

        AddState(Machine machine, MachineGraphicsPanel panel, State state)
        {
            m_machine = machine;
            m_panel = panel;
            m_state = state;
        }

        @SuppressWarnings("unchecked")
        public void doCommand()
        {
            m_machine.addState(m_state);
            if (m_panel != null)
            {
                m_panel.addLabelToDictionary(m_state.getLabel());
            }
        }

        @SuppressWarnings("unchecked")
        public void undoCommand()
        {
            m_machine.deleteState(m_state);
            if (m_panel != null)
            {
                m_panel.removeLabelFromDictionary(m_state.getLabel());
            }
        }

        public String getName()
        {
            return "Add State";
        }
    }

    private static final class RemoveState implements TMCommand
    {
        private final Machine m_machine;
        private final MachineGraphicsPanel m_panel;
        private final State m_state;

        RemoveState(Machine machine, MachineGraphicsPanel panel, State state)
        {
            m_machine = machine;
            m_panel = panel;
            m_state = state;
        }

        @SuppressWarnings("unchecked")
        public void doCommand()
        {
            m_machine.deleteState(m_state);
            if (m_panel != null)
            {
                m_panel.removeLabelFromDictionary(m_state.getLabel());
                if (m_panel.getSimulator().getCurrentState() == m_state)
                {
                    m_panel.getSimulator().resetMachine();
                }
            }
        }

        @SuppressWarnings("unchecked")
        public void undoCommand()
        {
            m_machine.addState(m_state);
            if (m_panel != null)
            {
                m_panel.addLabelToDictionary(m_state.getLabel());
            }
        }

        public String getName()
        {
            return "Delete State";
        }
    }

    private static final class Rename implements TMCommand
    {
        private final MachineGraphicsPanel m_panel;
        private final State m_state;
        private final String m_before;
        private final String m_after;

        Rename(MachineGraphicsPanel panel, State state, String after)
        {
            m_panel = panel;
            m_state = state;
            m_before = state.getLabel();
            m_after = after;
        }

        public void doCommand()
        {
            m_state.setLabel(m_after);
            if (m_panel != null)
            {
                m_panel.removeLabelFromDictionary(m_before);
                m_panel.addLabelToDictionary(m_after);
            }
        }

        public void undoCommand()
        {
            m_state.setLabel(m_before);
            if (m_panel != null)
            {
                m_panel.removeLabelFromDictionary(m_after);
                m_panel.addLabelToDictionary(m_before);
            }
        }

        public String getName()
        {
            return "Rename State";
        }
    }

    private static final class SetRole implements TMCommand
    {
        private final State m_state;
        private final boolean m_start;
        private final boolean m_value;

        SetRole(State state, boolean start, boolean value)
        {
            m_state = state;
            m_start = start;
            m_value = value;
        }

        public void doCommand()
        {
            set(m_value);
        }

        public void undoCommand()
        {
            set(!m_value);
        }

        private void set(boolean value)
        {
            if (m_start)
            {
                m_state.setStartState(value);
            }
            else
            {
                m_state.setFinalState(value);
            }
        }

        public String getName()
        {
            return m_start? "Toggle Start State" : "Toggle Final State";
        }
    }

    private static final class AddTransition implements TMCommand
    {
        private final Machine m_machine;
        private final Transition m_transition;

        AddTransition(Machine machine, Transition transition)
        {
            m_machine = machine;
            m_transition = transition;
        }

        @SuppressWarnings("unchecked")
        public void doCommand()
        {
            m_machine.addTransition(m_transition);
        }

        @SuppressWarnings("unchecked")
        public void undoCommand()
        {
            m_machine.deleteTransition(m_transition);
        }

        public String getName()
        {
            return "Add Transition";
        }
    }

    private static final class RemoveTransition implements TMCommand
    {
        private final Machine m_machine;
        private final Transition m_transition;

        RemoveTransition(Machine machine, Transition transition)
        {
            m_machine = machine;
            m_transition = transition;
        }

        @SuppressWarnings("unchecked")
        public void doCommand()
        {
            m_machine.deleteTransition(m_transition);
        }

        @SuppressWarnings("unchecked")
        public void undoCommand()
        {
            m_machine.addTransition(m_transition);
        }

        public String getName()
        {
            return "Delete Transition";
        }
    }

    private static final class Move implements TMCommand
    {
        private final Machine m_machine;
        private final State m_state;
        private final int m_beforeX;
        private final int m_beforeY;
        private final int m_afterX;
        private final int m_afterY;

        Move(Machine machine, State state, int x, int y)
        {
            m_machine = machine;
            m_state = state;
            m_beforeX = state.getX();
            m_beforeY = state.getY();
            m_afterX = x;
            m_afterY = y;
        }

        public void doCommand()
        {
            move(m_afterX, m_afterY);
        }

        public void undoCommand()
        {
            move(m_beforeX, m_beforeY);
        }

        private void move(int x, int y)
        {
            m_state.setPosition(x, y);
            // The arrows at either end have to follow, or they end up pointing at where the state
            // used to be.
            for (Object o : m_machine.getTransitions())
            {
                Transition t = (Transition)o;
                if (t.getFromState() == m_state || t.getToState() == m_state)
                {
                    Layout.route(m_machine, t);
                }
            }
        }

        public String getName()
        {
            return "Move State";
        }
    }

    private static final class SetAlphabet implements TMCommand
    {
        private final Machine m_machine;
        private final Alphabet m_before;
        private final Alphabet m_after;

        SetAlphabet(Machine machine, Alphabet before, Alphabet after)
        {
            m_machine = machine;
            m_before = before;
            m_after = after;
        }

        public void doCommand()
        {
            m_machine.setAlphabet(m_after);
        }

        public void undoCommand()
        {
            m_machine.setAlphabet(m_before);
        }

        public String getName()
        {
            return "Configure Alphabet";
        }
    }
}
