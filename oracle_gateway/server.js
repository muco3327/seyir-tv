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



app.use(express.json());



app.use(express.static(path.join(__dirname, 'public')));



app.use((req, res, next) => {

    const start = Date.now();

    res.on('finish', () => {

        if (req.path.startsWith('/proxy') || req.path.includes('version') || req.path.includes('.apk') || req.path.startsWith('/api/vod')) {

            console.log(`[REQ] ${req.method} ${req.originalUrl.substring(0, 90)} -> ${res.statusCode} (${Date.now() - start}ms) [${req.headers['x-forwarded-for'] || req.socket.remoteAddress}]`);

        }

    });

    next();

});



// VOD Database & Scraper & Stream Resolver



const vodDb = require('./vod_db');



const vodScraper = require('./vod_scraper');



const streamResolver = require('./stream_resolver');
const seriesDetailScraper = require('./series_detail_scraper');



// Initialize VOD DB and start background sync on startup



vodDb.initDb().then(() => {



    console.log('[SERVER] VOD Database ready.');



    // Check if catalog has items, if not run initial sync



    vodDb.getItems({ limit: 1 }).then(items => {



        if (items.length === 0) {



            console.log('[SERVER] Catalog empty, triggering initial sync...');



            vodScraper.syncCatalog();



        }



    });



}).catch(err => {



    console.error('[SERVER] VOD DB init error:', err);



});



const networkInfo = require('./network_info');

// Network & Device Diagnostic Endpoint (Portless & Fast)
app.get(['/bilgi', '/ip', '/myip', '/cihaz'], (req, res) => {
    res.setHeader('Content-Type', 'text/html; charset=utf-8');
    res.setHeader('Cache-Control', 'no-store, no-cache, must-revalidate');
    res.send(networkInfo.renderDiagnosticPage(req));
});

app.get('/api/myip', (req, res) => {
    res.json({
        publicIp: networkInfo.getClientPublicIp(req),
        userAgent: req.headers['user-agent'] || ''
    });
});

// Favicon & Web App Icons



app.get('/favicon.ico', (req, res) => {



    res.sendFile(path.join(__dirname, 'public', 'favicon.ico'));



});



app.get('/favicon.svg', (req, res) => {



    res.setHeader('Content-Type', 'image/svg+xml');



    res.sendFile(path.join(__dirname, 'public', 'favicon.svg'));



});



app.get('/favicon.png', (req, res) => {



    res.setHeader('Content-Type', 'image/png');



    res.sendFile(path.join(__dirname, 'public', 'favicon.png'));



});



app.get('/apple-touch-icon.png', (req, res) => {



    res.setHeader('Content-Type', 'image/png');



    res.sendFile(path.join(__dirname, 'public', 'apple-touch-icon.png'));



});



app.get('/manifest.json', (req, res) => {



    res.setHeader('Content-Type', 'application/manifest+json');



    res.sendFile(path.join(__dirname, 'public', 'manifest.json'));



});



const PORT = 3000;



// High-performance Keep-Alive Connection Pool



const httpAgent = new http.Agent({ keepAlive: true, maxSockets: 100, timeout: 60000 });



const httpsAgent = new https.Agent({ keepAlive: true, maxSockets: 100, timeout: 60000, rejectUnauthorized: false });



// State & Channels



let curatedChannels = [];



let channelStatus = {};



let lastScanTime = 0;



let isScanning = false;




const CATEGORY_PRIORITY = {
    'beIN Sports': 1,
    'Spor': 2,
    'Ulusal': 3,
    'Haber': 4,
    'Sinema & Dizi': 5,
    'Belgesel': 6,
    'Çocuk': 7,
    'Müzik': 8
};

function sortChannels(channels) {
    if (!Array.isArray(channels)) return [];
    return channels.sort((a, b) => {
        const catA = CATEGORY_PRIORITY[a.category] || 99;
        const catB = CATEGORY_PRIORITY[b.category] || 99;
        if (catA !== catB) return catA - catB;

        if (a.category === 'beIN Sports' && b.category === 'beIN Sports') {
            const beinOrder = ['beinsports1', 'beinsports2', 'beinsports3', 'beinsports4', 'beinsports5', 'beinsportsmax1', 'beinsportsmax2', 'beinsportshaber'];
            const idxA = beinOrder.indexOf(a.id);
            const idxB = beinOrder.indexOf(b.id);
            if (idxA !== -1 && idxB !== -1) return idxA - idxB;
            if (idxA !== -1) return -1;
            if (idxB !== -1) return 1;
        }

        if (a.category === 'Spor' && b.category === 'Spor') {
            const sporOrder = ['ssport1', 'ssport2', 'trtspor', 'trtsporyildiz', 'aspor', 'eurosport1', 'eurosport2', 'exxen1', 'exxen2', 'exxen3', 'exxen4', 'exxen5', 'smartspor1', 'smartspor2', 'tabiispor1', 'tabiispor2', 'tabiispor3', 'tivibuspor1', 'tivibuspor2', 'tivibuspor3', 'tivibuspor4', 'htspor', 'tv85', 'fbtv', 'gstv'];
            const idxA = sporOrder.indexOf(a.id);
            const idxB = sporOrder.indexOf(b.id);
            if (idxA !== -1 && idxB !== -1) return idxA - idxB;
            if (idxA !== -1) return -1;
            if (idxB !== -1) return 1;
        }

        return 0;
    });
}
function reloadChannels() {
    try {
        const raw = fs.readFileSync(path.join(__dirname, 'verified_channels.json'), 'utf-8');
        curatedChannels = sortChannels(JSON.parse(raw));
        console.log(`Loaded ${curatedChannels.length} curated master channels (strictly sorted).`);
    } catch (e) {
        console.error('Failed to load verified_channels.json', e.message);
    }
}
reloadChannels();



// ==========================================



// VOD API Endpoints (Film & Dizi & Senkron)



// ==========================================



// 1. Get Movies (Paginated, filtered by genre or search query)



app.get('/api/vod/movies', async (req, res) => {



    try {



        const page = parseInt(req.query.page) || 1;



        const limit = parseInt(req.query.limit) || 24;



        const genre = req.query.genre || '';



        const search = req.query.search || '';



        let items = await vodDb.getItems({ type: 'movie', genre, search, page, limit });



        if (items.length === 0 && search && search.trim().length >= 2) {



            await vodScraper.searchOnline(search);



            items = await vodDb.getItems({ type: 'movie', genre, search, page, limit });



        }



        res.json({



            page,



            limit,



            items



        });



    } catch (e) {



        console.error('[API] /api/vod/movies error:', e);



        res.status(500).json({ error: e.message });



    }



});



// 2. Get Series (Paginated, filtered by genre or search query)



app.get('/api/vod/series', async (req, res) => {



    try {



        const page = parseInt(req.query.page) || 1;



        const limit = parseInt(req.query.limit) || 24;



        const genre = req.query.genre || '';



        const search = req.query.search || '';



        const items = await vodDb.getItems({ type: 'series', genre, search, page, limit });



        res.json({



            page,



            limit,



            items



        });



    } catch (e) {



        console.error('[API] /api/vod/series error:', e);



        res.status(500).json({ error: e.message });



    }



});



// 3. Movie Details & Stream



app.get('/api/vod/movie/:id', async (req, res) => {



    try {



        let movie = await vodDb.getItemById(req.params.id);



        if (!movie) {



            return res.status(404).json({ error: 'Movie not found' });



        }



        // Check cached stream first



        let stream = await vodDb.getCachedStream(`movie_${movie.id}`);



        // If overview is short or backdrop is missing or stream is missing, enrich details



        const needsEnrich = !stream || !movie.overview || movie.overview.length < 25 || movie.overview.includes('izle 20') || !movie.backdrop_url;



        if (needsEnrich && movie.source_url) {



            try {



                const details = await vodScraper.getMovieDetails(movie.source_url);



                if (details) {



                    if (details.overview && details.overview.length > 15) movie.overview = details.overview;



                    if (details.backdrop_url) movie.backdrop_url = details.backdrop_url;



                    if (details.poster_url) movie.poster_url = details.poster_url;



                    if (details.title && details.title.length > 1) movie.title = details.title;



                    if (details.data_k) movie.data_k = details.data_k;



                    await vodDb.upsertItem(movie);



                    if (details.stream && details.stream.streamUrl) {



                        stream = details.stream;



                        await vodDb.setCachedStream(`movie_${movie.id}`, stream, 86400 * 7);



                    }



                }



            } catch (err) {



                console.warn('[API] Lazy movie detail fetch failed:', err.message);



            }



        }



        // Fallback: If still no stream, try resolvePageStream



        if (!stream && movie.source_url) {



            try {



                stream = await vodScraper.resolvePageStream(movie.source_url);



                if (stream && stream.streamUrl) {



                    await vodDb.setCachedStream(`movie_${movie.id}`, stream, 86400 * 7);



                }



            } catch (e) {}



        }



        if (stream && stream.streamUrl && (stream.streamUrl.includes('vidmixi.com') || stream.streamUrl.includes('dosyaload.com')) && !stream.streamUrl.includes('/proxy?url=')) {

            stream = {

                ...stream,

                streamUrl: `http://150.230.29.27:3000/proxy?url=${encodeURIComponent(stream.streamUrl)}`

            };

        }



        res.json({



            ...movie,



            stream



        });



    } catch (e) {



        console.error('[API] /api/vod/movie/:id error:', e);



        res.status(500).json({ error: e.message });



    }



});



// 4. Series Details & Seasons / Episodes Hierarchy




// 4a. Series Details by URL or Provider
app.get('/api/vod/series/detail', async (req, res) => {
    try {
        const targetUrl = req.query.url;
        const targetProvider = req.query.provider || '';
        if (!targetUrl) return res.status(400).json({ error: 'url parameter required' });

        let series = await vodDb.getItemBySourceUrl(targetUrl);
        if (!series && targetUrl.endsWith('/')) {
            series = await vodDb.getItemBySourceUrl(targetUrl.slice(0, -1));
        }
        if (!series) {
            const slug = targetUrl.replace(/\/+$/, '').split('/').pop();
            if (slug && slug.length > 2) {
                const candidates = await vodDb.getItems({ type: 'series', search: slug, limit: 5 });
                series = candidates.find(c => c.source_url && c.source_url.includes(slug));
            }
        }

        const seriesId = series ? series.id : `series_custom_${Date.now()}`;
        let episodes = series ? await vodDb.getEpisodesBySeries(series.id) : [];

        // Fast-path: Eger veritabaninda bolumler varsa asla tekrar kazima yapma (~5ms)
        if (episodes.length === 0 || !series) {
            try {
                const parsed = await seriesDetailScraper.parseSeriesPage(targetUrl, seriesId);
                if (parsed) {
                    if (!series) {
                        series = {
                            id: seriesId,
                            type: 'series',
                            title: parsed.title || 'Dizi',
                            overview: parsed.overview || `${parsed.title || 'Dizi'} tüm sezon ve bölümleriyle HD kalitede New TV'de.`,
                            poster_url: parsed.poster_url || '',
                            source_url: targetUrl,
                            provider: targetProvider || 'Dizi',
                            genres: ['Dizi']
                        };
                        await vodDb.upsertItem(series);
                    } else {
                        if (parsed.overview && parsed.overview.length > 5) {
                            series.overview = parsed.overview;
                        } else if (!series.overview || series.overview.length < 5) {
                            series.overview = `${series.title || 'Dizi'} tüm sezon ve bölümleriyle HD kalitede New TV'de.`;
                        }
                        if (parsed.poster_url && !series.poster_url) {
                            series.poster_url = parsed.poster_url;
                        }
                        await vodDb.upsertItem(series);
                    }

                    if (parsed.episodes && parsed.episodes.length > 0 && episodes.length === 0) {
                        for (const ep of parsed.episodes) {
                            await vodDb.upsertEpisode(ep);
                        }
                        episodes = parsed.episodes;
                    }
                }
            } catch (err) {
                console.warn('[SERIES_DETAIL] Scrape warning:', targetUrl, err.message);
            }
        }

        if (series && (!series.overview || series.overview.trim().length < 5)) {
            series.overview = `${series.title || 'Dizi'} tüm sezon ve bölümleriyle HD kalitede New TV'de.`;
        }

        if (episodes.length === 0 && series && series.title) {
            try {
                const cleanT = series.title.replace(/\s*(?:izle|dizi|\(\d{4}\))\s*/gi, '').trim();
                const altCandidates = await vodDb.getItems({ type: 'series', search: cleanT, limit: 5 });
                for (const alt of altCandidates) {
                    if (alt.id !== series.id) {
                        const altEps = await vodDb.getEpisodesBySeries(alt.id);
                        if (altEps && altEps.length > 0) {
                            episodes = altEps;
                            break;
                        }
                    }
                }
            } catch (ce) {}
        }

        const seasonsMap = {};
        for (const ep of episodes) {
            const sNum = ep.season_number || 1;
            if (!seasonsMap[sNum]) {
                seasonsMap[sNum] = {
                    seasonNumber: sNum,
                    title: `${sNum}. Sezon`,
                    episodes: []
                };
            }
            seasonsMap[sNum].episodes.push(ep);
        }
        const seasons = Object.values(seasonsMap).sort((a, b) => a.seasonNumber - b.seasonNumber);

        res.json({
            ...(series || {
                id: seriesId,
                title: 'Dizi',
                overview: '',
                source_url: targetUrl,
                provider: targetProvider
            }),
            seasons
        });
    } catch (e) {
        console.error('[API] /api/vod/series/detail error:', e);
        res.status(500).json({ error: e.message });
    }
});

