(function () {
  /* Provider adapters. Selectors are recorded in SITE_SUPPORT.md. */
  const clean = value => (value || '').replace(/\s+/g, ' ').trim();
  const absolute = value => { try { const u = new URL(value, location.href); return u.protocol === 'https:' ? u.href : ''; } catch (_) { return ''; } };
  const host = location.hostname.replace(/^www\./, '');
  const episodes = [], frames = [], actions = [], seenEpisodes = new Set(), seenFrames = new Set();
  const addFrame = value => { const url = absolute(value); if (url && !/youtube|doubleclick|googlesyndication|googlead|adservice|adsystem|popads|adsterra|propeller|banner|traffic|1xbet|betwinner/i.test(url) && !seenFrames.has(url)) { seenFrames.add(url); frames.push(url); } };
  const addEpisode = (value, label, forcedSeason) => {
    const url = absolute(value); if (!url || seenEpisodes.has(url)) return;
    const match = url.match(/(?:-|\/)(\d+)-sezon-(\d+)-bolum/i) || clean(label).match(/(\d+)\.?\s*sezon\s*(\d+)\.?\s*bölüm/i);
    if (!match && !/\/bolum\//i.test(url)) return;
    seenEpisodes.add(url);
    const season = forcedSeason || (match ? Number(match[1]) : 0), number = match ? Number(match[2]) : 0;
    episodes.push({ title: season && number ? season + '. Sezon · ' + number + '. Bölüm' : clean(label), url, image: '', info: 'Bölüm', season });
  };
  const addAction = (element, label) => { if (!element || actions.length >= 20) return; const id = String(actions.length); element.setAttribute('data-seyir-action', id); actions.push({ id, label: clean(label) }); };
  const structuredEpisodes = () => document.querySelectorAll('script[type="application/ld+json"]').forEach(script => {
    const visit = (value, season, depth) => { if (!value || typeof value !== 'object' || depth > 20) return; if (value['@type'] === 'TVSeason') season = Number(value.seasonNumber) || season; if (value['@type'] === 'TVEpisode') addEpisode(value.url, value.name, season); Object.values(value).forEach(child => { if (child && typeof child === 'object') visit(child, season, depth + 1); }); };
    try { visit(JSON.parse(script.textContent), 0, 0); } catch (_) { }
  });
  const fetchSeasonPages = (selector, parse) => {
    if (window.__seyirSeasons) { window.__seyirSeasons.rows.forEach(row => addEpisode(row.url, row.label, row.season)); return window.__seyirSeasons.pending > 0; }
    const links = [...document.querySelectorAll(selector)].map(anchor => ({ url: absolute(anchor.getAttribute('href')) })).filter(row => row.url && new URL(row.url).origin === location.origin);
    const unique = [...new Map(links.map(row => [row.url, row])).values()], state = window.__seyirSeasons = { rows: [], pending: unique.length };
    unique.forEach(row => { const controller = new AbortController(), timer = setTimeout(() => controller.abort(), 12000); fetch(row.url, { credentials: 'same-origin', signal: controller.signal }).then(response => response.ok ? response.text() : Promise.reject()).then(html => parse(new DOMParser().parseFromString(html, 'text/html'), row.url, state.rows)).catch(() => { }).finally(() => { clearTimeout(timer); state.pending--; }); });
    return state.pending > 0;
  };

  let seasonLoading = false;
  if (host.includes('fullhdfilmizlesene')) {
    // Provider selection is .part-item, which then exposes #plx iframe[data-src].
    document.querySelectorAll('.part-item[data-name]').forEach(item => addAction(item, item.getAttribute('data-name') || item.textContent));
    document.querySelectorAll('#plx iframe').forEach(frame => addFrame(frame.getAttribute('data-src') || frame.getAttribute('src')));
  } else if (host.includes('dizilla')) {
    structuredEpisodes();
    let secureData = '';
    const nextScript = document.getElementById('__NEXT_DATA__');
    if (nextScript) {
      try {
        const j = JSON.parse(nextScript.textContent);
        secureData = j?.props?.pageProps?.secureData || '';
      } catch (_) {}
    }
    document.querySelectorAll('div[class*="z-[9999]"], .adArea, [class*="feather-play"]').forEach(e => {
      try { e.remove(); } catch (_) {}
    });
    if (/\/dizi\//.test(location.pathname) && !secureData) seasonLoading = fetchSeasonPages('a[href$="-sezon"],a[href$="-sezon/"]', (doc, base, rows) => {
      doc.querySelectorAll('a.text.block[href*="-sezon-"][href*="-bolum"],a[href*="-sezon-"][href*="-bolum"]').forEach(anchor => { const url = new URL(anchor.getAttribute('href'), base).href, match = url.match(/(\d+)-sezon-(\d+)-bolum/i); if (match) rows.push({ url, label: clean(anchor.textContent), season: Number(match[1]) }); });
    });
    const playBtn = document.querySelector('.player button, .feather-play, #play-video, button[aria-label*="Play" i]');
    if (playBtn) {
      const btn = playBtn.closest ? (playBtn.closest('button') || playBtn) : playBtn;
      if (btn && btn.setAttribute && !btn.getAttribute('data-seyir-clicked')) {
        btn.setAttribute('data-seyir-clicked', '1');
        try { if (typeof btn.click === 'function') btn.click(); } catch (_) {}
      }
      addAction(btn, '▶ Oynatıcıyı Başlat');
    }
    document.querySelectorAll('button,[role="button"]').forEach(b => {
      const txt = clean(b.textContent);
      if (/^(türkçe|dublaj|altyazı|kaynak|alternatif|player|rapid|vid|part)/i.test(txt) && txt.length < 30) addAction(b, txt);
    });
    document.querySelectorAll('iframe').forEach(frame => addFrame(frame.getAttribute('src') || frame.getAttribute('data-src')));
  } else if (host.includes('dizibox')) {
    // DiziBOX has one independent URL per season and episode anchors use .season-episode or slug pattern.
    seasonLoading = fetchSeasonPages('a.btn[href*="/dizi/"][href*="sezon"],a[href*="-sezon/"]', (doc, base, rows) => {
      doc.querySelectorAll('a.season-episode[href],a[href*="-sezon-"][href*="-bolum"]').forEach(anchor => { const label = clean(anchor.textContent), match = label.match(/(\d+)\.?\s*sezon\s*(\d+)\.?\s*bölüm/i) || anchor.getAttribute('href').match(/(\d+)-sezon-(\d+)-bolum/i); rows.push({ url: new URL(anchor.getAttribute('href'), base).href, label, season: match ? Number(match[1]) : 0 }); });
    });
    document.querySelectorAll('a.season-episode[href],a[href*="-sezon-"][href*="-bolum"]').forEach(anchor => addEpisode(anchor.getAttribute('href'), anchor.textContent, 0));
    document.querySelectorAll('iframe').forEach(frame => addFrame(frame.getAttribute('src') || frame.getAttribute('data-src')));
    document.querySelectorAll('.sources a, .video-options a, [class*="source"] a, .parts a').forEach(a => {
      const txt = clean(a.textContent);
      if (txt.length > 1 && txt.length < 30) addAction(a, txt);
    });
  } else if (host.includes('hdfilmcehennemi')) {
    document.querySelectorAll('iframe').forEach(frame => addFrame(frame.getAttribute('src') || frame.getAttribute('data-src')));
    document.querySelectorAll('button,[role="button"],a').forEach(element => { const label = clean(element.textContent); if (/^(rapid|vid|sibnet|ok\.?ru|dublaj|altyazı|türkçe|izle|oynat)/i.test(label)) addAction(element, label); });
    document.querySelectorAll('a[href*="sezon"][href*="bolum"],a[href*="/bolum/"]').forEach(anchor => addEpisode(anchor.getAttribute('href'), anchor.textContent, 0));
  }
  const description = document.querySelector('meta[name="description"]');
  return JSON.stringify({ title: clean(document.querySelector('h1')?.textContent) || document.title, description: description ? description.content : '', episodes: episodes.slice(0, 800), frames: frames.slice(0, 12), actions, seasonLoading, secureData: typeof secureData !== 'undefined' ? secureData : '' });
})()
