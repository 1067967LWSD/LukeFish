package chess.engine;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.SplittableRandom;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import chess.engine.TranspositionTable.Bound;
import chess.engine.TranspositionTable.Entry;
import chess.model.Move;
import chess.model.Piece;
import chess.model.Position;

/**
 * Original, classical chess search in readable layers:
 * iterative deepening -> negamax/alpha-beta -> quiescence -> evaluation.
 *
 * Create one Engine per request. It is intentionally not shared by concurrent
 * searches. All fields below are private working memory, never the live game.
 */
public final class Engine {
    public static final int MATE_SCORE = 30_000;
    public static final int MATE_THRESHOLD = 29_000;
    private static final int INFINITY = 32_000;
    private static final int MAX_PLY = 128;

    private Position position;
    private EngineSettings settings;
    private AtomicBoolean stop;
    private Consumer<SearchInfo> listener;
    private TranspositionTable table;
    private long startedNanos;
    private long budgetNanos;
    private long nodes;
    private long historySignature;
    private final Map<Long, Integer> repetitions = new HashMap<>();
    private final Move[][] killers = new Move[MAX_PLY][2];
    private final int[][][] historyHeuristic = new int[2][64][64];
    private final Move[][] pv = new Move[MAX_PLY][MAX_PLY];
    private final int[] pvLength = new int[MAX_PLY];

    public SearchResult search(Position source, EngineSettings settings,
            List<Long> gameHistory, AtomicBoolean stop, Consumer<SearchInfo> listener) {
        this.position = source.copy();
        this.settings = Objects.requireNonNull(settings);
        this.stop = Objects.requireNonNull(stop);
        this.listener = Objects.requireNonNull(listener);
        this.table = settings.transpositionTable() ? new TranspositionTable() : null;
        this.startedNanos = System.nanoTime();
        this.budgetNanos = settings.timeLimitMillis() * 1_000_000L;
        this.nodes = 0;
        this.historySignature = 0;
        repetitions.clear();
        for (Move[] row : killers) {
            java.util.Arrays.fill(row, null);
        }
        for (int[][] color : historyHeuristic) {
            for (int[] row : color) {
                java.util.Arrays.fill(row, 0);
            }
        }
        for (long key : gameHistory) {
            pushHistory(key);
        }
        if (gameHistory.isEmpty() || gameHistory.get(gameHistory.size() - 1) != position.key()) {
            pushHistory(position.key());
        }

        List<Move> rootMoves = position.legalMoves();
        if (rootMoves.isEmpty()) {
            return result(null, position.isInCheck() ? -MATE_SCORE : 0, 0, List.of());
        }
        if (isDraw()) {
            return result(null, 0, 0, List.of());
        }
        orderMoves(rootMoves, null, 0);
        Move bestMove = rootMoves.get(0);
        int bestScore = Evaluator.evaluate(position, settings.weights());
        List<Move> bestLine = List.of(bestMove);
        int completedDepth = 0;

        // Noise is sampled ONCE per root move, never in recursive evaluation.
        // Otherwise alpha-beta and cached scores would disagree with themselves.
        Map<Move, Integer> rootNoise = new HashMap<>();
        SplittableRandom random = new SplittableRandom(position.key() ^ System.nanoTime());
        for (Move move : rootMoves) {
            int noise = settings.randomnessCp() == 0 ? 0
                    : random.nextInt(-settings.randomnessCp(), settings.randomnessCp() + 1);
            rootNoise.put(move, noise);
        }

        for (int depth = 1; depth <= settings.maxDepth(); depth++) {
            try {
                checkStop();
                RootResult iteration = searchRoot(rootMoves, depth, bestMove, rootNoise);
                bestMove = iteration.move();
                bestScore = iteration.score();
                bestLine = iteration.line();
                completedDepth = depth;
                listener.accept(new SearchInfo(depth, bestScore, nodes, elapsedMillis(),
                        bestLine, true, "completed depth"));
                if (Math.abs(bestScore) >= MATE_THRESHOLD) {
                    break;
                }
            } catch (SearchStopped expected) {
                // Only completed iterations replace the result. A timeout
                // halfway through a depth must not discard the last good move.
                break;
            }
        }
        return result(bestMove, bestScore, completedDepth, bestLine);
    }

