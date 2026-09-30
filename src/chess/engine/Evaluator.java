package chess.engine;

import java.util.Locale;

import chess.engine.EngineSettings.EvaluationWeights;
import chess.model.Piece;
import chess.model.Position;
import chess.model.Square;

/**
 * A deliberately explainable evaluation, not a neural network. Scores use
 * centipawns: 100 means roughly one pawn. These are preferences, not proof!
 *
 * Middlegame and endgame terms blend gradually as heavy pieces disappear.
 * In particular, hiding a king is good early; centralizing it is good late.
 */
public final class Evaluator {
    private static final int[] MID_VALUE = {0, 100, 320, 335, 500, 900, 0};
    private static final int[] END_VALUE = {0, 120, 305, 330, 520, 900, 0};
    private static final int[] PHASE_VALUE = {0, 0, 1, 1, 2, 4, 0};
    private static final int MAX_PHASE = 24;

    private Evaluator() {
    }

    public static int pieceValue(int type) {
        return MID_VALUE[type];
    }

    /** Search always wants the score from the side-to-move's perspective. */
    public static int evaluate(Position position, EvaluationWeights weights) {
        int score = explain(position, weights).total() * position.sideToMove();
        // Keep even artificial FEN positions outside the special mate range.
        return Math.max(-20_000, Math.min(20_000, score));
    }

    /** The UI uses exactly the same components as the search, in White's favor. */
    public static Breakdown explain(Position position, EvaluationWeights weights) {
        int materialMid = 0;
        int materialEnd = 0;
        int placementMid = 0;
        int placementEnd = 0;
        int phase = 0;
        int[][] pawnsByFile = new int[2][8];
        int[] bishops = new int[2];

        for (int square = 0; square < 64; square++) {
            int piece = position.pieceAt(square);
            if (piece == Piece.EMPTY) {
                continue;
            }
            int color = Piece.color(piece);
            int type = Math.abs(piece);
            int colorIndex = color == Piece.WHITE ? 0 : 1;
            int file = Square.file(square);
            int rank = color == Piece.WHITE ? Square.rank(square) : 7 - Square.rank(square);
            int center = 14 - Math.abs(2 * file - 7) - Math.abs(2 * rank - 7);

            materialMid += color * MID_VALUE[type];
            materialEnd += color * END_VALUE[type];
            phase += PHASE_VALUE[type];
            placementMid += color * placement(type, file, rank, center, false);
            placementEnd += color * placement(type, file, rank, center, true);
            if (type == Piece.PAWN) {
                pawnsByFile[colorIndex][file]++;
            } else if (type == Piece.BISHOP) {
                bishops[colorIndex]++;
            }
        }
        phase = Math.min(MAX_PHASE, phase);
        int bishopPair = (bishops[0] >= 2 ? 1 : 0) - (bishops[1] >= 2 ? 1 : 0);
        placementMid += 30 * bishopPair;
        placementEnd += 45 * bishopPair;

        int material = blend(materialMid, materialEnd, phase) * weights.materialPercent() / 100;
        int placement = blend(placementMid, placementEnd, phase) * weights.placementPercent() / 100;
        int mobility = weights.mobilityCp() == 0 ? 0
                : (position.mobility(Piece.WHITE) - position.mobility(Piece.BLACK)) * weights.mobilityCp();
        int pawns = weights.pawnStructurePercent() == 0 ? 0
                : (pawnScore(position, Piece.WHITE, pawnsByFile[0], phase)
                    - pawnScore(position, Piece.BLACK, pawnsByFile[1], phase))
                    * weights.pawnStructurePercent() / 100;
        int kings = weights.kingSafetyPercent() == 0 ? 0
                : (kingSafety(position, Piece.WHITE, pawnsByFile[0])
                    - kingSafety(position, Piece.BLACK, pawnsByFile[1]))
                    * phase / MAX_PHASE * weights.kingSafetyPercent() / 100;
        int tempo = 10 * position.sideToMove();
        return new Breakdown(material, placement, mobility, pawns, kings, tempo);
    }