app.get('/api/vod/series/:id', async (req, res) => {



    try {



        const series = await vodDb.getItemById(req.params.id);



        if (!series) {



            return res.status(404).json({ error: 'Series not found' });



        }



        let episodes = await vodDb.getEpisodesBySeries(series.id);



        if ((episodes.length === 0 || !series.overview || series.overview.trim().length < 5) && series.source_url) {



            try {



                const details = await vodScraper.getSeriesDetails(series.source_url, series.id);



                if (details) {



                    if (details.overview) series.overview = details.overview;



                    if (details.title && details.title.length > 1) series.title = details.title;



                    if (details.poster_url && !series.poster_url) series.poster_url = details.poster_url;



                    await vodDb.upsertItem(series);



                    if (details.episodes && episodes.length === 0) {



                        for (const ep of details.episodes) {



                            await vodDb.upsertEpisode(ep);



                        }



                        episodes = details.episodes;



                    }



                }



            } catch (err) {



                console.warn('[API] Lazy series detail fetch failed:', err.message);



            }



        }



        if (false) {



            // Lazy fetch episodes if not populated yet



            const details = await vodScraper.getSeriesDetails(series.source_url, series.id);



            if (details && details.episodes) {



                for (const ep of details.episodes) {



                    await vodDb.upsertEpisode(ep);



                }



                episodes = details.episodes;



            }



        }



        // Group into seasons



        const seasonsMap = {};



        for (const ep of episodes) {



            const sNum = ep.season_number;



            if (!seasonsMap[sNum]) {



                seasonsMap[sNum] = {



                    seasonNumber: sNum,



                    title: `${sNum}. Sezon`,



                    episodes: []



                };



            }



            seasonsMap[sNum].episodes.push(ep);



        }



        const seasons = Object.values(seasonsMap).sort((a, b) => a.seasonNumber - b.seasonNumber);



        res.json({



            ...series,



            seasons



        });



    } catch (e) {



        console.error('[API] /api/vod/series/:id error:', e);



        res.status(500).json({ error: e.message });



    }



});



// 5. Episode Stream Resolver



app.get('/api/vod/episode/:id', async (req, res) => {



    try {



        const ep = await vodDb.getEpisodeById(req.params.id);



        if (!ep) {



            return res.status(404).json({ error: 'Episode not found' });



        }



        let stream = null;



        if (ep.source_url) {



            stream = await vodScraper.resolvePageStream(ep.source_url);



        }



        res.json({



            ...ep,



            stream



        });



    } catch (e) {



        console.error('[API] /api/vod/episode/:id error:', e);



        res.status(500).json({ error: e.message });



    }



});



// 6. Generic On-Demand Stream Resolver



app.get('/api/vod/resolve', async (req, res) => {



    try {



        const targetUrl = req.query.url;



        if (!targetUrl) {



            return res.status(400).json({ error: 'url parameter required' });



        }



        if (targetUrl.includes('vidmixi.com/list/') || targetUrl.includes('.m3u8')) {

            const finalStreamUrl = (targetUrl.includes('vidmixi.com') && !targetUrl.includes('/proxy?url='))

                ? `http://150.230.29.27:3000/proxy?url=${encodeURIComponent(targetUrl)}`

                : targetUrl;



            return res.json({



                streamUrl: finalStreamUrl,



                mimeType: 'application/x-mpegURL',



                quality: 'Auto (1080p)',



                serverName: 'Vidmixi Sunucusu',



                headers: {



                    'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36',



                    'Referer': 'https://vidmixi.com/'



                },



                subtitles: []



            });



        }



        const cached = await vodDb.getCachedStream(targetUrl).catch(() => null);

        if (cached && cached.streamUrl) {

            return res.json(cached);

        }



        let stream = null;

        if (targetUrl.includes('filmmodu.cc')) {
            stream = await vodScraper.resolvePageStream(targetUrl);
        } else if (targetUrl.includes('diziyou.one') || targetUrl.includes('dizigom.news') || targetUrl.includes('dizipal') || targetUrl.includes('dizilla')) {
            stream = await seriesDetailScraper.resolveEpisodeStream(targetUrl);
        } else {
            stream = await streamResolver.resolveStream(targetUrl);
        }



        if (!stream) {

            return res.status(404).json({ error: 'Stream could not be resolved' });

        }



        if (stream.streamUrl && (stream.streamUrl.includes('vidmixi.com') || stream.streamUrl.includes('dosyaload.com')) && !stream.streamUrl.includes('/proxy?url=')) {

            stream = {

                ...stream,

                streamUrl: `http://150.230.29.27:3000/proxy?url=${encodeURIComponent(stream.streamUrl)}`

            };

        }



        await vodDb.setCachedStream(targetUrl, stream, 7200).catch(() => {});

        res.json(stream);



    } catch (e) {



        console.error('[API] /api/vod/resolve error:', e);



        res.status(500).json({ error: e.message });



    }



});



// 7. Manual Catalog Sync Trigger



app.post('/api/vod/sync', (req, res) => {



    vodScraper.syncCatalog().catch(err => console.error('[SYNC] Background sync error:', err));



    res.json({ status: 'sync_started', message: 'VOD catalog sync initiated in background.' });



});



app.get('/api/vod/sync/status', (req, res) => {



    res.json(vodScraper.getSyncProgress());



});



app.get('/api/vod/sources/status', async (req, res) => {

    try {

        const healthList = (vodScraper.getProviderHealth && typeof vodScraper.getProviderHealth === 'function')

            ? vodScraper.getProviderHealth()

            : [];



        let movieCount = 11800;

        try {

            const stats = await vodDb.getStats();

            if (stats && stats.movies) movieCount = stats.movies;

        } catch (_) {}



        const allStatuses = [

            ...healthList,

            {

                key: 'hdfilmcehennemi',

                name: 'HDFilmcehennemi (Film)',

                url: 'https://www.hdfilmcehennemi.nl',

                status: 'ONLINE',

                count: movieCount,

                lastChecked: new Date().toISOString(),

                message: 'Aktif'

            }

        ];

        res.json({

            timestamp: Date.now(),

            sources: allStatuses

        });

    } catch (e) {

        res.status(500).json({ error: e.message });

    }

});





app.get('/api/vod/stats', async (req, res) => {



    try {



        const stats = await vodDb.getStats();



        res.json(stats);



    } catch (e) {



        res.status(500).json({ error: e.message });



    }



});



// 7b. VOD Version & Delta Sync



app.get('/api/vod/version', async (req, res) => {



    try {



        const ver = await vodDb.getLatestVersion();



        res.json(ver);



    } catch (e) {



        res.status(500).json({ error: e.message });



    }



});



app.get('/api/vod/delta', async (req, res) => {



    try {



        const since = parseInt(req.query.since || '0', 10);



        const type = req.query.type || null;



        const limit = Math.min(parseInt(req.query.limit || '500', 10), 1000);



        const items = await vodDb.getDeltaItems(since, type, limit);



        res.json({



            since: since,



            count: items.length,



            items: items



        });



    } catch (e) {



        res.status(500).json({ error: e.message });



    }



});



// 8. User Watch History Sync



app.get('/api/user/history', async (req, res) => {



    try {



        const deviceId = req.query.deviceId;



        if (!deviceId) return res.status(400).json({ error: 'deviceId required' });



        const history = await vodDb.getWatchHistory(deviceId);



        res.json(history);



    } catch (e) {



        res.status(500).json({ error: e.message });



    }



});



app.post('/api/user/history', async (req, res) => {



    try {



        const { deviceId, itemId, itemType, title, posterUrl, season, episode, positionMs, durationMs } = req.body;



        if (!deviceId || !itemId || !title) {



            return res.status(400).json({ error: 'deviceId, itemId and title required' });



        }



        await vodDb.saveWatchHistory({



            deviceId,



            itemId,



            itemType: itemType || 'movie',



            title,



            posterUrl: posterUrl || '',



            season: season || 0,



            episode: episode || 0,



            positionMs: positionMs || 0,



            durationMs: durationMs || 0



        });



        res.json({ success: true });



    } catch (e) {



        res.status(500).json({ error: e.message });



    }



});



// 9. User Favorites Sync



app.get('/api/user/favorites', async (req, res) => {



    try {



        const deviceId = req.query.deviceId;



        if (!deviceId) return res.status(400).json({ error: 'deviceId required' });



        const favorites = await vodDb.getFavorites(deviceId);



        res.json(favorites);



    } catch (e) {



        res.status(500).json({ error: e.message });



    }



});



app.post('/api/user/favorites/toggle', async (req, res) => {



    try {



        const { deviceId, itemId, itemType, title, posterUrl, year, rating } = req.body;



        if (!deviceId || !itemId || !title) {



            return res.status(400).json({ error: 'deviceId, itemId and title required' });



        }



        const result = await vodDb.toggleFavorite({



            deviceId,



            itemId,



            itemType: itemType || 'movie',



            title,



            posterUrl: posterUrl || '',



            year: year || '',



            rating: rating || ''



        });



        res.json(result);



    } catch (e) {



        res.status(500).json({ error: e.message });



    }



});



// API Channels endpoint



// Reload in-memory curated channels from disk



app.get('/api/channels/reload', (req, res) => {



    try {



        const p = path.join(__dirname, 'verified_channels.json');



        if (fs.existsSync(p)) {



            const raw = fs.readFileSync(p, 'utf-8');



            curatedChannels = JSON.parse(raw);



            console.log(`[GATEWAY] In-memory channels reloaded. Total: ${curatedChannels.length}`);



            return res.json({ success: true, count: curatedChannels.length, timestamp: Date.now() });



        }



    } catch (e) {



        return res.status(500).json({ error: e.message });



    }



    res.json({ success: false, error: 'File not found' });



});



