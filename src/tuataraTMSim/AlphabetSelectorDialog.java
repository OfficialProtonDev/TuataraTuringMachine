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
import java.util.*;
import javax.swing.*;
import tuataraTMSim.commands.ConfigureAlphabetCommand;
import tuataraTMSim.commands.JoinCommand;
import tuataraTMSim.commands.RemoveInconsistentTransitionsCommand;
import tuataraTMSim.machine.Alphabet;
import tuataraTMSim.machine.Tape;
import tuataraTMSim.machine.Transition;

/**
 * A dialog used to select the current alphabet for a machine.
 *
 * This replaces an internal frame which was made pseudo-modal by parking it on the window's glass
 * pane and swallowing mouse events. Being a real modal dialog removes that machinery, and with it
 * the special-casing the main window's key handler needed in order to route typing here.
 */
public class AlphabetSelectorDialog extends JDialog
{
    /**
     * Number of symbol toggles per row in the letter grid.
     */
    protected static final int LETTER_COLUMNS = 7;

    /**
     * Number of symbol toggles per row in the digit grid.
     */
    protected static final int DIGIT_COLUMNS = 5;

    /**
     * Edge length of a symbol toggle, in pixels.
     */
    protected static final int TOGGLE_SIZE = 34;

    /**
     * Creates a new instance of AlphabetSelectorDialog.
     * @param owner The frame owning this dialog.
     */
    public AlphabetSelectorDialog(Frame owner)
    {
        super(owner, "Configure Alphabet", true);
        initComponents();
    }

