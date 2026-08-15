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

package tuataraTMSim.exceptions;

/**
 * Thrown when a machine reaches a point with more than one way forward and nobody is available to
 * choose. Interactively the user is asked; a run with no user behind it stops here instead, so
 * that a machine which needs a decision is reported as such rather than silently taking one path.
 */
public class NondeterminismException extends ComputationFailedException
{
    /**
     * Creates an instance of NondeterminismException.
     */
    public NondeterminismException() { }

    /**
     * Creates an instance of NondeterminismException.
     * @param msg The message associated with this exception.
     */
    public NondeterminismException(String msg) { super(msg); }
}
