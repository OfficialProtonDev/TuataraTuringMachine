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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import tuataraTMSim.*;
import tuataraTMSim.machine.*;
import tuataraTMSim.machine.DFSA.*;
import tuataraTMSim.machine.TM.*;

/**
 * What an agent can see and change in a running Tuatara.
 *
 * Two kinds of machine live here. A tab is one the user has in front of them, with an undo stack
 * and a place on screen. A draft is one only the agent knows about: somewhere to build and test
 * without turning somebody's workspace into a scratchpad. Everything else -- the tape, the
 * settings, the console -- there is only one of, and it belongs to the user.
 *
 * Everything in this class touches Swing, so everything in this class runs on the event dispatch
 * thread. {@link #onSwingThread} is how callers get there.
 */
public final class Workspace
{
    /**
     * A machine an agent has been asked to work on: either a tab or one of its own drafts.
     */
    public static final class Target
    {
        /**
         * How to refer to this machine: "tab:3" or "draft:factorial".
         */
        public final String id;

        /**
         * What it is called.
         */
        public final String title;

        /**
         * The machine itself.
         */
        public final Machine machine;

        /**
         * The panel showing it, or null for a draft.
         */
        public final MachineGraphicsPanel panel;

        /**
         * The tab it belongs to, or null for a draft.
         */
        public final MachineInternalFrame frame;

        Target(String id, String title, Machine machine, MachineGraphicsPanel panel,
               MachineInternalFrame frame)
        {
            this.id = id;
            this.title = title;
            this.machine = machine;
            this.panel = panel;
            this.frame = frame;
        }

        /**
         * Determine whether this machine is one the user can see.
         * @return true if it is open in a tab.
         */
        public boolean isTab()
        {
            return panel != null;
        }
    }

    /**
     * A machine the agent is working on which the user cannot see.
     */
    public static final class Draft
    {
        /**
         * The name the agent gave it.
         */
        public final String id;

        /**
         * The machine.
         */
        public Machine machine;

        Draft(String id, Machine machine)
        {
            this.id = id;
            this.machine = machine;
        }
    }

    /**
     * Something an agent has offered the user, which they may take or leave. Nothing here is ever
     * applied without the user saying so, and nothing here blocks the agent waiting for an answer.
     */
    public static final class Proposal
    {
        /**
         * How to refer to it.
         */
        public final String id;

        /**
         * The machine it is about.
         */
        public final String target;

        /**
         * What kind of offer it is; at present always "layout".
         */
        public final String kind;

        /**
         * "awaiting_user", "applied" or "dismissed".
         */
        public String status = "awaiting_user";

        /**
         * A one-line description of what taking it would do.
         */
        public final String summary;

        /**
         * Where each state would move to, keyed by label.
         */
        public final Map<String, int[]> positions;

        Proposal(String id, String target, String kind, String summary, Map<String, int[]> positions)
        {
            this.id = id;
            this.target = target;
            this.kind = kind;
            this.summary = summary;
            this.positions = positions;
        }
    }

    /**
     * The drafts, in the order they were made.
     */
    private static final Map<String, Draft> DRAFTS = new LinkedHashMap<String, Draft>();

    /**
     * Offers waiting on the user.
     */
    private static final List<Proposal> PROPOSALS = new ArrayList<Proposal>();

    /**
     * Counter behind proposal ids.
     */
    private static int proposalCount = 0;

    /**
     * Not instantiable.
     */
    private Workspace() { }

    /* ---------------------------------------------------------------- *
     * Getting onto the event thread
     * ---------------------------------------------------------------- */

    /**
     * Work to do on the Swing thread, which may fail.
     * @param <T> What the work produces.
     */
    public interface Job<T>
    {
        /**
         * Do the work.
         * @return The result.
         * @throws Exception If the work could not be done.
         */
        T run() throws Exception;
    }

