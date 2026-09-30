package chess.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.awt.event.ActionEvent;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JEditorPane;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.text.BadLocationException;

import chess.engine.Engine;
import chess.engine.EngineSettings;
import chess.engine.Evaluator;
import chess.engine.SearchInfo;
import chess.engine.SearchResult;
import chess.model.Game;
import chess.model.Move;
import chess.model.Piece;
import chess.model.Position;

/**
 * The application controller. All live game state and Swing widgets belong to
 * the event dispatch thread (EDT). Only an isolated position copy crosses into
 * the engine worker. See startSearch() for the main concurrency lesson.
 */
public final class ChessFrame extends JFrame {
    private static final long serialVersionUID = 1L;
    private static final Logger LOG = Logger.getLogger(ChessFrame.class.getName());
    private Game game = new Game();
    private final BoardPanel board = new BoardPanel(this::requestBoardMove);
    private final SettingsPanel settingsPanel = new SettingsPanel();
    private final JComboBox<String> humanSide = new JComboBox<>(new String[] {"White", "Black", "Both (local game)"});
    private final JCheckBox paused = new JCheckBox("Pause engine / explore both sides");
    private final JLabel status = new JLabel();
    private final JLabel staticScore = new JLabel();
    private final JLabel thinkingSummary = new JLabel("Ready to explore");
    private final JTextArea thinking = new JTextArea();
    private final JTextArea moveHistory = new JTextArea();
    private final JTextField moveInput = new JTextField(9);
    private final JTextField fenOutput = new JTextField();
    private final JProgressBar progress = new JProgressBar();
    private final JButton newGameButton = button("New game", "newGame", "Start again (Ctrl+N).", this::newGame);
    private final JButton undoButton = button("Undo turn", "undoTurn", "Undo your move and the engine reply (Ctrl+Z).", this::undoTurn);
    private final JButton flipButton = button("Flip [F3]", "flipBoard", "Rotate the view, without changing the game.", () -> board.setFlipped(!board.isFlipped()));
    private final JButton analyzeButton = button("Analyze [F2]", "analyze", "Think about this position and show a hint, without playing it.", () -> startSearch(false));
    private final JButton finishButton = button("Move now [F4]", "finishSearch", "Use the last fully completed depth.", this::finishSearch);
    private final JButton loadButton = button("Load FEN", "loadFen", "Load a six-field position (Ctrl+L). The engine will pause.", this::loadFen);
    private SwingWorker<SearchResult, SearchInfo> worker;
    private AtomicBoolean stopSignal;
    private boolean workerPlaysMove;
    private boolean closing;
    private long searchRevision;

    public ChessFrame() {
        super("LukeFish - Java Chess Classroom");
        if (!SwingUtilities.isEventDispatchThread()) {
            throw new IllegalStateException("Create ChessFrame on the Swing event dispatch thread.");
        }
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        buildLayout();
        wireControls();
        refreshView();
        appendLog("Welcome to LukeFish. Scores favor White; 100 centipawns (cp) is about one pawn."
                + "\nPV = principal variation: the engine's predicted best line."
                + "\nChoose First steps for a gentler opponent, or open the Guide tab.");
        Rectangle screen = GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
        setMinimumSize(new Dimension(Math.min(980, screen.width), Math.min(700, screen.height)));
        setSize(Math.min(1260, screen.width), Math.min(900, screen.height));
        setLocationRelativeTo(null);
    }

