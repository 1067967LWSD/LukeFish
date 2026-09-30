package chess;

import java.awt.Component;
import java.awt.Container;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;
import java.awt.Window;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.imageio.ImageIO;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JSpinner;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

import chess.model.Piece;
import chess.model.Position;
import chess.model.Square;
import chess.ui.BoardPanel;
import chess.ui.ChessFrame;

/**
 * Optional desktop integration check. It drives actual Swing event handlers
 * and dialogs, rather than requiring external GUI automation dependencies.
 * Run with a PNG filename argument to save the rendered application.
 */
public final class UiSmokeTest {
    private static final ConcurrentLinkedQueue<Throwable> FAILURES = new ConcurrentLinkedQueue<>();
    private static ChessFrame frame;

    private UiSmokeTest() {
    }

    public static void main(String[] args) throws Exception {
        if (GraphicsEnvironment.isHeadless()) {
            throw new IllegalStateException("The UI smoke check requires a desktop display.");
        }
        Thread.setDefaultUncaughtExceptionHandler((thread, error) -> {
            error.printStackTrace();
            FAILURES.add(error);
        });
        Logger.getLogger(ChessFrame.class.getName()).setLevel(Level.WARNING);
        Main.configureAppearance();
        try {
            onEdt(() -> {
                frame = new ChessFrame();
                frame.setVisible(true);
                check(component("boardInstructions", JLabel.class).getWidth()
                        >= component("boardInstructions", JLabel.class).getPreferredSize().width,
                        "Board instructions have enough horizontal space");
                component("searchDepth", JSpinner.class).setValue(12);
                component("thinkingSeconds", JSpinner.class).setValue(5.0);
                clickSquare("e2");
                clickSquare("e4");
                check(fen().contains(" b "), "Mouse plays a legal move");
            });

            long start = System.nanoTime();
            onEdt(() -> {
                component("flipBoard", JButton.class).doClick();
                component("flipBoard", JButton.class).doClick();
                component("undoTurn", JButton.class).doClick();
                check(fen().equals(Position.START_FEN), "Undo during thinking restores the board");
            });
            check((System.nanoTime() - start) / 1_000_000 < 2_000, "EDT stays responsive during a five-second search");
            Thread.sleep(350);
            onEdt(() -> check(fen().equals(Position.START_FEN), "Cancelled worker cannot play a stale move"));

            onEdt(() -> typeMove("e2e4"));
            await(() -> component("thinkingOutput", JTextArea.class).getText().contains("completed depth"), 5_000);
            onEdt(() -> component("finishSearch", JButton.class).doClick());
            await(() -> fen().contains(" w ") && !fen().equals(Position.START_FEN), 5_000);
            onEdt(() -> {
                check(component("thinkingOutput", JTextArea.class).getText().contains("n/s"), "Live node and score output");
                check(component("moveHistory", JTextArea.class).getText().contains("1."), "Move history is populated");
                component("undoTurn", JButton.class).doClick();
                check(fen().equals(Position.START_FEN), "Undo a full human/computer turn");
                component("humanSide", JComboBox.class).setSelectedIndex(2);
                typeMove("e2e4");
                typeMove("e7e5");
                clickSquare("g1");
                clickSquare("f3");
                check(Position.fromFen(fen()).pieceAt(Square.parse("f3")) == Piece.KNIGHT, "Local two-player mouse move");
                component("analyze", JButton.class).doClick();
                component("materialWeight", JSpinner.class).setValue(80);
                check(component("finishSearch", JButton.class).isEnabled(), "Changing settings restarts analysis");
                answerNextDialog(JOptionPane.YES_OPTION, null);
                component("newGame", JButton.class).doClick();
                check(fen().equals(Position.START_FEN), "New game cancels the restarted search");
            });
            Thread.sleep(350);
            onEdt(() -> check(fen().equals(Position.START_FEN), "New game rejects old search results"));

            String promotion = "7k/P7/8/8/8/8/8/7K w - - 0 1";
            onEdt(() -> {
                answerNextDialog(JOptionPane.OK_OPTION, promotion);
                component("loadFen", JButton.class).doClick();
                check(fen().equals(promotion), "FEN dialog loads the exact position");
                check(component("pauseEngine", JCheckBox.class).isSelected(), "FEN import pauses the engine");
                clickSquare("a7");
                answerNextDialog("Knight", null);
                clickSquare("a8");
                check(Position.fromFen(fen()).pieceAt(Square.parse("a8")) == Piece.KNIGHT, "Mouse underpromotion dialog");
                check(component("gameStatus", JLabel.class).getText().contains("insufficient"), "Result appears in UI");
                component("undoTurn", JButton.class).doClick();
                check(fen().equals(promotion), "Undo also reverses a promotion");
            });

            String lesson = "r3k2r/p1ppqpb1/bn2pnp1/3PN3/1p2P3/2N2Q1p/PPPBBPPP/R3K2R w KQkq - 0 1";
            onEdt(() -> {
                answerNextDialog(JOptionPane.OK_OPTION, lesson);
                component("loadFen", JButton.class).doClick();
                component("difficultyPreset", JComboBox.class).setSelectedIndex(3);
                component("thinkingSeconds", JSpinner.class).setValue(0.7);
                component("searchDepth", JSpinner.class).setValue(6);
                component("clearThinking", JButton.class).doClick();
                component("analyze", JButton.class).doClick();
            });
            await(() -> !component("finishSearch", JButton.class).isEnabled()
                    && component("thinkingOutput", JTextArea.class).getText().contains("Selected"), 5_000);
            onEdt(() -> check(fen().equals(lesson), "Analysis provides a hint without moving"));
            onEdt(() -> {
                int width = frame.getWidth();
                int height = frame.getHeight();
                frame.setSize(frame.getMinimumSize());
                frame.validate();
                BoardPanel board = component("board", BoardPanel.class);
                check(board.squareBounds(0).width >= 30, "Board remains usable at the minimum window size");
                check(component("boardInstructions", JLabel.class).getWidth()
                        >= component("boardInstructions", JLabel.class).getPreferredSize().width,
                        "Instructions fit at the minimum window size");
                frame.setSize(width, height);
                frame.validate();
            });

            if (args.length > 0) {
                AtomicReference<BufferedImage> capture = new AtomicReference<>();
                onEdt(() -> {
                    BufferedImage image = new BufferedImage(frame.getWidth(), frame.getHeight(), BufferedImage.TYPE_INT_RGB);
                    Graphics2D graphics = image.createGraphics();
                    try {
                        frame.paintAll(graphics);
                    } finally {
                        graphics.dispose();
                    }
                    capture.set(image);
                });
                if (!ImageIO.write(capture.get(), "png", Path.of(args[0]).toFile())) {
                    throw new IllegalStateException("No PNG writer is available.");
                }
                System.out.println("Screenshot: " + args[0]);
            }
            check(FAILURES.isEmpty(), "No uncaught EDT/background exceptions: " + FAILURES);
            System.out.println("PASS: Swing play, responsive search, move now, cancellation, undo, FEN, promotion, settings, analysis.");
        } finally {
            if (frame != null) {
                onEdt(frame::dispose);
            }
        }
    }

