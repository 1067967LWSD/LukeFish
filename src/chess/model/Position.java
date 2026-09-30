package chess.model;

import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;

/**
 * Chess rules without any Swing or search code.
 *
 * This object is mutable for fast make/unmake during search. The UI gives the
 * engine a copy, so the two threads never edit the same position. A beginner
 * can first study legalMoves(), then follow one pawn move through makeMove().
 */
public final class Position {
    public static final String START_FEN =
            "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1";
    public static final int WHITE_KING_SIDE = 1;
    public static final int WHITE_QUEEN_SIDE = 2;
    public static final int BLACK_KING_SIDE = 4;
    public static final int BLACK_QUEEN_SIDE = 8;

    private static final int[][] KNIGHT_STEPS = {
        {1, 2}, {2, 1}, {2, -1}, {1, -2},
        {-1, -2}, {-2, -1}, {-2, 1}, {-1, 2}
    };
    private static final int[][] KING_STEPS = {
        {1, 0}, {1, 1}, {0, 1}, {-1, 1},
        {-1, 0}, {-1, -1}, {0, -1}, {1, -1}
    };
    private static final int[][] BISHOP_STEPS = {{1, 1}, {1, -1}, {-1, 1}, {-1, -1}};
    private static final int[][] ROOK_STEPS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

    // Zobrist hashing combines random piece/square numbers with XOR. A fixed
    // seed makes hashes reproducible, rather than changing between app runs.
    private static final long[][] PIECE_KEYS = new long[12][64];
    private static final long[] CASTLING_KEYS = new long[16];
    private static final long[] EP_KEYS = new long[8];
    private static final long SIDE_KEY;

    static {
        SplittableRandom random = new SplittableRandom(0x4C756B6546697368L);
        for (long[] row : PIECE_KEYS) {
            for (int square = 0; square < 64; square++) {
                row[square] = random.nextLong();
            }
        }
        for (int i = 0; i < CASTLING_KEYS.length; i++) {
            CASTLING_KEYS[i] = random.nextLong();
        }
        for (int i = 0; i < EP_KEYS.length; i++) {
            EP_KEYS[i] = random.nextLong();
        }
        SIDE_KEY = random.nextLong();
    }

    private final int[] board = new int[64];
    private int sideToMove = Piece.WHITE;
    private int castlingRights;
    private int enPassantSquare = -1;
    private int halfmoveClock;
    private int fullmoveNumber = 1;
    private int whiteKing;
    private int blackKing;
    private long key;

    private Position() {
    }

    public static Position initial() {
        return fromFen(START_FEN);
    }