    /**
     * Build the dialog.
     */
    private void initComponents()
    {
        Theme.Palette p = Theme.palette();

        JPanel root = new JPanel(new BorderLayout(0, 0));
        root.setBorder(BorderFactory.createEmptyBorder(18, 20, 16, 20));
        root.setBackground(p.background);

        // Header.
        JPanel header = new JPanel();
        header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));
        header.setOpaque(false);

        JLabel heading = new JLabel("Choose the symbols this machine may read and write");
        heading.setFont(Theme.ui(Font.BOLD, 14));
        heading.setForeground(p.text);
        heading.setAlignmentX(Component.LEFT_ALIGNMENT);

        m_summary = new JLabel(" ");
        m_summary.setFont(Theme.ui(Font.PLAIN, 12));
        m_summary.setForeground(p.textMuted);
        m_summary.setAlignmentX(Component.LEFT_ALIGNMENT);
        m_summary.setBorder(BorderFactory.createEmptyBorder(3, 0, 14, 0));

        header.add(heading);
        header.add(m_summary);
        root.add(header, BorderLayout.NORTH);

        // Body: letters on the left, digits and the blank on the right.
        JPanel body = new JPanel(new BorderLayout(22, 0));
        body.setOpaque(false);

        JPanel letters = section("Letters");
        JPanel letterGrid = grid(LETTER_COLUMNS);
        for (char c = 'A'; c <= 'Z'; c++)
        {
            SymbolToggle t = new SymbolToggle(c);
            letterGrid.add(t);
            m_letters.add(t);
        }
        letters.add(letterGrid);
        body.add(letters, BorderLayout.WEST);

        JPanel right = new JPanel();
        right.setLayout(new BoxLayout(right, BoxLayout.Y_AXIS));
        right.setOpaque(false);

        JPanel digits = section("Digits");
        JPanel digitGrid = grid(DIGIT_COLUMNS);
        for (char c = '0'; c <= '9'; c++)
        {
            SymbolToggle t = new SymbolToggle(c);
            digitGrid.add(t);
            m_digits.add(t);
        }
        digits.add(digitGrid);
        digits.setAlignmentX(Component.LEFT_ALIGNMENT);
        right.add(digits);

        JPanel blank = section("Blank");
        m_blank = new SymbolToggle(Tape.BLANK_SYMBOL);
        m_blank.setToolTipText("The blank symbol, written to empty tape cells");
        JPanel blankRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        blankRow.setOpaque(false);
        blankRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        blankRow.add(m_blank);
        JLabel blankHint = new JLabel("empty cell");
        blankHint.setFont(Theme.ui(Font.PLAIN, 12));
        blankHint.setForeground(p.textMuted);
        blankRow.add(blankHint);
        blank.add(blankRow);
        blank.setAlignmentX(Component.LEFT_ALIGNMENT);
        right.add(Box.createVerticalStrut(4));
        right.add(blank);

        JPanel presets = section("Presets");
        JPanel presetRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        presetRow.setOpaque(false);
        presetRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        presetRow.add(preset("Unary", "1"));
        presetRow.add(preset("Binary", "01"));
        presetRow.add(preset("Decimal", "0123456789"));
        presetRow.add(preset("Clear", ""));
        presets.add(presetRow);
        presets.setAlignmentX(Component.LEFT_ALIGNMENT);
        right.add(Box.createVerticalStrut(4));
        right.add(presets);
        right.add(Box.createVerticalGlue());

        body.add(right, BorderLayout.CENTER);
        root.add(body, BorderLayout.CENTER);

        // Footer.
        JPanel footer = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        footer.setOpaque(false);
        footer.setBorder(BorderFactory.createEmptyBorder(18, 0, 0, 0));

        JButton cancel = new JButton("Cancel");
        cancel.addActionListener(new ActionListener()
        {
            public void actionPerformed(ActionEvent e)
            {
                setVisible(false);
            }
        });

        JButton apply = new JButton("Apply");
        apply.addActionListener(new ActionListener()
        {
            public void actionPerformed(ActionEvent e)
            {
                applyAlphabet();
            }
        });

        footer.add(cancel);
        footer.add(apply);
        root.add(footer, BorderLayout.SOUTH);

        setContentPane(root);
        getRootPane().setDefaultButton(apply);

        // Escape closes the dialog, which users expect of any modal.
        getRootPane().registerKeyboardAction(new ActionListener()
        {
            public void actionPerformed(ActionEvent e)
            {
                setVisible(false);
            }
        }, KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);

        // Typing a letter or digit toggles it, preserving the shortcut the old frame offered.
        for (SymbolToggle t : all())
        {
            final SymbolToggle target = t;
            char c = t.getSymbol();
            if (!Character.isLetterOrDigit(c))
            {
                continue;
            }
            ActionListener toggle = new ActionListener()
            {
                public void actionPerformed(ActionEvent e)
                {
                    target.setSelected(!target.isSelected());
                    updateSummary();
                }
            };
            getRootPane().registerKeyboardAction(toggle,
                    KeyStroke.getKeyStroke(Character.toUpperCase(c), 0), JComponent.WHEN_IN_FOCUSED_WINDOW);
            getRootPane().registerKeyboardAction(toggle,
                    KeyStroke.getKeyStroke(Character.toLowerCase(c), 0), JComponent.WHEN_IN_FOCUSED_WINDOW);
        }

        pack();
    }

    /**
     * Build a titled section container.
     * @param title The section title.
     * @return The container, ready to have content added to it.
     */
    private JPanel section(String title)
    {
        JPanel outer = new JPanel();
        outer.setLayout(new BoxLayout(outer, BoxLayout.Y_AXIS));
        outer.setOpaque(false);

        JLabel label = new JLabel(title.toUpperCase());
        label.setFont(Theme.ui(Font.BOLD, 10));
        label.setForeground(Theme.palette().textMuted);
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        label.setBorder(BorderFactory.createEmptyBorder(0, 1, 6, 0));
        outer.add(label);

        return outer;
    }

    /**
     * Build a grid to hold symbol toggles.
     * @param columns The number of columns in the grid.
     * @return The grid.
     */
    private JPanel grid(int columns)
    {
        JPanel g = new JPanel(new GridLayout(0, columns, 4, 4));
        g.setOpaque(false);
        g.setAlignmentX(Component.LEFT_ALIGNMENT);
        return g;
    }

    /**
     * Build a preset button which selects exactly the given digits, plus the blank symbol.
     * @param name The label of the preset.
     * @param digits The digits the preset selects.
     * @return The button.
     */
    private JButton preset(final String name, final String digits)
    {
        JButton b = new JButton(name);
        b.setFont(Theme.ui(Font.PLAIN, 12));
        b.addActionListener(new ActionListener()
        {
            public void actionPerformed(ActionEvent e)
            {
                for (SymbolToggle t : m_letters)
                {
                    t.setSelected(false);
                }
                for (SymbolToggle t : m_digits)
                {
                    t.setSelected(digits.indexOf(t.getSymbol()) >= 0);
                }
                m_blank.setSelected(!digits.isEmpty());
                updateSummary();
            }
        });
        return b;
    }

    /**
     * Get every symbol toggle in the dialog.
     * @return All symbol toggles.
     */
    private ArrayList<SymbolToggle> all()
    {
        ArrayList<SymbolToggle> result = new ArrayList<SymbolToggle>();
        result.addAll(m_letters);
        result.addAll(m_digits);
        result.add(m_blank);
        return result;
    }

    /**
     * Update the running count of selected symbols.
     */
    private void updateSummary()
    {
        int n = 0;
        for (SymbolToggle t : all())
        {
            if (t.isSelected())
            {
                n++;
            }
        }
        m_summary.setText(n == 0
                ? "No symbols selected — the machine will not be able to read anything"
                : n + (n == 1? " symbol" : " symbols") + " selected");
        m_summary.setForeground(n == 0? Theme.palette().warning : Theme.palette().textMuted);
        repaint();
    }

    /**
     * Show the dialog for the given panel's machine.
     * @param panel The current graphics panel.
     */
    public void showFor(MachineGraphicsPanel panel)
    {
        m_panel = panel;
        synchronizeToAlphabet();
        updateSummary();
        setLocationRelativeTo(getOwner());
        setVisible(true);
    }

    /**
     * Build an alphabet from the current selection and apply it to the machine, prompting about
     * transitions which would be left referring to symbols outside the new alphabet.
     */
    private void applyAlphabet()
    {
        // Set up an alphabet object corresponding to these options
        Alphabet newAlph = new Alphabet();
        for (SymbolToggle t : m_digits)
        {
            newAlph.setSymbol(t.getSymbol(), t.isSelected());
        }
        for (SymbolToggle t : m_letters)
        {
            newAlph.setSymbol(t.getSymbol(), t.isSelected());
        }
        newAlph.setSymbol(Tape.BLANK_SYMBOL, m_blank.isSelected());

        // Take a deep copy of the old alphabet
        Alphabet oldAlphabet = (Alphabet)m_panel.getSimulator().getMachine().getAlphabet().clone();

        // Can we copy the new alphabet across without issue?
        if (m_panel.getSimulator().getMachine().isConsistentWithAlphabet(newAlph))
        {
            m_panel.doCommand(new ConfigureAlphabetCommand(m_panel, oldAlphabet, newAlph));
            m_panel.setModifiedSinceSave(true);
            setVisible(false);
            return;
        }

        int choice = JOptionPane.showConfirmDialog(this,
                "Some transitions in this machine use symbols which are not in the new alphabet.\n" +
                "Delete those transitions?", "Configure Alphabet",
                JOptionPane.YES_NO_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE);

        switch (choice)
        {
            case JOptionPane.YES_OPTION:
                // Get the set of inconsistent transitions
                ArrayList<Transition> purge =
                    m_panel.getSimulator().getMachine().getInconsistentTransitions(newAlph);

                // Change the alphabet, and remove inconsistent transitions
                m_panel.doCommand(new JoinCommand(
                        new RemoveInconsistentTransitionsCommand(m_panel, purge),
                        new ConfigureAlphabetCommand(m_panel, oldAlphabet, newAlph)));

                m_panel.setModifiedSinceSave(true);
                // This causes a repaint also
                m_panel.deselectSymbol();
                setVisible(false);
                break;

            case JOptionPane.NO_OPTION:
                // Do not reconfigure the alphabet, but close the dialog
                setVisible(false);
                break;

            default:
                // Leave the dialog open
                break;
        }
    }

    /**
     * Given the underlying alphabet, set all relevant toggles to match the alphabet.
     */
    private void synchronizeToAlphabet()
    {
        Alphabet a = m_panel.getAlphabet();
        for (SymbolToggle t : all())
        {
            t.setSelected(a.containsSymbol(t.getSymbol()));
        }
    }

    /**
     * A square toggle representing membership of a single symbol in the alphabet.
     */
    private class SymbolToggle extends JToggleButton
    {
        /**
         * Creates a new instance of SymbolToggle.
         * @param symbol The symbol this toggle represents.
         */
        SymbolToggle(char symbol)
        {
            m_symbol = symbol;
            setFocusable(false);
            setOpaque(false);
            setContentAreaFilled(false);
            setBorderPainted(false);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setPreferredSize(new Dimension(TOGGLE_SIZE, TOGGLE_SIZE));
            addActionListener(new ActionListener()
            {
                public void actionPerformed(ActionEvent e)
                {
                    updateSummary();
                }
            });
        }

        /**
         * Get the symbol this toggle represents.
         * @return The symbol.
         */
        char getSymbol()
        {
            return m_symbol;
        }

        /**
         * Render the toggle.
         * @param g The graphics object to render onto.
         */
        protected void paintComponent(Graphics g)
        {
            Graphics2D g2d = Theme.prepare(g.create());
            Theme.Palette p = Theme.palette();
            boolean on = isSelected();
            boolean hover = getModel().isRollover();

            Shape box = Theme.round(0.5, 0.5, getWidth() - 1, getHeight() - 1, 7);
            g2d.setColor(on? p.accent : (hover? p.surfaceHover : p.surface));
            g2d.fill(box);
            g2d.setColor(on? p.accent : p.border);
            g2d.setStroke(new BasicStroke(1f));
            g2d.draw(box);

            g2d.setColor(on? p.onAccent : p.text);
            g2d.setFont(Theme.mono(Font.BOLD, 14));
            Theme.drawCentered(g2d, String.valueOf(m_symbol), getWidth() / 2.0, getHeight() / 2.0);
            g2d.dispose();
        }

        /**
         * The symbol this toggle represents.
         */
        private final char m_symbol;
    }

    /**
     * All toggles which represent letters.
     */
    private ArrayList<SymbolToggle> m_letters = new ArrayList<SymbolToggle>();

    /**
     * All toggles which represent digits.
     */
    private ArrayList<SymbolToggle> m_digits = new ArrayList<SymbolToggle>();

    /**
     * The toggle which represents the blank character.
     */
    private SymbolToggle m_blank;

    /**
     * Running count of the selected symbols.
     */
    private JLabel m_summary;

    /**
     * The current graphics panel.
     */
    private MachineGraphicsPanel m_panel;
}
