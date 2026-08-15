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
import java.util.Map;
import tuataraTMSim.machine.*;

/**
 * Checks for the agent core. No GUI and no protocol: this is the layer where the meaning lives, so
 * it is the layer worth testing directly.
 *
 * Run with: make test
 */
public class AgentTest
{
    private static int checks = 0;
    private static int failures = 0;
    private static String section = "";

    public static void main(String[] args)
    {
        json();
        documents();
        simulation();
        acceptors();
        editing();
        layout();
        scale();

        System.out.println();
        System.out.printf("%d checks, %d failed%n", checks, failures);
        System.exit(failures == 0? 0 : 1);
    }

    /* ---------------------------------------------------------------- *
     * Checking
     * ---------------------------------------------------------------- */

    private static void section(String name)
    {
        section = name;
        System.out.println();
        System.out.println("== " + name + " ==");
    }

    private static void ok(String what, boolean condition)
    {
        ok(what, condition, null);
    }

    private static void ok(String what, boolean condition, String detail)
    {
        checks++;
        if (!condition)
        {
            failures++;
        }
        System.out.printf("  %-64s %s%s%n", what, condition? "ok" : "FAILED",
                detail == null? "" : "   " + detail);
    }

    private static void same(String what, Object expected, Object actual)
    {
        boolean equal = expected == null? actual == null : expected.equals(actual);
        ok(what, equal, equal? null : "expected <" + expected + "> but was <" + actual + ">");
    }

    /* ---------------------------------------------------------------- *
     * JSON
     * ---------------------------------------------------------------- */

    private static void json()
    {
        section("JSON");

        same("a number stays whole", Long.valueOf(42), Json.parse("42"));
        same("a fraction stays a fraction", Double.valueOf(1.5), Json.parse("1.5"));
        same("a big step budget survives", Long.valueOf(20000000000L), Json.parse("20000000000"));
        same("escapes are read", "a\"b\\c\nd\te", Json.parse("\"a\\\"b\\\\c\\nd\\te\""));
        same("unicode escapes are read", "\u03BB", Json.parse("\"\\u03bb\""));
        same("greek passes through unescaped", "\"\u03BB\"", Json.write("\u03BB"));

        Object doc = Json.parse("{\"a\":[1,2,{\"b\":true}],\"c\":null}");
        same("nested arrays and objects", Long.valueOf(2), Json.arr(doc, "a").get(1));
        ok("null members are present but null", Json.has(doc, "c") && Json.member(doc, "c") == null);
        same("booleans nested in arrays", Boolean.TRUE, Json.member(Json.arr(doc, "a").get(2), "b"));

        String round = Json.write(Json.parse("{\"x\":[1,\"two\",false,null],\"y\":{\"z\":-3}}"));
        same("a round trip is stable", "{\"x\":[1,\"two\",false,null],\"y\":{\"z\":-3}}", round);

        ok("control characters are escaped", Json.write("a\u0001b").equals("\"a\\u0001b\""));
        same("a missing member falls back", "fallback", Json.str(doc, "nope", "fallback"));
        same("a missing number falls back", 7L, Json.num(doc, "nope", 7L));

        boolean threw = false;
        try
        {
            Json.parse("{\"a\":}");
        }
        catch (Json.SyntaxException e)
        {
            threw = true;
        }
        ok("bad text is rejected rather than guessed at", threw);

        threw = false;
        try
        {
            Json.parse("{} trailing");
        }
        catch (Json.SyntaxException e)
        {
            threw = true;
        }
        ok("text after the value is rejected", threw);
    }

    /* ---------------------------------------------------------------- *
     * Documents
     * ---------------------------------------------------------------- */

