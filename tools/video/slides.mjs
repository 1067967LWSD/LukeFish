import sharp from 'sharp';
import { escapeXml as xml, HEIGHT, WIDTH, wrap, wrapCode } from './video.mjs';

const C = {
  bg: '#0b1220', panel: '#111e31', ink: '#f3f7fd', muted: '#a7b6cc',
  green: '#69e3b5', blue: '#79caff', red: '#ff929e', gold: '#f6cd86', line: '#2a3a51',
};
const mono = 'Consolas';

function rect(x, y, width, height, fill, stroke = 'none', radius = 18) {
  return `<rect x="${x}" y="${y}" width="${width}" height="${height}" rx="${radius}"
    fill="${fill}" stroke="${stroke}" stroke-width="2"/>`;
}

function text(x, y, value, size = 30, fill = C.ink, weight = 400, anchor = 'start', family = 'Segoe UI') {
  return `<text x="${x}" y="${y}" font-family="${family}" font-size="${size}"
    font-weight="${weight}" text-anchor="${anchor}" fill="${fill}">${xml(value)}</text>`;
}

function lines(x, y, value, columns, size = 30, fill = C.ink, maxRows = 7) {
  const rows = wrap(value, columns);
  if (rows.length > maxRows) throw new Error(`Text exceeds its panel: ${value}`);
  return rows.map((row, index) => text(x, y + index * size * 1.3, row, size, fill)).join('');
}

function arrow(x1, y1, x2, y2, color = C.green, dashed = false) {
  const marker = color === C.red ? 'red' : color === C.blue ? 'blue' : 'green';
  return `<path d="M${x1},${y1} L${x2},${y2}" fill="none" stroke="${color}"
    stroke-width="5" ${dashed ? 'stroke-dasharray="12 9"' : ''} marker-end="url(#${marker})"/>`;
}

function syntax(value) {
  const tokens = /(\/\/.*$|"(?:\\.|[^"\\])*"|\b(?:public|private|protected|static|final|class|record|return|int|long|boolean|void|new|if|else|for|while|try|finally|throw|throws|catch|enum|extends|switch|case|break|false|true|null|this)\b|\b\d[\d_]*\b)/g;
  let result = '';
  let cursor = 0;
  for (const match of value.matchAll(tokens)) {
    result += xml(value.slice(cursor, match.index));
    const color = match[0].startsWith('//') ? C.muted : match[0].startsWith('"') ? C.green
      : /^\d/.test(match[0]) ? C.gold : '#c6abff';
    result += `<tspan fill="${color}">${xml(match[0])}</tspan>`;
    cursor = match.index + match[0].length;
  }
  return result + xml(value.slice(cursor));
}

function codePanel(scene, source, beat) {
  let rows;
  let font;
  for (font of [29, 28, 27, 26, 25, 24]) {
    const columns = Math.floor(1056 / (font * 0.61));
    rows = source.flatMap((line, sourceIndex) => wrapCode(line.text, columns).map((value, index) => ({
      ...line, text: value, sourceIndex, number: index ? null : line.number, continued: index > 0,
    })));
    if (rows.length * (font + 7) <= 538) break;
  }
  if (rows.length * (font + 7) > 538) throw new Error(`${scene.id}: code excerpt is too tall.`);
  let svg = rect(64, 246, 1180, 634, C.panel, C.line)
    + text(92, 289, scene.source.file, 25, C.blue, 600)
    + `<path d="M90,312 H1218" stroke="${C.line}" stroke-width="2"/>`;
  for (const [index, line] of rows.entries()) {
    const y = 354 + index * (font + 7);
    const active = scene.source.slices.length === 1
      ? (line.sourceIndex < Math.ceil(source.length / 2)) === (beat === 0)
      : beat === 0 ? line.group === 0 : line.group > 0;
    if (active && (line.number !== null || line.continued)) {
      svg += rect(80, y - font, 1148, font + 6, '#183740', 'none', 5);
    }
    if (line.number !== null) svg += text(133, y, line.number, 19, C.muted, 400, 'end', mono);
    if (line.continued) svg += text(130, y, '>', 18, C.muted, 400, 'end', mono);
    svg += `<text x="159" y="${y}" xml:space="preserve" font-family="${mono}"
      font-size="${font}" fill="${C.ink}">${syntax(line.text)}</text>`;
  }
  return svg;
}

function cards(scene, beat) {
  return scene.cards.map(([label, body], index) => {
    const y = 246 + index * 329;
    const active = index === beat;
    return rect(1280, y, 576, 305, active ? '#142b36' : C.panel, active ? C.green : C.line)
      + text(1310, y + 45, `0${index + 1}`, 22, active ? C.green : C.muted, 700)
      + text(1360, y + 45, label, 27, C.ink, 600)
      + lines(1310, y + 104, body, 32, 30, active ? C.ink : C.muted, 5);
  }).join('');
}

