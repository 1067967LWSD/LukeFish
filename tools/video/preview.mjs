import http from 'node:http';
import { createReadStream } from 'node:fs';
import { stat } from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const args = process.argv.slice(2);
const outputIndex = args.indexOf('--output');
if (outputIndex >= 0 && (!args[outputIndex + 1] || args[outputIndex + 1].startsWith('--'))) {
  throw new Error('Missing output directory.');
}
const directory = path.resolve(outputIndex < 0
  ? path.join(path.dirname(fileURLToPath(import.meta.url)), '..', '..', 'video-output')
  : args[outputIndex + 1]);
const portIndex = args.indexOf('--port');
const port = portIndex < 0 ? 0 : Number(args[portIndex + 1]);
if (!Number.isInteger(port) || port < 0 || port > 65535) throw new Error('Invalid preview port.');
const allowed = new Map([
  ['index.html', 'text/html; charset=utf-8'], ['LukeFish-Java-Walkthrough.mp4', 'video/mp4'],
  ['poster.jpg', 'image/jpeg'], ['captions.vtt', 'text/vtt; charset=utf-8'],
  ['captions.srt', 'application/x-subrip'], ['transcript.md', 'text/plain; charset=utf-8'],
  ['chapters.txt', 'text/plain; charset=utf-8'],
]);

const server = http.createServer(async (request, response) => {
  try {
    if (request.method !== 'GET' && request.method !== 'HEAD') {
      response.writeHead(405, { Allow: 'GET, HEAD' }).end('Method not allowed');
      return;
    }
    const pathname = new URL(request.url, 'http://localhost').pathname;
    const filename = pathname === '/' ? 'index.html' : pathname.slice(1);
    const type = allowed.get(filename);
    if (!type) { response.writeHead(404).end('Not found'); return; }
    const fullPath = path.join(directory, filename);
    const { size } = await stat(fullPath);
    let start = 0;
    let end = size - 1;
    let status = 200;
    const headers = { 'Content-Type': type, 'Accept-Ranges': 'bytes',
      'Cache-Control': 'no-cache', 'X-Content-Type-Options': 'nosniff' };
    if (request.headers.range) {
      const range = /^bytes=(\d*)-(\d*)$/.exec(request.headers.range);
      if (!range || (!range[1] && !range[2])) {
        response.writeHead(416, { 'Content-Range': `bytes */${size}` }).end();
        return;
      }
      if (range[1]) {
        start = Number(range[1]);
        end = range[2] ? Math.min(Number(range[2]), end) : end;
      } else {
        start = Math.max(0, size - Number(range[2]));
      }
      if (!Number.isSafeInteger(start) || !Number.isSafeInteger(end) || start >= size || start > end) {
        response.writeHead(416, { 'Content-Range': `bytes */${size}` }).end();
        return;
      }
      status = 206;
      headers['Content-Range'] = `bytes ${start}-${end}/${size}`;
    }
    headers['Content-Length'] = end - start + 1;
    response.writeHead(status, headers);
    if (request.method === 'HEAD') { response.end(); return; }
    const stream = createReadStream(fullPath, { start, end });
    stream.on('error', error => {
      console.error(error);
      response.destroy(error);
    });
    response.on('close', () => stream.destroy());
    stream.pipe(response);
  } catch (error) {
    console.error(error);
    if (!response.headersSent) response.writeHead(error.code === 'ENOENT' ? 404 : 500);
    response.end('Could not read the requested video asset.');
  }
});
server.on('error', error => { console.error(error); process.exitCode = 1; });
server.listen(port, '127.0.0.1', () => {
  console.log(`Video preview: http://127.0.0.1:${server.address().port}`);
});
