package chess.ui;

import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;

import javax.swing.BorderFactory;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import javax.swing.event.ChangeListener;

import chess.engine.EngineSettings;
import chess.engine.EngineSettings.EvaluationWeights;
import chess.engine.EngineSettings.Preset;

/** Converts friendly controls into one validated, immutable settings object. */
public final class SettingsPanel extends JPanel {
    private static final long serialVersionUID = 1L;
    private final JComboBox<String> preset = new JComboBox<>(
            new String[] {"First steps", "Casual", "Challenge", "Strongest", "Custom"});
    private final JSpinner depth = integerSpinner(7, 1, 12, 1, "searchDepth");
    private final JSpinner seconds = new JSpinner(new SpinnerNumberModel(2.5, 0.1, 30.0, 0.1));
    private final JSpinner quiescence = integerSpinner(6, 0, 12, 1, "quiescenceDepth");
    private final JSpinner randomness = integerSpinner(0, 0, 300, 10, "randomness");
    private final JCheckBox table = new JCheckBox("Remember searched positions");
    private final JCheckBox ordering = new JCheckBox("Try promising moves first");
    private final JCheckBox verbose = new JCheckBox("Log every root candidate (slower)");
    private final JSpinner material = integerSpinner(100, 0, 200, 10, "materialWeight");
    private final JSpinner placement = integerSpinner(100, 0, 200, 10, "placementWeight");
    private final JSpinner mobility = integerSpinner(3, 0, 10, 1, "mobilityWeight");
    private final JSpinner pawns = integerSpinner(100, 0, 200, 10, "pawnWeight");
    private final JSpinner king = integerSpinner(100, 0, 200, 10, "kingWeight");
    private Runnable onChange = () -> { };
    private boolean updating;
    private int row;

