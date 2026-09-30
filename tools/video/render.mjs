import { createHash } from 'node:crypto';
import { spawn } from 'node:child_process';
import { mkdir, readFile, writeFile, access, rename } from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import sharp from 'sharp';
import { assText, captions, clock, extractSource, FPS, HEIGHT, LEAD, subtitleText, TAIL, WIDTH } from './video.mjs';
import { slideSvg } from './slides.mjs';
import { playerHtml } from './player.mjs';

const here = path.dirname(fileURLToPath(import.meta.url));
const root = path.resolve(here, '..', '..');
const options = process.argv.slice(2);
const value = name => {
  const index = options.indexOf(name);
  if (index < 0) return undefined;
  if (!options[index + 1] || options[index + 1].startsWith('--')) throw new Error(`Missing value for ${name}`);
  return options[index + 1];
};
const output = path.resolve(value('--output') ?? path.join(root, 'video-output'));
const work = path.join(output, 'work');
const ffmpeg = process.env.FFMPEG_PATH ?? 'ffmpeg';
const ffprobe = process.env.FFPROBE_PATH ?? 'ffprobe';
const movieName = 'LukeFish-Java-Walkthrough.mp4';
const lesson = JSON.parse(await readFile(path.join(here, 'lesson.json'), 'utf8'));
const hash = value => createHash('sha256').update(value).digest('hex');

function run(command, args, cwd = root, echo = false) {
  return new Promise((resolve, reject) => {
    const child = spawn(command, args, { cwd, windowsHide: true, shell: false });
    let stdout = '';
    let stderr = '';
    child.stdout.on('data', chunk => {
      stdout += chunk;
      if (echo) process.stdout.write(chunk);
    });
    child.stderr.on('data', chunk => { stderr = (stderr + chunk).slice(-16000); });
    child.on('error', reject);
    child.on('close', code => code === 0 ? resolve(stdout)
      : reject(new Error(`${command} exited ${code}:\n${stderr || stdout}`)));
  });
}

async function exists(filename) {
  try {
    await access(filename);
    return true;
  } catch (error) {
    if (error.code !== 'ENOENT') throw error;
    return false;
  }
}

async function probe(filename) {
  return JSON.parse(await run(ffprobe, ['-v', 'error', '-show_format', '-show_streams', '-show_chapters',
    '-of', 'json', filename]));
}

async function prepare() {
  await mkdir(work, { recursive: true });
  const narratorHash = hash(await readFile(path.join(here, 'narrate.ps1')));
  const screenshot = path.join(output, 'app.png');
  if (!await exists(screenshot)) throw new Error(`Missing ${screenshot}. Run chess.UiSmokeTest with that output filename first.`);
  const beats = [];
  const sourceMap = [];
  const thumbnails = [];
  for (const [index, scene] of lesson.scenes.entries()) {
    const source = await extractSource(scene.source, root);
    if (scene.source) {
      const raw = await readFile(path.join(root, scene.source.file));
      sourceMap.push({ scene: scene.id, file: scene.source.file, sha256: hash(raw), lines: source });
    }
    for (const [beat, narration] of scene.beats.entries()) {
      const id = `${String(index + 1).padStart(2, '0')}-${scene.id}-${beat + 1}`;
      const svg = await slideSvg(lesson, scene, source, index, beat, screenshot);
      const png = await sharp(Buffer.from(svg)).png().toBuffer();
      await writeFile(path.join(work, `${id}.png`), png);
      if (!beat) thumbnails.push(await sharp(png).resize(384, 216).png().toBuffer());
      beats.push({ id, text: narration, scene, sceneIndex: index, beat,
        imageHash: hash(png), audioHash: hash(`${narratorHash}\n${lesson.voice}\n${lesson.rate}\n${narration}`) });
    }
  }
  await sharp({ create: { width: 1536, height: Math.ceil(thumbnails.length / 4) * 216,
    channels: 3, background: '#0b1220' } })
    .composite(thumbnails.map((input, index) => ({ input, left: (index % 4) * 384, top: Math.floor(index / 4) * 216 })))
    .png().toFile(path.join(output, 'storyboard.png'));
  await sharp(path.join(work, `${beats[0].id}.png`)).resize(1280, 720).jpeg({ quality: 90 })
    .toFile(path.join(output, 'poster.jpg'));
  await writeFile(path.join(output, 'sources.json'), JSON.stringify(sourceMap, null, 2) + '\n');
  return beats;
}

