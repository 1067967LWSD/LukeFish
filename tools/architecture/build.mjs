import PptxGenJS from 'pptxgenjs';
import { mkdir, readFile } from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..', '..');
const output = path.join(root, 'docs', 'LukeFish-Architecture.pptx');
const W = 13.333333;
const H = 7.5;
const C = {
  bg: '0B1220', panel: '111E31', alt: '16283B', ink: 'F3F7FD', muted: 'A7B6CC',
  green: '69E3B5', blue: '79CAFF', gold: 'F6CD86', purple: 'C6ABFF', line: '2A3A51',
};
const catalog = [
  ['Main', ''],
  ...['Piece', 'Square', 'Move', 'Position', 'Game'].map(name => [name, 'model']),
  ...['Engine', 'EngineSettings', 'Evaluator', 'TranspositionTable', 'SearchInfo', 'SearchResult']
    .map(name => [name, 'engine']),
  ...['BoardPanel', 'SettingsPanel', 'ChessFrame'].map(name => [name, 'ui']),
];
const sources = new Map();
for (const [name, area] of catalog) {
  const filename = path.join('src', 'chess', ...(area ? [area] : []), `${name}.java`);
  const content = await readFile(path.join(root, filename), 'utf8');
  if (!new RegExp(`\\b(class|record)\\s+${name}\\b`).test(content)) throw new Error(`Missing type: ${name}`);
  sources.set(name, { filename, content });
}
const anchors = {
  Main: ['SwingUtilities.invokeLater', 'new ChessFrame()'],
  Game: ['private final Position position', 'List<Position.Undo> undoStack', 'List<PlayedMove> moves',
    'List<Long> positionKeys', 'public enum EndReason', 'public record Outcome'],
  Position: ['private final int[] board = new int[64]', 'public Undo makeMove', 'public void unmakeMove',
    'public record Undo', 'public List<Move> legalMoves'],
  Engine: ['this.position = source.copy()', 'new TranspositionTable()', 'Consumer<SearchInfo>',
    'private int negamax', 'private int quiescence', 'entry.depth() >= depth',
    'entry.halfmoveClock() == position.halfmoveClock()', 'entry.historySignature() == historySignature'],
  EngineSettings: ['public record EvaluationWeights', 'public enum Preset'],
  Evaluator: ['public static int evaluate', 'public static Breakdown explain', 'public record Breakdown'],
  TranspositionTable: ['Entry[] entries = new Entry[1 << 18]', 'enum Bound', 'record Entry'],
  SearchInfo: ['principalVariation = List.copyOf(principalVariation)'],
  SearchResult: ['principalVariation = List.copyOf(principalVariation)'],
  BoardPanel: ['extends JPanel', 'BiConsumer<Integer, Integer>', 'position = source.copy()'],
  SettingsPanel: ['extends JPanel', 'public EngineSettings settings()', 'private Runnable onChange'],
  ChessFrame: ['extends JFrame', 'new Game()', 'new BoardPanel(this::requestBoardMove)',
    'new SwingWorker<>()', 'new Engine().search', 'if (revision != searchRevision)', 'stopSignal.set(true)'],
};
for (const [name, expected] of Object.entries(anchors)) {
  for (const anchor of expected) {
    if (!sources.get(name).content.includes(anchor)) throw new Error(`${name} no longer contains: ${anchor}`);
  }
}

const pptx = new PptxGenJS();
pptx.defineLayout({ name: 'LUKEFISH', width: W, height: H });
pptx.layout = 'LUKEFISH';
pptx.author = 'LukeFish';
pptx.subject = 'Source-grounded class architecture of the LukeFish Java chess engine';
pptx.title = 'LukeFish: Class Architecture';
pptx.company = '1067967LWSD/LukeFish';
pptx.lang = 'en-US';
pptx.theme = { headFontFace: 'Segoe UI', bodyFontFace: 'Segoe UI', lang: 'en-US' };
const slides = [];
const visibleText = [];

function bounds(x, y, w, h, label) {
  if (![x, y, w, h].every(Number.isFinite) || x < 0 || y < 0 || w <= 0 || h <= 0
      || x + w > W + 0.001 || y + h > H + 0.001) {
    throw new Error(`Slide ${slides.length}: out-of-bounds ${label}: ${[x, y, w, h]}`);
  }
}

function text(slide, value, x, y, w, h, options = {}) {
  bounds(x, y, w, h, value);
  visibleText.push(value);
  slide.addText(value, {
    x, y, w, h, fontFace: 'Segoe UI', fontSize: 16, color: C.ink, margin: 0,
    breakLine: false, valign: 'top', paraSpaceAfter: 0, paraSpaceBefore: 0,
    lineSpacingMultiple: 1, ...options,
  });
}

function panel(slide, x, y, w, h, color = C.panel, border = C.line) {
  bounds(x, y, w, h, 'panel');
  slide.addShape(pptx.ShapeType.roundRect, {
    x, y, w, h, rectRadius: 0.14, radius: 0.14,
    fill: { color }, line: { color: border, width: 1.1 },
  });
}