    private void buildLayout() {
        JPanel content = new JPanel(new BorderLayout(10, 10));
        content.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        setContentPane(content);

        JPanel header = new JPanel();
        header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));
        JPanel titleRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        JLabel title = new JLabel("LukeFish");
        title.setForeground(new Color(27, 86, 92));
        title.setFont(title.getFont().deriveFont(Font.BOLD, 27f));
        titleRow.add(title);
        titleRow.add(new JLabel("Play chess. Change an idea. Watch the engine think."));
        header.add(titleRow);
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 6));
        for (JButton button : new JButton[] {newGameButton, undoButton, flipButton, analyzeButton, finishButton}) {
            actions.add(button);
        }
        actions.add(button("Help [F1]", "help", "Mouse, keyboard, and engine lab instructions.", this::showHelp));
        header.add(actions);
        JPanel players = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        JLabel playAs = new JLabel("You play:");
        playAs.setLabelFor(humanSide);
        players.add(playAs);
        players.add(humanSide);
        players.add(paused);
        header.add(players);
        content.add(header, BorderLayout.NORTH);

        JPanel boardArea = new JPanel(new BorderLayout());
        boardArea.add(board, BorderLayout.CENTER);
        JPanel boardFooter = new JPanel();
        boardFooter.setLayout(new BoxLayout(boardFooter, BoxLayout.Y_AXIS));
        boardFooter.setBorder(BorderFactory.createEmptyBorder(6, 8, 0, 8));
        status.setFont(status.getFont().deriveFont(Font.BOLD, 16f));
        status.setName("gameStatus");
        status.setAlignmentX(Component.LEFT_ALIGNMENT);
        status.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));
        staticScore.setName("staticScore");
        staticScore.setAlignmentX(Component.LEFT_ALIGNMENT);
        staticScore.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));
        boardFooter.add(status);
        boardFooter.add(staticScore);
        JPanel entry = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 6));
        entry.setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel moveLabel = new JLabel("Keyboard move:");
        moveLabel.setLabelFor(moveInput);
        entry.add(moveLabel);
        entry.add(moveInput);
        entry.add(new JLabel("e2e4 / e7e8q, then Enter"));
        boardFooter.add(entry);
        JLabel instructions = new JLabel("Click a piece, then a dot. On the board: arrows + Enter; Esc clears.");
        instructions.setName("boardInstructions");
        instructions.setFont(instructions.getFont().deriveFont(12f));
        instructions.setAlignmentX(Component.LEFT_ALIGNMENT);
        instructions.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));
        boardFooter.add(instructions);
        boardArea.add(boardFooter, BorderLayout.SOUTH);

        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Engine lab", new JScrollPane(settingsPanel));
        configureText(moveHistory, "moveHistory", false);
        moveHistory.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 15));
        tabs.addTab("Moves", new JScrollPane(moveHistory));
        tabs.addTab("Guide", new JScrollPane(guide()));

        JPanel thinkingPanel = new JPanel(new BorderLayout(4, 4));
        JPanel thinkingHeader = new JPanel(new BorderLayout(4, 4));
        thinkingHeader.add(thinkingSummary, BorderLayout.CENTER);
        thinkingHeader.add(button("Clear", "clearThinking", "Clear this panel; Eclipse's Console keeps its output.",
                () -> thinking.setText("")), BorderLayout.EAST);
        progress.setPreferredSize(new Dimension(100, 5));
        thinkingHeader.add(progress, BorderLayout.SOUTH);
        thinkingPanel.add(thinkingHeader, BorderLayout.NORTH);
        configureText(thinking, "thinkingOutput", true);
        thinking.setBackground(new Color(25, 42, 48));
        thinking.setForeground(new Color(221, 236, 227));
        thinking.setCaretColor(Color.WHITE);
        thinkingPanel.add(new JScrollPane(thinking), BorderLayout.CENTER);
        thinkingPanel.setMinimumSize(new Dimension(350, 170));
        JSplitPane right = new JSplitPane(JSplitPane.VERTICAL_SPLIT, tabs, thinkingPanel);
        right.setResizeWeight(0.58);
        right.setDividerLocation(390);
        right.setMinimumSize(new Dimension(380, 400));

        JSplitPane main = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, boardArea, right);
        main.setResizeWeight(0.62);
        main.setDividerLocation(715);
        main.setBorder(null);
        content.add(main, BorderLayout.CENTER);

        JPanel fenBar = new JPanel(new BorderLayout(6, 0));
        JLabel fenLabel = new JLabel("FEN:");
        fenLabel.setLabelFor(fenOutput);
        fenBar.add(fenLabel, BorderLayout.WEST);
        fenOutput.setName("fenOutput");
        fenOutput.setEditable(false);
        fenOutput.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        fenOutput.setToolTipText("This describes the board, not the earlier move history. Copy it for a repeatable experiment.");
        fenBar.add(fenOutput, BorderLayout.CENTER);
        JPanel fenActions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
        fenActions.add(button("Copy FEN", "copyFen", "Copy this position to the clipboard.", this::copyFen));
        fenActions.add(loadButton);
        fenBar.add(fenActions, BorderLayout.EAST);
        content.add(fenBar, BorderLayout.SOUTH);
    }

    private void wireControls() {
        humanSide.setName("humanSide");
        paused.setName("pauseEngine");
        moveInput.setName("moveInput");
        moveInput.setToolTipText("Coordinate moves: e2e4, e1g1 for castling, a7a8n to promote to a knight.");
        moveInput.addActionListener(event -> requestTypedMove());
        humanSide.addActionListener(event -> {
            cancelSearch("Player control changed.");
            if (humanColor() != 0) {
                board.setFlipped(humanColor() == Piece.BLACK);
            }
            refreshView();
            maybeStartComputer();
        });
        paused.addActionListener(event -> {
            cancelSearch(paused.isSelected() ? "Engine paused." : "Engine resumed.");
            refreshView();
            maybeStartComputer();
        });
        settingsPanel.setOnChange(() -> {
            boolean wasAnalysis = worker != null && !workerPlaysMove;
            cancelSearch("Engine settings changed.");
            appendLog("Settings: " + settingsPanel.settings());
            refreshView();
            if (wasAnalysis && !game.outcome().isOver()) {
                startSearch(false);
            } else {
                maybeStartComputer();
            }
        });
        bind("control N", "new", newGameButton);
        bind("control Z", "undo", undoButton);
        bind("control L", "load", loadButton);
        bind("F2", "analyze", analyzeButton);
        bind("F3", "flip", flipButton);
        bind("F4", "finish", finishButton);
        getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke("F1"), "help");
        getRootPane().getActionMap().put("help", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent event) {
                showHelp();
            }
        });
    }

    private int humanColor() {
        return switch (humanSide.getSelectedIndex()) {
            case 0 -> Piece.WHITE;
            case 1 -> Piece.BLACK;
            default -> 0;
        };
    }

    private boolean computerTurn() {
        return humanColor() != 0 && humanColor() != game.position().sideToMove();
    }

    private boolean humanCanMove() {
        return !game.outcome().isOver() && (paused.isSelected() || !computerTurn());
    }

    private void requestBoardMove(int from, int to) {
        if (!humanCanMove()) {
            return;
        }
        List<Move> matches = game.position().legalMoves().stream()
                .filter(move -> move.from() == from && move.to() == to).toList();
        if (matches.isEmpty()) {
            showInputError("Choose one of the marked legal destinations.");
            return;
        }
        Move move = matches.get(0);
        if (matches.size() > 1) {
            String[] names = {"Queen", "Rook", "Bishop", "Knight"};
            int[] types = {Piece.QUEEN, Piece.ROOK, Piece.BISHOP, Piece.KNIGHT};
            int choice = JOptionPane.showOptionDialog(this, "Choose the pawn's new piece.",
                    "Promotion", JOptionPane.DEFAULT_OPTION, JOptionPane.QUESTION_MESSAGE,
                    null, names, names[0]);
            if (choice < 0) {
                return;
            }
            int type = types[choice];
            move = matches.stream().filter(candidate -> candidate.promotion() == type).findFirst().orElseThrow();
        }
        cancelSearch("A move replaced the analysis.");
        playMove(move);
    }

    private void requestTypedMove() {
        if (!humanCanMove()) {
            showInputError("It is the computer's turn. Pause the engine to explore both sides.");
            return;
        }
        try {
            Move move = game.position().requireLegalMove(moveInput.getText().trim());
            cancelSearch("A move replaced the analysis.");
            playMove(move);
            moveInput.setText("");
        } catch (IllegalArgumentException exception) {
            showInputError(exception.getMessage());
        }
    }

    private void playMove(Move move) {
        Game.PlayedMove played = game.play(move);
        appendLog(played.number() + (played.color() == Piece.WHITE ? ". " : "... ")
                + Piece.colorName(played.color()) + ": " + played.notation() + " [" + move.toUci() + "]"
                + "\n" + Evaluator.explain(game.position(), settingsPanel.settings().weights()));
        refreshView();
        if (game.outcome().isOver()) {
            appendLog(game.outcome().description());
        } else {
            maybeStartComputer();
        }
    }

    private void maybeStartComputer() {
        if (!closing && worker == null && !paused.isSelected() && !game.outcome().isOver() && computerTurn()) {
            startSearch(true);
        }
    }

    /**
     * Never search on the EDT: paint, clicks, and keypresses would all freeze.
     * publish/process safely transfer progress; done runs back on the EDT.
     */
    private void startSearch(boolean playResult) {
        if (closing || worker != null || game.outcome().isOver()) {
            return;
        }
        Position snapshot = game.position().copy();
        List<Long> history = game.positionKeys();
        EngineSettings settings = settingsPanel.settings();
        long revision = ++searchRevision;
        AtomicBoolean signal = new AtomicBoolean();
        stopSignal = signal;
        workerPlaysMove = playResult;
        board.setHint(null);
        appendLog("\n" + (playResult ? "Computer thinking" : "Analysis (no move will be played)")
                + " for " + Piece.colorName(snapshot.sideToMove())
                + " | depth <= " + settings.maxDepth() + " | budget " + settings.timeLimitMillis() + "ms"
                + (settings.randomnessCp() > 0 ? " | root noise +/-" + settings.randomnessCp() + "cp" : ""));
        thinkingSummary.setText("Thinking for " + Piece.colorName(snapshot.sideToMove()) + "...");
        worker = new SwingWorker<>() {
            @Override
            protected SearchResult doInBackground() {
                return new Engine().search(snapshot, settings, history, signal, info -> publish(info));
            }

            @Override
            protected void process(List<SearchInfo> messages) {
                if (revision != searchRevision || isCancelled() || worker != this) {
                    return;
                }
                for (SearchInfo info : messages) {
                    appendLog(info.format(snapshot.sideToMove()));
                    if (info.completedIteration()) {
                        thinkingSummary.setText("Depth " + info.depth() + " | White "
                                + SearchInfo.formatScore(info.score() * snapshot.sideToMove())
                                + " | " + String.format(Locale.ROOT, "%,d nodes", info.nodes()));
                    }
                }
            }

            @Override
            protected void done() {
                // Undo, new game, FEN import, and settings changes increment
                // the revision. A stale worker must NEVER play on a new board.
                if (revision != searchRevision) {
                    return;
                }
                worker = null;
                stopSignal = null;
                try {
                    SearchResult result = get(); // Already finished: this does not block the EDT.
                    if (game.position().key() != snapshot.key()) {
                        throw new IllegalStateException("The game changed without cancelling its search.");
                    }
                    thinkingSummary.setText("Finished depth " + result.completedDepth()
                            + " | " + result.elapsedMillis() + "ms");
                    if (result.bestMove() == null) {
                        throw new IllegalStateException("The engine returned no move for an active game.");
                    }
                    appendLog("Selected " + result.bestMove().toUci()
                            + (result.completedDepth() == 0
                                ? " (legal fallback; no depth completed)"
                                : " | White " + SearchInfo.formatScore(result.score() * snapshot.sideToMove())));
                    if (playResult) {
                        playMove(result.bestMove());
                    } else {
                        board.setHint(result.bestMove());
                        refreshView();
                    }
                } catch (CancellationException expected) {
                    appendLog("Search cancelled.");
                    refreshView();
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    reportSearchFailure(exception);
                } catch (ExecutionException exception) {
                    reportSearchFailure(exception.getCause());
                } catch (IllegalStateException exception) {
                    reportSearchFailure(exception);
                }
            }
        };
        refreshView();
        worker.execute();
    }

    private void finishSearch() {
        if (stopSignal != null) {
            // Unlike cancelSearch(), this preserves the worker's result so
            // "Move now" can play the best completed iteration.
            stopSignal.set(true);
            finishButton.setEnabled(false);
            thinkingSummary.setText("Finishing the current search...");
        }
    }

    private void cancelSearch(String reason) {
        searchRevision++;
        if (worker != null) {
            stopSignal.set(true);
            worker.cancel(true);
            worker = null;
            stopSignal = null;
            appendLog(reason);
            thinkingSummary.setText("Search cancelled");
        }
    }

    private void reportSearchFailure(Throwable exception) {
        paused.setSelected(true);
        LOG.log(Level.SEVERE, "Chess search failed", exception);
        appendLog("ERROR: " + exception + "\nThe engine is paused. Details are in the Eclipse Console.");
        refreshView();
        JOptionPane.showMessageDialog(this, "The search failed and the engine was paused.\n"
                + exception.getMessage() + "\nSee the Console for the stack trace.",
                "Engine error", JOptionPane.ERROR_MESSAGE);
    }

    private void newGame() {
        if (!confirmReplace()) {
            return;
        }
        cancelSearch("New game cancelled the search.");
        game = new Game();
        moveInput.setText("");
        board.setHint(null);
        appendLog("\n--- New game ---");
        refreshView();
        maybeStartComputer();
    }

    private boolean confirmReplace() {
        return !game.canUndo() || JOptionPane.showConfirmDialog(this,
                "Replace this game? Copy its FEN first if you want to keep the current position.",
                "Replace game", JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION;
    }

    private void undoTurn() {
        if (!game.canUndo()) {
            return;
        }
        cancelSearch("Undo cancelled the search.");
        do {
            game.undo();
        } while (game.canUndo() && !paused.isSelected() && computerTurn());
        if (computerTurn() && !paused.isSelected()) {
            // Undoing the computer's very first move has no preceding human
            // move to return to. Pause instead of instantly replaying it.
            paused.setSelected(true);
        }
        board.setHint(null);
        appendLog("Undo -> " + game.position().toFen());
        refreshView();
    }

    private void loadFen() {
        String input = JOptionPane.showInputDialog(this, "Paste a six-field FEN. Loading pauses the engine.",
                game.position().toFen());
        if (input == null) {
            return;
        }
        try {
            Game replacement = new Game(Position.fromFen(input));
            if (!confirmReplace()) {
                return;
            }
            cancelSearch("FEN import cancelled the search.");
            game = replacement;
            paused.setSelected(true);
            moveInput.setText("");
            board.setHint(null);
            appendLog("\nLoaded FEN (history starts here): " + game.position().toFen());
            refreshView();
        } catch (IllegalArgumentException exception) {
            showInputError(exception.getMessage());
        }
    }

    private void copyFen() {
        try {
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(
                    new StringSelection(game.position().toFen()), null);
            appendLog("Position copied as FEN.");
        } catch (IllegalStateException exception) {
            showInputError("The clipboard is busy. Select the FEN field and copy it manually.");
        }
    }

    private void refreshView() {
        List<Game.PlayedMove> moves = game.moves();
        Move previous = moves.isEmpty() ? null : moves.get(moves.size() - 1).move();
        board.setGameState(game.position(), game.position().legalMoves(), previous, humanCanMove());
        fenOutput.setText(game.position().toFen());
        String state = game.outcome().isOver() ? game.outcome().description()
                : Piece.colorName(game.position().sideToMove()) + " to move"
                    + (game.position().isInCheck() ? " - CHECK" : "")
                    + (paused.isSelected() ? " (engine paused)" : computerTurn() ? " (computer)" : " (you)");
        status.setText(state);
        int score = Evaluator.explain(game.position(), settingsPanel.settings().weights()).total();
        staticScore.setText(String.format(Locale.ROOT, "Static evaluation: %+.2f pawns | positive favors White", score / 100.0));
        moveInput.setEnabled(humanCanMove());
        undoButton.setEnabled(game.canUndo());
        analyzeButton.setEnabled(worker == null && !game.outcome().isOver());
        finishButton.setEnabled(worker != null && stopSignal != null && !stopSignal.get());
        finishButton.setText(worker != null && !workerPlaysMove ? "Finish analysis [F4]" : "Move now [F4]");
        progress.setIndeterminate(worker != null);
        renderHistory(moves);
    }

    private void renderHistory(List<Game.PlayedMove> moves) {
        StringBuilder text = new StringBuilder();
        for (int index = 0; index < moves.size(); index++) {
            Game.PlayedMove move = moves.get(index);
            if (move.color() == Piece.WHITE) {
                text.append(String.format(Locale.ROOT, "%3d. %-9s", move.number(), move.notation()));
                if (index + 1 < moves.size() && moves.get(index + 1).number() == move.number()) {
                    text.append(moves.get(++index).notation());
                }
            } else {
                text.append(String.format(Locale.ROOT, "%3d... %s", move.number(), move.notation()));
            }
            text.append('\n');
        }
        if (game.outcome().isOver()) {
            text.append('\n').append(game.outcome().description());
        }
        moveHistory.setText(text.toString());
        moveHistory.setCaretPosition(moveHistory.getDocument().getLength());
    }

    private void appendLog(String message) {
        LOG.info(message);
        thinking.append(message + "\n");
        if (thinking.getDocument().getLength() > 80_000) {
            try {
                thinking.getDocument().remove(0, 20_000);
            } catch (BadLocationException exception) {
                throw new IllegalStateException("Could not trim the thinking log.", exception);
            }
        }
        thinking.setCaretPosition(thinking.getDocument().getLength());
    }

    private void showInputError(String message) {
        appendLog("Input: " + message);
        JOptionPane.showMessageDialog(this, message, "Check your input", JOptionPane.WARNING_MESSAGE);
    }

    private void showHelp() {
        JScrollPane help = new JScrollPane(guide());
        help.setPreferredSize(new Dimension(560, 490));
        JOptionPane.showMessageDialog(this, help, "LukeFish quick start", JOptionPane.INFORMATION_MESSAGE);
    }

    private static JEditorPane guide() {
        JEditorPane guide = new JEditorPane("text/html",
                "<html><body style='font-family:sans-serif;font-size:11pt;margin:12px'>"
                + "<h2>Your chess classroom</h2>"
                + "<p><b>Play:</b> click a piece, then a legal dot. Rings mark captures; red marks check. "
                + "Promotion offers queen, rook, bishop, or knight.</p>"
                + "<p><b>Keyboard:</b> focus the board with Tab. Arrows move the blue cursor; Enter or Space selects; "
                + "Escape clears selection. Or type <b>e2e4</b> below the board and press Enter. "
                + "Use <b>e1g1</b> to castle and <b>a7a8n</b> to underpromote.</p>"
                + "<p><b>Shortcuts:</b> Ctrl+N new game, Ctrl+Z undo turn, Ctrl+L load FEN, "
                + "F2 analyze, F3 flip, F4 finish thinking, F1 this guide.</p>"
                + "<h3>Make it easier or harder</h3>"
                + "<p>Start with <b>First steps</b>. More depth and time usually play better. "
                + "Quiescence follows capture exchanges beyond the normal horizon. "
                + "Randomness deliberately chooses less consistent moves; zero is deterministic at a fixed completed depth.</p>"
                + "<p>The table and ordering switches change efficiency, not chess rules. "
                + "Evaluation weights change the engine's priorities. Try disabling material!</p>"
                + "<h3>Read its thoughts</h3>"
                + "<p>Positive scores favor White; negative favor Black. <b>+1.00</b> is roughly a pawn, "
                + "not a winning probability. <b>+M3</b> means a predicted White mate in three moves. "
                + "<b>Depth</b> counts plies (one player's move); <b>nodes</b> count explored positions. "
                + "<b>PV</b> is the predicted best line, not a promise. Cached lines may be shorter than the depth.</p>"
                + "<p><b>Analyze</b> shows a blue hint without moving. <b>Move now</b> uses the last completed depth. "
                + "<b>Pause</b> cancels thinking and lets you play either side.</p>"
                + "<h3>Repeat an experiment</h3>"
                + "<p>Copy FEN, change one setting, and analyze again. Loading FEN clears history and pauses the engine. "
                + "FEN does not preserve earlier repetitions. The app automatically claims threefold and 50-move draws; "
                + "it recognizes common insufficient-material draws, not every possible blocked dead position.</p>"
                + "<p>Open <b>docs/LEARNING_GUIDE.md</b> for Java/JavaScript comparisons, breakpoints, "
                + "exercises, and a suggested lesson sequence.</p></body></html>");
        guide.setEditable(false);
        guide.setCaretPosition(0);
        return guide;
    }

    private static void configureText(JTextArea area, String name, boolean wrap) {
        area.setName(name);
        area.setEditable(false);
        area.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
        area.setMargin(new Insets(8, 8, 8, 8));
        area.setLineWrap(wrap);
        area.setWrapStyleWord(wrap);
    }

    private static JButton button(String text, String name, String tip, Runnable action) {
        JButton button = new JButton(text);
        button.setName(name);
        button.setToolTipText(tip);
        button.addActionListener(event -> action.run());
        return button;
    }

    private void bind(String key, String name, JButton button) {
        getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(key), name);
        getRootPane().getActionMap().put(name, new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent event) {
                if (button.isEnabled()) {
                    button.doClick();
                }
            }
        });
    }

    @Override
    public void dispose() {
        closing = true;
        cancelSearch("Window closed.");
        super.dispose();
    }
}