async function narrate(beats) {
  let cache = {};
  const cacheFile = path.join(work, 'audio-cache.json');
  if (await exists(cacheFile)) cache = JSON.parse(await readFile(cacheFile, 'utf8'));
  const missing = [];
  for (const beat of beats) {
    if (cache[beat.id] !== beat.audioHash || !await exists(path.join(work, `${beat.id}.wav`))
        || !await exists(path.join(work, `${beat.id}.words.json`))) missing.push({ id: beat.id, text: beat.text });
  }
  if (missing.length) {
    const input = path.join(work, 'narration-input.json');
    await writeFile(input, JSON.stringify(missing));
    const powershell = path.join(process.env.SystemRoot ?? 'C:\\Windows', 'System32', 'WindowsPowerShell', 'v1.0', 'powershell.exe');
    await run(powershell, ['-NoProfile', '-NonInteractive', '-File', path.join(here, 'narrate.ps1'),
      '-InputFile', input, '-OutputDirectory', work, '-Voice', lesson.voice, '-Rate', String(lesson.rate)], root, true);
    cache = Object.fromEntries(beats.map(beat => [beat.id, beat.audioHash]));
    await writeFile(cacheFile, JSON.stringify(cache));
  }
  let duration = 0;
  const allCues = [];
  const chapters = [];
  for (const beat of beats) {
    const audio = await probe(path.join(work, `${beat.id}.wav`));
    beat.audioDuration = Number(audio.format.duration);
    if (!audio.streams.some(stream => stream.codec_type === 'audio') || !(beat.audioDuration > 1)) {
      throw new Error(`Missing or empty narration: ${beat.id}`);
    }
    const words = JSON.parse(await readFile(path.join(work, `${beat.id}.words.json`), 'utf8'));
    beat.cues = captions(beat.text, words, beat.audioDuration);
    beat.duration = Math.ceil((beat.audioDuration + LEAD + TAIL) * FPS) / FPS;
    beat.start = duration;
    if (!chapters.length || chapters.at(-1).number !== beat.scene.chapter) {
      if (chapters.length) chapters.at(-1).end = duration;
      chapters.push({ number: beat.scene.chapter, title: lesson.chapters[beat.scene.chapter - 1], start: duration });
    }
    allCues.push(...beat.cues.map(cue => ({ ...cue, start: cue.start + duration, end: cue.end + duration })));
    duration += beat.duration;
  }
  chapters.at(-1).end = duration;
  if (duration < 900 || duration > 1200) {
    throw new Error(`Lesson length is ${clock(duration)}, outside the requested 15-20 minutes. Adjust the script or speech rate.`);
  }
  console.log(`Narration ready: ${clock(duration)} across ${beats.length} visual beats.`);
  return { duration, chapters, cues: allCues };
}

function metadata(timeline) {
  return `;FFMETADATA1\ntitle=${lesson.title}\nartist=LukeFish classroom\ncomment=Local TTS; repository-based Java lesson.\n`
    + timeline.chapters.map(chapter => `[CHAPTER]\nTIMEBASE=1/1000\nSTART=${Math.round(chapter.start * 1000)}\n`
      + `END=${Math.round(chapter.end * 1000)}\ntitle=${chapter.title}\n`).join('');
}