    /**
     * FEN is a six-field text format for a board and its rule state.
     * We reject malformed state, but do not try to prove that a composed
     * position could have arisen in a real game.
     */
    public static Position fromFen(String fen) {
        if (fen == null) {
            throw new IllegalArgumentException("FEN cannot be null.");
        }
        String[] fields = fen.trim().split("\\s+");
        if (fields.length != 6) {
            throw new IllegalArgumentException("FEN must have six fields, including both move counters.");
        }
        Position position = new Position();
        String[] ranks = fields[0].split("/", -1);
        if (ranks.length != 8) {
            throw new IllegalArgumentException("FEN must describe eight ranks.");
        }
        int whiteKings = 0;
        int blackKings = 0;
        for (int row = 0; row < 8; row++) {
            int file = 0;
            for (char character : ranks[row].toCharArray()) {
                if (character >= '1' && character <= '8') {
                    file += character - '0';
                } else {
                    if (file >= 8) {
                        throw new IllegalArgumentException("Too many squares in a FEN rank.");
                    }
                    int piece = Piece.fromFen(character);
                    int square = Square.of(file++, 7 - row);
                    position.board[square] = piece;
                    if (Math.abs(piece) == Piece.PAWN && (row == 0 || row == 7)) {
                        throw new IllegalArgumentException("A pawn cannot remain on its promotion rank.");
                    }
                    if (piece == Piece.KING) {
                        position.whiteKing = square;
                        whiteKings++;
                    } else if (piece == -Piece.KING) {
                        position.blackKing = square;
                        blackKings++;
                    }
                }
            }
            if (file != 8) {
                throw new IllegalArgumentException("Each FEN rank must contain exactly eight squares.");
            }
        }
        if (whiteKings != 1 || blackKings != 1) {
            throw new IllegalArgumentException("A position needs exactly one king of each color.");
        }
        if (Math.abs(Square.file(position.whiteKing) - Square.file(position.blackKing)) <= 1
                && Math.abs(Square.rank(position.whiteKing) - Square.rank(position.blackKing)) <= 1) {
            throw new IllegalArgumentException("Kings cannot occupy adjacent squares.");
        }
        position.sideToMove = switch (fields[1]) {
            case "w" -> Piece.WHITE;
            case "b" -> Piece.BLACK;
            default -> throw new IllegalArgumentException("FEN side to move must be w or b.");
        };
        if (!fields[2].equals("-")) {
            for (char character : fields[2].toCharArray()) {
                int right = switch (character) {
                    case 'K' -> WHITE_KING_SIDE;
                    case 'Q' -> WHITE_QUEEN_SIDE;
                    case 'k' -> BLACK_KING_SIDE;
                    case 'q' -> BLACK_QUEEN_SIDE;
                    default -> throw new IllegalArgumentException("Invalid castling rights.");
                };
                if ((position.castlingRights & right) != 0) {
                    throw new IllegalArgumentException("Duplicate castling right.");
                }
                position.castlingRights |= right;
            }
        }
        position.validateCastlingRight(WHITE_KING_SIDE, 4, 7, Piece.WHITE);
        position.validateCastlingRight(WHITE_QUEEN_SIDE, 4, 0, Piece.WHITE);
        position.validateCastlingRight(BLACK_KING_SIDE, 60, 63, Piece.BLACK);
        position.validateCastlingRight(BLACK_QUEEN_SIDE, 60, 56, Piece.BLACK);
        if (!fields[3].equals("-")) {
            position.enPassantSquare = Square.parse(fields[3]);
            int ep = position.enPassantSquare;
            int expectedRank = position.sideToMove == Piece.WHITE ? 5 : 2;
            if (Square.rank(ep) != expectedRank || position.board[ep] != Piece.EMPTY
                    || position.board[ep - 8 * position.sideToMove] != -position.sideToMove * Piece.PAWN
                    || position.board[ep + 8 * position.sideToMove] != Piece.EMPTY) {
                throw new IllegalArgumentException("The en passant target must follow an opposing double pawn move.");
            }
        }
        try {
            position.halfmoveClock = Integer.parseInt(fields[4]);
            position.fullmoveNumber = Integer.parseInt(fields[5]);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("FEN move counters must be whole numbers.", exception);
        }
        if (position.halfmoveClock < 0 || position.fullmoveNumber < 1
                || position.halfmoveClock > 1_000_000 || position.fullmoveNumber > 1_000_000) {
            throw new IllegalArgumentException("FEN counters must be in range: halfmoves 0..1000000, moves 1..1000000.");
        }
        position.key = position.calculateKey();
        return position;
    }

    private void validateCastlingRight(int right, int king, int rook, int color) {
        if ((castlingRights & right) != 0
                && (board[king] != color * Piece.KING || board[rook] != color * Piece.ROOK)) {
            throw new IllegalArgumentException("Castling rights require the king and rook on their starting squares.");
        }
    }

    public Position copy() {
        Position copy = new Position();
        System.arraycopy(board, 0, copy.board, 0, board.length);
        copy.sideToMove = sideToMove;
        copy.castlingRights = castlingRights;
        copy.enPassantSquare = enPassantSquare;
        copy.halfmoveClock = halfmoveClock;
        copy.fullmoveNumber = fullmoveNumber;
        copy.whiteKing = whiteKing;
        copy.blackKing = blackKing;
        copy.key = key;
        return copy;
    }

    public int pieceAt(int square) {
        return board[square];
    }

    public int sideToMove() {
        return sideToMove;
    }

    public int halfmoveClock() {
        return halfmoveClock;
    }

    public int fullmoveNumber() {
        return fullmoveNumber;
    }

    public int kingSquare(int color) {
        return color == Piece.WHITE ? whiteKing : blackKing;
    }

    /**
     * Repetition identity excludes move counters, and includes en passant
     * only when a legal en passant capture exists (even pins matter).
     */
    public long key() {
        return key;
    }

    public boolean isInCheck() {
        return isInCheck(sideToMove);
    }

    public boolean isInCheck(int color) {
        return isAttacked(kingSquare(color), -color);
    }