    private RootResult searchRoot(List<Move> moves, int depth, Move previousBest,
            Map<Move, Integer> rootNoise) {
        orderMoves(moves, previousBest, 0);
        int alpha = -INFINITY;
        int bestRank = -INFINITY - 1_000;
        int bestScore = -INFINITY;
        Move bestMove = moves.get(0);
        List<Move> bestLine = List.of(bestMove);
        boolean first = true;

        for (Move move : moves) {
            checkStop();
            Position.Undo undo = position.makeMove(move);
            pushHistory(position.key());
            int score;
            try {
                // Random selection and verbose comparisons require exact
                // candidate scores, rather than alpha-beta upper bounds.
                if (first || settings.randomnessCp() > 0 || settings.verboseThinking()) {
                    score = -negamax(depth - 1, -INFINITY, INFINITY, 1);
                } else {
                    // Principal variation search: first ask "can this beat
                    // alpha?" with a tiny window, then re-search if it can.
                    score = -negamax(depth - 1, -alpha - 1, -alpha, 1);
                    if (score > alpha) {
                        score = -negamax(depth - 1, -INFINITY, -alpha, 1);
                    }
                }
            } finally {
                // This also runs during cancellation: recursion must always
                // restore its position before returning to its caller.
                popHistory(position.key());
                position.unmakeMove(undo);
            }
            List<Move> line = rootLine(move);
            int rank = score + rootNoise.get(move);
            if (rank > bestRank) {
                bestRank = rank;
                bestScore = score;
                bestMove = move;
                bestLine = line;
            }
            alpha = Math.max(alpha, score);
            first = false;
            if (settings.verboseThinking()) {
                listener.accept(new SearchInfo(depth, score, nodes, elapsedMillis(),
                        line, false, "root " + move.toUci() + " (before noise)"));
            }
        }
        return new RootResult(bestMove, bestScore, bestLine);
    }

    private int negamax(int depth, int alpha, int beta, int ply) {
        if (depth <= 0) {
            return quiescence(alpha, beta, ply, settings.quiescenceDepth());
        }
        visitNode(ply);
        List<Move> moves = position.legalMoves();
        if (moves.isEmpty()) {
            return position.isInCheck() ? -MATE_SCORE + ply : 0;
        }
        if (isDraw()) {
            return 0;
        }
        if (ply >= MAX_PLY - 1) {
            return Evaluator.evaluate(position, settings.weights());
        }

        int originalAlpha = alpha;
        Entry entry = table == null ? null : table.find(position.key());
        Move tableMove = entry == null ? null : entry.bestMove();
        if (entry != null && entry.depth() >= depth
                && entry.halfmoveClock() == position.halfmoveClock()
                && entry.historySignature() == historySignature) {
            int cached = scoreFromTable(entry.score(), ply);
            if (entry.bound() == Bound.EXACT
                    || (entry.bound() == Bound.LOWER && cached >= beta)
                    || (entry.bound() == Bound.UPPER && cached <= alpha)) {
                if (tableMove != null && moves.contains(tableMove)) {
                    pv[ply][ply] = tableMove;
                    pvLength[ply] = ply + 1;
                }
                return cached;
            }
        }
        orderMoves(moves, tableMove, ply);
        int bestScore = -INFINITY;
        Move bestMove = null;
        boolean first = true;
        for (Move move : moves) {
            Position.Undo undo = position.makeMove(move);
            pushHistory(position.key());
            int score;
            try {
                if (first) {
                    score = -negamax(depth - 1, -beta, -alpha, ply + 1);
                } else {
                    score = -negamax(depth - 1, -alpha - 1, -alpha, ply + 1);
                    if (score > alpha && score < beta) {
                        score = -negamax(depth - 1, -beta, -alpha, ply + 1);
                    }
                }
            } finally {
                popHistory(position.key());
                position.unmakeMove(undo);
            }
            first = false;
            if (score > bestScore) {
                bestScore = score;
                bestMove = move;
                savePrincipalVariation(ply, move);
            }
            alpha = Math.max(alpha, score);
            if (alpha >= beta) {
                rememberCutoff(move, depth, ply);
                break; // The opponent already has a better alternative.
            }
        }
        if (table != null) {
            Bound bound = bestScore <= originalAlpha ? Bound.UPPER
                    : bestScore >= beta ? Bound.LOWER : Bound.EXACT;
            table.store(new Entry(position.key(), historySignature, position.halfmoveClock(),
                    depth, scoreToTable(bestScore, ply), bound, bestMove));
        }
        return bestScore;
    }

    /**
     * Avoid stopping the movie in the middle of a capture exchange. Check
     * evasions are compulsory even with quiescence set to zero: standing still
     * in check is not a legal option. Repetition and MAX_PLY bound check cycles.
     */
    private int quiescence(int alpha, int beta, int ply, int remaining) {
        visitNode(ply);
        List<Move> moves = position.legalMoves();
        boolean inCheck = position.isInCheck();
        if (moves.isEmpty()) {
            return inCheck ? -MATE_SCORE + ply : 0;
        }
        if (isDraw()) {
            return 0;
        }
        int standingPat = Evaluator.evaluate(position, settings.weights());
        if (ply >= MAX_PLY - 1) {
            return standingPat;
        }
        int best = -INFINITY;
        if (!inCheck) {
            if (remaining <= 0 || standingPat >= beta) {
                return standingPat;
            }
            best = standingPat;
            alpha = Math.max(alpha, standingPat);
            moves.removeIf(move -> !move.isCapture() && move.promotion() == 0);
        }
        orderMoves(moves, null, ply);
        for (Move move : moves) {
            Position.Undo undo = position.makeMove(move);
            pushHistory(position.key());
            int score;
            try {
                score = -quiescence(-beta, -alpha, ply + 1, remaining - 1);
            } finally {
                popHistory(position.key());
                position.unmakeMove(undo);
            }
            if (score > best) {
                best = score;
                savePrincipalVariation(ply, move);
            }
            alpha = Math.max(alpha, score);
            if (alpha >= beta) {
                break;
            }
        }
        return best;
    }

