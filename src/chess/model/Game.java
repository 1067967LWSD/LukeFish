package chess.model;

import java.util.ArrayList;
import java.util.List;

/**
 * A game adds history, notation, and results to a position. It belongs to the
 * UI thread; search receives a position copy and an immutable history snapshot.
 */
public final class Game {
    private final Position position;
    private final List<Position.Undo> undoStack = new ArrayList<>();
    private final List<PlayedMove> moves = new ArrayList<>();
    private final List<Long> positionKeys = new ArrayList<>();
    private Outcome outcome;

    public Game() {
        this(Position.initial());
    }

    public Game(Position position) {
        this.position = position.copy();
        positionKeys.add(this.position.key());
        updateOutcome();
    }

    /** Read-only by convention: use play()/undo() to change the live game. */
    public Position position() {
        return position;
    }

    public List<PlayedMove> moves() {
        return List.copyOf(moves);
    }

    public List<Long> positionKeys() {
        return List.copyOf(positionKeys);
    }

    public Outcome outcome() {
        return outcome;
    }

    public boolean canUndo() {
        return !undoStack.isEmpty();
    }

    public PlayedMove play(Move move) {
        if (outcome.isOver()) {
            throw new IllegalStateException("The game has ended. Undo or start a new game.");
        }
        List<Move> legal = position.legalMoves();
        if (!legal.contains(move)) {
            throw new IllegalArgumentException("Move is not legal in this position: " + move);
        }
        String notation = notationBeforeMove(move, legal);
        int color = position.sideToMove();
        int number = position.fullmoveNumber();
        undoStack.add(position.makeMove(move));
        positionKeys.add(position.key());
        updateOutcome();
        if (position.isInCheck()) {
            notation += outcome.reason() == EndReason.CHECKMATE ? "#" : "+";
        }
        PlayedMove played = new PlayedMove(move, notation, color, number);
        moves.add(played);
        return played;
    }

    public void undo() {
        if (!canUndo()) {
            throw new IllegalStateException("There is no move to undo.");
        }
        position.unmakeMove(undoStack.remove(undoStack.size() - 1));
        moves.remove(moves.size() - 1);
        positionKeys.remove(positionKeys.size() - 1);
        updateOutcome();
    }

    private void updateOutcome() {
        if (position.legalMoves().isEmpty()) {
            outcome = position.isInCheck()
                    ? new Outcome(EndReason.CHECKMATE, -position.sideToMove())
                    : new Outcome(EndReason.STALEMATE, 0);
        } else if (position.hasInsufficientMaterial()) {
            outcome = new Outcome(EndReason.INSUFFICIENT_MATERIAL, 0);
        } else if (position.halfmoveClock() >= 100) {
            outcome = new Outcome(EndReason.FIFTY_MOVE_RULE, 0);
        } else if (positionKeys.stream().filter(key -> key == position.key()).count() >= 3) {
            outcome = new Outcome(EndReason.THREEFOLD_REPETITION, 0);
        } else {
            outcome = new Outcome(EndReason.PLAYING, 0);
        }
    }

    private String notationBeforeMove(Move move, List<Move> legal) {
        if (move.isCastle()) {
            return move.to() > move.from() ? "O-O" : "O-O-O";
        }
        int type = Math.abs(position.pieceAt(move.from()));
        StringBuilder san = new StringBuilder();
        if (type != Piece.PAWN) {
            san.append(Piece.letter(type));
            boolean ambiguous = false;
            boolean sameFile = false;
            boolean sameRank = false;
            for (Move other : legal) {
                if (other.from() != move.from() && other.to() == move.to()
                        && Math.abs(position.pieceAt(other.from())) == type) {
                    ambiguous = true;
                    sameFile |= Square.file(other.from()) == Square.file(move.from());
                    sameRank |= Square.rank(other.from()) == Square.rank(move.from());
                }
            }
            if (ambiguous) {
                if (!sameFile) {
                    san.append((char) ('a' + Square.file(move.from())));
                } else if (!sameRank) {
                    san.append((char) ('1' + Square.rank(move.from())));
                } else {
                    san.append(Square.name(move.from()));
                }
            }
        } else if (move.isCapture()) {
            san.append((char) ('a' + Square.file(move.from())));
        }
        if (move.isCapture()) {
            san.append('x');
        }
        san.append(Square.name(move.to()));
        if (move.promotion() != 0) {
            san.append('=').append(Piece.letter(move.promotion()));
        }
        return san.toString();
    }

    public enum EndReason {
        PLAYING, CHECKMATE, STALEMATE, INSUFFICIENT_MATERIAL,
        FIFTY_MOVE_RULE, THREEFOLD_REPETITION
    }

    public record PlayedMove(Move move, String notation, int color, int number) {
    }

    public record Outcome(EndReason reason, int winner) {
        public boolean isOver() {
            return reason != EndReason.PLAYING;
        }

        public String description() {
            return switch (reason) {
                case PLAYING -> "Game in progress";
                case CHECKMATE -> "Checkmate - " + Piece.colorName(winner) + " wins";
                case STALEMATE -> "Draw - stalemate";
                case INSUFFICIENT_MATERIAL -> "Draw - insufficient mating material";
                case FIFTY_MOVE_RULE -> "Draw - 50-move rule (automatically claimed)";
                case THREEFOLD_REPETITION -> "Draw - threefold repetition (automatically claimed)";
            };
        }
    }
}