    private static void typeMove(String move) {
        JTextField input = component("moveInput", JTextField.class);
        check(input.isEnabled(), "Keyboard input is enabled");
        input.setText(move);
        input.postActionEvent();
    }

    private static void clickSquare(String square) {
        BoardPanel board = component("board", BoardPanel.class);
        Rectangle box = board.squareBounds(Square.parse(square));
        board.dispatchEvent(new MouseEvent(board, MouseEvent.MOUSE_PRESSED, System.currentTimeMillis(), 0,
                (int) box.getCenterX(), (int) box.getCenterY(), 1, false, MouseEvent.BUTTON1));
    }

    private static String fen() {
        return component("fenOutput", JTextField.class).getText();
    }

    private static <T extends Component> T component(String name, Class<T> type) {
        Component found = find(frame, name, type);
        if (found == null) {
            throw new AssertionError("Missing component: " + name);
        }
        return type.cast(found);
    }

    private static Component find(Component parent, String name, Class<?> type) {
        if (type.isInstance(parent) && (name == null || name.equals(parent.getName()))) {
            return parent;
        }
        if (parent instanceof Container container) {
            for (Component child : container.getComponents()) {
                Component found = find(child, name, type);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    /** A Swing Timer keeps running inside a modal dialog's nested event loop. */
    private static void answerNextDialog(Object answer, String input) {
        Timer timer = new Timer(80, null);
        timer.setRepeats(false);
        timer.addActionListener(event -> {
            for (Window window : Window.getWindows()) {
                if (window instanceof JDialog dialog && dialog.isVisible()) {
                    Component found = find(dialog, null, JOptionPane.class);
                    if (found instanceof JOptionPane pane) {
                        timer.stop();
                        if (input != null) {
                            pane.setInputValue(input);
                        }
                        pane.setValue(answer);
                        return;
                    }
                }
            }
            FAILURES.add(new AssertionError("The expected dialog did not appear."));
        });
        timer.start();
    }

    private static void await(BooleanSupplier condition, long milliseconds) throws Exception {
        long deadline = System.nanoTime() + milliseconds * 1_000_000;
        while (System.nanoTime() < deadline) {
            AtomicBoolean result = new AtomicBoolean();
            onEdt(() -> result.set(condition.getAsBoolean()));
            if (result.get()) {
                return;
            }
            if (!FAILURES.isEmpty()) {
                throw new AssertionError("UI failure: " + FAILURES);
            }
            Thread.sleep(25);
        }
        throw new AssertionError("Timed out waiting for the Swing state.");
    }

    private static void onEdt(Runnable action) throws Exception {
        SwingUtilities.invokeAndWait(action);
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

}
