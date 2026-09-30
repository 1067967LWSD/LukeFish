package chess;

import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import javax.swing.SwingUtilities;
import javax.swing.Timer;

import chess.engine.Engine;
import chess.engine.EngineSettings;
import chess.engine.Evaluator;
import chess.engine.SearchInfo;
import chess.engine.SearchResult;
import chess.model.Game;
import chess.model.Move;
import chess.model.Piece;
import chess.model.Position;
import chess.model.Square;
import chess.ui.BoardPanel;

/**
 * No test-library installation is needed: run this class as a Java application.
 * Each check throws AssertionError on failure, even when JVM assertions are off.
 */
public final class ChessTests {
    private static final String KIWIPETE =
            "r3k2r/p1ppqpb1/bn2pnp1/3PN3/1p2P3/2N2Q1p/PPPBBPPP/R3K2R w KQkq - 0 1";
    private static int groups;
    private static long checks;

    private ChessTests() {
    }

    public static void main(String[] args) throws Exception {
        run("Coordinates and input validation", ChessTests::coordinatesAndFen);
        run("Starting position perft through depth 4", ChessTests::startingPerft);
        run("Reference positions: castling, pins, en passant, promotions", ChessTests::referencePerft);
        run("Make/unmake and incremental hashes", ChessTests::reversibleMoves);
        run("Special move rules", ChessTests::specialMoves);
        run("Game results, notation, and undo", ChessTests::gameResults);
        run("Draw rules and repetition identity", ChessTests::drawRules);
        run("Evaluation controls", ChessTests::evaluation);
        run("Mate and tactics with search switches", ChessTests::tacticalSearch);
        run("Search consistency and legal thinking lines", ChessTests::searchConsistency);
        run("Alpha-beta agrees with exhaustive reference search", ChessTests::referenceSearch);
        run("Verbose scores and bounded root randomness", ChessTests::rootRandomness);
        run("Search draw handling", ChessTests::searchDraws);
        run("Engine self-play legality and thinking lines", ChessTests::selfPlay);
        run("Deadlines and cross-thread cancellation", ChessTests::cancellation);
        run("Board geometry, mouse, keyboard, and offscreen painting", ChessTests::boardInput);
        run("Move deltas, non-blocking flashes, and independent expiry", ChessTests::moveFeedback);
        System.out.println("PASS: " + groups + " groups, " + checks + " checks.");
    }

    private static void coordinatesAndFen() {
        for (int square = 0; square < 64; square++) {
            equal(square, Square.parse(Square.name(square)), "Coordinate round trip");
        }
        equal(Position.START_FEN, Position.initial().toFen(), "Starting FEN");
        equal(KIWIPETE, Position.fromFen(KIWIPETE).toFen(), "Kiwipete FEN");
        expectIllegal(() -> Square.parse("i4"));
        expectIllegal(() -> Square.parse("e9"));
        expectIllegal(() -> new Move(0, 0, 0, 0));
        expectIllegal(() -> new Move(0, 8, Piece.KING, 0));
        expectIllegal(() -> Position.fromFen("8/8/8/8/8/8/8/8 w - - 0 1"));
        expectIllegal(() -> Position.fromFen("4k3/8/8/8/8/8/4K3/4K3 w - - 0 1"));
        expectIllegal(() -> Position.fromFen("8/8/8/8/8/8/4k3/4K3 w - - 0 1"));
        expectIllegal(() -> Position.fromFen("4k3/8/8/8/8/8/8/4K3 x - - 0 1"));
        expectIllegal(() -> Position.fromFen("4k3/8/8/8/8/8/8/4K3 w K - 0 1"));
        expectIllegal(() -> Position.fromFen("4k3/8/8/8/8/8/8/4K3 w - e6 0 1"));
        expectIllegal(() -> Position.fromFen("4k3/8/8/8/8/8/8/4K3 w - - -1 1"));
        expectIllegal(() -> Position.fromFen("P3k3/8/8/8/8/8/8/4K3 w - - 0 1"));
        expectIllegal(() -> Position.initial().requireLegalMove("e2e5"));
    }

    private static void startingPerft() {
        Position position = Position.initial();
        equal(20, perft(position, 1), "Start depth 1");
        equal(400, perft(position, 2), "Start depth 2");
        equal(8_902, perft(position, 3), "Start depth 3");
        equal(197_281, perft(position, 4), "Start depth 4");
        equal(Position.START_FEN, position.toFen(), "Perft restores the board");
    }