function connector(slide, points, { color = C.blue, dashed = false, arrow = true } = {}) {
  for (let index = 1; index < points.length; index++) {
    const [x1, y1] = points[index - 1];
    const [x2, y2] = points[index];
    if (x1 !== x2 && y1 !== y2) throw new Error('Use orthogonal architecture connectors.');
    const reversed = x2 < x1 || y2 < y1;
    const x = Math.min(x1, x2);
    const y = Math.min(y1, y2);
    const w = Math.max(0.001, Math.abs(x2 - x1));
    const h = Math.max(0.001, Math.abs(y2 - y1));
    bounds(x, y, w, h, 'connector');
    slide.addShape(pptx.ShapeType.line, {
      x, y, w, h, line: {
        color, width: 1.6, dashType: dashed ? 'dash' : 'solid',
        ...(arrow && index === points.length - 1
          ? { [reversed ? 'beginArrowType' : 'endArrowType']: 'triangle' } : {}),
      },
    });
  }
}

function label(slide, value, x, y, w, color = C.blue, h = 0.34) {
  text(slide, value, x, y, w, h, { fontSize: 11.5, color, align: 'center', fill: { color: C.bg } });
}

function typeBox(slide, name, kind, fields, x, y, w, h, color = C.green) {
  panel(slide, x, y, w, h, C.panel, color);
  text(slide, kind.toUpperCase(), x + 0.16, y + 0.12, w - 0.32, 0.19,
    { fontSize: 9.5, color, bold: true, charSpacing: 1.0 });
  text(slide, name, x + 0.16, y + 0.38, w - 0.32, 0.36, { fontSize: 19, bold: true });
  connector(slide, [[x + 0.16, y + 0.86], [x + w - 0.16, y + 0.86]], { color: C.line, arrow: false });
  text(slide, fields.join('\n'), x + 0.16, y + 1.02, w - 0.32, h - 1.15,
    { fontSize: 14.1, fontFace: 'Consolas', color: C.muted, lineSpacingMultiple: 1.12 });
}

function smallBox(slide, name, detail, x, y, w, h = 0.96, color = C.green) {
  panel(slide, x, y, w, h, C.alt, color);
  text(slide, name, x + 0.16, y + 0.10, w - 0.32, 0.30, { fontSize: 17.5, bold: true, color });
  if (detail) text(slide, detail, x + 0.16, y + 0.49, w - 0.32, h - 0.55,
    { fontSize: 13.6, color: C.muted });
}

function banner(slide, value, y = 6.40, h = 0.53) {
  panel(slide, 0.5, y, 12.33, h, '17322F', '315D51');
  text(slide, value, 0.68, y + 0.09, 11.97, h - 0.15, { fontSize: 15.1, color: C.green });
}

function slide(title, subtitle, sourceNames, notes) {
  const result = pptx.addSlide();
  result.background = { color: C.bg };
  slides.push(result);
  text(result, 'LUKEFISH / CLASS ARCHITECTURE', 0.46, 0.19, 11.9, 0.22,
    { fontSize: 10.2, color: C.green, bold: true, charSpacing: 1.6 });
  text(result, title, 0.45, 0.57, 12.4, 0.52, { fontSize: 29, bold: true });
  text(result, subtitle, 0.47, 1.19, 12.35, 0.36, { fontSize: 14.1, color: C.muted });
  const citations = sourceNames.map(name => {
    const source = sources.get(name);
    if (!source) throw new Error(`Unknown source citation: ${name}`);
    return source.filename;
  });
  result.addNotes(`${title}\n\n${notes}\n\nSource files:\n${citations.join('\n')}\n\n`
    + 'Diagrams summarize actual source relationships. Solid green relationships denote ownership or returned data; '
    + 'blue arrows denote calls/dependencies unless labeled otherwise. Gold arrows denote callbacks. '
    + 'Nested records and enums are explicitly named. The diagram is not an exhaustive UML model.');
  return result;
}

{
  const s = slide('LukeFish: from classes to a chess engine',
    'An architectural map of the Java 17+ desktop application.', catalog.map(([name]) => name),
    'Start with responsibilities rather than algorithms. LukeFish has fifteen top-level production types. '
    + 'The model knows chess rules; the engine chooses moves; Swing makes the system interactive. '
    + 'The engine is original classical search, not a wrapper around Stockfish. The Java application has no third-party dependencies. '
    + 'This deck uses editable native PowerPoint shapes and text.');
  text(s, 'State.\nDecisions.\nInteraction.', 0.65, 2.02, 6.6, 2.26,
    { fontSize: 40, bold: true, lineSpacingMultiple: 1.10 });
  text(s, '15 top-level types, small data records,\nand explicit ownership boundaries.', 0.69, 4.75, 6.4, 0.9,
    { fontSize: 21, color: C.muted });
  smallBox(s, 'MODEL', 'Pieces, legal moves, board state, game history', 8.16, 2.04, 4.5, 1.10);
  smallBox(s, 'ENGINE', 'Evaluation, recursive search, bounded caching', 8.16, 3.62, 4.5, 1.10, C.blue);
  smallBox(s, 'UI', 'Swing components, callbacks, worker lifecycle', 8.16, 5.20, 4.5, 1.10, C.gold);
}