    public List<Move> legalMoves() {
        List<Move> legal = new ArrayList<>(48);
        int movingSide = sideToMove;
        for (Move move : pseudoLegalMoves()) {
            Undo undo = makeMove(move);
            boolean leavesKingSafe = !isInCheck(movingSide);
            unmakeMove(undo);
            if (leavesKingSafe) {
                legal.add(move);
            }
        }
        return legal;
    }

    public Move requireLegalMove(String uci) {
        for (Move move : legalMoves()) {
            if (move.toUci().equalsIgnoreCase(uci)) {
                return move;
            }
        }
        throw new IllegalArgumentException("Not a legal move: " + uci
                + ". Use coordinates such as e2e4, or e7e8q for promotion.");
    }

    private List<Move> pseudoLegalMoves() {
        List<Move> moves = new ArrayList<>(48);
        for (int from = 0; from < 64; from++) {
            int piece = board[from];
            if (Piece.color(piece) != sideToMove) {
                continue;
            }
            switch (Math.abs(piece)) {
                case Piece.PAWN -> addPawnMoves(moves, from);
                case Piece.KNIGHT -> addStepMoves(moves, from, KNIGHT_STEPS);
                case Piece.BISHOP -> addSlidingMoves(moves, from, BISHOP_STEPS);
                case Piece.ROOK -> addSlidingMoves(moves, from, ROOK_STEPS);
                case Piece.QUEEN -> {
                    addSlidingMoves(moves, from, BISHOP_STEPS);
                    addSlidingMoves(moves, from, ROOK_STEPS);
                }
                case Piece.KING -> {
                    addStepMoves(moves, from, KING_STEPS);
                    addCastlingMoves(moves, from);
                }
                default -> throw new IllegalStateException("Invalid piece on the board.");
            }
        }
        return moves;
    }

    private void addPawnMoves(List<Move> moves, int from) {
        int direction = sideToMove * 8;
        int to = from + direction;
        if (to >= 0 && to < 64 && board[to] == Piece.EMPTY) {
            addPawnMove(moves, from, to, 0);
            int startRank = sideToMove == Piece.WHITE ? 1 : 6;
            if (Square.rank(from) == startRank && board[to + direction] == Piece.EMPTY) {
                moves.add(new Move(from, to + direction, 0, Move.DOUBLE_PAWN));
            }
        }
        for (int fileChange : new int[] {-1, 1}) {
            int file = Square.file(from) + fileChange;
            int rank = Square.rank(from) + sideToMove;
            if (!Square.onBoard(file, rank)) {
                continue;
            }
            to = Square.of(file, rank);
            if (Piece.color(board[to]) == -sideToMove && Math.abs(board[to]) != Piece.KING) {
                addPawnMove(moves, from, to, Move.CAPTURE);
            } else if (to == enPassantSquare) {
                moves.add(new Move(from, to, 0, Move.CAPTURE | Move.EN_PASSANT));
            }
        }
    }

    private void addPawnMove(List<Move> moves, int from, int to, int flags) {
        if (Square.rank(to) == 0 || Square.rank(to) == 7) {
            for (int promotion : new int[] {Piece.QUEEN, Piece.ROOK, Piece.BISHOP, Piece.KNIGHT}) {
                moves.add(new Move(from, to, promotion, flags));
            }
        } else {
            moves.add(new Move(from, to, 0, flags));
        }
    }

    private void addStepMoves(List<Move> moves, int from, int[][] steps) {
        for (int[] step : steps) {
            int file = Square.file(from) + step[0];
            int rank = Square.rank(from) + step[1];
            if (Square.onBoard(file, rank)) {
                addDestination(moves, from, Square.of(file, rank));
            }
        }
    }

    private void addSlidingMoves(List<Move> moves, int from, int[][] steps) {
        for (int[] step : steps) {
            int file = Square.file(from) + step[0];
            int rank = Square.rank(from) + step[1];
            while (Square.onBoard(file, rank)) {
                int to = Square.of(file, rank);
                addDestination(moves, from, to);
                if (board[to] != Piece.EMPTY) {
                    break;
                }
                file += step[0];
                rank += step[1];
            }
        }
    }

    private void addDestination(List<Move> moves, int from, int to) {
        if (board[to] == Piece.EMPTY) {
            moves.add(new Move(from, to, 0, 0));
        } else if (Piece.color(board[to]) == -sideToMove && Math.abs(board[to]) != Piece.KING) {
            moves.add(new Move(from, to, 0, Move.CAPTURE));
        }
    }

