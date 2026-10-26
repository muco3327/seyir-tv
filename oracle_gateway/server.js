const express = require('express');
const axios = require('axios');
const cors = require('cors');
const fs = require('fs');
const path = require('path');
const { URL } = require('url');

const app = express();
app.use(cors());

const PORT = 3000;

// Load Verified Channels directly
let curatedChannels = [];
function reloadChannels() {
    try {
        const raw = fs.readFileSync(path.join(__dirname, 'verified_channels.json'), 'utf-8');
        curatedChannels = JSON.parse(raw);
        console.log(`Loaded ${curatedChannels.length} curated master channels.`);
    } catch (e) {
        console.error('Failed to load verified_channels.json', e.message);
    }
}
reloadChannels();

// API Channels endpoint
app.get('/api/channels', (req, res) => {
    res.json(curatedChannels);
});

// M3U Playlist endpoint
app.get('/playlist.m3u', (req, res) => {
    res.setHeader('Content-Type', 'application/vnd.apple.mpegurl; charset=utf-8');
    let m3u = '#EXTM3U\n';
    const host = req.headers.host || '150.230.29.27:3000';
    for (const ch of curatedChannels) {
        const primaryUrl = (ch.urls && ch.urls.length > 0) ? ch.urls[0] : '';
        if (!primaryUrl) continue;
        const proxied = `http://${host}/proxy?url=${encodeURIComponent(primaryUrl)}`;
        m3u += `#EXTINF:-1 tvg-id="${ch.id}" tvg-name="${ch.name}" group-title="${ch.category || 'Spor'}",${ch.name}\n`;
        m3u += `${proxied}\n`;
    }
    res.send(m3u);
});

// Proxy endpoint: Adapts headers per host and rewrites m3u8
app.get('/proxy', async (req, res) => {
    const targetUrl = req.query.url;
    if (!targetUrl) return res.status(400).send('URL required');

    const isSegment = Boolean(
        targetUrl.match(/\.(ts|jpg|aac|m4s)(\?|$)/i) ||
        targetUrl.includes('_seg_') ||
        targetUrl.includes('/segment')
    );

    try {
        const u = new URL(targetUrl);
        const headers = {
            'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36'
        };

        if (
            targetUrl.includes('evrenesoglu') ||
            targetUrl.includes('andro') ||
            targetUrl.includes('google.d6e3eae5e065d5.help') ||
            targetUrl.includes('fastly.net') ||
            targetUrl.includes('mangeinde')
        ) {
            headers['Referer'] = 'https://mahsunsports57.xyz/';
            headers['Origin'] = 'https://mahsunsports57.xyz';
        } else if (targetUrl.includes('tulix.tv')) {
            // Tulix requires clean headers
        } else {
            headers['Referer'] = `${u.protocol}//${u.host}/`;
            headers['Origin'] = `${u.protocol}//${u.host}`;
        }

        const response = await axios({
            method: 'get',
            url: targetUrl,
            headers: headers,
            responseType: isSegment ? 'stream' : 'arraybuffer',
            timeout: 9000,
            validateStatus: (status) => status >= 200 && status < 400
        });

        res.setHeader('Access-Control-Allow-Origin', '*');

        if (isSegment) {
            const contentType = response.headers['content-type'] || 'video/mp2t';
            res.setHeader('Content-Type', contentType);
            return response.data.pipe(res);
        }

        const contentType = response.headers['content-type'] || '';
        const bodyStr = response.data.toString('utf-8');

        // Rewrite M3U8 child segments
        if (bodyStr.startsWith('#EXTM3U') || targetUrl.includes('.m3u8')) {
            res.setHeader('Content-Type', 'application/vnd.apple.mpegurl');
            const lines = bodyStr.split('\n');
            const rewritten = lines.map(line => {
                const trimmed = line.trim();
                if (!trimmed || trimmed.startsWith('#')) return line;
                try {
                    const resolvedUrl = new URL(trimmed, targetUrl).href;
                    return '/proxy?url=' + encodeURIComponent(resolvedUrl);
                } catch (e) {
                    return line;
                }
            }).join('\n');
            return res.send(rewritten);
        }

        res.setHeader('Content-Type', contentType || 'video/mp2t');
        res.send(response.data);
    } catch (err) {
        if (isSegment) {
            // Live HLS segment expired or unavailable: return 404 so player advances seamlessly
            return res.status(404).end();
        }
        const upstreamStatus = err.response ? err.response.status : 502;
        res.status(upstreamStatus).send('Stream error: ' + err.message);
    }
});