    private static void referencePerft() {
        checkPerft(KIWIPETE, 48, 2_039, 97_862);
        checkPerft("8/2p5/3p4/KP5r/1R3p1k/8/4P1P1/8 w - - 0 1", 14, 191, 2_812, 43_238);
        checkPerft("r3k2r/Pppp1ppp/1b3nbN/nP6/BBP1P3/q4N2/Pp1P2PP/R2Q1RK1 w kq - 0 1",
                6, 264, 9_467);
        checkPerft("rnbq1k1r/pp1Pbppp/2p5/8/2B5/8/PPP1NnPP/RNBQK2R w KQ - 1 8",
                44, 1_486, 62_379);
    }

    private static void checkPerft(String fen, long... counts) {
        Position position = Position.fromFen(fen);
        for (int depth = 1; depth <= counts.length; depth++) {
            equal(counts[depth - 1], perft(position, depth), "Perft depth " + depth + " for " + fen);
        }
    }

    /**
     * "Perft" counts every legal sequence of a given length, WITHOUT search
     * pruning or draw adjudication. Published counts catch subtle rule bugs.
     */
    public static long perft(Position position, int depth) {
        if (depth == 0) {
            return 1;
        }
        List<Move> moves = position.legalMoves();
        if (depth == 1) {
            return moves.size();
        }
        long nodes = 0;
        for (Move move : moves) {
            Position.Undo undo = position.makeMove(move);
            nodes += perft(position, depth - 1);
            position.unmakeMove(undo);
        }
        return nodes;
    }

    private static void reversibleMoves() {
        verifyRestoration(Position.fromFen(KIWIPETE), 2);
        verifyRestoration(Position.fromFen("4k3/8/8/3pP3/8/8/8/4K3 w - d6 0 2"), 2);
        verifyRestoration(Position.fromFen("1r5k/P7/8/8/8/8/8/7K w - - 0 1"), 2);
    }

    private static void verifyRestoration(Position position, int depth) {
        String before = position.toFen();
        long key = position.key();
        List<Move> moves = position.legalMoves();
        equal(before, position.toFen(), "Move generation leaves FEN unchanged");
        equal(key, position.key(), "Move generation leaves key unchanged");
        for (Move move : moves) {
            Position.Undo undo = position.makeMove(move);
            equal(Position.fromFen(position.toFen()).key(), position.key(), "Incremental hash for " + move);
            equal(position.toFen(), position.copy().toFen(), "Position copy");
            if (depth > 1) {
                verifyRestoration(position, depth - 1);
            }
            position.unmakeMove(undo);
            equal(before, position.toFen(), "Undo FEN for " + move);
            equal(key, position.key(), "Undo key for " + move);
        }
    }

    private static void specialMoves() {
        Position castle = Position.fromFen("r3k2r/8/8/8/8/8/8/R3K2R w KQkq - 0 1");
        Move kingSide = castle.requireLegalMove("e1g1");
        check(kingSide.isCastle(), "Castling flag");
        Position.Undo undo = castle.makeMove(kingSide);
        equal(Piece.KING, castle.pieceAt(Square.parse("g1")), "Castled king");
        equal(Piece.ROOK, castle.pieceAt(Square.parse("f1")), "Castled rook");
        check(castle.toFen().contains(" b kq "), "White loses both castling rights");
        castle.unmakeMove(undo);
        castle.requireLegalMove("e1c1");
        Position attacked = Position.fromFen("4k3/8/8/8/2b5/8/8/4K2R w K - 0 1");
        expectIllegal(() -> attacked.requireLegalMove("e1g1"));

        Position enPassant = Position.fromFen("4k3/8/8/3pP3/8/8/8/4K3 w - d6 0 2");
        Move capture = enPassant.requireLegalMove("e5d6");
        check(capture.isEnPassant(), "En passant flag");
        enPassant.makeMove(capture);
        equal(0, enPassant.pieceAt(Square.parse("d5")), "En passant removes the bypassed pawn");
        equal(Piece.PAWN, enPassant.pieceAt(Square.parse("d6")), "En passant destination");
        Position pinned = Position.fromFen("k3r3/8/8/3pP3/8/8/8/4K3 w - d6 0 1");
        expectIllegal(() -> pinned.requireLegalMove("e5d6"));

        Position promotion = Position.fromFen("1r5k/P7/8/8/8/8/8/7K w - - 0 1");
        equal(8, promotion.legalMoves().stream().filter(move -> move.promotion() != 0).count(),
                "Four quiet and four capturing promotions");
        promotion.makeMove(promotion.requireLegalMove("a7b8n"));
        equal(Piece.KNIGHT, promotion.pieceAt(Square.parse("b8")), "Underpromotion");

        Position rookCapture = Position.fromFen("r3k3/1Q6/8/8/8/8/8/4K3 w q - 0 1");
        rookCapture.makeMove(rookCapture.requireLegalMove("b7a8"));
        check(rookCapture.toFen().contains(" b - "), "Capturing a rook removes its castling right");
    }

