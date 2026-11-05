const scanLock = require('./scan_lock');
/**
 * Seyir TV Autonomous Channel Scanner & Adaptive Match-Watch Worker
 * 
 * Runs as an independent background worker.
 * Adaptive Schedule:
 * - Match hours (Fri-Mon 16:00-00:30 Istanbul): scans every 5 mins
 * - Cup hours (Tue-Thu 19:00-00:00 Istanbul): scans every 10 mins
 * - Off-peak: scans every 120 mins
 */

const fs = require('fs');
const path = require('path');
const axios = require('axios');
const http = require('http');
const https = require('https');

const httpAgent = new http.Agent({ keepAlive: true, timeout: 5000 });
const httpsAgent = new https.Agent({ keepAlive: true, timeout: 5000, rejectUnauthorized: false });

const CHANNELS_FILE = path.join(__dirname, 'verified_channels.json');
const GATEWAY_RELOAD_URL = 'http://localhost:3000/api/channels/reload';


const HIGH_PRIORITY_SEEDS = {
    beinsports1: [
        'https://css.d6e3eds5e065t5.help//hls/arda.m3u8'
    ],
    beinsports2: [
        'https://css.d6e3eds5e065t5.help//b2/tracks-v1a1/mono.m3u8'
    ],
    beinsports3: [
        'https://css.d6e3eds5e065t5.help//b3/tracks-v1a1/mono.m3u8'
    ],
    beinsports4: [
        'https://css.d6e3eds5e065t5.help//b4/tracks-v1a1/mono.m3u8'
    ],
    beinsports5: [
        'https://css.d6e3eds5e065t5.help//b5/tracks-v1a1/mono.m3u8'
    ],
    beinsportsmax1: [
        'https://css.d6e3eds5e065t5.help//bm1/tracks-v1a1/mono.m3u8'
    ],
    beinsportsmax2: [
        'https://css.d6e3eds5e065t5.help//bm2/tracks-v1a1/mono.m3u8'
    ],
    ssport1: [
        'https://css.d6e3eds5e065t5.help//ss/tracks-v1a1/mono.m3u8'
    ],
    ssport2: [
        'https://css.d6e3eds5e065t5.help//ss2/tracks-v1a1/mono.m3u8'
    ],
    aspor: [
        'https://css.d6e3eds5e065t5.help//aspor/tracks-v1a1/mono.m3u8'
    ],
    trtspor: [
        'https://trt.daioncdn.net/trtspor/master.m3u8?app=web'
    ],
    trt1: [
        'https://trt.daioncdn.net/trt-1/master.m3u8?app=web'
    ]
};

const COMMUNITY_SOURCES = [
    { name: 'akcagoz', url: 'https://raw.githubusercontent.com/batuhansabri55/AkcagozTV_Canli/main/tr.m3u' },
    { name: 'mahsun', url: 'https://raw.githubusercontent.com/kadirsener1/mahsun/main/playlist.m3u' },
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
    if (n.includes('bein') && n.includes('1') && !n.includes('haber') && !n.includes('max')) return 'beinsports1';
    if (n.includes('bein') && n.includes('2') && !n.includes('max')) return 'beinsports2';
    if (n.includes('bein') && n.includes('3') && !n.includes('max')) return 'beinsports3';
    if (n.includes('bein') && n.includes('4') && !n.includes('max')) return 'beinsports4';
    if (n.includes('bein') && n.includes('5') && !n.includes('max')) return 'beinsports5';
    if (n.includes('bein') && n.includes('max') && n.includes('1')) return 'beinsportsmax1';
    if (n.includes('bein') && n.includes('max') && n.includes('2')) return 'beinsportsmax2';
    if (n.includes('bein') && n.includes('haber')) return 'beinsportshaber';
    if (n.includes('ssport') && n.includes('1') && !n.includes('10')) return 'ssport1';
    if (n.includes('ssport') && (n.includes('2') || n.includes('plus2'))) return 'ssport2';
    if (n.includes('tivibu') && n.includes('1')) return 'tivibuspor1';
    if (n.includes('tivibu') && n.includes('2')) return 'tivibuspor2';
    if (n.includes('tivibu') && n.includes('3')) return 'tivibuspor3';
    if (n.includes('aspor')) return 'aspor';
    if (n.includes('trtspor')) return 'trtspor';
    if (n.includes('exxen') && n.includes('1')) return 'exxen1';
    if (n.includes('exxen') && n.includes('2')) return 'exxen2';
    return null;
}

