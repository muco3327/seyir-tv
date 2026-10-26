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

// Version endpoint
app.get('/api/version', (req, res) => {
    res.setHeader('Access-Control-Allow-Origin', '*');
    res.json({
        versionCode: 74,
        versionName: "0.5.31",
        changelog: "Görüntü Kalitesi ve Oyuncu İyileştirmeleri (v0.5.31):\n1. TV ekranında pikselleşmeyi önlemek için düşük kaliteli 'Mobil' (360p) yayınlar filtrelendi.\n2. beIN Sports ve spor kanalları yüksek çözünürlüklü HD/FHD kaynaklara bağlandı.\n3. Oynatıcı içi hızlı 'Kanallar' menüsü, en boy oranı ve 1080p/720p netlik seçici eklendi.",
        apkUrl: "https://raw.githubusercontent.com/muco3327/seyir-tv/main/dist/Seyir-TV-0.5.31.apk"
    });
});

function isSegmentUrl(url) {
    return Boolean(
        url.match(/\.(ts|jpg|jpeg|png|aac|m4s)(\?|$)/i) ||
        url.includes('_seg_') ||
        url.includes('/segment') ||
        url.includes('.cfd/c/') ||
        url.includes('/c/')
    );
}

function getHeadersForUrl(targetUrl, u) {
    const headers = {
        'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36'
    };
    if (
        targetUrl.includes('evrenesoglu') ||
        targetUrl.includes('andro') ||
        targetUrl.includes('google.d6e3eae5e065d5.help') ||
        targetUrl.includes('fastly.net') ||
        targetUrl.includes('mangeinde') ||
        targetUrl.includes('vahided') ||
        targetUrl.includes('ardatvgs') ||
        targetUrl.includes('ladyboy')
    ) {
        headers['Referer'] = 'https://mahsunsports57.xyz/';
        headers['Origin'] = 'https://mahsunsports57.xyz';
    } else if (targetUrl.includes('zeus')) {
        headers['Referer'] = 'https://zeus645423.cfd/';
        headers['Origin'] = 'https://zeus645423.cfd';
    } else if (targetUrl.includes('daioncdn') || targetUrl.includes('trt')) {
        headers['Referer'] = `${u.protocol}//${u.host}/`;
        headers['Origin'] = `${u.protocol}//${u.host}`;
    } else {
        headers['Referer'] = `${u.protocol}//${u.host}/`;
        headers['Origin'] = `${u.protocol}//${u.host}`;
    }
    return headers;
}

