package chess.model;

/**
 * A record is an immutable data carrier. Java supplies its constructor,
 * accessors, equals(), and hashCode(); no caller can change a move in place.
 */
public record Move(int from, int to, int promotion, int flags) {
    public static final int CAPTURE = 1;
    public static final int EN_PASSANT = 2;
    public static final int CASTLE = 4;
    public static final int DOUBLE_PAWN = 8;

    public Move {
        if (from < 0 || from >= 64 || to < 0 || to >= 64 || from == to) {
            throw new IllegalArgumentException("A move needs two different board squares.");
        }
        if (promotion != 0 && (promotion < Piece.KNIGHT || promotion > Piece.QUEEN)) {
            throw new IllegalArgumentException("Promote to a queen, rook, bishop, or knight.");
        }
        if ((flags & ~15) != 0) {
            throw new IllegalArgumentException("Unknown move flags.");
        }
    }

    public boolean isCapture() {
        return (flags & CAPTURE) != 0;
    }

    public boolean isEnPassant() {
        return (flags & EN_PASSANT) != 0;
    }

    public boolean isCastle() {
        return (flags & CASTLE) != 0;
    }

    /** Coordinate notation is also convenient for reproducible tests. */
    public String toUci() {
        String suffix = promotion == 0 ? "" : String.valueOf(
                Character.toLowerCase(Piece.letter(promotion)));
        return Square.name(from) + Square.name(to) + suffix;
    }

    @Override
    public String toString() {
        return toUci();
    }
}