{
  const s = slide('The package boundaries',
    'Main starts the application; UI depends on the engine and model; the model stays independent.',
    catalog.map(([name]) => name),
    'Main configures appearance and constructs ChessFrame on the Swing event dispatch thread. '
    + 'The ui package imports model and engine types. The engine package uses model types but has no Swing dependency. '
    + 'The model uses standard Java types, not engine or UI classes. The arrows show dependencies, not a claim that every listed type calls every other type.');
  smallBox(s, 'Main', '', 1.17, 1.82, 2.30, 0.53);
  connector(s, [[2.32, 2.35], [2.32, 2.89]], { color: C.green });
  label(s, 'creates ChessFrame', 2.60, 2.47, 1.65, C.green);
  text(s, 'Engine has no Swing dependency.\nModel has no UI or search dependency.', 5.00, 1.88, 7.50, 0.71,
    { fontSize: 17.2, color: C.muted });
  typeBox(s, 'chess.ui', '3 top-level classes', ['ChessFrame', 'BoardPanel', 'SettingsPanel'], 0.50, 2.90, 3.60, 2.80, C.gold);
  typeBox(s, 'chess.engine', '6 top-level types',
    ['Engine / Evaluator', 'EngineSettings', 'TranspositionTable', 'SearchInfo / SearchResult'], 4.86, 2.90, 3.60, 2.80, C.blue);
  typeBox(s, 'chess.model', '5 top-level types',
    ['Piece / Square', 'Move', 'Position', 'Game'], 9.23, 2.90, 3.60, 2.80);
  connector(s, [[4.10, 4.17], [4.86, 4.17]]);
  label(s, 'search', 4.11, 3.82, 0.72);
  connector(s, [[8.46, 4.17], [9.23, 4.17]]);
  label(s, 'rules', 8.48, 3.82, 0.72);
  connector(s, [[2.30, 5.70], [2.30, 6.09], [11.03, 6.09], [11.03, 5.70]]);
  label(s, 'UI also uses the live game, rules, and move values', 4.25, 5.85, 5.95);
  banner(s, 'Dependencies point toward reusable rules. Rendering a piece must not decide whether a move is legal.', 6.48, 0.43);
}

{
  const s = slide('Model: values, state, and history',
    'Game owns the played game; Position supplies reversible rules; small value types connect them.',
    ['Game', 'Position', 'Move', 'Piece', 'Square'],
    'Game constructs its own Position copy and maintains histories. Position generates Move values and uses Piece and Square utility methods. '
    + 'Position.makeMove produces an Undo record; Game stores Undo records and search uses them transiently. '
    + 'Move and Undo are immutable records whose components are immutable values here. Position is mutable. '
    + 'Game.position exposes the live position read-only by convention; callers should use Game.play and Game.undo for live-game changes.');
  typeBox(s, 'Game', 'live-game owner', ['position: Position', 'undo / move / key lists', 'play() / undo()'], 0.50, 2.22, 3.15, 2.40);
  typeBox(s, 'Position', 'mutable rule state', ['board: int[64]', 'turn / rights / counters', 'legalMoves() / makeMove()'], 5.00, 2.22, 3.80, 2.40);
  typeBox(s, 'Piece', 'static utility', ['piece kinds + colors', 'letter / FEN helpers'], 9.75, 1.85, 2.95, 1.91, C.blue);
  typeBox(s, 'Square', 'static utility', ['index <-> file / rank', 'parse() / name()'], 9.75, 4.25, 2.95, 1.91, C.blue);
  smallBox(s, 'Position.Undo', 'Changed values needed to reverse a move', 0.50, 5.22, 3.15, 1.02);
  smallBox(s, 'Move', 'record: from / to / promotion / flags', 5.00, 5.22, 3.80, 1.02);
  connector(s, [[3.65, 3.38], [5.00, 3.38]], { color: C.green });
  label(s, 'owns a copy', 3.73, 2.99, 1.18, C.green);
  connector(s, [[8.80, 2.85], [9.75, 2.85]]);
  connector(s, [[8.80, 4.10], [9.27, 4.10], [9.27, 5.20], [9.75, 5.20]]);
  connector(s, [[2.07, 4.62], [2.07, 5.22]], { color: C.green });
  label(s, 'stores', 0.84, 4.80, 0.92, C.green);
  connector(s, [[6.90, 4.62], [6.90, 5.22]], { color: C.green });
  label(s, 'generates', 7.03, 4.82, 1.18, C.green);
  connector(s, [[5.30, 4.62], [5.30, 4.95], [3.18, 4.95], [3.18, 5.22]], { color: C.blue, dashed: true });
  banner(s, 'Think in three categories: immutable values, a mutable position, and a game that remembers what happened.', 6.49, 0.45);
}