    private static void documents()
    {
        section("documents");

        List<String> errors = new ArrayList<String>();
        Machine m = Doc.build(Json.parse(SUCCESSOR), errors);
        ok("the successor machine builds", m != null && errors.isEmpty(), errors.toString());
        same("three states", 3, m.getStates().size());
        same("five transitions", 5, m.getTransitions().size());
        same("it validates", null, Diagnosis.verdict(m));

        same("the alphabet is derived from what the transitions use",
             "A1", new String(m.getAlphabet().getSymbols()));

        errors.clear();
        Machine lower = Doc.build(Json.parse(
            "{\"states\":[{\"name\":\"a\",\"start\":true},{\"name\":\"b\",\"final\":true}],"
          + "\"transitions\":[{\"from\":\"a\",\"to\":\"b\",\"on\":\"x\",\"action\":\"y\"}]}"), errors);
        same("lower case folds the way the alphabet does",
             "XY", new String(lower.getAlphabet().getSymbols()));

        errors.clear();
        Doc.build(Json.parse(
            "{\"states\":[{\"name\":\"a\",\"start\":true}],"
          + "\"transitions\":[{\"from\":\"a\",\"to\":\"a\",\"on\":\"1\",\"action\":\"1R\"}]}"), errors);
        ok("write-and-move in one action is refused", errors.size() == 1
           && errors.get(0).contains("moves or writes, never both"), errors.toString());

        errors.clear();
        Doc.build(Json.parse(
            "{\"states\":[{\"name\":\"a\",\"start\":true}],"
          + "\"transitions\":[{\"from\":\"a\",\"to\":\"a\",\"on\":\"#\",\"action\":\"R\"}]}"), errors);
        ok("a symbol outside the alphabet is refused", errors.size() == 1
           && errors.get(0).contains("only 0-9, A-Z"), errors.toString());

        errors.clear();
        Doc.build(Json.parse(
            "{\"states\":[{\"name\":\"a\",\"start\":true}],"
          + "\"transitions\":[{\"from\":\"a\",\"to\":\"ghost\",\"on\":\"1\",\"action\":\"R\"}]}"), errors);
        ok("an arrow to a state that does not exist is refused", errors.size() == 1
           && errors.get(0).contains("not a state in this machine"), errors.toString());

        errors.clear();
        Doc.build(Json.parse("{\"states\":[{\"name\":\"a\"},{\"name\":\"a\"}],\"transitions\":[]}"), errors);
        ok("two states with one name is refused", errors.size() == 1, errors.toString());

        same("R renders back as R", "R", Doc.writeAction(new tuataraTMSim.machine.TM.TM_Action(1, '1', ' ')));
        same("L renders back as L", "L", Doc.writeAction(new tuataraTMSim.machine.TM.TM_Action(-1, '1', ' ')));
        same("a write renders back as the symbol", "0",
             Doc.writeAction(new tuataraTMSim.machine.TM.TM_Action(0, '1', '0')));
        same("do-nothing renders back as none", "none",
             Doc.writeAction(new tuataraTMSim.machine.TM.TM_Action(0, '1', Machine.EMPTY_ACTION_SYMBOL)));

        String text = Doc.toText(m, "successor");
        ok("the text form names the machine", text.contains("turing machine \"successor\""), null);
        ok("the text form groups by state", text.contains("scan") && text.contains("_/1 -> back"), text);
        ok("the text form says which state is final", text.contains("final: done"), null);

        Map<String, Object> back = Doc.toJson(m, "successor", false);
        ok("a document round trip keeps the transitions",
           Json.arr(back, "transitions").size() == 5, null);
        ok("coordinates are left out unless asked for",
           !Json.has(Json.arr(back, "states").get(0), "x"), null);
        ok("coordinates are included when asked for",
           Json.has(Json.arr(Doc.toJson(m, "s", true), "states").get(0), "x"), null);
    }

    /* ---------------------------------------------------------------- *
     * Running machines
     * ---------------------------------------------------------------- */

    private static Sandbox.Result run(Machine m, String input)
    {
        return Sandbox.run(m, input, 100000, Long.MAX_VALUE, false);
    }

