const express = require('express');
const { execFile } = require('child_process');
const fs = require('fs');
const os = require('os');
const path = require('path');
const crypto = require('crypto');

const app = express();
const PORT = process.env.PORT || 8080;
const TMP = path.join(os.tmpdir(), 'dl-files');
const MAX_SECONDS = 3600;       // max video length: 60 min
const MAX_PARALLEL = 2;         // downloads at the same time
const FILE_TTL_MS = 10 * 60 * 1000; // delete files after 10 min
let running = 0;

fs.rmSync(TMP, { recursive: true, force: true });
fs.mkdirSync(TMP, { recursive: true });

app.disable('x-powered-by');
app.set('trust proxy', 1);

// simple per-IP rate limit (in memory, nothing saved)
const hits = new Map();
function limit(max, windowMs) {
  return (req, res, next) => {
    const now = Date.now();
    const list = (hits.get(req.ip + req.path) || []).filter(t => now - t < windowMs);
    if (list.length >= max) return res.status(429).json({ error: 'Too many requests. Please wait a minute.' });
    list.push(now);
    hits.set(req.ip + req.path, list);
    next();
  };
}
setInterval(() => hits.clear(), 10 * 60 * 1000);

// delete old files
setInterval(() => {
  const now = Date.now();
  for (const f of fs.readdirSync(TMP)) {
    const p = path.join(TMP, f);
    try { if (now - fs.statSync(p).mtimeMs > FILE_TTL_MS) fs.unlinkSync(p); } catch (e) {}
  }
}, 60 * 1000);

const validId = id => typeof id === 'string' && /^[\w-]{11}$/.test(id);
const urlOf = id => 'https://www.youtube.com/watch?v=' + id;

function ytdlp(args, timeout) {
  return new Promise((resolve, reject) => {
    execFile('yt-dlp', args, { timeout, maxBuffer: 30 * 1024 * 1024 }, (err, stdout, stderr) => {
      if (err) { console.error('yt-dlp error:', (stderr || err.message).toString().slice(0, 500)); return reject(err); }
      resolve(stdout);
    });
  });
}

// video info + available qualities
app.get('/api/info', limit(20, 60000), async (req, res) => {
  const id = req.query.id;
  if (!validId(id)) return res.status(400).json({ error: 'Invalid link' });
  try {
    const out = await ytdlp(['--dump-single-json', '--no-playlist', '--no-warnings', urlOf(id)], 60000);
    const info = JSON.parse(out);
    if (info.duration && info.duration > MAX_SECONDS)
      return res.status(413).json({ error: 'Video is too long (max 60 minutes).' });

    const maxH = Math.max(0, ...(info.formats || []).filter(f => f.vcodec && f.vcodec !== 'none').map(f => f.height || 0));
    let hs = [1080, 720, 480, 360].filter(h => h <= maxH);
    if (!hs.length) hs = [360];
    const formats = hs.map(h => ({ label: 'MP4 - ' + h + 'p', value: 'mp4_' + h }));
    formats.push({ label: 'MP3 - Audio only', value: 'mp3' });
    res.json({ title: info.title, formats });
  } catch (e) {
    res.status(502).json({ error: 'Could not fetch this video.' });
  }
});

// download
app.get('/api/download', limit(6, 60000), async (req, res) => {
  const { id, format } = req.query;
  const tk = /^\w{6,24}$/.test(String(req.query.t || '')) ? req.query.t : null;
  const mark = v => { if (tk) res.setHeader('Set-Cookie', 'dl_' + tk + '=' + v + '; Path=/; Max-Age=120; SameSite=Lax'); };
  if (!validId(id) || !/^(mp4_(1080|720|480|360)|mp3)$/.test(format || ''))
    return res.status(400).send('Invalid request');
  if (running >= MAX_PARALLEL) { mark('busy'); return res.status(503).send('Server is busy. Please try again in a minute.'); }

  const name = crypto.randomBytes(8).toString('hex');
  const base = path.join(TMP, name);
  const common = ['--no-playlist', '--no-warnings', '--max-filesize', '500M',
                  '--match-filter', 'duration<=' + MAX_SECONDS, '-o', base + '.%(ext)s'];
  let args, ext;
  if (format === 'mp3') {
    ext = 'mp3';
    args = ['-x', '--audio-format', 'mp3', '--audio-quality', '0', ...common];
  } else {
    ext = 'mp4';
    const h = format.split('_')[1];
    args = ['-f',
      `bv*[height<=${h}][vcodec^=avc1]+ba[ext=m4a]/b[height<=${h}][ext=mp4]/bv*[height<=${h}]+ba/b[height<=${h}]`,
      '--merge-output-format', 'mp4', ...common];
  }

  running++;
  try {
    await ytdlp([...args, urlOf(id)], 10 * 60 * 1000);
    const file = fs.readdirSync(TMP).find(f => f.startsWith(name + '.'));
    if (!file) throw new Error('no file');
    const full = path.join(TMP, file);
    mark('ok');
    res.download(full, 'video-' + id + '.' + ext, () => fs.unlink(full, () => {}));
  } catch (e) {
    for (const f of fs.readdirSync(TMP)) if (f.startsWith(name + '.')) fs.unlink(path.join(TMP, f), () => {});
    if (!res.headersSent) { mark('err'); res.status(500).send('Download failed. Please try another quality or video.'); }
  } finally {
    running--;
  }
});

app.use(express.static(path.join(__dirname, '..', 'public')));

app.listen(PORT, '0.0.0.0', () => console.log('Running on http://localhost:' + PORT));