{
  const s = slide('Position + Undo: fast, reversible state',
    'Search can explore many candidates without copying all 64 squares at every node.',
    ['Position', 'Game'],
    'Position.makeMove accepts generated moves and returns a record of changed state. Game.play validates user-level input first. '
    + 'unmakeMove must restore all rule state, not only piece locations. Castling moves a rook too; en passant removes a pawn off the destination square. '
    + 'The Zobrist key and king locations are restored along with counters and rights. Reversibility is checked using both FEN and hash restoration tests.');
  typeBox(s, 'Position', 'mutable object',
    ['board: int[64]', 'sideToMove', 'castlingRights / enPassantSquare', 'halfmoveClock / fullmoveNumber',
      'whiteKing / blackKing', 'key: long'], 0.50, 2.05, 5.80, 3.85);
  typeBox(s, 'Position.Undo', 'immutable nested record',
    ['move / movedPiece', 'capturedPiece / capturedSquare', 'previous rights + en passant', 'previous clocks + king squares',
      'previous key'], 7.40, 2.05, 5.43, 3.85, C.blue);
  connector(s, [[6.30, 3.25], [7.40, 3.25]], { color: C.green });
  label(s, 'makeMove', 6.32, 2.83, 1.06, C.green);
  connector(s, [[7.40, 4.75], [6.30, 4.75]], { color: C.blue });
  label(s, 'unmakeMove', 6.31, 4.28, 1.08);
  banner(s, 'Invariant: making and unmaking a move restores the original board, rule state, counters, and hash.', 6.28, 0.57);
}

{
  const s = slide('Game turns a position into a played game',
    'User-facing changes go through a validated domain operation.',
    ['Game', 'Position', 'Move'],
    'Game.play rejects moves after the game ends and requires membership in Position.legalMoves. It computes algebraic notation, '
    + 'applies the move, saves Undo and position keys, updates the result, and appends a PlayedMove. '
    + 'undo reverses the last ply and recomputes the result. Outcome and PlayedMove are nested records; EndReason is an enum. '
    + 'Checkmate is detected before automatic draw claims. FEN stores a position and counters, not game history.');
  typeBox(s, 'Game', 'history + results',
    ['play(Move) -> PlayedMove', 'undo()', 'outcome() -> Outcome', 'moves() / positionKeys()', 'position(): live Position',
      '  read-only by convention'], 0.50, 2.05, 4.40, 4.18);
  smallBox(s, 'Position', 'Current board, turn, special-move rights, and counters', 5.70, 2.05, 7.12, 1.12);
  smallBox(s, 'History lists', 'Undo records; PlayedMove records; repetition position keys', 5.70, 3.54, 7.12, 1.12, C.blue);
  smallBox(s, 'Outcome + EndReason', 'Playing, mate, stalemate, or an automatically claimed draw', 5.70, 5.03, 7.12, 1.12, C.gold);
  for (const y of [2.61, 4.10, 5.59]) connector(s, [[4.90, y], [5.70, y]], { color: C.green });
  banner(s, 'FEN recreates a position. It cannot recreate earlier repetitions or a complete played game.', 6.51, 0.43);
}

{
  const s = slide('Engine: one search, explicit collaborators',
    'The search owns its working state and returns immutable messages, not UI mutations.',
    ['Engine', 'EngineSettings', 'Evaluator', 'TranspositionTable', 'SearchInfo', 'SearchResult', 'Position'],
    'ChessFrame creates a new Engine for each search request. Engine.search copies its input Position and creates a new bounded transposition table when enabled. '
    + 'EngineSettings is immutable. Evaluator is a static calculation utility. The engine publishes SearchInfo through Consumer<SearchInfo> and returns SearchResult. '
    + 'Both message records defensively copy their principal-variation lists. AtomicBoolean and interruption carry stop requests; no Swing types are needed.');
  typeBox(s, 'EngineSettings', 'immutable input', ['limits / feature switches', 'EvaluationWeights'], 0.50, 1.94, 3.25, 1.78, C.blue);
  typeBox(s, 'Position', 'private working copy', ['legalMoves()', 'makeMove() / unmakeMove()'], 0.50, 4.53, 3.25, 1.78);
  typeBox(s, 'Engine', 'one instance per request',
    ['position / settings / table', 'stop / listener / histories', 'search(...) -> SearchResult'], 4.70, 3.15, 3.90, 2.20);
  typeBox(s, 'Evaluator', 'static calculation', ['evaluate() / explain()', 'uses Position + weights'], 9.45, 1.94, 3.38, 1.78, C.blue);
  typeBox(s, 'TranspositionTable', 'bounded search cache', ['find() / store()', 'Entry + Bound'], 9.45, 4.53, 3.38, 1.78, C.blue);
  smallBox(s, 'SearchInfo', 'Progress + PV snapshot', 4.70, 1.85, 3.90, 0.95, C.gold);
  smallBox(s, 'SearchResult', 'Move + score + depth + PV', 4.70, 5.95, 3.90, 0.95, C.gold);
  connector(s, [[3.75, 2.83], [4.20, 2.83], [4.20, 3.80], [4.70, 3.80]]);
  label(s, 'input', 3.87, 3.18, 0.68);
  connector(s, [[4.70, 4.70], [4.20, 4.70], [4.20, 5.42], [3.75, 5.42]], { color: C.green });
  label(s, 'owns', 3.85, 4.88, 0.70, C.green);
  connector(s, [[8.60, 3.80], [9.02, 3.80], [9.02, 2.83], [9.45, 2.83]]);
  connector(s, [[8.60, 4.70], [9.02, 4.70], [9.02, 5.42], [9.45, 5.42]]);
  connector(s, [[6.65, 3.15], [6.65, 2.80]], { color: C.gold });
  connector(s, [[6.65, 5.35], [6.65, 5.95]], { color: C.gold });
}