    private static void gameResults() {
        Game game = new Game();
        play(game, "f2f3", "e7e5", "g2g4", "d8h4");
        equal(Game.EndReason.CHECKMATE, game.outcome().reason(), "Fool's mate");
        equal(Piece.BLACK, game.outcome().winner(), "Black wins");
        equal("Qh4#", game.moves().get(3).notation(), "Mate notation");
        game.undo();
        check(!game.outcome().isOver(), "Undo reopens the game");
        equal(3, game.moves().size(), "Undo removes move history");

        Game stalemate = new Game(Position.fromFen("7k/5K2/6Q1/8/8/8/8/8 b - - 0 1"));
        equal(Game.EndReason.STALEMATE, stalemate.outcome().reason(), "Stalemate");
        Game mateAt100 = new Game(Position.fromFen("7k/6Q1/5K2/8/8/8/8/8 b - - 100 1"));
        equal(Game.EndReason.CHECKMATE, mateAt100.outcome().reason(), "Mate takes priority over the draw clock");

        Game ambiguous = new Game(Position.fromFen("4k3/8/8/8/8/8/8/1N2KN2 w - - 0 1"));
        play(ambiguous, "b1d2");
        equal("Nbd2", ambiguous.moves().get(0).notation(), "SAN file disambiguation");
        Game promotion = new Game(Position.fromFen("7k/P7/8/8/8/8/8/7K w - - 0 1"));
        play(promotion, "a7a8q");
        equal("a8=Q+", promotion.moves().get(0).notation(), "Promotion with check");
    }

    private static void drawRules() {
        Game repeat = new Game();
        play(repeat, "g1f3", "g8f6", "f3g1", "f6g8", "g1f3", "g8f6", "f3g1", "f6g8");
        equal(Game.EndReason.THREEFOLD_REPETITION, repeat.outcome().reason(), "Third occurrence is a draw");
        repeat.undo();
        check(!repeat.outcome().isOver(), "Undo also removes repetition history");
        equal(8, repeat.positionKeys().size(), "History includes the initial position");

        Game fifty = new Game(Position.fromFen("7k/8/8/8/8/8/8/R5K1 w - - 99 1"));
        play(fifty, "a1a2");
        equal(Game.EndReason.FIFTY_MOVE_RULE, fifty.outcome().reason(), "100 reversible halfmoves");

        check(Position.fromFen("k7/8/8/8/8/8/8/7K w - - 0 1").hasInsufficientMaterial(), "Bare kings");
        check(Position.fromFen("k7/8/8/8/8/8/8/2B4K w - - 0 1").hasInsufficientMaterial(), "Single bishop");
        check(Position.fromFen("k4b2/8/8/8/8/8/8/2B4K w - - 0 1").hasInsufficientMaterial(),
                "Same-color bishops");
        check(!Position.fromFen("k1b5/8/8/8/8/8/8/2B4K w - - 0 1").hasInsufficientMaterial(),
                "Opposite-color bishops are not automatically dead");
        check(!Position.fromFen("k7/8/8/8/8/8/8/1NN4K w - - 0 1").hasInsufficientMaterial(),
                "Two knights can produce a mate even if they cannot force one");

        String pinned = "k3r3/8/8/3pP3/8/8/8/4K3 w - d6 0 1";
        equal(Position.fromFen(pinned).key(), Position.fromFen(pinned.replace("d6", "-")).key(),
                "Illegal en passant does not change repetition identity");
        String available = "k7/8/8/3pP3/8/8/8/4K3 w - d6 0 1";
        check(Position.fromFen(available).key() != Position.fromFen(available.replace("d6", "-")).key(),
                "Legal en passant changes repetition identity");
        equal(Position.initial().key(), Position.fromFen(Position.START_FEN.replace("0 1", "8 5")).key(),
                "Move counters are excluded from repetition identity");
    }