    private static int blend(int mid, int end, int phase) {
        return (mid * phase + end * (MAX_PHASE - phase)) / MAX_PHASE;
    }

    private static int placement(int type, int file, int rank, int center, boolean endgame) {
        // Formulas are used instead of 64-entry tables so each preference is
        // easy to change and explain. rank is measured from the piece's home.
        return switch (type) {
            case Piece.PAWN -> rank * (endgame ? 12 : 7) + center * 2;
            case Piece.KNIGHT -> center * 6 - 30 - (rank == 0 ? 12 : 0);
            case Piece.BISHOP -> center * 4 + rank * 2 - 20;
            case Piece.ROOK -> rank * 2 + (rank == 6 ? 22 : 0);
            case Piece.QUEEN -> center * 2 - 12 - (!endgame && rank > 2 ? 10 : 0);
            case Piece.KING -> endgame ? center * 7
                    : -center * 6 - rank * 8 + (rank == 0 && (file == 2 || file == 6) ? 35 : 0);
            default -> throw new IllegalArgumentException("Cannot evaluate an unknown piece.");
        };
    }

    private static int pawnScore(Position position, int color, int[] files, int phase) {
        int score = 0;
        for (int file = 0; file < 8; file++) {
            if (files[file] > 1) {
                score -= 14 * (files[file] - 1);
            }
            boolean isolated = (file == 0 || files[file - 1] == 0)
                    && (file == 7 || files[file + 1] == 0);
            if (isolated) {
                score -= 16 * files[file];
            }
        }
        for (int square = 0; square < 64; square++) {
            if (position.pieceAt(square) != color * Piece.PAWN) {
                continue;
            }
            int file = Square.file(square);
            int rank = Square.rank(square);
            boolean passed = true;
            for (int otherFile = Math.max(0, file - 1); otherFile <= Math.min(7, file + 1); otherFile++) {
                for (int ahead = rank + color; ahead >= 0 && ahead < 8; ahead += color) {
                    if (position.pieceAt(Square.of(otherFile, ahead)) == -color * Piece.PAWN) {
                        passed = false;
                    }
                }
            }
            if (passed) {
                int advance = color == Piece.WHITE ? rank : 7 - rank;
                score += blend(advance * advance * 2, advance * advance * 5, phase);
            }
        }
        return score;
    }

    private static int kingSafety(Position position, int color, int[] pawnFiles) {
        int king = position.kingSquare(color);
        int file = Square.file(king);
        int rank = Square.rank(king);
        int score = 0;
        for (int nearbyFile = Math.max(0, file - 1); nearbyFile <= Math.min(7, file + 1); nearbyFile++) {
            if (pawnFiles[nearbyFile] == 0) {
                score -= 18;
            }
            for (int distance = 1; distance <= 2; distance++) {
                int shieldRank = rank + color * distance;
                if (Square.onBoard(nearbyFile, shieldRank)
                        && position.pieceAt(Square.of(nearbyFile, shieldRank)) == color * Piece.PAWN) {
                    score += distance == 1 ? 14 : 6;
                }
            }
            for (int nearbyRank = Math.max(0, rank - 1); nearbyRank <= Math.min(7, rank + 1); nearbyRank++) {
                if (position.isAttacked(Square.of(nearbyFile, nearbyRank), -color)) {
                    score -= 12;
                }
            }
        }
        return score;
    }

    public record Breakdown(int material, int placement, int mobility,
            int pawnStructure, int kingSafety, int tempo) {
        public int total() {
            return material + placement + mobility + pawnStructure + kingSafety + tempo;
        }

        @Override
        public String toString() {
            return String.format(Locale.ROOT,
                    "White cp: material %+d | placement %+d | mobility %+d | pawns %+d | king %+d | tempo %+d = %+d",
                    material, placement, mobility, pawnStructure, kingSafety, tempo, total());
        }
    }
}
