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

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import tuataraTMSim.*;
import tuataraTMSim.machine.*;
import tuataraTMSim.machine.DFSA.*;
import tuataraTMSim.machine.TM.*;

/**
 * The tools an agent is offered, and what they do.
 *
 * Both halves live here on purpose. The descriptions are the whole of what an agent knows before it
 * calls anything, so they belong next to the behaviour they describe rather than in a separate
 * process that can drift out of step with it. The wording is not incidental either: it was arrived
 * at by giving these tools to models with no other context and watching which mistakes they made.
 */
public final class Tools
{
    /**
     * What a tool does when called.
     */
    public interface Handler
    {
        /**
         * Carry out the call.
         * @param args The arguments, as a JSON object.
         * @return The result, as a JSON value.
         * @throws Exception If the call could not be carried out.
         */
        Object run(Object args) throws Exception;
    }

    /**
     * One tool: what it is called, what it is for, what it takes, and what it does.
     */
    public static final class Tool
    {
        /**
         * The name an agent calls it by.
         */
        public final String name;

        /**
         * What it is for. This is all an agent has to go on.
         */
        public final String description;

        /**
         * Its arguments, as a JSON schema.
         */
        public final Map<String, Object> schema;

        /**
         * What it does.
         */
        public final Handler handler;

        Tool(String name, String description, Map<String, Object> schema, Handler handler)
        {
            this.name = name;
            this.description = description;
            this.schema = schema;
            this.handler = handler;
        }
    }

    /**
     * The tools, in the order they are offered.
     */
    private static final Map<String, Tool> TOOLS = new LinkedHashMap<String, Tool>();

    /**
     * Not instantiable.
     */
    private Tools() { }

    /**
     * What the agent is told about Tuatara before it calls anything.
     *
     * The four rules earn their place. Given them, a model with no other context built a correct
     * machine first time; without them, its first attempt failed every test on the parking rule --
     * and then recovered, because the failure message says which rule was broken. Both halves
     * matter, so both are here and in the messages.
     * @return The server's instructions.
     */
    public static String instructions()
    {
        return
          "Tuatara is a desktop simulator for Turing machines and finite-state acceptors. These "
        + "tools read and edit the machines a user has open, run inputs, and drive the on-screen "
        + "simulation.\n"
        + "\n"
        + "Tuatara's Turing machines are not the textbook kind. Four rules decide whether a machine "
        + "works:\n"
        + "\n"
        + "1. A transition either MOVES the head or WRITES a symbol -- never both. To write and then "
        + "move, use two transitions through an intermediate state.\n"
        + "2. Exactly one final state, and nothing may leave it.\n"
        + "3. A run only succeeds if the machine reaches the final state with the head back at cell "
        + "0. Every machine therefore needs a phase that returns the head home. Reaching the final "
        + "state anywhere else is a failed run, not a successful one.\n"
        + "4. The alphabet is only 0-9, A-Z and the blank _, and it is case-insensitive: 'a' and 'A' "
        + "are the same symbol. Every symbol a transition reads or writes must be in the machine's "
        + "alphabet.\n"
        + "\n"
        + "Two extras: '?' as the read symbol matches anything with no transition of its own, and "
        + "moving left off cell 0 fails the run.\n"
        + "\n"
        + "Finite-state acceptors (type \"fsa\") invert two of those. A transition just names the "
        + "symbol it consumes -- there is no action. Reaching a final state does not end the run: "
        + "the machine keeps consuming until the input runs out, and accepts only if it is in a "
        + "final state at that moment. Tuatara also requires an acceptor to be complete, with a "
        + "transition for every alphabet symbol in every state, and '?' is not allowed.\n"
        + "\n"
        + "Every tool that changes a machine reports a \"layout\" field saying whether the diagram "
        + "still reads cleanly, and naming what is wrong when it does not. Watch it as you build. A "
        + "user following along is looking at the picture, not at the transition table, so a diagram "
        + "that has become unreadable halfway through is a real failure even though every edit "
        + "succeeded. Fix it when it appears rather than at the end: call arrange_machine, or "
        + "move_state, and use render_machine to see what they are seeing.\n"
        + "\n"
        + "Work in a draft, not in the user's tab. create_machine with open:false gives you a "
        + "private machine to iterate on; run_tests on it is fast and invisible. Call open_in_app "
        + "when you have something worth showing. Editing a tab the user has open changes what is "
        + "on their screen.\n"
        + "\n"
        + "Every tool that names a machine takes the same optional \"target\": leave it out for "
        + "whichever tab is in front, or pass a tab id like \"tab:2\", a tab title, or a draft id "
        + "like \"draft:factorial\".";
    }

    /**
     * Every tool, in the order they are offered.
     * @return The tools.
     */
    public static List<Tool> all()
    {
        return new ArrayList<Tool>(TOOLS.values());
    }

    /**
     * The tool definitions, in the shape a client expects for a listing.
     * @return A list of JSON objects with name, description and inputSchema.
     */
    public static List<Object> definitions()
    {
        List<Object> out = new ArrayList<Object>();
        for (Tool tool : TOOLS.values())
        {
            out.add(Json.object("name", tool.name, "description", tool.description,
                        "inputSchema", tool.schema));
        }
        return out;
    }

