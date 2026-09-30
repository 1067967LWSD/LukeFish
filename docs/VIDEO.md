# Narrated Java walkthrough

**Inside LukeFish: Learn Java through Chess** is a 15-20 minute beginner lesson.
It follows a move from input through the model, evaluation, search, and Swing
controller, teaching the Java concepts used at each boundary.

**[Watch or download the checked-in MP4](video/LukeFish-Java-Walkthrough.mp4).**
The companion [architecture PowerPoint](LukeFish-Architecture.pptx) maps the
classes and their relationships using editable diagrams and speaker notes.

The lesson has eight chapters:

1. Meet the engine and its three packages.
2. Java building blocks: compilation, classes, objects, types, arrays, records,
   equality, and generic collections.
3. Legal moves, special rules, undo, game history, enums, and exceptions.
4. Evaluation, score perspective, game phase, and immutable settings.
5. Recursion, negamax, iterative deepening, `finally`, pruning, ordering, and
   quiescence.
6. Position hashing and history-sensitive caching.
7. Swing callbacks, background workers, cancellation, and stale-result checks.
8. Tests, debugging, and controlled experiments.

The editable narration and storyboard are in
[`tools\video\lesson.json`](../tools/video/lesson.json). Code excerpts are extracted
from the actual Java files using checked anchors and retain their original line
numbers. Search trees are explicitly labeled teaching examples, not engine
measurements. The application image comes from the existing desktop smoke check.

## Watch the generated lesson

The producer writes these files to `video-output` by default, or to the directory
passed with `--output`:

| File | Purpose |
| --- | --- |
| `LukeFish-Java-Walkthrough.mp4` | 1080p H.264 video, AAC narration, visible captions, embedded chapters, and an optional subtitle track. |
| `index.html` | Offline player with chapter buttons and a searchable, timestamped transcript. |
| `captions.srt`, `captions.vtt` | Speech-event-timed subtitles for players and editors. |
| `transcript.md`, `chapters.txt` | Read-along text and chapter timestamps. |
| `storyboard.png`, `poster.jpg` | Visual overview and video poster. |
| `sources.json`, `timeline.json`, `media-info.json` | Source hashes, excerpt locations, timing, and encoded media information. |

Open the MP4 directly, or open `index.html` beside the other files. Use fullscreen
to read code. Captions are already visible in the picture; enabling the optional
subtitle track may display them twice. If a browser blocks local files, use the
loopback-only preview server below.

A published copy of the MP4 and its player, captions, transcript, and chapter
timestamps is checked in under `docs\video`. After cloning the repository, open
`docs\video\index.html` locally, or run:

```powershell
node .\tools\video\preview.mjs --output .\docs\video
```

GitHub displays the HTML's source rather than running the player. The MP4 can
be downloaded directly from its file page. Rebuild scratch output and caches
remain ignored under `video-output`.

## Rebuild on Windows

The chess application itself still has **no third-party Java dependencies**.
Only this optional media producer needs:

- JDK 17 or newer, available through `JAVA_HOME` or `PATH`.
- Node.js 20 or newer and npm.
- Windows PowerShell 5.1 and an installed English Windows speech voice.
- FFmpeg and FFprobe on `PATH`; FFmpeg must include `libx264` and the
  `subtitles`/libass filter. On Windows ARM64, an x64 FFmpeg build can run under
  Windows emulation.

Alternatively, set `FFMPEG_PATH` and `FFPROBE_PATH` to the full executable paths.
The default voice is `Microsoft David Desktop`; edit `voice` and `rate` in
`lesson.json` to choose another installed voice. The producer fails explicitly
if that voice is unavailable. Speech and rendering run locally; no narration,
source code, or application image is sent to a cloud service.

From the repository root:

```powershell
.\build.ps1 -Test
npm ci --prefix .\tools\video
npm test --prefix .\tools\video

$video = Join-Path $PWD 'video-output'
New-Item -ItemType Directory -Path $video -Force | Out-Null

# Use the Java executable from JAVA_HOME when it is not on PATH.
$java = if ($env:JAVA_HOME) { Join-Path $env:JAVA_HOME 'bin\java.exe' } else { 'java' }
& $java -ea -cp .\out chess.UiSmokeTest (Join-Path $video 'app.png')
if ($LASTEXITCODE -ne 0) { throw 'The desktop capture failed.' }

node .\tools\video\render.mjs --output $video
node .\tools\video\preview.mjs --output $video
```

The capture requires a desktop display. It paints the application's own window,
not a screenshot of the user's desktop. The preview prints its local URL and
runs until stopped with Ctrl+C.

For an external output directory, use its absolute Windows path for `$video`.
Production takes several minutes. Audio and encoded segments are cached under
the output's `work` subdirectory with content fingerprints. A rerun reuses
unchanged work. Do not change lesson files during a build.
On Windows, close players using a previous MP4 before replacing it. If a player
locks the published file, the producer reports the error and retains the verified
`.building.mp4`; it does not delete the previous video.

`--slides-only` renders all 66 visual beats and the storyboard without speech or
encoding. `--audio-only` also produces narration, caption timing, and the player,
but does not produce a playable video. A full build checks that the runtime is
within 15-20 minutes, verifies resolution/audio/chapters/timing, and decodes the
finished media before publishing the final filename.

The source excerpts and text bounds are checked by `npm test`. For a substantial
lesson change, also inspect the storyboard, watch representative sections, and
confirm that the spoken explanation still matches the displayed code. If an
anchor becomes ambiguous after a Java edit, update the selection rather than
silently using a different method.

After a successful build, the `work` subdirectory is only a rebuild cache. The
MP4 and companion files outside it are the deliverables. Keep `index.html`,
`poster.jpg`, the MP4, captions, transcript, and chapter timestamps together when
sharing the offline player.

## Rebuild the architecture PowerPoint

The deck covers the 15 top-level production Java types, important nested
records and enums, ownership, callbacks, search collaboration, and the
event-dispatch-thread/background-worker boundary. All diagrams are editable
PowerPoint shapes and text. Speaker notes cite the source files for each slide.

```powershell
npm ci --prefix .\tools\architecture
npm run build --prefix .\tools\architecture
```

This writes `docs\LukeFish-Architecture.pptx`. The builder checks its source
references and slide bounds. If desktop PowerPoint is installed, the optional
`tools\architecture\verify.ps1` opens the deck read-only, checks its actual text
bounds, and exports slide previews. It closes only its own presentation.