    private void addCastlingMoves(List<Move> moves, int from) {
        int start = sideToMove == Piece.WHITE ? 4 : 60;
        if (from != start || isInCheck()) {
            return;
        }
        int kingSide = sideToMove == Piece.WHITE ? WHITE_KING_SIDE : BLACK_KING_SIDE;
        int queenSide = sideToMove == Piece.WHITE ? WHITE_QUEEN_SIDE : BLACK_QUEEN_SIDE;
        if ((castlingRights & kingSide) != 0
                && board[start + 1] == 0 && board[start + 2] == 0
                && !isAttacked(start + 1, -sideToMove) && !isAttacked(start + 2, -sideToMove)) {
            moves.add(new Move(start, start + 2, 0, Move.CASTLE));
        }
        if ((castlingRights & queenSide) != 0
                && board[start - 1] == 0 && board[start - 2] == 0 && board[start - 3] == 0
                && !isAttacked(start - 1, -sideToMove) && !isAttacked(start - 2, -sideToMove)) {
            moves.add(new Move(start, start - 2, 0, Move.CASTLE));
        }
    }

    public boolean isAttacked(int square, int byColor) {
        int file = Square.file(square);
        int rank = Square.rank(square);
        for (int change : new int[] {-1, 1}) {
            int pawnFile = file + change;
            int pawnRank = rank - byColor;
            if (Square.onBoard(pawnFile, pawnRank)
                    && board[Square.of(pawnFile, pawnRank)] == byColor * Piece.PAWN) {
                return true;
            }
        }
        if (attackedByStep(file, rank, byColor * Piece.KNIGHT, KNIGHT_STEPS)
                || attackedByStep(file, rank, byColor * Piece.KING, KING_STEPS)) {
            return true;
        }
        return attackedByRay(file, rank, byColor, Piece.BISHOP, BISHOP_STEPS)
                || attackedByRay(file, rank, byColor, Piece.ROOK, ROOK_STEPS);
    }

    private boolean attackedByStep(int file, int rank, int attacker, int[][] steps) {
        for (int[] step : steps) {
            int targetFile = file + step[0];
            int targetRank = rank + step[1];
            if (Square.onBoard(targetFile, targetRank)
                    && board[Square.of(targetFile, targetRank)] == attacker) {
                return true;
            }
        }
        return false;
    }

    private boolean attackedByRay(int file, int rank, int color, int type, int[][] steps) {
        for (int[] step : steps) {
            int targetFile = file + step[0];
            int targetRank = rank + step[1];
            while (Square.onBoard(targetFile, targetRank)) {
                int piece = board[Square.of(targetFile, targetRank)];
                if (piece != Piece.EMPTY) {
                    if (piece == color * type || piece == color * Piece.QUEEN) {
                        return true;
                    }
                    break;
                }
                targetFile += step[0];
                targetRank += step[1];
            }
        }
        return false;
    }

    /**
     * Apply a generated move, not arbitrary user input. Game.play() validates
     * input; search uses this lower-level method millions of times.
     * Undo stores only what changed, rather than copying all 64 squares.
     */
    public Undo makeMove(Move move) {
        int movedPiece = board[move.from()];
        int capturedSquare = move.isEnPassant() ? move.to() - 8 * sideToMove : move.to();
        Undo undo = new Undo(move, movedPiece, board[capturedSquare], capturedSquare,
                castlingRights, enPassantSquare, halfmoveClock, fullmoveNumber,
                whiteKing, blackKing, key);
        int oldEpFile = effectiveEnPassantFile();
        if (oldEpFile >= 0) {
            key ^= EP_KEYS[oldEpFile];
        }
        key ^= CASTLING_KEYS[castlingRights];
        setPiece(move.from(), Piece.EMPTY);
        if (move.isEnPassant()) {
            setPiece(capturedSquare, Piece.EMPTY);
        }
        setPiece(move.to(), move.promotion() == 0 ? movedPiece : sideToMove * move.promotion());
        if (move.isCastle()) {
            int rookFrom = move.to() > move.from() ? move.from() + 3 : move.from() - 4;
            int rookTo = move.to() > move.from() ? move.from() + 1 : move.from() - 1;
            setPiece(rookTo, board[rookFrom]);
            setPiece(rookFrom, Piece.EMPTY);
        }
        if (Math.abs(movedPiece) == Piece.KING) {
            if (sideToMove == Piece.WHITE) {
                whiteKing = move.to();
                castlingRights &= ~(WHITE_KING_SIDE | WHITE_QUEEN_SIDE);
            } else {
                blackKing = move.to();
                castlingRights &= ~(BLACK_KING_SIDE | BLACK_QUEEN_SIDE);
            }
        }
        clearRookRight(move.from());
        clearRookRight(move.to());
        enPassantSquare = (move.flags() & Move.DOUBLE_PAWN) != 0
                ? (move.from() + move.to()) / 2 : -1;
        halfmoveClock = Math.abs(movedPiece) == Piece.PAWN || undo.capturedPiece() != 0
                ? 0 : halfmoveClock + 1;
        if (sideToMove == Piece.BLACK) {
            fullmoveNumber++;
        }
        sideToMove = -sideToMove;
        key ^= SIDE_KEY ^ CASTLING_KEYS[castlingRights];
        int newEpFile = effectiveEnPassantFile();
        if (newEpFile >= 0) {
            key ^= EP_KEYS[newEpFile];
        }
        return undo;
    }