// Convert Xtream raw TS stream link to direct HLS m3u8 link if applicable
function normalizeStreamLink(rawUrl) {
    if (!rawUrl) return rawUrl;
    const u = rawUrl.trim();
    // http://host:port/user/pass/ID -> http://host:port/live/user/pass/ID.m3u8
    const m = u.match(/^(https?:\/\/[^\/]+)\/([^\/]+)\/([^\/]+)\/(\d+)$/);
    if (m) {
        return `${m[1]}/live/${m[2]}/${m[3]}/${m[4]}.m3u8`;
    }
    return u;
}

async function testStream(url, timeoutMs = 3000) {
    if (!url) return false;
    try {
        const u = new URL(url);
        const headers = {
            'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36'
        };
        if (u.hostname.includes('ardastream') || u.hostname.includes('cfd') || u.hostname.includes('help')) {
            headers['Referer'] = 'https://mahsunsports57.xyz/';
            headers['Origin'] = 'https://mahsunsports57.xyz';
        } else if (u.hostname.includes('atomsport')) {
            headers['Referer'] = 'https://www.atomsportv512.top';
        } else if (u.hostname.includes('cdnlivetv')) {
            headers['Referer'] = 'https://cdnlivetv.tv/';
        }

        const res = await axios({
            method: 'get',
            url: url,
            headers: headers,
            timeout: timeoutMs,
            httpAgent,
            httpsAgent,
            validateStatus: (status) => status >= 200 && status < 300
        });

        const ct = (res.headers['content-type'] || '').toLowerCase();
        const data = res.data;
        const text = typeof data === 'string' ? data : (Buffer.isBuffer(data) ? data.toString('utf-8', 0, 4000) : '');

        if (!text.includes('#EXTM3U')) {
            return ct.includes('video') || ct.includes('mpegurl') || text.includes('.ts');
        }

        // Deep segment probe to weed out 401/403/404 Xtream traps
        const lines = text.split(/\r?\n/).map(l => l.trim()).filter(l => l && !l.startsWith('#'));
        if (lines.length === 0) return false;

        let segUrl = lines[0];
        try {
            segUrl = new URL(segUrl, url).href;
        } catch (e) {
            // Keep segUrl as is
        }

        const segHeaders = { ...headers };
        const segU = new URL(segUrl);
        if (segU.hostname.includes('ardastream') || segU.hostname.includes('cfd') || segU.hostname.includes('help')) {
            segHeaders['Referer'] = 'https://mahsunsports57.xyz/';
        }

        const segRes = await axios({
            method: 'get',
            url: segUrl,
            headers: {
                ...segHeaders,
                'Range': 'bytes=0-1024'
            },
            timeout: 2500,
            httpAgent,
            httpsAgent,
            validateStatus: (status) => (status >= 200 && status < 300) || status === 206
        });

        return segRes.status === 200 || segRes.status === 206;
    } catch (e) {
        return false;
    }
}