    /**
     * Run a job on the event dispatch thread and wait for its answer.
     *
     * Every read of the model has to happen here as much as every write: a snapshot taken while the
     * user is dragging a state would be a picture of a machine that never existed.
     * @param <T> What the job produces.
     * @param job The work to do.
     * @return Whatever the job returned.
     * @throws Exception Whatever the job threw.
     */
    public static <T> T onSwingThread(final Job<T> job) throws Exception
    {
        if (javax.swing.SwingUtilities.isEventDispatchThread())
        {
            return job.run();
        }
        final Object[] result = new Object[1];
        final Exception[] failure = new Exception[1];
        javax.swing.SwingUtilities.invokeAndWait(new Runnable()
        {
            public void run()
            {
                try
                {
                    result[0] = job.run();
                }
                catch (Exception e)
                {
                    failure[0] = e;
                }
            }
        });
        if (failure[0] != null)
        {
            throw failure[0];
        }
        @SuppressWarnings("unchecked")
        T typed = (T)result[0];
        return typed;
    }

    /* ---------------------------------------------------------------- *
     * Finding a machine
     * ---------------------------------------------------------------- */

    /**
     * Work out which machine a request is about.
     * @param target A tab id, a tab title, a draft id, or null for whichever tab is in front.
     * @return The machine.
     * @throws AgentException If there is no such machine, with a message saying what there is.
     */
    public static Target resolve(String target) throws AgentException
    {
        MainWindow window = MainWindow.getInstance();
        List<MachineInternalFrame> documents = window == null
            ? new ArrayList<MachineInternalFrame>() : window.getOpenDocuments();

        if (target == null || target.trim().isEmpty())
        {
            MachineGraphicsPanel panel = window == null? null : window.getSelectedGraphicsPanel();
            if (panel == null)
            {
                throw new AgentException(
                        "No machine is open in Tuatara and you did not name one. Use create_machine "
                      + "to make one, or call get_workspace to see what there is." + available());
            }
            return forPanel(documents, panel);
        }

        String name = target.trim();
        if (name.startsWith("draft:"))
        {
            name = name.substring("draft:".length());
        }
        Draft draft = DRAFTS.get(name);
        if (draft != null)
        {
            return new Target("draft:" + draft.id, draft.id, draft.machine, null, null);
        }

        for (int i = 0; i < documents.size(); i++)
        {
            MachineInternalFrame frame = documents.get(i);
            if (target.equals("tab:" + (i + 1)) || target.equals(frame.getTitle()))
            {
                return new Target("tab:" + (i + 1), frame.getTitle(),
                        frame.getGfxPanel().getSimulator().getMachine(), frame.getGfxPanel(), frame);
            }
        }
        throw new AgentException("Nothing called \"" + target + "\"." + available());
    }

    private static Target forPanel(List<MachineInternalFrame> documents, MachineGraphicsPanel panel)
    {
        for (int i = 0; i < documents.size(); i++)
        {
            if (documents.get(i).getGfxPanel() == panel)
            {
                return new Target("tab:" + (i + 1), documents.get(i).getTitle(),
                        panel.getSimulator().getMachine(), panel, documents.get(i));
            }
        }
        return new Target("tab:?", panel.getFilename(), panel.getSimulator().getMachine(), panel, null);
    }

    /**
     * A description of everything an agent could name, for when it named something else.
     * @return A sentence listing the open tabs and drafts.
     */
    public static String available()
    {
        StringBuilder sb = new StringBuilder();
        MainWindow window = MainWindow.getInstance();
        List<MachineInternalFrame> documents = window == null
            ? new ArrayList<MachineInternalFrame>() : window.getOpenDocuments();
        sb.append(" Open tabs: ");
        if (documents.isEmpty())
        {
            sb.append("(none)");
        }
        else
        {
            for (int i = 0; i < documents.size(); i++)
            {
                sb.append(i == 0? "" : ", ");
                sb.append("tab:").append(i + 1).append(" (\"").append(documents.get(i).getTitle()).append("\")");
            }
        }
        sb.append(". Drafts: ");
        if (DRAFTS.isEmpty())
        {
            sb.append("(none)");
        }
        else
        {
            boolean first = true;
            for (String id : DRAFTS.keySet())
            {
                sb.append(first? "" : ", ").append("draft:").append(id);
                first = false;
            }
        }
        sb.append('.');
        return sb.toString();
    }

    /* ---------------------------------------------------------------- *
     * Drafts
     * ---------------------------------------------------------------- */

    /**
     * Keep a machine as a draft, replacing any draft of the same name.
     * @param id What to call it.
     * @param machine The machine.
     * @return The draft.
     */
    public static Draft putDraft(String id, Machine machine)
    {
        Draft draft = new Draft(id, machine);
        DRAFTS.put(id, draft);
        save(draft);
        return draft;
    }