    private static void simulation()
    {
        section("running Turing machines");

        List<String> errors = new ArrayList<String>();
        Machine m = Doc.build(Json.parse(SUCCESSOR), errors);

        Sandbox.Result r = run(m, "A111");
        same("A111 is accepted", "accept", r.outcome);
        same("A111 leaves A1111", "A1111", r.finalTape);
        same("the head comes home", 0, r.head);
        same("eleven steps, the same number of presses of Step", 11L, r.steps);

        same("A on its own is accepted", "accept", run(m, "A").outcome);
        same("a tape with no marker falls off the end", "fell_off", run(m, "111").outcome);

        Machine notParked = Doc.build(Json.parse(
            "{\"states\":[{\"name\":\"q0\",\"start\":true},{\"name\":\"qf\",\"final\":true}],"
          + "\"transitions\":[{\"from\":\"q0\",\"to\":\"qf\",\"on\":\"1\",\"action\":\"R\"}]}"),
            new ArrayList<String>());
        r = run(notParked, "1");
        same("reaching the final state away from home is a failure", "failed", r.outcome);
        ok("and the reason says why", r.reason.contains("not parked at cell 0"), r.reason);

        Machine stuck = Doc.build(Json.parse(
            "{\"states\":[{\"name\":\"q0\",\"start\":true},{\"name\":\"qf\",\"final\":true}],"
          + "\"transitions\":[{\"from\":\"q0\",\"to\":\"qf\",\"on\":\"0\",\"action\":\"0\"}]}"),
            new ArrayList<String>());
        r = run(stuck, "1");
        same("no matching transition is being stuck", "stuck", r.outcome);
        ok("and the reason names the state and symbol",
           r.reason.contains("'q0'") && r.reason.contains("'1'"), r.reason);

        Machine ambiguous = Doc.build(Json.parse(
            "{\"states\":[{\"name\":\"q0\",\"start\":true},{\"name\":\"qf\",\"final\":true}],"
          + "\"transitions\":[{\"from\":\"q0\",\"to\":\"qf\",\"on\":\"1\",\"action\":\"1\"},"
          + "                 {\"from\":\"q0\",\"to\":\"q0\",\"on\":\"1\",\"action\":\"R\"}]}"),
            new ArrayList<String>());
        same("an ambiguous machine is reported, not resolved",
             "nondeterministic", run(ambiguous, "11").outcome);

        Machine loop = Doc.build(Json.parse(
            "{\"states\":[{\"name\":\"q0\",\"start\":true},{\"name\":\"qf\",\"final\":true}],"
          + "\"transitions\":[{\"from\":\"q0\",\"to\":\"q0\",\"on\":\"?\",\"action\":\"R\"}]}"),
            new ArrayList<String>());
        r = Sandbox.run(loop, "1", 500, Long.MAX_VALUE, false);
        same("a machine that will not stop runs out of budget", "over_budget", r.outcome);
        same("and reports the budget it used", 500L, r.steps);

        Machine otherwise = Doc.build(Json.parse(OTHERWISE), new ArrayList<String>());
        same("? matches a symbol with no rule of its own", "accept", run(otherwise, "0").outcome);
        same("? does not override an exact match", "fell_off", run(otherwise, "1").outcome);

        r = Sandbox.run(m, "A111", 100000, Long.MAX_VALUE, true);
        ok("a trace is recorded when asked for", r.trace.size() > 0, null);
        ok("the trace marks where the head is", r.trace.get(0).contains("["), r.trace.toString());
        ok("the trace says what the machine did next", r.trace.get(0).contains("->"),
           r.trace.toString());

        same("running does not disturb the machine it was given",
             11L, run(m, "A111").steps);
        same("nor the second time", "A1111", run(m, "A111").finalTape);
    }

    /* ---------------------------------------------------------------- *
     * Acceptors
     * ---------------------------------------------------------------- */

