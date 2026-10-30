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

// High-performance Keep-Alive Connection Pool
const httpAgent = new http.Agent({ keepAlive: true, maxSockets: 100, timeout: 60000 });
const httpsAgent = new https.Agent({ keepAlive: true, maxSockets: 100, timeout: 60000 });

// State & Channels
let curatedChannels = [];
let channelStatus = {};
let lastScanTime = 0;
let isScanning = false;
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
// Direct APK download endpoint (Fast fallback for TV app)
app.get('/api/apk', (req, res) => {
    let p = path.join(__dirname, 'Seyir-TV.apk');
    if (!fs.existsSync(p)) p = path.join(__dirname, '..', 'dist', 'Seyir-TV.apk');
    if (fs.existsSync(p)) {
        res.setHeader('Content-Type', 'application/vnd.android.package-archive');
        res.setHeader('Content-Disposition', 'attachment; filename="Seyir-TV-0.5.40.apk"');
        return res.sendFile(p);
    }
    res.redirect('https://raw.githubusercontent.com/muco3327/seyir-tv/main/dist/Seyir-TV-0.5.40.apk');
});

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

function getQualityScore(url) {
    const u = (url || '').toLowerCase();
    if (u.includes('4k') || u.includes('uhd') || u.includes('2160')) return 1000;
    // Top-tier verified live HLS streams
    if (u.includes('ardastream2') || u.includes('atom.han') || u.includes('daioncdn') || u.includes('ercdn')) return 980;
    if (u.includes('zeus')) return 950;
    // Verified 1080p FHD IPTV streams
    if (
        u.includes('77238') || u.includes('237081') || u.includes('87') || u.includes('31407') ||
        u.includes('223134') || u.includes('223135') || u.includes('80') || u.includes('77') ||
        u.includes('232183') || u.includes('76') || u.includes('94871') || u.includes('1105083') ||
        u.includes('1108685') || u.includes('16536') || u.includes('16390') || u.includes('52750') ||
        u.includes('26969') || u.includes('41227') || u.includes('4817') || u.includes('50157') ||
        u.includes('988849') || u.includes('988867') || u.includes('1080') || u.includes('fhd')
    ) return 850;
    // 720p HD streams
    if (
        u.includes('720') || u.includes('hd') || u.includes('89') || u.includes('116675') ||
        u.includes('31408') || u.includes('31409') || u.includes('81') || u.includes('172690') ||
        u.includes('79') || u.includes('233930') || u.includes('94872') || u.includes('15169') ||
        u.includes('ardastream')
    ) return 800;
    // Standard IPTV
    if (u.includes('deat1') || u.includes('camon1') || u.includes('eempiree') || u.includes('tomtv') || u.includes('nexus') || u.includes('corestream') || u.includes('atom') || u.includes('cdnlive') || u.includes('tvnow')) return 600;
    // 576p SD
    if (u.includes('576') || u.includes('/84') || u.includes('/8?output=ts')) return 400;
    // Andro / mobile
    if (u.includes('andro') || u.includes('mobil') || u.includes('mobile') || u.includes('sd') || u.includes('480') || u.includes('360')) return 300;
    return 500;
}


function isRawTsStream(url) {
    if (!url) return false;
    const u = url.toLowerCase();
    return (
        (u.includes('output=ts') || u.includes('extension=ts') || u.includes('format=ts') || u.includes('live.php')) &&
        !u.includes('.m3u8') &&
        !u.includes('index.txt')
    );
}

function isSegmentUrl(url) {
    if (!url) return false;
    const u = url.toLowerCase();
    return Boolean(
        u.match(/\.(ts|jpg|jpeg|png|aac|m4s)(\?|$)/i) ||
        u.includes('output=ts') ||
        u.includes('extension=ts') ||
        u.includes('format=ts') ||
        u.includes('_seg_') ||
        u.includes('/segment') ||
        u.includes('.cfd/c/') ||
        u.includes('/c/') ||
        u.includes('tomtv') ||
        u.includes('deat1') ||
        u.includes('camon1') ||
        u.includes('eempiree') ||
        u.includes('nexusconnects') ||
        u.includes('live.php')
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
    } else if (targetUrl.includes('atomsport') || targetUrl.includes('ardastream')) {
        headers['Referer'] = 'https://www.atomsportv512.top';
        headers['Origin'] = 'https://www.atomsportv512.top';
    } else if (targetUrl.includes('cdnlivetv.tv')) {
        headers['Referer'] = 'https://cdnlivetv.tv/';
        headers['Origin'] = 'https://cdnlivetv.tv';
    } else if (targetUrl.includes('zeus')) {
        // Zeus CDN blocks requests if Referer/Origin headers are attached. Clean headers required.
    } else if (targetUrl.includes('daioncdn') || targetUrl.includes('trt')) {
        headers['Referer'] = `${u.protocol}//${u.host}/`;
        headers['Origin'] = `${u.protocol}//${u.host}`;
    } else {
        headers['Referer'] = `${u.protocol}//${u.host}/`;
        headers['Origin'] = `${u.protocol}//${u.host}`;
    }
    return headers;
}

// --- High Performance Broadcast Caching & Stateful Circuit Breaker ---
const activeChannelSources = new Map(); // channelId -> url
const sourceCooldowns = new Map(); // url -> timestamp
const manifestCache = new Map(); // channelId -> { body: string, expires: number }
const segmentCache = new Map(); // targetUrl -> { data: Buffer, contentType: string, expires: number }
const MAX_CACHED_SEGMENTS = 70; // ~60-80 MB in RAM
const inFlightRequests = new Map(); // targetUrl -> Promise<{ data: Buffer, contentType: string }>

function isSourceCooledDown(url) {
    const until = sourceCooldowns.get(url);
    if (!until) return false;
    if (Date.now() > until) {
        sourceCooldowns.delete(url);
        return false;
    }
    return true;
}

function markSourceFailed(url) {
    sourceCooldowns.set(url, Date.now() + 60000); // Cooldown for 60 seconds
}

function markSourceSuccess(channelId, url) {
    sourceCooldowns.delete(url);
    activeChannelSources.set(channelId, url);
}

function pruneSegmentCache() {
    const now = Date.now();
    for (const [key, item] of segmentCache.entries()) {
        if (now > item.expires) segmentCache.delete(key);
    }
    while (segmentCache.size > MAX_CACHED_SEGMENTS) {
        const oldestKey = segmentCache.keys().next().value;
        if (oldestKey) segmentCache.delete(oldestKey);
        else break;
    }
}
setInterval(pruneSegmentCache, 15000);