    /**
     * Where drafts are kept between runs.
     * @return The directory, which may not exist.
     */
    private static java.io.File draftDir()
    {
        return new java.io.File(new java.io.File(System.getProperty("user.home", "."), ".tuatara"),
                "drafts");
    }

    /**
     * Write a draft to disk.
     *
     * A machine an assistant has been iterating on for twenty calls is worth more than the minute
     * it took to write it, and closing the program should not throw it away. Saved as a document
     * rather than in the program's own format, which keeps them readable and side-steps the file
     * format entirely.
     * @param draft The draft to save.
     */
    private static void save(Draft draft)
    {
        try
        {
            java.io.File dir = draftDir();
            if (!dir.isDirectory() && !dir.mkdirs())
            {
                return;
            }
            java.io.FileOutputStream out =
                new java.io.FileOutputStream(new java.io.File(dir, draft.id + ".json"));
            try
            {
                out.write(Json.writePretty(
                            Doc.toJson(draft.machine, draft.id, true), 2).getBytes("UTF-8"));
            }
            finally
            {
                out.close();
            }
        }
        catch (Exception e)
        {
            // Losing a draft is a nuisance, not a failure worth interrupting anybody over.
        }
    }

    /**
     * Read back the drafts left behind by an earlier run. Called once, as the window starts.
     */
    public static void loadDrafts()
    {
        java.io.File dir = draftDir();
        java.io.File[] files = dir.listFiles();
        if (files == null)
        {
            return;
        }
        for (java.io.File file : files)
        {
            if (!file.getName().endsWith(".json"))
            {
                continue;
            }
            try
            {
                byte[] bytes = new byte[(int)file.length()];
                java.io.FileInputStream in = new java.io.FileInputStream(file);
                try
                {
                    int read = 0;
                    while (read < bytes.length)
                    {
                        int n = in.read(bytes, read, bytes.length - read);
                        if (n < 0)
                        {
                            break;
                        }
                        read += n;
                    }
                }
                finally
                {
                    in.close();
                }
                List<String> errors = new ArrayList<String>();
                Machine machine = Doc.build(Json.parse(new String(bytes, "UTF-8")), errors);
                if (machine != null && errors.isEmpty())
                {
                    String id = file.getName().substring(0, file.getName().length() - 5);
                    DRAFTS.put(id, new Draft(id, machine));
                }
            }
            catch (Exception e)
            {
                // A draft that will not read back is not worth stopping the program for.
            }
        }
    }

    /**
     * Forget a draft.
     * @param id The draft to forget.
     * @return true if there was one.
     */
    public static boolean removeDraft(String id)
    {
        java.io.File file = new java.io.File(draftDir(), id + ".json");
        if (file.isFile() && !file.delete())
        {
            file.deleteOnExit();
        }
        return DRAFTS.remove(id) != null;
    }

    /**
     * Write a draft back to disk after it has been changed.
     * @param id The draft that changed.
     */
    public static void draftChanged(String id)
    {
        Draft draft = DRAFTS.get(id);
        if (draft != null)
        {
            save(draft);
        }
    }

    /**
     * Every draft, in the order they were made.
     * @return The drafts.
     */
    public static List<Draft> drafts()
    {
        return new ArrayList<Draft>(DRAFTS.values());
    }

    /**
     * Turn a name into one that can be used as a draft id.
     * @param suggested What the agent asked for, or null.
     * @param fallback What to use if that is unusable.
     * @return A usable id.
     */
    public static String draftId(String suggested, String fallback)
    {
        String base = suggested == null || suggested.trim().isEmpty()? fallback : suggested;
        if (base.startsWith("draft:"))
        {
            base = base.substring("draft:".length());
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < base.length() && sb.length() < 48; i++)
        {
            char c = base.charAt(i);
            sb.append(Character.isLetterOrDigit(c) || c == '-' || c == '_'? c : '-');
        }
        String id = sb.toString().replaceAll("^-+|-+$", "");
        return id.isEmpty()? "draft" : id;
    }

    /* ---------------------------------------------------------------- *
     * Opening a machine in front of the user
     * ---------------------------------------------------------------- */

