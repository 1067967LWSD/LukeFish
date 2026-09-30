# Learning Java by building a chess engine

This guide assumes you have written JavaScript in a high-school programming
class. You do **not** need to understand all the chess engine at once. Work
through a small feature, change it, predict what will happen, and play.

The order below also gives each future narrated walkthrough a self-contained
system, visible demonstration, breakpoint, and exercise. The repository does
not generate videos or TTS audio yet.

## Java ideas you already half-know

| Familiar JavaScript idea | Java counterpart in this project |
| --- | --- |
| A file exporting functions | A class in a named package; `Square` groups coordinate helpers. |
| `const` | `final` prevents reassignment. It does **not** make a referenced array immutable. |
| A plain data object | `Move` is a `record`: immutable fields, generated accessors and value equality. |
| A constructor and methods | `new Game()`, `game.play(move)`, and `game.undo()`. |
| An array | `int[] board` has fixed length and a single element type. Empty squares contain zero. |
| A growing array | `List<Move>` / `ArrayList<Move>`; the generic type rules out non-move elements. |
| A callback / arrow function | A lambda such as `event -> requestTypedMove()`. |
| Object identity versus values | `==` compares object identity; `equals()` compares records/strings by value. |
| Event loop | Swing's event dispatch thread, which runs clicks, keys, and painting. |
| Asynchronous work | `SwingWorker`: compute in the background, publish results to the UI thread. |
| Throwing an error | Exceptions have declared types; invalid input and unexpected failures are handled differently. |

Java compiles before it runs. An incorrect type can stop compilation rather
than becoming a surprising value during a game. Read the **first** compiler
error first; later ones may just be consequences.

## 1. Get a piece on the screen

**Read:** `chess.Main`, then the constructor of `chess.ui.BoardPanel`.

**Java:** classes, packages, `public`/`private`, static methods, constructors,
and the application's `main(String[] args)` entry point.

**Demo:** run the game. Flip the board. Resize the window. Notice that the
board remains square and the labels move with the orientation.

**Breakpoint:** `BoardPanel.paintPiece`. Inspect `piece`, `type`, and `box`.
Resume promptly: pausing the UI thread also pauses painting and input.

**Try:** change the board colors or increase the coordinate font size. Predict
whether this should change the engine's chosen move. It should not.

## 2. Describe chess with types and arrays

**Read:** `Piece`, `Square`, and `Move` in `chess.model`.

**Java:** primitive `int`, arrays, constants, arithmetic, records, and value
equality. White pieces are positive numbers; Black pieces are negative.

Square zero is `a1`. Files and ranks are zero-based. A square is `rank * 8 +
file`; its file is the remainder after dividing by eight. This is a flat-array
alternative to an array of eight arrays.

**Breakpoint:** `Square.parse`, while entering `e2e4`.

**Try:** write a small loop that prints every square's number and name. Then
read `coordinatesAndFen()` in `ChessTests`. Why does each square need to round
trip from number to name and back?

**Discuss:** the same move can be represented by two different `Move` objects.
Why does a `List<Move>.contains()` check still work?

## 3. Move a pawn, then enforce the rules

**Read:** `Position.addPawnMoves`, `pseudoLegalMoves`, `legalMoves`, and
`isAttacked`. Leave hashing for a later lesson.

**Java:** loops, conditions, lists, methods, and separating responsibilities.

A pseudo-legal move obeys a piece's movement pattern. A legal move also leaves
its own king safe. `legalMoves()` tries a move, checks the king, then undoes it.
This simple rule catches pins and discovered checks without special pin code.

**Demo:** compare ordinary capture and en passant. An en passant capture
removes a pawn from a square other than the destination.

**Load this FEN:** `k3r3/8/8/3pP3/8/8/8/4K3 w - d6 0 1`

The white pawn on e5 cannot capture en passant: moving it exposes its king to
the rook. Confirm that d6 is not a legal marker.