    private static void acceptors()
    {
        section("running acceptors");

        List<String> errors = new ArrayList<String>();
        Machine m = Doc.build(Json.parse(ENDS_IN_01), errors);
        ok("the acceptor builds", m != null && errors.isEmpty(), errors.toString());
        ok("it is an acceptor", Doc.isAcceptor(m));

        same("01 is accepted", "accept", run(m, "01").outcome);
        same("1101 is accepted", "accept", run(m, "1101").outcome);
        same("0101 is accepted", "accept", run(m, "0101").outcome);
        same("011 is rejected", "reject", run(m, "011").outcome);
        same("0110 is rejected", "reject", run(m, "0110").outcome);
        same("the empty input is rejected", "reject", run(m, "").outcome);

        // The subtle one: reaching a final state means nothing to an acceptor until the input runs
        // out. A Turing machine halts there; an acceptor keeps consuming.
        same("01101 is accepted, having passed through the final state and come back",
             "accept", run(m, "01101").outcome);
        same("the head is sent home when an acceptor finishes", 0, run(m, "01101").head);

        Machine incomplete = Doc.build(Json.parse(
            "{\"type\":\"fsa\",\"alphabet\":[\"0\",\"1\"],"
          + "\"states\":[{\"name\":\"s\",\"start\":true,\"final\":true}],"
          + "\"transitions\":[{\"from\":\"s\",\"to\":\"s\",\"on\":\"1\"}]}"), new ArrayList<String>());
        Sandbox.Result r = run(incomplete, "10");
        same("an acceptor with a gap gets stuck", "stuck", r.outcome);
        ok("and is told it must be complete", r.reason.contains("every alphabet symbol"), r.reason);
        ok("the program's own validation says the same",
           String.valueOf(Diagnosis.verdict(incomplete)).contains("does not have a transition"),
           String.valueOf(Diagnosis.verdict(incomplete)));

        Object missing = Diagnosis.missingSymbols(m);
        ok("the blank is not reported as a gap in an acceptor",
           !Json.write(missing).contains("\"_\""), Json.write(missing));
    }

    /* ---------------------------------------------------------------- *
     * Editing
     * ---------------------------------------------------------------- */