{
  const s = slide('The search is a stack of small contracts',
    'Engine controls exploration; Position controls legal state transitions; Evaluator supplies preferences.',
    ['Engine', 'Position', 'Evaluator'],
    'search performs iterative deepening. searchRoot compares root candidates. negamax recursively explores alternating sides with alpha-beta bounds and principal-variation searches. '
    + 'At the horizon, quiescence searches captures/promotions and mandatory check evasions before static evaluation. '
    + 'The negamax and quiescence methods recurse; these boxes show call relationships, not a one-way execution pipeline. '
    + 'makeMove/unmakeMove and history push/pop are paired by finally so cancellation restores state. Only completed iterations replace the current result.');
  const entries = [
    ['search', 'Engine', ['depth 1, 2, 3...', 'completed result']],
    ['searchRoot', 'Engine', ['root moves', 'noise + ordering']],
    ['negamax', 'Engine', ['recursive bounds', 'cache + cutoffs']],
    ['quiescence', 'Engine', ['tactical moves', 'check evasions']],
    ['evaluate', 'Evaluator', ['static score', 'current player']],
  ];
  entries.forEach(([name, owner, fields], index) => {
    const x = 0.55 + index * 2.55;
    typeBox(s, name, owner, fields, x, 2.62, 2.12, 2.12, index === 4 ? C.blue : C.green);
    if (index < 4) connector(s, [[x + 2.12, 3.50], [x + 2.55, 3.50]]);
  });
  connector(s, [[6.15, 2.62], [6.15, 2.10], [7.16, 2.10], [7.16, 2.62]], { color: C.gold });
  label(s, 'recursive call', 5.95, 1.76, 1.45, C.gold, 0.25);
  smallBox(s, 'Position.makeMove / Position.unmakeMove', 'try / finally always restores the working position and repetition history.',
    0.55, 5.00, 12.25, 1.04, C.blue);
  connector(s, [[6.65, 4.74], [6.65, 5.00]], { color: C.blue, dashed: true });
  banner(s, 'Stop conditions include depth, the soft time budget, draw/terminal states, and a maximum-ply safety limit.', 6.48, 0.45);
}

{
  const s = slide('Evaluation is explainable and configurable',
    'Settings choose preferences; Evaluator calculates them; Breakdown exposes the same components to the UI.',
    ['EngineSettings', 'Evaluator', 'SettingsPanel', 'SearchInfo'],
    'SettingsPanel produces a validated EngineSettings record containing EvaluationWeights. Evaluator.explain returns Breakdown in White\'s favor. '
    + 'Evaluator.evaluate converts that total to the side-to-move perspective and clamps ordinary scores outside the mate range. '
    + 'Middlegame/endgame values are blended by phase. SearchInfo.format converts internal scores back to White\'s perspective. '
    + 'Preset is an enum that constructs immutable settings, not a measured Elo rating.');
  typeBox(s, 'EngineSettings', 'record + nested records',
    ['weights: EvaluationWeights', 'depth / time / quiescence', 'randomness / feature flags', 'Preset -> settings()'],
    0.55, 2.12, 3.20, 3.35, C.blue);
  typeBox(s, 'Evaluator', 'stateless utility',
    ['evaluate(Position, weights)', '  -> int score', 'explain(Position, weights)', '  -> Breakdown', 'phase-dependent blending'],
    5.00, 2.12, 3.65, 3.35);
  typeBox(s, 'Breakdown', 'nested record',
    ['material / placement', 'mobility / pawnStructure', 'kingSafety / tempo', 'total()'],
    9.55, 2.12, 3.25, 3.35, C.gold);
  connector(s, [[3.75, 3.70], [5.00, 3.70]]);
  label(s, 'weights', 3.90, 3.27, 0.92);
  connector(s, [[8.65, 3.70], [9.55, 3.70]], { color: C.green });
  label(s, 'explain', 8.69, 3.27, 0.82, C.green);
  smallBox(s, 'UI convention', 'Positive displayed scores favor White.', 0.55, 5.90, 5.97, 0.94);
  smallBox(s, 'Search convention', 'Positive internal scores favor the side to move.', 6.83, 5.90, 5.97, 0.94, C.blue);
}