**Breakpoint:** `Position.makeMove`, with a condition such as
`move.from() == 36` (e5). Conditional breakpoints are much less overwhelming
than pausing on every generated move.

**Try:** explain why a king cannot castle through an attacked square, even if
its final square is safe. Find where the code enforces it.

## 4. Undo is a data-design problem

**Read:** `Position.Undo`, `makeMove`, `unmakeMove`, and `Game.undo`.

**Java:** immutable records holding a snapshot of changed values, mutable
objects, stacks implemented with lists, and invariants.

Moving a piece changes more than two squares: castling rights, en passant,
move counters, king locations, and a hash may all change. Undo must restore
**all** of them. Castling and en passant also affect extra squares.

**Demo:** castle, undo, and castle again. Underpromote, undo, and choose a
different promotion. Nothing should disappear or become permanently disabled.

**Breakpoint:** `Position.unmakeMove`. Compare the fields in `undo` with the
live `position`.

**Try:** add a regression check that moves a rook away from its starting square
and back. It must not regain castling rights, but undoing both moves must
restore the original rights.

## 5. Give the position a score

**Read:** `Evaluator.explain`, `placement`, and `EngineSettings.EvaluationWeights`.

**Java:** pure calculations, helper methods, a validated record constructor,
and returning several results in a `Breakdown`.

Material is only one idea. Knights like useful central squares; pawns can be
passed or isolated; king safety changes as pieces leave the board. A game-phase
weight smoothly blends middlegame and endgame preferences instead of abruptly
switching modes.

**Demo:** pause the engine, load the same position twice, and change only the
material percentage. Read the component totals after a move.

**Breakpoint:** `Evaluator.explain`. Inspect `material`, `placement`,
`mobility`, `pawns`, `kings`, and `tempo`.

**Try:** reward a rook on the seventh rank more strongly. Check whether this
improves one chosen position without making another clearly worse. An idea
that sounds good is not automatically a good evaluation term.

**Important:** internal evaluation favors the side whose turn it is. Displayed
scores always favor White. Find the multiplication that converts between them.

## 6. Think ahead with recursion

**Read:** `Engine.search`, `searchRoot`, and then `negamax`.

**Java:** recursion, a call stack, local variables, return values, and `try/finally`.

Imagine making a move, letting the opponent choose their best reply, and
repeating. Negamax uses the fact that a good score for one side is bad for the
other: negate the returned score after changing turns.

Iterative deepening finishes depth one, then two, then three. It looks wasteful,
but it provides a usable result if time expires and a good move-ordering hint
for the next iteration.

**Demo:** choose maximum depth 3, time 5 seconds, randomness 0, and analyze.
Follow the completed-depth messages. A ply is one player's move, not a pair.

**Breakpoint:** the root `score = -negamax(...)` call. Step into one shallow
line and inspect `depth`, `ply`, `alpha`, and `beta`.

**Debugger tip:** the wall-clock budget keeps advancing while paused in Eclipse.
Set the budget to 30 seconds, use shallow depth and conditional breakpoints,
and expect a long debugging pause to end the search on resume. The model and
perft tests are easier places for leisurely stepping because they have no
thinking deadline. Eclipse F5 steps in, F6 steps over, and F8 resumes.

**Try:** draw a tiny two-ply tree on paper with three possible moves per side.
Predict which move negamax should choose before stepping through the code.

## 7. Skip work without changing the answer

**Read:** the alpha/beta cutoff in `negamax`, `orderMoves`, `rememberCutoff`,
and the narrow-window search followed by re-search.

**Java:** comparators, lambdas, multidimensional arrays, and optimization that
preserves a contract.

Alpha-beta stops searching an alternative once it cannot change the opponent's
decision. Good ordering finds useful alternatives sooner. A principal-variation
search first asks whether a later move can beat the current best, then spends
more effort only when needed.

