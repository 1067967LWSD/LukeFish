package chess.model;

/** Coordinates are zero-based: a1 = 0, b1 = 1, ..., h8 = 63. */
public final class Square {
    private Square() {
    }

    public static int of(int file, int rank) {
        if (!onBoard(file, rank)) {
            throw new IllegalArgumentException("Square is outside the board.");
        }
        return rank * 8 + file;
    }

    public static boolean onBoard(int file, int rank) {
        return file >= 0 && file < 8 && rank >= 0 && rank < 8;
    }

    public static int file(int square) {
        return square % 8;
    }

    public static int rank(int square) {
        return square / 8;
    }

    public static String name(int square) {
        if (square < 0 || square >= 64) {
            throw new IllegalArgumentException("Square must be between 0 and 63.");
        }
        return "" + (char) ('a' + file(square)) + (char) ('1' + rank(square));
    }

    public static int parse(String text) {
        if (text == null || text.length() != 2
                || text.charAt(0) < 'a' || text.charAt(0) > 'h'
                || text.charAt(1) < '1' || text.charAt(1) > '8') {
            throw new IllegalArgumentException("Use a square name such as e4.");
        }
        return of(text.charAt(0) - 'a', text.charAt(1) - '1');
    }
}