{
  const s = slide('A cache entry includes its validity context',
    'TranspositionTable is package-private and owned by one Engine search.',
    ['TranspositionTable', 'Engine', 'Position'],
    'The table has 2^18 Entry slots and checks the full position key because multiple keys can map to a slot. '
    + 'Engine also requires sufficient stored depth, equal halfmove clock, and an equal repetition-history fingerprint before reusing a score. '
    + 'Bound distinguishes exact values from lower/upper bounds produced by pruning. A cached best move can still order moves when the draw context differs. '
    + 'Hashes and fingerprints have a small theoretical collision risk; they are not mathematical proofs of identity.');
  typeBox(s, 'TranspositionTable', 'bounded array cache',
    ['Entry[]: 262,144 slots', 'find(key) / store(entry)', 'checks the full key', 'depth-aware replacement'],
    0.55, 2.04, 4.03, 3.57, C.blue);
  typeBox(s, 'Entry', 'nested record',
    ['key + historySignature', 'halfmoveClock + depth', 'score + bound + bestMove'],
    6.28, 1.96, 6.52, 2.34);
  smallBox(s, 'Bound (enum)', 'EXACT / LOWER / UPPER', 6.28, 4.86, 6.52, 0.94, C.gold);
  connector(s, [[4.58, 3.14], [6.28, 3.14]], { color: C.green });
  label(s, 'stores / returns', 4.70, 2.66, 1.47, C.green);
  connector(s, [[9.54, 4.30], [9.54, 4.86]], { color: C.gold });
  label(s, 'score meaning', 9.70, 4.44, 1.55, C.gold);
  banner(s, 'Score reuse checks the full key, sufficient depth, halfmove clock, and repetition context. Move ordering can reuse less.', 6.13, 0.72);
}

{
  const s = slide('Swing composition and callback boundaries',
    'ChessFrame is both the top-level window and the controller; panels report user intent.',
    ['Main', 'ChessFrame', 'BoardPanel', 'SettingsPanel', 'Game'],
    'ChessFrame extends JFrame and owns Game, BoardPanel, and SettingsPanel. Main constructs it on the EDT. '
    + 'Both panels extend JPanel. BoardPanel stores its own Position copy and legal Move list for rendering/input; '
    + 'it calls a BiConsumer<Integer,Integer> injected as this::requestBoardMove. It cannot play moves itself. '
    + 'SettingsPanel builds EngineSettings and invokes a Runnable on change. Gold return arrows show callbacks, not direct panel dependencies on ChessFrame.');
  smallBox(s, 'Main', '', 0.50, 1.88, 2.50, 0.55);
  smallBox(s, 'JFrame', 'Swing base class', 5.17, 1.79, 3.00, 0.95, C.purple);
  smallBox(s, 'Game', 'Owned live game', 9.50, 1.88, 3.25, 1.12);
  typeBox(s, 'ChessFrame', 'window + controller',
    ['game / board / settings', 'input -> playMove()', 'worker + stop + revision'], 4.80, 3.22, 3.80, 2.26);
  typeBox(s, 'BoardPanel', 'extends JPanel',
    ['display Position copy', 'legal Move list', 'BiConsumer<Integer,Integer>'], 0.50, 4.16, 3.15, 2.30, C.blue);
  typeBox(s, 'SettingsPanel', 'extends JPanel',
    ['controls -> settings()', 'EngineSettings snapshot', 'Runnable onChange'], 9.50, 4.16, 3.25, 2.30, C.blue);
  connector(s, [[3.00, 2.155], [4.04, 2.155], [4.04, 3.88], [4.80, 3.88]], { color: C.green });
  label(s, 'creates on EDT', 3.20, 2.63, 1.32, C.green);
  connector(s, [[6.70, 3.22], [6.70, 2.74]], { color: C.purple });
  label(s, 'extends', 6.87, 2.87, 1.06, C.purple);
  connector(s, [[8.60, 3.88], [9.04, 3.88], [9.04, 2.44], [9.50, 2.44]], { color: C.green });
  label(s, 'owns', 8.64, 3.10, 0.73, C.green);
  connector(s, [[4.80, 4.50], [3.65, 4.50]], { color: C.green });
  label(s, 'owns', 3.78, 4.10, 0.87, C.green);
  connector(s, [[3.65, 5.20], [4.80, 5.20]], { color: C.gold, dashed: true });
  label(s, 'callback', 3.73, 5.56, 0.98, C.gold);
  connector(s, [[8.60, 4.50], [9.50, 4.50]], { color: C.green });
  label(s, 'owns', 8.66, 4.08, 0.75, C.green);
  connector(s, [[9.50, 5.20], [8.60, 5.20]], { color: C.gold, dashed: true });
  label(s, 'callback', 8.64, 5.55, 0.82, C.gold);
  banner(s, 'Panels report intent. ChessFrame validates, plays, refreshes, and starts the next search.', 6.55, 0.42);
}