**Demo:** keep the FEN, completed depth, quiescence, and weights fixed; set
randomness to zero. Compare ordering on/off. The score should agree when both
searches finish the same depth, though equally scored moves and node counts
can differ.

**Try:** disable killer ordering while leaving capture ordering on. Compare
node counts, not just elapsed time (other programs and the JVM affect timing).
Keep `searchConsistency()` passing.

## 8. Do not stop halfway through a tactic

**Read:** `Engine.quiescence`.

**Java:** another recursive base case and understanding why a shortcut may not
be correct in every state.

A static evaluation immediately after capturing a defended queen can miss the
recapture. Quiescence follows captures and promotions beyond the main horizon.
It cannot treat standing still in check as an option, so check evasions remain
mandatory even when the capture-extension setting is zero.

**Demo:** analyze a capture exchange with quiescence zero, then increase it.
Depth limits, move ordering, and randomness should otherwise stay fixed.

**Try:** explain why checking only captures would be wrong when in check, and
why stalemate must be recognized before trusting a static score.

## 9. Remember positions without forgetting history

**Read:** the Zobrist fields in `Position`, `TranspositionTable`, and
`Engine.pushHistory` / `popHistory`.

**Java:** `long`, bitwise XOR, deterministic pseudorandom data, hash maps,
bounded arrays, and cache validity.

The repetition key describes the board, turn, castling rights, and legal en
passant. Move counters are not part of that identity. But a cached **search
score** also depends on the 50-move clock and repetition history.

LukeFish conservatively fingerprints the multiset of history keys and checks
it plus the halfmove clock before reusing a score. This gives up some cache
hits to avoid incorrectly reusing a draw result from a different history.
A move can still be reused for ordering when that context differs. Like other
64-bit hash schemes, this has a very small theoretical collision risk.

**Demo:** do a knight shuffle until the position repeats three times, then
undo. Load its FEN into a new game: the earlier occurrences are no longer known.

**Try:** inspect the pinned en passant example from lesson 3. Why must its
repetition key match the same board with `-` instead of `d6`?

## 10. Keep the UI alive while the engine thinks

**Read:** `ChessFrame.startSearch`, `cancelSearch`, and the `SwingWorker` methods
`doInBackground`, `process`, and `done`.

**Java:** ownership, concurrency, atomics, snapshots, lambdas, and handling
expected cancellation separately from actual failures.

Swing widgets and the live game belong to the EDT. The engine owns a copy.
Settings and progress records are immutable. `AtomicBoolean` makes a stop
request visible across threads. A revision number rejects stale results after
undo, new game, FEN import, player changes, or parameter changes.

**Demo:** let the computer think with a five-second budget. Flip the board,
undo, and start again. Input must not freeze, and an old move must never appear
on the new board.

**Breakpoint:** `done()` after `get()`. It is safe to call `get()` there because
the worker is already finished; doing the same on the EDT immediately after
`execute()` would freeze the UI.

**Try:** follow why **Move now** sets the stop signal but **Undo** also cancels
the worker and increments the revision. They deliberately have different
effects on whether the result is played.

## A repeatable experiment

Copy a FEN and write down one hypothesis, for example: "Ordering should lower
the number of nodes needed to finish depth three without changing the score."
Set randomness to zero, choose a reachable depth, and give both runs enough
time to complete it. Change one variable, then record depth, nodes, score, and
PV. If a run stops at a different depth, it is not an equivalent comparison.

Use the **Tests** as executable explanations, not just a final checkbox.
`ChessTests.perft` counts legal move sequences without evaluation or pruning.
Its standard starting-position counts are 20, 400, 8,902, and 197,281 through
four plies. The other reference positions come from the
[Chess Programming Wiki perft results](https://www.chessprogramming.org/Perft_Results).
When a count differs, investigate legality or make/unmake before changing search.

For a first independent improvement, choose something small: add an evaluation
experiment, improve an accessibility label, or extend a test. Keep it in the
correct package, add a focused check, and explain what it taught you before
moving on to another algorithm.