    /**
     * Call a tool.
     * @param name The tool to call.
     * @param args Its arguments.
     * @return The result.
     * @throws Exception If the tool is unknown or the call could not be carried out.
     */
    public static Object call(String name, Object args) throws Exception
    {
        Tool tool = TOOLS.get(name);
        if (tool == null)
        {
            StringBuilder known = new StringBuilder();
            for (String id : TOOLS.keySet())
            {
                known.append(known.length() == 0? "" : ", ").append(id);
            }
            throw new AgentException("There is no tool called \"" + name + "\". There is: " + known);
        }
        return tool.handler.run(args == null? Json.object() : args);
    }

    /* ---------------------------------------------------------------- *
     * Schema shorthand
     * ---------------------------------------------------------------- */

    private static Map<String, Object> schema(Object... pairs)
    {
        Map<String, Object> properties = Json.object();
        List<Object> required = new ArrayList<Object>();
        for (int i = 0; i + 1 < pairs.length; i += 2)
        {
            String key = String.valueOf(pairs[i]);
            if (key.endsWith("!"))
            {
                key = key.substring(0, key.length() - 1);
                required.add(key);
            }
            properties.put(key, pairs[i + 1]);
        }
        Map<String, Object> out = Json.object("type", "object", "properties", properties);
        if (!required.isEmpty())
        {
            out.put("required", required);
        }
        out.put("additionalProperties", Boolean.FALSE);
        return out;
    }

    private static Map<String, Object> string(String description)
    {
        return Json.object("type", "string", "description", description);
    }

    private static Map<String, Object> bool(String description)
    {
        return Json.object("type", "boolean", "description", description);
    }

    private static Map<String, Object> integer(String description)
    {
        return Json.object("type", "integer", "description", description);
    }

    private static Map<String, Object> enumeration(String description, String... values)
    {
        return Json.object("type", "string", "description", description, "enum", Json.array((Object[])values));
    }

    private static Map<String, Object> list(String description, Object items)
    {
        return Json.object("type", "array", "description", description, "items", items);
    }

    private static final Map<String, Object> TARGET = string(
            "A tab id like \"tab:2\", a tab title, or a draft id like \"draft:factorial\". "
          + "Leave it out for whichever tab is in front.");

    private static Map<String, Object> machineSchema()
    {
        return Json.object(
            "type", "object",
            "description", "A machine document.",
            "properties", Json.object(
                "type", enumeration("\"turing\" (the default) or \"fsa\" for an acceptor.", "turing", "fsa"),
                "name", string("What to call it."),
                "alphabet", list("Optional; worked out from the transitions when left out. "
                               + "Only 0-9, A-Z and the blank _.", Json.object("type", "string")),
                "states", list("The states.", schema(
                    "name!", string("Its label."),
                    "start", bool("Whether the machine starts here. Exactly one state should say true."),
                    "final", bool("Whether this is the final state. A Turing machine has exactly one, "
                                + "and nothing may leave it."),
                    "x", integer("Optional. Leave positions out and they are worked out for you."),
                    "y", integer("Optional."))),
                "transitions", list("The transitions.", schema(
                    "from!", string("The state it leaves."),
                    "to!", string("The state it arrives at."),
                    "on!", string("The symbol read: one alphabet symbol, _ for the blank, or ? for "
                                + "\"anything with no rule of its own\"."),
                    "action", string("Turing machines only. \"R\" or \"L\" to move the head, a single "
                                   + "symbol to WRITE it, or \"none\" to do nothing. A transition "
                                   + "moves or writes, never both.")))),
            "required", Json.array("states", "transitions"));
    }

    /* ---------------------------------------------------------------- *
     * Registration
     * ---------------------------------------------------------------- */

    private static void tool(String name, String description, Map<String, Object> schema,
                             Handler handler)
    {
        TOOLS.put(name, new Tool(name, description, schema, handler));
    }

    /** Runs a tool body on the Swing thread. */
    private abstract static class SwingHandler implements Handler
    {
        public Object run(final Object args) throws Exception
        {
            return Workspace.onSwingThread(new Workspace.Job<Object>()
            {
                public Object run() throws Exception
                {
                    return call(args);
                }
            });
        }

        abstract Object call(Object args) throws Exception;
    }

    static
    {
        seeing();
        building();
        running();
        theApp();
    }

    /* ---------------------------------------------------------------- *
     * Seeing
     * ---------------------------------------------------------------- */