    private static void evaluation() {
        EngineSettings.EvaluationWeights weights = EngineSettings.EvaluationWeights.defaults();
        equal(10, Evaluator.evaluate(Position.initial(), weights), "Symmetric board plus tempo");
        Position black = Position.fromFen(Position.START_FEN.replace(" w ", " b "));
        equal(10, Evaluator.evaluate(black, weights), "Search perspective follows the side to move");
        Position extraQueen = Position.fromFen("rnb1kbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1");
        check(Evaluator.evaluate(extraQueen, weights) > 800, "A queen advantage should matter");
        EngineSettings.EvaluationWeights disabled = new EngineSettings.EvaluationWeights(0, 0, 0, 0, 0);
        equal(10, Evaluator.evaluate(extraQueen, disabled), "Every weight is wired into evaluation");
        equal(Evaluator.explain(extraQueen, weights).total(), Evaluator.evaluate(extraQueen, weights),
                "Debug breakdown is the actual evaluation");
        expectIllegal(() -> new EngineSettings(0, 1_000, 2, 0, true, true, false, weights));
        expectIllegal(() -> new EngineSettings.EvaluationWeights(-1, 100, 3, 100, 100));

        EngineSettings.EvaluationWeights[] experiments = {
            new EngineSettings.EvaluationWeights(0, 100, 3, 100, 100),
            new EngineSettings.EvaluationWeights(100, 0, 3, 100, 100),
            new EngineSettings.EvaluationWeights(100, 100, 0, 100, 100),
            new EngineSettings.EvaluationWeights(100, 100, 3, 0, 100),
            new EngineSettings.EvaluationWeights(100, 100, 3, 100, 0)
        };
        boolean[] exercised = new boolean[5];
        for (String fen : new String[] {KIWIPETE,
                "r3k2r/3p1pb1/bn2pnp1/3PN3/1p2P3/2N2Q1p/PPPBBPPP/R3K2R w KQkq - 0 1"}) {
            Position sample = Position.fromFen(fen);
            Evaluator.Breakdown base = Evaluator.explain(sample, weights);
            int[] components = {base.material(), base.placement(), base.mobility(), base.pawnStructure(), base.kingSafety()};
            for (int index = 0; index < experiments.length; index++) {
                Evaluator.Breakdown adjusted = Evaluator.explain(sample, experiments[index]);
                equal(base.total() - components[index], adjusted.total(), "Disable exactly one evaluation term");
                exercised[index] |= components[index] != 0;
            }
        }
        for (boolean changed : exercised) {
            check(changed, "Every evaluation control changes a nonzero component in a sample");
        }
    }

    private static void tacticalSearch() {
        for (boolean table : new boolean[] {false, true}) {
            for (boolean ordering : new boolean[] {false, true}) {
                for (String fen : new String[] {
                        "7k/5Q2/6K1/8/8/8/8/8 w - - 0 1",
                        "8/8/8/8/8/6k1/5q2/7K b - - 0 1"}) {
                    Position position = Position.fromFen(fen);
                    EngineSettings settings = settings(3, 5_000, 2, table, ordering);
                    SearchResult result = search(position, settings);
                    check(result.score() >= Engine.MATE_SCORE - 1, "Engine sees mate in one");
                    Game game = new Game(position);
                    game.play(result.bestMove());
                    equal(Game.EndReason.CHECKMATE, game.outcome().reason(), "Chosen move really mates");
                }
            }
        }
        Position captureQueen = Position.fromFen("4k3/8/8/4q3/4R3/8/8/4K3 w - - 0 1");
        SearchResult result = search(captureQueen, settings(3, 5_000, 4, true, true));
        equal("e4e5", result.bestMove().toUci(), "Do not leave a free queen");
    }