async function runScan() {
    if (!scanLock.acquireLock('channel_scanner')) {
        console.log('[Worker] Scan lock busy or deferred, skipping channel scan.');
        return;
    }
    try {
    console.log(`[Worker] Starting channel scan at ${new Date().toISOString()}...`);
    let channels = [];
    try {
        channels = JSON.parse(fs.readFileSync(CHANNELS_FILE, 'utf-8'));
    } catch (e) {
        console.error('[Worker] Failed to load verified_channels.json:', e.message);
        return;
    }

    const discovered = {};
    for (const [cid, sUrls] of Object.entries(HIGH_PRIORITY_SEEDS)) {
        discovered[cid] = new Set(sUrls);
    }
    for (const src of COMMUNITY_SOURCES) {
        try {
            const res = await axios.get(src.url, { timeout: 7000, httpAgent, httpsAgent });
            const lines = res.data.toString('utf-8').split('\n');
            let currentName = '';
            for (let i = 0; i < lines.length; i++) {
                const line = lines[i].trim();
                if (line.startsWith('#EXTINF:')) {
                    const comma = line.indexOf(',');
                    currentName = comma !== -1 ? line.substring(comma + 1).trim() : '';
                } else if (!line.startsWith('#') && (line.startsWith('http://') || line.startsWith('https://'))) {
                    const cid = matchChannelId(currentName);
                    if (cid) {
                        const rawStream = line.split('|')[0].trim();
                        const hlsStream = normalizeStreamLink(rawStream);
                        if (!discovered[cid]) discovered[cid] = new Set();
                        discovered[cid].add(hlsStream);
                        if (hlsStream !== rawStream) discovered[cid].add(rawStream);
                    }
                }
            }
        } catch (err) {
            console.warn(`[Worker] Playlist ${src.name} fetch warning:`, err.message);
        }
    }

    let updatedCount = 0;
    for (const ch of channels) {
        // Collect candidate URLs: current URLs + newly discovered
        const candidateSet = new Set();
        if (discovered[ch.id]) {
            for (const u of discovered[ch.id]) candidateSet.add(normalizeStreamLink(u));
        }
        for (const u of (ch.urls || [])) {
            candidateSet.add(normalizeStreamLink(u));
        }

        const candidateList = Array.from(candidateSet).filter(u => 
            u && !u.includes('atom.han') && !u.includes('ardatvgs') && !u.includes('150.230.29.27')
        );

        if (candidateList.length === 0) continue;

        // Test candidates up to 6
        const toTest = candidateList.slice(0, 6);
        const results = await Promise.allSettled(toTest.map(u => testStream(u, 2200)));

        const working = [];
        const failing = [];
        for (let i = 0; i < toTest.length; i++) {
            if (results[i].status === 'fulfilled' && results[i].value === true) {
                working.push(toTest[i]);
            } else {
                failing.push(toTest[i]);
            }
        }

        // Remaining candidates kept at the end
        for (const u of candidateList.slice(6)) {
            failing.push(u);
        }

        if (working.length > 0) {
            const reordered = [...working, ...failing];
            ch.urls = reordered;
            updatedCount++;
        }
    }

    // Atomic write to disk
    try {
        const tmpFile = CHANNELS_FILE + '.tmp';
        fs.writeFileSync(tmpFile, JSON.stringify(channels, null, 2), 'utf-8');
        fs.renameSync(tmpFile, CHANNELS_FILE);
        console.log(`[Worker] Scan complete. Updated channels saved to disk. Active channels: ${updatedCount}/${channels.length}`);

        // Signal Gateway to reload in-memory channel pool
        try {
            await axios.get(GATEWAY_RELOAD_URL, { timeout: 3000 });
            console.log('[Worker] Gateway in-memory reload signal sent successfully.');
        } catch (reloadErr) {
            console.log('[Worker] Gateway reload notification (gateway will read on next cycle).');
        }
    } catch (e) {
        console.error('[Worker] Error saving channels:', e.message);
    }

    } finally {
        scanLock.releaseLock('channel_scanner');
    }
}

// Adaptive Interval Calculator based on Istanbul Time (UTC+3)
function getNextIntervalMs() {
    const now = new Date();
    // Istanbul UTC+3 offset
    const utcHours = now.getUTCHours();
    const utcDay = now.getUTCDay(); // 0: Sun, 1: Mon, ... 5: Fri, 6: Sat
    const trHours = (utcHours + 3) % 24;

    const isWeekendBlock = (utcDay === 5 || utcDay === 6 || utcDay === 0 || utcDay === 1); // Fri, Sat, Sun, Mon
    const isMatchHours = isWeekendBlock && (trHours >= 16 || trHours === 0); // 16:00 to 00:30

    const isMidweekBlock = (utcDay === 2 || utcDay === 3 || utcDay === 4); // Tue, Wed, Thu
    const isCupHours = isMidweekBlock && (trHours >= 19 || trHours === 23); // 19:00 to 00:00

    if (isMatchHours) {
        console.log(`[Scheduler] Active MATCH MODE (Fri-Mon 16:00-00:30 TR). Interval: 5 minutes.`);
        return 5 * 60 * 1000;
    } else if (isCupHours) {
        console.log(`[Scheduler] Active CUP MODE (Tue-Thu 19:00-00:00 TR). Interval: 10 minutes.`);
        return 10 * 60 * 1000;
    } else {
        console.log(`[Scheduler] Off-peak hours (${trHours}:00 TR). Interval: 120 minutes.`);
        return 120 * 60 * 1000;
    }
}

let timer = null;
function scheduleNext() {
    const delay = getNextIntervalMs();
    clearTimeout(timer);
    timer = setTimeout(async () => {
        try {
            await runScan();
        } catch (err) {
            console.error('[Worker] Scan error:', err.message);
        }
        scheduleNext();
    }, delay);
}

// Initial run on boot - only when executed directly
if (require.main === module) {
    (async () => {
        console.log('[Worker] Channel Scanner starting initial run...');
        try {
            await runScan();
        } catch (e) {
            console.error('[Worker] Initial run error:', e.message);
        }
        scheduleNext();
    })();
}

module.exports = { runScan };
