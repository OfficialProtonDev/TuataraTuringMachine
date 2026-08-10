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

package tuataraTMSim;

import java.awt.*;
import java.awt.event.*;
import java.awt.geom.Point2D;
import java.io.*;
import java.util.*;
import javax.swing.*;
import tuataraTMSim.commands.*;
import tuataraTMSim.exceptions.*;
import tuataraTMSim.machine.*;
import tuataraTMSim.machine.TM.*;
import tuataraTMSim.machine.DFSA.*;

/**
 * The main window of the program. A tabbed interface for building and running turing machines.
 * This class is the main entry point into the program.
 */
public class MainWindow extends JFrame
{
    /**
     * String for execution halting.
     */
    protected static final String HALTED_MESSAGE_TITLE_STR  = "Machine halted!";

    /**
     * Delay between steps for slow execution speed.
     */
    protected static final int SLOW_EXECUTE_SPEED_DELAY = 1200;

    /**
     * Delay between steps for medium execution speed.
     */
    protected static final int MEDIUM_EXECUTE_SPEED_DELAY = 800;

    /**
     * Delay between steps for fast execution speed.
     */
    protected static final int FAST_EXECUTE_SPEED_DELAY = 400;

    /**
     * Delay between steps for superfast execution speed.
     */
    protected static final int SUPERFAST_EXECUTE_SPEED_DELAY = 200;

    /**
     * Delay between steps for ultrafast execution speed.
     */
    protected static final int ULTRAFAST_EXECUTE_SPEED_DELAY = 10;

    /**
     * Delay between steps for zero-delay execution. A delay of zero is not a timer interval at all;
     * it selects running the machine straight through to a halt rather than stepping it on a timer.
     */
    protected static final int ZERO_EXECUTE_SPEED_DELAY = 0;


    /**
     * Width of the machine canvas.
     */
    protected static final int MACHINE_CANVAS_SIZE_X = 2000;

    /**
     * Height of the machine canvas.
     */
    protected static final int MACHINE_CANVAS_SIZE_Y = 2000;
   
    /**
     * Horizontal translation of states to avoid stacking.
     */
    protected static final int TRANSLATE_TO_AVOID_STACKING_X = State.STATE_RENDERING_WIDTH * 2;

    /**
     * Vertical translation of states to avoid stacking.
     */
    protected static final int TRANSLATE_TO_AVOID_STACKING_Y = State.STATE_RENDERING_WIDTH * 2;

    /**
     * Size the toolbar renders its icons at.
     */
    protected static final int TOOLBAR_ICON_SIZE = 18;

    /**
     * Size the menus render their icons at.
     */
    protected static final int MENU_ICON_SIZE = 16;

    /**
     * Creates a new instance of MainWindow.
     */
    public MainWindow()
    {
        m_instance = this;
        initComponents();
       
        // Whenever a mouse click occurs, deselect the selected action. If the action was clicked on
        // again, it will regain focus.
        addMouseListener(new MouseAdapter()
        {
            public void mousePressed(MouseEvent e)
            {
                MachineGraphicsPanel gfx = getSelectedGraphicsPanel();
                if (gfx != null)
                {
                    gfx.deselectSymbol();
                }
            }
        });

        // Handle global keyboard input
        final KeyboardFocusManager kfm = KeyboardFocusManager.getCurrentKeyboardFocusManager();
        
        kfm.addKeyEventPostProcessor(new KeyEventPostProcessor()
        {
           public boolean postProcessKeyEvent(KeyEvent e) 
           {       
               if (!m_keyboardEnabled)
               {
                   return false;
               }
               MachineGraphicsPanel gfxPanel = getSelectedGraphicsPanel();
               if (gfxPanel != null && !gfxPanel.getKeyboardEnabled())
               {
                   return false;
               }
               // Ignore anything with a ctrl or alt, as this may conflict with menu keyboard
               // shortcuts/accelerators
               if (e.isAltDown() || e.isControlDown())
               {
                   return false;
               }
               if (e.getID() == KeyEvent.KEY_TYPED ||
                    (e.getID() == KeyEvent.KEY_PRESSED &&
                      (e.isActionKey() ||
                       e.getKeyCode() == KeyEvent.VK_DELETE ||
                       e.getKeyCode() == KeyEvent.VK_BACK_SPACE)))
               {
                   // A modal dialog owns the keyboard while it is up; the alphabet selector now
                   // handles its own typing rather than having it routed here.
                   Window active = KeyboardFocusManager.getCurrentKeyboardFocusManager()
                       .getActiveWindow();
                   if (active != null && active != MainWindow.this)
                   {
                       return false;
                   }

                   if (gfxPanel != null)
                   {
                       if (gfxPanel.handleKeyEvent(e))
                       {
                           gfxPanel.repaint();
                       }
                       else if (m_tapeDisp != null)
                       {
                           m_tapeDisp.handleKeyEvent(e);
                       }
                   }
                   // No graphics panel, just a tape
                   else if (m_tapeDisp != null) 
                   {
                       m_tapeDisp.handleKeyEvent(e);
                   }
               }
               return false;
           }
        });
        
        // Check for unsaved machines on exit.
        addWindowListener(new WindowAdapter()
        {
             public void windowClosing(WindowEvent e)
             {
                 userRequestToExit();   
             }
        });
    }
    
    /**
     * Program entry point.
     * @param args Command line arguments. Currently, all are ignored.
     */
    public static void main(String[] args)
    {
        // Install the theme, which also chooses the look-and-feel, before building any component.
        Theme.install();

        java.awt.EventQueue.invokeLater(new Runnable()
        {
            public void run()
            {
                (new MainWindow()).setVisible(true);
            }
        });
    }

    /**
     * Get the tabbed pane holding every open machine.
     * @return The tab pane in use.
     */
    public MachineTabPane getTabPane()
    {
        return m_tabs;
    }

    /**
     * Get the current console.
     * @return The current console.
     */
    public ConsolePanel getConsole()
    {
        return m_console;
    }

    /** 
     * Gets the graphics panel for the currently selected machine diagram window.
     * @return A reference to the currently selected graphics panel, or null if there is no such panel.
     */
    public MachineGraphicsPanel getSelectedGraphicsPanel()
    {
        if (m_tabs == null)
        {
            return null;
        }
        MachineInternalFrame selected = m_tabs.getSelectedDocument();
        return selected == null? null : selected.getGfxPanel();
    }

    /**
     * Get every machine document currently open.
     * @return A list of the open documents.
     */
    public ArrayList<MachineInternalFrame> getOpenDocuments()
    {
        ArrayList<MachineInternalFrame> result = new ArrayList<MachineInternalFrame>();
        if (m_tabs == null)
        {
            return result;
        }
        for (int i = 0; i < m_tabs.getTabCount(); i++)
        {
            Component c = m_tabs.getComponentAt(i);
            if (c instanceof MachineInternalFrame)
            {
                result.add((MachineInternalFrame)c);
            }
        }
        return result;
    }

    /**
     * Get the tape currently in use.
     * @return The tape.
     */
    public Tape getTape()
    {
        return m_tape;
    }

    /**
     * Get a reference to the current instance of MainWindow
     * @return A reference to the current instance of MainWindow
     */
    public static MainWindow getInstance()
    {
        return m_instance;
    }

    /** 
     * Selects the user interface interaction mode and notifies all internal windows accordingly.
     * This determines the results of user interactions such as clicking on the state diagrams.
     * @param mode The new GUI mode.
     */
    public void setUIMode(GUI_Mode mode)
    {
        m_currentMode = mode;

        for (MachineInternalFrame doc : getOpenDocuments())
        {
            doc.getGfxPanel().setUIMode(mode);
        }

        if (m_toolbarButtons != null)
        {
            for (GUIModeButton b : m_toolbarButtons)
            {
                b.setChosen(b.getGUI_Mode() == mode);
            }
        }
        refreshStatus();
    }

    /**
     * Get the interaction mode currently selected.
     * @return The current GUI mode.
     */
    public GUI_Mode getUIMode()
    {
        return m_currentMode;
    }
    
    /** 
     * Builds the main window and its components.
     */
    private void initComponents()
    {
        // Set up the main window
        setMinimumSize(new Dimension(860, 560));
        setTitle("Tuatara Turing Machine Simulator");
        setIconImage(Global.loadIcon("tuatara.png").getImage());
        setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);

        // Tabs hold every open machine. The welcome panel takes their place while none are open,
        // so that the window is never simply blank.
        m_tabs = new MachineTabPane();
        m_tabs.addChangeListener(new javax.swing.event.ChangeListener()
        {
            public void stateChanged(javax.swing.event.ChangeEvent e)
            {
                setEnabledActionsThatRequireAMachine(getSelectedGraphicsPanel() != null);
                updateUndoActions();
                refreshStatus();
            }
        });

        m_welcome = new WelcomePanel(m_newTuringMachineAction, m_newDFSAAction, m_openMachineAction);

        m_documentArea = new JPanel(new BorderLayout());
        m_documentArea.add(m_welcome, BorderLayout.CENTER);

        // Console is the global point for logging
        m_console = new ConsolePanel();
        m_console.setMinimumSize(new Dimension(200, 80));
        m_console.setPreferredSize(new Dimension(200, 120));