    private static void searchConsistency() {
        Position position = Position.initial();
        List<SearchInfo> thinking = new ArrayList<>();
        EngineSettings settings = settings(3, 20_000, 2, true, true);
        SearchResult cached = new Engine().search(position, settings, List.of(position.key()),
                new AtomicBoolean(), thinking::add);
        SearchResult uncached = search(position, settings(3, 20_000, 2, false, true));
        SearchResult unordered = search(position, settings(3, 20_000, 2, false, false));
        equal(3, cached.completedDepth(), "Complete the requested depth");
        equal(cached.score(), uncached.score(), "Cache does not change the answer");
        equal(cached.score(), unordered.score(), "Ordering does not change the answer");
        equal(3, thinking.size(), "One thinking report per completed depth");
        equal(Position.START_FEN, position.toFen(), "Engine does not mutate its caller");
        for (SearchInfo info : thinking) {
            Position line = position.copy();
            check(!info.principalVariation().isEmpty(), "Thinking includes a line");
            for (Move move : info.principalVariation()) {
                check(line.legalMoves().contains(move), "Every PV move is legal");
                line.makeMove(move);
            }
        }
        check(cached.nodes() > 0, "Nodes are reported");
        equal("-M2", SearchInfo.formatScore(-Engine.MATE_SCORE + 3), "Mate score formatting");
    }

    private static void searchDraws() {
        Game repeated = new Game();
        play(repeated, "g1f3", "g8f6", "f3g1", "f6g8", "g1f3", "g8f6", "f3g1", "f6g8");
        SearchResult draw = new Engine().search(repeated.position(), settings(2, 1_000, 2, true, true),
                repeated.positionKeys(), new AtomicBoolean(), info -> { });
        check(draw.bestMove() == null && draw.score() == 0, "History draw at search root");
        SearchResult stale = search(Position.fromFen("7k/5K2/6Q1/8/8/8/8/8 b - - 0 1"),
                settings(2, 1_000, 2, true, true));
        check(stale.bestMove() == null && stale.score() == 0, "No move in stalemate");
        SearchResult mate = search(Position.fromFen("7k/5Q2/6K1/8/8/8/8/8 w - - 99 1"),
                settings(2, 5_000, 0, true, true));
        equal(Engine.MATE_SCORE - 1, mate.score(), "Mate on move 100 is not incorrectly scored as a draw");
    }

    private static void referenceSearch() {
        for (String fen : new String[] {Position.START_FEN, KIWIPETE,
                "8/2p5/3p4/KP5r/1R3p1k/8/4P1P1/8 w - - 0 1",
                "1r5k/P7/8/8/8/8/8/7K w - - 0 1"}) {
            Position position = Position.fromFen(fen);
            List<Long> history = new ArrayList<>(List.of(position.key()));
            int expected = exhaustive(position, history, 2, 0);
            for (boolean table : new boolean[] {false, true}) {
                SearchResult result = search(position, settings(2, 20_000, 0, table, true));
                check(result.completedDepth() == 2 || Math.abs(result.score()) >= Engine.MATE_THRESHOLD,
                        "Reference comparison must complete its depth, unless mate ends it early");
                equal(expected, result.score(), "Optimized search matches exhaustive negamax for " + fen);
            }
        }
    }

    /** A slow, independent oracle: no pruning, ordering, cache, or iterative deepening. */
    private static int exhaustive(Position position, List<Long> history, int depth, int ply) {
        List<Move> moves = position.legalMoves();
        if (moves.isEmpty()) {
            return position.isInCheck() ? -Engine.MATE_SCORE + ply : 0;
        }
        if (position.hasInsufficientMaterial() || position.halfmoveClock() >= 100
                || history.stream().filter(key -> key == position.key()).count() >= 3) {
            return 0;
        }
        if ((depth <= 0 && !position.isInCheck()) || ply >= 127) {
            return Evaluator.evaluate(position, EngineSettings.EvaluationWeights.defaults());
        }
        int best = -32_000;
        for (Move move : moves) {
            Position.Undo undo = position.makeMove(move);
            history.add(position.key());
            int score = -exhaustive(position, history, depth - 1, ply + 1);
            history.remove(history.size() - 1);
            position.unmakeMove(undo);
            best = Math.max(best, score);
        }
        return best;
    }