    private boolean isDraw() {
        return position.halfmoveClock() >= 100 || position.hasInsufficientMaterial()
                || repetitions.getOrDefault(position.key(), 0) >= 3;
    }

    private void pushHistory(long key) {
        repetitions.merge(key, 1, Integer::sum);
        historySignature += mix(key);
    }

    private void popHistory(long key) {
        int count = repetitions.get(key);
        if (count == 1) {
            repetitions.remove(key);
        } else {
            repetitions.put(key, count - 1);
        }
        historySignature -= mix(key);
    }

    private static long mix(long value) {
        value = (value ^ (value >>> 30)) * 0xbf58476d1ce4e5b9L;
        value = (value ^ (value >>> 27)) * 0x94d049bb133111ebL;
        return value ^ (value >>> 31);
    }

    private void visitNode(int ply) {
        pvLength[ply] = ply;
        nodes++;
        if ((nodes & 255) == 0) {
            checkStop();
        }
    }

    private void checkStop() {
        if (stop.get() || Thread.currentThread().isInterrupted()
                || System.nanoTime() - startedNanos >= budgetNanos) {
            throw new SearchStopped();
        }
    }

    private void orderMoves(List<Move> moves, Move preferred, int ply) {
        if (settings.moveOrdering()) {
            moves.sort(Comparator.comparingInt((Move move) -> orderingScore(move, preferred, ply)).reversed());
        }
    }

    private int orderingScore(Move move, Move preferred, int ply) {
        if (move.equals(preferred)) {
            return 2_000_000;
        }
        if (move.promotion() != 0) {
            return 1_000_000 + Evaluator.pieceValue(move.promotion());
        }
        if (move.isCapture()) {
            int victim = move.isEnPassant() ? Piece.PAWN : Math.abs(position.pieceAt(move.to()));
            int attacker = Math.abs(position.pieceAt(move.from()));
            return 100_000 + 16 * Evaluator.pieceValue(victim) - Evaluator.pieceValue(attacker);
        }
        if (move.equals(killers[ply][0])) {
            return 90_000;
        }
        if (move.equals(killers[ply][1])) {
            return 89_000;
        }
        int colorIndex = position.sideToMove() == Piece.WHITE ? 0 : 1;
        return historyHeuristic[colorIndex][move.from()][move.to()];
    }

    private void rememberCutoff(Move move, int depth, int ply) {
        if (settings.moveOrdering() && !move.isCapture() && move.promotion() == 0) {
            if (!move.equals(killers[ply][0])) {
                killers[ply][1] = killers[ply][0];
                killers[ply][0] = move;
            }
            int colorIndex = position.sideToMove() == Piece.WHITE ? 0 : 1;
            int old = historyHeuristic[colorIndex][move.from()][move.to()];
            historyHeuristic[colorIndex][move.from()][move.to()] = Math.min(80_000, old + depth * depth);
        }
    }

    private void savePrincipalVariation(int ply, Move move) {
        pv[ply][ply] = move;
        int childLength = pvLength[ply + 1];
        System.arraycopy(pv[ply + 1], ply + 1, pv[ply], ply + 1, childLength - ply - 1);
        pvLength[ply] = childLength;
    }

    private List<Move> rootLine(Move rootMove) {
        List<Move> line = new ArrayList<>();
        line.add(rootMove);
        for (int ply = 1; ply < pvLength[1]; ply++) {
            line.add(pv[1][ply]);
        }
        return List.copyOf(line);
    }

    private static int scoreToTable(int score, int ply) {
        if (score >= MATE_THRESHOLD) { return score + ply; }
        if (score <= -MATE_THRESHOLD) { return score - ply; }
        return score;
    }

    private static int scoreFromTable(int score, int ply) {
        if (score >= MATE_THRESHOLD) { return score - ply; }
        if (score <= -MATE_THRESHOLD) { return score + ply; }
        return score;
    }

    private long elapsedMillis() {
        return (System.nanoTime() - startedNanos) / 1_000_000;
    }

    private SearchResult result(Move move, int score, int depth, List<Move> line) {
        return new SearchResult(move, score, depth, nodes, elapsedMillis(), line);
    }

    private record RootResult(Move move, int score, List<Move> line) {
    }

    private static final class SearchStopped extends RuntimeException {
        private static final long serialVersionUID = 1L;

        SearchStopped() {
            super(null, null, false, false);
        }
    }
}