    private static void editing()
    {
        section("editing");

        Machine m = Doc.build(Json.parse(SUCCESSOR), new ArrayList<String>());
        Layout.all(m);

        Edits.Outcome out = Edits.apply(m, null, Json.arr(Json.parse(
            "{\"ops\":[{\"op\":\"add_state\",\"name\":\"extra\"},"
          + "         {\"op\":\"set_transition\",\"from\":\"extra\",\"to\":\"scan\",\"on\":\"1\",\"action\":\"R\"}]}"),
            "ops"), "add a state");
        ok("a state and an arrow to it apply together", out.ok(), out.errors.toString());
        same("two operations", 2, out.applied);
        same("the machine has four states", 4, m.getStates().size());
        same("the new state was placed", 1, out.placed.size());
        ok("the undo entry says who did it", out.undoLabel.startsWith("Claude: "), out.undoLabel);

        int before = m.getTransitions().size();
        out = Edits.apply(m, null, Json.arr(Json.parse(
            "{\"ops\":[{\"op\":\"set_transition\",\"from\":\"extra\",\"to\":\"done\",\"on\":\"1\",\"action\":\"L\"}]}"),
            "ops"), "redirect");
        ok("setting a transition that exists replaces it", out.ok(), out.errors.toString());
        same("rather than adding a second", before, m.getTransitions().size());
        same("it points somewhere new", "done",
             ((State)Machines.transition(m, "extra", null, '1').getToState()).getLabel());
        same("and does something new", "L",
             Doc.writeAction(Machines.transition(m, "extra", null, '1').getAction()));

        // The finding from testing the design: an agent reached for set_transition before the
        // transition existed. Creating it is friendlier than an error nobody learns from.
        out = Edits.apply(m, null, Json.arr(Json.parse(
            "{\"ops\":[{\"op\":\"set_transition\",\"from\":\"extra\",\"to\":\"done\",\"on\":\"0\",\"action\":\"R\"}]}"),
            "ops"), "upsert");
        ok("setting a transition that does not exist creates it", out.ok(), out.errors.toString());
        ok("it is there", Machines.transition(m, "extra", null, '0') != null);

        int states = m.getStates().size();
        int transitions = m.getTransitions().size();
        out = Edits.apply(m, null, Json.arr(Json.parse(
            "{\"ops\":[{\"op\":\"add_state\",\"name\":\"fine\"},"
          + "         {\"op\":\"rename_state\",\"name\":\"nosuchstate\",\"to\":\"x\"}]}"), "ops"), "bad");
        ok("a batch with a bad operation is refused", !out.ok(), null);
        ok("and says which states there are", out.errors.get(0).contains("States in this machine"),
           out.errors.toString());
        same("nothing was added", states, m.getStates().size());
        same("nothing was changed", transitions, m.getTransitions().size());

        out = Edits.apply(m, null, Json.arr(Json.parse(
            "{\"ops\":[{\"op\":\"set_final\",\"name\":\"scan\"}]}"), "ops"), "move the final state");
        ok("setting a final state applies", out.ok(), out.errors.toString());
        same("there is still exactly one final state", 1, m.getFinalStates().size());
        same("and it is the new one", "scan", ((State)m.getFinalStates().get(0)).getLabel());

        out = Edits.apply(m, null, Json.arr(Json.parse(
            "{\"ops\":[{\"op\":\"remove_state\",\"name\":\"extra\"}]}"), "ops"), "remove");
        ok("removing a state applies", out.ok(), out.errors.toString());
        ok("and takes its arrows with it", Machines.transition(m, "extra", null, '1') == null);
        for (Object o : m.getTransitions())
        {
            Transition t = (Transition)o;
            ok("no arrow is left dangling",
               Machines.state(m, ((State)t.getFromState()).getLabel()) != null
               && Machines.state(m, ((State)t.getToState()).getLabel()) != null);
            break;
        }

        Machine alpha = Doc.build(Json.parse(SUCCESSOR), new ArrayList<String>());
        out = Edits.apply(alpha, null, Json.arr(Json.parse(
            "{\"ops\":[{\"op\":\"set_alphabet\",\"symbols\":[\"0\",\"1\",\"A\",\"B\"]}]}"), "ops"), "alphabet");
        ok("the alphabet can be set", out.ok(), out.errors.toString());
        same("and holds what was asked for", "AB01", new String(alpha.getAlphabet().getSymbols()));

        out = Edits.apply(alpha, null, Json.arr(Json.parse(
            "{\"ops\":[{\"op\":\"set_alphabet\",\"symbols\":[\"#\"]}]}"), "ops"), "bad alphabet");
        ok("a symbol the alphabet cannot hold is refused", !out.ok(), null);
        same("and the alphabet is untouched", "AB01", new String(alpha.getAlphabet().getSymbols()));

        out = Edits.apply(alpha, null, Json.arr(Json.parse(
            "{\"ops\":[{\"op\":\"frobnicate\",\"name\":\"scan\"}]}"), "ops"), "unknown");
        ok("an operation that does not exist is refused", !out.ok(), null);
        ok("and the message lists the ones that do",
           out.errors.get(0).contains("add_state"), out.errors.toString());
    }

    /* ---------------------------------------------------------------- *
     * Layout
     * ---------------------------------------------------------------- */