function node(x, y, label, detail, active = false, width = 290) {
  return rect(x, y, width, 100, active ? '#183c3c' : '#18283e', active ? C.green : C.line, 16)
    + text(x + width / 2, y + 42, label, 30, active ? C.green : C.ink, 600, 'middle')
    + text(x + width / 2, y + 76, detail, 22, C.muted, 400, 'middle');
}

const glyphs = {
  K: '\u2654', Q: '\u2655', R: '\u2656', B: '\u2657', N: '\u2658', P: '\u2659',
  k: '\u265a', q: '\u265b', r: '\u265c', b: '\u265d', n: '\u265e', p: '\u265f',
};

function board(pieces, highlights = {}, x = 142, y = 295, size = 536) {
  const cell = size / 8;
  let svg = '';
  for (let rank = 0; rank < 8; rank++) {
    for (let file = 0; file < 8; file++) {
      const square = String.fromCharCode(97 + file) + (rank + 1);
      const left = x + file * cell;
      const top = y + (7 - rank) * cell;
      const fill = highlights[square] ?? ((file + rank) % 2 === 0 ? '#547e78' : '#d9e2d4');
      svg += rect(left, top, cell, cell, fill, 'none', 0);
      if (pieces[square]) {
        const piece = pieces[square];
        const white = piece === piece.toUpperCase();
        svg += `<text x="${left + cell / 2}" y="${top + cell * 0.82}" font-family="Segoe UI Symbol"
          font-size="${cell * 0.89}" text-anchor="middle" fill="${white ? '#fffaf0' : '#122632'}"
          stroke="${white ? '#122632' : '#f4f5e9'}" stroke-width="0.8">${glyphs[piece]}</text>`;
      }
    }
    svg += text(x - 24, y + (7 - rank + 0.62) * cell, rank + 1, 23, C.muted, 400, 'middle');
    svg += text(x + (rank + 0.5) * cell, y + size + 30, String.fromCharCode(97 + rank), 23, C.muted, 400, 'middle');
  }
  return svg;
}

function initialPieces() {
  const pieces = {};
  for (const [index, piece] of [...'RNBQKBNR'].entries()) {
    const file = String.fromCharCode(97 + index);
    pieces[`${file}1`] = piece;
    pieces[`${file}2`] = 'P';
    pieces[`${file}7`] = 'p';
    pieces[`${file}8`] = piece.toLowerCase();
  }
  return pieces;
}

