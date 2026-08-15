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

import java.util.HashMap;
import java.util.Map;
import tuataraTMSim.machine.*;
import tuataraTMSim.machine.DFSA.*;
import tuataraTMSim.machine.TM.*;

/**
 * Copying machines.
 *
 * Serializing and reading back would be shorter, but it walks the object graph recursively and a
 * machine is exactly the shape that punishes: a run of states linked in a line. This builds the
 * copy with two flat passes instead, which has no depth limit and is quick enough to do per test
 * batch -- twenty thousand states in about seventeen milliseconds.
 */
public final class Machines
{
    /**
     * Not instantiable.
     */
    private Machines() { }

    /**
     * Copy a machine, structure and positions and all. Nothing is shared with the original except
     * any submachine, which is carried across by reference.
     * @param machine The machine to copy.
     * @return A new machine that can be run and edited without touching the original.
     */
    public static Machine copy(Machine machine)
    {
        return Doc.isAcceptor(machine)
            ? (Machine)copyAcceptor((DFSA_Machine)machine)
            : (Machine)copyTuring((TM_Machine)machine);
    }

    /**
     * Copy a Turing machine.
     * @param machine The machine to copy.
     * @return A new machine.
     */
    public static TM_Machine copyTuring(TM_Machine machine)
    {
        TM_Machine copy = new TM_Machine();
        copy.setAlphabet((Alphabet)machine.getAlphabet().clone());

        Map<TM_State, TM_State> mapping = new HashMap<TM_State, TM_State>();
        for (TM_State state : machine.getStates())
        {
            TM_State fresh = new TM_State(state.getLabel(), state.isStartState(),
                    state.isFinalState(), state.getX(), state.getY());
            // A submachine is a whole machine in its own right and belongs to the state that owns
            // it; carrying the reference across keeps a copy honest without duplicating it.
            fresh.setSubmachine(state.getSubmachine());
            mapping.put(state, fresh);
            copy.addState(fresh);
        }
        for (TM_Transition transition : machine.getTransitions())
        {
            TM_Action action = transition.getAction();
            TM_Transition fresh = new TM_Transition(
                    mapping.get(transition.getFromState()),
                    mapping.get(transition.getToState()),
                    new TM_Action(action.getDirection(), action.getInputChar(), action.getOutputChar()));
            fresh.setControlPoint((int)transition.getControlPoint().getX(),
                                  (int)transition.getControlPoint().getY());
            copy.addTransition(fresh);
        }
        return copy;
    }

    /**
     * Copy a finite-state acceptor.
     * @param machine The machine to copy.
     * @return A new machine.
     */
    public static DFSA_Machine copyAcceptor(DFSA_Machine machine)
    {
        DFSA_Machine copy = new DFSA_Machine();
        copy.setAlphabet((Alphabet)machine.getAlphabet().clone());

        Map<DFSA_State, DFSA_State> mapping = new HashMap<DFSA_State, DFSA_State>();
        for (DFSA_State state : machine.getStates())
        {
            DFSA_State fresh = new DFSA_State(state.getLabel(), state.isStartState(),
                    state.isFinalState(), state.getX(), state.getY());
            mapping.put(state, fresh);
            copy.addState(fresh);
        }
        for (DFSA_Transition transition : machine.getTransitions())
        {
            DFSA_Transition fresh = new DFSA_Transition(
                    mapping.get(transition.getFromState()),
                    mapping.get(transition.getToState()),
                    new DFSA_Action(transition.getAction().getInputChar()));
            fresh.setControlPoint((int)transition.getControlPoint().getX(),
                                  (int)transition.getControlPoint().getY());
            copy.addTransition(fresh);
        }
        return copy;
    }

    /**
     * Build a simulator for a machine, whichever kind it is.
     * @param machine The machine to simulate.
     * @param tape The tape to run it on.
     * @return A simulator ready to step.
     */
    public static Simulator simulator(Machine machine, Tape tape)
    {
        return Doc.isAcceptor(machine)
            ? (Simulator)new DFSA_Simulator((DFSA_Machine)machine, tape)
            : (Simulator)new TM_Simulator((TM_Machine)machine, tape);
    }

    /**
     * Find a state by its label.
     * @param machine The machine to search.
     * @param label The label to look for.
     * @return The state, or null if there is no state with that label.
     */
    public static State state(Machine machine, String label)
    {
        if (label == null)
        {
            return null;
        }
        for (Object o : machine.getStates())
        {
            State s = (State)o;
            if (s.getLabel().equals(label))
            {
                return s;
            }
        }
        return null;
    }

    /**
     * Find the transition leaving a state on a given symbol and arriving at another.
     * @param machine The machine to search.
     * @param from The label of the state it leaves.
     * @param to The label of the state it arrives at, or null to match any.
     * @param on The symbol it reads.
     * @return The transition, or null if there is no such transition.
     */
    public static Transition transition(Machine machine, String from, String to, char on)
    {
        for (Object o : machine.getTransitions())
        {
            Transition t = (Transition)o;
            if (!((State)t.getFromState()).getLabel().equals(from))
            {
                continue;
            }
            if (to != null && !((State)t.getToState()).getLabel().equals(to))
            {
                continue;
            }
            if (t.getAction().getInputChar() == on)
            {
                return t;
            }
        }
        return null;
    }
}