    private static void layout()
    {
        section("layout");

        Machine m = Doc.build(Json.parse(SUCCESSOR), new ArrayList<String>());
        ok("states start unplaced", !Layout.isPlaced((State)m.getStates().iterator().next()));
        Layout.all(m);

        boolean allPlaced = true;
        for (Object o : m.getStates())
        {
            allPlaced &= Layout.isPlaced((State)o);
        }
        ok("laying out places everything", allPlaced);
        ok("nothing overlaps", minSeparation(m) >= 48, "closest pair " + minSeparation(m) + "px");
        ok("everything is on the canvas", onCanvas(m));

        List<State> ordered = new ArrayList<State>();
        for (Object o : m.getStates())
        {
            ordered.add((State)o);
        }
        ok("the start state is left of the final state",
           Machines.state(m, "scan").getX() < Machines.state(m, "done").getX(),
           Machines.state(m, "scan").getX() + " vs " + Machines.state(m, "done").getX());

        // Adding to a machine somebody arranged must not move what they arranged.
        int[] before = new int[ordered.size() * 2];
        for (int i = 0; i < ordered.size(); i++)
        {
            before[i * 2] = ordered.get(i).getX();
            before[i * 2 + 1] = ordered.get(i).getY();
        }
        Edits.apply(m, null, Json.arr(Json.parse(
            "{\"ops\":[{\"op\":\"add_state\",\"name\":\"newbie\"},"
          + "         {\"op\":\"set_transition\",\"from\":\"scan\",\"to\":\"newbie\",\"on\":\"0\",\"action\":\"R\"}]}"),
            "ops"), "add");

        boolean unmoved = true;
        for (int i = 0; i < ordered.size(); i++)
        {
            unmoved &= ordered.get(i).getX() == before[i * 2]
                    && ordered.get(i).getY() == before[i * 2 + 1];
        }
        ok("adding a state moves nothing that was already placed", unmoved);
        ok("the new state was placed clear of the others", minSeparation(m) >= 48,
           "closest pair " + minSeparation(m) + "px");
        ok("near the state it connects to",
           Math.hypot(Machines.state(m, "newbie").getX() - Machines.state(m, "scan").getX(),
                      Machines.state(m, "newbie").getY() - Machines.state(m, "scan").getY()) < 260,
           null);

        // Self-loops and opposite pairs need control points that do not sit on the line.
        Machine pair = Doc.build(Json.parse(
            "{\"states\":[{\"name\":\"a\",\"start\":true},{\"name\":\"b\",\"final\":true}],"
          + "\"transitions\":[{\"from\":\"a\",\"to\":\"b\",\"on\":\"1\",\"action\":\"R\"},"
          + "                 {\"from\":\"b\",\"to\":\"a\",\"on\":\"0\",\"action\":\"R\"},"
          + "                 {\"from\":\"a\",\"to\":\"a\",\"on\":\"_\",\"action\":\"R\"}]}"),
            new ArrayList<String>());
        Layout.all(pair);
        Transition ab = Machines.transition(pair, "a", "b", '1');
        Transition ba = Machines.transition(pair, "b", "a", '0');
        Transition loop = Machines.transition(pair, "a", "a", '_');
        double midX = (((State)ab.getFromState()).getX() + ((State)ab.getToState()).getX()) / 2.0;
        double midY = (((State)ab.getFromState()).getY() + ((State)ab.getToState()).getY()) / 2.0;
        ok("arrows in opposite directions bow apart",
           Math.hypot(ab.getControlPoint().getX() - midX, ab.getControlPoint().getY() - midY) > 20,
           null);
        ok("and bow to the same extent, so they are symmetric",
           Math.abs(Math.hypot(ab.getControlPoint().getX() - midX, ab.getControlPoint().getY() - midY)
                  - Math.hypot(ba.getControlPoint().getX() - midX, ba.getControlPoint().getY() - midY)) < 2,
           null);
        State looped = (State)loop.getFromState();
        double loopDistance = Math.hypot(
                loop.getControlPoint().getX() - (looped.getX() + 15),
                loop.getControlPoint().getY() - (looped.getY() + 15));
        ok("a self-loop is parked clear of its state", loopDistance > 30 && loopDistance < 80,
           String.format("%.0fpx from the middle", loopDistance));
        // A state whose name will not fit inside the circle has it drawn underneath, so that is
        // the one direction a loop should not take by default.
        ok("and keeps out of the space under it, where a long label goes",
           loop.getControlPoint().getY() <= looped.getY() + 15,
           "loop y " + (int)loop.getControlPoint().getY() + ", state y " + looped.getY());

        // An arrow that would run straight through a third state should go round it.
        Machine through = Doc.build(Json.parse(
            "{\"states\":[{\"name\":\"a\",\"start\":true,\"x\":100,\"y\":300},"
          + "            {\"name\":\"mid\",\"x\":300,\"y\":300},"
          + "            {\"name\":\"c\",\"final\":true,\"x\":500,\"y\":300}],"
          + "\"transitions\":[{\"from\":\"a\",\"to\":\"c\",\"on\":\"1\",\"action\":\"R\"}]}"),
            new ArrayList<String>());
        Transition ac = Machines.transition(through, "a", "c", '1');
        Layout.route(through, ac);
        ok("an arrow bows around a state in its way",
           Math.abs(ac.getControlPoint().getY() - 300) > 40,
           "control point y = " + ac.getControlPoint().getY());
    }