    private static void seeing()
    {
        tool("get_workspace",
             "What Tuatara is showing right now: every open tab, the shared tape, whether a "
           + "simulation is running, the app's settings, recent console output, your drafts, and "
           + "any layout proposal waiting on the user. Call this first -- it is the only tool that "
           + "reports the app's live state.",
             schema(),
             new SwingHandler()
             {
                 Object call(Object args)
                 {
                     return Workspace.snapshot();
                 }
             });

        tool("get_machine",
             "Read one machine and say what is wrong with it. Returns a compact rendering grouped "
           + "by the state each transition leaves, which is what you want to read, plus a "
           + "diagnosis. Use this to see a user's construction without asking for a screenshot.",
             schema("target", TARGET,
                    "include", list("Extra detail. \"positions\" adds each state's coordinates; "
                                  + "\"json\" adds the machine as a document you could pass back to "
                                  + "create_machine. Both are large on a big machine, and neither is "
                                  + "needed for ordinary work -- layout is handled for you.",
                                    enumeration("", "positions", "json")),
                    "around", string("Only show this state and the ones within \"depth\" of it. For "
                                   + "a machine too large to read whole."),
                    "depth", integer("How far around \"around\" to show. Default 2.")),
             new SwingHandler()
             {
                 Object call(Object args) throws Exception
                 {
                     Workspace.Target target = Workspace.resolve(Json.str(args, "target", null));
                     Machine machine = target.machine;
                     List<Object> include = Json.arr(args, "include");

                     Machine shown = machine;
                     String note = null;
                     String around = Json.str(args, "around", null);
                     if (around != null)
                     {
                         shown = neighbourhood(machine, around, (int)Json.num(args, "depth", 2));
                         note = "Showing only the states within " + Json.num(args, "depth", 2)
                              + " of '" + around + "'.";
                     }
                     else if (machine.getStates().size() > 400 && !include.contains("json"))
                     {
                         note = "This machine has " + machine.getStates().size() + " states. The "
                              + "rendering below is complete; use \"around\" to look at one part of "
                              + "it instead.";
                     }

                     Map<String, Object> out = Json.object(
                             "target", target.id,
                             "title", target.title,
                             "type", Doc.typeOf(machine),
                             "states", Integer.valueOf(machine.getStates().size()),
                             "transitions", Integer.valueOf(machine.getTransitions().size()),
                             "machine", Doc.toText(shown, target.title),
                             "diagnosis", Diagnosis.summary(machine));
                     if (note != null)
                     {
                         out.put("note", note);
                     }
                     if (include.contains("json") || include.contains("positions"))
                     {
                         out.put("document", Doc.toJson(machine, target.title,
                                     include.contains("positions")));
                     }
                     return out;
                 }
             });

        tool("render_machine",
             "Draw a machine exactly as Tuatara draws it and return the picture as a PNG. Use it to "
           + "check that a machine you built or rearranged actually looks right: overlapping "
           + "states, arrows crossing through other states and unreadable labels show up here and "
           + "not in get_machine. The \"layout\" field alongside names any of those it can "
           + "measure, so read both.",
             schema("target", TARGET,
                    "highlight", list("State labels to pick out in colour.", Json.object("type", "string"))),
             new SwingHandler()
             {
                 Object call(Object args) throws Exception
                 {
                     Workspace.Target target = Workspace.resolve(Json.str(args, "target", null));
                     List<String> highlight = new ArrayList<String>();
                     for (Object o : Json.arr(args, "highlight"))
                     {
                         highlight.add(String.valueOf(o));
                     }
                     return Render.png(target.machine, highlight);
                 }
             });

        tool("validate",
             "Run Tuatara's own Validate check -- the one on the Machine menu -- and return its "
           + "verdict word for word, plus deeper structural checks the app does not do. Use it "
           + "before telling a user their machine is fine, and after every edit that adds states or "
           + "transitions. Report the app's answer and the extra checks as the separate things they "
           + "are.",
             schema("target", TARGET,
                    "show_in_app", bool("Also log the verdict to the user's console, as if they had "
                                      + "clicked Validate themselves. Default false.")),
             new SwingHandler()
             {
                 Object call(Object args) throws Exception
                 {
                     Workspace.Target target = Workspace.resolve(Json.str(args, "target", null));
                     String verdict = Diagnosis.verdict(target.machine);
                     if (Json.bool(args, "show_in_app", false))
                     {
                         Workspace.log(verdict == null
                                 ? target.title + " is deterministic"
                                 : target.title + " is nondeterministic: " + verdict);
                     }
                     return Json.object(
                             "target", target.id,
                             "app_validate", Json.object(
                                 "passes", Boolean.valueOf(verdict == null),
                                 "message", verdict == null? "Machine is deterministic" : verdict),
                             "additional_checks", Diagnosis.checks(target.machine));
                 }
             });
    }

    /* ---------------------------------------------------------------- *
     * Building
     * ---------------------------------------------------------------- */