// Resilient Channel Master Endpoint with Automatic Multi-Source Failover
app.get('/channel/:id.m3u8', async (req, res) => {
    const channelId = req.params.id;
    const ch = curatedChannels.find(c => c.id === channelId);
    if (!ch || !ch.urls || ch.urls.length === 0) {
        return res.status(404).send('Channel not found');
    }

    res.setHeader('Access-Control-Allow-Origin', '*');
    res.setHeader('Access-Control-Allow-Methods', 'GET, HEAD, OPTIONS');
    res.setHeader('Access-Control-Allow-Headers', '*');

    for (let i = 0; i < ch.urls.length; i++) {
        const targetUrl = ch.urls[i];
        try {
            const u = new URL(targetUrl);
            const headers = getHeadersForUrl(targetUrl, u);

            const response = await axios({
                method: 'get',
                url: targetUrl,
                headers: headers,
                timeout: 6500,
                validateStatus: (status) => status >= 200 && status < 300
            });

            const bodyStr = response.data.toString('utf-8');
            if (bodyStr.startsWith('#EXTM3U') || targetUrl.includes('.m3u8') || targetUrl.includes('index.txt')) {
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
        } catch (err) {
            console.log(`[Failover] Channel ${channelId} source ${i+1}/${ch.urls.length} failed (${err.message}). Trying next...`);
            if (targetUrl.includes('medya.trt.com.tr')) {
                console.log(`[Redirect] Channel ${channelId} redirecting to Turkey direct CDN: ${targetUrl}`);
                return res.redirect(targetUrl);
            }
        }
    }

    const directTRT = ch.urls.find(u => u.includes('medya.trt.com.tr'));
    if (directTRT) {
        return res.redirect(directTRT);
    }

    res.status(502).send(`All sources for channel ${channelId} currently unavailable.`);
});

// M3U Playlist endpoint for IPTV apps / Seyir TV / VLC
app.get('/playlist.m3u', (req, res) => {
    res.setHeader('Content-Type', 'application/vnd.apple.mpegurl; charset=utf-8');
    let m3u = '#EXTM3U\n';
    const host = req.headers.host || '150.230.29.27:3000';
    for (const ch of curatedChannels) {
        m3u += `#EXTINF:-1 tvg-id="${ch.id}" tvg-name="${ch.name}" group-title="${ch.category || 'Spor'}",${ch.name}\n`;
        m3u += `http://${host}/channel/${ch.id}.m3u8\n`;
    }
    res.send(m3u);
});

// Proxy endpoint: Adapts headers per host, streams segments and rewrites child playlists
app.get('/proxy', async (req, res) => {
    const targetUrl = req.query.url;
    if (!targetUrl) return res.status(400).send('URL required');

    const isSegment = isSegmentUrl(targetUrl);

    try {
        const u = new URL(targetUrl);
        const headers = getHeadersForUrl(targetUrl, u);

        const response = await axios({
            method: 'get',
            url: targetUrl,
            headers: headers,
            responseType: isSegment ? 'stream' : 'arraybuffer',
            timeout: 9000,
            validateStatus: (status) => status >= 200 && status < 400
        });

        res.setHeader('Access-Control-Allow-Origin', '*');
        res.setHeader('Access-Control-Allow-Methods', 'GET, HEAD, OPTIONS');
        res.setHeader('Access-Control-Allow-Headers', '*');

        if (isSegment) {
            let contentType = response.headers['content-type'] || 'video/mp2t';
            if (contentType.includes('image/') || contentType.includes('text/') || targetUrl.includes('/c/') || targetUrl.includes('.jpg')) {
                contentType = 'video/mp2t';
            }
            res.setHeader('Content-Type', contentType);
            return response.data.pipe(res);
        }

        const contentType = response.headers['content-type'] || '';
        const bodyStr = response.data.toString('utf-8');

        // Rewrite M3U8 child segments & playlists
        if (bodyStr.startsWith('#EXTM3U') || targetUrl.includes('.m3u8') || targetUrl.includes('index.txt')) {
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
            return res.status(404).end();
        }
        const upstreamStatus = err.response ? err.response.status : 502;
        res.status(upstreamStatus).send('Stream error: ' + err.message);
    }
});

// Modern Responsive Mobile & Desktop Web Player Interface
app.get('/', (req, res) => {
    res.send(`
<!DOCTYPE html>
<html lang="tr">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no, viewport-fit=cover">
    <meta name="theme-color" content="#0b0f19">
    <title>Seyir TV - Canlı Yayın & Spor Kanalları</title>
    <script src="https://cdn.jsdelivr.net/npm/hls.js@latest"></script>
    <style>
        * { box-sizing: border-box; -webkit-tap-highlight-color: transparent; }
        html, body { margin: 0; padding: 0; width: 100%; height: 100%; height: 100dvh; background: #070a12; color: #fff; font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Helvetica, Arial, sans-serif; overflow: hidden; }

        /* Desktop Layout */
        #app-container { display: flex; width: 100%; height: 100%; }
        #sidebar { width: 360px; background: #0f172a; border-right: 1px solid #1e293b; display: flex; flex-direction: column; height: 100%; flex-shrink: 0; }
        #main-stage { flex: 1; display: flex; flex-direction: column; background: #030712; height: 100%; overflow-y: auto; padding: 20px; align-items: center; justify-content: center; }

        /* Video Area */
        #video-wrapper { position: relative; width: 100%; max-width: 1020px; aspect-ratio: 16 / 9; background: #000; border-radius: 12px; overflow: hidden; box-shadow: 0 12px 35px rgba(0,0,0,0.7); }
        video { width: 100%; height: 100%; object-fit: contain; background: #000; image-rendering: -webkit-optimize-contrast; }
        video.crisp-mode { filter: contrast(1.05) saturate(1.06) brightness(1.02); }

        /* Video Overlays */
        #video-top-bar { position: absolute; top: 10px; left: 12px; right: 12px; display: flex; justify-content: space-between; align-items: center; pointer-events: none; z-index: 10; }
        .v-badge { font-size: 11px; font-weight: 700; padding: 4px 9px; border-radius: 6px; text-transform: uppercase; letter-spacing: 0.5px; }
        .live-badge { background: #ef4444; color: #fff; }
        .res-badge { background: rgba(0,0,0,0.75); color: #38bdf8; border: 1px solid rgba(56,189,248,0.4); backdrop-filter: blur(4px); }

        /* Channel Info & Controls below video */
        #player-meta { width: 100%; max-width: 1020px; margin-top: 14px; display: flex; flex-direction: column; gap: 10px; }
        #title-row { display: flex; justify-content: space-between; align-items: center; flex-wrap: wrap; gap: 8px; }
        #now-playing-title { font-size: 18px; font-weight: 700; color: #f8fafc; }
        #stream-status { font-size: 12px; font-weight: 600; padding: 4px 10px; border-radius: 12px; background: #3b82f6; color: #fff; }

        #controls-row { display: flex; justify-content: space-between; align-items: center; flex-wrap: wrap; gap: 8px; background: #0f172a; padding: 10px 14px; border-radius: 8px; border: 1px solid #1e293b; }
        .pill-group { display: flex; gap: 6px; align-items: center; flex-wrap: wrap; }
        .group-label { font-size: 12px; color: #94a3b8; font-weight: 600; margin-right: 4px; }

        .btn-pill { font-size: 12px; padding: 6px 11px; background: #1e293b; color: #cbd5e1; border: 1px solid #334155; border-radius: 6px; cursor: pointer; font-weight: 500; transition: all 0.15s; }
        .btn-pill:hover { background: #334155; color: #fff; }
        .btn-pill.active { background: #2563eb; color: #fff; border-color: #60a5fa; font-weight: 700; }
        .btn-pill.crisp-active { background: #10b981; color: #fff; border-color: #34d399; }

        /* Sidebar Elements */
        #header { padding: 16px; font-weight: 800; font-size: 17px; border-bottom: 2px solid #2563eb; background: #1e293b; display: flex; align-items: center; justify-content: space-between; }
        #category-chips { display: flex; gap: 6px; padding: 10px 12px; background: #0b1120; border-bottom: 1px solid #1e293b; overflow-x: auto; scrollbar-width: none; }
        #category-chips::-webkit-scrollbar { display: none; }
        .chip { padding: 6px 12px; font-size: 12px; border-radius: 20px; background: #1e293b; color: #94a3b8; border: 1px solid #334155; cursor: pointer; white-space: nowrap; font-weight: 600; flex-shrink: 0; }
        .chip.active { background: #2563eb; color: #fff; border-color: #60a5fa; }

        #search-box { padding: 10px 12px; background: #0f172a; border-bottom: 1px solid #1e293b; position: relative; }
        #search-input { width: 100%; padding: 10px 14px; background: #090e17; border: 1px solid #334155; color: #fff; border-radius: 8px; font-size: 14px; outline: none; }
        #search-input:focus { border-color: #3b82f6; }

        #channel-list { flex: 1; overflow-y: auto; -webkit-overflow-scrolling: touch; }
        .channel-card { padding: 13px 16px; border-bottom: 1px solid #1e293b; cursor: pointer; display: flex; justify-content: space-between; align-items: center; transition: background 0.1s; }
        .channel-card:active { background: #1e293b; }
        .channel-card.active { background: #1e3a8a; border-left: 4px solid #3b82f6; }
        .ch-name { font-size: 14px; font-weight: 600; color: #f1f5f9; }
        .channel-card.active .ch-name { color: #60a5fa; }
        .ch-badge { font-size: 11px; background: rgba(255,255,255,0.08); padding: 3px 8px; border-radius: 4px; color: #94a3b8; font-weight: 500; }
        .channel-card.active .ch-badge { background: rgba(59,130,246,0.3); color: #bfdbfe; }

        /* MOBILE SPECIFIC UX (Phone portrait / landscape) */
        @media (max-width: 820px) {
            #app-container { flex-direction: column; }
            #main-stage { flex: none; width: 100%; height: auto; padding: 0; background: #070a12; }
            #video-wrapper { max-width: 100%; aspect-ratio: 16 / 9; border-radius: 0; box-shadow: none; }
            #player-meta { padding: 10px 12px; margin-top: 0; gap: 8px; }
            #now-playing-title { font-size: 16px; }
            #controls-row { padding: 8px 10px; font-size: 11px; }
            .btn-pill { font-size: 11px; padding: 5px 8px; }
            #sidebar { width: 100%; flex: 1; border-right: none; border-top: 1px solid #1e293b; min-height: 0; }
            #header { display: none; } /* hide redundant header on mobile */
            #channel-list { min-height: 0; }
        }
    </style>
</head>
<body>
    <div id="app-container">
        <!-- Sidebar (Channels & Categories) -->
        <div id="sidebar">
            <div id="header">
                <span>📺 Seyir TV</span>
                <span style="font-size: 12px; color: #10b981; font-weight: 600;">● Oracle Gateway</span>
            </div>
            <div id="category-chips">
                <button class="chip active" onclick="setCategory('Tümü')">Tümü</button>
                <button class="chip" onclick="setCategory('beIN Sports')">beIN Sports</button>
                <button class="chip" onclick="setCategory('Tabii Spor')">Tabii Spor</button>
                <button class="chip" onclick="setCategory('Tivibu Spor')">Tivibu Spor</button>
                <button class="chip" onclick="setCategory('Smart Spor')">Smart Spor</button>
                <button class="chip" onclick="setCategory('S Sport')">S Sport</button>
                <button class="chip" onclick="setCategory('Ulusal')">Ulusal</button>
            </div>
            <div id="search-box">
                <input type="text" id="search-input" placeholder="Kanal ara... (beIN, Tabii, Tivibu vb.)" oninput="filterChannels()" />
            </div>
            <div id="channel-list"></div>
        </div>

        <!-- Main Video & Detail Area -->
        <div id="main-stage">
            <div id="video-wrapper">
                <video id="videoElement" controls autoplay playsinline preload="auto"></video>
                <div id="video-top-bar">
                    <span class="v-badge live-badge">● CANLI</span>
                    <span id="res-badge" class="v-badge res-badge">1080p FHD</span>
                </div>
            </div>

            <div id="player-meta">
                <div id="title-row">
                    <span id="now-playing-title">Kanal Seçin</span>
                    <span id="stream-status">Hazır</span>
                </div>

                <div id="controls-row">
                    <!-- Source Selector -->
                    <div class="pill-group" id="sources-container">
                        <span class="group-label">Kaynak:</span>
                        <div id="source-buttons" style="display: inline-flex; gap: 5px; flex-wrap: wrap;"></div>
                    </div>

                    <!-- Quality & Clarity Controls -->
                    <div class="pill-group">
                        <span class="group-label">Görüntü Netliği:</span>
                        <button id="btn-crisp" class="btn-pill" onclick="toggleCrispMode()" title="Görüntü keskinliğini ve kontrastını artırır">✨ Netlik Güçlendirici</button>
                        <button id="btn-q-max" class="btn-pill active" onclick="forceMaxQuality()">1080p Max</button>
                        <button id="btn-q-auto" class="btn-pill" onclick="setAutoQuality()">Otomatik</button>
                    </div>
                </div>
            </div>
        </div>
    </div>

    <script>
        const video = document.getElementById('videoElement');
        let channels = [];
        let currentChannel = null;
        let currentUrlIndex = -1; // -1: automatic resilient failover master endpoint
        let activeCategory = 'Tümü';
        let hlsPlayer = null;
        let crispEnabled = false;

        function destroyPlayers() {
            if (hlsPlayer) { hlsPlayer.destroy(); hlsPlayer = null; }
        }

        async function init() {
            try {
                const res = await fetch('/api/channels');
                channels = await res.json();
                renderList(channels);
                if (channels.length > 0) playChannel(channels[0], -1);
            } catch (e) {
                document.getElementById('channel-list').innerHTML = '<div style="padding: 20px; color: #ef4444;">Kanallar alınamadı!</div>';
            }
        }

        function setCategory(cat) {
            activeCategory = cat;
            document.querySelectorAll('.chip').forEach(b => {
                b.classList.toggle('active', b.innerText === cat);
            });
            filterChannels();
        }

        function filterChannels() {
            const q = document.getElementById('search-input').value.toLowerCase();
            let filtered = channels;
            if (activeCategory !== 'Tümü') {
                filtered = filtered.filter(c => (c.category || '').toLowerCase() === activeCategory.toLowerCase());
            }
            if (q) {
                filtered = filtered.filter(c => c.name.toLowerCase().includes(q) || (c.category || '').toLowerCase().includes(q));
            }
            renderList(filtered);
        }

        function renderList(items) {
            const listEl = document.getElementById('channel-list');
            listEl.innerHTML = '';
            items.forEach((ch, idx) => {
                const div = document.createElement('div');
                div.className = 'channel-card' + (currentChannel && currentChannel.id === ch.id ? ' active' : (idx === 0 && !currentChannel ? ' active' : ''));
                div.innerHTML = '<span class="ch-name">' + ch.name + '</span><span class="ch-badge">' + ch.category + '</span>';
                div.onclick = () => {
                    document.querySelectorAll('.channel-card').forEach(e => e.classList.remove('active'));
                    div.classList.add('active');
                    playChannel(ch, -1);
                };
                listEl.appendChild(div);
            });
        }

        function renderSources(ch) {
            const container = document.getElementById('source-buttons');
            container.innerHTML = '';

            const autoBtn = document.createElement('button');
            autoBtn.className = 'btn-pill' + (currentUrlIndex === -1 ? ' active' : '');
            autoBtn.innerText = '⚡ Otomatik Failover';
            autoBtn.onclick = () => playChannel(ch, -1);
            container.appendChild(autoBtn);

            if (!ch.urls || ch.urls.length <= 1) return;
            ch.urls.forEach((u, i) => {
                const btn = document.createElement('button');
                let label = 'K-' + (i + 1);
                if (u.includes('zeus')) label = 'Zeus (' + (i + 1) + ')';
                else if (u.includes('evrenesoglu')) label = 'CDN (' + (i + 1) + ')';
                else if (u.includes('medya.trt')) label = 'TRT Medya';
                else if (u.includes('daion')) label = 'Daion';
                btn.className = 'btn-pill' + (i === currentUrlIndex ? ' active' : '');
                btn.innerText = label;
                btn.onclick = () => playChannel(ch, i);
                container.appendChild(btn);
            });
        }

        function playChannel(ch, urlIndex = -1) {
            currentChannel = ch;
            currentUrlIndex = urlIndex;
            destroyPlayers();
            renderSources(ch);

            document.getElementById('now-playing-title').innerText = ch.name;
            const statusEl = document.getElementById('stream-status');
            statusEl.innerText = 'Bağlanıyor...';
            statusEl.style.background = '#3b82f6';

            let streamUrl = '/channel/' + ch.id + '.m3u8';
            if (urlIndex >= 0 && ch.urls && ch.urls[urlIndex]) {
                streamUrl = '/proxy?url=' + encodeURIComponent(ch.urls[urlIndex]);
            }

            let loaded = false;
            const fallbackTimer = setTimeout(() => {
                if (!loaded && urlIndex < (ch.urls.length - 1)) {
                    playChannel(ch, urlIndex + 1);
                }
            }, 8000);

            function onReady() {
                loaded = true;
                clearTimeout(fallbackTimer);
                statusEl.innerText = 'Canlı Yayın';
                statusEl.style.background = '#10b981';
                updateResBadge();
            }

            video.onplaying = onReady;

            if (Hls.isSupported()) {
                hlsPlayer = new Hls({
                    enableWorker: true,
                    lowLatencyMode: true,
                    capLevelToPlayerSize: false, // Don't reduce quality on small phone screens!
                    backBufferLength: 30,
                    maxBufferLength: 20
                });
                hlsPlayer.loadSource(streamUrl);
                hlsPlayer.attachMedia(video);
                hlsPlayer.on(Hls.Events.MANIFEST_PARSED, () => {
                    // Force maximum quality level immediately for maximum clarity!
                    if (hlsPlayer.levels && hlsPlayer.levels.length > 0) {
                        hlsPlayer.currentLevel = hlsPlayer.levels.length - 1;
                    }
                    video.play().catch(() => {});
                    updateResBadge();
                });
                hlsPlayer.on(Hls.Events.LEVEL_SWITCHED, () => {
                    updateResBadge();
                });
                hlsPlayer.on(Hls.Events.ERROR, (e, data) => {
                    if (data.fatal) {
                        switch (data.type) {
                            case Hls.ErrorTypes.NETWORK_ERROR:
                                console.warn('HLS Network Error, attempting recovery...');
                                hlsPlayer.startLoad();
                                break;
                            case Hls.ErrorTypes.MEDIA_ERROR:
                                console.warn('HLS Media Error, attempting recovery...');
                                hlsPlayer.recoverMediaError();
                                break;
                            default:
                                clearTimeout(fallbackTimer);
                                console.warn('Fatal HLS Error, switching source...');
                                if (urlIndex < (ch.urls.length - 1)) {
                                    playChannel(ch, urlIndex + 1);
                                } else {
                                    statusEl.innerText = 'Yayın Hatası';
                                    statusEl.style.background = '#ef4444';
                                }
                                break;
                        }
                    } else if (data.details === 'bufferStalledError') {
                        console.warn('Buffer stalled, nudging video...');
                        if (video.paused) {
                            video.play().catch(() => {});
                        } else {
                            video.currentTime += 0.1;
                        }
                    }
                });
            } else if (video.canPlayType('application/vnd.apple.mpegurl')) {
                video.src = streamUrl;
                video.play().catch(() => {});
                video.onloadedmetadata = updateResBadge;
            }
        }

        function updateResBadge() {
            const badge = document.getElementById('res-badge');
            if (hlsPlayer && hlsPlayer.currentLevel >= 0 && hlsPlayer.levels[hlsPlayer.currentLevel]) {
                const lvl = hlsPlayer.levels[hlsPlayer.currentLevel];
                badge.innerText = (lvl.height ? lvl.height + 'p ' : '') + (lvl.height >= 1080 ? 'FHD' : (lvl.height >= 720 ? 'HD' : 'SD'));
            } else if (video.videoHeight > 0) {
                badge.innerText = video.videoHeight + 'p ' + (video.videoHeight >= 1080 ? 'FHD' : 'HD');
            } else {
                badge.innerText = '1080p FHD';
            }
        }

        function forceMaxQuality() {
            if (hlsPlayer && hlsPlayer.levels.length > 0) {
                hlsPlayer.currentLevel = hlsPlayer.levels.length - 1;
            }
            document.getElementById('btn-q-max').classList.add('active');
            document.getElementById('btn-q-auto').classList.remove('active');
            updateResBadge();
        }

        function setAutoQuality() {
            if (hlsPlayer) {
                hlsPlayer.currentLevel = -1; // Auto level
            }
            document.getElementById('btn-q-auto').classList.add('active');
            document.getElementById('btn-q-max').classList.remove('active');
            updateResBadge();
        }

        function toggleCrispMode() {
            crispEnabled = !crispEnabled;
            video.classList.toggle('crisp-mode', crispEnabled);
            const btn = document.getElementById('btn-crisp');
            btn.classList.toggle('crisp-active', crispEnabled);
            btn.innerText = crispEnabled ? '✓ Netlik Aktif' : '✨ Netlik Güçlendirici';
        }

        function toggleFullscreen() {
            const wrapper = document.getElementById('video-wrapper');
            if (!document.fullscreenElement) {
                if (wrapper.requestFullscreen) wrapper.requestFullscreen();
                else if (video.webkitEnterFullscreen) video.webkitEnterFullscreen(); // iOS Safari
            } else {
                if (document.exitFullscreen) document.exitFullscreen();
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