// Clean Web Player Interface
app.get('/', (req, res) => {
    res.send(`
<!DOCTYPE html>
<html lang="tr">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
    <title>Seyir TV - Canlı Yayın & Web Player</title>
    <script src="https://cdn.jsdelivr.net/npm/hls.js@latest"></script>
    <script src="https://cdn.jsdelivr.net/npm/mpegts.js@latest/dist/mpegts.js"></script>
    <style>
        * { box-sizing: border-box; }
        body { margin: 0; background: #0b0f19; color: #fff; font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif; display: flex; height: 100vh; overflow: hidden; }
        #sidebar { width: 340px; background: #111827; border-right: 1px solid #1f2937; display: flex; flex-direction: column; }
        #header { padding: 16px; font-weight: bold; font-size: 18px; border-bottom: 2px solid #2563eb; background: #1e293b; display: flex; align-items: center; justify-content: space-between; }
        #search { padding: 12px 14px; background: #0f172a; border: 1px solid #334155; color: #fff; border-radius: 6px; margin: 10px 14px; outline: none; font-size: 16px; }
        #list { flex: 1; overflow-y: auto; }
        #main { flex: 1; display: flex; flex-direction: column; align-items: center; justify-content: center; padding: 20px; background: #030712; }
        .channel-item { padding: 12px 16px; border-bottom: 1px solid #1f2937; cursor: pointer; transition: all 0.15s; display: flex; justify-content: space-between; align-items: center; }
        .channel-item:hover { background: #1e293b; }
        .channel-item.active { background: #2563eb; color: #fff; font-weight: bold; }
        .channel-title { font-size: 15px; font-weight: 500; }
        .channel-badge { font-size: 11px; background: rgba(255,255,255,0.15); padding: 3px 8px; border-radius: 4px; }
        video { width: 100%; max-width: 980px; height: 550px; border-radius: 10px; background: #000; box-shadow: 0 10px 25px rgba(0,0,0,0.5); }
        #info-bar { width: 100%; max-width: 980px; margin-top: 14px; display: flex; justify-content: space-between; align-items: center; }
        .status-tag { font-size: 13px; background: #10b981; padding: 4px 10px; border-radius: 12px; font-weight: 600; }
        @media (max-width: 768px) {
            body { flex-direction: column; }
            #sidebar { width: 100%; height: 45vh; }
            #main { height: 55vh; padding: 10px; }
            video { height: 80%; }
        }
    </style>
</head>
<body>
    <div id="sidebar">
        <div id="header">
            <span>📺 Seyir TV</span>
            <span style="font-size: 12px; color: #10b981;">● Aktif</span>
        </div>
        <input type="text" id="search" placeholder="Kanal ara..." oninput="filterChannels()" />
        <div id="list"></div>
    </div>
    <div id="main">
        <video id="videoElement" controls autoplay playsinline></video>
        <div id="info-bar">
            <span id="playing-title" style="font-weight: bold; color: #fff;">Kanal seçin</span>
            <span id="status-tag" class="status-tag">Hazır</span>
        </div>
    </div>

    <script>
        const video = document.getElementById('videoElement');
        let channels = [];
        let currentChannel = null;
        let currentUrlIndex = 0;
        let hlsPlayer = null;
        let tsPlayer = null;

        function destroyPlayers() {
            if (hlsPlayer) { hlsPlayer.destroy(); hlsPlayer = null; }
            if (tsPlayer) { tsPlayer.destroy(); tsPlayer = null; }
        }

        async function init() {
            try {
                const res = await fetch('/api/channels');
                channels = await res.json();
                renderList(channels);
                if (channels.length > 0) playChannel(channels[0]);
            } catch (e) {
                document.getElementById('list').innerHTML = '<div style="padding: 20px; color: red;">Liste alınamadı!</div>';
            }
        }

        function renderList(items) {
            const listEl = document.getElementById('list');
            listEl.innerHTML = '';
            items.forEach((ch, idx) => {
                const div = document.createElement('div');
                div.className = 'channel-item' + (currentChannel && currentChannel.id === ch.id ? ' active' : (idx === 0 && !currentChannel ? ' active' : ''));
                div.innerHTML = '<div class="channel-title">' + ch.name + '</div><div class="channel-badge">' + ch.category + '</div>';
                div.onclick = () => {
                    document.querySelectorAll('.channel-item').forEach(e => e.classList.remove('active'));
                    div.classList.add('active');
                    playChannel(ch, 0);
                };
                listEl.appendChild(div);
            });
        }

        function filterChannels() {
            const q = document.getElementById('search').value.toLowerCase();
            const filtered = channels.filter(c => c.name.toLowerCase().includes(q) || c.category.toLowerCase().includes(q));
            renderList(filtered);
        }

        function playChannel(ch, urlIndex = 0) {
            currentChannel = ch;
            currentUrlIndex = urlIndex;
            destroyPlayers();

            if (!ch.urls || urlIndex >= ch.urls.length) {
                document.getElementById('status-tag').innerText = 'Kaynak tükendi';
                document.getElementById('status-tag').style.background = '#ef4444';
                return;
            }

            const rawUrl = ch.urls[urlIndex];
            document.getElementById('playing-title').innerText = '▶ ' + ch.name;
            document.getElementById('status-tag').innerText = 'Bağlanıyor...';
            document.getElementById('status-tag').style.background = '#3b82f6';

            const proxiedUrl = '/proxy?url=' + encodeURIComponent(rawUrl);

            let loaded = false;
            const fallbackTimer = setTimeout(() => {
                if (!loaded) {
                    playChannel(ch, urlIndex + 1);
                }
            }, 6000);

            function onReady() {
                loaded = true;
                clearTimeout(fallbackTimer);
                document.getElementById('status-tag').innerText = 'Canlı Yayın';
                document.getElementById('status-tag').style.background = '#10b981';
            }

            video.onplaying = onReady;

            if (Hls.isSupported()) {
                hlsPlayer = new Hls({ enableWorker: true, lowLatencyMode: true });
                hlsPlayer.loadSource(proxiedUrl);
                hlsPlayer.attachMedia(video);
                hlsPlayer.on(Hls.Events.MANIFEST_PARSED, () => video.play().catch(() => {}));
                hlsPlayer.on(Hls.Events.ERROR, (e, data) => {
                    if (data.fatal) {
                        clearTimeout(fallbackTimer);
                        playChannel(ch, urlIndex + 1);
                    }
                });
            } else {
                video.src = proxiedUrl;
                video.play().catch(() => {});
            }
        }

        init();
    </script>
</body>
</html>
    `);
});

app.listen(PORT, '0.0.0.0', () => {
    console.log(`Seyir Gateway running on port ${PORT}`);
});