    private static void building()
    {
        tool("create_machine",
             "Build a machine from scratch. With open:false (the default) it becomes a private "
           + "draft the user never sees -- that is where to iterate. With open:true it opens as a "
           + "new tab in front of them. Positions are worked out for you; leave x and y out unless "
           + "you have a reason.",
             schema("machine!", machineSchema(),
                    "open", bool("Show it to the user as a new tab. Default false."),
                    "id", string("What to call the draft, e.g. \"factorial-v3\". Reusing a name "
                               + "replaces that draft.")),
             new SwingHandler()
             {
                 Object call(Object args) throws Exception
                 {
                     List<String> errors = new ArrayList<String>();
                     Machine machine = Doc.build(Json.member(args, "machine"), errors);
                     if (machine == null || !errors.isEmpty())
                     {
                         throw new AgentException("That machine could not be built:\n  - "
                                 + join(errors, "\n  - "));
                     }
                     Layout.all(machine);
                     String name = Json.str(Json.member(args, "machine"), "name", "untitled");

                     if (Json.bool(args, "open", false))
                     {
                         String id = Workspace.openInApp(machine, name);
                         Workspace.log("Claude opened " + name);
                         return Json.object("target", id, "opened_in_app", Boolean.TRUE,
                                 "states", Integer.valueOf(machine.getStates().size()),
                                 "transitions", Integer.valueOf(machine.getTransitions().size()),
                                 "diagnosis", Diagnosis.summary(machine),
                                 "layout", Legibility.report(machine));
                     }
                     Workspace.Draft draft = Workspace.putDraft(
                             Workspace.draftId(Json.str(args, "id", null), name), machine);
                     return Json.object("target", "draft:" + draft.id, "opened_in_app", Boolean.FALSE,
                             "states", Integer.valueOf(machine.getStates().size()),
                             "transitions", Integer.valueOf(machine.getTransitions().size()),
                             "diagnosis", Diagnosis.summary(machine),
                             "layout", Legibility.report(machine),
                             "note", "This is a draft; the user cannot see it. Call open_in_app when "
                                   + "it is worth showing.");
                 }
             });

        tool("edit_machine",
             "Change an existing machine with a list of small operations, applied together as one "
           + "step the user can undo with a single Ctrl+Z. Existing states keep the positions they "
           + "have; anything you add is placed in clear space nearby, and the arrows touching it "
           + "are shaped to keep their labels clear of everything else. This is the tool for "
           + "correcting or extending a machine somebody has been working on. If any operation is "
           + "invalid the whole call fails and nothing changes. Check the \"layout\" field it "
           + "returns: it says whether the diagram still reads cleanly after the edit.",
             schema("target", TARGET,
                    "label", string("What to call this in the undo menu, e.g. \"add the carry loop\"."),
                    "ops!", list(
                        "Applied in order. set_transition creates the transition if it is not there "
                      + "already, and replaces it if it is -- a state may only have one transition "
                      + "per symbol.",
                        schema("op!", enumeration("Which operation.",
                                    "add_state", "remove_state", "rename_state", "set_start",
                                    "set_final", "set_transition", "remove_transition",
                                    "move_state", "set_alphabet"),
                               "name", string("The state, for the operations that name one."),
                               "to", string("The new label for rename_state, or the destination for "
                                          + "set_transition and remove_transition."),
                               "start", bool("For add_state."),
                               "final", bool("For add_state."),
                               "value", bool("For set_start and set_final; default true."),
                               "from", string("The state a transition leaves."),
                               "on", string("The symbol a transition reads."),
                               "action", string("\"R\", \"L\", a symbol to write, or \"none\"."),
                               "x", integer("For move_state."),
                               "y", integer("For move_state."),
                               "symbols", list("For set_alphabet.", Json.object("type", "string"))))),
             new SwingHandler()
             {
                 Object call(Object args) throws Exception
                 {
                     Workspace.Target target = Workspace.resolve(Json.str(args, "target", null));
                     String label = Json.str(args, "label", null);
                     Edits.Outcome outcome = Edits.apply(target.machine, target.panel,
                             Json.arr(args, "ops"), label);
                     if (!outcome.ok())
                     {
                         throw new AgentException("Those edits were not applied:\n  - "
                                 + join(outcome.errors, "\n  - ") + "\nThe machine is unchanged.");
                     }
                     if (target.isTab())
                     {
                         Workspace.invalidateProposals(target.id);
                         Workspace.log(outcome.undoLabel);
                         target.panel.repaint();
                         MainWindow.getInstance().refreshTab(target.frame);
                     }
                     else
                     {
                         Workspace.draftChanged(target.title);
                     }
                     return Json.object(
                             "target", target.id,
                             "applied", Integer.valueOf(outcome.applied),
                             "undo_label", outcome.undoLabel,
                             "placed", outcome.placed,
                             "diagnosis", Diagnosis.summary(target.machine),
                             "layout", Legibility.report(target.machine));
                 }
             });

        tool("arrange_machine",
             "Tidy a machine's layout. On a machine you built, this rearranges it immediately. On a "
           + "machine the user has positioned by hand (hand_positioned:true in get_workspace) a "
           + "full rearrangement is OFFERED rather than applied: they get a banner in their tab "
           + "with Preview, Apply and Dismiss, and this call returns straight away. Do not wait for "
           + "an answer and do not ask in chat as well -- the banner is the question.",
             schema("target", TARGET,
                    "scope", enumeration(
                        "\"new_states\" (the default) places only states that have no position yet "
                      + "and touches nothing else. \"all\" rearranges the whole machine.",
                        "new_states", "all")),
             new SwingHandler()
             {
                 Object call(Object args) throws Exception
                 {
                     Workspace.Target target = Workspace.resolve(Json.str(args, "target", null));
                     String scope = Json.str(args, "scope", "new_states");

                     if (!"all".equals(scope))
                     {
                         List<State> placed = Layout.placeNew(target.machine);
                         if (target.isTab())
                         {
                             target.panel.repaint();
                         }
                         if (!target.isTab())
                         {
                             Workspace.draftChanged(target.title);
                         }
                         return Json.object("target", target.id, "status", "applied",
                                 "scope", "new_states", "moved", Integer.valueOf(placed.size()),
                                 "layout", Legibility.report(target.machine));
                     }

                     if (target.isTab() && target.panel.isHandPositioned())
                     {
                         Machine preview = Machines.copy(target.machine);
                         Layout.all(preview);
                         Map<String, int[]> positions = new LinkedHashMap<String, int[]>();
                         for (Object o : preview.getStates())
                         {
                             State s = (State)o;
                             positions.put(s.getLabel(), new int[] { s.getX(), s.getY() });
                         }
                         int moving = 0;
                         for (Object o : target.machine.getStates())
                         {
                             State s = (State)o;
                             int[] to = positions.get(s.getLabel());
                             if (to != null && (to[0] != s.getX() || to[1] != s.getY()))
                             {
                                 moving++;
                             }
                         }
                         if (moving == 0)
                         {
                             return Json.object("target", target.id, "status", "already_tidy",
                                     "note", "The layout already matches what an automatic "
                                           + "arrangement would produce; nothing was offered.");
                         }
                         Workspace.Proposal proposal = Workspace.propose(target.id,
                                 "it would move " + moving
                               + (moving == 1? " state" : " states")
                               + " into columns from the start state", positions);
                         target.frame.setBanner(new ProposalBanner(target.frame, proposal));
                         target.panel.repaint();
                         Workspace.log("Claude suggests a tidier layout for " + target.title);
                         return Json.object(
                                 "target", target.id,
                                 "status", "proposed",
                                 "proposal_id", proposal.id,
                                 "note", "The user has a banner in their tab offering this. Carry on "
                                       + "with what you were doing; get_workspace will show whether "
                                       + "they took it.");
                     }

                     int moved = Layout.all(target.machine);
                     if (target.isTab())
                     {
                         target.panel.repaint();
                         target.panel.setModifiedSinceSave(true);
                     }
                     else
                     {
                         Workspace.draftChanged(target.title);
                     }
                     return Json.object("target", target.id, "status", "applied", "scope", "all",
                             "moved", Integer.valueOf(moved),
                             "layout", Legibility.report(target.machine));
                 }
             });

        tool("open_in_app",
             "Open one of your drafts as a tab in front of the user. Use it when a draft is worth "
           + "showing -- usually after run_tests says it works.",
             schema("target!", string("A draft id, e.g. \"draft:factorial\"."),
                    "title", string("What to call the tab.")),
             new SwingHandler()
             {
                 Object call(Object args) throws Exception
                 {
                     String name = Json.str(args, "target", "");
                     Workspace.Target target = Workspace.resolve(name);
                     if (target.isTab())
                     {
                         throw new AgentException(target.id + " is already open as a tab.");
                     }
                     String title = Json.str(args, "title", target.title);
                     String id = Workspace.openInApp(target.machine, title);
                     Workspace.removeDraft(target.title);
                     Workspace.log("Claude opened " + title);
                     return Json.object("target", id, "title", title,
                             "states", Integer.valueOf(target.machine.getStates().size()),
                             "diagnosis", Diagnosis.summary(target.machine));
                 }
             });
    }

