package chess.engine;

import java.util.List;

import chess.model.Move;

/**
 * bestMove is null only for an ended position. Depth zero means the budget
 * expired before an iteration finished; a legal fallback is still supplied.
 */
public record SearchResult(Move bestMove, int score, int completedDepth, long nodes,
        long elapsedMillis, List<Move> principalVariation) {
    public SearchResult {
        principalVariation = List.copyOf(principalVariation);
    }
}