    /**
     * Open a machine as a new tab.
     * @param machine The machine to show.
     * @param title What to call the tab.
     * @return The tab's id.
     */
    public static String openInApp(Machine machine, String title)
    {
        MainWindow window = MainWindow.getInstance();
        MachineGraphicsPanel panel = Doc.isAcceptor(machine)
            ? (MachineGraphicsPanel)new DFSAGraphicsPanel((DFSA_Machine)machine, window.getTape(), null)
            : (MachineGraphicsPanel)new TMGraphicsPanel((TM_Machine)machine, window.getTape(), null);

        // Everything in it was placed by the layout, not by the user, so a later rearrangement does
        // not need to ask. Dragging any state changes that.
        panel.setHandPositioned(false);
        if (title != null && !title.trim().isEmpty())
        {
            panel.setDocumentName(title.trim());
        }
        for (Object o : machine.getStates())
        {
            panel.addLabelToDictionary(((State)o).getLabel());
        }

        MachineInternalFrame frame = window.newMachineWindow(panel);
        window.addFrame(frame);
        panel.setModifiedSinceSave(true);

        List<MachineInternalFrame> documents = window.getOpenDocuments();
        for (int i = 0; i < documents.size(); i++)
        {
            if (documents.get(i) == frame)
            {
                return "tab:" + (i + 1);
            }
        }
        return "tab:?";
    }

    /* ---------------------------------------------------------------- *
     * Proposals
     * ---------------------------------------------------------------- */

    /**
     * Offer the user a rearrangement of a machine they positioned themselves.
     * @param target The machine it applies to.
     * @param summary A line describing what it would do.
     * @param positions Where each state would go, keyed by label.
     * @return The proposal.
     */
    public static Proposal propose(String target, String summary, Map<String, int[]> positions)
    {
        // Only one layout offer per machine at a time; a second would just be two banners arguing.
        for (int i = PROPOSALS.size() - 1; i >= 0; i--)
        {
            Proposal existing = PROPOSALS.get(i);
            if (existing.target.equals(target) && "awaiting_user".equals(existing.status))
            {
                PROPOSALS.remove(i);
            }
        }
        Proposal proposal = new Proposal("p" + (++proposalCount), target, "layout", summary, positions);
        PROPOSALS.add(proposal);
        return proposal;
    }

    /**
     * Every proposal, including ones already answered.
     * @return The proposals.
     */
    public static List<Proposal> proposals()
    {
        return new ArrayList<Proposal>(PROPOSALS);
    }

    /**
     * The offer waiting on the user for a given machine.
     * @param target The machine.
     * @return The proposal, or null if there is none outstanding.
     */
    public static Proposal pendingFor(String target)
    {
        for (Proposal p : PROPOSALS)
        {
            if (p.target.equals(target) && "awaiting_user".equals(p.status))
            {
                return p;
            }
        }
        return null;
    }

    /**
     * Find a proposal by its id.
     * @param id The id to look for.
     * @return The proposal, or null.
     */
    public static Proposal proposal(String id)
    {
        for (Proposal p : PROPOSALS)
        {
            if (p.id.equals(id))
            {
                return p;
            }
        }
        return null;
    }

    /**
     * Drop a proposal that no longer makes sense, because the machine it described has changed.
     * @param target The machine that changed.
     */
    public static void invalidateProposals(String target)
    {
        boolean removed = false;
        for (int i = PROPOSALS.size() - 1; i >= 0; i--)
        {
            if (PROPOSALS.get(i).target.equals(target)
                    && "awaiting_user".equals(PROPOSALS.get(i).status))
            {
                PROPOSALS.remove(i);
                removed = true;
            }
        }
        if (!removed)
        {
            return;
        }
        // The banner described a machine that no longer exists in that shape, so it goes too.
        try
        {
            Target t = resolve(target);
            if (t.frame != null && t.frame.getBanner() != null)
            {
                t.frame.getBanner().close();
            }
        }
        catch (AgentException e)
        {
            // The tab has gone; there is no banner to take down.
        }
    }

    /* ---------------------------------------------------------------- *
     * The snapshot
     * ---------------------------------------------------------------- */