    /* ---------------------------------------------------------------- *
     * Running
     * ---------------------------------------------------------------- */

    private static void running()
    {
        tool("run_tests",
             "Run inputs through a COPY of a machine, away from the app. Nothing on the user's "
           + "screen moves, their tape is untouched, and it is fast enough to run thousands of "
           + "cases. This is how you check whether a machine works and how you iterate on one. For "
           + "showing the user a machine working, use run_in_app instead.",
             schema("target", TARGET,
                    "cases!", Json.object(
                        "type", "array",
                        "description", "Each entry is either a plain input string, or an object with "
                                     + "an expected result.",
                        "items", Json.object("oneOf", Json.array(
                            Json.object("type", "string"),
                            schema("input!", string("The input to write on the tape."),
                                   "expect", Json.object(
                                       "description", "\"accept\", \"reject\", or {\"tape\": \"the "
                                                    + "tape you expect at the end\"}."))))),
                    "max_steps", integer("Per case. Default 1,000,000. Exceeding it is reported as "
                                       + "\"over_budget\", which is not the same as a failure."),
                    "timeout_seconds", integer("For the whole batch. Default 30, at most 600."),
                    "trace", enumeration("Default \"failures\".", "none", "failures", "all")),
             new Handler()
             {
                 public Object run(Object args) throws Exception
                 {
                     // The machine is read on the Swing thread and copied there; the running happens
                     // off it, so a long batch never holds up the window.
                     final String targetName = Json.str(args, "target", null);
                     final Object[] found = new Object[2];
                     Workspace.onSwingThread(new Workspace.Job<Object>()
                     {
                         public Object run() throws Exception
                         {
                             Workspace.Target target = Workspace.resolve(targetName);
                             found[0] = target.id;
                             found[1] = Machines.copy(target.machine);
                             return null;
                         }
                     });
                     String id = (String)found[0];
                     Machine machine = (Machine)found[1];

                     long maxSteps = Math.max(1, Math.min(Sandbox.MAX_MAX_STEPS,
                                 Json.num(args, "max_steps", Sandbox.DEFAULT_MAX_STEPS)));
                     long timeout = Math.max(1, Math.min(Sandbox.MAX_TIMEOUT_MS / 1000,
                                 Json.num(args, "timeout_seconds", Sandbox.DEFAULT_TIMEOUT_MS / 1000)));
                     long deadline = System.nanoTime() + timeout * 1000000000L;
                     String traceMode = Json.str(args, "trace", "failures");

                     List<Object> results = new ArrayList<Object>();
                     int passed = 0;
                     int failed = 0;
                     int overBudget = 0;
                     int notRun = 0;
                     long totalSteps = 0;
                     long started = System.nanoTime();

                     List<Object> cases = Json.arr(args, "cases");
                     for (Object entry : cases)
                     {
                         boolean plain = !(entry instanceof Map);
                         String input = plain? String.valueOf(entry) : Json.str(entry, "input", "");
                         Object expect = plain? null : Json.member(entry, "expect");

                         if (System.nanoTime() > deadline)
                         {
                             notRun++;
                             continue;
                         }

                         boolean wantTrace = "all".equals(traceMode)
                                 || (!"none".equals(traceMode));
                         Sandbox.Result r = Sandbox.run(machine, input, maxSteps, deadline, wantTrace);
                         totalSteps += r.steps;

                         Boolean pass = null;
                         if ("accept".equals(expect))
                         {
                             pass = Boolean.valueOf(r.accepted());
                         }
                         else if ("reject".equals(expect))
                         {
                             pass = Boolean.valueOf(!r.accepted());
                         }
                         else if (expect instanceof Map && Json.has(expect, "tape"))
                         {
                             pass = Boolean.valueOf(r.accepted()
                                     && r.finalTape.equals(Sandbox.normalise(Json.str(expect, "tape", ""))));
                         }

                         if ("over_budget".equals(r.outcome) || "timeout".equals(r.outcome))
                         {
                             overBudget++;
                         }
                         else if (Boolean.TRUE.equals(pass))
                         {
                             passed++;
                         }
                         else if (Boolean.FALSE.equals(pass))
                         {
                             failed++;
                         }

                         Map<String, Object> row = Json.object(
                                 "input", input,
                                 "outcome", r.outcome,
                                 "steps", Long.valueOf(r.steps),
                                 "final_tape", r.finalTape,
                                 "head", Integer.valueOf(r.head));
                         if (r.reason != null)
                         {
                             row.put("reason", r.reason);
                         }
                         if (expect != null)
                         {
                             row.put("expected", expect);
                             row.put("pass", pass);
                         }
                         boolean showTrace = "all".equals(traceMode)
                                 || ("failures".equals(traceMode)
                                     && (Boolean.FALSE.equals(pass)
                                         || (pass == null && !r.accepted())));
                         if (showTrace && !r.trace.isEmpty())
                         {
                             row.put("trace", r.trace);
                         }
                         results.add(row);
                     }

                     double seconds = (System.nanoTime() - started) / 1e9;
                     Map<String, Object> out = Json.object(
                             "target", id,
                             "passed", Integer.valueOf(passed),
                             "failed", Integer.valueOf(failed),
                             "over_budget", Integer.valueOf(overBudget),
                             "results", results);
                     if (notRun > 0)
                     {
                         out.put("not_run", Integer.valueOf(notRun));
                         out.put("note", notRun + " case(s) were not run: the batch ran out of time. "
                                 + "Raise timeout_seconds, or run fewer cases at once.");
                     }
                     if (seconds > 0.05)
                     {
                         out.put("steps_per_second", Long.valueOf((long)(totalSteps / seconds)));
                     }
                     return out;
                 }
             });

        tool("run_in_app",
             "Drive the simulation the USER is watching: load an input onto their tape and step or "
           + "play the machine on screen. Use this when the point is for them to see it work. For "
           + "checking whether a machine is correct use run_tests, which is far faster and disturbs "
           + "nothing. \"play\" returns as soon as the machine starts; it does not wait for it to "
           + "halt, except at \"instant\" speed, which runs to a halt before it returns.",
             schema("target", TARGET,
                    "input", string("Written to the tape first, with the head reset to 0."),
                    "action!", enumeration("What to do.", "step", "play", "pause", "stop"),
                    "speed", enumeration("How fast to play. \"instant\" runs it through in one go "
                                       + "rather than animating it.",
                                         "slow", "medium", "fast", "superfast", "ultrafast", "instant"),
                    "max_steps", integer("How many steps \"instant\" may take before it gives up on "
                                       + "a machine that is not going to halt. Default 1,000,000. "
                                       + "The other speeds run on a timer and ignore it.")),
             new SwingHandler()
             {
                 Object call(Object args) throws Exception
                 {
                     Workspace.Target target = Workspace.resolve(Json.str(args, "target", null));
                     if (!target.isTab())
                     {
                         throw new AgentException(target.id + " is a draft, so there is nothing on "
                                 + "screen to run. Call open_in_app first, or use run_tests.");
                     }
                     MainWindow window = MainWindow.getInstance();
                     window.getTabPane().setSelectedComponent(target.frame);

                     // Nobody is necessarily watching the screen, and a message box waiting to be
                     // dismissed would hold the event thread against every later request. The
                     // console still records everything the boxes would have said. The user gets
                     // them back the moment they run the machine themselves.
                     Global.setMessagesSuppressed(true);

                     String input = Json.str(args, "input", null);
                     if (input != null)
                     {
                         window.getTape().copyOther(new CA_Tape(Sandbox.normalise(input)));
                         target.panel.getSimulator().resetMachine();
                     }
                     String speed = Json.str(args, "speed", null);
                     if (speed != null && !window.setExecutionSpeedByName(speed))
                     {
                         throw new AgentException("\"" + speed + "\" is not a speed. Use slow, "
                                 + "medium, fast, superfast, ultrafast or instant.");
                     }

                     String action = Json.str(args, "action", "step");
                     if ("stop".equals(action))
                     {
                         window.m_stopMachineAction.actionPerformed(null);
                     }
                     else if ("pause".equals(action))
                     {
                         window.m_pauseExecutionAction.actionPerformed(null);
                     }
                     else if ("play".equals(action))
                     {
                         double limit = Json.num(args, "max_steps", Sandbox.DEFAULT_MAX_STEPS);
                         if (limit < 1)
                         {
                             throw new AgentException("max_steps must be at least 1.");
                         }
                         window.startExecution(target.panel,
                                 (int)Math.min(Integer.MAX_VALUE, limit));
                     }
                     else if ("step".equals(action))
                     {
                         window.m_stepAction.actionPerformed(null);
                     }
                     else
                     {
                         throw new AgentException("\"" + action + "\" is not an action. Use step, "
                                 + "play, pause or stop.");
                     }

                     Tape tape = window.getTape();
                     State current = (State)target.panel.getSimulator().getCurrentState();
                     return Json.object(
                             "target", target.id,
                             "action", action,
                             "status", window.isExecuting()? "running" : "stopped",
                             "tape", tape.getPartialString(0, tape.getLength()),
                             "head", Integer.valueOf(tape.headLocation()),
                             "current_state", current == null? null : current.getLabel());
                 }
             });

        tool("set_tape",
             "Set the tape the user sees. All open machines share this one tape.",
             schema("content!", string("What to write on it."),
                    "head", integer("Where to put the read/write head. Default 0.")),
             new SwingHandler()
             {
                 Object call(Object args) throws Exception
                 {
                     MainWindow window = MainWindow.getInstance();
                     if (window == null)
                     {
                         throw new AgentException("Tuatara is not running.");
                     }
                     String content = Sandbox.normalise(Json.str(args, "content", ""));
                     Tape tape = window.getTape();
                     tape.copyOther(new CA_Tape(content));
                     int head = (int)Json.num(args, "head", 0);
                     for (int i = 0; i < head; i++)
                     {
                         tape.headRight();
                     }
                     window.getTapeDisplay().repaint();
                     return Json.object("tape", tape.getPartialString(0, tape.getLength()),
                             "head", Integer.valueOf(tape.headLocation()));
                 }
             });
    }

