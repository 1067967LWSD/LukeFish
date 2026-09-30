# Inside LukeFish: Learn Java through Chess

Beginner lesson. Narration is synthesized locally with Windows speech.

Code excerpts are read directly from the repository; teaching diagrams are labeled separately.


## 00:00:00 - Learn Java through chess

Welcome to Luke Fish. In this lesson, a chess game becomes a map of Java. We will follow one move from the board, through the rules, into the computer's thinking, and back to the screen. You do not need to understand every line at once.

If you have written a little JavaScript, many ideas will feel familiar. Java adds explicit types, classes, and compilation. We will connect each idea to real code in this repository. Pause whenever you want to inspect a method or try an experiment yourself.


## 00:00:32 - Start with something you can play

This is the actual Swing application. The board accepts clicks or a coordinate move such as E two, E four. The Engine lab changes the opponent's settings. Pause lets you explore both sides. Analyze suggests a move without playing it.

The thinking panel reports depth, visited positions called nodes, and the predicted best continuation, called the principal variation. Displayed scores favor White. Positive one point zero means roughly a pawn of advantage, not a guaranteed win or a probability.


## 00:01:04 - Three packages, three jobs

A package groups related classes under a name. The model package describes chess: pieces, moves, positions, and game history. It does not need Swing to decide whether a move is legal. That separation makes the rules easier to test.

The engine package evaluates positions and searches for a good move. The U I package draws the board and handles interaction. Chess Frame coordinates these parts. Imports let one class use another. Notice the design principle: drawing a piece and choosing a move are different responsibilities.


## 00:01:38 - From source code to a running app

Read: `src\chess\Main.java`

Java source files compile into bytecode before the Java virtual machine runs them. The J D K includes the compiler. In Eclipse, import the existing project and run chess dot Main as a Java application. This project does not require Maven or an external chess engine.

Main is the entry point. Public makes it accessible, static means the method belongs to the class, and void means it returns no value. String brackets describes an array of command line arguments. The final block schedules window creation on Swing's event thread.


## 00:02:12 - Classes describe; objects remember

Read: `src\chess\model\Game.java`

A class defines a kind of object. Calling new Game creates an instance, with its own position and history lists. A constructor has the class's name and no return type. Here, the no argument constructor delegates to another constructor using this.

Private fields keep implementation details inside the class. Other code should use methods such as play and undo to maintain the game's rules. This is encapsulation. Final on the class prevents inheritance; final on a field prevents reassigning that field. These are related restrictions, not identical meanings.


## 00:02:48 - Types turn ideas into checked data

Read: `src\chess\model\Piece.java`

Java requires declared types. Int holds whole numbers; boolean holds true or false. The compiler rejects many incompatible assignments before the game runs. These named constants make integer values meaningful. Static final is the usual pattern for constants shared by all users of a class.

Luke Fish uses a piece's sign for its color and its absolute value for its kind. A white knight is positive two; a black knight is negative two. Zero is empty. These are compact conventions, not a special chess type enforced by Java. Validation still matters.


## 00:03:25 - A chessboard fits in one array

Read: `src\chess\model\Square.java`

The board is a flat array with sixty four slots. Files and ranks start at zero. Multiply the rank by eight, then add the file. E two has file four and rank one, so its index is twelve. E four becomes twenty eight.

Square dot of is a static helper: no Square object is needed. Its parameters and return value are integers. The reverse operations use remainder for the file and integer division for the rank. Naming these helpers is clearer than repeating coordinate arithmetic throughout the application.


## 00:03:58 - A Move is a value

Read: `src\chess\model\Move.java`

Move is a record containing the source, destination, promotion, and flags. Java generates accessors such as from and to, plus value equality. The compact constructor checks incoming values. Each component here is an integer, so callers cannot mutate the move's contents.

For objects, double equals generally checks whether two references point to the same object. Equals can compare values. Records provide that comparison, which lets a list recognize an equivalent move. Flags combine independent facts as bits; the capture method tests whether the capture bit is present.


## 00:04:35 - Arrays, lists, and the meaning of final

Read: `src\chess\model\Game.java`

An array has a fixed length. An Array List can grow as moves are added. List is the interface, or contract; Array List is one implementation. The type inside angle brackets is a generic parameter. A list of moves cannot also contain a string.