function teachingDiagram(scene, beat, source) {
  let svg = rect(64, 246, 1180, 634, C.panel, C.line);
  switch (scene.visual) {
    case 'hero': {
      const pieces = initialPieces();
      if (beat) {
        delete pieces.e2;
        pieces.e4 = 'P';
      }
      svg += board(pieces, { e2: '#779e8c', e4: '#8dcdb0' }, 112, 330, 456)
        + text(650, 340, 'The Java learning route', 35, C.ink, 600);
      for (const [index, [label, detail]] of [
        ['01  Represent a game', 'Types, classes, records, collections'],
        ['02  Choose a move', 'Rules, evaluation, recursive search'],
        ['03  Make it usable', 'Callbacks, workers, tests'],
      ].entries()) svg += node(648, 382 + index * 146, label, detail, index === beat, 538);
      break;
    }
    case 'architecture': {
      const panels = [
        ['chess.model', ['Piece / Square', 'Move / Position', 'Game'], 'What is legal?'],
        ['chess.engine', ['Evaluator', 'Engine', 'TranspositionTable'], 'What is promising?'],
        ['chess.ui', ['BoardPanel', 'SettingsPanel', 'ChessFrame'], 'What does the user see?'],
      ];
      for (const [index, [label, names, question]] of panels.entries()) {
        const x = 92 + index * 382;
        svg += rect(x, 368, 348, 360, '#17283c', (beat === 0) === (index === 0) ? C.green : C.line)
          + text(x + 24, 421, label, 30, C.blue, 600, 'start', mono);
        names.forEach((name, row) => { svg += text(x + 24, 488 + row * 57, name, 27); });
        svg += text(x + 24, 683, question, 25, C.green);
      }
      svg += text(94, 305, 'Main starts the app. ChessFrame coordinates the parts.', 31, C.ink, 600)
        + text(94, 815, 'Rules can run and be tested without a Swing window.', 29, C.muted);
      break;
    }
    case 'coordinates': {
      svg += board(beat ? { e4: 'P' } : { e2: 'P' }, { e2: '#79bdad', e4: '#79bdad' });
      svg += text(732, 366, 'file = 4', 32, C.blue, 600, 'start', mono)
        + text(732, 420, `rank = ${beat ? 3 : 1}`, 32, C.blue, 600, 'start', mono)
        + text(732, 500, beat ? '3 * 8 + 4 = 28' : '1 * 8 + 4 = 12', 37, C.green, 600, 'start', mono)
        + text(732, 584, 'Square.of(file, rank)', 28, C.muted, 400, 'start', mono);
      const actual = source.find(line => line.text.includes('return rank * 8 + file;'));
      if (!actual) throw new Error('Coordinate example no longer matches Square.of.');
      svg += text(732, 637, actual.text.trim(), 28, C.ink, 400, 'start', mono)
        + text(732, 755, 'a1 = 0       h8 = 63', 28, C.muted, 400, 'start', mono);
      break;
    }
    case 'pin': {
      const pieces = beat ? { a8: 'k', e8: 'r', e1: 'K', d6: 'P' }
        : { a8: 'k', e8: 'r', e1: 'K', e5: 'P', d5: 'p' };
      svg += board(pieces, beat ? { e1: '#bb727d', d6: '#bd8b81' } : { e5: '#83bfab' });
      if (beat) svg += arrow(443.5, 375, 443.5, 754, C.red);
      else svg += arrow(443.5, 380, 443.5, 487, C.blue);
      svg += text(736, 378, beat ? 'Candidate rejected' : 'The pawn is pinned', 33,
        beat ? C.red : C.green, 600)
        + lines(736, 447, beat ? 'The rook now attacks the white king along the e-file.'
          : 'The pawn blocks the rook. Moving it can expose the king.', 25, 31)
        + text(736, 660, 'e5 -> d6', 42, beat ? C.red : C.blue, 600, 'start', mono)
        + lines(736, 720, 'En passant also removes the pawn from d5.', 25, 29, C.muted);
      break;
    }
    case 'tree':
    case 'pruning': {
      const pruning = scene.visual === 'pruning';
      svg += text(94, 298, 'White chooses the best guaranteed score', 30, C.ink, 600)
        + arrow(649, 445, 360, 518, C.blue) + arrow(649, 445, 948, 518, C.blue)
        + arrow(354, 613, 232, 698, C.blue) + arrow(354, 613, 484, 698, C.blue)
        + arrow(950, 613, 833, 698, C.blue)
        + arrow(950, 613, 1096, 698, pruning && beat ? C.red : C.blue, pruning && beat);
      svg += node(509, 340, 'White to move', 'Choose A: +30', beat === 1, 280)
        + node(220, 516, 'Move A', 'Black can hold +30', beat === 1, 270)
        + node(815, 516, 'Move B', pruning ? 'At most +10' : 'Black can hold +10', false, 270);
      const leaves = [[138, '+30'], [390, '+80'], [740, '+10'], [1000, pruning ? '?' : '+90']];
      for (const [index, [x, score]] of leaves.entries()) {
        svg += rect(x, 707, 184, 84, '#1a2b40', index === 0 && beat ? C.green : C.line)
          + text(x + 92, 763, pruning && index === 3 && beat ? 'SKIP' : score, 37,
            pruning && index === 3 && beat ? C.red : C.ink, 600, 'middle');
      }
      svg += text(94, 846, 'Illustrative centipawn scores; all values here favor White.', 24, C.muted);
      break;
    }
    case 'threads':
      svg += text(94, 302, 'Ownership keeps mutable state separated', 31, C.ink, 600)
        + rect(94, 349, 1120, 186, '#172f42', C.blue)
        + text(122, 395, 'EVENT DISPATCH THREAD', 25, C.blue, 600)
        + text(122, 451, 'Input  /  live Game  /  widgets  /  painting', 32)
        + text(122, 496, 'process(progress)                 done(result)', 28, C.muted, 400, 'start', mono)
        + rect(94, 647, 1120, 181, '#183a36', C.green)
        + text(122, 693, 'BACKGROUND WORKER', 25, C.green, 600)
        + text(122, 753, 'doInBackground() -> Engine.search(...)', 32, C.ink, 400, 'start', mono)
        + arrow(307, 546, 307, 631, C.blue)
        + arrow(1008, 631, 1008, 546, C.green)
        + text(335, 595, 'snapshot', 28, C.blue)
        + text(778, 595, 'messages', 28, C.green);
      break;
    case 'journey': {
      const nodes = [
        [96, 380, 'Input', 'e2e4', 0], [481, 380, 'Legal Move', 'validate coordinates', 0],
        [866, 380, 'Game.play', 'history + repaint', 0],
        [96, 673, 'Snapshot', 'position + settings', 1], [481, 673, 'Engine.search', 'recursive decisions', 1],
        [866, 673, 'SearchResult', 'freshness check + play', 1],
      ];
      svg += text(94, 309, 'Player action', 28, C.blue, 600)
        + text(94, 601, 'Computer response', 28, C.green, 600);
      for (const [x, y, label, detail, phase] of nodes) svg += node(x, y, label, detail, phase === beat, 340);
      for (const y of [430, 723]) {
        svg += arrow(444, y, 471, y, C.blue) + arrow(829, y, 856, y, C.blue);
      }
      break;
    }
    case 'debug':
    case 'experiment': {
      const items = scene.visual === 'debug' ? [
        ['01  A small breakpoint', 'Square.parse: inspect text and indices.'],
        ['02  Predict a state change', 'Position.makeMove: inspect board and Undo.'],
        ['03  Step with a purpose', 'F5 step in   |   F6 step over   |   F8 resume'],
      ] : [
        ['01  Fix the conditions', 'Same FEN, depth, weights, quiescence. Noise = 0.'],
        ['02  Change move ordering', 'Compare score and nodes at equal completed depth.'],
        ['03  Explain the result', 'Make one small edit and add a focused check.'],
      ];
      items.forEach(([label, detail], index) => {
        const y = 280 + index * 195;
        svg += rect(92, y, 1122, 158, '#182b3e', index === beat ? C.green : C.line)
          + text(122, y + 58, label, 36, index === beat ? C.green : C.ink, 600)
          + text(122, y + 115, detail, 30, C.muted);
      });
      break;
    }
    default:
      throw new Error(`Unknown teaching visual: ${scene.visual}`);
  }
  return svg;
}

