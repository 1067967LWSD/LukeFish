import { readFile } from 'node:fs/promises';
import path from 'node:path';

export const WIDTH = 1920;
export const HEIGHT = 1080;
export const FPS = 24;
export const LEAD = 0.25;
export const TAIL = 0.45;

export function escapeXml(text) {
  return String(text).replace(/[&<>"']/g, character => ({
    '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&apos;',
  })[character]);
}

export function wrap(text, columns) {
  const lines = [];
  let line = '';
  for (const word of text.split(/\s+/)) {
    if (word.length > columns) {
      throw new Error(`Unbreakable text is too wide (${columns} columns): ${word}`);
    }
    if (line && line.length + word.length + 1 > columns) {
      lines.push(line);
      line = '';
    }
    line += (line ? ' ' : '') + word;
  }
  if (line) lines.push(line);
  return lines;
}

export function wrapCode(text, columns) {
  const lines = [];
  let rest = text;
  const indentation = Math.min(12, text.length - text.trimStart().length + 4);
  while (rest.length > columns) {
    let cut = rest.lastIndexOf(' ', columns);
    if (cut <= indentation) cut = columns;
    lines.push(rest.slice(0, cut));
    rest = ' '.repeat(indentation) + rest.slice(cut).trimStart();
  }
  lines.push(rest);
  return lines;
}

export async function extractSource(source, root) {
  if (!source) return [];
  const filename = path.resolve(root, source.file);
  if (!filename.startsWith(root + path.sep)) throw new Error('Source must be inside the repository.');
  const lines = (await readFile(filename, 'utf8')).split(/\r?\n/);
  const result = [];
  for (const [group, slice] of source.slices.entries()) {
    const matches = lines.flatMap((line, index) => line.includes(slice.anchor) ? [index] : []);
    if (!matches.length || (matches.length > 1 && !slice.occurrence)) {
      throw new Error(`${source.file}: missing or ambiguous anchor: ${slice.anchor}`);
    }
    const start = matches[(slice.occurrence ?? 1) - 1];
    if (start === undefined || start + slice.count > lines.length) {
      throw new Error(`${source.file}: excerpt exceeds source bounds.`);
    }
    const selected = lines.slice(start, start + slice.count);
    const indent = Math.min(...selected.filter(line => line.trim())
      .map(line => line.length - line.trimStart().length));
    if (result.length) result.push({ number: null, text: '...', group: -1 });
    for (const [offset, line] of selected.entries()) {
      if (!line.trim() || line.trim().startsWith('//')) continue;
      result.push({ number: start + offset + 1, text: line.slice(indent), group });
    }
  }
  return result;
}

export function clock(seconds, milliseconds = false, separator = '.') {
  const total = Math.round(seconds * 1000);
  const hours = Math.floor(total / 3_600_000);
  const minutes = Math.floor(total / 60_000) % 60;
  const secs = Math.floor(total / 1000) % 60;
  const base = [hours, minutes, secs].map(value => String(value).padStart(2, '0')).join(':');
  return milliseconds ? `${base}${separator}${String(total % 1000).padStart(3, '0')}` : base;
}

export function captions(text, words, audioDuration) {
  if (!Array.isArray(words) || !words.length || !(audioDuration > 0)) {
    throw new Error('Narration needs word timings and a positive duration.');
  }
  let previousStart = -1;
  let previousTime = -1;
  for (const word of words) {
    if (!Number.isInteger(word.Start) || !Number.isInteger(word.Length)
        || word.Start < previousStart || word.Start < 0 || word.Length <= 0
        || word.Start + word.Length > text.length || !Number.isFinite(word.Seconds)
        || word.Seconds < previousTime || word.Seconds < 0 || word.Seconds >= audioDuration) {
      throw new Error('Invalid speech word timing.');
    }
    previousStart = word.Start;
    previousTime = word.Seconds;
  }
  const groups = [];
  let start = 0;
  for (let index = 0; index < words.length; index++) {
    const nextStart = words[index + 1]?.Start ?? text.length;
    const fragment = text.slice(words[start].Start, nextStart).trim();
    const nextEnd = words[index + 2]?.Start ?? text.length;
    const wouldOverflow = text.slice(words[start].Start, nextEnd).trim().length > 94;
    if (wouldOverflow || /[.!?]$/.test(fragment) || index - start >= 14 || index === words.length - 1) {
      groups.push({ start: words[start].Seconds + LEAD, text: fragment });
      start = index + 1;
    }
  }
  return groups.map((group, index) => {
    const end = index + 1 < groups.length
      ? groups[index + 1].start - 0.02 : audioDuration + LEAD + 0.2;
    if (end <= group.start) throw new Error('Caption has no display time.');
    return { ...group, end };
  });
}

export function subtitleText(cues, format) {
  const vtt = format === 'vtt';
  if (!vtt && format !== 'srt') throw new Error(`Unsupported subtitle format: ${format}`);
  return (vtt ? 'WEBVTT\n\n' : '') + cues.map((cue, index) =>
    `${index + 1}\n${clock(cue.start, true, vtt ? '.' : ',')} --> `
    + `${clock(cue.end, true, vtt ? '.' : ',')}\n${wrap(cue.text, 52).join('\n')}\n`,
  ).join('\n');
}

export function assText(cues) {
  const assTime = seconds => {
    const centiseconds = Math.round(seconds * 100);
    const hours = Math.floor(centiseconds / 360000);
    const minutes = Math.floor(centiseconds / 6000) % 60;
    const wholeSeconds = Math.floor(centiseconds / 100) % 60;
    return `${hours}:${String(minutes).padStart(2, '0')}:${String(wholeSeconds).padStart(2, '0')}.`
      + String(centiseconds % 100).padStart(2, '0');
  };
  return `[Script Info]
ScriptType: v4.00+
PlayResX: ${WIDTH}
PlayResY: ${HEIGHT}
WrapStyle: 2

[V4+ Styles]
Format: Name, Fontname, Fontsize, PrimaryColour, SecondaryColour, OutlineColour, BackColour, Bold, Italic, Underline, StrikeOut, ScaleX, ScaleY, Spacing, Angle, BorderStyle, Outline, Shadow, Alignment, MarginL, MarginR, MarginV, Encoding
Style: Default,Segoe UI,38,&H00F7F9FC,&H00F7F9FC,&H00100B07,&H00100B07,0,0,0,0,100,100,0,0,1,1.5,0,2,110,110,35,1

[Events]
Format: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text
` + cues.map(cue => {
    const text = wrap(cue.text, 52).map(line => line.replace(/\\/g, '\\\\')
      .replace(/\{/g, '\\{').replace(/\}/g, '\\}')).join('\\N');
    return `Dialogue: 0,${assTime(cue.start)},${assTime(cue.end)},Default,,0,0,0,,${text}\n`;
  }).join('');
}