{
  const s = slide('Thread ownership keeps the interface responsive',
    'The live Game stays on the EDT; a SwingWorker searches copied state.',
    ['ChessFrame', 'Engine', 'BoardPanel', 'EngineSettings', 'SearchInfo', 'SearchResult'],
    'Main and Swing event handlers run on the EDT. ChessFrame.startSearch copies the position, snapshots game history and settings, '
    + 'and creates an anonymous SwingWorker<SearchResult,SearchInfo>. Engine.search copies its source again for exclusive mutable ownership. '
    + 'doInBackground computes; publish/process moves progress onto the EDT; done handles completion on the EDT. '
    + 'get is safe in done because the worker has finished. AtomicBoolean supports cooperative cancellation. Undo/new game/settings changes also increment the revision so stale work is rejected.');
  panel(s, 0.50, 1.95, 12.33, 2.00, '152E41', C.blue);
  text(s, 'EVENT DISPATCH THREAD / owns widgets and the live Game', 0.74, 2.12, 11.80, 0.36,
    { fontSize: 17.5, color: C.blue, bold: true });
  smallBox(s, 'Input + Game + painting', 'Short UI operations only', 0.75, 2.74, 3.62, 0.94, C.blue);
  smallBox(s, 'process(messages)', 'Progress updates on the EDT', 4.87, 2.74, 3.25, 0.94, C.blue);
  smallBox(s, 'done()', 'Check revision, then apply result', 8.62, 2.74, 3.94, 0.94, C.blue);
  panel(s, 0.50, 4.83, 12.33, 1.50, '17342F', C.green);
  text(s, 'BACKGROUND WORKER / owns its search state', 0.74, 5.01, 11.80, 0.35,
    { fontSize: 17.5, color: C.green, bold: true });
  text(s, 'doInBackground()  ->  new Engine().search(snapshot, settings, history, signal, listener)',
    0.80, 5.65, 11.77, 0.35, { fontSize: 14.5, fontFace: 'Consolas' });
  connector(s, [[2.50, 3.95], [2.50, 4.83]], { color: C.blue });
  label(s, 'position + settings + history', 0.68, 4.17, 3.49);
  connector(s, [[6.51, 4.83], [6.51, 3.95]], { color: C.gold });
  label(s, 'publish / process', 6.70, 4.19, 1.75, C.gold);
  connector(s, [[10.60, 4.83], [10.60, 3.95]], { color: C.gold });
  label(s, 'SearchResult', 10.74, 4.19, 1.57, C.gold);
  banner(s, 'AtomicBoolean requests stop. A revision token rejects obsolete progress and results.', 6.57, 0.39);
}

{
  const s = slide('One move, end to end',
    'Simplified successful turn: request, validate, mutate, search, then apply a fresh reply.',
    ['BoardPanel', 'ChessFrame', 'Game', 'Position', 'Engine', 'SearchResult'],
    'A board request reports from/to squares; coordinate entry follows a parallel requestTypedMove path. '
    + 'ChessFrame resolves a legal Move, including promotion choice when needed. Game.play validates again, applies the move through Position, '
    + 'and records notation, Undo, and repetition keys. ChessFrame refreshes the UI and starts the computer only when appropriate. '
    + 'The worker invokes Engine.search. Its done method checks the revision before applying SearchResult.bestMove. '
    + 'Arrows omit repeated legality/outcome queries and some return values for readability. Game, not Position, owns played-game history.');
  const actors = ['Board / input', 'ChessFrame', 'Game', 'Position', 'SwingWorker', 'Engine'];
  const centers = actors.map((_, index) => 1.30 + index * 2.14);
  actors.forEach((name, index) => {
    const x = centers[index] - 0.89;
    panel(s, x, 1.91, 1.78, 0.62, C.alt, index >= 4 ? C.green : C.blue);
    text(s, name, x + 0.05, 2.09, 1.68, 0.25, { fontSize: 14, bold: true, align: 'center' });
    connector(s, [[centers[index], 2.54], [centers[index], 6.82]], { color: C.line, dashed: true, arrow: false });
  });
  const messages = [
    [0, 1, 'request a move'], [1, 2, 'play(legal Move)'], [2, 3, 'makeMove -> Undo'],
    [1, 4, 'start search with snapshots'], [4, 5, 'search(...)'], [5, 4, 'SearchResult'],
    [4, 1, 'done(): reject a stale revision'], [1, 2, 'play fresh reply'],
  ];
  messages.forEach(([from, to, message], index) => {
    const y = 3.02 + index * 0.50;
    const color = index === 5 || index === 6 ? C.green : C.blue;
    connector(s, [[centers[from], y], [centers[to], y]], { color });
    const left = Math.min(centers[from], centers[to]);
    label(s, message, left + 0.05, y - 0.23, Math.abs(centers[to] - centers[from]) - 0.10, color, 0.23);
  });
}