    public SettingsPanel() {
        super(new GridBagLayout());
        setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));
        preset.setName("difficultyPreset");
        seconds.setName("thinkingSeconds");
        seconds.setEditor(new JSpinner.NumberEditor(seconds, "0.0"));
        table.setName("transpositionTable");
        ordering.setName("moveOrdering");
        verbose.setName("verboseThinking");

        addRow("Difficulty", preset, "Presets change search settings; they are not measured Elo ratings.");
        addSection("Search: how hard should it think?");
        addRow("Maximum depth (plies)", depth, "A ply is one player's move. The time budget may stop search earlier.");
        addRow("Time per move (seconds)", seconds, "A soft wall-clock budget, not a chess clock. More time usually means deeper search.");
        addRow("Quiescence depth", quiescence, "Extra capture/promotion plies at the horizon. Zero disables these, but check evasions remain mandatory.");
        addRow("Move randomness (cp)", randomness, "A stable random bonus between minus and plus this amount is added to each root move for selection only.");
        addWide(table, "The transposition table caches bounds and moves. Turn it off to compare search.");
        addWide(ordering, "Captures, the previous best move, killers, and history help alpha-beta prune sooner.");
        addWide(verbose, "Exact scores for each root candidate make a useful lesson, but need wider search windows and extra output.");

        addSection("Evaluation: what should it care about?");
        addRow("Material (%)", material, "Value of pieces. At 0%, the engine stops caring about material!");
        addRow("Piece placement (%)", placement, "Central knights, advanced rooks, the bishop pair, and game-phase-dependent king placement.");
        addRow("Mobility (cp / square)", mobility, "Reward available knight, bishop, rook, and queen destinations. Pins are ignored in this estimate.");
        addRow("Pawn structure (%)", pawns, "Passed pawns are rewarded; doubled and isolated pawns are penalized.");
        addRow("King safety (%)", king, "Pawn shields, open files, and attacked squares around the king matter most before the endgame.");
        JLabel note = new JLabel("<html><body style='width:280px'>"
                + "100 cp is about one pawn. Positive displayed scores favor White.<br><br>"
                + "Change one setting at a time, analyze the same FEN, and compare completed depth, nodes, score, and PV."
                + "</body></html>");
        addWide(note, null);
        GridBagConstraints filler = constraints(0, row++);
        filler.weighty = 1;
        add(new JPanel(), filler);

        preset.addActionListener(event -> {
            if (!updating && preset.getSelectedIndex() < Preset.values().length) {
                apply(Preset.values()[preset.getSelectedIndex()].settings());
                onChange.run();
            }
        });
        ChangeListener changed = event -> settingsChanged();
        for (JSpinner spinner : new JSpinner[] {
                depth, seconds, quiescence, randomness, material, placement, mobility, pawns, king}) {
            spinner.addChangeListener(changed);
        }
        table.addActionListener(event -> settingsChanged());
        ordering.addActionListener(event -> settingsChanged());
        verbose.addActionListener(event -> settingsChanged());
        updating = true;
        preset.setSelectedIndex(Preset.CHALLENGE.ordinal());
        updating = false;
        apply(EngineSettings.defaults());
    }

    public void setOnChange(Runnable onChange) {
        this.onChange = onChange;
    }

    public EngineSettings settings() {
        return new EngineSettings(value(depth), (int) Math.round(((Number) seconds.getValue()).doubleValue() * 1_000),
                value(quiescence), value(randomness), table.isSelected(), ordering.isSelected(), verbose.isSelected(),
                new EvaluationWeights(value(material), value(placement), value(mobility), value(pawns), value(king)));
    }

    private void settingsChanged() {
        if (!updating) {
            updating = true;
            preset.setSelectedIndex(Preset.values().length);
            updating = false;
            onChange.run();
        }
    }

    private void apply(EngineSettings settings) {
        updating = true;
        depth.setValue(settings.maxDepth());
        seconds.setValue(settings.timeLimitMillis() / 1_000.0);
        quiescence.setValue(settings.quiescenceDepth());
        randomness.setValue(settings.randomnessCp());
        table.setSelected(settings.transpositionTable());
        ordering.setSelected(settings.moveOrdering());
        verbose.setSelected(settings.verboseThinking());
        EvaluationWeights weights = settings.weights();
        material.setValue(weights.materialPercent());
        placement.setValue(weights.placementPercent());
        mobility.setValue(weights.mobilityCp());
        pawns.setValue(weights.pawnStructurePercent());
        king.setValue(weights.kingSafetyPercent());
        updating = false;
    }

    private static int value(JSpinner spinner) {
        return ((Number) spinner.getValue()).intValue();
    }

    private static JSpinner integerSpinner(int value, int min, int max, int step, String name) {
        JSpinner spinner = new JSpinner(new SpinnerNumberModel(value, min, max, step));
        spinner.setName(name);
        return spinner;
    }

    private GridBagConstraints constraints(int column, int row) {
        GridBagConstraints constraints = new GridBagConstraints();
        constraints.gridx = column;
        constraints.gridy = row;
        constraints.fill = GridBagConstraints.HORIZONTAL;
        constraints.anchor = GridBagConstraints.NORTHWEST;
        constraints.insets = new Insets(4, 2, 4, 7);
        constraints.weightx = column == 0 ? 1 : 0.3;
        return constraints;
    }

    private void addRow(String title, JComponent control, String tip) {
        JLabel label = new JLabel(title);
        label.setLabelFor(control);
        label.setToolTipText(tip);
        control.setToolTipText(tip);
        control.getAccessibleContext().setAccessibleName(title);
        add(label, constraints(0, row));
        add(control, constraints(1, row++));
    }

    private void addSection(String text) {
        JLabel title = new JLabel(text);
        title.setFont(title.getFont().deriveFont(Font.BOLD));
        title.setBorder(BorderFactory.createEmptyBorder(12, 0, 3, 0));
        addWide(title, null);
    }

    private void addWide(JComponent component, String tip) {
        component.setToolTipText(tip);
        GridBagConstraints constraints = constraints(0, row++);
        constraints.gridwidth = 2;
        add(component, constraints);
    }
}