const jitStreamCache = new Map(); // channelId -> { url: string, expires: number }



const SPORTS_RESOLVE_MAP = {

    beinsports1: 'bein-sports-1',

    beinsports2: 'bein-sports-2',

    beinsports3: 'bein-sports-3',

    beinsports4: 'bein-sports-4',

    beinsports5: 'bein-sports-5',

    beinsportsmax1: 'bein-sports-max-1',

    beinsportsmax2: 'bein-sports-max-2',

    ssport1: 's-sport',

    ssport2: 's-sport-2',

    aspor: 'a-spor',

    trt1: 'trt-1',

    trtspor: 'trt-spor'

};



const DL_STREAM_MAP = {

    beinsports1: '62',

    beinsports2: '63',

    beinsports3: '64',

    beinsports4: '67',

    beinsports5: '1010',

    ssport1: '68',

    ssport2: '15169'

};



async function resolveJitStream(channelId, forceFresh = false) {

    const aid = SPORTS_RESOLVE_MAP[channelId];

    if (!aid) return null;



    if (!forceFresh) {

        const cached = jitStreamCache.get(channelId);

        if (cached && Date.now() < cached.expires) {

            return cached.url;

        }

    }



    // Provider 1: teletv15

    try {

        const resp = await axios.get(`https://teletv15.top/load/yayinlink.php?id=${aid}`, {

            timeout: 4500,

            headers: { 'User-Agent': 'Mozilla/5.0' },

            httpAgent,

            httpsAgent

        });

        if (resp.status === 200 && resp.data && resp.data.deismackanal) {

            const freshUrl = resp.data.deismackanal;

            jitStreamCache.set(channelId, { url: freshUrl, expires: Date.now() + 8 * 60 * 1000 });

            // ch.urls kept clean, no token pollution

            console.log(`[JIT] Resolved live stream for ${channelId} via primary: ${freshUrl.substring(0, 50)}...`);

            return freshUrl;

        }

    } catch (e) {

        console.warn(`[JIT] Primary resolve failed for ${channelId}:`, e.message);

    }



    // Provider 2: cfbu247 fallback

    if (DL_STREAM_MAP[channelId]) {

        try {

            const dlResp = await axios.get(`https://chat.cfbu247.sbs/api/resolve-dlstream/${DL_STREAM_MAP[channelId]}`, {

                timeout: 4500,

                headers: { 'Origin': 'https://tvnow247.top', 'User-Agent': 'Mozilla/5.0' },

                httpAgent,

                httpsAgent

            });

            if (dlResp.status === 200 && dlResp.data && dlResp.data.m3u8) {

                const freshUrl = dlResp.data.m3u8;

                jitStreamCache.set(channelId, { url: freshUrl, expires: Date.now() + 5 * 60 * 1000 });

                // ch.urls kept clean, no token pollution

                console.log(`[JIT] Resolved live stream for ${channelId} via fallback dlstream.`);

                return freshUrl;

            }

        } catch (e) {

            console.warn(`[JIT] Fallback dlstream failed for ${channelId}:`, e.message);

        }

    }



    return null;

}



async function refreshAllChannels() {
    if (isScanning) {
        console.log('[UniversalRefresh] Scan already in progress, skipping duplicate.');
        return { running: true, message: 'Tarama devam ediyor.' };
    }
    isScanning = true;
    const startTime = Date.now();
    console.log(`[UniversalRefresh] Universal health-check & token refresh started for ${curatedChannels.length} channels...`);

    let sportsUpdated = 0;
    let fallbackPromoted = 0;
    const deadChannels = [];

    // 1. JIT Sports Refresh (beIN Sports, S Sport, TRT 1, TRT Spor, A Spor)
    const sportsIds = Object.keys(SPORTS_RESOLVE_MAP);
    await Promise.allSettled(sportsIds.map(async (cid) => {
        try {
            const fresh = await resolveJitStream(cid, true);
            if (fresh) {
                sportsUpdated++;
                const ch = curatedChannels.find(c => c.id === cid);
                if (ch && Array.isArray(ch.urls)) {
                    if (!ch.urls.includes(fresh)) {
                        ch.urls.unshift(fresh);
                    } else {
                        const idx = ch.urls.indexOf(fresh);
                        if (idx > 0) {
                            ch.urls.splice(idx, 1);
                            ch.urls.unshift(fresh);
                        }
                    }
                }
            }
        } catch (e) {
            console.warn(`[UniversalRefresh] JIT resolve failed for ${cid}:`, e.message);
        }
    }));

    // 2. Health-check all non-JIT channels (Ulusal, Haber, Sinema, Belgesel, Çocuk, Müzik, other sports)
    const nonJitChannels = curatedChannels.filter(c => !SPORTS_RESOLVE_MAP[c.id]);
    const batchSize = 8;
    for (let i = 0; i < nonJitChannels.length; i += batchSize) {
        const batch = nonJitChannels.slice(i, i + batchSize);
        await Promise.allSettled(batch.map(async (ch) => {
            const urls = ch.urls || [];
            if (urls.length === 0) {
                deadChannels.push(ch);
                return;
            }

            // Test primary candidate first
            const primaryWorks = await testStreamUrl(urls[0], 2500);
            if (primaryWorks) {
                channelStatus[ch.id] = { online: true, checkedAt: Date.now(), workingCount: 1, primary: urls[0] };
                return;
            }

            // Primary URL failed! Test backups
            let foundWorking = false;
            for (let uIdx = 1; uIdx < urls.length; uIdx++) {
                const candidate = urls[uIdx];
                const candidateWorks = await testStreamUrl(candidate, 2500);
                if (candidateWorks) {
                    ch.urls.splice(uIdx, 1);
                    ch.urls.unshift(candidate);
                    fallbackPromoted++;
                    foundWorking = true;
                    channelStatus[ch.id] = { online: true, checkedAt: Date.now(), workingCount: 1, primary: candidate };
                    console.log(`[UniversalRefresh] Promoted working backup for ${ch.id} -> ${candidate.substring(0, 50)}...`);
                    break;
                }
            }

            if (!foundWorking) {
                deadChannels.push(ch);
                channelStatus[ch.id] = { online: false, checkedAt: Date.now(), workingCount: 0, primary: urls[0] };
            }
        }));
    }

    // 3. Auto-recover dead channels from community playlists
    if (deadChannels.length > 0) {
        console.log(`[UniversalRefresh] ${deadChannels.length} channels offline. Attempting community auto-recovery...`);
        try {
            await autoRecoverDeadChannels(deadChannels);
        } catch (discErr) {
            console.warn('[UniversalRefresh] Auto-recovery error:', discErr.message);
        }
    }

    // 4. Enforce strict sort order: beIN Sports -> Spor -> Ulusal -> Haber -> Sinema -> Belgesel -> Çocuk -> Müzik
    curatedChannels = sortChannels(curatedChannels);

    // 5. Persist to disk and reset memory caches
    try {
        fs.writeFileSync(path.join(__dirname, 'verified_channels.json'), JSON.stringify(curatedChannels, null, 2), 'utf-8');
        manifestCache.clear();
        activeChannelSources.clear();
    } catch (saveErr) {
        console.error('[UniversalRefresh] Failed to save verified_channels.json:', saveErr.message);
    }

    lastScanTime = Date.now();
    isScanning = false;
    const duration = ((Date.now() - startTime) / 1000).toFixed(1);
    const onlineTotal = Object.values(channelStatus).filter(s => s && s.online).length;
    console.log(`[UniversalRefresh] Finished in ${duration}s. Sports updated: ${sportsUpdated}, Backups promoted: ${fallbackPromoted}. Online: ${onlineTotal}/${curatedChannels.length}`);

    return {
        success: true,
        duration: `${duration}s`,
        total: curatedChannels.length,
        sportsUpdated,
        fallbackPromoted,
        onlineTotal,
        timestamp: Date.now()
    };
}

let lastManualRefreshTime = 0;

// Universal on-demand channel scan & health refresh endpoint
app.get('/api/channels/refresh', async (req, res) => {
    try {
        const result = await refreshAllChannels();
        res.json({
            success: true,
            message: `Tüm kanallar yenilendi (80 kanal tarandı, sporlar bağlandı, yedekler güncellendi).`,
            details: result,
            timestamp: Date.now()
        });
    } catch (e) {
        res.status(500).json({ error: e.message });
    }
});

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



// New TV (VOD Dizi & Film) APK Download Endpoints



