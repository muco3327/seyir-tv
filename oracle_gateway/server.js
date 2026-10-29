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

    const sortedUrls = [...validUrls].sort((a, b) => getQualityScore(b) - getQualityScore(a));
    // Prioritize actual HLS manifest URLs (.m3u8, index.txt) over raw TS streams
    const hlsCandidates = sortedUrls.filter(u => !isRawTsStream(u));
    const urlsToTry = hlsCandidates.length > 0 ? hlsCandidates : sortedUrls;

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
                timeout: 5000,
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
                return res.send(rewritten);
            }
        } catch (err) {
            console.log(`[Failover] Channel ${channelId} source ${i+1}/${urlsToTry.length} failed (${err.message}). Trying next...`);
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
    if (targetUrl.includes('150.230.29.27') || targetUrl.includes('localhost') || targetUrl.includes('127.0.0.1')) {
        return res.status(400).send('Loopback proxy forbidden');
    }

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
        #video-top-bar { position: absolute; top: 10px; left: 12px; right: 12px; display: flex; justify-content: space-between; align-items: center; z-index: 30; pointer-events: none; transition: opacity 0.3s ease; }
        #video-top-bar.fade-out { opacity: 0; pointer-events: none; }
        .top-bar-left, .top-bar-right { display: flex; gap: 6px; align-items: center; pointer-events: auto; }
        .v-badge { font-size: 11px; font-weight: 700; padding: 4px 9px; border-radius: 6px; text-transform: uppercase; letter-spacing: 0.5px; }
        .live-badge { background: #ef4444; color: #fff; }
        .res-badge { background: rgba(0,0,0,0.75); color: #38bdf8; border: 1px solid rgba(56,189,248,0.4); backdrop-filter: blur(4px); }
        .channel-title-badge { background: rgba(15,23,42,0.85); color: #fff; border: 1px solid rgba(255,255,255,0.15); backdrop-filter: blur(6px); font-size: 12px; font-weight: 700; padding: 4px 10px; border-radius: 6px; max-width: 140px; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }

        .btn-icon { background: rgba(15,23,42,0.85); border: 1px solid rgba(255,255,255,0.2); color: #fff; border-radius: 6px; padding: 5px 9px; font-size: 12px; font-weight: 600; cursor: pointer; backdrop-filter: blur(4px); display: inline-flex; align-items: center; gap: 4px; transition: all 0.15s; }
        .btn-icon:hover { background: rgba(30,41,59,0.95); }
        .btn-icon:active { background: #2563eb; }
        video.fit-cover { object-fit: cover !important; }

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

        /* Mobile Portrait Specific Layout */
        #mobile-nav-bar { display: none; }
        @media (max-width: 820px) and (orientation: portrait), (max-width: 650px) {
            #app-container { flex-direction: column; }
            #sidebar { display: none; position: fixed; top: 0; left: 0; width: 100%; height: 100%; z-index: 100; }
            #sidebar.open { display: flex; }
            #main-stage { padding: 10px; justify-content: flex-start; padding-bottom: 66px; }
            #video-wrapper { border-radius: 8px; }
            #mobile-nav-bar { display: flex; position: fixed; bottom: 0; left: 0; width: 100%; height: 56px; background: #0f172a; border-top: 1px solid #1e293b; z-index: 50; justify-content: space-around; align-items: center; }
            .mobile-nav-btn { flex: 1; display: flex; flex-direction: column; align-items: center; justify-content: center; height: 100%; color: #94a3b8; font-size: 11px; font-weight: 600; cursor: pointer; background: none; border: none; }
            .mobile-nav-btn.active { color: #38bdf8; }
            .mobile-nav-btn svg { width: 20px; height: 20px; margin-bottom: 2px; }
            #close-mobile-sidebar { display: block; padding: 12px; background: #1e293b; color: #fff; text-align: center; font-weight: 700; cursor: pointer; }
        }

        /* Mobile Landscape Mode (Phone turned sideways - Fullscreen Video) */
        @media (orientation: landscape) and (max-height: 550px), (orientation: landscape) and (max-width: 1000px) and (max-height: 600px) {
            html, body {
                overflow: hidden !important;
                position: fixed !important;
                width: 100vw !important;
                height: 100vh !important;
                height: 100dvh !important;
                margin: 0 !important;
                padding: 0 !important;
            }
            #app-container {
                position: fixed !important;
                inset: 0 !important;
                width: 100vw !important;
                height: 100vh !important;
                height: 100dvh !important;
                overflow: hidden !important;
            }
            #sidebar {
                display: none;
                position: fixed !important;
                top: 0 !important;
                left: 0 !important;
                width: 320px !important;
                max-width: 80vw !important;
                height: 100% !important;
                height: 100dvh !important;
                z-index: 200 !important;
                box-shadow: 6px 0 30px rgba(0,0,0,0.85);
            }
            #sidebar.open {
                display: flex !important;
            }
            #main-stage {
                padding: 0 !important;
                margin: 0 !important;
                width: 100vw !important;
                height: 100vh !important;
                height: 100dvh !important;
                max-width: 100vw !important;
                max-height: 100dvh !important;
                background: #000 !important;
                overflow: hidden !important;
                justify-content: center !important;
                align-items: center !important;
                position: fixed !important;
                inset: 0 !important;
            }
            #video-wrapper {
                position: fixed !important;
                inset: 0 !important;
                width: 100vw !important;
                height: 100vh !important;
                height: 100dvh !important;
                max-width: 100vw !important;
                max-height: 100dvh !important;
                aspect-ratio: unset !important;
                border-radius: 0 !important;
                box-shadow: none !important;
                z-index: 50 !important;
                display: flex !important;
                align-items: center !important;
                justify-content: center !important;
                background: #000 !important;
            }
            video {
                width: 100% !important;
                height: 100% !important;
                object-fit: contain !important;
            }
            video.fit-cover {
                object-fit: cover !important;
            }
            #player-meta {
                display: none !important;
            }
            #mobile-nav-bar {
                display: none !important;
            }
            #close-mobile-sidebar {
                display: block !important;
                padding: 12px !important;
                background: #1e293b !important;
                color: #fff !important;
                text-align: center !important;
                font-weight: 700 !important;
                cursor: pointer !important;
            }
        }

        /* Native Fullscreen API adjustments */
        :fullscreen #video-wrapper, :-webkit-full-screen #video-wrapper {
            width: 100vw !important;
            height: 100vh !important;
            max-width: 100vw !important;
            max-height: 100vh !important;
            border-radius: 0 !important;
            background: #000 !important;
        }
        :fullscreen video, :-webkit-full-screen video {
            width: 100% !important;
            height: 100% !important;
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
                    <div class="top-bar-left">
                        <span class="v-badge live-badge">● CANLI</span>
                        <span id="top-bar-title" class="channel-title-badge">Yayın seçiniz</span>
                        <span id="res-badge" class="v-badge res-badge">1080p FHD</span>
                    </div>
                    <div class="top-bar-right">
                        <button class="btn-icon" onclick="toggleSidebar()" title="Kanal Listesi">
                            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><rect x="2" y="7" width="20" height="15" rx="2" ry="2"></rect><polyline points="17 2 12 7 7 2"></polyline></svg>
                            <span>Kanallar</span>
                        </button>
                        <button id="btn-fit" class="btn-icon" onclick="toggleFit()" title="Ekranı Doldur / Sığdır">
                            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M15 3h6v6M9 21H3v-6M21 3l-7 7M3 21l7-7"/></svg>
                            <span id="fit-label">Doldur</span>
                        </button>
                        <button class="btn-icon" onclick="toggleFullscreen()" title="Tam Ekran">
                            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M8 3H5a2 2 0 0 0-2 2v3m18 0V5a2 2 0 0 0-2-2h-3m0 18h3a2 2 0 0 0 2-2v-3M3 16v3a2 2 0 0 0 2 2h3"/></svg>
                            <span>Tam Ekran</span>
                        </button>
                    </div>
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
                        <button class="btn-pill" onclick="toggleFullscreen()">⛶ Tam Ekran</button>
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
        <button class="mobile-nav-btn" onclick="toggleFullscreen()">
            <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M8 3H5a2 2 0 0 0-2 2v3m18 0V5a2 2 0 0 0-2-2h-3m0 18h3a2 2 0 0 0 2-2v-3M3 16v3a2 2 0 0 0 2 2h3"/></svg>
            Tam Ekran
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
            const topTitle = document.getElementById('top-bar-title');
            if (topTitle) topTitle.textContent = ch.name;
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
            wakeControls();
        }

        // Fullscreen Toggle
        function toggleFullscreen() {
            const el = document.getElementById('video-wrapper') || document.documentElement;
            if (!document.fullscreenElement && !document.webkitFullscreenElement) {
                if (el.requestFullscreen) {
                    el.requestFullscreen().catch(() => {});
                } else if (el.webkitRequestFullscreen) {
                    el.webkitRequestFullscreen();
                } else if (video.webkitEnterFullscreen) {
                    video.webkitEnterFullscreen();
                }
            } else {
                if (document.exitFullscreen) {
                    document.exitFullscreen().catch(() => {});
                } else if (document.webkitExitFullscreen) {
                    document.webkitExitFullscreen();
                }
            }
            wakeControls();
        }

        // Fit vs Fill (Aspect ratio toggle)
        function toggleFit() {
            const isCover = video.classList.toggle('fit-cover');
            const label = document.getElementById('fit-label');
            if (label) {
                label.textContent = isCover ? 'Sığdır' : 'Doldur';
            }
            wakeControls();
        }

        // Auto-hide controls overlay
        let controlsTimer = null;
        const videoWrapper = document.getElementById('video-wrapper');
        const videoTopBar = document.getElementById('video-top-bar');

        function wakeControls() {
            if (!videoTopBar) return;
            videoTopBar.classList.remove('fade-out');
            clearTimeout(controlsTimer);
            controlsTimer = setTimeout(() => {
                if (!sidebar.classList.contains('open')) {
                    videoTopBar.classList.add('fade-out');
                }
            }, 3500);
        }

        videoWrapper.addEventListener('click', (e) => {
            if (!e.target.closest('button')) {
                if (videoTopBar.classList.contains('fade-out')) {
                    wakeControls();
                } else {
                    videoTopBar.classList.add('fade-out');
                }
            }
        });

        videoWrapper.addEventListener('mousemove', wakeControls);
        videoWrapper.addEventListener('touchstart', wakeControls, { passive: true });

        // Double-tap on video to toggle fit / fullscreen
        let lastTapTime = 0;
        video.addEventListener('touchend', (e) => {
            const currentTime = new Date().getTime();
            const tapLength = currentTime - lastTapTime;
            if (tapLength < 350 && tapLength > 0) {
                toggleFit();
                e.preventDefault();
            }
            lastTapTime = currentTime;
        });
        video.addEventListener('dblclick', toggleFullscreen);

        // Auto-detect orientation change
        function checkOrientation() {
            const isLandscape = (window.innerWidth > window.innerHeight) && (window.innerHeight <= 650);
            if (isLandscape && screen.orientation && screen.orientation.type && screen.orientation.type.startsWith('landscape')) {
                // Try entering fullscreen if allowed
                if (!document.fullscreenElement && document.documentElement.requestFullscreen) {
                    document.documentElement.requestFullscreen().catch(() => {});
                }
            }
            wakeControls();
        }
        window.addEventListener('resize', checkOrientation);
        window.addEventListener('orientationchange', () => setTimeout(checkOrientation, 150));
        if (screen.orientation) {
            screen.orientation.addEventListener('change', () => setTimeout(checkOrientation, 150));
        }
        wakeControls();
    </script>
</body>
</html>
    `);
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