// Resilient Channel Master Endpoint with Automatic Multi-Source Failover & 2.5s RAM Manifest Cache
app.get('/channel/:id.m3u8', async (req, res) => {
    const channelId = req.params.id;
    const ch = curatedChannels.find(c => c.id === channelId);
    if (!ch || !ch.urls || ch.urls.length === 0) {
        return res.status(404).send('Channel not found');
    }

    res.setHeader('Access-Control-Allow-Origin', '*');
    res.setHeader('Access-Control-Allow-Methods', 'GET, HEAD, OPTIONS');
    res.setHeader('Access-Control-Allow-Headers', '*');

    // 1. Check RAM Cache (Live HLS manifest cache: 2.5s TTL)
    const cached = manifestCache.get(channelId);
    if (cached && Date.now() < cached.expires) {
        res.setHeader('Content-Type', 'application/vnd.apple.mpegurl');
        return res.send(cached.body);
    }

    // Filter out recursive loopbacks to self
    const validUrls = (ch.urls || []).filter(u => 
        u && 
        !u.includes('150.230.29.27') && 
        !u.includes('localhost') && 
        !u.includes('127.0.0.1') &&
        !u.includes('/channel/')
    );

    if (validUrls.length === 0) {
        return res.status(503).send(`No external upstream source configured for channel ${channelId}`);
    }

    // Stateful Ordering:
    // 1. Currently active/working source for this channel (if not cooled down)
    // 2. High quality available sources (not cooled down)
    // 3. Cooled down sources as last resort
    const activeSource = activeChannelSources.get(channelId);
    const sortedUrls = [...validUrls].sort((a, b) => getQualityScore(b) - getQualityScore(a));
    const hlsCandidates = sortedUrls.filter(u => !isRawTsStream(u));
    const pool = hlsCandidates.length > 0 ? hlsCandidates : sortedUrls;

    const available = [];
    const cooledDown = [];
    for (const u of pool) {
        if (isSourceCooledDown(u)) cooledDown.push(u);
        else available.push(u);
    }

    const urlsToTry = [];
    if (activeSource && available.includes(activeSource)) {
        urlsToTry.push(activeSource);
    }
    for (const u of available) {
        if (!urlsToTry.includes(u)) urlsToTry.push(u);
    }
    for (const u of cooledDown) {
        if (!urlsToTry.includes(u)) urlsToTry.push(u);
    }

    for (let i = 0; i < urlsToTry.length; i++) {
        const targetUrl = urlsToTry[i];
        if (isRawTsStream(targetUrl) && hlsCandidates.length === 0) {
            return res.redirect('/proxy?url=' + encodeURIComponent(targetUrl));
        }
        try {
            const u = new URL(targetUrl);
            const headers = getHeadersForUrl(targetUrl, u);

            const response = await axios({
                method: 'get',
                url: targetUrl,
                headers: headers,
                timeout: 3000,
                httpAgent: httpAgent,
                httpsAgent: httpsAgent,
                validateStatus: (status) => status >= 200 && status < 300
            });

            const bodyStr = response.data.toString('utf-8');
            if (bodyStr.includes('#EXTM3U') || targetUrl.includes('.m3u8') || targetUrl.includes('index.txt')) {
                res.setHeader('Content-Type', 'application/vnd.apple.mpegurl');
                const lines = bodyStr.split('\n');
                const rewritten = lines.map(l => {
                    const trimmed = l.trim();
                    if (!trimmed || trimmed.startsWith('#')) return l;
                    try {
                        const resolvedUrl = new URL(trimmed, targetUrl).href;
                        return '/proxy?url=' + encodeURIComponent(resolvedUrl);
                    } catch (e) {
                        return l;
                    }
                }).join('\n');

                markSourceSuccess(channelId, targetUrl);
                manifestCache.set(channelId, { body: rewritten, expires: Date.now() + 2500 });
                return res.send(rewritten);
            }
        } catch (err) {
            markSourceFailed(targetUrl);
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

// Proxy endpoint: Adapts headers per host, streams segments and rewrites child playlists with RAM segment cache & in-flight de-duplication
app.get('/proxy', async (req, res) => {
    const targetUrl = req.query.url;
    if (!targetUrl) return res.status(400).send('URL required');
    if (targetUrl.includes('150.230.29.27') || targetUrl.includes('localhost') || targetUrl.includes('127.0.0.1')) {
        return res.status(400).send('Loopback proxy forbidden');
    }

    const isSegment = isSegmentUrl(targetUrl);

    res.setHeader('Access-Control-Allow-Origin', '*');
    res.setHeader('Access-Control-Allow-Methods', 'GET, HEAD, OPTIONS');
    res.setHeader('Access-Control-Allow-Headers', '*');

    // 1. Segment RAM Cache hit check
    if (isSegment) {
        const cachedSeg = segmentCache.get(targetUrl);
        if (cachedSeg && Date.now() < cachedSeg.expires) {
            res.setHeader('Content-Type', cachedSeg.contentType);
            res.setHeader('Content-Length', cachedSeg.data.length);
            return res.send(cachedSeg.data);
        }
    }

    // 2. In-flight de-duplication: if multiple clients request the exact same segment, await the same fetch
    let fetchPromise;
    if (isSegment && inFlightRequests.has(targetUrl)) {
        fetchPromise = inFlightRequests.get(targetUrl);
    } else {
        const u = new URL(targetUrl);
        const headers = getHeadersForUrl(targetUrl, u);

        fetchPromise = (async () => {
            const response = await axios({
                method: 'get',
                url: targetUrl,
                headers: headers,
                responseType: 'arraybuffer',
                timeout: isSegment ? 6000 : 8000,
                httpAgent: httpAgent,
                httpsAgent: httpsAgent,
                validateStatus: (status) => status >= 200 && status < 400
            });

            let contentType = response.headers['content-type'] || '';
            if (isSegment) {
                if (!contentType || contentType.includes('image/') || contentType.includes('text/') || targetUrl.includes('/c/') || targetUrl.includes('.jpg')) {
                    contentType = 'video/mp2t';
                }
            }
            return {
                data: Buffer.from(response.data),
                contentType: contentType
            };
        })();

        if (isSegment) {
            inFlightRequests.set(targetUrl, fetchPromise);
            fetchPromise.finally(() => {
                inFlightRequests.delete(targetUrl);
            });
        }
    }

    // Handle client early disconnect
    let clientDisconnected = false;
    req.on('close', () => {
        clientDisconnected = true;
    });

    try {
        const result = await fetchPromise;
        if (clientDisconnected || res.writableEnded) return;

        if (isSegment) {
            segmentCache.set(targetUrl, {
                data: result.data,
                contentType: result.contentType,
                expires: Date.now() + 25000 // Cache for 25s
            });
            res.setHeader('Content-Type', result.contentType);
            res.setHeader('Content-Length', result.data.length);
            return res.send(result.data);
        }

        const bodyStr = result.data.toString('utf-8');
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

        res.setHeader('Content-Type', result.contentType || 'video/mp2t');
        res.setHeader('Content-Length', result.data.length);
        res.send(result.data);
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
    res.send(`<!DOCTYPE html>
<html lang="tr">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no, viewport-fit=cover">
    <meta name="theme-color" content="#070a13">
    <title>Seyir TV - Canlı TV & Spor Kanalları</title>
    <link rel="preconnect" href="https://fonts.googleapis.com">
    <link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
    <link href="https://fonts.googleapis.com/css2?family=Inter:wght@400;500;600;700;800&display=swap" rel="stylesheet">
    <script src="https://cdn.jsdelivr.net/npm/hls.js@latest"></script>
    <style>
        :root {
            --bg-base: #06080f;
            --bg-surface: #0b1120;
            --bg-card: #0f172a;
            --bg-card-hover: #1e293b;
            --border: rgba(255, 255, 255, 0.08);
            --border-active: #38bdf8;
            --accent: #0284c7;
            --accent-glow: rgba(56, 189, 248, 0.25);
            --text-main: #f8fafc;
            --text-muted: #94a3b8;
            --text-dim: #64748b;
            --live-red: #ef4444;
            --success: #10b981;
            --sidebar-w: 380px;
        }

        * { box-sizing: border-box; margin: 0; padding: 0; -webkit-tap-highlight-color: transparent; }
        html, body {
            width: 100%; height: 100%; height: 100dvh;
            background: var(--bg-base);
            color: var(--text-main);
            font-family: 'Inter', -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif;
            overflow: hidden;
            letter-spacing: -0.01em;
        }

        /* Ambient Glow Background */
        body::before {
            content: '';
            position: fixed;
            top: 0; left: var(--sidebar-w); right: 0; height: 350px;
            background: radial-gradient(ellipse at 50% -20%, rgba(14, 116, 144, 0.18) 0%, transparent 70%);
            pointer-events: none;
            z-index: 0;
            transition: opacity 0.5s ease;
        }

        #app-layout {
            display: flex;
            width: 100%;
            height: 100%;
            position: relative;
            z-index: 1;
        }

        /* --- SIDEBAR --- */
        #sidebar {
            width: var(--sidebar-w);
            background: var(--bg-surface);
            border-right: 1px solid var(--border);
            display: flex;
            flex-direction: column;
            height: 100%;
            flex-shrink: 0;
            z-index: 40;
            box-shadow: 4px 0 24px rgba(0,0,0,0.4);
        }

        /* Brand Header */
        .sidebar-header {
            padding: 16px 18px 12px 18px;
            display: flex;
            align-items: center;
            justify-content: space-between;
            border-bottom: 1px solid var(--border);
        }
        .brand-logo {
            display: flex;
            align-items: center;
            gap: 10px;
            font-size: 19px;
            font-weight: 800;
            color: #fff;
            letter-spacing: -0.03em;
        }
        .brand-icon {
            width: 32px;
            height: 32px;
            background: linear-gradient(135deg, #0284c7 0%, #38bdf8 100%);
            border-radius: 9px;
            display: flex;
            align-items: center;
            justify-content: center;
            box-shadow: 0 4px 12px rgba(2, 132, 199, 0.4);
        }
        .brand-icon svg { width: 18px; height: 18px; fill: #fff; }
        .channel-counter-badge {
            font-size: 11px;
            font-weight: 700;
            background: rgba(255, 255, 255, 0.08);
            padding: 4px 8px;
            border-radius: 12px;
            color: var(--text-muted);
            border: 1px solid var(--border);
        }

        /* Search Input */
        .search-container {
            padding: 12px 16px 8px 16px;
            position: relative;
        }
        .search-wrap {
            position: relative;
            display: flex;
            align-items: center;
        }
        .search-icon {
            position: absolute;
            left: 12px;
            width: 16px;
            height: 16px;
            fill: var(--text-dim);
            pointer-events: none;
            transition: fill 0.2s;
        }
        #search-input {
            width: 100%;
            padding: 10px 34px 10px 36px;
            background: rgba(15, 23, 42, 0.8);
            border: 1px solid var(--border);
            border-radius: 10px;
            color: #fff;
            font-size: 13px;
            font-family: inherit;
            outline: none;
            transition: all 0.2s ease;
        }
        #search-input:focus {
            border-color: var(--border-active);
            box-shadow: 0 0 0 3px rgba(56, 189, 248, 0.15);
            background: #0f172a;
        }
        #search-input:focus + .search-icon { fill: #38bdf8; }
        .clear-search-btn {
            position: absolute;
            right: 10px;
            background: none;
            border: none;
            color: var(--text-dim);
            font-size: 14px;
            cursor: pointer;
            display: none;
            padding: 4px;
            line-height: 1;
        }
        .clear-search-btn:hover { color: #fff; }

        /* Category Chips Bar */
        #category-bar {
            display: flex;
            gap: 6px;
            padding: 8px 16px 12px 16px;
            overflow-x: auto;
            scrollbar-width: none;
            border-bottom: 1px solid var(--border);
        }
        #category-bar::-webkit-scrollbar { display: none; }
        .cat-chip {
            padding: 6px 12px;
            background: rgba(255, 255, 255, 0.04);
            border: 1px solid var(--border);
            border-radius: 20px;
            color: var(--text-muted);
            font-size: 12px;
            font-weight: 600;
            cursor: pointer;
            white-space: nowrap;
            transition: all 0.15s ease;
            display: flex;
            align-items: center;
            gap: 5px;
        }
        .cat-chip:hover {
            background: rgba(255, 255, 255, 0.08);
            color: #fff;
        }
        .cat-chip.active {
            background: #0284c7;
            color: #fff;
            border-color: #38bdf8;
            box-shadow: 0 2px 8px rgba(2, 132, 199, 0.4);
        }

        /* Channel List */
        #channel-list {
            flex: 1;
            overflow-y: auto;
            padding: 10px 12px;
            display: flex;
            flex-direction: column;
            gap: 4px;
            scrollbar-width: thin;
            scrollbar-color: rgba(255,255,255,0.12) transparent;
        }
        #channel-list::-webkit-scrollbar { width: 5px; }
        #channel-list::-webkit-scrollbar-thumb { background: rgba(255,255,255,0.12); border-radius: 4px; }

        .ch-card {
            display: flex;
            align-items: center;
            gap: 12px;
            padding: 9px 12px;
            background: transparent;
            border-radius: 10px;
            cursor: pointer;
            transition: all 0.15s ease;
            position: relative;
            border: 1px solid transparent;
        }
        .ch-card:hover {
            background: var(--bg-card-hover);
            transform: translateX(2px);
        }
        .ch-card.active {
            background: rgba(2, 132, 199, 0.16);
            border-color: rgba(56, 189, 248, 0.35);
        }
        .ch-card.active::before {
            content: '';
            position: absolute;
            left: 0;
            top: 18%;
            bottom: 18%;
            width: 3px;
            background: #38bdf8;
            border-radius: 0 4px 4px 0;
            box-shadow: 0 0 8px #38bdf8;
        }

        .ch-avatar {
            width: 36px;
            height: 36px;
            border-radius: 8px;
            background: #1e293b;
            display: flex;
            align-items: center;
            justify-content: center;
            font-size: 13px;
            font-weight: 700;
            color: #94a3b8;
            flex-shrink: 0;
            border: 1px solid var(--border);
            text-transform: uppercase;
        }
        .ch-card.active .ch-avatar {
            background: linear-gradient(135deg, #0284c7 0%, #0369a1 100%);
            color: #fff;
            border-color: #38bdf8;
        }

        .ch-info {
            flex: 1;
            min-width: 0;
            display: flex;
            flex-direction: column;
            gap: 2px;
        }
        .ch-name-text {
            font-size: 13.5px;
            font-weight: 600;
            color: #e2e8f0;
            white-space: nowrap;
            overflow: hidden;
            text-overflow: ellipsis;
        }
        .ch-card.active .ch-name-text {
            color: #fff;
            font-weight: 700;
        }
        .ch-meta-row {
            display: flex;
            align-items: center;
            gap: 6px;
        }
        .ch-category-pill {
            font-size: 10px;
            font-weight: 600;
            color: var(--text-dim);
            text-transform: uppercase;
            letter-spacing: 0.4px;
        }
        .ch-card.active .ch-category-pill { color: #7dd3fc; }

        .ch-status-indicator {
            width: 6px;
            height: 6px;
            border-radius: 50%;
            background: #475569;
        }
        .ch-card.active .ch-status-indicator {
            background: #10b981;
            box-shadow: 0 0 8px #10b981;
            animation: pulse-dot 2s infinite;
        }
        @keyframes pulse-dot {
            0%, 100% { opacity: 1; transform: scale(1); }
            50% { opacity: 0.6; transform: scale(1.2); }
        }

        /* --- MAIN STAGE & PLAYER --- */
        #main-stage {
            flex: 1;
            display: flex;
            flex-direction: column;
            background: var(--bg-base);
            height: 100%;
            overflow-y: auto;
            padding: 24px;
            align-items: center;
            justify-content: center;
        }

        /* Video Frame */
        #video-container {
            position: relative;
            width: 100%;
            max-width: 1080px;
            aspect-ratio: 16 / 9;
            background: #000;
            border-radius: 14px;
            overflow: hidden;
            box-shadow: 0 16px 40px rgba(0, 0, 0, 0.8), 0 0 60px rgba(2, 132, 199, 0.12);
            border: 1px solid rgba(255, 255, 255, 0.08);
            transition: box-shadow 0.3s ease;
        }
        video {
            width: 100%;
            height: 100%;
            object-fit: contain;
            background: #000;
        }
        video.crisp-mode { filter: contrast(1.06) saturate(1.08) brightness(1.02); }
        video.fit-cover { object-fit: cover !important; }

        /* Video Top Floating Overlay */
        #video-top-overlay {
            position: absolute;
            top: 0; left: 0; right: 0;
            padding: 14px 16px;
            background: linear-gradient(to bottom, rgba(0, 0, 0, 0.85) 0%, rgba(0, 0, 0, 0.3) 65%, transparent 100%);
            display: flex;
            justify-content: space-between;
            align-items: center;
            z-index: 30;
            transition: opacity 0.3s ease;
            pointer-events: none;
        }
        #video-top-overlay.fade-out { opacity: 0; pointer-events: none; }
        .overlay-left, .overlay-right { display: flex; gap: 8px; align-items: center; pointer-events: auto; }

        .badge-live-pulse {
            display: inline-flex;
            align-items: center;
            gap: 5px;
            font-size: 11px;
            font-weight: 800;
            color: #fff;
            background: #dc2626;
            padding: 4px 9px;
            border-radius: 6px;
            text-transform: uppercase;
            letter-spacing: 0.5px;
            box-shadow: 0 2px 8px rgba(220, 38, 38, 0.4);
        }
        .badge-live-pulse::before {
            content: '';
            width: 6px; height: 6px;
            background: #fff;
            border-radius: 50%;
            animation: pulse-dot 1.4s infinite;
        }

        .badge-quality-fhd {
            font-size: 11px;
            font-weight: 700;
            padding: 4px 9px;
            border-radius: 6px;
            background: rgba(15, 23, 42, 0.75);
            color: #38bdf8;
            border: 1px solid rgba(56, 189, 248, 0.3);
            backdrop-filter: blur(6px);
        }

        .channel-title-pill {
            font-size: 13px;
            font-weight: 700;
            padding: 4px 11px;
            border-radius: 6px;
            background: rgba(15, 23, 42, 0.85);
            color: #fff;
            border: 1px solid rgba(255, 255, 255, 0.15);
            backdrop-filter: blur(8px);
            max-width: 200px;
            white-space: nowrap;
            overflow: hidden;
            text-overflow: ellipsis;
        }

        .top-action-btn {
            background: rgba(15, 23, 42, 0.85);
            border: 1px solid rgba(255, 255, 255, 0.18);
            color: #fff;
            border-radius: 8px;
            padding: 6px 11px;
            font-size: 12px;
            font-weight: 600;
            cursor: pointer;
            backdrop-filter: blur(8px);
            display: inline-flex;
            align-items: center;
            gap: 5px;
            transition: all 0.15s ease;
        }
        .top-action-btn:hover {
            background: rgba(30, 41, 59, 0.95);
            border-color: #38bdf8;
            transform: translateY(-1px);
        }

        /* Player Bottom Details & Quick Strip */
        #player-bottom-details {
            width: 100%;
            max-width: 1080px;
            margin-top: 16px;
            display: flex;
            flex-direction: column;
            gap: 12px;
        }
        .info-card-row {
            display: flex;
            justify-content: space-between;
            align-items: center;
            flex-wrap: wrap;
            gap: 10px;
        }
        .now-playing-heading {
            display: flex;
            flex-direction: column;
            gap: 4px;
        }
        .now-channel-name {
            font-size: 20px;
            font-weight: 800;
            color: #fff;
            letter-spacing: -0.02em;
        }
        .now-epg-subtitle {
            font-size: 13px;
            color: #38bdf8;
            font-weight: 500;
            display: flex;
            align-items: center;
            gap: 6px;
        }
        .now-epg-subtitle::before {
            content: '●';
            color: var(--text-dim);
            font-size: 9px;
        }

        .status-pill-badge {
            font-size: 12px;
            font-weight: 700;
            padding: 5px 12px;
            border-radius: 20px;
            background: rgba(16, 185, 129, 0.15);
            color: #10b981;
            border: 1px solid rgba(16, 185, 129, 0.3);
            display: flex;
            align-items: center;
            gap: 6px;
        }
        .status-pill-badge::before {
            content: '';
            width: 7px;
            height: 7px;
            border-radius: 50%;
            background: currentColor;
        }

        /* Modern Quick Toolbar */
        .modern-toolbar {
            display: flex;
            justify-content: space-between;
            align-items: center;
            flex-wrap: wrap;
            gap: 8px;
            background: var(--bg-surface);
            padding: 10px 14px;
            border-radius: 12px;
            border: 1px solid var(--border);
        }
        .toolbar-group {
            display: flex;
            gap: 6px;
            align-items: center;
            flex-wrap: wrap;
        }

        .tool-btn {
            font-size: 12.5px;
            padding: 7px 12px;
            background: var(--bg-card);
            color: #cbd5e1;
            border: 1px solid var(--border);
            border-radius: 8px;
            cursor: pointer;
            font-weight: 600;
            display: inline-flex;
            align-items: center;
            gap: 6px;
            transition: all 0.15s ease;
        }
        .tool-btn:hover {
            background: #1e293b;
            color: #fff;
            border-color: rgba(255,255,255,0.2);
        }
        .tool-btn.active-glow {
            background: rgba(16, 185, 129, 0.2);
            color: #34d399;
            border-color: #10b981;
        }
        .tool-btn svg { width: 14px; height: 14px; fill: currentColor; }

        /* Shortcut Helper Footnote */
        .shortcuts-hint {
            font-size: 11px;
            color: var(--text-dim);
            display: flex;
            gap: 12px;
            padding: 0 4px;
        }
        .kbd-key {
            background: rgba(255, 255, 255, 0.08);
            padding: 2px 5px;
            border-radius: 4px;
            font-family: monospace;
            color: var(--text-muted);
            border: 1px solid var(--border);
        }

        /* --- MOBILE LAYOUTS --- */
        #mobile-bottom-bar { display: none; }
        #close-sidebar-mobile { display: none; }

        @media (max-width: 860px) and (orientation: portrait), (max-width: 700px) {
            #video-top-overlay { display: none !important; }
            #app-layout { flex-direction: column; }
            #sidebar {
                display: none;
                position: fixed;
                top: 0; left: 0; width: 100%; height: 100%;
                z-index: 100;
                background: #090d16;
            }
            #sidebar.open { display: flex; }
            #close-sidebar-mobile {
                display: flex;
                align-items: center;
                justify-content: center;
                gap: 6px;
                padding: 14px;
                background: #1e293b;
                color: #fff;
                font-size: 14px;
                font-weight: 700;
                cursor: pointer;
                border-top: 1px solid var(--border);
            }
            #main-stage {
                padding: 12px;
                justify-content: flex-start;
                padding-bottom: 74px;
            }
            #video-container { border-radius: 10px; }
            .shortcuts-hint { display: none; }
            .now-channel-name { font-size: 17px; }

            /* Mobile Bottom Bar */
            #mobile-bottom-bar {
                display: flex;
                position: fixed;
                bottom: 0; left: 0; width: 100%;
                height: 58px;
                background: rgba(11, 17, 32, 0.92);
                border-top: 1px solid var(--border);
                backdrop-filter: blur(12px);
                z-index: 60;
                justify-content: space-around;
                align-items: center;
                padding-bottom: env(safe-area-inset-bottom);
            }
            .nav-item-btn {
                flex: 1;
                display: flex;
                flex-direction: column;
                align-items: center;
                justify-content: center;
                height: 100%;
                color: var(--text-muted);
                font-size: 11px;
                font-weight: 600;
                cursor: pointer;
                background: none;
                border: none;
                gap: 3px;
            }
            .nav-item-btn.active { color: #38bdf8; }
            .nav-item-btn svg { width: 19px; height: 19px; fill: currentColor; }
        }

        /* Mobile Landscape (Full Screen Video) */
        @media (orientation: landscape) and (max-height: 550px), (orientation: landscape) and (max-width: 1000px) and (max-height: 600px) {
            html, body {
                overflow: hidden !important;
                position: fixed !important;
                width: 100vw !important; height: 100vh !important; height: 100dvh !important;
                margin: 0 !important; padding: 0 !important;
            }
            #app-layout, #main-stage {
                position: fixed !important; inset: 0 !important;
                width: 100vw !important; height: 100vh !important; height: 100dvh !important;
                padding: 0 !important; margin: 0 !important;
                background: #000 !important;
                overflow: hidden !important;
            }
            #sidebar {
                display: none;
                position: fixed !important; top: 0 !important; left: 0 !important;
                width: 320px !important; max-width: 80vw !important; height: 100% !important;
                z-index: 200 !important;
            }
            #sidebar.open { display: flex !important; }
            #video-container {
                position: fixed !important; inset: 0 !important;
                width: 100vw !important; height: 100vh !important; height: 100dvh !important;
                max-width: 100vw !important; max-height: 100dvh !important;
                aspect-ratio: unset !important; border-radius: 0 !important;
                border: none !important; box-shadow: none !important;
            }
            #player-bottom-details, #mobile-bottom-bar, #video-top-overlay { display: none !important; }
            #close-sidebar-mobile { display: flex !important; }
        }

        /* Fullscreen API */
        :fullscreen #video-container, :-webkit-full-screen #video-container {
            width: 100vw !important; height: 100vh !important;
            max-width: 100vw !important; max-height: 100vh !important;
            border-radius: 0 !important; background: #000 !important; border: none !important;
        }
        :fullscreen video, :-webkit-full-screen video { width: 100% !important; height: 100% !important; }
    </style>
</head>
<body>
    <div id="app-layout">
        <!-- SIDEBAR -->
        <aside id="sidebar">
            <div class="sidebar-header">
                <div class="brand-logo">
                    <div class="brand-icon">
                        <svg viewBox="0 0 24 24"><path d="M4 6h16v12H4z M21 3H3C1.9 3 1 3.9 1 5v14c0 1.1.9 2 2 2h18c1.1 0 2-.9 2-2V5c0-1.1-.9-2-2-2zm-11 6.5l6 4-6 4v-8z"/></svg>
                    </div>
                    <span>Seyir TV</span>
                </div>
                <span id="channel-counter" class="channel-counter-badge">80 Kanal</span>
            </div>

            <!-- Search -->
            <div class="search-container">
                <div class="search-wrap">
                    <svg class="search-icon" viewBox="0 0 24 24"><path d="M15.5 14h-.79l-.28-.27A6.471 6.471 0 0 0 16 9.5 6.5 6.5 0 1 0 9.5 16c1.61 0 3.09-.59 4.23-1.57l.27.28v.79l5 4.99L20.49 19l-4.99-5zm-6 0C7.01 14 5 11.99 5 9.5S7.01 5 9.5 5 14 7.01 14 9.5 11.99 14 9.5 14z"/></svg>
                    <input type="text" id="search-input" placeholder="Kanal ara... (Örn: beIN, S Sport, TRT)">
                    <button id="clear-search" class="clear-search-btn" onclick="clearSearch()">✕</button>
                </div>
            </div>

            <!-- Categories -->
            <div id="category-bar">
                <button class="cat-chip active" onclick="filterCategory('Tümü')">Tümü</button>
                <button class="cat-chip" onclick="filterCategory('beIN Sports')">🏆 beIN Sports</button>
                <button class="cat-chip" onclick="filterCategory('Spor')">⚽ Spor</button>
                <button class="cat-chip" onclick="filterCategory('Ulusal')">📺 Ulusal</button>
                <button class="cat-chip" onclick="filterCategory('Haber')">📰 Haber</button>
                <button class="cat-chip" onclick="filterCategory('Belgesel')">🌍 Belgesel</button>
                <button class="cat-chip" onclick="filterCategory('Sinema & Dizi')">🎬 Sinema</button>
                <button class="cat-chip" onclick="filterCategory('Çocuk')">👶 Çocuk</button>
                <button class="cat-chip" onclick="filterCategory('Müzik')">🎵 Müzik</button>
            </div>

            <!-- Channel List -->
            <div id="channel-list"></div>
            <div id="close-sidebar-mobile" onclick="toggleSidebar()">✕ Listeyi Kapat</div>
        </aside>

        <!-- MAIN STAGE -->
        <main id="main-stage">
            <div id="video-container">
                <video id="video-player" playsinline controls></video>

                <!-- Floating Top Bar Overlay -->
                <div id="video-top-overlay">
                    <div class="overlay-left">
                        <span class="badge-live-pulse">CANLI</span>
                        <span id="top-title" class="channel-title-pill">Yayın Seçiniz</span>
                        <span id="top-res" class="badge-quality-fhd">1080p FHD</span>
                    </div>
                    <div class="overlay-right">
                        <button class="top-action-btn" onclick="prevChannel()" title="Önceki Kanal (Sol Ok)">
                            <span>◀ Önceki</span>
                        </button>
                        <button class="top-action-btn" onclick="nextChannel()" title="Sonraki Kanal (Sağ Ok)">
                            <span>Sonraki ▶</span>
                        </button>
                        <button id="btn-fit" class="top-action-btn" onclick="toggleFit()" title="Ekranı Doldur / Sığdır">
                            <span id="fit-label">Doldur</span>
                        </button>
                        <button class="top-action-btn" onclick="toggleFullscreen()" title="Tam Ekran (F)">
                            <span>Tam Ekran</span>
                        </button>
                    </div>
                </div>
            </div>

            <!-- Player Bottom Details & Quick Strip -->
            <div id="player-bottom-details">
                <div class="info-card-row">
                    <div class="now-playing-heading">
                        <h1 id="channel-heading" class="now-channel-name">Seyir TV</h1>
                        <span id="epg-title" class="now-epg-subtitle">Yayın akışı hazırlanıyor</span>
                    </div>
                    <div id="stream-status" class="status-pill-badge">Hazır</div>
                </div>

                <!-- Modern Toolbar Strip -->
                <div class="modern-toolbar">
                    <div class="toolbar-group">
                        <button class="tool-btn" onclick="prevChannel()" title="Önceki Kanal">
                            <svg viewBox="0 0 24 24"><path d="M6 6h2v12H6zm3.5 6l8.5 6V6z"/></svg>
                            <span>Önceki</span>
                        </button>
                        <button class="tool-btn" onclick="nextChannel()" title="Sonraki Kanal">
                            <span>Sonraki</span>
                            <svg viewBox="0 0 24 24"><path d="M6 18l8.5-6L6 6v12zM16 6v12h2V6h-2z"/></svg>
                        </button>
                        <button id="btn-crisp" class="tool-btn" onclick="toggleCrisp()">
                            <span>✨ Canlı & Net</span>
                        </button>
                    </div>

                    <div class="toolbar-group">
                        <button id="btn-fit-row" class="tool-btn" onclick="toggleFit()">
                            <span>⛶ Doldur</span>
                        </button>
                        <button class="tool-btn" onclick="toggleSidebar()">
                            <span>📺 Kanallar</span>
                        </button>
                        <button class="tool-btn" onclick="toggleFullscreen()">
                            <span>⛶ Tam Ekran</span>
                        </button>
                    </div>
                </div>

                <div class="shortcuts-hint">
                    <span>Kısayollar:</span>
                    <span><span class="kbd-key">▲</span><span class="kbd-key">▼</span> Kanal Değiştir</span>
                    <span><span class="kbd-key">Space</span> Oynat/Duraklat</span>
                    <span><span class="kbd-key">F</span> Tam Ekran</span>
                    <span><span class="kbd-key">M</span> Sessiz</span>
                    <span><span class="kbd-key">C</span> Netlik</span>
                </div>
            </div>
        </main>
    </div>

    <!-- Mobile Bottom Navigation -->
    <nav id="mobile-bottom-bar">
        <button class="nav-item-btn active" onclick="toggleSidebar()">
            <svg viewBox="0 0 24 24"><path d="M4 6h16v2H4zm0 5h16v2H4zm0 5h16v2H4z"/></svg>
            <span>Kanallar</span>
        </button>
        <button class="nav-item-btn" onclick="prevChannel()">
            <svg viewBox="0 0 24 24"><path d="M6 6h2v12H6zm3.5 6l8.5 6V6z"/></svg>
            <span>Önceki</span>
        </button>
        <button class="nav-item-btn" onclick="nextChannel()">
            <svg viewBox="0 0 24 24"><path d="M6 18l8.5-6L6 6v12zM16 6v12h2V6h-2z"/></svg>
            <span>Sonraki</span>
        </button>
        <button class="nav-item-btn" onclick="toggleCrisp()">
            <svg viewBox="0 0 24 24"><path d="M12 2l3.09 6.26L22 9.27l-5 4.87 1.18 6.88L12 17.77l-6.18 3.25L7 14.14 2 9.27l6.91-1.01L12 2z"/></svg>
            <span>Netlik</span>
        </button>
        <button class="nav-item-btn" onclick="toggleFullscreen()">
            <svg viewBox="0 0 24 24"><path d="M7 14H5v5h5v-2H7v-3zm-2-4h2V7h3V5H5v5zm12 7h-3v2h5v-5h-2v3zM14 5v2h3v3h2V5h-5z"/></svg>
            <span>Tam Ekran</span>
        </button>
    </nav>

    <script>
        let allChannels = [];
        let currentChannel = null;
        let hls = null;
        let currentFilter = 'Tümü';

        const video = document.getElementById('video-player');
        const channelList = document.getElementById('channel-list');
        const channelHeading = document.getElementById('channel-heading');
        const epgTitle = document.getElementById('epg-title');
        const statusEl = document.getElementById('stream-status');
        const topTitle = document.getElementById('top-title');
        const topRes = document.getElementById('top-res');
        const searchInput = document.getElementById('search-input');
        const clearSearchBtn = document.getElementById('clear-search');
        const sidebar = document.getElementById('sidebar');
        const channelCounter = document.getElementById('channel-counter');

        // Dynamic EPG Generator matching current Turkish broadcast hours
        function getLiveEpg(name) {
            const n = (name || '').toLowerCase();
            const hour = new Date().getHours();
            if (n.includes('bein') && n.includes('1')) {
                if (hour >= 18 && hour < 23) return "Trendyol Süper Lig Canlı Karşılaşma";
                if (hour >= 23 || hour < 2) return "Gün Sonu & Süper Lig Maç Özetleri";
                return "Süper Lig Maç Önü & Özel Dosyalar";
            }
            if (n.includes('bein') && n.includes('2')) return (hour >= 19 && hour < 23) ? "Trendyol Süper Lig & Ligue 1 Canlı" : "Ligue 1 & Süper Lig Karşılaşmaları";
            if (n.includes('bein') && n.includes('3')) return "Trendyol 1. Lig & Basketbol Süper Ligi";
            if (n.includes('bein') && n.includes('4')) return "EuroLeague Basketbol & WTA Tenis";
            if (n.includes('bein') && n.includes('5')) return "Sultanlar Voleybol Ligi & Motor Sporları";
            if (n.includes('s sport')) return (hour >= 18 && hour < 23) ? "İspanya LaLiga & Serie A Canlı" : "LaLiga Özetler & NBA Action";
            if (n.includes('tivibu')) return "LaLiga, Serie A & İtalya Kupası";
            if (n.includes('smart spor')) return "Sultanlar Voleybol Ligi Canlı Maç";
            if (n.includes('exxen')) return "UEFA Şampiyonlar Ligi & Avrupa Ligi";
            if (n.includes('tabii')) return "UEFA Avrupa Ligi & Konferans Ligi";
            if (n.includes('trt spor')) return "Canlı Maç Kuşağı & Aktüel Futbol";
            if (n.includes('a spor')) return (hour >= 21) ? "Son Sayfa Canlı & Transfer Raporu" : "Günün Spor Başlıkları & Sabah Sporu";
            if (n.includes('trt 1')) return (hour >= 20 && hour < 23) ? "Prime Time Dizi / Canlı Karşılaşma" : "TRT 1 Gün Kuşağı";
            if (n.includes('atv')) return (hour >= 20 && hour < 23) ? "Kuruluş Osman / Prime Time Dizi" : "Müge Anlı ile Tatlı Sert / Gün Ortası";
            if (n.includes('kanal d')) return (hour >= 20 && hour < 23) ? "Arka Sokaklar / Dizi Kuşağı" : "Kanal D Gün Kuşağı";
            if (n.includes('show')) return (hour >= 20 && hour < 23) ? "Kızılcık Şerbeti / Dizi Kuşağı" : "Show TV Gün Kuşağı";
            if (n.includes('star')) return (hour >= 20 && hour < 23) ? "Yalı Çapkını / Prime Time Kuşağı" : "Star TV Gün Kuşağı";
            if (n.includes('tv8')) return "MasterChef Türkiye / Survivor Canlı";
            if (n.includes('haber') || n.includes('ntv') || n.includes('cnn')) return "Canlı Haber Bülteni & Sıcak Gelişmeler";
            if (n.includes('belgesel') || n.includes('dmax') || n.includes('nat geo')) return "Vahşi Doğa & Bilim Belgeselleri";
            return "Canlı Yayın Akışı";
        }

        // Fetch Curated Master Channels
        fetch('/api/channels')
            .then(res => res.json())
            .then(data => {
                allChannels = data || [];
                channelCounter.textContent = allChannels.length + ' Kanal';
                renderChannels(allChannels);
                if (allChannels.length > 0) {
                    playChannel(allChannels[0]);
                }
            })
            .catch(err => {
                statusEl.textContent = 'Bağlantı Hatası';
                statusEl.style.color = '#ef4444';
            });

        function getCategoryInitial(name, cat) {
            const n = (name || '').toLowerCase();
            if (n.includes('bein') && n.includes('1')) return 'B1';
            if (n.includes('bein') && n.includes('2')) return 'B2';
            if (n.includes('bein') && n.includes('3')) return 'B3';
            if (n.includes('bein') && n.includes('4')) return 'B4';
            if (n.includes('bein') && n.includes('5')) return 'B5';
            if (n.includes('s sport')) return 'SS';
            if (n.includes('tivibu')) return 'TVB';
            if (n.includes('a spor')) return 'AS';
            if (n.includes('trt spor')) return 'TS';
            if (n.includes('trt 1')) return 'TRT';
            if (n.includes('atv')) return 'ATV';
            if (n.includes('show')) return 'SHW';
            if (n.includes('star')) return 'STR';
            if (n.includes('kanal d')) return 'KD';
            if (n.includes('tv8')) return 'TV8';
            if (name && name.length >= 2) return name.substring(0, 2).toUpperCase();
            return 'TV';
        }

        function renderChannels(list) {
            channelList.innerHTML = '';
            list.forEach((ch, idx) => {
                const item = document.createElement('div');
                item.className = 'ch-card' + (currentChannel && currentChannel.id === ch.id ? ' active' : '');
                item.onclick = () => {
                    playChannel(ch);
                    if (window.innerWidth <= 860) {
                        sidebar.classList.remove('open');
                    }
                };

                const avatar = document.createElement('div');
                avatar.className = 'ch-avatar';
                avatar.textContent = getCategoryInitial(ch.name, ch.category);

                const info = document.createElement('div');
                info.className = 'ch-info';

                const nameText = document.createElement('div');
                nameText.className = 'ch-name-text';
                nameText.textContent = ch.name;

                const metaRow = document.createElement('div');
                metaRow.className = 'ch-meta-row';

                const catPill = document.createElement('span');
                catPill.className = 'ch-category-pill';
                catPill.textContent = ch.category || 'Canlı';

                metaRow.appendChild(catPill);
                info.appendChild(nameText);
                info.appendChild(metaRow);

                const statusDot = document.createElement('div');
                statusDot.className = 'ch-status-indicator';

                item.appendChild(avatar);
                item.appendChild(info);
                item.appendChild(statusDot);
                channelList.appendChild(item);
            });
        }

        function playChannel(ch) {
            if (!ch) return;
            currentChannel = ch;

            channelHeading.textContent = ch.name;
            topTitle.textContent = ch.name;
            epgTitle.textContent = getLiveEpg(ch.name);

            statusEl.textContent = 'Bağlanıyor…';
            statusEl.style.color = '#f59e0b';
            statusEl.style.borderColor = 'rgba(245, 158, 11, 0.4)';
            statusEl.style.background = 'rgba(245, 158, 11, 0.15)';

            document.querySelectorAll('.ch-card').forEach(el => el.classList.remove('active'));
            const activeCard = Array.from(document.querySelectorAll('.ch-card')).find(el => {
                const n = el.querySelector('.ch-name-text');
                return n && n.textContent === ch.name;
            });
            if (activeCard) {
                activeCard.classList.add('active');
                activeCard.scrollIntoView({ block: 'nearest', behavior: 'smooth' });
            }

            const streamUrl = '/channel/' + encodeURIComponent(ch.id) + '.m3u8';

            // Clean previous player
            if (hls) {
                hls.destroy();
                hls = null;
            }
            video.pause();
            video.removeAttribute('src');
            video.load();

            if (Hls.isSupported()) {
                hls = new Hls({
                    enableWorker: true,
                    lowLatencyMode: false,
                    liveSyncDurationCount: 3,
                    liveMaxLatencyDurationCount: 6,
                    maxBufferLength: 30,
                    maxMaxBufferLength: 60,
                    backBufferLength: 60
                });
                hls.loadSource(streamUrl);
                hls.attachMedia(video);
                hls.on(Hls.Events.MANIFEST_PARSED, (e, data) => {
                    video.play().catch(() => {});
                    statusEl.textContent = 'Yayında (FHD)';
                    statusEl.style.color = '#10b981';
                    statusEl.style.borderColor = 'rgba(16, 185, 129, 0.4)';
                    statusEl.style.background = 'rgba(16, 185, 129, 0.15)';
                    if (data.levels && data.levels.length > 0) {
                        const h = data.levels[0].height;
                        topRes.textContent = h ? h + 'p HD' : '1080p FHD';
                    }
                });

                let errCount = 0;
                hls.on(Hls.Events.ERROR, (e, data) => {
                    if (data.fatal) {
                        errCount++;
                        switch (data.type) {
                            case Hls.ErrorTypes.NETWORK_ERROR:
                                statusEl.textContent = 'Ağ yenileniyor…';
                                statusEl.style.color = '#f59e0b';
                                if (errCount < 3) hls.startLoad();
                                else {
                                    errCount = 0;
                                    setTimeout(() => playChannel(currentChannel), 1500);
                                }
                                break;
                            case Hls.ErrorTypes.MEDIA_ERROR:
                                statusEl.textContent = 'Akış kurtarılıyor…';
                                statusEl.style.color = '#f59e0b';
                                hls.recoverMediaError();
                                break;
                            default:
                                statusEl.textContent = 'Yedek deneniyor…';
                                statusEl.style.color = '#ef4444';
                                hls.destroy();
                                setTimeout(() => playChannel(currentChannel), 1500);
                                break;
                        }
                    }
                });
            } else if (video.canPlayType('application/vnd.apple.mpegurl')) {
                video.src = streamUrl;
                video.addEventListener('error', () => {
                    statusEl.textContent = 'Bağlantı yenileniyor…';
                    setTimeout(() => playChannel(currentChannel), 1500);
                }, { once: true });
                video.play().catch(() => {});
                statusEl.textContent = 'Yayında';
                statusEl.style.color = '#10b981';
            }
        }

        // Category Filtering & Sorting
        function filterCategory(cat) {
            currentFilter = cat;
            document.querySelectorAll('#category-bar .cat-chip').forEach(b => {
                b.classList.toggle('active', b.textContent.includes(cat));
            });
            applyFilters();
        }

        searchInput.addEventListener('input', () => {
            clearSearchBtn.style.display = searchInput.value ? 'block' : 'none';
            applyFilters();
        });

        function clearSearch() {
            searchInput.value = '';
            clearSearchBtn.style.display = 'none';
            applyFilters();
            searchInput.focus();
        }

        function applyFilters() {
            const query = searchInput.value.toLowerCase().trim();
            let filtered = allChannels.filter(ch => {
                const matchName = ch.name.toLowerCase().includes(query);
                if (!matchName) return false;
                if (currentFilter === 'Tümü') return true;
                if (currentFilter === 'beIN Sports') {
                    return (ch.category === 'beIN Sports') || ch.name.toLowerCase().includes('bein');
                }
                if (currentFilter === 'Spor') {
                    return (ch.category === 'Spor') && !ch.name.toLowerCase().includes('bein');
                }
                return ch.category === currentFilter;
            });

            // Sort Spor A to Z
            if (currentFilter === 'Spor') {
                filtered.sort((a, b) => a.name.localeCompare(b.name, 'tr'));
            }

            renderChannels(filtered);
            channelCounter.textContent = filtered.length + ' Kanal';
        }

        // Quick Previous & Next Channel
        function prevChannel() {
            if (!allChannels.length) return;
            const currentIdx = allChannels.findIndex(c => c.id === currentChannel.id);
            const prevIdx = (currentIdx - 1 + allChannels.length) % allChannels.length;
            playChannel(allChannels[prevIdx]);
        }

        function nextChannel() {
            if (!allChannels.length) return;
            const currentIdx = allChannels.findIndex(c => c.id === currentChannel.id);
            const nextIdx = (currentIdx + 1) % allChannels.length;
            playChannel(allChannels[nextIdx]);
        }

        // Toggle Crisp Mode Filter
        function toggleCrisp() {
            const btn = document.getElementById('btn-crisp');
            const isActive = video.classList.toggle('crisp-mode');
            if (btn) btn.classList.toggle('active-glow', isActive);
        }

        // Aspect ratio (Fit vs Fill)
        function toggleFit() {
            const isCover = video.classList.toggle('fit-cover');
            const topLabel = document.getElementById('fit-label');
            const rowBtn = document.getElementById('btn-fit-row');
            const text = isCover ? '⛶ Sığdır' : '⛶ Doldur';
            if (topLabel) topLabel.textContent = isCover ? 'Sığdır' : 'Doldur';
            if (rowBtn) rowBtn.querySelector('span').textContent = text;
            wakeControls();
        }

        function toggleSidebar() {
            sidebar.classList.toggle('open');
            wakeControls();
        }

        function toggleFullscreen() {
            if (video.webkitEnterFullscreen && !navigator.userAgent.includes('Macintosh')) {
                video.webkitEnterFullscreen();
                return;
            }
            const el = document.getElementById('video-container') || document.documentElement;
            if (!document.fullscreenElement && !document.webkitFullscreenElement) {
                if (el.requestFullscreen) el.requestFullscreen().catch(() => {});
                else if (el.webkitRequestFullscreen) el.webkitRequestFullscreen();
            } else {
                if (document.exitFullscreen) document.exitFullscreen().catch(() => {});
                else if (document.webkitExitFullscreen) document.webkitExitFullscreen();
            }
            wakeControls();
        }

        // Keyboard Controls
        window.addEventListener('keydown', (e) => {
            if (document.activeElement === searchInput) return;
            switch (e.key) {
                case 'ArrowUp':
                case 'ArrowLeft':
                    prevChannel();
                    e.preventDefault();
                    break;
                case 'ArrowDown':
                case 'ArrowRight':
                    nextChannel();
                    e.preventDefault();
                    break;
                case ' ':
                    video.paused ? video.play() : video.pause();
                    e.preventDefault();
                    break;
                case 'f':
                case 'F':
                    toggleFullscreen();
                    break;
                case 'm':
                case 'M':
                    video.muted = !video.muted;
                    break;
                case 'c':
                case 'C':
                    toggleCrisp();
                    break;
            }
        });

        // Auto-hide controls overlay
        let controlsTimer = null;
        const videoContainer = document.getElementById('video-container');
        const videoTopOverlay = document.getElementById('video-top-overlay');

        function wakeControls() {
            if (!videoTopOverlay) return;
            videoTopOverlay.classList.remove('fade-out');
            clearTimeout(controlsTimer);
            controlsTimer = setTimeout(() => {
                if (!sidebar.classList.contains('open')) {
                    videoTopOverlay.classList.add('fade-out');
                }
            }, 3200);
        }

        videoContainer.addEventListener('click', (e) => {
            if (!e.target.closest('button')) {
                videoTopOverlay.classList.toggle('fade-out');
            }
        });
        videoContainer.addEventListener('mousemove', wakeControls);
        videoContainer.addEventListener('touchstart', wakeControls, { passive: true });
        video.addEventListener('dblclick', toggleFullscreen);

        // Auto Landscape Fullscreen
        function checkOrientation() {
            const isLandscape = (window.innerWidth > window.innerHeight) && (window.innerHeight <= 650);
            if (isLandscape && screen.orientation && screen.orientation.type && screen.orientation.type.startsWith('landscape')) {
                if (!document.fullscreenElement && document.documentElement.requestFullscreen) {
                    document.documentElement.requestFullscreen().catch(() => {});
                }
            }
            wakeControls();
        }
        window.addEventListener('resize', checkOrientation);
        window.addEventListener('orientationchange', () => setTimeout(checkOrientation, 150));
        wakeControls();
    </script>
</body>
</html>`);
});

// ============================================================================
// Server-Side Autonomous Playlist Scraper & Rapid Token/Health Refresher (Every 2 min)
// ============================================================================
const COMMUNITY_SOURCES = [
    { name: 'mahsun', url: 'https://raw.githubusercontent.com/kadirsener1/mahsun/main/playlist.m3u' },
    { name: 'akcagoz', url: 'https://raw.githubusercontent.com/batuhansabri55/AkcagozTV_Canli/main/tr.m3u' },
    { name: 'lunedor', url: 'https://raw.githubusercontent.com/Lunedor/iptvTR/main/iptv_tum_kanallar.m3u' }
];

function normalizeChannelName(rawName) {
    if (!rawName) return '';
    return rawName.toLowerCase()
        .replace(/ı/g, 'i')
        .replace(/ğ/g, 'g')
        .replace(/ü/g, 'u')
        .replace(/ş/g, 's')
        .replace(/ö/g, 'o')
        .replace(/ç/g, 'c')
        .replace(/[^a-z0-9]/g, '');
}

function matchChannelId(rawName) {
    const n = normalizeChannelName(rawName);
    if (!n) return null;

    if (n.includes('beinsports1') || n.includes('bein1') || n.includes('beinsport1')) return 'beinsports1';
    if (n.includes('beinsports2') || n.includes('bein2') || n.includes('beinsport2')) return 'beinsports2';
    if (n.includes('beinsports3') || n.includes('bein3') || n.includes('beinsport3')) return 'beinsports3';
    if (n.includes('beinsports4') || n.includes('bein4') || n.includes('beinsport4')) return 'beinsports4';
    if (n.includes('beinsports5') || n.includes('bein5') || n.includes('beinsport5')) return 'beinsports5';
    if (n.includes('ssport1') || n.includes('ssportplus') || n === 'ssport') return 'ssport1';
    if (n.includes('ssport2')) return 'ssport2';
    if (n.includes('tivibuspor1') || n === 'tivibuspor') return 'tivibuspor1';
    if (n.includes('tivibuspor2')) return 'tivibuspor2';
    if (n.includes('tivibuspor3')) return 'tivibuspor3';
    if (n.includes('tivibuspor4')) return 'tivibuspor4';
    if (n.includes('smartspor1') || n === 'smartspor') return 'smartspor1';
    if (n.includes('smartspor2')) return 'smartspor2';
    if (n.includes('trtsporyildiz')) return 'trtsporyildiz';
    if (n.includes('trtspor')) return 'trtspor';
    if (n.includes('aspor')) return 'aspor';
    if (n.includes('exxen1')) return 'exxen1';
    if (n.includes('exxen2')) return 'exxen2';
    if (n.includes('exxen3')) return 'exxen3';
    if (n.includes('exxen4')) return 'exxen4';
    if (n.includes('exxen5')) return 'exxen5';
    if (n.includes('exxen6')) return 'exxen6';
    if (n.includes('exxen7')) return 'exxen7';
    if (n.includes('exxen8')) return 'exxen8';
    if (n.includes('tabiispor1') || n.includes('tabispor1')) return 'tabiispor1';
    if (n.includes('tabiispor2') || n.includes('tabispor2')) return 'tabiispor2';
    if (n.includes('tabiispor3') || n.includes('tabispor3')) return 'tabiispor3';
    if (n === 'trt1') return 'trt1';
    if (n === 'atv') return 'atv';
    if (n === 'kanald') return 'kanald';
    if (n === 'showtv' || n === 'show') return 'showtv';
    if (n === 'startv' || n === 'star') return 'star';
    if (n === 'tv8') return 'tv8';
    return null;
}

async function testStreamUrl(targetUrl, timeoutMs = 2500) {
    if (!targetUrl) return false;
    try {
        const u = new URL(targetUrl);
        const headers = getHeadersForUrl(targetUrl, u);
        const res = await axios({
            method: 'get',
            url: targetUrl,
            headers: headers,
            timeout: timeoutMs,
            httpAgent,
            httpsAgent,
            maxRedirects: 3,
            validateStatus: (status) => status >= 200 && status < 400
        });
        const ct = res.headers['content-type'] || '';
        const bodyStr = typeof res.data === 'string' ? res.data : (res.data ? res.data.toString('utf-8') : '');
        return bodyStr.includes('#EXT') || targetUrl.includes('.m3u8') || targetUrl.includes('index.txt') || ct.includes('mpegurl') || ct.includes('video');
    } catch (e) {
        return false;
    }
}

async function resolveNestedStreamUrl(urlStr) {
    if (!urlStr || !urlStr.includes('raw.githubusercontent.com') || !urlStr.endsWith('.m3u8')) {
        return urlStr;
    }
    try {
        const res = await axios.get(urlStr, { timeout: 3500, httpAgent, httpsAgent });
        const lines = res.data.toString('utf-8').split('\n');
        for (const l of lines) {
            const line = l.trim();
            if (!line.startsWith('#') && (line.startsWith('http://') || line.startsWith('https://'))) {
                return line;
            }
        }
    } catch (e) {}
    return urlStr;
}

async function scanAndRefreshChannels() {
    if (isScanning) return;
    isScanning = true;
    const startTime = Date.now();
    console.log(`[Scanner] Autonomous channel & token scan started at ${new Date().toLocaleTimeString()}...`);

    const discoveredByChannel = {};

    // 1. Fetch community playlists
    for (const src of COMMUNITY_SOURCES) {
        try {
            const res = await axios.get(src.url, { timeout: 6000, httpAgent, httpsAgent });
            const lines = res.data.toString('utf-8').split('\n');
            let currentName = '';

            for (let i = 0; i < lines.length; i++) {
                const line = lines[i].trim();
                if (line.startsWith('#EXTINF:')) {
                    const commaIdx = line.indexOf(',');
                    currentName = commaIdx !== -1 ? line.substring(commaIdx + 1).trim() : '';
                } else if (!line.startsWith('#') && (line.startsWith('http://') || line.startsWith('https://'))) {
                    const matchedId = matchChannelId(currentName);
                    if (matchedId) {
                        let streamUrl = line.split('|')[0].trim();
                        if (!discoveredByChannel[matchedId]) discoveredByChannel[matchedId] = new Set();
                        discoveredByChannel[matchedId].add(streamUrl);
                    }
                }
            }
        } catch (err) {
            console.log(`[Scanner] Failed to fetch source ${src.name}: ${err.message}`);
        }
    }

    // 2. Process and health-check channels
    let onlineCount = 0;
    for (const ch of curatedChannels) {
        const urlCandidates = new Set(ch.urls || []);
        if (discoveredByChannel[ch.id]) {
            for (const u of discoveredByChannel[ch.id]) {
                urlCandidates.add(u);
            }
        }

        const candidateList = Array.from(urlCandidates).sort((a, b) => getQualityScore(b) - getQualityScore(a));
        // Resolve nested github URLs in candidate list
        const resolvedList = [];
        for (const u of candidateList.slice(0, 8)) {
            const resolved = await resolveNestedStreamUrl(u);
            if (!resolvedList.includes(resolved)) resolvedList.push(resolved);
        }

        // Test candidates concurrently
        const testResults = await Promise.allSettled(
            resolvedList.slice(0, 5).map(u => testStreamUrl(u, 2500))
        );

        const workingUrls = [];
        const failingUrls = [];

        for (let i = 0; i < testResults.length; i++) {
            const u = resolvedList[i];
            if (testResults[i].status === 'fulfilled' && testResults[i].value === true) {
                workingUrls.push(u);
            } else {
                failingUrls.push(u);
            }
        }

        // Keep remaining untried candidates at the end
        for (const u of resolvedList.slice(5)) {
            failingUrls.push(u);
        }

        workingUrls.sort((a, b) => getQualityScore(b) - getQualityScore(a));
        failingUrls.sort((a, b) => getQualityScore(b) - getQualityScore(a));
        const reordered = [...workingUrls, ...failingUrls];
        if (reordered.length > 0) {
            ch.urls = reordered;
        }

        const isOnline = workingUrls.length > 0;
        channelStatus[ch.id] = {
            online: isOnline,
            checkedAt: Date.now(),
            workingCount: workingUrls.length,
            primary: ch.urls[0] || ''
        };
        if (isOnline) onlineCount++;
    }

    lastScanTime = Date.now();
    isScanning = false;
    const duration = ((Date.now() - startTime) / 1000).toFixed(1);
    console.log(`[Scanner] Done in ${duration}s. Online: ${onlineCount}/${curatedChannels.length} channels.`);

    // Persist verified channels to disk
    try {
        fs.writeFileSync(path.join(__dirname, 'verified_channels.json'), JSON.stringify(curatedChannels, null, 2), 'utf-8');
    } catch (e) {
        console.error('[Scanner] Failed to save verified_channels.json:', e.message);
    }
}

// Scanner disabled in live gateway process to preserve CPU and zero-memory leaks
// setInterval(scanAndRefreshChannels, 2 * 60 * 1000);
// setTimeout(scanAndRefreshChannels, 3000);

app.listen(PORT, '0.0.0.0', () => {
    console.log(`Seyir TV Oracle Gateway running on port ${PORT}`);
});