        // The document area and console go together in a vertical-split pane so the console may be
        // resized vertically only. setMinimumSize(0,0) ensures that the tape display is always shown.
        m_mainSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT, true, m_documentArea, m_console);
        m_mainSplit.setMinimumSize(new Dimension(0,0));
        m_mainSplit.setOneTouchExpandable(true);
        m_mainSplit.setResizeWeight(0.82D);
        m_mainSplit.setBorder(BorderFactory.createEmptyBorder());

        // Set up the tape and associated controllers
        m_tape = new CA_Tape();
        m_tapeDisp = new TapeDisplayPanel(m_tape);
        m_tapeDispController =
            new TapeDisplayControllerPanel(m_tapeDisp, m_headToStartAction, m_eraseTapeAction, m_reloadTapeAction);

        // Set up the file choosers
        m_fcMachine.setDialogTitle("Save machine");
        m_fcMachine.addChoosableFileFilter(TMGraphicsPanel.FILE_FILTER);
        m_fcMachine.addChoosableFileFilter(DFSAGraphicsPanel.FILE_FILTER);

        m_fcTape.setDialogTitle("Save tape");
        m_fcTape.addChoosableFileFilter(Tape.FILE_FILTER);

        // Set up menus
        setJMenuBar(createMenus());

        // Status bar, reporting the active tool and the state of any running machine
        m_statusBar = new StatusBar();

        // Assemble: toolbar on top, then documents over console, then the tape, then the status bar.
        JPanel centre = new JPanel(new BorderLayout());
        centre.add(m_mainSplit, BorderLayout.CENTER);
        centre.add(m_tapeDispController, BorderLayout.SOUTH);

        getContentPane().setLayout(new BorderLayout());
        getContentPane().add(createToolbar(), BorderLayout.NORTH);
        getContentPane().add(centre, BorderLayout.CENTER);
        getContentPane().add(m_statusBar, BorderLayout.SOUTH);

        registerWindowShortcuts();

        // Maximize on startup
        this.setExtendedState(Frame.MAXIMIZED_BOTH);

        Theme.onChange(new Runnable()
        {
            public void run()
            {
                applyTheme();
            }
        });
        applyTheme();

        // Disable all toolbars (no default machine)
        setEnabledActionsThatRequireAMachine(false);

        setVisible(true);
        updateUndoActions();
        refreshStatus();
    }

    /**
     * Apply the current palette to the parts of the window which are not themselves theme-aware.
     */
    private void applyTheme()
    {
        Theme.Palette p = Theme.palette();
        getContentPane().setBackground(p.background);
        if (m_documentArea != null)
        {
            m_documentArea.setBackground(p.canvas);
        }
        if (m_toolbar != null)
        {
            m_toolbar.setBackground(p.background);
            m_toolbar.setBorder(BorderFactory.createCompoundBorder(
                        BorderFactory.createMatteBorder(0, 0, 1, 0, p.border),
                        BorderFactory.createEmptyBorder(5, 8, 5, 8)));
        }
        repaint();
    }

    /**
     * Register the window-level keyboard shortcuts which are not attached to a menu item.
     */
    private void registerWindowShortcuts()
    {
        JRootPane root = getRootPane();

        root.registerKeyboardAction(new ActionListener()
        {
            public void actionPerformed(ActionEvent e)
            {
                MachineInternalFrame doc = m_tabs.getSelectedDocument();
                if (doc != null)
                {
                    userConfirmSaveModifiedThenClose(doc);
                }
            }
        }, KeyStroke.getKeyStroke(KeyEvent.VK_W, KeyEvent.CTRL_DOWN_MASK),
           JComponent.WHEN_IN_FOCUSED_WINDOW);

        root.registerKeyboardAction(new ActionListener()
        {
            public void actionPerformed(ActionEvent e)
            {
                int n = m_tabs.getTabCount();
                if (n > 1)
                {
                    m_tabs.setSelectedIndex((m_tabs.getSelectedIndex() + 1) % n);
                }
            }
        }, KeyStroke.getKeyStroke(KeyEvent.VK_TAB, KeyEvent.CTRL_DOWN_MASK),
           JComponent.WHEN_IN_FOCUSED_WINDOW);
    }

    /**
     * Refresh the tab showing the given document. Called when a document's title or modified state
     * changes.
     * @param doc The document whose tab should be refreshed.
     */
    public void refreshTab(MachineInternalFrame doc)
    {
        if (m_tabs != null)
        {
            m_tabs.refreshTab(doc);
        }
    }

    /**
     * Update the status bar from the current machine, simulation and tape.
     */
    public void refreshStatus()
    {
        if (m_statusBar == null)
        {
            return;
        }

        m_statusBar.setTool(iconForMode(m_currentMode), labelForMode(m_currentMode));

        MachineGraphicsPanel panel = getSelectedGraphicsPanel();
        if (panel == null)
        {
            m_statusBar.setMachineInfo("");
            m_statusBar.setRunInfo("run", "", null);
        }
        else
        {
            Machine machine = panel.getSimulator().getMachine();
            int states = machine.getStates().size();
            int transitions = machine.getTransitions().size();
            // The zoom is only worth mentioning when it is not showing the diagram at actual size.
            String zoom = Math.abs(panel.getZoom() - 1.0) < 1e-9? ""
                        : String.format("  ·  %d%%", Math.round(panel.getZoom() * 100));
            m_statusBar.setMachineInfo(String.format("%s  ·  %d %s, %d %s%s",
                        panel.getMachineType(),
                        states, states == 1? "state" : "states",
                        transitions, transitions == 1? "transition" : "transitions",
                        zoom));

            State current = panel.getSimulator().getCurrentState();
            if (!m_editingEnabled)
            {
                m_statusBar.setRunInfo("run", current != null
                        ? "Running — in " + current.getLabel() : "Running",
                        Theme.palette().success);
            }
            else if (current != null)
            {
                m_statusBar.setRunInfo("pause", "Paused — in " + current.getLabel(),
                        Theme.palette().warning);
            }
            else
            {
                m_statusBar.setRunInfo("run", "", null);
            }
        }

        if (m_tape != null)
        {
            m_statusBar.setTapeInfo(String.format("Head at %d", m_tape.headLocation()));
        }
    }

    /**
     * Get the icon representing an interaction mode.
     * @param mode The interaction mode.
     * @return The name of the icon.
     */
    private static String iconForMode(GUI_Mode mode)
    {
        if (mode == null)
        {
            return "select";
        }
        switch (mode)
        {
            case ADDNODES:           return "state";
            case ADDTRANSITIONS:     return "transition";
            case SELECTION:          return "select";
            case ERASER:             return "eraser";
            case CHOOSESTART:        return "start";
            case CHOOSEFINAL:        return "final";
            case CHOOSECURRENTSTATE: return "current";
            default:                 return "select";
        }
    }

    /**
     * Get the human-readable name of an interaction mode.
     * @param mode The interaction mode.
     * @return The name of the mode.
     */
    private static String labelForMode(GUI_Mode mode)
    {
        if (mode == null)
        {
            return "";
        }
        switch (mode)
        {
            case ADDNODES:           return "Add states";
            case ADDTRANSITIONS:     return "Add transitions";
            case SELECTION:          return "Select";
            case ERASER:             return "Erase";
            case CHOOSESTART:        return "Set start state";
            case CHOOSEFINAL:        return "Set final state";
            case CHOOSECURRENTSTATE: return "Set current state";
            default:                 return "";
        }
    }
    
    /** 
     * Construct the menus and return the master JMenuBar object.
     * @return A menu bar containing all menus.
     */
    private JMenuBar createMenus()
    {
        JMenuBar menuBar = new JMenuBar();
        
        // File menu
        JMenu fileMenu = new JMenu("File");
        fileMenu.setMnemonic(KeyEvent.VK_F);
        menuBar.add(fileMenu);
      
        JMenu newSubmenu = new JMenu("New Machine");
        newSubmenu.setIcon(Icons.get("new-machine", MENU_ICON_SIZE));
        newSubmenu.setMnemonic(KeyEvent.VK_N);
        newSubmenu.add(new JMenuItem(m_newTuringMachineAction));
        newSubmenu.add(new JMenuItem(m_newDFSAAction));
        fileMenu.add(newSubmenu);

        fileMenu.add(new JMenuItem(m_openMachineAction));
        fileMenu.add(new JMenuItem(m_saveMachineAction));
        fileMenu.add( new JMenuItem(m_saveMachineAsAction));
        fileMenu.add(new JMenuItem(m_closeMachineAction));
        fileMenu.addSeparator();
        fileMenu.add(new JMenuItem(m_newTapeAction));
        fileMenu.add(new JMenuItem(m_openTapeAction));
        fileMenu.add(new JMenuItem(m_saveTapeAction));
        fileMenu.add(new JMenuItem(m_saveTapeAsAction));
        fileMenu.addSeparator();
        fileMenu.add(new JMenuItem(m_exitAction));


        // Edit menu
        JMenu editMenu = new JMenu("Edit");
        editMenu.setMnemonic(KeyEvent.VK_E);
        menuBar.add(editMenu);
        
        editMenu.add(new JMenuItem(m_undoAction));
        editMenu.add(new JMenuItem(m_redoAction));
        editMenu.add(new JMenuItem(m_cutAction));
        editMenu.add(new JMenuItem(m_copyAction));
        editMenu.add(new JMenuItem(m_pasteAction));
        editMenu.add(new JMenuItem(m_deleteAction));
        

        // View menu
        JMenu viewMenu = new JMenu("View");
        viewMenu.setMnemonic(KeyEvent.VK_V);
        menuBar.add(viewMenu);

        JMenu appearanceMenu = new JMenu("Appearance");
        appearanceMenu.setIcon(Icons.get("light", MENU_ICON_SIZE));
        ButtonGroup appearanceItems = new ButtonGroup();

        m_lightThemeItem = new JRadioButtonMenuItem(m_lightThemeAction);
        m_darkThemeItem = new JRadioButtonMenuItem(m_darkThemeAction);
        appearanceMenu.add(m_lightThemeItem);
        appearanceMenu.add(m_darkThemeItem);
        appearanceItems.add(m_lightThemeItem);
        appearanceItems.add(m_darkThemeItem);
        m_lightThemeItem.setSelected(!Theme.isDark());
        m_darkThemeItem.setSelected(Theme.isDark());
        viewMenu.add(appearanceMenu);

        viewMenu.addSeparator();
        m_showConsoleItem = new JCheckBoxMenuItem(m_toggleConsoleAction);
        m_showConsoleItem.setSelected(true);
        viewMenu.add(m_showConsoleItem);

        m_showStatusBarItem = new JCheckBoxMenuItem(m_toggleStatusBarAction);
        m_showStatusBarItem.setSelected(true);
        viewMenu.add(m_showStatusBarItem);

        m_showTapeItem = new JCheckBoxMenuItem(m_toggleTapeAction);
        m_showTapeItem.setSelected(true);
        viewMenu.add(m_showTapeItem);

        viewMenu.addSeparator();
        viewMenu.add(new JMenuItem(m_zoomInAction));
        viewMenu.add(new JMenuItem(m_zoomOutAction));
        viewMenu.add(new JMenuItem(m_zoomResetAction));


        // Mode menu
        JMenu modeMenu = new JMenu("Mode");
        modeMenu.setMnemonic(KeyEvent.VK_O);
        menuBar.add(modeMenu);

        ButtonGroup modeMenuItems = new ButtonGroup();
 
        JRadioButtonMenuItem m_addNodesMenuItem = new JRadioButtonMenuItem(m_addNodesAction);
        m_addNodesAction.setMenuItem(m_addNodesMenuItem);
        modeMenu.add(m_addNodesMenuItem);
        modeMenuItems.add(m_addNodesMenuItem);
       
        JRadioButtonMenuItem m_addTransitionsMenuItem = new JRadioButtonMenuItem(m_addTransitionsAction);
        m_addTransitionsAction.setMenuItem(m_addTransitionsMenuItem);
        modeMenu.add(m_addTransitionsMenuItem);
        modeMenuItems.add(m_addTransitionsMenuItem);
        
        JRadioButtonMenuItem m_makeSelectionMenuItem = new JRadioButtonMenuItem(m_selectionAction);
        m_selectionAction.setMenuItem(m_makeSelectionMenuItem);
        modeMenu.add(m_makeSelectionMenuItem);
        modeMenuItems.add(m_makeSelectionMenuItem);
        
        JRadioButtonMenuItem m_eraserMenuItem = new JRadioButtonMenuItem(m_eraserAction);
        m_eraserAction.setMenuItem(m_eraserMenuItem);
        modeMenu.add(m_eraserMenuItem);
        modeMenuItems.add(m_eraserMenuItem);
        
        JRadioButtonMenuItem m_chooseStartMenuItem = new JRadioButtonMenuItem(m_chooseStartAction);
        m_chooseStartAction.setMenuItem(m_chooseStartMenuItem);
        modeMenu.add(m_chooseStartMenuItem);
        modeMenuItems.add(m_chooseStartMenuItem);
        
        JRadioButtonMenuItem m_chooseFinalMenuItem = new JRadioButtonMenuItem(m_chooseFinalAction);
        m_chooseFinalAction.setMenuItem(m_chooseFinalMenuItem);
        modeMenu.add(m_chooseFinalMenuItem);
        modeMenuItems.add(m_chooseFinalMenuItem);
        
        JRadioButtonMenuItem m_chooseCurrentStateMenuItem = new JRadioButtonMenuItem(m_chooseCurrentStateAction);
        m_chooseCurrentStateAction.setMenuItem(m_chooseCurrentStateMenuItem);
        modeMenu.add(m_chooseCurrentStateMenuItem);
        modeMenuItems.add(m_chooseCurrentStateMenuItem);
 
        m_addNodesMenuItem.setSelected(true);
        

        // Machine menu
        JMenu machineMenu = new JMenu("Machine");
        machineMenu.setMnemonic(KeyEvent.VK_M);
        menuBar.add(machineMenu);
       
        machineMenu.add(new JMenuItem(m_validateAction));
        machineMenu.add(new JMenuItem(m_stepAction));
        machineMenu.add(new JMenuItem(m_fastExecuteAction));
        machineMenu.add(new JMenuItem(m_pauseExecutionAction));
        machineMenu.add(new JMenuItem(m_stopMachineAction));
        machineMenu.addSeparator();
        
        ButtonGroup executeSpeedMenuItems = new ButtonGroup();
        
        JRadioButtonMenuItem m_slowExecuteSpeed = new JRadioButtonMenuItem(m_slowExecuteSpeedAction);
        machineMenu.add(m_slowExecuteSpeed);
        executeSpeedMenuItems.add(m_slowExecuteSpeed);
        
        JRadioButtonMenuItem m_mediumExecuteSpeed = new JRadioButtonMenuItem(m_mediumExecuteSpeedAction);
        machineMenu.add(m_mediumExecuteSpeed);
        executeSpeedMenuItems.add(m_mediumExecuteSpeed);
        
        JRadioButtonMenuItem m_fastExecuteSpeed = new JRadioButtonMenuItem(m_fastExecuteSpeedAction);
        machineMenu.add(m_fastExecuteSpeed);
        executeSpeedMenuItems.add(m_fastExecuteSpeed);
        
        JRadioButtonMenuItem m_superFastExecuteSpeed = new JRadioButtonMenuItem(m_superFastExecuteSpeedAction);
        machineMenu.add(m_superFastExecuteSpeed);
        executeSpeedMenuItems.add(m_superFastExecuteSpeed);
        
        JRadioButtonMenuItem m_ultraFastExecuteSpeed = new JRadioButtonMenuItem(m_ultraFastExecuteSpeedAction);
        machineMenu.add(m_ultraFastExecuteSpeed);
        executeSpeedMenuItems.add(m_ultraFastExecuteSpeed);

        JRadioButtonMenuItem m_zeroDelayExecuteSpeed = new JRadioButtonMenuItem(m_zeroDelayExecuteSpeedAction);
        machineMenu.add(m_zeroDelayExecuteSpeed);
        executeSpeedMenuItems.add(m_zeroDelayExecuteSpeed);

        m_fastExecuteSpeed.setSelected(true);
        m_executionDelayTime = FAST_EXECUTE_SPEED_DELAY;
        
       
        // Tape menu
        JMenu tapeMenu = new JMenu("Tape");
        tapeMenu.setMnemonic(KeyEvent.VK_T);
        menuBar.add(tapeMenu);
 
        tapeMenu.add(new JMenuItem(m_headToStartAction));
        tapeMenu.add(new JMenuItem(m_reloadTapeAction));
        tapeMenu.add(new JMenuItem(m_eraseTapeAction));
      

        // Config menu
        JMenu configMenu = new JMenu("Configuration");
        configMenu.setMnemonic(KeyEvent.VK_C);
        menuBar.add(configMenu);

        configMenu.add(new JMenuItem(m_configureAlphabetAction));
        
        
        // Help menu
        JMenu helpMenu = new JMenu("Help");
        helpMenu.setMnemonic(KeyEvent.VK_H);
        menuBar.add(helpMenu);

        helpMenu.add(new JMenuItem(m_helpAction));
        helpMenu.add(new JMenuItem(m_aboutAction));

        return menuBar;
    }
    
    /**
     * Build the toolbar: a single flat strip, divided into groups for file actions, editing, the
     * mutually exclusive drawing tools, and running a machine.
     *
     * The program previously presented three undifferentiated strips of bevelled icon buttons, in
     * which a destructive action, a mode switch and the run command were indistinguishable. Grouping
     * them and labelling the run controls makes the common path obvious.
     *
     * @return The toolbar.
     */
    private JComponent createToolbar()
    {
        // Every mode button will be registered here for iteration purposes
        m_toolbarButtons = new ArrayList<GUIModeButton>();

        m_toolbar = new JPanel(new WrapLayout(FlowLayout.LEFT, 2, 3));
        m_toolbar.setOpaque(true);

        // ---- File group.
        // SPECIAL: newMachine shows a popup menu containing all new***MachineAction's
        final FlatButton newMachineButton = new FlatButton(null, "new-machine", FlatButton.Style.TOOL, false);
        newMachineButton.setIconSize(TOOLBAR_ICON_SIZE);
        newMachineButton.setToolTipText("Create a new machine");
        final JPopupMenu newMachineMenu = new JPopupMenu();
        newMachineMenu.add(m_newTuringMachineAction);
        newMachineMenu.add(m_newDFSAAction);
        newMachineButton.addActionListener(new ActionListener()
        {
            public void actionPerformed(ActionEvent e)
            {
                newMachineMenu.show(newMachineButton, 0, newMachineButton.getHeight());
            }
        });

        m_toolbar.add(newMachineButton);
        m_toolbar.add(button(m_openMachineAction, "open"));
        m_toolbar.add(button(m_saveMachineAction, "save"));
        m_toolbar.add(new FlatButton.Divider());

        // ---- Tape group.
        m_toolbar.add(button(m_newTapeAction, "new-tape"));
        m_toolbar.add(button(m_openTapeAction, "open-tape"));
        m_toolbar.add(button(m_saveTapeAction, "save-tape"));
        m_toolbar.add(new FlatButton.Divider());

        // ---- Edit group.
        m_undoToolBarButton = button(m_undoAction, "undo");
        m_redoToolBarButton = button(m_redoAction, "redo");
        m_toolbar.add(m_undoToolBarButton);
        m_toolbar.add(m_redoToolBarButton);
        m_toolbar.add(button(m_cutAction, "cut"));
        m_toolbar.add(button(m_copyAction, "copy"));
        m_toolbar.add(button(m_pasteAction, "paste"));

        FlatButton delete = new FlatButton(m_deleteAction, "delete", FlatButton.Style.DANGER, false);
        delete.setIconSize(TOOLBAR_ICON_SIZE);
        m_toolbar.add(delete);
        m_toolbar.add(new FlatButton.Divider());

        // ---- Tool group. Exactly one of these is active at a time.
        m_toolbar.add(modeButton(m_addNodesAction, GUI_Mode.ADDNODES, "state"));
        m_toolbar.add(modeButton(m_addTransitionsAction, GUI_Mode.ADDTRANSITIONS, "transition"));
        m_toolbar.add(modeButton(m_selectionAction, GUI_Mode.SELECTION, "select"));
        m_toolbar.add(modeButton(m_eraserAction, GUI_Mode.ERASER, "eraser"));
        m_toolbar.add(modeButton(m_chooseStartAction, GUI_Mode.CHOOSESTART, "start"));
        m_toolbar.add(modeButton(m_chooseFinalAction, GUI_Mode.CHOOSEFINAL, "final"));
        m_toolbar.add(modeButton(m_chooseCurrentStateAction, GUI_Mode.CHOOSECURRENTSTATE, "current"));
        m_toolbar.add(new FlatButton.Divider());

        // ---- Configuration.
        m_toolbar.add(button(m_configureAlphabetAction, "alphabet"));
        m_toolbar.add(new FlatButton.Divider());

        // ---- Run group. These carry labels, being the actions users look for most often.
        FlatButton run = new FlatButton(m_fastExecuteAction, "run", FlatButton.Style.PRIMARY, true);
        run.setIconSize(TOOLBAR_ICON_SIZE);
        m_toolbar.add(run);

        FlatButton step = new FlatButton(m_stepAction, "step", FlatButton.Style.TOOL, true);
        step.setIconSize(TOOLBAR_ICON_SIZE);
        m_toolbar.add(step);

        m_toolbar.add(button(m_pauseExecutionAction, "pause"));

        FlatButton stop = new FlatButton(m_stopMachineAction, "stop", FlatButton.Style.TOOL, true);
        stop.setIconSize(TOOLBAR_ICON_SIZE);
        m_toolbar.add(stop);

        m_toolbar.add(button(m_validateAction, "validate"));

        // ---- Speed. Exposed here as well as in the menu, since it governs what the run button does.
        m_toolbar.add(new FlatButton.Divider());
        m_toolbar.add(createSpeedSelector());

        // Default mode
        setUIMode(GUI_Mode.ADDNODES);

        return m_toolbar;
    }

    /**
     * Build an icon-only toolbar button.
     * @param act The action the button performs.
     * @param iconName The name of the icon to render.
     * @return The button.
     */
    private FlatButton button(Action act, String iconName)
    {
        FlatButton b = new FlatButton(act, iconName, FlatButton.Style.TOOL, false);
        b.setIconSize(TOOLBAR_ICON_SIZE);
        return b;
    }

    /**
     * Build a toolbar button which selects one of the drawing tools.
     * @param act The action the button performs.
     * @param mode The mode the button selects.
     * @param iconName The name of the icon to render.
     * @return The button.
     */
    private GUIModeButton modeButton(Action act, GUI_Mode mode, String iconName)
    {
        GUIModeButton b = new GUIModeButton(act, mode, iconName);
        b.setIconSize(TOOLBAR_ICON_SIZE);
        m_toolbarButtons.add(b);
        return b;
    }

    /**
     * Build the execution speed selector. Exposed here as well as in the Machine menu; the two are
     * kept in step by routing both through the same actions.
     * @return The selector.
     */
    private JComponent createSpeedSelector()
    {
        m_speedSelector = new JComboBox<String>(new String[]
                { "Slow", "Medium", "Fast", "Super fast", "Ultra fast", "Zero delay" });
        m_speedSelector.setFocusable(false);
        m_speedSelector.setToolTipText("How fast the machine steps while running");
        m_speedSelector.setSelectedIndex(2);
        m_speedSelector.setMaximumRowCount(6);
        m_speedSelector.addActionListener(new ActionListener()
        {
            public void actionPerformed(ActionEvent e)
            {
                switch (m_speedSelector.getSelectedIndex())
                {
                    case 0: m_slowExecuteSpeedAction.actionPerformed(e); break;
                    case 1: m_mediumExecuteSpeedAction.actionPerformed(e); break;
                    case 2: m_fastExecuteSpeedAction.actionPerformed(e); break;
                    case 3: m_superFastExecuteSpeedAction.actionPerformed(e); break;
                    case 4: m_ultraFastExecuteSpeedAction.actionPerformed(e); break;
                    default: m_zeroDelayExecuteSpeedAction.actionPerformed(e); break;
                }
            }
        });

        JPanel wrapper = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        wrapper.setOpaque(false);
        JLabel caption = new JLabel("Speed");
        caption.setFont(Theme.ui(Font.PLAIN, 12));
        caption.setForeground(Theme.palette().textMuted);
        wrapper.add(caption);
        wrapper.add(m_speedSelector);
        return wrapper;
    }
   
    /**
     * Creates a new document displaying a machine.
     * @param gfxPanel The underlying graphics panel.
     * @return A document containing the graphics panel.
     */
    public MachineInternalFrame newMachineWindow(MachineGraphicsPanel gfxPanel)
    {
        gfxPanel.setUIMode(m_currentMode);
        MachineInternalFrame returner = new MachineInternalFrame(gfxPanel, ++m_windowCount);
        gfxPanel.setFrame(returner);
        gfxPanel.setPreferredSize(new Dimension(MACHINE_CANVAS_SIZE_X, MACHINE_CANVAS_SIZE_Y));

        JScrollPane scroller = new JScrollPane(gfxPanel);
        scroller.setBorder(BorderFactory.createEmptyBorder());
        scroller.getVerticalScrollBar().setUnitIncrement(16);
        scroller.getHorizontalScrollBar().setUnitIncrement(16);
        returner.add(scroller, BorderLayout.CENTER);
        returner.setScrollPane(scroller);
        returner.updateTitle();

        return returner;
    }

    /**
     * Open a document as a tab, or bring its existing tab to the front if it is already open.
     * @param frame The document to open.
     */
    public void addFrame(MachineInternalFrame frame)
    {
        int existing = m_tabs.indexOfComponent(frame);
        if (existing >= 0)
        {
            m_tabs.setSelectedIndex(existing);
            return;
        }

        // The welcome panel occupies the document area while nothing is open.
        if (m_tabs.getTabCount() == 0)
        {
            m_documentArea.removeAll();
            m_documentArea.add(m_tabs, BorderLayout.CENTER);
            m_documentArea.revalidate();
            m_documentArea.repaint();
        }

        m_tabs.addDocument(frame, new ActionListener()
        {
            public void actionPerformed(ActionEvent e)
            {
                userConfirmSaveModifiedThenClose((MachineInternalFrame)e.getSource());
            }
        });

        setEnabledActionsThatRequireAMachine(true);
        updateUndoActions();
        refreshStatus();
    }

    /**
     * Close a document, removing its tab. When the last document closes, the welcome panel returns.
     * @param frame The document to remove.
     */
    public void removeFrame(MachineInternalFrame frame)
    {
        int index = m_tabs.indexOfComponent(frame);
        if (index >= 0)
        {
            m_tabs.removeTabAt(index);
        }

        if (m_tabs.getTabCount() == 0)
        {
            m_documentArea.removeAll();
            m_documentArea.add(m_welcome, BorderLayout.CENTER);
            m_documentArea.revalidate();
            m_documentArea.repaint();
        }

        frame.fireClosed();
        handleLostFocus();
    }
    
    /** 
     * Ask the user to confirm whether they wish to save a modified machine. If they agree,
     * correctly handle the saving. Afterwards, close the window associated with the machine.
     * @param iFrame The frame being closed.
     * @return false if the user cancelled, true otherwise.
     */
    private boolean userConfirmSaveModifiedThenClose(MachineInternalFrame iFrame)
    {
        MachineGraphicsPanel gfxPanel = iFrame.getGfxPanel();

        // Bring the machine being closed to the front, so the user can see what they are answering
        // about.
        int index = m_tabs.indexOfComponent(iFrame);
        if (index >= 0)
        {
            m_tabs.setSelectedIndex(index);
        }

        if (gfxPanel.isModifiedSinceSave())
        {
            int result = JOptionPane.showConfirmDialog(this,
                    String.format("The machine '%s' has unsaved changes. Save before closing?",
                        iFrame.getTitle()),
                    "Close Machine", JOptionPane.YES_NO_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE);
            if (result == JOptionPane.YES_OPTION)
            {
                Machine machine = gfxPanel.getSimulator().getMachine();
                File outFile = gfxPanel.getFile();
                if (outFile == null)
                {
                    outFile = chooseSaveFile(m_fcMachine, "Save Machine", gfxPanel.getMachineExt());
                    if (outFile == null)
                    {
                        // Cancelled by user
                        return false;
                    }
                }

                try
                {
                    Machine.saveMachine(machine, outFile);
                    iFrame.dispose();
                    return true;
                }
                catch (Exception e)
                {
                    m_console.logError("Could not save %s: %s", iFrame.getTitle(), e.getMessage());
                    return false;
                }
            }
            else if (result == JOptionPane.NO_OPTION)
            {
                iFrame.dispose();
                return true;
            }
            // On cancel, do nothing
            return false;
        }
        else
        {
            iFrame.dispose();
            return true;
        }
    }

    /**
     * When there is no focus owner, the focus needs to be redirected to a valid component so that
     * we can trap keyboard events. This method finds the best component to give the focus, and
     * transfers the focus to that component.
     */
    public void handleLostFocus()
    {
        if (m_tabs == null)
        {
            return;
        }

        if (m_tabs.getTabCount() == 0)
        {
            m_documentArea.requestFocusInWindow();
            setEnabledActionsThatRequireAMachine(false);
        }
        else if (m_tabs.getSelectedIndex() < 0)
        {
            m_tabs.setSelectedIndex(0);
        }

        updateUndoActions();
        refreshStatus();
    }
    
    /** 
     * Set the enabled/disabled status of Actions (ie toolbars and menu items) that
     * need a machine to apply to, however editing operations will only be enabled if
     * the isEditingEnabled() currently returns true.
     * @param isEnabled true if controls should be enabled, false otherwise.
     */
    private void setEnabledActionsThatRequireAMachine(boolean isEnabled)
    {
        m_stopMachineAction.setEnabled(isEnabled);
        m_pauseExecutionAction.setEnabled(isEnabled);

        // Zooming stays available while a machine runs; it changes nothing about the machine.
        m_zoomInAction.setEnabled(isEnabled);
        m_zoomOutAction.setEnabled(isEnabled);
        m_zoomResetAction.setEnabled(isEnabled);


        if (isEditingEnabled() || isEnabled == false)
        {
            m_validateAction.setEnabled(isEnabled);
            m_stepAction.setEnabled(isEnabled);
            m_configureAlphabetAction.setEnabled(isEnabled);
            m_saveMachineAction.setEnabled(isEnabled);
            m_cutAction.setEnabled(isEnabled);
            m_copyAction.setEnabled(isEnabled);
            m_pasteAction.setEnabled(isEnabled);
            m_deleteAction.setEnabled(isEnabled);
            m_fastExecuteAction.setEnabled(isEnabled);
            
            m_addNodesAction.setEnabled(isEnabled);
            m_addTransitionsAction.setEnabled(isEnabled);
            m_eraserAction.setEnabled(isEnabled);
            m_selectionAction.setEnabled(isEnabled);
            m_chooseStartAction.setEnabled(isEnabled);
            m_chooseFinalAction.setEnabled(isEnabled);
            m_chooseCurrentStateAction.setEnabled(isEnabled);
            
            m_slowExecuteSpeedAction.setEnabled(isEnabled);
            m_mediumExecuteSpeedAction.setEnabled(isEnabled);
            m_fastExecuteSpeedAction.setEnabled(isEnabled);
            m_superFastExecuteSpeedAction.setEnabled(isEnabled);
            m_ultraFastExecuteSpeedAction.setEnabled(isEnabled);
            m_zeroDelayExecuteSpeedAction.setEnabled(isEnabled);
        }
    }
    
    /**
     * Set whether or not all controls are to be enabled or not.
     * @param isEnabled true if all controls are to be enabled, false otherwise.
     */
    public void setEditingActionsEnabledState(boolean isEnabled)
    {
        m_validateAction.setEnabled(isEnabled);
        m_stepAction.setEnabled(isEnabled);
        m_configureAlphabetAction.setEnabled(isEnabled);
        m_cutAction.setEnabled(isEnabled);
        m_copyAction.setEnabled(isEnabled);
        m_pasteAction.setEnabled(isEnabled);
        m_undoAction.setEnabled(isEnabled);
        m_redoAction.setEnabled(isEnabled);
        m_deleteAction.setEnabled(isEnabled);
        m_fastExecuteAction.setEnabled(isEnabled);
        
        m_addNodesAction.setEnabled(isEnabled);
        m_addTransitionsAction.setEnabled(isEnabled);
        m_eraserAction.setEnabled(isEnabled);
        m_selectionAction.setEnabled(isEnabled);
        m_chooseStartAction.setEnabled(isEnabled);
        m_chooseFinalAction.setEnabled(isEnabled);
        m_chooseCurrentStateAction.setEnabled(isEnabled);
        
        m_newTuringMachineAction.setEnabled(isEnabled);
        m_newDFSAAction.setEnabled(isEnabled);
        m_openMachineAction.setEnabled(isEnabled);
        m_saveMachineAsAction.setEnabled(isEnabled);
        m_saveMachineAction.setEnabled(isEnabled);
        m_newTapeAction.setEnabled(isEnabled);
        m_openTapeAction.setEnabled(isEnabled);
        m_saveTapeAsAction.setEnabled(isEnabled);
        m_saveTapeAction.setEnabled(isEnabled);
        
        m_slowExecuteSpeedAction.setEnabled(isEnabled);
        m_mediumExecuteSpeedAction.setEnabled(isEnabled);
        m_fastExecuteSpeedAction.setEnabled(isEnabled);
        m_superFastExecuteSpeedAction.setEnabled(isEnabled);
        m_ultraFastExecuteSpeedAction.setEnabled(isEnabled);
        m_zeroDelayExecuteSpeedAction.setEnabled(isEnabled);
        
        m_headToStartAction.setEnabled(isEnabled);
        m_eraseTapeAction.setEnabled(isEnabled);
        m_reloadTapeAction.setEnabled(isEnabled);
    }

    /**
     * Compute the next transition for every simulator currently loaded.
     */
    public void updateAllSimulators()
    {
        for (MachineInternalFrame doc : getOpenDocuments())
        {
            MachineGraphicsPanel panel = doc.getGfxPanel();
            if (panel != null)
            {
                panel.repaint();
            }
        }
        refreshStatus();
    }
    
    /**
     * Run a machine straight through to a halt, rather than stepping it on a timer. Because this
     * cannot be watched as it happens, and because a machine may not halt at all, the user is asked
     * for a limit on the number of steps first.
     * @param panel The panel whose machine is to be run.
     */
    private void executeWithoutDelay(MachineGraphicsPanel panel)
    {
        Simulator sim = panel.getSimulator();

        // Pre-validate the machine, as the timed path does.
        String result = sim.getMachine().hasUndefinedSymbols();
        if (result != null)
        {
            getConsole().log("Cannot simulate %s: %s", panel.getFrame().getTitle(), result);
            Global.showErrorMessage("Execute", "Cannot simulate: %s", result);
            setEditingEnabled(true);
            return;
        }

        int maxSteps = Global.getInteger();
        if (maxSteps <= 0)
        {
            // The prompt was cancelled or the answer was not a usable number; leave the machine be.
            setEditingEnabled(true);
            return;
        }

        try
        {
            if (sim.getCurrentState() == null)
            {
                Tape tape = getTape();
                if (tape.headLocation() != 0)
                {
                    getConsole().log("Warning: Tape head has not been reset");
                }
                getConsole().logPartial(panel, "Input: %s\n",
                        tape.getPartialString(tape.headLocation(),
                                              tape.getLength() - tape.headLocation()));
            }

            // runUntilHalt reports acceptance, not halting: it returns false both when the step
            // limit is hit and when the machine halted without the head parked. Only the machine's
            // own state distinguishes the two, so ask it rather than trusting the return value.
            sim.runUntilHalt(maxSteps);

            panel.repaint();
            m_tapeDisp.repaint();
            getConsole().logPartial(panel, sim.getConfiguration());
            getConsole().endPartial();

            if (!sim.isHalted())
            {
                throw new ComputationFailedException(
                        String.format("The machine did not halt after %d steps", maxSteps));
            }
            throw new ComputationCompletedException(sim.isAccepted()
                    ? "The machine halted"
                    : "The machine halted, but the read/write head was not parked");
        }
        // Machine halted as expected
        catch (ComputationCompletedException e)
        {
            setEditingEnabled(true);
            stopExecution();

            String msg = panel.getErrorMessage(e);
            getConsole().log("Simulation of %s finished: %s", panel.getFrame().getTitle(), msg);
            Global.showInfoMessage(HALTED_MESSAGE_TITLE_STR, "Simulation finished: %s", msg);
            sim.resetMachine();
            panel.repaint();
        }
        // Machine halted unexpectedly, or ran past the step limit
        catch (Exception e)
        {
            setEditingEnabled(true);
            stopExecution();

            String msg = panel.getErrorMessage(e);
            getConsole().log("Simulation of %s halted unexpectedly: %s",
                    panel.getFrame().getTitle(), msg);
            Global.showErrorMessage(HALTED_MESSAGE_TITLE_STR,
                    "Simulation halted unexpectedly: %s", msg);
        }
        refreshStatus();
        repaint();
    }

    /**
     * Stop execution of the current machine.
     * @return true if the currently executing machine is stopped, false otherwise.
     */
    public boolean stopExecution()
    {
        if (m_timerTask != null)
        {
            return m_timerTask.cancel();
        }
        return false;
    }

    /**
     * A general function used for displaying save file dialogs. This keeps all behaviours for file
     * choosing consistent across types.
     * @param fc The file chooser to be used.
     * @param title The title of the dialog.
     * @param ext The file extension.
     * @return The chosen file if one was picked, otherwise null if the user cancelled.
     */
    public File chooseSaveFile(JFileChooser fc, String title, String ext)
    {
        do
        {
            // Prevent the program from reading from the keyboard while the file dialog is active
            m_keyboardEnabled = false;
            fc.setDialogTitle(title);
            int returnVal = fc.showSaveDialog(MainWindow.this);
            m_keyboardEnabled = true;
            if (returnVal != JFileChooser.APPROVE_OPTION)
            {
                return null;
            }
            File outfile = fc.getSelectedFile();
            // If it's a new file, and doesn't have our extension, append the extension
            if (!outfile.exists() && !outfile.toString().endsWith(ext))
            {
                outfile = new File(outfile.toString() + ext);
            }
            // If it exists, confirm overwrite
            if (outfile.exists())
            {
                int overwrite = JOptionPane.showConfirmDialog(MainWindow.this,
                        String.format("The file %s already exists. Overwrite?", outfile.getName()),
                        "Save As", JOptionPane.YES_NO_CANCEL_OPTION);
                switch (overwrite)
                {
                    case JOptionPane.CANCEL_OPTION: 
                        return null;
                    case JOptionPane.NO_OPTION:
                        continue;
                    case JOptionPane.YES_OPTION:
                        return outfile;
                    default:
                        return null;
                }
            }
            // Otherwise return the file chosen
            return outfile;
        }
        while (true);
    }

    /**
     * A general function used for displaying load file dialogs. This keeps all behaviours for file
     * choosing consistent across types.
     * @param fc The file chooser to be used.
     * @param title The title of the dialog.
     * @param ext The file extension.
     * @return The chosen file if one was picked, otherwise null if the user cancelled.
     */
    public File chooseLoadFile(JFileChooser fc, String title, String ext)
    {
        do
        {
            // Prevent the program from reading from the keyboard while the file dialog is active
            m_keyboardEnabled = false;
            fc.setDialogTitle(title);
            int returnVal = fc.showOpenDialog(MainWindow.this);
            m_keyboardEnabled = true;
            if (returnVal != JFileChooser.APPROVE_OPTION)
            {
                return null;
            }
            File infile = fc.getSelectedFile();
            // If it doesn't exist, try with extension
            if (!infile.exists())
            {
                infile = new File(infile.toString() + ext);
            }
            // Still nothing, prompt again
            if (!infile.exists())
            {
                Global.showWarningMessage("Load File", "Cannot find file %s", infile.toString());
                continue;
            }
            // Otherwise return the file chosen
            return infile;
        }
        while (true);
    }

    /** 
     * Determine if editing the machine or tape is enabled
     * @return true if editing is enabled, false otherwise.
     */
    public boolean isEditingEnabled()
    {
        return m_editingEnabled;
    }
    
    /**
     * Set whether editing the machine or tape is enabled.
     * @param isEnabled true if editing is enabled, false otherwise.
     */
    public void setEditingEnabled(boolean isEnabled)
    {
        m_editingEnabled = isEnabled;
        m_keyboardEnabled = isEnabled;

        for (MachineInternalFrame doc : getOpenDocuments())
        {
            doc.getGfxPanel().setEditingEnabled(isEnabled);
        }
        m_exitAction.setEnabled(isEnabled);
        m_closeMachineAction.setEnabled(isEnabled);

        setEditingActionsEnabledState(isEnabled);
        m_tapeDispController.setEditingEnabled(isEnabled);
        refreshStatus();
    }
 
    /**
     * Handle when a user requests to exit the program.
     */
    public void userRequestToExit()
    {
        if (m_tabs == null)
        {
            System.exit(0);
        }
        if (!m_editingEnabled)
        {
            // A machine is running; stop it before allowing the program to close, rather than
            // silently ignoring the request as this previously did.
            int result = JOptionPane.showConfirmDialog(this,
                    "A machine is still running. Stop it and exit?",
                    "Exit", JOptionPane.OK_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE);
            if (result != JOptionPane.OK_OPTION)
            {
                return;
            }
            stopExecution();
            setEditingEnabled(true);
        }

        for (MachineInternalFrame doc : getOpenDocuments())
        {
            if (doc.getGfxPanel().isModifiedSinceSave())
            {
                if (!userConfirmSaveModifiedThenClose(doc))
                {
                    return;
                }
            }
            else
            {
                doc.dispose();
            }
        }
        System.exit(0);
    }
 
    /**
     * Compute the centroid of the given set of states, and move the centroid of the machine to the
     * center of the window.
     * @param states The set of states.
     * @param transitions The set of transitions.
     * @param centreOfWindow The centre of the frame.
     * @param lastPastedLoc The last pasted location.
     * @param numTimesPastedToLastLoc The number of times an item has been pasted to the last pasted location.
     * @param panel The current graphics panel.
     */
    private void translateCentroidToMiddleOfWindow(Collection<? extends State> states,
            Collection<? extends Transition> transitions, Point2D centreOfWindow,
            Point2D lastPastedLoc, int numTimesPastedToLastLoc, MachineGraphicsPanel panel)
    {
        if (states.size() == 0)
        {
            return;
        }
        Point2D centroid = computeCentroid(states);

        // TODO: Ensure that we dont go off the edge of the map.
        int rightMostX = Integer.MIN_VALUE;
        int leftMostX = Integer.MAX_VALUE;
        int bottomMostY = Integer.MIN_VALUE;
        int topMostY = Integer.MAX_VALUE;

        for (State s : states)
        {
            if (s.getX() > rightMostX)
            {
                rightMostX = s.getX();
            }
            if (s.getY() > bottomMostY)
            {
                bottomMostY = s.getY();
            }
            if (s.getX() < leftMostX)
            {
                leftMostX = s.getX();
            }
            if (s.getY() < topMostY)
            {
                topMostY = s.getY();
            }
        }

        int translateVectorX = (int)(centreOfWindow.getX() - centroid.getX());
        int translateVectorY = (int)(centreOfWindow.getY() - centroid.getY());


        if (leftMostX + translateVectorX < 0)
        {
            translateVectorX -= leftMostX + translateVectorX;
        }

        if (topMostY + translateVectorY < 0)
        {
            translateVectorY -= topMostY + translateVectorY;
        }

        if (rightMostX + translateVectorX > MACHINE_CANVAS_SIZE_X - State.STATE_RENDERING_WIDTH)
        {
            translateVectorX -= rightMostX + translateVectorX - (MACHINE_CANVAS_SIZE_X - State.STATE_RENDERING_WIDTH);
        }

        if (bottomMostY + translateVectorY >  MACHINE_CANVAS_SIZE_Y - State.STATE_RENDERING_WIDTH)
        {
            translateVectorY -= bottomMostY + translateVectorY - (MACHINE_CANVAS_SIZE_Y - State.STATE_RENDERING_WIDTH);
        }

        if (lastPastedLoc != null && ((int)lastPastedLoc.getX() == (int)centreOfWindow.getX() + translateVectorX
                    &&(int)lastPastedLoc.getY() == (int)centreOfWindow.getY() + translateVectorY))
        {
            translateVectorX += TRANSLATE_TO_AVOID_STACKING_X * numTimesPastedToLastLoc;
            translateVectorY += TRANSLATE_TO_AVOID_STACKING_Y * numTimesPastedToLastLoc;
            panel.incrementNumPastesToSameLocation();
        }
        else
        {
            panel.setLastPastedLocation(new Point2D.Float((int)centreOfWindow.getX() + translateVectorX, 
                        (int)centreOfWindow.getY() + translateVectorY));
        }


        for (State s : states)
        {
            int newX = (int)(s.getX() + translateVectorX);
            int newY = (int)(s.getY() + translateVectorY);
            s.setPosition(newX, newY);
        }

        for (Transition t : transitions)
        {
            int newX = (int)(t.getControlPoint().getX() + translateVectorX);
            int newY = (int)(t.getControlPoint().getY() + translateVectorY);
            t.setControlPoint(newX, newY);
        }
    }
  
    /**
     * Computes the centroid (centre of mass) of the positions of a collection of states.
     * @param states The set of states.
     * @return The centroid of the states.
     */
    private static Point2D computeCentroid(Collection<? extends State> states)
    {
        float totalX = 0;
        float totalY = 0;

        for (State s : states)
        {
            totalX += s.getX() + State.STATE_RENDERING_WIDTH / 2; // Use middle of state
            totalY += s.getY() + State.STATE_RENDERING_WIDTH / 2; // instead of top-left
        }
        return new Point2D.Float(totalX / states.size(), totalY / states.size());
    }
  
    /**
     * Update the undo/redo buttons with the new undo/redo command names.
     */
    public void updateUndoActions()
    {
        MachineGraphicsPanel panel = getSelectedGraphicsPanel();
        if (panel != null && isEditingEnabled())
        {
            String undoCommandName = panel.undoCommandName();
            if (undoCommandName != null)
            {
                m_undoAction.putValue(Action.NAME, "Undo " + undoCommandName);
                m_undoAction.setEnabled(true);
            }
            else
            {
                m_undoAction.setEnabled(false);
                m_undoAction.putValue(Action.NAME, "Undo");
            }

            String redoCommandName = panel.redoCommandName();
            if (redoCommandName != null)
            {
                m_redoAction.putValue(Action.NAME, "Redo " + redoCommandName);
                m_redoAction.setEnabled(true);
            }
            else
            {
                m_redoAction.setEnabled(false);
                m_redoAction.putValue(Action.NAME, "Redo");
            }
        }
        else
        {
            m_undoAction.setEnabled(false);
            m_undoAction.putValue(Action.NAME, "Undo");
            m_redoAction.setEnabled(false);
            m_redoAction.putValue(Action.NAME, "Redo");
        }

        m_undoToolBarButton.setText("");
        m_redoToolBarButton.setText("");
    }
   
    /**
     * A wrapper around AbstractAction which exposes a more useful constructor, more easily allowing
     * for anonymous actions.
     */
    protected abstract class MenuAction extends AbstractAction
    {
        /**
         * Creates a new instance of MenuAction
         * @param text A name for the action.
         * @param icon An image for the action.
         * @param desc A description of the action.
         * @param accel The accelerator key for this action. Null if no accelerator.
         */
        public MenuAction(String text, Icon icon, String desc, KeyStroke accel)
        {
            super(text);
            putValue(SMALL_ICON, icon);
            putValue(SHORT_DESCRIPTION, desc != null? desc : text);
            putValue(ACCELERATOR_KEY, accel);
        }
    }

    /**
     * Action for saving a machine diagram.
     */
    class SaveMachineAction extends MenuAction
    {
        /**
         * Creates a new instance of SaveMachineAction.
         * @param text Description of the action.
         * @param icon Icon for the action.
         * @param forceDialog Whether or not this action should always show a file chooser.
         *                    Setting this to true creates a save-as action, while setting it to
         *                    false creates a save action.
         */
        public SaveMachineAction(String text, Icon icon, boolean forceDialog)
        {
            super(text, icon, null, KeyStroke.getKeyStroke(KeyEvent.VK_S, KeyEvent.CTRL_DOWN_MASK));
            m_force = forceDialog;
        }
       
        /**
         * Save the machine to file. If forceDialog is set to true, or the machine has no associated
         * file, a dialog is shown. Otherwise, the file is saved to its associated file.
         * @param e The generating event.
         */
        public void actionPerformed(ActionEvent e)
        {
            MachineGraphicsPanel panel = getSelectedGraphicsPanel();
            if (panel == null)
            {
                // Whatever we are looking at isn't a graphics panel
                return;
            }

            Machine machine = panel.getSimulator().getMachine();
            File outFile = panel.getFile();
            try
            {
                if (m_force || outFile == null)
                {
                    // !!!
                    outFile = chooseSaveFile(m_fcMachine, "Save Machine", panel.getMachineExt());
                    if (outFile == null)
                    {
                        // Cancelled by user 
                        return;
                    }
                }
                
                Machine.saveMachine(machine, outFile);
                panel.setModifiedSinceSave(false);
                panel.setFile(outFile);
                m_console.log("Successfully saved machine %s", panel.getFrame().getTitle());
            }
            catch (IOException ex)
            {
                m_console.log("Encountered an error when saving the machine %s: %s",
                              panel.getFrame().getTitle(), ex.getMessage());
                Global.showErrorMessage("Save Machine", "Error saving machine %s", 
                        panel.getFrame().getTitle());
            }
        }

        /**
         * Whether or not a file chooser should always be displayed.
         */
        private final boolean m_force;
    }
 
    /** 
     * Action for saving a tape.
     */
    class SaveTapeAction extends MenuAction
    {
        /**
         * Creates a new instance of SaveTapeAction. 
         * @param text Description of the action.
         * @param icon Icon for the action.
         * @param forceDialog Whether or not this action should always show a file chooser.
         *                    Setting this to true creates a save-as action, while setting it to
         *                    false creates a save action.
         */
        public SaveTapeAction(String text, Icon icon, boolean forceDialog)
        {
            super(text, icon, null, KeyStroke.getKeyStroke(KeyEvent.VK_S, KeyEvent.CTRL_DOWN_MASK | KeyEvent.SHIFT_DOWN_MASK));
            m_force = forceDialog;
        }
        
        /**
         * Save the tape to its associated file. If it does not have an associated file, display a
         * dialog.
         * @param e The generating event.
         */
        public void actionPerformed(ActionEvent e)
        {
            Tape tape = m_tapeDisp.getTape();
            File outFile = m_tapeDisp.getFile();

            try
            {
                if (m_force || outFile == null)
                {
                    outFile = chooseSaveFile(m_fcTape, "Save Tape", Tape.TAPE_EXTENSION);
                    if (outFile == null)
                    {
                        // Cancelled by user 
                        return;
                    }
                }
                
                Tape.saveTape(m_tapeDisp.getTape(), outFile);
                m_tapeDisp.setFile(outFile);
                m_console.log("Successfully saved tape to %s", outFile.toString());
            }
            catch (IOException ex)
            {
                m_console.log("Encountered an error when saving the tape to %s: %s",
                              outFile.toString(), ex.getMessage());
                Global.showErrorMessage("Save Tape", "Error saving tape to %s", outFile.toString());
            }
        }

        /**
         * Whether or not a file chooser should always be displayed.
         */
        private final boolean m_force;
    }

    /**
     * An action for selecting user interface interaction modes.
     */
    class GUI_ModeSelectionAction extends MenuAction
    {
        /**
         * Creates a new instance of GUI_ModeSelectionAction.
         * @param text Description of the action.
         * @param mode Mode the action puts the GUI into.
         * @param icon Icon for the action.
         * @param keyShortcut Shortcut associated with the action.
         */
        public GUI_ModeSelectionAction(String text, GUI_Mode mode, Icon icon, KeyStroke keyShortcut)
        {
            super(text, icon, null, keyShortcut);
            m_mode = mode;
        }

        /**
         * Change the UI mode, and select the relevant menu item.
         */
        public void actionPerformed(ActionEvent e)
        {
            setUIMode(m_mode);
            if (m_menuItem != null)
            {
                m_menuItem.setSelected(true);
            }
        }
        
        /**
         * Set the associated menu item.
         * @param menuItem The menu item associated with this action.
         */
        public void setMenuItem(JRadioButtonMenuItem menuItem)
        {
            m_menuItem = menuItem;
        }

        /**
         * The GUI mode associated with this action.
         */
        private GUI_Mode m_mode;

        /**
         * The menu item associated with this action.
         */
        private JRadioButtonMenuItem m_menuItem = null;
    }
   
    /**
     * An action for selecting speeds for automatic execution of machines.
     */
    class ExecutionSpeedSelectionAction extends MenuAction
    {
        /**
         * Creates a new instance of ExecutionSpeedSelectionAction.
         * @param text Description of the action.
         * @param delay The new execution delay for the machine.
         * @param keyShortcut Shortcut associated with the action.
        */
        public ExecutionSpeedSelectionAction(String text, int delay, KeyStroke keyShortcut)
        {
            super(text, null, null, keyShortcut);
            m_delay = delay;
        }

        /**
         * Change the execution delay of the machine.
         * @param e The generating event.
         */
        public void actionPerformed(ActionEvent e)
        {
           m_executionDelayTime = m_delay;
        }
        
        /**
         * The delay for execution of the machine.
         */
        private int m_delay;
    }

   
    /**
     * Singleton instance of MainWindow.
     */
    private static /*final*/ MainWindow m_instance;

    /**
     * Current GUI mode.
     */
    private GUI_Mode m_currentMode;
    
    /**
     * Whether the keyboard is currently enabled.
     */
    private boolean m_keyboardEnabled = true;

    /**
     * Whether editing is currently enabled.
     */
    private boolean m_editingEnabled = true;

    /**
     * Dialog for choosing a file, specifically for machines.
     */
    private final JFileChooser m_fcMachine = new JFileChooser();

    /**
     * Dialog for choosing a file, specifically for tapes.
     */
    private final JFileChooser m_fcTape = new JFileChooser();

    /**
     * Internal timer for repeatedly calling m_timerTask.
     */
    protected final java.util.Timer m_timer = new java.util.Timer(true);

    /**
     * Timer task used for stepping through a machine on a delay.
     */
    private ExecutionTimerTask m_timerTask;
    
    /**
     * Simulation delay associated with the machine, used by m_timerTask.
     */
    private int m_executionDelayTime;

    /**
     * How many machine internal frames have been created since the program started.
     */
    private int m_windowCount = 0;

    /**
     * Data which has been copied, used for pasting.
     */
    private byte[] m_copiedData = null;

    /**
     * List of buttons which have an associated GUI mode and action.
     */
    private ArrayList<GUIModeButton> m_toolbarButtons;

    /**
     * The toolbar strip.
     */
    private JPanel m_toolbar;

    /**
     * Selector for the execution speed.
     */
    private JComboBox<String> m_speedSelector;

    /**
     * The status bar along the bottom of the window.
     */
    private StatusBar m_statusBar;

    /**
     * The panel shown when no machine is open.
     */
    private WelcomePanel m_welcome;

    /**
     * Container holding either the tabs or the welcome panel.
     */
    private JPanel m_documentArea;

    /**
     * Split pane dividing the document area from the console.
     */
    private JSplitPane m_mainSplit;

    /**
     * Position of the console divider, remembered so the console can be hidden and restored.
     */
    private int m_consoleDividerLocation = -1;

    /**
     * Menu item reflecting whether the light palette is in force.
     */
    private JRadioButtonMenuItem m_lightThemeItem;

    /**
     * Menu item reflecting whether the dark palette is in force.
     */
    private JRadioButtonMenuItem m_darkThemeItem;

    /**
     * Menu item reflecting whether the console is shown.
     */
    private JCheckBoxMenuItem m_showConsoleItem;

    /**
     * Menu item reflecting whether the status bar is shown.
     */
    private JCheckBoxMenuItem m_showStatusBarItem;

    /**
     * Menu item reflecting whether the tape is shown.
     */
    private JCheckBoxMenuItem m_showTapeItem;

    /**
     * Tape display panel.
     */
    private TapeDisplayPanel m_tapeDisp;

    /**
     * Tape controller.
     */
    private TapeDisplayControllerPanel m_tapeDispController;

    /**
     * Main shared tape.
     */
    private Tape m_tape;

    /**
     * Toolbar button for undoing an action.
     */
    private FlatButton m_undoToolBarButton;

    /**
     * Toolbar button for redoing an action.
     */
    private FlatButton m_redoToolBarButton;

    /**
     * Tabbed pane for the window, containing all open machines.
     */
    private MachineTabPane m_tabs;

    /**
     * Dialog for selecting the current alphabet.
     */
    private AlphabetSelectorDialog m_alphabetDialog;

    /**
     * Window for displaying help information as HTML.
     */
    private HelpDialog m_helpDisp;

    /**
     * Frame for displaying a console window for logging information.
     */
    private ConsolePanel m_console;

    /**
     * Action for creating a new Turing Machine.
     */
    public final Action m_newTuringMachineAction = 
        new MenuAction("New Turing Machine", Icons.get("new-machine", MENU_ICON_SIZE), null, null)
        {
            public void actionPerformed(ActionEvent e)
            {
                if (m_tabs != null)
                {
                    TMGraphicsPanel panel = new TMGraphicsPanel(new TM_Machine(), m_tape, null);
                    MachineInternalFrame frame = newMachineWindow(panel);
                    panel.setFrame(frame);
                    addFrame(frame);
                }
            }
        };

    /**
     * Action for creating a new DFSA.
     */
    public final Action m_newDFSAAction = 
        new MenuAction("New DFSA", Icons.get("dfsa", MENU_ICON_SIZE), null, null)
        {
            public void actionPerformed(ActionEvent e)
            {
                if (m_tabs != null)
                {
                    addFrame(newMachineWindow(new DFSAGraphicsPanel(new DFSA_Machine(), m_tape, null)));
                }
            }
        };

    /**
     * Action for opening a machine.
     */
    public final Action m_openMachineAction = 
        new MenuAction("Open Machine", Icons.get("open", MENU_ICON_SIZE), null, 
                       KeyStroke.getKeyStroke(KeyEvent.VK_O, KeyEvent.CTRL_DOWN_MASK))
        {
            public void actionPerformed(ActionEvent e)
            {
                // Choose the file to load
                File inFile = chooseLoadFile(m_fcMachine, "Load Machine", "");
                if (inFile == null)
                {
                    // Cancelled by user
                    return;
                }
                try
                {
                    Machine machine = Machine.loadMachine(inFile);

                    if (machine instanceof TM_Machine)
                    {
                        TMGraphicsPanel panel = new TMGraphicsPanel((TM_Machine)machine, m_tape, inFile);
                        MachineInternalFrame frame = newMachineWindow(panel);
                        panel.setFrame(frame);
                        addFrame(frame);
                    }
                    else if (machine instanceof DFSA_Machine)
                    {
                        addFrame(newMachineWindow(new DFSAGraphicsPanel((DFSA_Machine)machine, m_tape, inFile)));
                    }
                    m_console.log("Successfully loaded machine file %s", inFile.toString());
                }
                catch (Exception ex)
                {
                    m_console.log("Encountered an error when opening machine file %s: %s", 
                                  inFile.toString(), ex.getMessage());
                    Global.showErrorMessage("Open Machine", "Error opening machine file %s", inFile.toString()); 
                }
            }
        };

    /**
     * Action for saving a machine to an associated file.
     */
    public final Action m_saveMachineAction = 
        new SaveMachineAction("Save Machine", Icons.get("save", MENU_ICON_SIZE), false);

    /**
     * Action for saving a machine to a selected file.
     */
    public final Action m_saveMachineAsAction = 
        new SaveMachineAction("Save Machine As", null, true);

    /**
     * Action for creating a new tape.
     */
    public final Action m_newTapeAction = 
        new MenuAction("New Tape", Icons.get("new-tape", MENU_ICON_SIZE), null, 
                       KeyStroke.getKeyStroke(KeyEvent.VK_N, KeyEvent.CTRL_DOWN_MASK | KeyEvent.SHIFT_DOWN_MASK))
        {
            public void actionPerformed(ActionEvent e)
            {
                // Prevent the program from reading from the keyboard while the file dialog is active
                m_keyboardEnabled = false;
                Object[] options = { "Ok", "Cancel" };
                int result = JOptionPane.showOptionDialog(MainWindow.this, 
                        "This will erase the tape. Do you want to continue?", "Clear tape",
                        JOptionPane.OK_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE, null, 
                        options, options[1]);
                m_keyboardEnabled = true;

                if (result == JOptionPane.YES_OPTION)
                {
                    m_tape.copyOther(new CA_Tape());
                    m_tapeDisp.repaint();
                }
            }
        };

    /**
     * Action for opening a tape.
     */
    public final Action m_openTapeAction = 
        new MenuAction("Open Tape", Icons.get("open-tape", MENU_ICON_SIZE), null, 
                       KeyStroke.getKeyStroke(KeyEvent.VK_O, KeyEvent.CTRL_DOWN_MASK | KeyEvent.SHIFT_DOWN_MASK))
        {
            public void actionPerformed(ActionEvent e)
            {
                File inFile = chooseLoadFile(m_fcTape, "Load Tape", Tape.TAPE_EXTENSION);
                if (inFile == null)
                {
                    // Cancelled by user
                    return;
                }

                try
                {
                    Tape tape = Tape.loadTape(inFile);
                    m_tapeDisp.getTape().copyOther(tape);
                    m_tapeDisp.setFile(inFile);
                    m_tapeDisp.repaint();
                    m_console.log("Successfully loaded tape file %s", inFile.toString());
                }
                catch (Exception ex)
                {
                    m_console.log("Encountered an error when opening tape file %s: %s",
                                  inFile.toString(), ex.getMessage());
                    Global.showErrorMessage("Open Tape", "Error opening tape file %s", inFile.toString());
                }
            }
        };

    /**
     * Action for saving a tape to an associated file.
     */
    public final Action m_saveTapeAction = 
        new SaveTapeAction("Save Tape", Icons.get("save-tape", MENU_ICON_SIZE), false);
    
    /**
     * Action for saving a tape to a selected file.
     */
    public final Action m_saveTapeAsAction = 
        new SaveTapeAction("Save Tape As", null, true);

    /**
     * Action for closing the current machine.
     */
    public final Action m_closeMachineAction =
        new MenuAction("Close Machine", null, null,
                       KeyStroke.getKeyStroke(KeyEvent.VK_W, KeyEvent.CTRL_DOWN_MASK))
        {
            public void actionPerformed(ActionEvent e)
            {
                MachineInternalFrame doc = m_tabs.getSelectedDocument();
                if (doc != null)
                {
                    userConfirmSaveModifiedThenClose(doc);
                }
            }
        };

    /**
     * Action for exiting the program.
     */
    public final Action m_exitAction =
        new MenuAction("Exit", null, null, null)
        {
            public void actionPerformed(ActionEvent e)
            {
                userRequestToExit();
            }
        };

    /**
     * Action for switching to the light palette.
     */
    public final Action m_lightThemeAction =
        new MenuAction("Light", Icons.get("light", MENU_ICON_SIZE), null, null)
        {
            public void actionPerformed(ActionEvent e)
            {
                Theme.set(Theme.LIGHT);
            }
        };

    /**
     * Action for switching to the dark palette.
     */
    public final Action m_darkThemeAction =
        new MenuAction("Dark", Icons.get("dark", MENU_ICON_SIZE), null, null)
        {
            public void actionPerformed(ActionEvent e)
            {
                Theme.set(Theme.DARK);
            }
        };

    /**
     * Action for showing and hiding the console.
     */
    public final Action m_toggleConsoleAction =
        new MenuAction("Console", Icons.get("console", MENU_ICON_SIZE), null,
                       KeyStroke.getKeyStroke(KeyEvent.VK_J, KeyEvent.CTRL_DOWN_MASK))
        {
            public void actionPerformed(ActionEvent e)
            {
                boolean show = !m_console.isVisible();
                if (!show)
                {
                    m_consoleDividerLocation = m_mainSplit.getDividerLocation();
                }
                m_console.setVisible(show);
                m_mainSplit.setDividerSize(show? 6 : 0);
                m_mainSplit.resetToPreferredSizes();
                if (show && m_consoleDividerLocation > 0)
                {
                    m_mainSplit.setDividerLocation(m_consoleDividerLocation);
                }
                m_showConsoleItem.setSelected(show);
            }
        };

    /**
     * Action for showing and hiding the status bar.
     */
    public final Action m_toggleStatusBarAction =
        new MenuAction("Status Bar", Icons.get("statusbar", MENU_ICON_SIZE), null, null)
        {
            public void actionPerformed(ActionEvent e)
            {
                m_statusBar.setVisible(!m_statusBar.isVisible());
                m_showStatusBarItem.setSelected(m_statusBar.isVisible());
                getContentPane().revalidate();
            }
        };

    /**
     * Action for showing and hiding the tape.
     */
    public final Action m_toggleTapeAction =
        new MenuAction("Tape", Icons.get("tape", MENU_ICON_SIZE), null, null)
        {
            public void actionPerformed(ActionEvent e)
            {
                m_tapeDispController.setVisible(!m_tapeDispController.isVisible());
                m_showTapeItem.setSelected(m_tapeDispController.isVisible());
                getContentPane().revalidate();
            }
        };

    /**
     * Action for magnifying the diagram.
     */
    public final Action m_zoomInAction =
        new MenuAction("Zoom In", Icons.get("zoom-in", MENU_ICON_SIZE), null,
                       KeyStroke.getKeyStroke(KeyEvent.VK_EQUALS, KeyEvent.CTRL_DOWN_MASK))
        {
            public void actionPerformed(ActionEvent e)
            {
                MachineGraphicsPanel panel = getSelectedGraphicsPanel();
                if (panel != null)
                {
                    panel.zoomIn();
                }
            }
        };

    /**
     * Action for shrinking the diagram.
     */
    public final Action m_zoomOutAction =
        new MenuAction("Zoom Out", Icons.get("zoom-out", MENU_ICON_SIZE), null,
                       KeyStroke.getKeyStroke(KeyEvent.VK_MINUS, KeyEvent.CTRL_DOWN_MASK))
        {
            public void actionPerformed(ActionEvent e)
            {
                MachineGraphicsPanel panel = getSelectedGraphicsPanel();
                if (panel != null)
                {
                    panel.zoomOut();
                }
            }
        };

    /**
     * Action for returning the diagram to actual size.
     */
    public final Action m_zoomResetAction =
        new MenuAction("Actual Size", Icons.get("zoom-reset", MENU_ICON_SIZE), null,
                       KeyStroke.getKeyStroke(KeyEvent.VK_0, KeyEvent.CTRL_DOWN_MASK))
        {
            public void actionPerformed(ActionEvent e)
            {
                MachineGraphicsPanel panel = getSelectedGraphicsPanel();
                if (panel != null)
                {
                    panel.resetZoom();
                }
            }
        };

    /**
     * Action for undoing a command.
     */
    public final Action m_undoAction =
        new MenuAction("Undo", Icons.get("undo", MENU_ICON_SIZE), null, 
                       KeyStroke.getKeyStroke(KeyEvent.VK_Z, KeyEvent.CTRL_DOWN_MASK))
        {
            public void actionPerformed(ActionEvent e)
            {
                MachineGraphicsPanel panel = getSelectedGraphicsPanel();
                if (panel != null)
                {
                    panel.undoCommand();
                    updateUndoActions();
                    panel.repaint();
                }
            }
        };

    /**
     * Action for redoing a command
     */
    public final Action m_redoAction = 
        new MenuAction("Redo", Icons.get("redo", MENU_ICON_SIZE), null, 
                       KeyStroke.getKeyStroke(KeyEvent.VK_Y, KeyEvent.CTRL_DOWN_MASK))
        {
            public void actionPerformed(ActionEvent e)
            {   
                MachineGraphicsPanel panel = getSelectedGraphicsPanel();
                if (panel != null)
                {
                    panel.redoCommand();
                    updateUndoActions();
                    panel.repaint();
                }
            }
        };

    /**
     * Action for cutting selected states and transitions.
     */
    public final Action m_cutAction = 
        new MenuAction("Cut", Icons.get("cut", MENU_ICON_SIZE), null, 
                       KeyStroke.getKeyStroke(KeyEvent.VK_X, KeyEvent.CTRL_DOWN_MASK))
        {
            public void actionPerformed(ActionEvent e)
            {   
                m_copyAction.actionPerformed(e); 
                MachineGraphicsPanel panel = getSelectedGraphicsPanel();
                if (panel != null)
                {
                    panel.doCommand(new CutCommand(panel, 
                                (HashSet<? extends State>)panel.getSelectedStates().clone(),
                                (HashSet<? extends Transition>)panel.getSelectedTransitions().clone()));
                    updateUndoActions();
                }
            }
        };

    /**
     * Action for copying selected states and transitions.
     */
    public final Action m_copyAction = 
        new MenuAction("Copy", Icons.get("copy", MENU_ICON_SIZE), null, 
                       KeyStroke.getKeyStroke(KeyEvent.VK_C, KeyEvent.CTRL_DOWN_MASK))
        {
            public void actionPerformed(ActionEvent e)
            {   
                MachineGraphicsPanel panel = getSelectedGraphicsPanel();
                if (panel != null)
                {
                    m_copiedData = panel.copySelectedToByteArray();
                }
            }
        };

    /**
     * Action for pasting selected states and transitions.
     */
    public final Action m_pasteAction = 
        new MenuAction("Paste", Icons.get("paste", MENU_ICON_SIZE), null, 
                       KeyStroke.getKeyStroke(KeyEvent.VK_V, KeyEvent.CTRL_DOWN_MASK))
        {
            public void actionPerformed(ActionEvent e)
            {   
                try
                {
                    MachineInternalFrame iFrame = m_tabs.getSelectedDocument();
                    if (m_copiedData == null || iFrame == null)
                    {
                        // Abort
                        return;
                    }

                    // Translate our byte[] back into real data
                    ObjectInputStream restore = new ObjectInputStream(new ByteArrayInputStream(m_copiedData));
                    HashSet<State> selectedStates = (HashSet<State>)restore.readObject();
                    HashSet<Transition> selectedTransitions = (HashSet<Transition>)restore.readObject();

                    // Figure out roughly the centre-of-mass of the copied data
                    Point2D centroid = computeCentroid(selectedStates);

                    // Find the centre of the frame
                    Point2D centreOfWindow = iFrame.getCenterOfViewPort();
                    translateCentroidToMiddleOfWindow(selectedStates, selectedTransitions,
                            centreOfWindow, iFrame.getGfxPanel().getLastPastedLocation(),
                            iFrame.getGfxPanel().getNumPastesToSameLocation(), iFrame.getGfxPanel());

                    MachineGraphicsPanel panel = getSelectedGraphicsPanel();
                    if (panel != null)
                    {
                        Machine machine = panel.getSimulator().getMachine();
                        panel.doCommand(new PasteCommand(panel, selectedStates, selectedTransitions)); 
                        updateUndoActions();
                    }
                }
                catch (Exception e2) { }
            }
        };

    /**
     * Action for deleting selected states and transitions.
     */
    public final Action m_deleteAction = 
        new MenuAction("Delete Selected Items", Icons.get("delete", MENU_ICON_SIZE), null, 
                       KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0))
        {
            public void actionPerformed(ActionEvent e)
            {   
                MachineGraphicsPanel panel = getSelectedGraphicsPanel();
                if (panel != null)
                {
                    panel.deleteAllSelected();
                }
            }
        };

    /**
     * Action associated with ADDNODES.
     */
    public final GUI_ModeSelectionAction m_addNodesAction = 
        new GUI_ModeSelectionAction("Add States", GUI_Mode.ADDNODES,
            Icons.get("state", MENU_ICON_SIZE), KeyStroke.getKeyStroke(KeyEvent.VK_F2,0));

    /**
     * Action associated with ADDTRANSITIONS.
     */
    public final GUI_ModeSelectionAction m_addTransitionsAction = 
        new GUI_ModeSelectionAction("Add Transitions", GUI_Mode.ADDTRANSITIONS,
            Icons.get("transition", MENU_ICON_SIZE), KeyStroke.getKeyStroke(KeyEvent.VK_F3,0));

    /**
     * Action associated with SELECTION.
     */
    public final GUI_ModeSelectionAction m_selectionAction = 
        new GUI_ModeSelectionAction("Make Selection", GUI_Mode.SELECTION,
            Icons.get("select", MENU_ICON_SIZE), KeyStroke.getKeyStroke(KeyEvent.VK_F4,0));

    /**
     * Action associated with ERASER.
     */
    public final GUI_ModeSelectionAction m_eraserAction = 
        new GUI_ModeSelectionAction("Eraser", GUI_Mode.ERASER, 
            Icons.get("eraser", MENU_ICON_SIZE), KeyStroke.getKeyStroke(KeyEvent.VK_F5,0));

    /**
     * Action associated with CHOOSESTART.
     */
    public final GUI_ModeSelectionAction m_chooseStartAction = 
        new GUI_ModeSelectionAction("Choose Start State", GUI_Mode.CHOOSESTART, 
            Icons.get("start", MENU_ICON_SIZE), KeyStroke.getKeyStroke(KeyEvent.VK_F6,0));

    /**
     * Action associated with CHOOSEFINAL.
     */
    public final GUI_ModeSelectionAction m_chooseFinalAction = 
        new GUI_ModeSelectionAction("Choose Final State", GUI_Mode.CHOOSEFINAL,
            Icons.get("final", MENU_ICON_SIZE), KeyStroke.getKeyStroke(KeyEvent.VK_F7,0));

    /**
     * Action associated with CHOOSECURRENTSTATE.
     */
    public final GUI_ModeSelectionAction m_chooseCurrentStateAction = 
        new GUI_ModeSelectionAction("Choose Current State", GUI_Mode.CHOOSECURRENTSTATE,
            Icons.get("current", MENU_ICON_SIZE), KeyStroke.getKeyStroke(KeyEvent.VK_F8,0));

    /**
     * Action for validating the machine.
     */
    public final Action m_validateAction =
        new MenuAction("Validate", Icons.get("validate", MENU_ICON_SIZE), null,
                       KeyStroke.getKeyStroke(KeyEvent.VK_D, KeyEvent.CTRL_DOWN_MASK))
        {
            public void actionPerformed(ActionEvent e)
            {
                MachineGraphicsPanel gfxPanel = getSelectedGraphicsPanel();
                if (gfxPanel == null)
                {
                    return;
                }

                String result = gfxPanel.getSimulator().getMachine().isDeterministic();
                if (result == null)
                {
                    m_console.log("%s is deterministic",
                            gfxPanel.getFrame().getTitle());
                    Global.showInfoMessage("Validation", "Machine is deterministic"); 
                }
                else
                {
                    m_console.log("%s is nondeterministic: %s", 
                            gfxPanel.getFrame().getTitle(), result);
                    Global.showErrorMessage("Validation", "Machine is nondeterministic: %s",  result);
                }
            }
        };

    /**
     * Action for stepping through execution.
     */
    public final Action m_stepAction = 
        new MenuAction("Step", Icons.get("step", MENU_ICON_SIZE), null, 
                       KeyStroke.getKeyStroke(KeyEvent.VK_T, KeyEvent.CTRL_DOWN_MASK))
        {
            public void actionPerformed(ActionEvent e)
            {
                MachineGraphicsPanel gfxPanel = getSelectedGraphicsPanel();
                if (gfxPanel == null)
                {
                    return;
                }

                try
                {
                    Simulator sim = gfxPanel.getSimulator();
                    // Pre-validate the machine
                    String result = sim.getMachine().hasUndefinedSymbols();
                    if (result != null)
                    {
                        m_console.log("Cannot simulate %s: %s", 
                                gfxPanel.getFrame().getTitle(), result);
                        Global.showErrorMessage("Step", "Cannot simulate: %s", result);
                        return;
                    }
                    // If we are just starting, write out the input on the tape
                    if (sim.getCurrentState() == null)
                    {
                        // Issue a minor warning to the console if the r/w head is not in the
                        // leftmost cell; continue execution
                        if (m_tape.headLocation() != 0)
                        {
                            m_console.log("Warning: Tape head has not been reset");
                        }
                        m_console.logPartial(gfxPanel, "Input: %s\n", 
                                m_tape.getPartialString(m_tape.headLocation(), 
                                                        m_tape.getLength() - m_tape.headLocation()));
                    }
                    sim.step();
                    m_tapeDisp.repaint();
                    if (sim.isHalted())
                    {
                        m_console.logPartial(gfxPanel, sim.getConfiguration());
                        m_console.endPartial();
                    }
                    else
                    {
                        m_console.logPartial(gfxPanel, "%s %c ", sim.getConfiguration(), Global.CONFIG_TEE);
                    }
                }
                // Machine halted as expected
                catch (ComputationCompletedException e2)
                {
                    String msg = gfxPanel.getErrorMessage(e2);
                    m_console.log("Simulation of %s finished: %s", 
                            gfxPanel.getFrame().getTitle(), msg);
                    Global.showInfoMessage(MainWindow.HALTED_MESSAGE_TITLE_STR, 
                            "Simulation finished: %s", msg);
                    gfxPanel.getSimulator().resetMachine();
                }
                // Machine halted unexpectedly
                catch (Exception e2)
                {
                    String msg = gfxPanel.getErrorMessage(e2);
                    m_console.log("Simulation of %s halted unexpectedly: %s",
                            gfxPanel.getFrame().getTitle(), msg);
                    Global.showErrorMessage(MainWindow.HALTED_MESSAGE_TITLE_STR,
                            "Simulation halted unexpectedly: %s", msg);
                }
                repaint();
                refreshStatus();
            }
        };

    /**
     * Action for starting simulation of the machine.
     */
    public final Action m_fastExecuteAction = 
        new MenuAction("Execute", Icons.get("run", MENU_ICON_SIZE), null, 
                       KeyStroke.getKeyStroke(KeyEvent.VK_E, KeyEvent.CTRL_DOWN_MASK))
        {
            public void actionPerformed(ActionEvent e)
            { 
                MachineGraphicsPanel panel = getSelectedGraphicsPanel();
                if (panel != null)
                {
                    if (m_timerTask != null)
                    {
                        m_timerTask.cancel();
                    }
                    setEditingEnabled(false);

                    // A delay of zero is not a timer interval; it means run the machine through to
                    // a halt in one go rather than animating it step by step.
                    if (m_executionDelayTime <= 0)
                    {
                        executeWithoutDelay(panel);
                        return;
                    }

                    m_timerTask = new ExecutionTimerTask(panel, m_tapeDisp);
                    m_timer.schedule(m_timerTask, 0, m_executionDelayTime);
                }
            }
        };

    /**
     * Action for pausing simulation of the machine.
     */
    public final Action m_pauseExecutionAction = 
        new MenuAction("Pause Execution", Icons.get("pause", MENU_ICON_SIZE), null, 
                       KeyStroke.getKeyStroke(KeyEvent.VK_P, KeyEvent.CTRL_DOWN_MASK))
        {
            public void actionPerformed(ActionEvent e)
            {
                stopExecution();
                updateUndoActions();
            }
        };

    /**
     * Action for stopping a simulation.
     */
    public final Action m_stopMachineAction = 
        new MenuAction("Stop Execution", Icons.get("stop", MENU_ICON_SIZE), null, 
                       KeyStroke.getKeyStroke(KeyEvent.VK_R, KeyEvent.CTRL_DOWN_MASK))
        {
            public void actionPerformed(ActionEvent e)
            {
                MachineGraphicsPanel gfxPanel = getSelectedGraphicsPanel();
                boolean wasRunning = stopExecution();
                if (gfxPanel != null)
                {
                    // TODO: reset it even if not running
                    if (m_timerTask == null || !wasRunning || gfxPanel == m_timerTask.getPanel())
                    {
                        gfxPanel.getSimulator().resetMachine();
                        gfxPanel.repaint();
                        m_console.log("Stopped executing %s", gfxPanel.getFrame().getTitle());
                    }
                }
                updateUndoActions();
            }
        };

    /**
     * Action to set execution speed to slow.
     */
    public final ExecutionSpeedSelectionAction m_slowExecuteSpeedAction = 
        new ExecutionSpeedSelectionAction("Slow", SLOW_EXECUTE_SPEED_DELAY,
                KeyStroke.getKeyStroke(KeyEvent.VK_1, KeyEvent.CTRL_DOWN_MASK));

    /**
     * Action to set execution speed to medium.
     */
    public final ExecutionSpeedSelectionAction m_mediumExecuteSpeedAction = 
        new ExecutionSpeedSelectionAction("Medium", MEDIUM_EXECUTE_SPEED_DELAY,
                KeyStroke.getKeyStroke(KeyEvent.VK_2, KeyEvent.CTRL_DOWN_MASK));

    /**
     * Action to set execution speed to fast.
     */
    public final ExecutionSpeedSelectionAction m_fastExecuteSpeedAction =
        new ExecutionSpeedSelectionAction("Fast", FAST_EXECUTE_SPEED_DELAY,
                KeyStroke.getKeyStroke(KeyEvent.VK_3, KeyEvent.CTRL_DOWN_MASK));

    /**
     * Action to set execution speed to superfast.
     */
    public final ExecutionSpeedSelectionAction m_superFastExecuteSpeedAction = 
        new ExecutionSpeedSelectionAction("Super Fast", SUPERFAST_EXECUTE_SPEED_DELAY,
                KeyStroke.getKeyStroke(KeyEvent.VK_4, KeyEvent.CTRL_DOWN_MASK));

    /**
     * Action to set execution speed to ultrafast.
     */
    public final ExecutionSpeedSelectionAction m_ultraFastExecuteSpeedAction =
        new ExecutionSpeedSelectionAction("Ultra Fast", ULTRAFAST_EXECUTE_SPEED_DELAY,
                KeyStroke.getKeyStroke(KeyEvent.VK_5, KeyEvent.CTRL_DOWN_MASK));

    /**
     * Action to run the machine straight through to a halt, with no delay between steps.
     */
    public final ExecutionSpeedSelectionAction m_zeroDelayExecuteSpeedAction =
        new ExecutionSpeedSelectionAction("Zero Delay", ZERO_EXECUTE_SPEED_DELAY,
                KeyStroke.getKeyStroke(KeyEvent.VK_6, KeyEvent.CTRL_DOWN_MASK));

    /**
     * Action for moving the read/write head to the start of the tape.
     */
    public final Action m_headToStartAction = 
        new MenuAction("Reset Read/Write Head", Icons.get("tape-start", MENU_ICON_SIZE), null, 
                       KeyStroke.getKeyStroke(KeyEvent.VK_M, KeyEvent.CTRL_DOWN_MASK))
        {
            public void actionPerformed(ActionEvent e) 
            {
                // Move r/w head to the left end of the tape
                m_tapeDisp.getTape().resetRWHead();
                m_tapeDispController.repaint();
            }
        };

    /**
     * Action for reloading the tape.
     */
    public final Action m_reloadTapeAction = 
        new MenuAction("Reload Tape", Icons.get("tape-reload", MENU_ICON_SIZE), null, 
                       KeyStroke.getKeyStroke(KeyEvent.VK_L, KeyEvent.CTRL_DOWN_MASK))
        {
            public void actionPerformed(ActionEvent e) 
            {
                Object[] options = {"Ok", "Cancel"};
                File tfile = m_tapeDisp.getFile();

                // TODO: should disable keyboard here
                if (tfile == null)
                {
                    int result = JOptionPane.showOptionDialog(null,
                            "This will erase the tape. Do you want to continue?", "Reload tape",
                            JOptionPane.OK_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE, null,
                            options, options[1]);
                    if (result == JOptionPane.YES_OPTION)
                    {
                        m_tape.clearTape();
                    }
                }
                else
                {
                    int result = JOptionPane.showOptionDialog(null, 
                            "This will reload the tape, discarding any changes. Do you want to continue?", 
                            "Reload tape", JOptionPane.OK_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE,
                            null, options, options[1]);
                    if (result == JOptionPane.YES_OPTION) try
                    {
                        m_tape = Tape.loadTape(tfile);
                        m_tapeDisp.getTape().copyOther(m_tape);
                        m_tapeDisp.setFile(tfile);
                        m_tapeDisp.repaint();
                        m_console.log("Reloaded tape from file %s", tfile.toString());
                    }
                    catch (Exception ex)
                    {
                        m_console.log("Encountered an error when loading the tape %s: %s", 
                                      tfile.toString(), ex.getMessage());
                        Global.showWarningMessage("Reload Tape", "Error opening tape file %s", tfile.toString());
                    }
                }
            }
        };

    /**
     * Action for erasing the tape.
     */
    public final Action m_eraseTapeAction = 
        new MenuAction("Erase Tape", Icons.get("tape-clear", MENU_ICON_SIZE), null, 
                       KeyStroke.getKeyStroke(KeyEvent.VK_B, KeyEvent.CTRL_DOWN_MASK))
        {
            public void actionPerformed(ActionEvent e) 
            {
                // Wipe the tape.
                Object[] options = {"Ok", "Cancel"};
                // TODO: should disable keyboard here
                int result = JOptionPane.showOptionDialog(null, 
                        "This will erase the tape. Do you want to continue?", "Clear tape", 
                        JOptionPane.OK_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE, null, 
                        options, options[1]);
                if (result == JOptionPane.YES_OPTION)
                {
                    m_tapeDisp.getTape().clearTape();
                    m_tapeDisp.setFile(null);
                    m_tapeDispController.repaint();
                }
            }
        };

    /**
     * Action for configuring the alphabet.
     */
    public final Action m_configureAlphabetAction = 
        new MenuAction("Configure Alphabet", Icons.get("alphabet", MENU_ICON_SIZE), null, 
                       KeyStroke.getKeyStroke(KeyEvent.VK_A, KeyEvent.CTRL_DOWN_MASK))
        {
            public void actionPerformed(ActionEvent e)
            {
                MachineGraphicsPanel panel = getSelectedGraphicsPanel();
                if (panel != null)
                {
                    if (m_alphabetDialog == null)
                    {
                        m_alphabetDialog = new AlphabetSelectorDialog(MainWindow.this);
                    }
                    m_alphabetDialog.showFor(panel);
                }
            }
        };

    /**
     * Action for displaying help documentation.
     */
    public final Action m_helpAction = 
        new MenuAction("Help", Icons.get("help", MENU_ICON_SIZE), null, 
                       KeyStroke.getKeyStroke(KeyEvent.VK_F1, 0))
        {
            public void actionPerformed(ActionEvent e)
            {
                if (m_helpDisp == null)
                {
                    m_helpDisp = new HelpDialog(MainWindow.this);
                    m_helpDisp.setLocationRelativeTo(MainWindow.this);
                }
                m_helpDisp.setVisible(true);
                m_helpDisp.toFront();
            }
        };

    /**
     * Action for displaying meta information about the program.
     */
    public final Action m_aboutAction = 
        new MenuAction("About", Icons.get("machine", MENU_ICON_SIZE), null, null)
        {
            public void actionPerformed(ActionEvent e)
            {
                Global.showInfoMessage("About Tuatara",
                        "Tuatara Turing Machine Simulator %s was written by Jimmy Foulds in 2006-2007,\n" + 
                        "and extended by Mitchell Grout in 2017-2018, with funding from the\n"            +
                        "Department of Mathematics at the University of Waikato, New Zealand.\n"          +
                        "Graphics were kindly provided by Justin Bedggood.", Global.VERSION);
            }
        };
}
