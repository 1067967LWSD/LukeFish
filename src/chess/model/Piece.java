package chess.model;

/**
 * Small integer values keep the engine's inner loops inexpensive.
 * The sign is the color: a white knight is +2, a black knight is -2.
 * Unlike a JavaScript object, these constants have a checked, fixed type.
 */
public final class Piece {
    public static final int EMPTY = 0;
    public static final int WHITE = 1;
    public static final int BLACK = -1;
    public static final int PAWN = 1;
    public static final int KNIGHT = 2;
    public static final int BISHOP = 3;
    public static final int ROOK = 4;
    public static final int QUEEN = 5;
    public static final int KING = 6;

    private static final String LETTERS = " PNBRQK";

    private Piece() {
        // A utility class groups functions; it does not need instances.
    }

    public static int color(int piece) {
        return Integer.signum(piece);
    }

    public static char letter(int type) {
        if (type < PAWN || type > KING) {
            throw new IllegalArgumentException("Unknown piece type: " + type);
        }
        return LETTERS.charAt(type);
    }

    public static char toFen(int piece) {
        char letter = letter(Math.abs(piece));
        return piece > 0 ? letter : Character.toLowerCase(letter);
    }

    public static int fromFen(char letter) {
        int type = LETTERS.indexOf(Character.toUpperCase(letter));
        if (type < PAWN) {
            throw new IllegalArgumentException("Unknown FEN piece: " + letter);
        }
        return Character.isUpperCase(letter) ? type : -type;
    }

    public static String colorName(int color) {
        if (color != WHITE && color != BLACK) {
            throw new IllegalArgumentException("A color must be WHITE or BLACK.");
        }
        return color == WHITE ? "White" : "Black";
    }
}
