const express = require('express');
const axios = require('axios');
const cors = require('cors');
const fs = require('fs');
const path = require('path');
const http = require('http');
const https = require('https');
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
app.get('/api/channels' , (req, res) => {
    res.json(curatedChannels);
});

// Channel status endpoint
app.get('/api/channels/status', (req, res) => {
    res.json({
        timestamp: Date.now(),
        total: curatedChannels.length,
        online: Object.values(channelStatus).filter(s => s.online).length,
        channels: channelStatus
    });
});

// Version endpoint (Fallback for Seyir TV updater)
app.get('/api/version', (req, res) => {
    res.setHeader('Access-Control-Allow-Origin', '*');
    try {
        let p = path.join(__dirname, 'version.json');
        if (!fs.existsSync(p)) p = path.join(__dirname, '..', 'version.json');
        const raw = fs.readFileSync(p, 'utf-8');
        res.setHeader('Content-Type', 'application/json');
        res.send(raw);
    } catch (e) {
        res.json({
            versionCode: 76,
            versionName: "0.5.33",
            changelog: "Kapsamlı Kararlılık, D-Pad Gezinme ve Güncelleme İyileştirmeleri:\n1. GitHub güncelleme CDN bağlantısı doğrudan bağlandı.\n2. Kanal listesi kumanda D-Pad kategori geçişleri ve sağ tuş uzun basma ile kapanma düzeltildi.\n3. Başlangıçta kanal canlılık kontrolü eklendi.\n4. Kanal afişine basınca ekran kaymasını önleyen modern çözünürlük dialogu eklendi.",
            apkUrl: "https://raw.githubusercontent.com/muco3327/seyir-tv/main/dist/Seyir-TV-0.5.33.apk"
        });
    }
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
                timeout: 6000,
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

        let response;
        let attempts = 0;
        const maxAttempts = isSegment ? 2 : 1;
        while (attempts < maxAttempts) {
            attempts++;
            try {
                response = await axios({
                    method: 'get',
                    url: targetUrl,
                    headers: headers,
                    responseType: isSegment ? 'stream' : 'arraybuffer',
                    timeout: isSegment ? 6000 : 9000,
                                                            validateStatus: (status) => status >= 200 && status < 400
                });
                break;
            } catch (retryErr) {
                if (attempts >= maxAttempts) throw retryErr;
            }
        }

        res.setHeader('Access-Control-Allow-Origin', '*');
        res.setHeader('Access-Control-Allow-Methods', 'GET, HEAD, OPTIONS');
        res.setHeader('Access-Control-Allow-Headers', '*');

        if (isSegment) {
            let contentType = response.headers['content-type'] || 'video/mp2t';
            if (contentType.includes('image/') || contentType.includes('text/') || targetUrl.includes('/c/') || targetUrl.includes('.jpg')) {
                contentType = 'video/mp2t';
            }
            res.setHeader('Content-Type', contentType);
            response.data.on('error', () => {
                if (!res.headersSent) res.status(502).end();
            });
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

        /* Sidebar channel list */
        #search-box { padding: 12px; border-bottom: 1px solid #1e293b; }
        #search-input { width: 100%; padding: 10px 14px; background: #030712; border: 1px solid #334155; border-radius: 8px; color: #fff; font-size: 14px; outline: none; }
        #search-input:focus { border-color: #38bdf8; }
        #category-pills { display: flex; gap: 6px; padding: 10px 12px; overflow-x: auto; border-bottom: 1px solid #1e293b; scrollbar-width: none; }
        #channel-list { flex: 1; overflow-y: auto; padding: 8px; }
        .channel-item { display: flex; align-items: center; padding: 10px 12px; margin-bottom: 4px; border-radius: 8px; cursor: pointer; transition: background 0.15s; }
        .channel-item:hover { background: #1e293b; }
        .channel-item.active { background: #1d4ed8; font-weight: 700; }
        .ch-num { font-size: 12px; color: #64748b; width: 28px; font-weight: 700; }
        .channel-item.active .ch-num { color: #bfdbfe; }
        .ch-name { flex: 1; font-size: 14px; color: #f1f5f9; }
        .ch-tag { font-size: 10px; padding: 2px 6px; border-radius: 4px; background: rgba(56,189,248,0.15); color: #38bdf8; font-weight: 600; }
        .channel-item.active .ch-tag { background: rgba(255,255,255,0.25); color: #fff; }

        /* Mobile specific bottom drawer & layout */
        #mobile-nav-bar { display: none; }
        @media (max-width: 820px) {
            #app-container { flex-direction: column; }
            #sidebar { display: none; position: fixed; top: 0; left: 0; width: 100%; height: 100%; z-index: 100; }
            #sidebar.open { display: flex; }
            #main-stage { padding: 10px; justify-content: flex-start; }
            #video-wrapper { border-radius: 8px; }
            #mobile-nav-bar { display: flex; position: fixed; bottom: 0; left: 0; width: 100%; height: 56px; background: #0f172a; border-top: 1px solid #1e293b; z-index: 50; justify-content: space-around; align-items: center; }
            .mobile-nav-btn { flex: 1; display: flex; flex-direction: column; align-items: center; justify-content: center; height: 100%; color: #94a3b8; font-size: 11px; font-weight: 600; cursor: pointer; background: none; border: none; }
            .mobile-nav-btn.active { color: #38bdf8; }
            .mobile-nav-btn svg { width: 20px; height: 20px; margin-bottom: 2px; }
            #main-stage { padding-bottom: 66px; }
            #close-mobile-sidebar { display: block; padding: 12px; background: #1e293b; color: #fff; text-align: center; font-weight: 700; cursor: pointer; }
        }
    </style>
</head>
<body>
    <div id="app-container">
        <!-- Sidebar -->
        <div id="sidebar">
            <div id="search-box">
                <input type="text" id="search-input" placeholder="Kanal ara... (Örn: beIN, S Sport)">
            </div>
            <div id="category-pills">
                <button class="btn-pill active" onclick="filterCategory('Tümü')">Tümü</button>
                <button class="btn-pill" onclick="filterCategory('beIN Sports')">beIN Sports</button>
                <button class="btn-pill" onclick="filterCategory('S Sport')">S Sport</button>
                <button class="btn-pill" onclick="filterCategory('Tivibu')">Tivibu</button>
                <button class="btn-pill" onclick="filterCategory('Tabii')">Tabii</button>
                <button class="btn-pill" onclick="filterCategory('Ulusal')">Ulusal</button>
            </div>
            <div id="channel-list"></div>
            <div id="close-mobile-sidebar" onclick="toggleSidebar()" style="display:none;">✕ Listeyi Kapat</div>
        </div>

        <!-- Main Stage -->
        <div id="main-stage">
            <div id="video-wrapper">
                <video id="video-player" playsinline controls></video>
                <div id="video-top-bar">
                    <span class="v-badge live-badge">● CANLI</span>
                    <span id="res-badge" class="v-badge res-badge">1080p FHD</span>
                </div>
            </div>

            <!-- Player meta & controls -->
            <div id="player-meta">
                <div id="title-row">
                    <span id="now-playing-title">Yayın seçiniz</span>
                    <span id="stream-status">Bağlantı Bekleniyor</span>
                </div>

                <div id="controls-row">
                    <div class="pill-group">
                        <span class="group-label">Kaynak:</span>
                        <button class="btn-pill active" onclick="switchStreamType('gateway')">Oracle Failover</button>
                        <button class="btn-pill" onclick="switchStreamType('direct')">Doğrudan Akış</button>
                    </div>

                    <div class="pill-group">
                        <span class="group-label">Görüntü:</span>
                        <button id="btn-crisp" class="btn-pill" onclick="toggleCrisp()">✨ Canlı & Net</button>
                    </div>

                    <div class="pill-group">
                        <button class="btn-pill" onclick="toggleSidebar()">📺 Kanallar</button>
                    </div>
                </div>
            </div>
        </div>
    </div>

    <!-- Mobile Nav Bar -->
    <div id="mobile-nav-bar">
        <button class="mobile-nav-btn active" onclick="toggleSidebar()">
            <svg fill="currentColor" viewBox="0 0 24 24"><path d="M4 6h16M4 12h16M4 18h16"/></svg>
            Kanallar
        </button>
        <button class="mobile-nav-btn" onclick="toggleCrisp()">
            <svg fill="currentColor" viewBox="0 0 24 24"><path d="M12 2l3.09 6.26L22 9.27l-5 4.87 1.18 6.88L12 17.77l-6.18 3.25L7 14.14 2 9.27l6.91-1.01L12 2z"/></svg>
            Netlik
        </button>
    </div>

    <script>
        let channels = [];
        let currentChannel = null;
        let hls = null;
        let streamType = 'gateway';
        let currentFilter = 'Tümü';

        const video = document.getElementById('video-player');
        const channelList = document.getElementById('channel-list');
        const titleEl = document.getElementById('now-playing-title');
        const statusEl = document.getElementById('stream-status');
        const resBadge = document.getElementById('res-badge');
        const searchInput = document.getElementById('search-input');
        const sidebar = document.getElementById('sidebar');

        // Load channels
        fetch('/api/channels')
            .then(res => res.json())
            .then(data => {
                channels = data;
                renderChannels(channels);
                if (channels.length > 0) {
                    playChannel(channels[0]);
                }
            })
            .catch(err => {
                statusEl.textContent = 'Kanal listesi alınamadı';
            });

        function renderChannels(list) {
            channelList.innerHTML = '';
            list.forEach((ch, idx) => {
                const item = document.createElement('div');
                item.className = 'channel-item' + (currentChannel && currentChannel.id === ch.id ? ' active' : '');
                item.onclick = () => {
                    playChannel(ch);
                    if (window.innerWidth <= 820) {
                        sidebar.classList.remove('open');
                    }
                };

                const num = document.createElement('span');
                num.className = 'ch-num';
                num.textContent = (idx + 1);

                const name = document.createElement('span');
                name.className = 'ch-name';
                name.textContent = ch.name;

                const tag = document.createElement('span');
                tag.className = 'ch-tag';
                tag.textContent = ch.category || 'Canlı';

                item.appendChild(num);
                item.appendChild(name);
                item.appendChild(tag);
                channelList.appendChild(item);
            });
        }

        function playChannel(ch) {
            currentChannel = ch;
            titleEl.textContent = ch.name;
            statusEl.textContent = 'Bağlanıyor...';
            statusEl.style.background = '#f59e0b';

            document.querySelectorAll('.channel-item').forEach(el => el.classList.remove('active'));
            const activeItem = Array.from(document.querySelectorAll('.channel-item')).find(el => el.querySelector('.ch-name').textContent === ch.name);
            if (activeItem) activeItem.classList.add('active');

            let streamUrl = '';
            if (streamType === 'gateway') {
                streamUrl = '/channel/' + ch.id + '.m3u8';
            } else {
                streamUrl = ch.urls && ch.urls.length > 0 ? ch.urls[0] : '';
            }

            if (hls) {
                hls.destroy();
                hls = null;
            }

            if (Hls.isSupported()) {
                hls = new Hls({
                    enableWorker: true,
                    lowLatencyMode: true,
                    backBufferLength: 60,
                    maxBufferLength: 30,
                    maxMaxBufferLength: 60
                });
                hls.loadSource(streamUrl);
                hls.attachMedia(video);
                hls.on(Hls.Events.MANIFEST_PARSED, (e, data) => {
                    video.play().catch(() => {});
                    statusEl.textContent = 'Yayında (FHD)';
                    statusEl.style.background = '#10b981';
                    if (data.levels && data.levels.length > 0) {
                        const h = data.levels[0].height;
                        resBadge.textContent = h ? h + 'p HD' : '1080p FHD';
                    }
                });
                hls.on(Hls.Events.ERROR, (e, data) => {
                    if (data.fatal) {
                        statusEl.textContent = 'Yedek deneniyor...';
                        statusEl.style.background = '#ef4444';
                    }
                });
            } else if (video.canPlayType('application/vnd.apple.mpegurl')) {
                video.src = streamUrl;
                video.play().catch(() => {});
                statusEl.textContent = 'Yayında';
                statusEl.style.background = '#10b981';
            }
        }

        function filterCategory(cat) {
            currentFilter = cat;
            document.querySelectorAll('#category-pills .btn-pill').forEach(b => {
                b.classList.toggle('active', b.textContent === cat);
            });
            applyFilters();
        }

        searchInput.addEventListener('input', applyFilters);

        function applyFilters() {
            const query = searchInput.value.toLowerCase().trim();
            const filtered = channels.filter(ch => {
                const matchName = ch.name.toLowerCase().includes(query);
                const matchCat = currentFilter === 'Tümü' || (ch.category && ch.category.includes(currentFilter));
                return matchName && matchCat;
            });
            renderChannels(filtered);
        }

        function switchStreamType(type) {
            streamType = type;
            document.querySelectorAll('#controls-row .pill-group:first-child .btn-pill').forEach(b => {
                b.classList.toggle('active', (type === 'gateway' && b.textContent.includes('Oracle')) || (type === 'direct' && b.textContent.includes('Doğrudan')));
            });
            if (currentChannel) playChannel(currentChannel);
        }

        function toggleCrisp() {
            const btn = document.getElementById('btn-crisp');
            video.classList.toggle('crisp-mode');
            btn.classList.toggle('crisp-active');
        }

        function toggleSidebar() {
            sidebar.classList.toggle('open');
            const closeBtn = document.getElementById('close-mobile-sidebar');
            if (closeBtn) closeBtn.style.display = sidebar.classList.contains('open') ? 'block' : 'none';
        }
    </script>
</body>
</html>
    `);
});

app.listen(PORT, '0.0.0.0', () => {
    console.log(`Seyir TV Oracle Gateway running on port ${PORT}`);
});