async function encode(beats, timeline) {
  const build = (await run(ffmpeg, ['-version'])).split(/\r?\n/)[0];
  for (const [index, beat] of beats.entries()) {
    const subtitles = assText(beat.cues);
    await writeFile(path.join(work, `${beat.id}.ass`), subtitles);
    const signature = hash(`render-v2-long-gop\n${build}\n${beat.imageHash}\n${beat.audioHash}\n${subtitles}\n${beat.duration}`);
    const marker = path.join(work, `${beat.id}.encoded`);
    const segment = path.join(work, `${beat.id}.mkv`);
    if (!await exists(segment) || !await exists(marker) || await readFile(marker, 'utf8') !== signature) {
      await run(ffmpeg, ['-hide_banner', '-loglevel', 'error', '-y',
        '-loop', '1', '-framerate', String(FPS), '-i', `${beat.id}.png`, '-i', `${beat.id}.wav`,
        '-vf', `subtitles=filename=${beat.id}.ass,fade=t=in:st=0:d=0.16`,
        '-af', `adelay=${LEAD * 1000},apad=whole_dur=${beat.duration},loudnorm=I=-18:TP=-1.5:LRA=9,aresample=48000`,
        '-t', String(beat.duration), '-c:v', 'libx264', '-preset', 'veryfast', '-tune', 'stillimage',
        '-crf', '20', '-pix_fmt', 'yuv420p', '-r', String(FPS), '-g', '240', '-threads', '4',
        '-c:a', 'pcm_s16le', '-ar', '48000', '-ac', '1', `${beat.id}.mkv`], work);
      await writeFile(marker, signature);
    }
    console.log(`Encoded ${index + 1}/${beats.length}: ${beat.id}`);
  }
  await writeFile(path.join(work, 'segments.txt'), beats.map(beat => `file '${beat.id}.mkv'`).join('\n'));
  await writeFile(path.join(work, 'chapters.ffmeta'), metadata(timeline));
  const temporary = path.join(output, 'LukeFish-Java-Walkthrough.building.mp4');
  await run(ffmpeg, ['-hide_banner', '-loglevel', 'error', '-y', '-f', 'concat', '-safe', '0',
    '-i', 'segments.txt', '-f', 'ffmetadata', '-i', 'chapters.ffmeta',
    '-i', path.join(output, 'captions.srt'), '-map', '0:v:0', '-map', '0:a:0', '-map', '2:0',
    '-map_metadata', '1', '-map_chapters', '1', '-c:v', 'copy', '-c:a', 'aac', '-b:a', '128k',
    '-c:s', 'mov_text', '-metadata:s:s:0', 'language=eng', '-disposition:s:0', '0',
    '-movflags', '+faststart', temporary], work);
  const info = await probe(temporary);
  const video = info.streams.find(stream => stream.codec_type === 'video');
  const audio = info.streams.find(stream => stream.codec_type === 'audio');
  if (video?.width !== WIDTH || video?.height !== HEIGHT || video?.codec_name !== 'h264'
      || audio?.codec_name !== 'aac' || info.chapters.length !== lesson.chapters.length
      || Math.abs(Number(info.format.duration) - timeline.duration) > 0.25) {
    throw new Error('Encoded video does not match expected picture, audio, chapters, or duration.');
  }
  await run(ffmpeg, ['-hide_banner', '-loglevel', 'error', '-xerror', '-i', temporary,
    '-map', '0:v:0', '-map', '0:a:0', '-f', 'null', '-']);
  try {
    await rename(temporary, path.join(output, movieName));
  } catch (error) {
    if (error.code !== 'EPERM' && error.code !== 'EBUSY') throw error;
    throw new Error(`The finished video is verified at ${temporary}, but Windows could not replace the published MP4. `
      + 'Close players using the previous video, then rerun the build.', { cause: error });
  }
  info.format.filename = movieName;
  await writeFile(path.join(output, 'media-info.json'), JSON.stringify(info, null, 2) + '\n');
}

async function companionFiles(beats, timeline) {
  await writeFile(path.join(output, 'captions.srt'), subtitleText(timeline.cues, 'srt'));
  await writeFile(path.join(output, 'captions.vtt'), subtitleText(timeline.cues, 'vtt'));
  await writeFile(path.join(output, 'chapters.txt'),
    timeline.chapters.map(chapter => `${clock(chapter.start)} ${chapter.title}`).join('\n') + '\n');
  const transcript = [`# ${lesson.title}\n`, 'Beginner lesson. Narration is synthesized locally with Windows speech.\n',
    'Code excerpts are read directly from the repository; teaching diagrams are labeled separately.\n'];
  for (const beat of beats) {
    if (beat.beat === 0) {
      transcript.push(`\n## ${clock(beat.start)} - ${beat.scene.title}\n`);
      if (beat.scene.source) transcript.push(`Read: \`${beat.scene.source.file}\`\n`);
    }
    transcript.push(beat.text + '\n');
  }
  await writeFile(path.join(output, 'transcript.md'), transcript.join('\n'));
  await writeFile(path.join(output, 'index.html'), playerHtml(lesson, timeline, beats, movieName));
  await writeFile(path.join(output, 'timeline.json'), JSON.stringify({
    duration: timeline.duration, voice: lesson.voice, rate: lesson.rate, chapters: timeline.chapters,
    beats: beats.map(({ id, start, duration, audioDuration }) => ({ id, start, duration, audioDuration })),
  }, null, 2) + '\n');
}

const beats = await prepare();
if (options.includes('--slides-only')) {
  console.log(`Prepared ${beats.length} 1080p frames and ${path.join(output, 'storyboard.png')}`);
} else {
  await run(ffmpeg, ['-version']);
  await run(ffprobe, ['-version']);
  const timeline = await narrate(beats);
  await companionFiles(beats, timeline);
  if (options.includes('--audio-only')) {
    console.log(`Prepared narration and captions in ${output}`);
  } else {
    await encode(beats, timeline);
    console.log(`Created ${path.join(output, movieName)} (${clock(timeline.duration)})`);
  }
}
