import { clock, escapeXml as html } from './video.mjs';

export function playerHtml(lesson, timeline, beats, movieName) {
  const sections = beats.filter(beat => beat.beat === 0).map(beat => `<details>
    <summary><button data-seek="${beat.start}" aria-label="Play ${html(beat.scene.title)}">${clock(beat.start).slice(3)}</button>
      ${html(beat.scene.title)}</summary>
    ${beat.scene.source ? `<p class="source">Read: <code>${html(beat.scene.source.file)}</code></p>` : ''}
    ${beat.scene.beats.map(text => `<p>${html(text)}</p>`).join('')}</details>`).join('\n');
  return `<!doctype html>
<html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>${html(lesson.title)}</title>
<style>
*{box-sizing:border-box}body{margin:0;background:#0b1220;color:#edf4fc;font:17px/1.6 "Segoe UI",sans-serif}
main{max-width:1460px;margin:auto;padding:30px}header{margin-bottom:24px}.eyebrow{color:#69e3b5;font-weight:700;letter-spacing:.12em;font-size:13px}
h1{font-size:clamp(26px,4vw,42px);line-height:1.15;margin:10px 0}h2{font-size:23px}p{color:#b6c4d8}
.layout{display:grid;grid-template-columns:minmax(0,1fr) 285px;gap:24px;align-items:start}
video{width:100%;display:block;background:#000;border:1px solid #2b4056;border-radius:12px;aspect-ratio:16/9}
nav{border:1px solid #2b4056;border-radius:12px;padding:18px;background:#111e31}
nav h2{margin:0 0 12px;font-size:19px}button{font:inherit;cursor:pointer}
nav button{display:block;width:100%;text-align:left;border:0;border-left:3px solid transparent;border-radius:5px;background:transparent;color:#b6c4d8;padding:9px 10px;margin:4px 0}
nav button:hover,nav button.active{color:#edf4fc;background:#19343c;border-left-color:#69e3b5}
nav time{font-size:13px;color:#69e3b5;display:block}a{color:#79caff}a:hover{color:#b5e3ff}
.downloads{display:flex;gap:18px;flex-wrap:wrap;margin:16px 0}.note{font-size:14px}
details{border:1px solid #2b4056;border-radius:9px;padding:14px 18px;margin:10px 0;background:#101c2e}
summary{cursor:pointer;font-weight:600}summary button{margin-right:10px;padding:2px 8px;border:1px solid #3c665f;border-radius:5px;background:#17332f;color:#69e3b5;font-size:14px}
details p{max-width:100ch}.source{font-size:14px}code{color:#79caff;overflow-wrap:anywhere}
button:focus-visible,a:focus-visible,summary:focus-visible{outline:3px solid #79caff;outline-offset:3px}
#error{color:#ff929e}footer{margin-top:25px;font-size:14px;color:#8e9eb5}
@media(max-width:850px){main{padding:16px}.layout{grid-template-columns:1fr}nav{display:grid;grid-template-columns:1fr 1fr}nav h2{grid-column:1/-1}}
</style></head><body><main>
<header><div class="eyebrow">LUKEFISH / JAVA CLASSROOM</div><h1>${html(lesson.title)}</h1>
<p>${clock(timeline.duration).slice(3)} &middot; Beginner lesson &middot; 1080p &middot; Local TTS narration</p></header>
<div class="layout"><section aria-label="Video lesson">
<video id="video" controls preload="metadata" poster="poster.jpg">
<source src="${movieName}" type="video/mp4">
<track kind="captions" src="captions.vtt" srclang="en" label="English (also visible in the picture)">
Your browser cannot play this video. <a href="${movieName}">Download the MP4.</a>
</video><p id="error" role="alert" hidden></p>
<div class="downloads"><a href="${movieName}" download>Download video</a><a href="transcript.md" download>Transcript</a>
<a href="captions.srt" download>Subtitles</a><a href="chapters.txt" download>Chapter timestamps</a></div>
<p class="note">Captions are already visible in the video. Optional text captions are also available through the player.
Fullscreen is recommended for reading code. This page works offline beside the downloaded MP4.</p></section>
<nav aria-label="Video chapters"><h2>Jump to a chapter</h2>
${timeline.chapters.map(chapter => `<button data-seek="${chapter.start}" data-chapter="${chapter.number}">
<time>${clock(chapter.start).slice(3)}</time>${html(chapter.title)}</button>`).join('\n')}
</nav></div>
<section aria-label="Searchable lesson transcript"><h2>Follow along with the code</h2>${sections}</section>
<footer>Actual repository excerpts and application capture. Search trees are labeled teaching examples.
No cloud speech service or external chess engine was used.</footer>
</main><script>
const video=document.querySelector('#video');
const error=document.querySelector('#error');
document.querySelectorAll('[data-seek]').forEach(button=>button.addEventListener('click',async event=>{
  event.preventDefault();
  video.currentTime=Number(button.dataset.seek);
  video.scrollIntoView({behavior:'smooth',block:'center'});
  try{await video.play();error.hidden=true;}catch(problem){error.textContent='Playback could not start: '+problem.message;error.hidden=false;}
}));
const chapters=[...document.querySelectorAll('[data-chapter]')];
video.addEventListener('timeupdate',()=>{
  const active=chapters.findLast(button=>Number(button.dataset.seek)<=video.currentTime+.1);
  for(const button of chapters){button.classList.toggle('active',button===active);button.setAttribute('aria-current',button===active?'true':'false');}
});
video.addEventListener('error',()=>{error.textContent='Video playback failed. Download the MP4, or serve this folder with the preview command.';error.hidden=false;});
</script></body></html>`.replace(/[ \t]+$/gm, '');
}