    public void unmakeMove(Undo undo) {
        Move move = undo.move();
        board[move.from()] = undo.movedPiece();
        board[move.to()] = Piece.EMPTY;
        board[undo.capturedSquare()] = undo.capturedPiece();
        if (move.isCastle()) {
            int rookFrom = move.to() > move.from() ? move.from() + 3 : move.from() - 4;
            int rookTo = move.to() > move.from() ? move.from() + 1 : move.from() - 1;
            board[rookFrom] = board[rookTo];
            board[rookTo] = Piece.EMPTY;
        }
        castlingRights = undo.castlingRights();
        enPassantSquare = undo.enPassantSquare();
        halfmoveClock = undo.halfmoveClock();
        fullmoveNumber = undo.fullmoveNumber();
        whiteKing = undo.whiteKing();
        blackKing = undo.blackKing();
        key = undo.key();
        sideToMove = -sideToMove;
    }

    private void clearRookRight(int square) {
        castlingRights &= switch (square) {
            case 0 -> ~WHITE_QUEEN_SIDE;
            case 7 -> ~WHITE_KING_SIDE;
            case 56 -> ~BLACK_QUEEN_SIDE;
            case 63 -> ~BLACK_KING_SIDE;
            default -> ~0;
        };
    }

    private static int pieceIndex(int piece) {
        return piece > 0 ? piece - 1 : 6 + (-piece - 1);
    }

    private void setPiece(int square, int piece) {
        if (board[square] != Piece.EMPTY) {
            key ^= PIECE_KEYS[pieceIndex(board[square])][square];
        }
        board[square] = piece;
        if (piece != Piece.EMPTY) {
            key ^= PIECE_KEYS[pieceIndex(piece)][square];
        }
    }

    private long calculateKey() {
        long hash = CASTLING_KEYS[castlingRights];
        for (int square = 0; square < 64; square++) {
            if (board[square] != Piece.EMPTY) {
                hash ^= PIECE_KEYS[pieceIndex(board[square])][square];
            }
        }
        if (sideToMove == Piece.BLACK) {
            hash ^= SIDE_KEY;
        }
        int epFile = effectiveEnPassantFile();
        return epFile < 0 ? hash : hash ^ EP_KEYS[epFile];
    }

    private int effectiveEnPassantFile() {
        if (enPassantSquare < 0) {
            return -1;
        }
        int capturedSquare = enPassantSquare - 8 * sideToMove;
        int file = Square.file(enPassantSquare);
        for (int change : new int[] {-1, 1}) {
            int sourceFile = file + change;
            if (sourceFile < 0 || sourceFile > 7) {
                continue;
            }
            int from = Square.of(sourceFile, Square.rank(capturedSquare));
            if (board[from] != sideToMove * Piece.PAWN) {
                continue;
            }
            // Do not call makeMove here: makeMove itself updates this hash.
            int captured = board[capturedSquare];
            board[from] = Piece.EMPTY;
            board[capturedSquare] = Piece.EMPTY;
            board[enPassantSquare] = sideToMove * Piece.PAWN;
            boolean legal = !isInCheck();
            board[from] = sideToMove * Piece.PAWN;
            board[capturedSquare] = captured;
            board[enPassantSquare] = Piece.EMPTY;
            if (legal) {
                return file;
            }
        }
        return -1;
    }

