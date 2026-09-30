import test from 'node:test';
import assert from 'node:assert/strict';
import { fileURLToPath } from 'node:url';
import { readFile } from 'node:fs/promises';
import { spawnSync } from 'node:child_process';
import sharp from 'sharp';
import { captions, clock, escapeXml, extractSource, subtitleText, wrap, wrapCode } from './video.mjs';
import { slideSvg } from './slides.mjs';
import { playerHtml } from './player.mjs';

const root = fileURLToPath(new URL('..\\..\\', import.meta.url));
const lesson = JSON.parse(await readFile(new URL('lesson.json', import.meta.url), 'utf8'));

test('all production entry points parse before an expensive media build', () => {
  for (const name of ['render.mjs', 'slides.mjs', 'player.mjs', 'preview.mjs']) {
    const result = spawnSync(process.execPath, ['--check', fileURLToPath(new URL(name, import.meta.url))]);
    assert.equal(result.status, 0, result.stderr.toString());
  }
});

test('every code excerpt resolves to real, unambiguous repository lines', async () => {
  for (const scene of lesson.scenes) {
    const lines = await extractSource(scene.source, root.replace(/[\\/]$/, ''));
    if (scene.source) assert.ok(lines.some(line => line.number > 0), scene.id);
  }
});

test('lesson has complete chapters, paired narration and visuals, and a beginner-length script', () => {
  assert.deepEqual([...new Set(lesson.scenes.map(scene => scene.chapter))], [1, 2, 3, 4, 5, 6, 7, 8]);
  assert.equal(new Set(lesson.scenes.map(scene => scene.id)).size, lesson.scenes.length);
  for (const scene of lesson.scenes) {
    assert.match(scene.id, /^[a-z0-9-]+$/);
    assert.equal(scene.beats.length, 2);
    assert.equal(scene.cards.length, 2);
    assert.ok(scene.source || scene.visual);
    assert.ok(scene.beats.every(beat => beat.trim().length > 100));
  }
  const words = lesson.scenes.flatMap(scene => scene.beats).join(' ').split(/\s+/).length;
  assert.ok(words >= 2400 && words <= 3000, `Unexpected script length: ${words}`);
});

test('every visual beat fits its text and source-code panels', async () => {
  const placeholder = await sharp({ create: { width: 1260, height: 900, channels: 3, background: '#fff' } })
    .png().toBuffer();
  const results = await Promise.allSettled(lesson.scenes.flatMap((scene, index) =>
    scene.beats.map(async (_, beat) => {
      const source = await extractSource(scene.source, root.replace(/[\\/]$/, ''));
      return slideSvg(lesson, scene, source, index, beat, placeholder);
    })));
  const failures = results.filter(result => result.status === 'rejected').map(result => result.reason.message);
  assert.deepEqual(failures, []);
});

test('text wrapping, code continuations, and XML escaping preserve content', () => {
  assert.deepEqual(wrap('one two three four', 8), ['one two', 'three', 'four']);
  assert.throws(() => wrap('unbreakable', 3), /too wide/);
  assert.ok(wrapCode('    return somethingRatherLong + anotherValue;', 25).every(line => line.length <= 25));
  assert.equal(escapeXml('List<Move> & "input"'), 'List&lt;Move&gt; &amp; &quot;input&quot;');
});

test('caption times use speech events, preserve punctuation, and do not overlap', () => {
  const text = 'Hello world. Learn Java.';
  const words = [
    { Start: 0, Length: 5, Seconds: 0 },
    { Start: 6, Length: 5, Seconds: 0.5 },
    { Start: 13, Length: 5, Seconds: 1.4 },
    { Start: 19, Length: 4, Seconds: 1.9 },
  ];
  const cues = captions(text, words, 2.5);
  assert.equal(cues[0].start, 0.25);
  assert.equal(cues[0].text, 'Hello world.');
  assert.equal(cues[1].text, 'Learn Java.');
  assert.ok(cues[0].end < cues[1].start);
  assert.match(subtitleText(cues, 'vtt'), /^WEBVTT\n/);
  assert.match(subtitleText(cues, 'srt'), /00:00:00,250 -->/);
  assert.throws(() => captions(text, [], 1), /word timings/);
  assert.throws(() => captions(text, [{ Start: 0, Length: 5, Seconds: 4 }], 3), /Invalid/);
});

test('timestamps carry rounded milliseconds across minute and hour boundaries', () => {
  assert.equal(clock(59.9999, true), '00:01:00.000');
  assert.equal(clock(3599.9999, true), '01:00:00.000');
});

test('the checked-in player includes all chapters and transcript sections without trailing whitespace', () => {
  const timeline = { duration: 1159, chapters: lesson.chapters.map((title, index) => ({
    number: index + 1, title, start: index * 100,
  })) };
  const beats = lesson.scenes.map((scene, index) => ({ scene, beat: 0, start: index * 30 }));
  const html = playerHtml(lesson, timeline, beats, 'LukeFish-Java-Walkthrough.mp4');
  assert.equal((html.match(/data-chapter=/g) ?? []).length, 8);
  assert.equal((html.match(/<details>/g) ?? []).length, 33);
  assert.equal(/[ \t]+$/m.test(html), false);
});