A final list field cannot be redirected to another list, but the existing list can still change. To protect history, these accessors return List dot copy Of. It is an unmodifiable snapshot, though it does not deeply copy arbitrary elements. Here, immutable records and boxed numbers make that useful.


## 00:05:11 - Small rules become methods

Read: `src\chess\model\Position.java`

Now follow a pawn. Direction is the side to move multiplied by eight. Adding that direction advances one rank in the flat array. The if statement checks that the destination exists and is empty. A pawn on its starting rank may also advance twice.

The rest of this method handles diagonal captures, while another helper handles promotion choices. Other methods handle knights and sliding pieces. Loops and conditions are familiar, but named methods keep each rule understandable. At this stage, a candidate move may still expose its own king.


## 00:05:45 - A movement pattern is not enough

Read: `src\chess\model\Position.java`

Read this enhanced for loop as: for each move in the candidate list. The method remembers which side is moving, applies a candidate, and checks that side's king. It then undoes the move before considering the next candidate.

Only safe candidates enter the legal list. Notice that the method returns data, not a screen update. Testing the resulting position handles pins and discovered checks without special code for every possible pin. Castling also checks that the king does not cross an attacked square.


## 00:06:16 - Why en passant needs the whole board

Consider this position from the learning guide. The white pawn on E five appears able to capture the black pawn en passant. That special capture moves to D six, while removing the black pawn from D five. Two different squares are involved.

But moving the white pawn clears the E file. The black rook can then attack the white king, so the capture is illegal. Make, check, and undo sees the real resulting board. This is why correct state changes must come before clever search optimizations.


## 00:06:47 - Undo must restore more than pieces

Read: `src\chess\model\Position.java`

Position is deliberately mutable: its board changes in place. Copying the entire position for every search step would create much more work. Instead, make Move returns an Undo record containing the changed information, and unmake Move restores it.

Undo must restore castling rights, the en passant square, move counters, king locations, and the hash, not merely move a piece backward. An invariant is a fact that must remain true. Here, making and unmaking a move must recover exactly the original state.


## 00:07:20 - A position is not a complete game

Read: `src\chess\model\Game.java`

Game adds the story around a position. It validates moves, records notation, stores undo information, and detects results. An enum defines a limited set of named possibilities, such as checkmate and stalemate. Invalid input throws a typed exception rather than silently corrupting the board.

F E N is a text format for pieces, turn, castling rights, en passant, and counters. It does not contain earlier moves. Loading the same F E N does not restore repetition history. This classroom app automatically claims threefold repetition and the fifty move rule; checkmate takes precedence.


## 00:07:59 - Evaluation asks: how good is this board?

Read: `src\chess\engine\Evaluator.java`

Evaluation assigns a score to a position. Material values pieces, but Luke Fish also considers placement, mobility, pawn structure, king safety, and a small turn bonus called tempo. The Breakdown record returns these components together, so the interface can explain the total.

A knight near useful central squares may receive a placement bonus. Isolated pawns can receive a penalty. These are programmed preferences, not absolute chess truth. Pure calculations and small helper methods make an evaluator easier to understand, change, and compare against known examples.


## 00:08:36 - Perspective and game phase

Read: `src\chess\engine\Evaluator.java`

The displayed breakdown favors White. Search, however, wants the current player's perspective. Multiplying by the side to move keeps a white score unchanged and reverses it for Black. Clear conventions prevent a positive number from accidentally meaning opposite things in different places.

Evaluation also blends middlegame and endgame values using a phase weight. A king often wants shelter early and activity late. Rather than switch abruptly, the blend changes gradually as material disappears. The helper returns an integer, so Java's integer division discards any fractional remainder.


## 00:09:13 - Settings are validated snapshots

Read: `src\chess\engine\EngineSettings.java`

The Engine lab creates an immutable settings record. Its constructor validates ranges immediately. A search gets one snapshot instead of repeatedly reading live controls. That makes its behavior easier to reason about, even if the user changes a spinner while it is thinking.

Depth controls the main search horizon. Time limits the thinking budget. Weights adjust evaluation preferences. Randomness affects root move selection, not recursive scores. For a fair experiment, set randomness to zero, hold the position fixed, and change just one setting at a time.