{
  const s = slide('Contracts make the architecture testable',
    'Test the boundary you changed, not just whether a game still opens.',
    ['Square', 'Position', 'Game', 'Engine', 'ChessFrame'],
    'The tests are executable explanations and need no external test framework. ChessTests covers coordinates, FEN, perft, reversible state, special moves, '
    + 'draws, evaluation settings, reference search, self-play, and cancellation. UiSmokeTest drives real Swing handlers and dialogs. '
    + 'Perft counts legal move sequences without search pruning or draw adjudication. The starting counts through four plies are 20, 400, 8902, and 197281. '
    + 'Use build.ps1 -Test and the optional desktop -UiSmoke check. Test source files: test\\chess\\ChessTests.java and test\\chess\\UiSmokeTest.java.');
  const columns = [0.50, 3.29, 7.52];
  const widths = [2.79, 4.23, 5.31];
  const headers = ['BOUNDARY', 'PROMISE', 'READ / CHECK'];
  headers.forEach((value, index) => {
    panel(s, columns[index], 1.94, widths[index], 0.60, '193934', C.line);
    text(s, value, columns[index] + 0.16, 2.12, widths[index] - 0.32, 0.24,
      { fontSize: 13, bold: true, color: C.green });
  });
  const rows = [
    ['Coordinates + rules', 'Legal generation matches known perft counts.', 'Square; Position\nChessTests.startingPerft'],
    ['Reversible state', 'make/unmake restores FEN and the position key.', 'Position.Undo\nChessTests.reversibleMoves'],
    ['Search decisions', 'Optimized search agrees with the exhaustive reference.', 'Engine\nChessTests.referenceSearch'],
    ['UI + cancellation', 'Responsive UI; no stale results.', 'ChessFrame\nUiSmokeTest'],
  ];
  rows.forEach((row, r) => row.forEach((value, c) => {
    const y = 2.56 + r * 1.02;
    panel(s, columns[c], y, widths[c], 1.02, r % 2 ? C.alt : C.panel, C.line);
    text(s, value, columns[c] + 0.16, y + 0.16, widths[c] - 0.32, 0.71,
      { fontSize: c === 2 ? 14.2 : 15.4, color: c === 0 ? C.ink : C.muted,
        bold: c === 0, fontFace: c === 2 ? 'Consolas' : 'Segoe UI' });
  }));
}

{
  const s = slide('Read one boundary at a time',
    'Every production type has a place in the map. Follow a small behavior before following every algorithm.',
    catalog.map(([name]) => name),
    'Use this as a reading checklist. Start at Main and the controller, then understand values and Position before opening the deepest recursion. '
    + 'EngineSettings and Evaluator make good first experiments. SearchInfo and SearchResult define the messages crossing the background/UI boundary. '
    + 'Read docs\\LEARNING_GUIDE.md and the narrated video for detailed Java explanations. '
    + 'For a change, identify the owner of the affected state, preserve its invariants, add a focused test, and then play the result.');
  const groups = [
    ['01  Start with interaction', ['Main', 'ChessFrame', 'BoardPanel', 'SettingsPanel'], C.gold],
    ['02  Follow the state', ['Piece / Square', 'Move', 'Position', 'Game'], C.green],
    ['03  Follow the decision', ['EngineSettings', 'Evaluator / Engine', 'TranspositionTable', 'SearchInfo / SearchResult'], C.blue],
  ];
  groups.forEach(([title, names, color], index) => {
    const x = 0.50 + index * 4.20;
    panel(s, x, 2.05, 3.93, 3.78, C.panel, color);
    text(s, title, x + 0.18, 2.29, 3.57, 0.70, { fontSize: 19, color, bold: true });
    text(s, names.join('\n'), x + 0.18, 3.29, 3.57, 2.02,
      { fontSize: 17.1, fontFace: 'Consolas', color: C.muted, lineSpacingMultiple: 1.55 });
  });
  banner(s, 'Keep ownership clear, preserve the invariants, and add a focused check before expanding the design.', 6.24, 0.61);
}

if (slides.length !== 14) throw new Error(`Expected 14 slides, got ${slides.length}.`);
for (const [name] of catalog) {
  if (!visibleText.some(value => value.includes(name))) throw new Error(`The deck does not show ${name}.`);
}
slides.forEach((s, index) => {
  connector(s, [[0.46, 7.06], [12.88, 7.06]], { color: C.line, arrow: false });
  text(s, 'LUKEFISH / JAVA CLASSROOM', 0.47, 7.17, 4.0, 0.17, { fontSize: 8.5, color: C.muted });
  text(s, 'Source references and reading guidance are in the speaker notes.', 4.45, 7.17, 7.3, 0.17,
    { fontSize: 8.5, color: C.muted });
  text(s, `${String(index + 1).padStart(2, '0')} / ${slides.length}`, 12.06, 7.15, 0.77, 0.22,
    { fontSize: 10, color: C.green, align: 'right' });
});
await mkdir(path.dirname(output), { recursive: true });
await pptx.writeFile({ fileName: output, compression: true });
console.log(`Created ${output}: ${slides.length} editable slides, ${catalog.length} source-checked production types.`);