    /** Counts destinations, not legal moves: pins are deliberately ignored. */
    public int mobility(int color) {
        int count = 0;
        for (int square = 0; square < 64; square++) {
            int piece = board[square];
            if (Piece.color(piece) != color) {
                continue;
            }
            int type = Math.abs(piece);
            if (type == Piece.KNIGHT) {
                for (int[] step : KNIGHT_STEPS) {
                    int file = Square.file(square) + step[0];
                    int rank = Square.rank(square) + step[1];
                    if (Square.onBoard(file, rank)
                            && Piece.color(board[Square.of(file, rank)]) != color) {
                        count++;
                    }
                }
            } else {
                if (type == Piece.BISHOP || type == Piece.QUEEN) {
                    count += rayMobility(square, color, BISHOP_STEPS);
                }
                if (type == Piece.ROOK || type == Piece.QUEEN) {
                    count += rayMobility(square, color, ROOK_STEPS);
                }
            }
        }
        return count;
    }

    private int rayMobility(int square, int color, int[][] steps) {
        int count = 0;
        for (int[] step : steps) {
            int file = Square.file(square) + step[0];
            int rank = Square.rank(square) + step[1];
            while (Square.onBoard(file, rank)) {
                int piece = board[Square.of(file, rank)];
                if (Piece.color(piece) == color) {
                    break;
                }
                count++;
                if (piece != Piece.EMPTY) {
                    break;
                }
                file += step[0];
                rank += step[1];
            }
        }
        return count;
    }

    /** Conservative dead-position detection; two knights are NOT an automatic draw. */
    public boolean hasInsufficientMaterial() {
        int minors = 0;
        int knights = 0;
        int bishopColor = -1;
        boolean sameColorBishops = true;
        for (int square = 0; square < 64; square++) {
            int type = Math.abs(board[square]);
            if (type == Piece.PAWN || type == Piece.ROOK || type == Piece.QUEEN) {
                return false;
            }
            if (type == Piece.KNIGHT) {
                knights++;
                minors++;
            } else if (type == Piece.BISHOP) {
                int color = (Square.file(square) + Square.rank(square)) % 2;
                if (bishopColor >= 0 && bishopColor != color) {
                    sameColorBishops = false;
                }
                bishopColor = color;
                minors++;
            }
        }
        return minors <= 1 || (knights == 0 && sameColorBishops);
    }

    public String toFen() {
        StringBuilder fen = new StringBuilder();
        for (int rank = 7; rank >= 0; rank--) {
            int empty = 0;
            for (int file = 0; file < 8; file++) {
                int piece = board[Square.of(file, rank)];
                if (piece == Piece.EMPTY) {
                    empty++;
                } else {
                    if (empty > 0) {
                        fen.append(empty);
                        empty = 0;
                    }
                    fen.append(Piece.toFen(piece));
                }
            }
            if (empty > 0) {
                fen.append(empty);
            }
            if (rank > 0) {
                fen.append('/');
            }
        }
        fen.append(sideToMove == Piece.WHITE ? " w " : " b ");
        if (castlingRights == 0) {
            fen.append('-');
        } else {
            if ((castlingRights & WHITE_KING_SIDE) != 0) { fen.append('K'); }
            if ((castlingRights & WHITE_QUEEN_SIDE) != 0) { fen.append('Q'); }
            if ((castlingRights & BLACK_KING_SIDE) != 0) { fen.append('k'); }
            if ((castlingRights & BLACK_QUEEN_SIDE) != 0) { fen.append('q'); }
        }
        fen.append(' ').append(enPassantSquare < 0 ? "-" : Square.name(enPassantSquare));
        return fen.append(' ').append(halfmoveClock).append(' ').append(fullmoveNumber).toString();
    }

    @Override
    public String toString() {
        return toFen();
    }

    /** Everything needed to reverse exactly one move, including rule state. */
    public record Undo(Move move, int movedPiece, int capturedPiece, int capturedSquare,
            int castlingRights, int enPassantSquare, int halfmoveClock, int fullmoveNumber,
            int whiteKing, int blackKing, long key) {
    }
}