    private static double minSeparation(Machine m)
    {
        List<State> states = new ArrayList<State>();
        for (Object o : m.getStates())
        {
            states.add((State)o);
        }
        double closest = Double.MAX_VALUE;
        for (int i = 0; i < states.size(); i++)
        {
            for (int j = i + 1; j < states.size(); j++)
            {
                closest = Math.min(closest, Math.hypot(states.get(i).getX() - states.get(j).getX(),
                                                       states.get(i).getY() - states.get(j).getY()));
            }
        }
        return closest == Double.MAX_VALUE? 999 : closest;
    }

    private static boolean onCanvas(Machine m)
    {
        for (Object o : m.getStates())
        {
            State s = (State)o;
            if (s.getX() < 0 || s.getY() < 0 || s.getX() > 2000 || s.getY() > 2000)
            {
                return false;
            }
        }
        return true;
    }

    /* ---------------------------------------------------------------- *
     * Scale
     * ---------------------------------------------------------------- */

    private static void scale()
    {
        section("scale");

        StringBuilder doc = new StringBuilder("{\"states\":[");
        int n = 2000;
        for (int i = 0; i < n; i++)
        {
            doc.append(i == 0? "" : ",");
            doc.append("{\"name\":\"q").append(i).append('"');
            if (i == 0)
            {
                doc.append(",\"start\":true");
            }
            if (i == n - 1)
            {
                doc.append(",\"final\":true");
            }
            doc.append('}');
        }
        doc.append("],\"transitions\":[");
        for (int i = 0; i < n - 1; i++)
        {
            doc.append(i == 0? "" : ",");
            doc.append("{\"from\":\"q").append(i).append("\",\"to\":\"q").append(i + 1)
               .append("\",\"on\":\"1\",\"action\":\"R\"}");
        }
        doc.append("]}");

        long t0 = System.nanoTime();
        Machine big = Doc.build(Json.parse(doc.toString()), new ArrayList<String>());
        long built = (System.nanoTime() - t0) / 1000000;
        same("two thousand states build", n, big.getStates().size());

        t0 = System.nanoTime();
        Layout.all(big);
        long laid = (System.nanoTime() - t0) / 1000000;
        ok("laying out two thousand states is quick", laid < 8000, laid + " ms");
        ok("and keeps them on the canvas", onCanvas(big));

        t0 = System.nanoTime();
        Machine copy = Machines.copy(big);
        long copied = (System.nanoTime() - t0) / 1000000;
        ok("copying two thousand states is quick", copied < 500, copied + " ms");
        same("the copy is complete", n, copy.getStates().size());
        ok("the copy is separate", copy.getStates().iterator().next()
           != big.getStates().iterator().next());

        String text = Doc.toText(big, "chain");
        String json = Json.write(Doc.toJson(big, "chain", true));
        ok("the text form is far smaller than the JSON",
           text.length() * 4 < json.length(),
           String.format("%,d vs %,d characters", text.length(), json.length()));

        // A long run, to show the sandbox is quick enough to be worth having.
        Machine scanner = Doc.build(Json.parse(SCANNER), new ArrayList<String>());
        StringBuilder tape = new StringBuilder("A");
        for (int i = 0; i < 400000; i++)
        {
            tape.append('1');
        }
        t0 = System.nanoTime();
        Sandbox.Result r = Sandbox.run(scanner, tape.toString(), 5000000, Long.MAX_VALUE, false);
        double seconds = (System.nanoTime() - t0) / 1e9;
        same("a long run finishes", "accept", r.outcome);
        ok("at a useful speed", r.steps / seconds > 1000000,
           String.format("%,d steps in %.2fs = %,.0f steps/sec", r.steps, seconds, r.steps / seconds));

        r = Sandbox.run(scanner, tape.toString(), 5000000, System.nanoTime(), false);
        same("a run past its deadline is called off", "timeout", r.outcome);
        System.out.printf("  (built in %d ms, laid out in %d ms)%n", built, laid);
    }

