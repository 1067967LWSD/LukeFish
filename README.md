# LukeFish: a Java chess classroom

A playable, original Java chess engine with a Swing desktop interface. Built for
a student who has taken a JavaScript programming class and wants to learn Java
by changing something they can immediately play against.

**Java 17 or later. No third-party libraries, build system, accounts, servers,
or external chess engine required.** Everything from legal moves to the thinking
algorithm is in this repository.

## Start in Eclipse

1. Install a full **JDK 17 or newer** and **Eclipse IDE for Java Developers**
   with Java 17 support. A JRE alone cannot compile the project.
2. In Eclipse, open **File > Import > General > Existing Projects into Workspace**.
   Select this repository's root directory, select **LukeFish**, and finish.
   The checked-in `.project` and `.classpath` configure the project; do not
   import it as a Maven or Gradle project.
3. If Eclipse reports an unbound Java runtime, use **Window > Preferences >
   Java > Installed JREs** to add your JDK directory. Under **Execution
   Environments > JavaSE-17**, select a compatible installed JDK. The project
   compiles to Java 17 even when run using a newer JDK.
4. Open `src\chess\Main.java`, then **Run As > Java Application**.
   Alternatively, run the supplied `LukeFish.launch` configuration.
5. For debugging, use **Debug As > Java Application** on the same class.
   The thinking output also appears in Eclipse's **Console** view.

These Eclipse instructions apply to Windows, macOS, and Linux with a desktop
display. The app is standard Java/Swing, not a web application.

### Windows command line

With the JDK's `bin` directory on `PATH`, or `JAVA_HOME` set to the JDK directory:

```powershell
.\build.ps1 -Run
```

The script compiles `src` and `test` into the ignored `out` directory, using
`javac --release 17 -encoding UTF-8 -Xlint:all`. It does not install anything.
If PowerShell blocks a downloaded script, review it and follow your machine's
script-execution policy, or use Eclipse instead.

## Play and explore

- **Mouse:** click a piece, then a marked destination. Dots are quiet moves;
  rings are captures. Choose any of four promotion pieces in the dialog.
- **Keyboard:** Tab to the board; use arrows to move the blue cursor, Enter or
  Space to select, and Escape to clear. Or type a coordinate move such as
  `e2e4` into the field below the board and press Enter. Castling uses `e1g1`;
  promotion includes a suffix, for example `a7a8n`.
- **You play:** choose White, Black, or Both for a local two-player game.
  Changing this control transfers control of the current position; it does
  not reset the game.
- **Pause engine:** cancels the search and allows you to move either side.
  Uncheck it to resume computer play.
- **Analyze:** searches without playing a move; a blue arrow shows the hint.
  **Move now** uses the last fully completed search depth, or a legal fallback
  if not even depth one has finished.
- **Undo turn:** reverses your move and the computer's reply. In local or paused
  play it reverses one ply. Undoing an opening computer move pauses the engine
  rather than immediately replaying it.
- **FEN:** copy or load a position to repeat an experiment. Loading pauses the
  engine and resets history. FEN includes the board and move counters, **not**
  earlier moves or repetition history.

| Shortcut | Action |
| --- | --- |
| Ctrl+N | New game |
| Ctrl+Z | Undo turn |
| Ctrl+L | Load FEN |
| F1 | Quick-start guide |
| F2 | Analyze / hint |
| F3 | Flip board |
| F4 | Finish thinking / move now |

Games and settings are kept in memory. Copy a FEN before closing if you want to
return to a position; this version does not save full games as PGN.

## Adjust the opponent

Start with **First steps**, **Casual**, **Challenge**, or **Strongest** in the
Engine lab. Then change individual controls to create a custom setting.

| Control | What it changes |
| --- | --- |
| Maximum depth: 1-12 plies | How many half-moves the main search tries to examine. |
| Time: 0.1-30 seconds | Per-search soft wall-clock budget; the smaller depth/time limit wins. |
| Quiescence: 0-12 | Extra capture/promotion plies beyond the normal horizon. Check evasions remain mandatory even at zero. |
| Randomness: 0-300 cp | A random selection bonus in the range `-value` to `+value` for each root move. Zero removes deliberate mistakes. |
| Remember positions | Bounded transposition table for cached bounds and move ordering. |
| Try promising moves first | Capture ordering, previous best move, killer moves, and history heuristic. |
| Log every root candidate | Exact candidate scores, with extra work and output; intentionally slower than ordinary search. |
| Material: 0-200% | Relative importance of owning pieces. |
| Piece placement: 0-200% | Centralization, development, bishop pair, and phase-dependent king placement. |
| Mobility: 0-10 cp/square | Reward for available knight/bishop/rook/queen destinations. |
| Pawn structure: 0-200% | Passed-pawn bonuses and doubled/isolated-pawn penalties. |
| King safety: 0-200% | Pawn shields, open files, and attacks near the king before the endgame. |

More time/depth generally produces stronger play, not a guarantee on every move.
Lower depth, less quiescence, and higher randomness make a gentler opponent.
With randomness enabled, root moves use full search windows so their selection
scores are comparable; weakening the engine can therefore cost more nodes at
the same depth. Noise changes **selection**, never recursive evaluation or
cached scores.