    /* ---------------------------------------------------------------- *
     * The app itself
     * ---------------------------------------------------------------- */

    private static void theApp()
    {
        tool("set_config",
             "Change Tuatara's settings. Pass only the ones you mean to change.",
             schema("theme", enumeration("Light or dark.", "light", "dark"),
                    "console", bool("Show the console."),
                    "status_bar", bool("Show the status bar."),
                    "tape", bool("Show the tape strip."),
                    "zoom", Json.object("type", "number", "description",
                        "Zoom for the machine in front, between 0.25 and 4."),
                    "speed", enumeration("Execution speed.",
                        "slow", "medium", "fast", "superfast", "ultrafast", "instant"),
                    "tool", enumeration("Which toolbar tool is selected.",
                        "selection", "add_state", "add_transition", "eraser", "start_state",
                        "final_state", "current_state")),
             new SwingHandler()
             {
                 Object call(Object args) throws Exception
                 {
                     MainWindow window = MainWindow.getInstance();
                     if (window == null)
                     {
                         throw new AgentException("Tuatara is not running.");
                     }
                     if (Json.has(args, "theme"))
                     {
                         Theme.set("dark".equals(Json.str(args, "theme", "light"))
                                 ? Theme.DARK : Theme.LIGHT);
                     }
                     for (String panel : new String[] { "console", "status_bar", "tape" })
                     {
                         if (Json.has(args, panel))
                         {
                             window.setPanelVisible(panel, Json.bool(args, panel, true));
                         }
                     }
                     if (Json.has(args, "speed") && !window.setExecutionSpeedByName(
                                 Json.str(args, "speed", "")))
                     {
                         throw new AgentException("\"" + Json.str(args, "speed", "")
                                 + "\" is not a speed.");
                     }
                     if (Json.has(args, "zoom"))
                     {
                         MachineGraphicsPanel panel = window.getSelectedGraphicsPanel();
                         if (panel != null)
                         {
                             Object raw = Json.member(args, "zoom");
                             double zoom = raw instanceof Number? ((Number)raw).doubleValue() : 1.0;
                             panel.setZoom(Math.max(MachineGraphicsPanel.ZOOM_MIN,
                                         Math.min(MachineGraphicsPanel.ZOOM_MAX, zoom)), null);
                         }
                     }
                     if (Json.has(args, "tool"))
                     {
                         String name = Json.str(args, "tool", "");
                         GUI_Mode mode = modeFor(name);
                         if (mode == null)
                         {
                             throw new AgentException("\"" + name + "\" is not a tool.");
                         }
                         window.setUIMode(mode);
                     }
                     return Json.object("config", Json.member(Workspace.snapshot(), "config"));
                 }
             });

        tool("save_machine",
             "Save a machine to a file: .tm for a Turing machine, .fsa for an acceptor.",
             schema("target", TARGET,
                    "path", string("Where to write it. Defaults to the file the tab came from.")),
             new SwingHandler()
             {
                 Object call(Object args) throws Exception
                 {
                     Workspace.Target target = Workspace.resolve(Json.str(args, "target", null));
                     String path = Json.str(args, "path", null);
                     if (path == null && target.isTab() && target.panel.getFile() != null)
                     {
                         path = target.panel.getFile().getAbsolutePath();
                     }
                     if (path == null)
                     {
                         throw new AgentException("That machine has no file yet -- pass a path. "
                                 + "Turing machines end in .tm, acceptors in .fsa.");
                     }
                     String wanted = Doc.isAcceptor(target.machine)
                             ? DFSAGraphicsPanel.MACHINE_EXT : TMGraphicsPanel.MACHINE_EXT;
                     if (!path.toLowerCase().endsWith(wanted))
                     {
                         path = path + wanted;
                     }
                     File file = new File(path);
                     Machine.saveMachine(target.machine, file);
                     if (target.isTab())
                     {
                         target.panel.setFile(file);
                         target.panel.setModifiedSinceSave(false);
                         MainWindow.getInstance().refreshTab(target.frame);
                     }
                     Workspace.log("Claude saved " + target.title + " to " + file.getName());
                     return Json.object("target", target.id, "path", file.getAbsolutePath(),
                             "saved", Boolean.TRUE);
                 }
             });

        tool("open_machine",
             "Open a .tm or .fsa file from disk as a new tab. The format is Java's own, so this is "
           + "the only way to read one.",
             schema("path!", string("The file to open.")),
             new SwingHandler()
             {
                 Object call(Object args) throws Exception
                 {
                     File file = new File(Json.str(args, "path", ""));
                     if (!file.isFile())
                     {
                         throw new AgentException("There is no file at " + file.getAbsolutePath());
                     }
                     Machine machine;
                     try
                     {
                         machine = Machine.loadMachine(file);
                     }
                     catch (Exception e)
                     {
                         throw new AgentException("That file could not be read as a machine: "
                                 + e.getClass().getSimpleName()
                                 + (e.getMessage() == null? "" : " -- " + e.getMessage()));
                     }
                     MainWindow window = MainWindow.getInstance();
                     MachineGraphicsPanel panel = Doc.isAcceptor(machine)
                         ? (MachineGraphicsPanel)new DFSAGraphicsPanel(
                                 (DFSA_Machine)machine, window.getTape(), file)
                         : (MachineGraphicsPanel)new TMGraphicsPanel(
                                 (TM_Machine)machine, window.getTape(), file);
                     // It came from a file somebody made, so treat its layout as theirs.
                     panel.setHandPositioned(true);
                     for (Object o : machine.getStates())
                     {
                         panel.addLabelToDictionary(((State)o).getLabel());
                     }
                     MachineInternalFrame frame = window.newMachineWindow(panel);
                     window.addFrame(frame);
                     Workspace.Target target = Workspace.resolve(frame.getTitle());
                     return Json.object("target", target.id, "title", frame.getTitle(),
                             "states", Integer.valueOf(machine.getStates().size()),
                             "diagnosis", Diagnosis.summary(machine));
                 }
             });

        tool("log_to_console",
             "Write a line into Tuatara's console, where the user is already looking. Worth using "
           + "for a result they should see next to their machine -- not for narrating your work.",
             schema("message!", string("What to say.")),
             new SwingHandler()
             {
                 Object call(Object args)
                 {
                     Workspace.log(Json.str(args, "message", ""));
                     return Json.object("logged", Boolean.TRUE);
                 }
             });
    }