    /**
     * Everything an agent needs to know about the state of the program.
     * @return A JSON object.
     */
    public static Map<String, Object> snapshot()
    {
        MainWindow window = MainWindow.getInstance();
        Map<String, Object> out = Json.object();
        out.put("connected", Boolean.TRUE);
        out.put("version", Global.VERSION);

        List<Object> tabs = new ArrayList<Object>();
        List<MachineInternalFrame> documents = window == null
            ? new ArrayList<MachineInternalFrame>() : window.getOpenDocuments();
        MachineGraphicsPanel selected = window == null? null : window.getSelectedGraphicsPanel();
        for (int i = 0; i < documents.size(); i++)
        {
            MachineInternalFrame frame = documents.get(i);
            MachineGraphicsPanel panel = frame.getGfxPanel();
            Machine machine = panel.getSimulator().getMachine();
            String verdict = Diagnosis.verdict(machine);
            tabs.add(Json.object(
                        "id", "tab:" + (i + 1),
                        "title", frame.getTitle(),
                        "type", Doc.typeOf(machine),
                        "active", Boolean.valueOf(panel == selected),
                        "states", Integer.valueOf(machine.getStates().size()),
                        "transitions", Integer.valueOf(machine.getTransitions().size()),
                        "modified", Boolean.valueOf(panel.isModifiedSinceSave()),
                        "file", panel.getFile() == null? null : panel.getFile().getAbsolutePath(),
                        "hand_positioned", Boolean.valueOf(panel.isHandPositioned()),
                        "validation", verdict == null? "ok" : verdict));
        }
        out.put("tabs", tabs);

        List<Object> drafts = new ArrayList<Object>();
        for (Draft draft : DRAFTS.values())
        {
            drafts.add(Json.object(
                        "id", "draft:" + draft.id,
                        "type", Doc.typeOf(draft.machine),
                        "states", Integer.valueOf(draft.machine.getStates().size()),
                        "transitions", Integer.valueOf(draft.machine.getTransitions().size())));
        }
        out.put("drafts", drafts);

        Tape tape = window == null? null : window.getTape();
        out.put("tape", tape == null? Json.object()
                : Json.object("content", tape.getPartialString(0, tape.getLength()),
                              "head", Integer.valueOf(tape.headLocation()),
                              "length", Integer.valueOf(tape.getLength())));

        String status = "stopped";
        String runningIn = null;
        String currentState = null;
        if (window != null)
        {
            MachineGraphicsPanel executing = window.getExecutingPanel();
            if (executing != null)
            {
                status = "running";
                runningIn = forPanel(documents, executing).id;
            }
            MachineGraphicsPanel panel = window.getSelectedGraphicsPanel();
            if (panel != null && panel.getSimulator().getCurrentState() != null)
            {
                currentState = ((State)panel.getSimulator().getCurrentState()).getLabel();
                if (status.equals("stopped"))
                {
                    status = "part way through";
                }
            }
        }
        out.put("execution", Json.object(
                    "status", status,
                    "tab", runningIn,
                    "current_state", currentState,
                    "speed", window == null? "fast" : window.getExecutionSpeedName()));

        out.put("config", window == null? Json.object() : Json.object(
                    "theme", Theme.isDark()? "dark" : "light",
                    "console", Boolean.valueOf(window.isConsoleVisible()),
                    "status_bar", Boolean.valueOf(window.isStatusBarVisible()),
                    "tape", Boolean.valueOf(window.isTapeVisible()),
                    "zoom", selected == null? Double.valueOf(1.0) : Double.valueOf(selected.getZoom()),
                    "tool", window.getUIMode() == null? null
                            : window.getUIMode().toString().toLowerCase()));

        out.put("console_tail", window == null? new ArrayList<Object>()
                : new ArrayList<Object>(window.getConsole().tail(10)));

        List<Object> offers = new ArrayList<Object>();
        for (Proposal p : PROPOSALS)
        {
            offers.add(Json.object("id", p.id, "target", p.target, "kind", p.kind,
                        "status", p.status, "summary", p.summary));
        }
        out.put("proposals", offers);
        return out;
    }

    /**
     * Write a line to the console the user is looking at.
     * @param message What to say.
     */
    public static void log(String message)
    {
        MainWindow window = MainWindow.getInstance();
        if (window != null && window.getConsole() != null)
        {
            window.getConsole().log("%s", message);
        }
    }
}