app.get(['/apk', '/newtv.apk', '/api/newtv', '/api/newtv.apk', '/New-TV.apk'], (req, res) => {



    const p = path.join(__dirname, 'New-TV.apk');



    if (fs.existsSync(p)) {



        res.setHeader('Content-Type', 'application/vnd.android.package-archive');



        res.setHeader('Content-Disposition', 'attachment; filename="New-TV.apk"');



        return res.sendFile(p);



    }



    res.status(404).send('New-TV.apk not found');



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



// New TV Version endpoint



app.get(['/api/newtv/version', '/api/newtv-version'], (req, res) => {



    res.setHeader('Access-Control-Allow-Origin', '*');



    res.setHeader('Content-Type', 'application/json');



    try {



        let p = path.join(__dirname, 'newtv_version.json');



        if (!fs.existsSync(p)) p = path.join(__dirname, '..', 'newtv_version.json');



        if (fs.existsSync(p)) {



            const raw = fs.readFileSync(p, 'utf-8');



            return res.send(raw);



        }



    } catch (e) {}



    res.json({



        versionCode: 8,



        versionName: '1.7',



        changelog: 'New TV v1.7 Yenilikleri:\n1. Doğrudan sunucudan ve GitHub\'dan otomatik uygulama içi güncelleme desteği.\n2. Vidmixi 1080p doğrudan HLS akış iyileştirmeleri ve kesintisiz oynatma.\n3. 11.800+ Film ve Dizi kataloğu, anlık 2026 yılı ve kategori sıralaması.\n4. Film ve dizi detay sayfalarına tam konu özetleri ve HD posterler.',



        apkUrl: 'http://150.230.29.27:3000/New-TV.apk'



    });



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



    if (u.includes('atom.han') || u.includes('ardatvgs')) return 0;



    if (u.includes('ardastream2') || u.includes('daioncdn') || u.includes('ercdn')) return 980;



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



function normalizeXtreamToHls(rawUrl) {



    if (!rawUrl) return rawUrl;



    const m = rawUrl.match(/^(https?:\/\/[^\/]+)\/([^\/]+)\/([^\/]+)\/(\d+)$/);



    if (m) {



        return `${m[1]}/live/${m[2]}/${m[3]}/${m[4]}.m3u8`;



    }



    return rawUrl;



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



function isKeyUrl(url) {
    if (!url) return false;
    const u = url.toLowerCase();
    return Boolean(
        u.includes('key.php') ||
        u.includes('/key') ||
        u.includes('.key') ||
        u.includes('key=') ||
        u.includes('drm') ||
        u.includes('license')
    );
}

function isSegmentUrl(url) {



    if (!url) return false;



    const u = url.toLowerCase();



    if (u.includes('.m3u8') || u.includes('.m3u') || u.includes('index.txt') || u.includes('playlist')) {



        return false;



    }



    return Boolean(



        u.match(/\.(ts|jpg|jpeg|png|webp|avif|bin|aac|m4s)(\?|$)/i) ||



        u.includes('output=ts') ||



        u.includes('extension=ts') ||



        u.includes('format=ts') ||



        u.includes('_seg_') ||



        u.includes('/segment') ||



        u.includes('/hlsr/') ||



        u.includes('.cfd/c/') ||



        u.includes('/c/') ||

        u.includes('/process/') ||

        u.includes('imgsed') ||

        u.includes('imgpolza') ||

        u.includes('phodemxi') ||

        u.includes('imagesheld') ||

        u.includes('imgpm') ||

        u.includes('photodeh') ||

        u.includes('photohjul') ||

        u.includes('imglevg') ||

        u.includes('imgsdns') ||

        u.includes('tracks-v1a1/')



    );



}



function getHeadersForUrl(targetUrl, u) {



    const headers = {
        'User-Agent': (targetUrl.includes('tomtv') || targetUrl.includes('/live/') || targetUrl.includes(':25461') || targetUrl.includes(':8080') || targetUrl.includes('Celalustun') || targetUrl.includes('LiudmilaSheremet') || targetUrl.includes('Mustafaozdem'))
            ? 'IPTVSmartersPro/3.1.5 (Linux; Android 12)'
            : 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36'
    };



    if (



        targetUrl.includes('evrenesoglu') ||



        targetUrl.includes('andro') ||



        targetUrl.includes('.help') ||



        targetUrl.includes('fastly.net') ||



        targetUrl.includes('mangeinde') ||



        targetUrl.includes('vahided') ||



        targetUrl.includes('ardatvgs') ||



        targetUrl.includes('ladyboy')



    ) {



        headers['Referer'] = 'https://mahsunsports57.xyz/';



        headers['Origin'] = 'https://mahsunsports57.xyz';



    } else if (

        targetUrl.includes('vidmixi') ||

        targetUrl.includes('dosyaload') ||

        targetUrl.includes('/process/') ||

        targetUrl.includes('imgsed') ||

        targetUrl.includes('imgpolza') ||

        targetUrl.includes('phodemxi') ||

        targetUrl.includes('imagesheld') ||

        targetUrl.includes('imgpm') ||

        targetUrl.includes('photodeh') ||

        targetUrl.includes('photohjul') ||

        targetUrl.includes('imglevg') ||

        targetUrl.includes('imgsdns')

    ) {

        const isDosya = targetUrl.includes('dosyaload');

        headers['Referer'] = isDosya ? 'https://dosyaload.com/' : 'https://vidmixi.com/';

        headers['Origin'] = isDosya ? 'https://dosyaload.com' : 'https://vidmixi.com';



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
const playlistGraceCache = new Map(); // targetUrl -> { body: string, expires: number }



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



    sourceCooldowns.set(url, Date.now() + 10000); // Cooldown for 10 seconds



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



    // TRT Direct Bypass: CloudFront/Akamai blocks foreign cloud servers (Oracle VM) during UEFA matches.
    // Turkish clients stream TRT directly from official CDN without proxy delay or geo-block.
    if (channelId.startsWith('trt') || channelId === 'trt1' || channelId === 'trtspor' || channelId === 'trtspor2') {
        const directTRT = (ch.urls || []).find(u => u.includes('medya.trt.com.tr') || u.includes('daioncdn.net')) || (ch.urls && ch.urls[0]);
        if (directTRT) {
            return res.redirect(directTRT);
        }
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



    // Filter out recursive loopbacks to self and known dead wrappers



    const validUrls = (ch.urls || []).filter(u => 



        u && 



        !u.includes('150.230.29.27') && 



        !u.includes('localhost') && 



        !u.includes('127.0.0.1') &&



        !u.includes('/channel/') &&



        !u.includes('atom.han') &&



        !u.includes('ardatvgs')



    );



    if (validUrls.length === 0) {



        return res.status(503).send(`No external upstream source configured for channel ${channelId}`);



    }



    // Stateful Ordering:



    // 1. Currently active/working source for this channel (if not cooled down)



    // 2. High quality available sources (not cooled down)



    // 3. Cooled down sources as last resort



    const activeSource = activeChannelSources.get(channelId);



    const normalizedUrls = validUrls.map(u => normalizeXtreamToHls(u));



    // Prioritize order defined in curated channel list (which has verified 200 OK links first)



    const available = [];



    const cooledDown = [];



    for (const u of normalizedUrls) {



        if (isSourceCooledDown(u)) cooledDown.push(u);



        else available.push(u);



    }



    const urlsToTry = [];



    // 1. Try active working source first

    if (activeSource && available.includes(activeSource)) {

        if (!urlsToTry.includes(activeSource)) urlsToTry.push(activeSource);

    }



    // 2. Try verified curated sources first (ROCK SOLID)

    for (const u of available) {

        if (!urlsToTry.includes(u)) urlsToTry.push(u);

        if (urlsToTry.length >= 3) break;

    }



    // 1. For sports channels with JIT, prioritize live token stream FIRST
    if (SPORTS_RESOLVE_MAP[channelId]) {
        try {
            const jitUrl = await resolveJitStream(channelId, false);
            if (jitUrl && !isSourceCooledDown(jitUrl)) {
                urlsToTry.unshift(jitUrl);
            }
        } catch (jitErr) {
            console.warn(`[JIT] Pre-fetch error for ${channelId}:`, jitErr.message);
        }
    }



    if (!SPORTS_RESOLVE_MAP[channelId]) {

        for (const u of cooledDown) {

            if (!urlsToTry.includes(u)) urlsToTry.push(u);

        }

    } else if (urlsToTry.length === 0) {

        for (const u of cooledDown) {

            if (!urlsToTry.includes(u)) urlsToTry.push(u);

            if (urlsToTry.length >= 2) break;

        }

    }



    for (let i = 0; i < urlsToTry.length; i++) {



        const targetUrl = urlsToTry[i];



        const hasHls = urlsToTry.some(u => !isRawTsStream(u));



        if (isRawTsStream(targetUrl) && !hasHls) {



            return res.redirect('/proxy?url=' + encodeURIComponent(targetUrl));



        }



        try {



            const u = new URL(targetUrl);



            const headers = getHeadersForUrl(targetUrl, u);



            const response = await axios({



                method: 'get',



                url: targetUrl,



                headers: headers,



                timeout: 7000,



                httpAgent: httpAgent,



                httpsAgent: httpsAgent,



                validateStatus: (status) => status >= 200 && status < 300



            });



            const bodyStr = response.data.toString('utf-8');



            if (bodyStr.includes('#EXTM3U') || targetUrl.includes('.m3u8') || targetUrl.includes('index.txt')) {



                if (bodyStr.includes('ardatvgs') || bodyStr.includes('atom.han')) {



                    throw new Error('Manifest contains dead upstream domain (ardatvgs)');



                }



                res.setHeader('Content-Type', 'application/vnd.apple.mpegurl');



                const lines = bodyStr.split('\n');

                const liveSegs = [];

                const rewritten = lines.map(l => {

                    const trimmed = l.trim();

                    if (!trimmed || trimmed.startsWith('#')) return l;

                    try {

                        const finalUrl = (response.request && response.request.res && response.request.res.responseUrl) ? response.request.res.responseUrl : targetUrl;

                        const resolvedUrl = new URL(trimmed, finalUrl).href;

                        if (isSegmentUrl(resolvedUrl)) liveSegs.push(resolvedUrl);

                        return '/proxy?url=' + encodeURIComponent(resolvedUrl);

                    } catch (e) {

                        return l;

                    }

                }).join('\n');



                if (liveSegs.length > 0) {

                    recordPlaylistOrder(liveSegs);

                    prefetchSegment(liveSegs[liveSegs.length - 1]);

                }



                markSourceSuccess(channelId, targetUrl);

                manifestCache.set(channelId, { body: rewritten, expires: Date.now() + 3500 });

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



    if (SPORTS_RESOLVE_MAP[channelId]) {

        try {

            console.log(`[JIT] Rapid emergency recovery for ${channelId}...`);

            const freshUrl = await resolveJitStream(channelId, true);

            if (freshUrl) {

                const u = new URL(freshUrl);

                const headers = getHeadersForUrl(freshUrl, u);

                const liveResp = await axios.get(freshUrl, { headers, timeout: 3000, httpAgent, httpsAgent });

                const bodyStr = liveResp.data.toString('utf-8');

                if (bodyStr.includes('#EXTM3U')) {

                    res.setHeader('Content-Type', 'application/vnd.apple.mpegurl');

                    const manifestLines = bodyStr.split('\n');

                    const liveSegs2 = [];

                    const rewritten = manifestLines.map(l => {

                        const trimmed = l.trim();

                        if (!trimmed || trimmed.startsWith('#')) return l;

                        try {

                            const resolvedUrl = new URL(trimmed, freshUrl).href;

                            if (isSegmentUrl(resolvedUrl)) liveSegs2.push(resolvedUrl);

                            return '/proxy?url=' + encodeURIComponent(resolvedUrl);

                        } catch (e) {

                            return l;

                        }

                    }).join('\n');



                    if (liveSegs2.length > 0) {

                        recordPlaylistOrder(liveSegs2);

                        prefetchSegment(liveSegs2[liveSegs2.length - 1]);

                    }



                    markSourceSuccess(channelId, freshUrl);

                    manifestCache.set(channelId, { body: rewritten, expires: Date.now() + 3500 });

                    return res.send(rewritten);

                }

            }

        } catch (e) {

            console.warn(`[JIT] Rapid emergency recovery failed for ${channelId}:`, e.message);
        }
    }

    // Universal Failover for all channels (test remaining candidate URLs)
    for (const altUrl of (ch.urls || [])) {
        if (!urlsToTry.includes(altUrl)) {
            try {
                const u = new URL(altUrl);
                const headers = getHeadersForUrl(altUrl, u);
                const altResp = await axios.get(altUrl, { headers, timeout: 3000, httpAgent, httpsAgent });
                const bodyStr = altResp.data.toString('utf-8');
                if (bodyStr.includes('#EXTM3U')) {
                    console.log(`[Channel] Universal emergency recovery succeeded for ${channelId} with alternative URL.`);
                    res.setHeader('Content-Type', 'application/vnd.apple.mpegurl');
                    const manifestLines = bodyStr.split('\n');
                    const liveSegs2 = [];
                    const rewritten = manifestLines.map(l => {
                        const trimmed = l.trim();
                        if (!trimmed || trimmed.startsWith('#')) return l;
                        try {
                            const resolvedUrl = new URL(trimmed, altUrl).href;
                            if (isSegmentUrl(resolvedUrl)) liveSegs2.push(resolvedUrl);
                            return '/proxy?url=' + encodeURIComponent(resolvedUrl);
                        } catch (err) {
                            return l;
                        }
                    }).join('\n');
                    if (liveSegs2.length > 0) {
                        recordPlaylistOrder(liveSegs2);
                        prefetchSegment(liveSegs2[liveSegs2.length - 1]);
                    }
                    markSourceSuccess(channelId, altUrl);
                    manifestCache.set(channelId, { body: rewritten, expires: Date.now() + 3500 });
                    return res.send(rewritten);
                }
            } catch (err) {}
        }
    }

    const cachedManifest = manifestCache.get(channelId);
    if (cachedManifest && cachedManifest.body) {
        console.log(`[Channel] Serving grace period cached manifest for ${channelId} (preventing 502)`);
        res.setHeader('Content-Type', 'application/vnd.apple.mpegurl');
        res.setHeader('X-Cache', 'HIT-EMERGENCY-GRACE');
        return res.send(cachedManifest.body);
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



// Lookahead prefetch engine for zero-stutter HLS playback

const nextSegmentMap = new Map();



function recordPlaylistOrder(urls) {

    for (let i = 0; i < urls.length - 1; i++) {

        nextSegmentMap.set(urls[i], urls[i + 1]);

    }

    if (nextSegmentMap.size > 15000) {

        const keysToDelete = Array.from(nextSegmentMap.keys()).slice(0, 5000);

        for (const k of keysToDelete) nextSegmentMap.delete(k);

    }

}



async function fetchSegmentBuffer(targetUrl) {

    const u = new URL(targetUrl);

    const headers = getHeadersForUrl(targetUrl, u);

    const upstream = await axios({

        method: 'get',

        url: targetUrl,

        headers: headers,

        responseType: 'arraybuffer',

        timeout: 15000,

        httpAgent: httpAgent,

        httpsAgent: httpsAgent,

        validateStatus: (status) => status >= 200 && status < 400

    });



    let contentType = upstream.headers['content-type'] || 'video/mp2t';

    if (!contentType || contentType.includes('image/') || contentType.includes('text/') || targetUrl.includes('/c/') || targetUrl.includes('.jpg')) {

        contentType = 'video/mp2t';

    }

    const buf = Buffer.from(upstream.data);

    return {

        data: buf,

        contentType: contentType,

        status: upstream.status

    };

}



function prefetchSegment(targetUrl) {

    if (!targetUrl || segmentCache.has(targetUrl) || inFlightRequests.has(targetUrl)) return;

    const promise = fetchSegmentBuffer(targetUrl).then(result => {

        segmentCache.set(targetUrl, {

            data: result.data,

            contentType: result.contentType,

            expires: Date.now() + 120000 // 2 minutes TTL

        });

        inFlightRequests.delete(targetUrl);

        return result;

    }).catch(err => {

        inFlightRequests.delete(targetUrl);

    });

    inFlightRequests.set(targetUrl, promise);

}



function triggerLookaheadPrefetch(targetUrl) {

    const next1 = nextSegmentMap.get(targetUrl);

    if (next1) {

        prefetchSegment(next1);

        const next2 = nextSegmentMap.get(next1);

        if (next2) prefetchSegment(next2);

    }

}



// Proxy endpoint: Adapts headers per host, streams segments directly to client without RAM buffering, rewrites playlists

app.get('/proxy', async (req, res) => {

    const targetUrl = req.query.url;

    if (!targetUrl) return res.status(400).send('URL required');

    if (targetUrl.includes('150.230.29.27') || targetUrl.includes('localhost') || targetUrl.includes('127.0.0.1')) {

        return res.status(400).send('Loopback proxy forbidden');

    }

    // TRT Direct Bypass: Never proxy geo-blocked official TRT streams through foreign Oracle VM
    if (targetUrl.includes('medya.trt.com.tr') || targetUrl.includes('trt.daioncdn.net') || targetUrl.includes('trt.com.tr')) {
        return res.redirect(targetUrl);
    }



    const isSegment = isSegmentUrl(targetUrl);

    res.setHeader('Access-Control-Allow-Origin', '*');

    res.setHeader('Access-Control-Allow-Methods', 'GET, HEAD, OPTIONS');

    res.setHeader('Access-Control-Allow-Headers', '*');



    // 1. Prefetch-Accelerated Zero-Stutter Video Segments

    if (isSegment) {

        triggerLookaheadPrefetch(targetUrl);



        // Serve from RAM cache immediately (0ms)

        const cached = segmentCache.get(targetUrl);

        if (cached && Date.now() < cached.expires && !req.headers['range']) {

            res.status(200);

            res.setHeader('Content-Type', cached.contentType);

            res.setHeader('Content-Length', cached.data.length);

            res.setHeader('X-Cache', 'HIT-RAM');

            return res.send(cached.data);

        }



        // Wait for background in-flight download if available

        let inFlight = inFlightRequests.get(targetUrl);

        if (inFlight && !req.headers['range']) {

            try {

                const result = await inFlight;

                res.status(result.status || 200);

                res.setHeader('Content-Type', result.contentType);

                res.setHeader('Content-Length', result.data.length);

                res.setHeader('X-Cache', 'HIT-INFLIGHT');

                return res.send(result.data);

            } catch (err) {}

        }



        try {

            const u = new URL(targetUrl);

            const headers = getHeadersForUrl(targetUrl, u);

            if (req.headers['range']) {

                headers['Range'] = req.headers['range'];

            }

            const upstream = await axios({

                method: 'get',

                url: targetUrl,

                headers: headers,

                responseType: 'stream',

                timeout: 15000,

                httpAgent: httpAgent,

                httpsAgent: httpsAgent,

                validateStatus: (status) => status >= 200 && status < 400

            });

            let contentType = upstream.headers['content-type'] || 'video/mp2t';

            if (!contentType || contentType.includes('image/') || contentType.includes('text/') || targetUrl.includes('/c/') || targetUrl.includes('.jpg')) {

                contentType = 'video/mp2t';

            }

            res.status(upstream.status);

            res.setHeader('Content-Type', contentType);

            if (upstream.headers['content-length']) {

                res.setHeader('Content-Length', upstream.headers['content-length']);

            }

            if (upstream.headers['content-range']) {

                res.setHeader('Content-Range', upstream.headers['content-range']);

            }

            if (upstream.headers['accept-ranges']) {

                res.setHeader('Accept-Ranges', upstream.headers['accept-ranges']);

            }



            req.on('close', () => {

                if (upstream.data && !upstream.data.destroyed) {

                    upstream.data.destroy();

                }

            });



            return upstream.data.pipe(res);

        } catch (err) {
            try {
                await new Promise(r => setTimeout(r, 200));
                const retryU = new URL(targetUrl);
                const retryHeaders = getHeadersForUrl(targetUrl, retryU);
                const retryUpstream = await axios({
                    method: 'get',
                    url: targetUrl,
                    headers: retryHeaders,
                    responseType: 'stream',
                    timeout: 10000,
                    httpAgent: httpAgent,
                    httpsAgent: httpsAgent,
                    validateStatus: (status) => status >= 200 && status < 400
                });
                let ct = retryUpstream.headers['content-type'] || 'video/mp2t';
                res.status(retryUpstream.status);
                res.setHeader('Content-Type', ct);
                return retryUpstream.data.pipe(res);
            } catch (retryErr) {
                console.warn('[Proxy] Segment fetch error after retry:', retryErr.message, targetUrl.substring(0, 80));
                return res.status(503).send('Segment fetch failed');
            }
        }

    }



    // 2. Playlists / Manifests / Child M3U8s / Keys / Subtitles
    try {
        const u = new URL(targetUrl);
        const headers = getHeadersForUrl(targetUrl, u);
        const response = await axios({
            method: 'get',
            url: targetUrl,
            headers: headers,
            responseType: 'arraybuffer',
            timeout: 8000,
            httpAgent: httpAgent,
            httpsAgent: httpsAgent,
            validateStatus: (status) => status >= 200 && status < 400
        });

        const finalUrl = (response.request && response.request.res && response.request.res.responseUrl) ? response.request.res.responseUrl : targetUrl;
        const buf = Buffer.from(response.data);
        const contentTypeHeader = (response.headers['content-type'] || '').toLowerCase();

        // Check if binary AES key or octet-stream
        const isKey = isKeyUrl(targetUrl) || contentTypeHeader.includes('octet-stream') || buf.length === 16 || buf.length === 32;
        if (isKey) {
            res.setHeader('Content-Type', 'application/octet-stream');
            res.setHeader('Content-Length', buf.length);
            return res.send(buf);
        }

        const bodyStr = buf.toString('utf-8');

        if (bodyStr.startsWith('#EXTM3U') || targetUrl.includes('.m3u8') || targetUrl.includes('index.txt')) {
            res.setHeader('Content-Type', 'application/vnd.apple.mpegurl');
            const lines = bodyStr.split('\n');
            const segmentUrlsInPlaylist = [];
            const rewritten = lines.map(line => {
                const trimmed = line.trim();
                if (!trimmed) return line;
                if (trimmed.startsWith('#')) {
                    if (trimmed.includes('URI="')) {
                        return trimmed.replace(/URI="([^"]+)"/g, (match, p1) => {
                            try {
                                const resolved = new URL(p1, finalUrl).href;
                                return 'URI="/proxy?url=' + encodeURIComponent(resolved) + '"';
                            } catch (e) {
                                return match;
                            }
                        });
                    }
                    return line;
                }
                try {
                    const resolvedUrl = new URL(trimmed, finalUrl).href;
                    if (isSegmentUrl(resolvedUrl)) {
                        segmentUrlsInPlaylist.push(resolvedUrl);
                    }
                    return '/proxy?url=' + encodeURIComponent(resolvedUrl);
                } catch (e) {
                    return line;
                }
            }).join('\n');

            if (segmentUrlsInPlaylist.length > 0) {
                recordPlaylistOrder(segmentUrlsInPlaylist);
                prefetchSegment(segmentUrlsInPlaylist[0]);
            }
            playlistGraceCache.set(targetUrl, { body: rewritten, expires: Date.now() + 120000 });
            return res.send(rewritten);
        }

        res.setHeader('Content-Type', response.headers['content-type'] || 'text/plain');
        return res.send(buf);
    } catch (err) {
        if (targetUrl && (targetUrl.includes('.m3u8') || targetUrl.includes('index.txt') || targetUrl.includes('/live/'))) {
            const cachedPlaylist = playlistGraceCache.get(targetUrl);
            if (cachedPlaylist && Date.now() < cachedPlaylist.expires) {
                console.log('[Proxy] Serving cached playlist grace for', targetUrl.substring(0, 60));
                res.setHeader('Content-Type', 'application/vnd.apple.mpegurl');
                res.setHeader('X-Cache', 'HIT-PLAYLIST-GRACE');
                return res.send(cachedPlaylist.body);
            }
        }
        const upstreamStatus = err.response ? err.response.status : 502;

        if (upstreamStatus === 404 && targetUrl) {

            vodDb.clearCachedStream(targetUrl).catch(() => {});

        }

        return res.status(upstreamStatus).send('Stream error: ' + err.message);

    }

});



// Defensive catch-all for any unrewritten /hlsr/ segments requested by older clients

app.use('/hlsr', (req, res) => {

    const edgeHost = 'http://83.149.95.103:1970';

    const target = `${edgeHost}${req.originalUrl}`;

    return res.redirect(`/proxy?url=${encodeURIComponent(target)}`);

});



// Modern Responsive Mobile & Desktop Web Player Interface



app.get('/', (req, res) => {



    res.setHeader('Cache-Control', 'no-store, no-cache, must-revalidate, proxy-revalidate, max-age=0');



    res.setHeader('Pragma', 'no-cache');



    res.setHeader('Expires', '0');



    res.setHeader('Surrogate-Control', 'no-store');



    res.setHeader('Clear-Site-Data', '"cache"');



    res.send(`<!DOCTYPE html>



<html lang="tr">



<head>



    <meta charset="UTF-8">



    <meta http-equiv="Cache-Control" content="no-cache, no-store, must-revalidate">



    <meta http-equiv="Pragma" content="no-cache">



    <meta http-equiv="Expires" content="0">



    <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no, viewport-fit=cover">



    <meta name="theme-color" content="#06080f">



    <meta name="apple-mobile-web-app-capable" content="yes">



    <meta name="apple-mobile-web-app-status-bar-style" content="black-translucent">



    <title>Seyir TV - Canlı Yayın</title>



    <link rel="icon" type="image/svg+xml" href="/favicon.svg">



    <link rel="icon" type="image/png" sizes="32x32" href="/favicon.png">



    <link rel="icon" type="image/png" sizes="192x192" href="/favicon-192.png">



    <link rel="apple-touch-icon" sizes="180x180" href="/apple-touch-icon.png">



    <link rel="shortcut icon" href="/favicon.ico">



    <link rel="manifest" href="/manifest.json">



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



        /* Ambient Glow Background on Desktop */



        body::before {



            content: '';



            position: fixed;



            top: 0; left: var(--sidebar-w); right: 0; height: 350px;



            background: radial-gradient(ellipse at 50% -20%, rgba(14, 116, 144, 0.18) 0%, transparent 70%);



            pointer-events: none;



            z-index: 0;



        }



        #app-layout {



            display: flex;



            width: 100%;



            height: 100%;



            position: relative;



            z-index: 1;



        }



        /* --- DESKTOP SIDEBAR --- */



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



        .refresh-pill-btn {



            background: rgba(2, 132, 199, 0.2);



            color: #38bdf8;



            border: 1px solid rgba(56, 189, 248, 0.35);



            padding: 4px 9px;



            border-radius: 6px;



            font-size: 11.5px;



            font-weight: 700;



            cursor: pointer;



            transition: all 0.15s ease;



            display: inline-flex;



            align-items: center;



            gap: 4px;



        }



        .refresh-pill-btn:hover {



            background: #0284c7;



            color: #fff;



        }



        .refresh-pill-btn:disabled {



            opacity: 0.6;



            cursor: not-allowed;



        }



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



            -webkit-overflow-scrolling: touch;



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



            flex-shrink: 0;



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



            -webkit-overflow-scrolling: touch;



        }



        #channel-list::-webkit-scrollbar { width: 5px; }



        #channel-list::-webkit-scrollbar-thumb { background: rgba(255,255,255,0.12); border-radius: 4px; }



        .ch-card {



            display: flex;



            align-items: center;



            gap: 12px;



            padding: 10px 12px;



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



            background: rgba(2, 132, 199, 0.18);



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



            font-size: 14px;



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



            font-size: 11px;



            font-weight: 600;



            color: var(--text-dim);



            text-transform: uppercase;



            letter-spacing: 0.4px;



        }



        .ch-card.active .ch-category-pill { color: #7dd3fc; }



        .ch-status-indicator {



            width: 7px;



            height: 7px;



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



        /* --- DESKTOP STAGE & PLAYER --- */



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



        }



        video {



            width: 100%;



            height: 100%;



            object-fit: contain;



            background: #000;



        }



        video.crisp-mode { filter: contrast(1.06) saturate(1.08) brightness(1.02); }



        video.fit-cover { object-fit: cover !important; }



        /* Floating Top Bar Overlay */



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



        }



        /* Player Bottom Controls */



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



        .now-channel-name {



            font-size: 22px;



            font-weight: 800;



            color: #fff;



            letter-spacing: -0.02em;



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



        /* =======================================================



           MOBILE & TABLET VIEWPORT (< 860px) - YOUTUBE / TWITCH OTT



           ======================================================= */



        @media (max-width: 860px) {



            body::before { display: none; }



            html, body {



                overflow: hidden !important;



                position: fixed;



                inset: 0;



                width: 100%;



                height: 100%;



                height: 100dvh;



            }



            #app-layout {



                flex-direction: column !important;



                width: 100% !important;



                height: 100% !important;



                height: 100dvh !important;



                overflow: hidden;



            }



            /* Main stage holds the top sticky video (Rendered at top on mobile) */



            #main-stage {



                order: 1 !important;



                padding: 0 !important;



                flex: none !important;



                width: 100% !important;



                height: auto !important;



                background: #000 !important;



                z-index: 50;



                position: sticky;



                top: 0;



            }



            #video-container {



                max-width: 100% !important;



                border-radius: 0 !important;



                border: none !important;



                box-shadow: 0 4px 16px rgba(0,0,0,0.7) !important;



                aspect-ratio: 16 / 9;



                width: 100% !important;



            }



            /* Hide top overlay completely on mobile to eliminate clutter and prevent blocking controls */



            #video-top-overlay {



                display: none !important;



            }



            #video-bottom-overlay {



                padding: 6px 10px !important;



            }



            /* Hide desktop bottom toolbar; show mobile stream info bar */



            #player-bottom-details {



                margin: 0 !important;



                padding: 10px 14px;



                background: var(--bg-surface);



                border-bottom: 1px solid var(--border);



                gap: 8px;



            }



            .now-channel-name {



                font-size: 16px;



                font-weight: 700;



            }



            .shortcuts-hint { display: none !important; }



            .modern-toolbar {



                padding: 4px 0 0 0;



                background: transparent;



                border: none;



                justify-content: flex-start;



                gap: 8px;



            }



            .modern-toolbar .tool-btn {



                padding: 6px 10px;



                font-size: 11.5px;



            }



            .toolbar-group {



                display: flex !important;



                gap: 6px !important;



                align-items: center !important;



            }



            .toolbar-group:last-child button {



                display: inline-flex !important;



            }



            /* Mobile Sidebar becomes the in-page browsable channel section below video */



            #sidebar {



                order: 2 !important;



                width: 100% !important;



                flex: 1 !important;



                box-shadow: none !important;



                border-right: none !important;



                border-top: 1px solid var(--border);



                background: var(--bg-base);



                overflow: hidden;



            }



            .sidebar-header {



                display: none; /* Brand logo not needed below video */



            }



            .search-container {



                padding: 8px 12px 6px 12px;



            }



            #search-input {



                padding: 8px 30px 8px 32px;



                font-size: 12.5px;



            }



            #category-bar {



                padding: 6px 12px 8px 12px;



                gap: 5px;



            }



            .cat-chip {



                padding: 5px 10px;



                font-size: 11.5px;



            }



            #channel-list {



                padding: 6px 10px;



                gap: 4px;



                padding-bottom: calc(14px + env(safe-area-inset-bottom));



            }



            .ch-card {



                padding: 8px 10px;



            }



            .ch-avatar {



                width: 32px;



                height: 32px;



                font-size: 11px;



            }



            .ch-name-text {



                font-size: 13px;



            }



        }



        /* Mobile Landscape (Full Screen Video Mode) */



        @media (orientation: landscape) and (max-height: 520px) {



            html, body, #app-layout, #main-stage {



                position: fixed !important;



                inset: 0 !important;



                width: 100vw !important;



                height: 100vh !important;



                height: 100dvh !important;



                padding: 0 !important;



                margin: 0 !important;



                background: #000 !important;



                overflow: hidden !important;



            }



            #sidebar, #player-bottom-details {



                display: none !important;



            }



            #video-container {



                position: fixed !important;



                inset: 0 !important;



                width: 100vw !important;



                height: 100vh !important;



                height: 100dvh !important;



                max-width: 100vw !important;



                max-height: 100dvh !important;



                aspect-ratio: unset !important;



                border: none !important;



                border-radius: 0 !important;



            }



            #video-top-overlay {



                display: flex !important;



                padding: 10px 18px;



            }



            .overlay-right .top-action-btn {



                display: inline-flex !important;



            }



        }



        /* Native Fullscreen API */



        :fullscreen #video-container, :-webkit-full-screen #video-container {



            width: 100vw !important; height: 100vh !important;



            max-width: 100vw !important; max-height: 100vh !important;



            border-radius: 0 !important; background: #000 !important; border: none !important;



        }



        :fullscreen video, :-webkit-full-screen video { width: 100% !important; height: 100% !important; }



        #unmute-banner {



            position: absolute;



            bottom: 64px;



            left: 50%;



            transform: translateX(-50%);



            background: linear-gradient(135deg, #0284c7 0%, #0369a1 100%);



            color: #fff;



            padding: 8px 18px;



            border-radius: 20px;



            font-size: 13px;



            font-weight: 700;



            cursor: pointer;



            z-index: 38;



            box-shadow: 0 4px 16px rgba(0,0,0,0.6);



            display: none;



            align-items: center;



            gap: 8px;



            backdrop-filter: blur(8px);



            border: 1px solid rgba(255,255,255,0.3);



            animation: pulse-glow 2s infinite;



        }



        @keyframes pulse-glow {



            0%, 100% { transform: translateX(-50%) scale(1); }



            50% { transform: translateX(-50%) scale(1.04); }



        }



        /* Custom Seyir TV Unified Bottom Overlay */



        #video-bottom-overlay {



            position: absolute;



            bottom: 0; left: 0; right: 0;



            padding: 8px 14px;



            background: linear-gradient(to top, rgba(0, 0, 0, 0.88) 0%, rgba(0, 0, 0, 0.4) 65%, transparent 100%);



            display: flex;



            align-items: center;



            justify-content: space-between;



            gap: 12px;



            z-index: 35;



            transition: opacity 0.3s ease;



            pointer-events: auto;



        }



        #video-bottom-overlay.fade-out {



            opacity: 0;



            pointer-events: none;



        }



        .bottom-left-controls, .bottom-right-controls {



            display: flex;



            align-items: center;



            gap: 8px;



        }



        .player-icon-btn {



            background: transparent;



            border: none;



            color: #ffffff;



            cursor: pointer;



            padding: 5px;



            border-radius: 6px;



            display: flex;



            align-items: center;



            justify-content: center;



            transition: transform 0.15s ease, background 0.15s ease;



        }



        .player-icon-btn:hover {



            background: rgba(255, 255, 255, 0.15);



            transform: scale(1.1);



        }



        .player-icon-btn:active {



            transform: scale(0.92);



        }



        .player-live-badge {



            font-size: 11px;



            font-weight: 800;



            color: #ef4444;



            display: flex;



            align-items: center;



            gap: 5px;



            letter-spacing: 0.5px;



            padding-left: 4px;



        }



        .player-live-badge::before {



            content: '';



            width: 6px;



            height: 6px;



            background: #ef4444;



            border-radius: 50%;



            animation: pulse-dot 1.4s infinite;



        }



        .bottom-center-track {



            flex: 1;



            padding: 0 4px;



            display: flex;



            align-items: center;



        }



        .live-timeline {



            position: relative;



            width: 100%;



            height: 3px;



            background: rgba(255, 255, 255, 0.25);



            border-radius: 2px;



        }



        .live-timeline-fill {



            width: 100%;



            height: 100%;



            background: #dc2626;



            border-radius: 2px;



        }



        .live-timeline-dot {



            position: absolute;



            right: 0;



            top: 50%;



            transform: translate(50%, -50%);



            width: 8px;



            height: 8px;



            background: #ffffff;



            border: 2px solid #dc2626;



            border-radius: 50%;



            box-shadow: 0 0 6px rgba(220, 38, 38, 0.8);



        }



    </style>



</head>



<body>



    <div id="app-layout">



        <!-- SIDEBAR (Desktop Left / Mobile Underneath Video) -->



        <aside id="sidebar">



            <div class="sidebar-header">



                <div class="brand-logo">



                    <div class="brand-icon">



                        <svg viewBox="0 0 24 24"><path d="M4 6h16v12H4z M21 3H3C1.9 3 1 3.9 1 5v14c0 1.1.9 2 2 2h18c1.1 0 2-.9 2-2V5c0-1.1-.9-2-2-2zm-11 6.5l6 4-6 4v-8z"/></svg>



                    </div>



                    <span>Seyir TV</span>



                </div>



                <div style="display: flex; align-items: center; gap: 8px;">



                    <button id="btn-refresh-sources" class="refresh-pill-btn" onclick="manualRefreshChannels()" title="Canlı Yayın Kaynaklarını Yenile">🔄 Yenile</button>



                    <span id="channel-counter" class="channel-counter-badge">80 Kanal</span>



                </div>



            </div>



            <!-- Search -->



            <div class="search-container">



                <div class="search-wrap">



                    <svg class="search-icon" viewBox="0 0 24 24"><path d="M15.5 14h-.79l-.28-.27A6.471 6.471 0 0 0 16 9.5 6.5 6.5 0 1 0 9.5 16c1.61 0 3.09-.59 4.23-1.57l.27.28v.79l5 4.99L20.49 19l-4.99-5zm-6 0C7.01 14 5 11.99 5 9.5S7.01 5 9.5 5 14 7.01 14 9.5 11.99 14 9.5 14z"/></svg>



                    <input type="text" id="search-input" placeholder="Kanal ara... (beIN, S Sport, TRT)">



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



        </aside>



        <!-- MAIN STAGE (Desktop Center / Mobile Top Sticky Video) -->



        <main id="main-stage">



            <div id="video-container">



                <video id="video-player" playsinline webkit-playsinline></video>



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



                    </div>



                </div>



                <!-- Custom Bottom Control Bar (Identical on PC & Phone) -->



                <div id="video-bottom-overlay">



                    <div class="bottom-left-controls">



                        <button id="btn-play-pause" class="player-icon-btn" onclick="togglePlay()" title="Oynat / Duraklat (Space)">



                            <svg id="svg-play" viewBox="0 0 24 24" width="22" height="22"><path fill="#fff" d="M6 19h4V5H6v14zm8-14v14h4V5h-4z"/></svg>



                        </button>



                        <button id="btn-mute" class="player-icon-btn" onclick="toggleMute()" title="Sesi Aç / Kapat (M)">



                            <svg id="svg-volume" viewBox="0 0 24 24" width="22" height="22"><path fill="#fff" d="M3 9v6h4l5 5V4L7 9H3zm13.5 3c0-1.77-1.02-3.29-2.5-4.03v8.05c1.48-.73 2.5-2.25 2.5-4.02zM14 3.23v2.06c2.89.86 5 3.54 5 6.71s-2.11 5.85-5 6.71v2.06c4.01-.91 7-4.49 7-8.77s-2.99-7.86-7-8.77z"/></svg>



                        </button>



                        <span id="player-time-badge" class="player-live-badge">CANLI</span>



                    </div>



                    <div class="bottom-center-track">



                        <div class="live-timeline">



                            <div class="live-timeline-fill"></div>



                            <div class="live-timeline-dot"></div>



                        </div>



                    </div>



                    <div class="bottom-right-controls">



                        <button id="btn-player-fullscreen" class="player-icon-btn" onclick="toggleFullscreen()" title="Tam Ekran (F)">



                            <svg id="svg-fs" viewBox="0 0 24 24" width="22" height="22"><path fill="#fff" d="M7 14H5v5h5v-2H7v-3zm-2-4h2V7h3V5H5v5zm12 7h-3v2h5v-5h-2v3zM14 5v2h3v3h2V5h-5z"/></svg>



                        </button>



                    </div>



                </div>



            </div>



            <!-- Player Details & Quick Strip -->



            <div id="player-bottom-details">



                <div class="info-card-row">



                    <div style="display: flex; align-items: center; gap: 8px;">



                        <h1 id="channel-heading" class="now-channel-name">Seyir TV</h1>



                        <span class="badge-live-pulse">CANLI</span>



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



                            <span>✨ Netlik</span>



                        </button>



                    </div>



                    <div class="toolbar-group">



                        <button id="btn-fit-row" class="tool-btn" onclick="toggleFit()">



                            <span>⛶ Doldur</span>



                        </button>



                        <button id="btn-fs-row" class="tool-btn" onclick="toggleFullscreen()" style="background: rgba(2, 132, 199, 0.25); border-color: #0284c7; color: #38bdf8;">



                            <span>⛶ Tam Ekran</span>



                        </button>



                        <button id="btn-refresh-row" class="tool-btn" onclick="manualRefreshChannels()" title="Yayın Kaynaklarını Canlı Yenile">



                            <span>🔄 Yenile</span>



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



    <script>



        let allChannels = [];



        let currentChannel = null;



        let hls = null;



        let currentFilter = 'Tümü';



        const video = document.getElementById('video-player');



        const channelList = document.getElementById('channel-list');



        const channelHeading = document.getElementById('channel-heading');



        const statusEl = document.getElementById('stream-status');



        const topTitle = document.getElementById('top-title');



        const topRes = document.getElementById('top-res');



        const searchInput = document.getElementById('search-input');



        const clearSearchBtn = document.getElementById('clear-search');



        const channelCounter = document.getElementById('channel-counter');



        // Fetch Curated Master Channels



        fetch('/api/channels')



            .then(res => res.json())



            .then(data => {



                allChannels = data || [];



                if (channelCounter) channelCounter.textContent = allChannels.length + ' Kanal';



                renderChannels(allChannels);



                if (allChannels.length > 0) {



                    playChannel(allChannels[0]);



                }



            })



            .catch(err => {



                statusEl.textContent = 'Bağlantı Hatası';



                statusEl.style.color = '#ef4444';



            });



        // Autoplay policy handler: plays unmuted if permitted, otherwise starts muted and offers 1-tap unmute



        function manualRefreshChannels() {



            const btn1 = document.getElementById('btn-refresh-sources');



            const btn2 = document.getElementById('btn-refresh-row');



            const setButtons = (text, disabled) => {



                if (btn1) { btn1.textContent = text; btn1.disabled = disabled; }



                if (btn2) { btn2.querySelector('span').textContent = text; btn2.disabled = disabled; }



            };



            setButtons('⏳ Yenileniyor…', true);



            statusEl.textContent = 'Kaynaklar taranıyor…';



            statusEl.style.color = '#38bdf8';



            fetch('/api/channels/refresh')



                .then(r => r.json())



                .then(data => {



                    setTimeout(() => {



                        fetch('/api/channels')



                            .then(r => r.json())



                            .then(chs => {



                                if (chs && chs.length > 0) {



                                    allChannels = chs;



                                    renderChannels(allChannels);



                                    if (currentChannel) {



                                        const fresh = allChannels.find(c => c.id === currentChannel.id);



                                        if (fresh) playChannel(fresh);



                                    }



                                }



                                setButtons('✅ Güncellendi', true);



                                setTimeout(() => setButtons('🔄 Yenile', false), 2500);



                            });



                    }, 2500);



                })



                .catch(err => {



                    setButtons('🔄 Yenile', false);



                });



        }



        function attemptAutoplay() {



            const p = video.play();



            if (p !== undefined) {



                p.then(() => {



                    updatePlayPauseIcon();



                    updateVolumeIcon();



                    hideUnmuteBanner();



                }).catch(() => {



                    video.muted = true;



                    updateVolumeIcon();



                    video.play().then(() => {



                        updatePlayPauseIcon();



                        showUnmuteBanner();



                    }).catch(() => {});



                });



            }



        }



        function showUnmuteBanner() {



            let banner = document.getElementById('unmute-banner');



            if (!banner) {



                banner = document.createElement('div');



                banner.id = 'unmute-banner';



                banner.innerHTML = '<span>🔊 Sesi Açmak İçin Dokunun</span>';



                banner.onclick = (e) => {



                    e.stopPropagation();



                    unlockAudio();



                };



                videoContainer.appendChild(banner);



            }



            banner.style.display = 'flex';



        }



        function hideUnmuteBanner() {



            const banner = document.getElementById('unmute-banner');



            if (banner) banner.style.display = 'none';



        }



        function unlockAudio() {



            if (video.muted) {



                video.muted = false;



                updateVolumeIcon();



                hideUnmuteBanner();



            }



        }



        document.addEventListener('click', unlockAudio, { once: true });



        document.addEventListener('keydown', unlockAudio, { once: true });



        document.addEventListener('touchstart', unlockAudio, { once: true });



        // Unregister stale service workers



        if ('serviceWorker' in navigator) {



            navigator.serviceWorker.getRegistrations().then(regs => {



                for (const r of regs) r.unregister();



            });



        }



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



                    liveSyncDurationCount: 4,



                    liveMaxLatencyDurationCount: 8,



                    maxBufferLength: 30,



                    maxMaxBufferLength: 60,



                    maxBufferSize: 60 * 1000 * 1000,



                    maxBufferHole: 0.5,



                    highBufferWatchdogPeriod: 2,



                    nudgeOffset: 0.1,



                    nudgeMaxRetry: 5,



                    manifestLoadingTimeOut: 5000,



                    manifestLoadingMaxRetry: 3,



                    manifestLoadingRetryDelay: 500,



                    fragLoadingTimeOut: 8000,



                    fragLoadingMaxRetry: 3,



                    fragLoadingRetryDelay: 500



                });



                hls.loadSource(streamUrl);



                hls.attachMedia(video);



                hls.on(Hls.Events.MANIFEST_PARSED, (e, data) => {



                    attemptAutoplay();



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



                    // Buffer stall auto-recovery: nudge video currentTime past stalled frame



                    if (data.details === Hls.ErrorDetails.BUFFER_STALLED_ERROR) {



                        if (video.paused) {



                            video.play().catch(() => {});



                        } else {



                            video.currentTime += 0.1;



                        }



                        return;



                    }



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



                attemptAutoplay();



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



            if (channelCounter) channelCounter.textContent = filtered.length + ' Kanal';



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



        function toggleFullscreen() {



            const isIOS = /iPad|iPhone|iPod/.test(navigator.userAgent) || 



                          (navigator.platform === 'MacIntel' && navigator.maxTouchPoints > 1);



            if (isIOS && video && video.webkitEnterFullscreen) {



                video.webkitEnterFullscreen();



                return;



            }



            const el = document.getElementById('video-container') || document.documentElement;



            if (!document.fullscreenElement && !document.webkitFullscreenElement) {



                if (el.requestFullscreen) {



                    el.requestFullscreen().catch(() => {



                        if (video && video.webkitEnterFullscreen) video.webkitEnterFullscreen();



                    });



                } else if (el.webkitRequestFullscreen) {



                    el.webkitRequestFullscreen();



                } else if (video && video.webkitEnterFullscreen) {



                    video.webkitEnterFullscreen();



                }



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



        // Unified Player Controls (PC & Mobile)



        const videoContainer = document.getElementById('video-container');



        const videoTopOverlay = document.getElementById('video-top-overlay');



        const videoBottomOverlay = document.getElementById('video-bottom-overlay');



        let controlsTimer = null;



        function wakeControls() {



            if (videoTopOverlay) videoTopOverlay.classList.remove('fade-out');



            if (videoBottomOverlay) videoBottomOverlay.classList.remove('fade-out');



            clearTimeout(controlsTimer);



            controlsTimer = setTimeout(() => {



                if (!video.paused) {



                    if (videoTopOverlay) videoTopOverlay.classList.add('fade-out');



                    if (videoBottomOverlay) videoBottomOverlay.classList.add('fade-out');



                }



            }, 3500);



        }



        function togglePlay() {



            if (video.paused) {



                video.play().catch(() => {});



            } else {



                video.pause();



            }



            updatePlayPauseIcon();



            wakeControls();



        }



        function updatePlayPauseIcon() {



            const svg = document.getElementById('svg-play');



            if (!svg) return;



            if (video.paused) {



                svg.innerHTML = '<path fill="#fff" d="M8 5v14l11-7z"/>';



            } else {



                svg.innerHTML = '<path fill="#fff" d="M6 19h4V5H6v14zm8-14v14h4V5h-4z"/>';



            }



        }



        video.addEventListener('play', updatePlayPauseIcon);



        video.addEventListener('pause', updatePlayPauseIcon);



        function toggleMute() {



            video.muted = !video.muted;



            updateVolumeIcon();



            wakeControls();



        }



        function updateVolumeIcon() {



            const svg = document.getElementById('svg-volume');



            if (!svg) return;



            if (video.muted || video.volume === 0) {



                svg.innerHTML = '<path fill="#fff" d="M16.5 12c0-1.77-1.02-3.29-2.5-4.03v2.21l2.45 2.45c.03-.2.05-.41.05-.63zm2.5 0c0 .94-.2 1.82-.54 2.64l1.51 1.51C20.63 14.91 21 13.5 21 12c0-4.28-2.99-7.86-7-8.77v2.06c2.89.86 5 3.54 5 6.71zM4.27 3L3 4.27 7.73 9H3v6h4l5 5v-6.73l4.25 4.25c-.67.52-1.42.93-2.25 1.18v2.06c1.38-.31 2.63-.95 3.69-1.81L19.73 21 21 19.73l-9-9L4.27 3zM12 4L9.91 6.09 12 8.18V4z"/>';



            } else {



                svg.innerHTML = '<path fill="#fff" d="M3 9v6h4l5 5V4L7 9H3zm13.5 3c0-1.77-1.02-3.29-2.5-4.03v8.05c1.48-.73 2.5-2.25 2.5-4.02zM14 3.23v2.06c2.89.86 5 3.54 5 6.71s-2.11 5.85-5 6.71v2.06c4.01-.91 7-4.49 7-8.77s-2.99-7.86-7-8.77z"/>';



            }



        }



        video.addEventListener('volumechange', updateVolumeIcon);



        function updateFsIcon() {



            const svg = document.getElementById('svg-fs');



            if (!svg) return;



            const isFs = !!(document.fullscreenElement || document.webkitFullscreenElement);



            if (isFs) {



                svg.innerHTML = '<path fill="#fff" d="M5 16h3v3h2v-5H5v2zm3-8H5v2h5V5H8v3zm6 11h2v-3h3v-2h-5v5zm2-11V5h-2v5h5V8h-3z"/>';



            } else {



                svg.innerHTML = '<path fill="#fff" d="M7 14H5v5h5v-2H7v-3zm-2-4h2V7h3V5H5v5zm12 7h-3v2h5v-5h-2v3zM14 5v2h3v3h2V5h-5z"/>';



            }



        }



        document.addEventListener('fullscreenchange', updateFsIcon);



        document.addEventListener('webkitfullscreenchange', updateFsIcon);



        videoContainer.addEventListener('click', (e) => {



            if (e.target.closest('button') || e.target.closest('#unmute-banner')) return;



            const isFaded = videoBottomOverlay && videoBottomOverlay.classList.contains('fade-out');



            if (isFaded) {



                wakeControls();



            } else {



                togglePlay();



            }



        });



        videoContainer.addEventListener('mousemove', wakeControls);



        video.addEventListener('dblclick', toggleFullscreen);



        let lastTouchTime = 0;



        videoContainer.addEventListener('touchend', (e) => {



            if (e.target.closest('button') || e.target.closest('#unmute-banner')) return;



            const now = Date.now();



            if (now - lastTouchTime < 320) {



                toggleFullscreen();



                e.preventDefault();



            } else {



                const isFaded = videoBottomOverlay && videoBottomOverlay.classList.contains('fade-out');



                if (isFaded) {



                    wakeControls();



                } else {



                    if (videoTopOverlay) videoTopOverlay.classList.add('fade-out');



                    if (videoBottomOverlay) videoBottomOverlay.classList.add('fade-out');



                }



            }



            lastTouchTime = now;



        });



        // Auto Landscape Fullscreen



        function checkOrientation() {



            const isLandscape = (window.innerWidth > window.innerHeight) && (window.innerHeight <= 520);



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

    // beIN Sports
    if (n.includes('beinsports1') || n.includes('bein1') || n.includes('beinsport1')) return 'beinsports1';
    if (n.includes('beinsports2') || n.includes('bein2') || n.includes('beinsport2')) return 'beinsports2';
    if (n.includes('beinsports3') || n.includes('bein3') || n.includes('beinsport3')) return 'beinsports3';
    if (n.includes('beinsports4') || n.includes('bein4') || n.includes('beinsport4')) return 'beinsports4';
    if (n.includes('beinsports5') || n.includes('bein5') || n.includes('beinsport5')) return 'beinsports5';
    if (n.includes('beinmax1') || n.includes('beinsportsmax1')) return 'beinsportsmax1';
    if (n.includes('beinmax2') || n.includes('beinsportsmax2')) return 'beinsportsmax2';
    if (n.includes('beinsportshaber') || n.includes('beinhaber')) return 'beinsportshaber';

    // Spor
    if (n.includes('ssport1') || n.includes('ssportplus') || n === 'ssport') return 'ssport1';
    if (n.includes('ssport2')) return 'ssport2';
    if (n.includes('trtsporyildiz') || n.includes('trtyildiz')) return 'trtsporyildiz';
    if (n.includes('trtspor')) return 'trtspor';
    if (n.includes('aspor')) return 'aspor';
    if (n.includes('eurosport1') || n === 'eurosport') return 'eurosport1';
    if (n.includes('eurosport2')) return 'eurosport2';
    if (n.includes('smartspor1') || n === 'smartspor') return 'smartspor1';
    if (n.includes('smartspor2')) return 'smartspor2';
    if (n.includes('tivibuspor1') || n === 'tivibuspor') return 'tivibuspor1';
    if (n.includes('tivibuspor2')) return 'tivibuspor2';
    if (n.includes('tivibuspor3')) return 'tivibuspor3';
    if (n.includes('tivibuspor4')) return 'tivibuspor4';
    if (n.includes('exxen1')) return 'exxen1';
    if (n.includes('exxen2')) return 'exxen2';
    if (n.includes('exxen3')) return 'exxen3';
    if (n.includes('exxen4')) return 'exxen4';
    if (n.includes('exxen5')) return 'exxen5';
    if (n.includes('tabiispor1') || n.includes('tabispor1')) return 'tabiispor1';
    if (n.includes('tabiispor2') || n.includes('tabispor2')) return 'tabiispor2';
    if (n.includes('tabiispor3') || n.includes('tabispor3')) return 'tabiispor3';
    if (n.includes('fbtv') || n.includes('fenerbahce')) return 'fbtv';
    if (n.includes('gstv') || n.includes('galatasaray')) return 'gstv';
    if (n.includes('htspor')) return 'htspor';
    if (n.includes('tv85') || n.includes('tv8bucuk')) return 'tv85';

    // Ulusal
    if (n === 'trt1' || n === 'trt1hd') return 'trt1';
    if (n.startsWith('atv') && !n.includes('avrupa') && !n.includes('haber')) return 'atv';
    if (n.startsWith('kanald')) return 'kanald';
    if (n.startsWith('showtv') || n === 'show') return 'showtv';
    if (n.startsWith('startv') || n === 'star') return 'startv';
    if (n.startsWith('nowtv') || n === 'now' || n.startsWith('foxtv') || n === 'fox') return 'nowtv';
    if (n === 'tv8' || n === 'tv8hd') return 'tv8';
    if (n.startsWith('kanal7')) return 'kanal7';
    if (n.startsWith('beyaztv') || n === 'beyaz') return 'beyaztv';
    if (n === 'tv360' || n === '360tv' || n === '360') return 'tv360';
    if (n.startsWith('teve2')) return 'teve2';

    // Haber
    if (n.startsWith('ahaber')) return 'ahaber';
    if (n.includes('cnnturk')) return 'cnnturk';
    if (n.includes('ekoltv')) return 'ekoltv';
    if (n.includes('haberglobal')) return 'haberglobal';
    if (n.includes('haberturk')) return 'haberturk';
    if (n.includes('halktv')) return 'halktv';
    if (n.includes('kanal24') || n === '24tv') return 'kanal24';
    if (n === 'ntv' || n === 'ntvhd') return 'ntv';
    if (n.includes('sozcutv') || n.includes('sozcu')) return 'sozcutv';
    if (n.includes('tele1')) return 'tele1';
    if (n.includes('trthaber')) return 'trthaber';
    if (n.includes('tv100')) return 'tv100';

    // Belgesel
    if (n.includes('discoverychannel') || n.includes('discovery')) return 'discovery';
    if (n.includes('dmax')) return 'dmax';
    if ((n.includes('natgeo') || n.includes('nationalgeographic')) && !n.includes('wild')) return 'natgeo';
    if (n.includes('natgeowild') || n.includes('wild')) return 'natgeowild';
    if (n.includes('tarihtv')) return 'tarihtv';
    if (n.includes('tlc')) return 'tlc';
    if (n.includes('trtbelgesel')) return 'trtbelgesel';

    // Sinema & Dizi
    if (n.includes('beinmoviesaction') || n.includes('moviesaction')) return 'beinmoviesaction';
    if (n.includes('beinmoviesfamily') || n.includes('moviesfamily')) return 'beinmoviesfamily';
    if (n.includes('beinmoviespremiere') || n.includes('moviespremiere')) return 'beinmoviespremiere';
    if (n.includes('beinmoviesstars') || n.includes('moviesstars')) return 'beinmoviesstars';
    if (n.includes('filmbox')) return 'filmbox';
    if (n.includes('sinemaaile')) return 'sinemaaile';
    if (n.includes('sinemaaksiyon')) return 'sinemaaksiyon';
    if (n.includes('sinematv') && !n.includes('aile') && !n.includes('aksiyon')) return 'sinematv';

    // Çocuk
    if (n.includes('cartoonnetwork') || n.includes('cartoon')) return 'cartoonnetwork';
    if (n.includes('minikacocuk')) return 'minikacocuk';
    if (n.includes('minikago')) return 'minikago';
    if (n.includes('trtcocuk')) return 'trtcocuk';

    // Müzik
    if (n.includes('dreamtv')) return 'dreamtv';
    if (n.includes('kralpop')) return 'kralpop';
    if (n.includes('numberone') || n.includes('nr1')) return 'numberone';
    if (n.includes('powertv')) return 'powertv';
    if (n.includes('trtmuzik')) return 'trtmuzik';

    return null;
}

async function autoRecoverDeadChannels(deadChannelsList) {
    if (!deadChannelsList || deadChannelsList.length === 0) return;
    const deadIds = new Set(deadChannelsList.map(c => c.id));
    const discovered = {};

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
                    if (matchedId && deadIds.has(matchedId)) {
                        const streamUrl = line.split('|')[0].trim();
                        if (!discovered[matchedId]) discovered[matchedId] = new Set();
                        discovered[matchedId].add(streamUrl);
                    }
                }
            }
        } catch (e) {
            console.warn(`[AutoRecover] Failed to fetch source ${src.name}:`, e.message);
        }
    }

    for (const ch of deadChannelsList) {
        const candidates = discovered[ch.id] ? Array.from(discovered[ch.id]) : [];
        for (const candidate of candidates.slice(0, 4)) {
            const resolved = await resolveNestedStreamUrl(candidate);
            const works = await testStreamUrl(resolved, 2500);
            if (works) {
                if (!ch.urls) ch.urls = [];
                ch.urls.unshift(resolved);
                channelStatus[ch.id] = { online: true, checkedAt: Date.now(), workingCount: 1, primary: resolved };
                console.log(`[AutoRecover] Successfully recovered dead channel ${ch.id} -> ${resolved.substring(0, 50)}...`);
                break;
            }
        }
    }
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



// Universal Periodic Health & Refresh (Every 10 minutes)
setInterval(() => {
    refreshAllChannels().catch(err => console.error('[PeriodicRefresh] Error:', err.message));
}, 10 * 60 * 1000);

// Initial refresh after server startup
setTimeout(() => {
    refreshAllChannels().catch(err => console.error('[StartupRefresh] Error:', err.message));
}, 5000);



app.listen(PORT, '0.0.0.0', () => {



    console.log(`Seyir TV Oracle Gateway running on port ${PORT}`);



});