    private static void rootRandomness() {
        Position position = Position.initial();
        List<SearchInfo> candidates = new ArrayList<>();
        EngineSettings noisy = new EngineSettings(2, 10_000, 2, 60, true, true,
                true, EngineSettings.EvaluationWeights.defaults());
        SearchResult result = new Engine().search(position, noisy, List.of(position.key()),
                new AtomicBoolean(), candidates::add);
        equal(2, result.completedDepth(), "Finish noisy comparison depth");
        List<SearchInfo> lastDepth = candidates.stream()
                .filter(info -> info.depth() == 2 && !info.completedIteration()).toList();
        equal(position.legalMoves().size(), lastDepth.size(), "Verbose output scores every root candidate");
        int bestActual = lastDepth.stream().mapToInt(SearchInfo::score).max().orElseThrow();
        SearchInfo chosen = lastDepth.stream()
                .filter(info -> info.principalVariation().get(0).equals(result.bestMove())).findFirst().orElseThrow();
        equal(chosen.score(), result.score(), "Displayed score excludes selection noise");
        check(result.score() >= bestActual - 2 * noisy.randomnessCp(), "Noise cannot select outside its stated score window");
    }

    private static void selfPlay() {
        Game game = new Game();
        EngineSettings settings = settings(4, 100, 3, true, true);
        for (int ply = 0; ply < 40 && !game.outcome().isOver(); ply++) {
            String before = game.position().toFen();
            SearchResult result = new Engine().search(game.position(), settings, game.positionKeys(),
                    new AtomicBoolean(), info -> { });
            equal(before, game.position().toFen(), "Self-play search uses an isolated board");
            check(game.position().legalMoves().contains(result.bestMove()), "Self-play move is legal");
            Position line = game.position().copy();
            for (Move move : result.principalVariation()) {
                check(line.legalMoves().contains(move), "Self-play PV remains legal");
                line.makeMove(move);
            }
            game.play(result.bestMove());
            check(!game.position().isInCheck(-game.position().sideToMove()), "Moving side never leaves its king in check");
        }
        check(!game.moves().isEmpty(), "Engine-vs-engine play makes progress");
    }