    /* ---------------------------------------------------------------- *
     * Odds and ends
     * ---------------------------------------------------------------- */

    private static GUI_Mode modeFor(String name)
    {
        if ("selection".equals(name))      return GUI_Mode.SELECTION;
        if ("add_state".equals(name))      return GUI_Mode.ADDNODES;
        if ("add_transition".equals(name)) return GUI_Mode.ADDTRANSITIONS;
        if ("eraser".equals(name))         return GUI_Mode.ERASER;
        if ("start_state".equals(name))    return GUI_Mode.CHOOSESTART;
        if ("final_state".equals(name))    return GUI_Mode.CHOOSEFINAL;
        if ("current_state".equals(name))  return GUI_Mode.CHOOSECURRENTSTATE;
        return null;
    }

    /**
     * A machine containing only the states within a given distance of one of them, so that a
     * machine too large to read whole can still be looked at a piece at a time.
     * @param machine The machine to cut down.
     * @param around The label of the state to centre on.
     * @param depth How many transitions out to go.
     * @return A copy holding only that neighbourhood.
     * @throws AgentException If there is no such state.
     */
    private static Machine neighbourhood(Machine machine, String around, int depth)
        throws AgentException
    {
        if (Machines.state(machine, around) == null)
        {
            throw new AgentException("There is no state called \"" + around + "\" in that machine.");
        }
        java.util.Set<String> keep = new java.util.LinkedHashSet<String>();
        keep.add(around);
        for (int step = 0; step < Math.max(0, depth); step++)
        {
            java.util.Set<String> next = new java.util.LinkedHashSet<String>(keep);
            for (Object o : machine.getTransitions())
            {
                Transition t = (Transition)o;
                String from = ((State)t.getFromState()).getLabel();
                String to = ((State)t.getToState()).getLabel();
                if (keep.contains(from))
                {
                    next.add(to);
                }
                if (keep.contains(to))
                {
                    next.add(from);
                }
            }
            keep = next;
        }

        Machine copy = Machines.copy(machine);
        for (Object o : new ArrayList<Object>(copy.getStates()))
        {
            State s = (State)o;
            if (!keep.contains(s.getLabel()))
            {
                removeState(copy, s);
            }
        }
        return copy;
    }

    @SuppressWarnings("unchecked")
    private static void removeState(Machine machine, State state)
    {
        machine.deleteState(state);
    }

    private static String join(List<String> parts, String separator)
    {
        StringBuilder sb = new StringBuilder();
        for (String part : parts)
        {
            sb.append(sb.length() == 0? "" : separator).append(part);
        }
        return sb.toString();
    }
}