    /* ---------------------------------------------------------------- *
     * Machines used above
     * ---------------------------------------------------------------- */

    /** Appends a 1 to a run of them, then walks home. */
    private static final String SUCCESSOR =
        "{\"type\":\"turing\",\"name\":\"successor\","
      + "\"states\":[{\"name\":\"scan\",\"start\":true},{\"name\":\"back\"},"
      + "           {\"name\":\"done\",\"final\":true}],"
      + "\"transitions\":["
      + "  {\"from\":\"scan\",\"to\":\"scan\",\"on\":\"A\",\"action\":\"R\"},"
      + "  {\"from\":\"scan\",\"to\":\"scan\",\"on\":\"1\",\"action\":\"R\"},"
      + "  {\"from\":\"scan\",\"to\":\"back\",\"on\":\"_\",\"action\":\"1\"},"
      + "  {\"from\":\"back\",\"to\":\"back\",\"on\":\"1\",\"action\":\"L\"},"
      + "  {\"from\":\"back\",\"to\":\"done\",\"on\":\"A\",\"action\":\"A\"}]}";

    /** Walks right and comes home, for timing. */
    private static final String SCANNER =
        "{\"type\":\"turing\",\"name\":\"scanner\","
      + "\"states\":[{\"name\":\"out\",\"start\":true},{\"name\":\"home\"},"
      + "           {\"name\":\"fin\",\"final\":true}],"
      + "\"transitions\":["
      + "  {\"from\":\"out\",\"to\":\"out\",\"on\":\"A\",\"action\":\"R\"},"
      + "  {\"from\":\"out\",\"to\":\"out\",\"on\":\"1\",\"action\":\"R\"},"
      + "  {\"from\":\"out\",\"to\":\"home\",\"on\":\"_\",\"action\":\"L\"},"
      + "  {\"from\":\"home\",\"to\":\"home\",\"on\":\"1\",\"action\":\"L\"},"
      + "  {\"from\":\"home\",\"to\":\"fin\",\"on\":\"A\",\"action\":\"A\"}]}";

    /** Exercises the otherwise symbol. */
    private static final String OTHERWISE =
        "{\"type\":\"turing\",\"alphabet\":[\"0\",\"1\",\"A\"],"
      + "\"states\":[{\"name\":\"q0\",\"start\":true},{\"name\":\"q1\"},"
      + "           {\"name\":\"qf\",\"final\":true}],"
      + "\"transitions\":["
      + "  {\"from\":\"q0\",\"to\":\"q1\",\"on\":\"?\",\"action\":\"R\"},"
      + "  {\"from\":\"q0\",\"to\":\"qf\",\"on\":\"0\",\"action\":\"0\"},"
      + "  {\"from\":\"q1\",\"to\":\"q1\",\"on\":\"?\",\"action\":\"L\"}]}";

    /** The textbook acceptor, complete so that it validates. */
    private static final String ENDS_IN_01 =
        "{\"type\":\"fsa\",\"name\":\"ends in 01\",\"alphabet\":[\"0\",\"1\"],"
      + "\"states\":[{\"name\":\"q0\",\"start\":true},{\"name\":\"q1\"},"
      + "           {\"name\":\"q2\",\"final\":true}],"
      + "\"transitions\":["
      + "  {\"from\":\"q0\",\"to\":\"q1\",\"on\":\"0\"},"
      + "  {\"from\":\"q0\",\"to\":\"q0\",\"on\":\"1\"},"
      + "  {\"from\":\"q1\",\"to\":\"q1\",\"on\":\"0\"},"
      + "  {\"from\":\"q1\",\"to\":\"q2\",\"on\":\"1\"},"
      + "  {\"from\":\"q2\",\"to\":\"q1\",\"on\":\"0\"},"
      + "  {\"from\":\"q2\",\"to\":\"q0\",\"on\":\"1\"}]}";
}