This is a capable educational **classical** engine, substantially beyond a
random-move or single-ply demonstration. It has iterative deepening,
principal-variation alpha-beta search, quiescence, move ordering, a transposition
table, and tapered positional evaluation. It has **no measured Elo rating** and
does not claim parity with Stockfish or modern tournament engines. There is no
opening book, neural network, or endgame tablebase.

## Read the thinking panel

**All displayed scores favor White:** positive means White is preferred,
negative means Black. `+1.00` is roughly a pawn, **not a win probability**.
`+M3` means a predicted White mate in three moves; `-M3` favors Black.
Internally, negamax uses the side-to-move's perspective; the UI converts it.

Depth is measured in **plies**, one player's move. Nodes count visited search
positions, and `n/s` is average nodes per second. **PV** (principal variation)
is the predicted best continuation in coordinate notation. A cached line may
be shorter than the search depth, and quiescence may extend it beyond that depth.
The score and PV are predictions, not guarantees.

The label below the board is the **static evaluation of the current board**.
The thinking panel describes a **search from its starting board**; its score
may differ. A move prints the actual material/placement/mobility/pawn/king/tempo
breakdown in centipawns. Logs also go to the Eclipse Console. The UI retains
only recent output to keep memory bounded.

Changes to the game, player control, or settings cancel obsolete work. A search
runs on a board copy, and a revision check prevents a late result from playing
on the wrong position. Changing settings during analysis restarts that analysis.

## Rules and deliberate scope

Standard chess movement includes check, pins, both castles, en passant,
underpromotion, checkmate, and stalemate. Kings are never captured.

For simpler classroom play, the app **automatically claims** threefold
repetition and the 50-move rule instead of asking a player to claim them.
Checkmate takes precedence. Repetition identity includes side to move,
castling rights, and **legally available** en passant, even when a pawn is pinned.
Common insufficient-material positions are recognized (bare kings, one minor
piece, and only same-color-square bishops). Arbitrary blocked dead positions
are not exhaustively detected. Two knights are not automatically declared dead.

FEN loading validates structure, kings, pawn ranks, castling pieces, en passant
state, and counters. It is not a proof that a composed position is historically
reachable. No tournament clocks, Chess960, or UCI protocol are included.
`Move.toUci()` is coordinate notation, not a claim of UCI protocol support.

## Code map

| Package / entry point | Responsibility |
| --- | --- |
| `chess.Main` | Application startup, appearance, and visible error reporting. |
| `chess.model.Piece`, `Square`, `Move` | Typed constants, coordinates, and immutable moves. |
| `chess.model.Position` | Legal moves, attacks, reversible state, FEN, and position hashing. |
| `chess.model.Game` | History, algebraic notation, undo, and results. |
| `chess.engine.Evaluator` | Explainable, tunable middlegame/endgame evaluation. |
| `chess.engine.EngineSettings` | Validated immutable settings and difficulty presets. |
| `chess.engine.Engine` | Search and cooperative cancellation. |
| `chess.engine.TranspositionTable` | Bounded cache with draw-history-aware score reuse. |
| `chess.ui.BoardPanel` | Painting, board coordinates, mouse, and keyboard. |
| `chess.ui.SettingsPanel` | Parameter controls and tooltips. |
| `chess.ui.ChessFrame` | Game controller, Swing workers, and thinking output. |

Start with [the learning guide](docs/LEARNING_GUIDE.md), not the deepest search
method. It connects JavaScript concepts to Java and supplies exercises,
breakpoint suggestions, and a suggested learning sequence.

The [narrated video lesson](docs/VIDEO.md) follows the engine from input to search
and back to the screen, teaching beginner Java with actual code excerpts,
diagrams, local text-to-speech narration, captions, and eight chapters. Its
editable script and optional video producer live in `tools\video`; media
production dependencies are separate from the dependency-free Java app.

**[Watch/download the video](docs/video/LukeFish-Java-Walkthrough.mp4)** or
**[download the architecture PowerPoint](docs/LukeFish-Architecture.pptx)**.
The editable deck shows the classes, their ownership and dependencies, move
processing, search collaboration, and Swing thread boundaries. A local
chaptered player and transcript are included in `docs\video`.

## Regression checks

No test framework or dependency download is required:

```powershell
.\build.ps1 -Test
.\build.ps1 -UiSmoke
```

In Eclipse, run `chess.ChessTests` as a Java application, or use
`Chess tests.launch`. The tests cover published perft counts, make/unmake and
hash restoration, special moves, notation, results, repetition, evaluation
knobs, tactics, cache/order equivalence, deadlines, cross-thread cancellation,
exhaustive reference search, self-play, and headless board rendering/input.

`chess.UiSmokeTest` additionally requires a desktop display. It opens and closes
a real Swing window and drives mouse/coordinate entry, engine replies, undo,
settings changes, FEN/promotion dialogs, analysis, and stale-result rejection.
An optional filename argument saves a rendered PNG. It never uses a network
service or an external chess engine.
