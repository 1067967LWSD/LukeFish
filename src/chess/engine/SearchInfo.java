package chess.engine;

import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

import chess.model.Move;

/** One immutable message from the search thread to the Swing thread. */
public record SearchInfo(int depth, int score, long nodes, long elapsedMillis,
        List<Move> principalVariation, boolean completedIteration, String label) {
    public SearchInfo {
        principalVariation = List.copyOf(principalVariation);
    }

    public long nodesPerSecond() {
        return nodes * 1_000 / Math.max(1, elapsedMillis);
    }

    public String pvText() {
        return principalVariation.stream().map(Move::toUci).collect(Collectors.joining(" "));
    }

    /** Public scores are labeled with their perspective to avoid sign confusion. */
    public static String formatScore(int whiteScore) {
        if (Math.abs(whiteScore) >= Engine.MATE_THRESHOLD) {
            int moves = (Engine.MATE_SCORE - Math.abs(whiteScore) + 1) / 2;
            return (whiteScore >= 0 ? "+M" : "-M") + moves;
        }
        return String.format(Locale.ROOT, "%+.2f", whiteScore / 100.0);
    }

    public String format(int rootColor) {
        return String.format(Locale.ROOT,
                "d%-2d White %6s | %,d nodes | %,d n/s | %.2fs | %s%n    PV %s",
                depth, formatScore(score * rootColor), nodes, nodesPerSecond(),
                elapsedMillis / 1_000.0, label, pvText());
    }
}