## 00:09:49 - Thinking ahead is a tree of choices

Search asks a different question from evaluation: what could happen next? Imagine White has moves A and B. After A, Black can allow a score of plus thirty or plus eighty for White. A sensible opponent chooses the smaller advantage, plus thirty.

After B, suppose Black can hold White to plus ten. White prefers A's guaranteed thirty to B's ten. These are illustrative numbers, not a live engine result. One player's move is a ply. Recursion repeats this decision process at each level of the tree.


## 00:10:22 - Recursion needs a smaller problem

Read: `src\chess\engine\Engine.java`

A recursive method calls itself on a smaller problem. Each call has its own local variables on the call stack. Here, depth decreases and ply increases. When depth reaches zero, the engine switches to quiescence, which we will explain shortly. Checkmate and stalemate are also stopping conditions.

Negamax evaluates from the player to move's perspective. After our move, it is the opponent's turn, so the returned score is negated. Good for the opponent means bad for us. The alpha and beta arguments also reverse signs and order; they describe the useful search window.


## 00:10:59 - Finish a useful answer before going deeper

Read: `src\chess\engine\Engine.java`

Instead of starting at the maximum depth, iterative deepening finishes shallow searches first. Each completed iteration becomes the current answer. It also suggests a promising move to try first in the next iteration, helping the deeper search work more efficiently.

If the time budget expires halfway through a depth, the engine keeps the last completed result. If no depth finishes, it still provides a legal fallback. The budget is soft because stop requests are checked at selected points, not enforced by interrupting every individual operation.


## 00:11:32 - Cleanup belongs in finally

Read: `src\chess\engine\Engine.java`

Search temporarily changes the position and repetition history. If a stop request throws an exception deep in recursion, ordinary statements after the recursive call might never run. A finally block performs cleanup while the call stack unwinds.

Here, cleanup pops the history entry and unmakes the move. This is a general Java pattern: pair an operation with guaranteed cleanup, including exceptional exits. Expected cancellation is handled separately from real failures. Do not hide every exception; unexpected problems need visible reporting.


## 00:12:07 - Skip a branch that cannot help

Return to our small tree. White already has an option worth plus thirty. Under move B, we discover that Black can hold White to plus ten. We do not need to examine every other Black reply to know that B cannot improve White's decision.

Alpha and beta express these useful bounds. When the window closes, the code stops searching that branch. This is not random guessing. Given the same depth and evaluation rules, correct pruning preserves the score. Better move ordering often lets the engine discover these cutoffs sooner.


## 00:12:40 - Good ordering makes pruning useful

Read: `src\chess\engine\Engine.java`

This lambda takes a move and produces an ordering score. The comparator sorts moves by that score, reversed so promising candidates come first. Lambdas let Java pass behavior into another method, much like an arrow function used as a callback in JavaScript.

Luke Fish prioritizes a preferred move, promotions, and captures, then uses remembered cutoff moves and a history heuristic. It also uses principal variation search: probe later moves with a narrow window, then search more fully if needed. These optimize searching; they do not redefine legal chess.


## 00:13:15 - Do not stop in the middle of an exchange

Read: `src\chess\engine\Engine.java`

Imagine evaluating immediately after winning a piece, before the opponent recaptures. The position can look misleadingly good. Quiescence follows captures and promotions beyond the ordinary depth limit, subject to its configured extension budget and the engine's safety limits.

When not in check, the current evaluation can serve as a baseline called standing pat. In check, doing nothing is not legal. The engine must consider evasions, including quiet moves, even when the capture extension is set to zero. Terminal positions are checked before trusting an evaluation.


## 00:13:50 - A compact fingerprint for a position

Read: `src\chess\model\Position.java`

Different move orders can lead to the same position. To recognize positions quickly, Luke Fish builds a sixty four bit fingerprint called a Zobrist key. Java's long type holds it. Bitwise exclusive or combines piece and square keys; applying the same key twice cancels that contribution.

The full repetition key also includes the turn, castling rights, and legally available en passant. Move counters are not part of repetition identity. Hashing is fast, but not mathematically collision free. This is a compact lookup tool, not a perfect proof that two positions are identical.


