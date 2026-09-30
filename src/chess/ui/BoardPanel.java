package chess.ui;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.font.GlyphVector;
import java.awt.geom.AffineTransform;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.util.List;
import java.util.function.BiConsumer;

import javax.swing.AbstractAction;
import javax.swing.JPanel;
import javax.swing.KeyStroke;

import chess.model.Move;
import chess.model.Piece;
import chess.model.Position;
import chess.model.Square;

/**
 * Rendering and input only: this panel cannot make a chess move by itself.
 * It reports a requested from/to pair to its controller, much like a browser
 * event handler reports a click to JavaScript application code.
 */
public final class BoardPanel extends JPanel {
    private static final long serialVersionUID = 1L;
    private static final Color LIGHT = new Color(232, 224, 204);
    private static final Color DARK = new Color(104, 143, 128);
    private static final Color ACCENT = new Color(32, 119, 174);
    private static final int MARGIN = 28;

    private final BiConsumer<Integer, Integer> moveRequest;
    private final String pieceFont;
    private Position position = Position.initial();
    private List<Move> legalMoves = List.of();
    private Move lastMove;
    private Move hint;
    private int selected = -1;
    private int cursor = Square.parse("e2");
    private boolean flipped;
    private boolean inputEnabled;

    public BoardPanel(BiConsumer<Integer, Integer> moveRequest) {
        this.moveRequest = moveRequest;
        this.pieceFont = choosePieceFont();
        setName("board");
        setFocusable(true);
        setBackground(new Color(245, 246, 244));
        setPreferredSize(new Dimension(640, 640));
        setMinimumSize(new Dimension(340, 340));
        setToolTipText("Click a piece, then a marked square. Keyboard: arrows, Enter or Space, Escape.");
        getAccessibleContext().setAccessibleName("Chess board");
        addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent event) {
                if (event.getButton() == MouseEvent.BUTTON1) {
                    requestFocusInWindow();
                    int square = squareAt(event.getPoint());
                    if (square >= 0) {
                        cursor = square;
                        selectOrMove(square);
                    }
                }
            }
        });
        addFocusListener(new FocusAdapter() {
            @Override
            public void focusGained(FocusEvent event) {
                repaint();
            }

            @Override
            public void focusLost(FocusEvent event) {
                repaint();
            }
        });
        bind("LEFT", "cursor-left", () -> moveCursor(-1, 0));
        bind("RIGHT", "cursor-right", () -> moveCursor(1, 0));
        bind("UP", "cursor-up", () -> moveCursor(0, -1));
        bind("DOWN", "cursor-down", () -> moveCursor(0, 1));
        bind("ENTER", "choose-square", () -> selectOrMove(cursor));
        bind("SPACE", "choose-square-space", () -> selectOrMove(cursor));
        bind("ESCAPE", "clear-selection", () -> {
            selected = -1;
            repaint();
        });
    }

    public void setGameState(Position source, List<Move> moves, Move previous, boolean enabled) {
        if (!position.toFen().equals(source.toFen())) {
            selected = -1;
            hint = null;
            cursor = previous == null
                    ? source.kingSquare(source.sideToMove()) : previous.to();
        }
        position = source.copy();
        legalMoves = List.copyOf(moves);
        lastMove = previous;
        inputEnabled = enabled;
        if (!enabled) {
            selected = -1;
        }
        getAccessibleContext().setAccessibleDescription(
                Piece.colorName(position.sideToMove()) + " to move. "
                + (enabled ? "Board input enabled." : "Board input disabled while the computer plays.")
                + " Arrow keys move the cursor; Enter selects. Coordinate entry is also available below the board.");
        repaint();
    }

    public void setFlipped(boolean flipped) {
        this.flipped = flipped;
        repaint();
    }

    public boolean isFlipped() {
        return flipped;
    }

    public void setHint(Move hint) {
        this.hint = hint;
        repaint();
    }

    /** Both painting and mouse input use the same geometry, including rotation. */
    public Rectangle squareBounds(int square) {
        int size = squareSize();
        int x = (getWidth() - 8 * size) / 2;
        int y = (getHeight() - 8 * size) / 2;
        int column = flipped ? 7 - Square.file(square) : Square.file(square);
        int row = flipped ? Square.rank(square) : 7 - Square.rank(square);
        return new Rectangle(x + column * size, y + row * size, size, size);
    }

    public int squareAt(Point point) {
        int size = squareSize();
        int left = (getWidth() - 8 * size) / 2;
        int top = (getHeight() - 8 * size) / 2;
        if (point.x < left || point.y < top || point.x >= left + 8 * size || point.y >= top + 8 * size) {
            return -1;
        }
        int column = (point.x - left) / size;
        int row = (point.y - top) / size;
        return Square.of(flipped ? 7 - column : column, flipped ? row : 7 - row);
    }

    private int squareSize() {
        return Math.max(1, (Math.min(getWidth(), getHeight()) - MARGIN * 2) / 8);
    }

    private void selectOrMove(int square) {
        if (!inputEnabled) {
            Toolkit.getDefaultToolkit().beep();
            return;
        }
        if (selected >= 0) {
            for (Move move : legalMoves) {
                if (move.from() == selected && move.to() == square) {
                    int from = selected;
                    selected = -1;
                    moveRequest.accept(from, square);
                    repaint();
                    return;
                }
            }
        }
        if (Piece.color(position.pieceAt(square)) == position.sideToMove()) {
            selected = selected == square ? -1 : square;
        } else {
            selected = -1;
            Toolkit.getDefaultToolkit().beep();
        }
        repaint();
    }

    private void moveCursor(int screenX, int screenY) {
        int file = Square.file(cursor) + (flipped ? -screenX : screenX);
        int rank = Square.rank(cursor) + (flipped ? screenY : -screenY);
        if (Square.onBoard(file, rank)) {
            cursor = Square.of(file, rank);
            repaint();
        }
    }

    private void bind(String key, String name, Runnable action) {
        getInputMap(WHEN_FOCUSED).put(KeyStroke.getKeyStroke(key), name);
        getActionMap().put(name, new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent event) {
                action.run();
            }
        });
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);
        Graphics2D g = (Graphics2D) graphics.create();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            boolean check = position.isInCheck();
            for (int square = 0; square < 64; square++) {
                Rectangle box = squareBounds(square);
                g.setColor((Square.file(square) + Square.rank(square)) % 2 == 0 ? DARK : LIGHT);
                g.fill(box);
                if (lastMove != null && (square == lastMove.from() || square == lastMove.to())) {
                    g.setColor(new Color(248, 215, 77, 115));
                    g.fill(box);
                }
                if (check && square == position.kingSquare(position.sideToMove())) {
                    g.setColor(new Color(220, 68, 64, 165));
                    g.fill(box);
                }
                if (square == selected) {
                    g.setColor(new Color(255, 210, 66, 165));
                    g.fill(box);
                }
                if (position.pieceAt(square) != Piece.EMPTY) {
                    paintPiece(g, position.pieceAt(square), box);
                }
            }
            paintDestinations(g);
            paintCoordinates(g);
            if (hint != null) {
                paintHint(g);
            }
            if (hasFocus()) {
                Rectangle box = squareBounds(cursor);
                g.setColor(ACCENT);
                g.setStroke(new BasicStroke(3));
                g.drawRect(box.x + 3, box.y + 3, box.width - 6, box.height - 6);
            }
        } finally {
            g.dispose();
        }
    }

    private void paintDestinations(Graphics2D g) {
        if (selected < 0) {
            return;
        }
        g.setColor(new Color(28, 65, 64, 120));
        boolean[] marked = new boolean[64];
        for (Move move : legalMoves) {
            if (move.from() != selected || marked[move.to()]) {
                continue;
            }
            // All four promotion choices share the same destination marker.
            marked[move.to()] = true;
            Rectangle box = squareBounds(move.to());
            if (move.isCapture()) {
                g.setStroke(new BasicStroke(Math.max(3, box.width / 16f)));
                g.drawOval(box.x + 5, box.y + 5, box.width - 10, box.height - 10);
            } else {
                int diameter = Math.max(8, box.width / 5);
                g.fillOval(box.x + (box.width - diameter) / 2,
                        box.y + (box.height - diameter) / 2, diameter, diameter);
            }
        }
    }

    private void paintCoordinates(Graphics2D g) {
        int size = squareSize();
        int left = (getWidth() - 8 * size) / 2;
        int top = (getHeight() - 8 * size) / 2;
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 12));
        g.setColor(new Color(74, 87, 86));
        for (int index = 0; index < 8; index++) {
            String file = String.valueOf((char) ('a' + (flipped ? 7 - index : index)));
            String rank = String.valueOf(flipped ? index + 1 : 8 - index);
            g.drawString(file, left + index * size + size / 2 - 3, top + 8 * size + 18);
            g.drawString(rank, left - 17, top + index * size + size / 2 + 4);
        }
    }

    private void paintPiece(Graphics2D g, int piece, Rectangle box) {
        int type = Math.abs(piece);
        // Filled chess glyphs make good silhouettes for BOTH colors. Drawing
        // an outline keeps white pieces visible on light squares and vice versa.
        char glyph = switch (type) {
            case Piece.PAWN -> '\u265f';
            case Piece.KNIGHT -> '\u265e';
            case Piece.BISHOP -> '\u265d';
            case Piece.ROOK -> '\u265c';
            case Piece.QUEEN -> '\u265b';
            case Piece.KING -> '\u265a';
            default -> throw new IllegalStateException("Unknown board piece.");
        };
        Font font = new Font(pieceFont, Font.PLAIN, Math.max(12, (int) (box.width * 0.82)));
        String text = font.canDisplay(glyph) ? String.valueOf(glyph) : String.valueOf(Piece.letter(type));
        GlyphVector vector = font.createGlyphVector(g.getFontRenderContext(), text);
        Shape outline = vector.getOutline();
        Rectangle2D bounds = outline.getBounds2D();
        Shape centered = AffineTransform.getTranslateInstance(
                box.getCenterX() - bounds.getCenterX(),
                box.getCenterY() - bounds.getCenterY()).createTransformedShape(outline);
        g.setColor(piece > 0 ? new Color(255, 253, 240) : new Color(34, 43, 49));
        g.fill(centered);
        g.setColor(piece > 0 ? new Color(52, 64, 66) : new Color(232, 237, 225));
        g.setStroke(new BasicStroke(Math.max(1, box.width / 55f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(centered);
    }

    private void paintHint(Graphics2D g) {
        Rectangle from = squareBounds(hint.from());
        Rectangle to = squareBounds(hint.to());
        double x1 = from.getCenterX();
        double y1 = from.getCenterY();
        double x2 = to.getCenterX();
        double y2 = to.getCenterY();
        double angle = Math.atan2(y2 - y1, x2 - x1);
        double tip = squareSize() * 0.28;
        g.setColor(new Color(32, 119, 174, 185));
        g.setStroke(new BasicStroke(Math.max(4, squareSize() / 10f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.drawLine((int) x1, (int) y1, (int) x2, (int) y2);
        Path2D arrow = new Path2D.Double();
        arrow.moveTo(x2, y2);
        arrow.lineTo(x2 - tip * Math.cos(angle - 0.55), y2 - tip * Math.sin(angle - 0.55));
        arrow.lineTo(x2 - tip * Math.cos(angle + 0.55), y2 - tip * Math.sin(angle + 0.55));
        arrow.closePath();
        g.fill(arrow);
    }

    private static String choosePieceFont() {
        for (String family : new String[] {"Segoe UI Symbol", "DejaVu Sans", "Noto Sans Symbols 2", Font.SERIF}) {
            Font font = new Font(family, Font.PLAIN, 48);
            if (font.canDisplayUpTo("\u265a\u265b\u265c\u265d\u265e\u265f") == -1) {
                return family;
            }
        }
        return Font.SANS_SERIF; // Letter pieces remain usable without a symbol font.
    }
}