    private static void cancellation() throws Exception {
        Position position = Position.initial();
        SearchResult immediate = new Engine().search(position, EngineSettings.defaults(),
                List.of(position.key()), new AtomicBoolean(true), info -> { });
        equal(0, immediate.completedDepth(), "Pre-cancelled search");
        check(position.legalMoves().contains(immediate.bestMove()), "Cancellation still supplies a legal fallback");

        long start = System.nanoTime();
        SearchResult timed = search(position, settings(12, 100, 8, true, true));
        long elapsed = (System.nanoTime() - start) / 1_000_000;
        check(elapsed < 2_000, "A 100ms budget should return promptly, not search to depth 12");
        check(position.legalMoves().contains(timed.bestMove()), "Timed result is legal");

        AtomicBoolean stop = new AtomicBoolean();
        CountDownLatch firstDepth = new CountDownLatch(1);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<SearchResult> future = executor.submit(() -> new Engine().search(position,
                    settings(12, 30_000, 8, true, true), List.of(position.key()), stop,
                    info -> firstDepth.countDown()));
            check(firstDepth.await(5, TimeUnit.SECONDS), "Search begins in background");
            stop.set(true);
            SearchResult result = future.get(3, TimeUnit.SECONDS);
            check(position.legalMoves().contains(result.bestMove()), "Cross-thread stop returns a legal move");
            equal(Position.START_FEN, position.toFen(), "Cancelled search leaves live state untouched");
        } finally {
            stop.set(true);
            executor.shutdownNow();
        }
    }

    private static void boardInput() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            List<String> requested = new ArrayList<>();
            BoardPanel board = new BoardPanel((from, to) -> requested.add(Square.name(from) + Square.name(to)));
            board.setSize(610, 550);
            Position position = Position.initial();
            board.setGameState(position, position.legalMoves(), null, true);
            for (boolean flipped : new boolean[] {false, true}) {
                board.setFlipped(flipped);
                for (int square = 0; square < 64; square++) {
                    Rectangle bounds = board.squareBounds(square);
                    equal(square, board.squareAt(new Point((int) bounds.getCenterX(), (int) bounds.getCenterY())),
                            "Mouse/paint coordinate agreement");
                }
                equal(-1, board.squareAt(new Point(0, 0)), "Margin is not a chess square");
            }
            board.setFlipped(false);
            click(board, "e2");
            click(board, "e4");
            equal(List.of("e2e4"), requested, "Mouse request");
            click(board, "g1");
            invokeBoardAction(board, "cursor-up");
            invokeBoardAction(board, "cursor-up");
            invokeBoardAction(board, "cursor-left");
            invokeBoardAction(board, "choose-square");
            equal("g1f3", requested.get(1), "Arrow/Enter request");
            board.setFlipped(true);
            click(board, "d2");
            click(board, "d4");
            equal("d2d4", requested.get(2), "Mouse request on a rotated board");
            board.setGameState(position, position.legalMoves(), null, false);
            click(board, "e2");
            click(board, "e4");
            equal(3, requested.size(), "Disabled input cannot submit a move");

            BufferedImage image = new BufferedImage(610, 550, BufferedImage.TYPE_INT_ARGB);
            Graphics2D graphics = image.createGraphics();
            try {
                board.paint(graphics);
            } finally {
                graphics.dispose();
            }
            Rectangle dark = board.squareBounds(Square.parse("d4"));
            Rectangle light = board.squareBounds(Square.parse("e4"));
            check(image.getRGB(dark.x + 4, dark.y + 4) != image.getRGB(light.x + 4, light.y + 4),
                    "Painting produces alternating board colors");
        });
    }

    private static void click(BoardPanel board, String square) {
        Rectangle bounds = board.squareBounds(Square.parse(square));
        board.dispatchEvent(new MouseEvent(board, MouseEvent.MOUSE_PRESSED, System.currentTimeMillis(), 0,
                (int) bounds.getCenterX(), (int) bounds.getCenterY(), 1, false, MouseEvent.BUTTON1));
    }

    private static void moveFeedback() throws Exception {
        AtomicReference<BoardPanel> panel = new AtomicReference<>();
        AtomicReference<Timer> delayedReply = new AtomicReference<>();
        CountDownLatch whiteExpired = new CountDownLatch(1);
        CountDownLatch bothExpired = new CountDownLatch(1);
        try {
            SwingUtilities.invokeAndWait(() -> {
                List<String> requested = new ArrayList<>();
                BoardPanel board = new BoardPanel((from, to) -> requested.add(Square.name(from) + Square.name(to)));
                panel.set(board);
                board.setSize(340, 340);
                Game game = new Game();
                Game.PlayedMove white = game.play(game.position().requireLegalMove("e2e4"));
                board.setGameState(game.position(), game.position().legalMoves(), white.move(), true);
                board.showMoveFeedback(white, 50, 175);
                check(feedback(board).contains("White 1. e4: +1.25 pawns (static change for White)."
                        + " Static eval for White: +0.50 -> +1.75."), "White delta and before/after scores");
                click(board, "e7");
                click(board, "e5");
                equal(List.of("e7e5"), requested, "Flashes do not intercept mouse moves");
                invokeBoardAction(board, "cursor-up");
                invokeBoardAction(board, "cursor-up");
                invokeBoardAction(board, "choose-square");
                invokeBoardAction(board, "cursor-down");
                invokeBoardAction(board, "cursor-down");
                invokeBoardAction(board, "choose-square");
                equal(List.of("e7e5", "e7e5"), requested, "Flashes do not intercept keyboard moves");

                Game.PlayedMove black = game.play(game.position().requireLegalMove("e7e5"));
                board.setGameState(game.position(), game.position().legalMoves(), black.move(), true);
                board.showMoveFeedback(black, 175, 50);
                check(feedback(board).contains("White 1. e4:"), "A rapid reply retains the player's flash");
                check(feedback(board).contains("Black 1... e5: +1.25 pawns (static change for Black)."
                        + " Static eval for Black: -1.75 -> -0.50."), "Black improvement has a positive delta");
                board.showMoveFeedback(black, 50, 75);
                check(feedback(board).contains("Black 1... e5: -0.25 pawns"), "Black loss has a negative delta");
                Game.PlayedMove nextWhite = game.play(game.position().requireLegalMove("g1f3"));
                board.showMoveFeedback(nextWhite, 75, 25);
                check(feedback(board).contains("White 2. Nf3: -0.50 pawns"), "White loss has a negative delta");
                check(!feedback(board).contains("White 1. e4:"), "Newer moves replace only the same side's flash");
                board.showMoveFeedback(nextWhite, 25, 25);
                check(feedback(board).contains("White 2. Nf3: +0.00 pawns"), "Unchanged score is neutral");
                board.setFlipped(true);
                board.setGameState(game.position(), game.position().legalMoves(), nextWhite.move(), true);
                check(feedback(board).contains("White 2. Nf3: +0.00 pawns"), "Flipping and refreshing retain feedback");

                BufferedImage image = new BufferedImage(340, 340, BufferedImage.TYPE_INT_RGB);
                Graphics2D graphics = image.createGraphics();
                try {
                    board.paint(graphics);
                    int withFlash = image.getRGB(20, 120);
                    board.clearMoveFeedback();
                    board.paint(graphics);
                    check(withFlash != image.getRGB(20, 120), "Flashes visibly paint over the board at small sizes");
                } finally {
                    graphics.dispose();
                }
                check(!feedback(board).contains("static change for"), "Clearing removes accessible feedback");
                board.showMoveFeedback(white, 50, 175);
                board.removeNotify();
                check(!feedback(board).contains("static change for"), "Removing the board clears its timer and feedback");

                board.showMoveFeedback(white, 50, 175);
                board.getAccessibleContext().addPropertyChangeListener(event -> {
                    String description = feedback(board);
                    if (!description.contains("White 1. e4:") && description.contains("Black 1... e5:")) {
                        whiteExpired.countDown();
                    }
                    if (!description.contains("static change for")) {
                        bothExpired.countDown();
                    }
                });
                Timer reply = new Timer(1_000, event -> board.showMoveFeedback(black, 175, 50));
                reply.setRepeats(false);
                delayedReply.set(reply);
                reply.start();
            });
            check(whiteExpired.await(5, TimeUnit.SECONDS), "Older flash expires without removing the newer reply");
            check(bothExpired.await(3, TimeUnit.SECONDS), "Feedback fades and expires without further moves");
        } finally {
            SwingUtilities.invokeAndWait(() -> {
                if (delayedReply.get() != null) {
                    delayedReply.get().stop();
                }
                if (panel.get() != null) {
                    panel.get().clearMoveFeedback();
                }
            });
        }
    }

    private static String feedback(BoardPanel board) {
        return board.getAccessibleContext().getAccessibleDescription();
    }

    private static void invokeBoardAction(BoardPanel board, String name) {
        board.getActionMap().get(name).actionPerformed(new ActionEvent(board, ActionEvent.ACTION_PERFORMED, name));
    }

    private static EngineSettings settings(int depth, int milliseconds, int quiescence,
            boolean table, boolean ordering) {
        return new EngineSettings(depth, milliseconds, quiescence, 0, table, ordering,
                false, EngineSettings.EvaluationWeights.defaults());
    }

    private static SearchResult search(Position position, EngineSettings settings) {
        return new Engine().search(position, settings, List.of(position.key()),
                new AtomicBoolean(), info -> { });
    }

    private static void play(Game game, String... moves) {
        for (String move : moves) {
            game.play(game.position().requireLegalMove(move));
        }
    }

    private static void run(String name, CheckedTest test) throws Exception {
        long start = System.nanoTime();
        test.run();
        groups++;
        System.out.printf("PASS %-64s %6.2fs%n", name, (System.nanoTime() - start) / 1_000_000_000.0);
    }

    private static void check(boolean condition, String description) {
        checks++;
        if (!condition) {
            throw new AssertionError(description);
        }
    }

    private static void equal(long expected, long actual, String description) {
        check(expected == actual, description + ": expected " + expected + ", got " + actual);
    }

    private static void equal(Object expected, Object actual, String description) {
        check(java.util.Objects.equals(expected, actual),
                description + ": expected " + expected + ", got " + actual);
    }

    private static void expectIllegal(Runnable action) {
        checks++;
        try {
            action.run();
        } catch (IllegalArgumentException expected) {
            return;
        }
        throw new AssertionError("Expected IllegalArgumentException for invalid input.");
    }

    @FunctionalInterface
    private interface CheckedTest {
        void run() throws Exception;
    }
}