## 00:14:27 - A cached score has conditions

Read: `src\chess\engine\TranspositionTable.java`

The transposition table stores previous search information in a bounded array. Its entries include depth, score, a bound type, and the best move. A cutoff may prove only a lower or upper bound, so treating every stored number as exact would be incorrect.

The engine also checks the halfmove clock and a repetition history fingerprint before reusing a score. Identical boards can have different draw futures. A remembered move can still help ordering when that context differs. The lesson is broader than chess: cache validity depends on all relevant inputs.


## 00:15:01 - The board reports; the controller decides

Read: `src\chess\ui\ChessFrame.java`

Board Panel extends Swing's J Panel, reusing the component framework and specializing painting and input. It reports requested squares through a callback; it cannot independently play a move. The method reference with two colons passes Chess Frame's request Board Move method as that callback.

The coordinate text field uses an action listener lambda. It asks the model for a legal move, then asks the controller to play it. An invalid move becomes a helpful input message. Painting, input handling, and rule validation remain separate even though the player experiences one action.


## 00:15:37 - Keep thinking off the UI thread

Swing has an event dispatch thread, often called the E D T. If a long search ran there, clicks and painting would freeze. Chess Frame starts a Swing Worker instead. The worker searches a copied position using immutable settings and a history snapshot.

Do in Background performs computation on a background thread. Published progress reaches process on the event thread, and done handles completion there too. The UI and search do not mutate the same position. Ownership and message passing are often simpler than putting locks around every operation.


## 00:16:11 - Cancellation needs a freshness check

Read: `src\chess\ui\ChessFrame.java`

Stopping a worker is not enough by itself. A result may already be finishing when the user chooses Undo or New game. Chess Frame increments a revision number. A worker remembers its starting revision and rejects completion if that revision is no longer current.

Atomic Boolean carries the cooperative stop request between threads. Move now sets that signal but preserves the usable result. Undo also cancels and invalidates the worker. These are intentionally different behaviors: one wants the best completed move; the other wants no old move applied at all.


## 00:16:44 - Follow e2e4 from end to end

Put the pieces together. The player enters E two, E four. Square helpers interpret coordinates. Legal move generation finds the matching Move record. Game applies it and updates history and results. Chess Frame refreshes the display and decides whether the computer should respond.

A copied position enters search. Recursive exploration calls evaluation, uses ordering and caching, and eventually returns a Search Result record. Back on the event thread, the revision check protects the live game before the result is played. Each boundary has a clear Java type and a clear responsibility.


## 00:17:22 - Tests are executable explanations

Read: `test\chess\ChessTests.java`

Perft counts legal move sequences without search pruning or draw adjudication. From the starting position, the first four counts are twenty, four hundred, eight thousand nine hundred two, and one hundred ninety seven thousand two hundred eighty one. Those precise numbers reveal subtle rule mistakes.

Other tests verify undo restoration, position hashes, special moves, and agreement with a simpler reference search. Think of a test as an executable promise. If move counts change unexpectedly, inspect legality and make or unmake before adjusting evaluation weights. A stronger sounding preference cannot fix a broken rule.


## 00:18:02 - Debug one small question at a time

Set a breakpoint in Square dot parse and enter a move. Inspect the input string and resulting indices. Next, stop in make Move and compare the board before and after. In Eclipse, F five steps in, F six steps over, and F eight resumes.

Use a conditional breakpoint to focus on one source square. Search time still passes while the debugger is paused, so start shallow and allow a larger budget. Model tests are better for leisurely stepping. Read the first compiler error first; later errors may simply be consequences.


## 00:18:36 - Make one prediction. Change one thing.

Try this experiment: analyze the same position with move ordering enabled and disabled. Keep depth, evaluation, and quiescence fixed, with randomness zero and enough time to finish. At the same completed depth, scores should agree, though tied best moves can differ. Compare visited nodes, not just elapsed time.

Then make one small code change and add a focused check. You have now seen types, objects, records, collections, loops, recursion, exceptions, callbacks, and concurrency working together. Luke Fish is a classical educational engine, not Stockfish. Its greatest feature is that you can understand and change the system yourself.