export async function slideSvg(lesson, scene, source, sceneIndex, beat, screenshot) {
  let body;
  if (scene.visual === 'app') {
    const picture = await sharp(screenshot).resize(1138, 595, { fit: 'inside' }).png().toBuffer();
    const dimensions = await sharp(picture).metadata();
    body = rect(64, 246, 1180, 634, C.panel, C.line)
      + `<image x="${64 + (1180 - dimensions.width) / 2}" y="${246 + (634 - dimensions.height) / 2}"
        width="${dimensions.width}" height="${dimensions.height}" href="data:image/png;base64,${picture.toString('base64')}"/>`;
  } else {
    body = scene.visual ? teachingDiagram(scene, beat, source) : codePanel(scene, source, beat);
  }
  if (scene.title.length > 58) throw new Error(`${scene.id}: title too long.`);
  const progress = (sceneIndex * 2 + beat + 1) / (lesson.scenes.length * 2);
  const chapter = lesson.chapters[scene.chapter - 1];
  return `<svg xmlns="http://www.w3.org/2000/svg" width="${WIDTH}" height="${HEIGHT}" viewBox="0 0 ${WIDTH} ${HEIGHT}">
    <defs>${[['green', C.green], ['blue', C.blue], ['red', C.red]].map(([name, color]) =>
      `<marker id="${name}" markerWidth="6" markerHeight="6" refX="5" refY="3" orient="auto">
        <path d="M0,0 L6,3 L0,6 Z" fill="${color}"/></marker>`).join('')}</defs>
    ${rect(0, 0, WIDTH, HEIGHT, C.bg, 'none', 0)}
    ${rect(64, 48, 10, 28, C.green, 'none', 3)}
    ${text(92, 71, 'LUKEFISH / JAVA FROM THE INSIDE OUT', 23, C.green, 600)}
    ${text(1856, 71, `${String(scene.chapter).padStart(2, '0')} / 08   ${chapter.toUpperCase()}`, 22, C.muted, 600, 'end')}
    ${text(64, 148, scene.title, 57, C.ink, 650)}
    ${text(64, 207, scene.subtitle, 28, C.muted)}
    ${body}
    ${cards(scene, beat)}
    ${text(66, 908, `${String(sceneIndex + 1).padStart(2, '0')} / ${lesson.scenes.length}`, 18, C.muted)}
    ${text(1854, 908, scene.source ? 'Repository excerpts / original line numbers / ... = omitted code'
      : scene.visual === 'app' ? 'Actual app capture' : 'Teaching diagram / not a live search trace', 18, C.muted, 400, 'end')}
    ${rect(0, 926, WIDTH, 146, '#070d16', 'none', 0)}
    ${rect(0, 1074, WIDTH, 6, C.line, 'none', 0)}
    ${rect(0, 1074, WIDTH * progress, 6, C.green, 'none', 0)}
  </svg>`;
}
