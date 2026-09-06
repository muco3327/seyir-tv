(function () {
  const clean = s => (s || '').replace(/\s+/g, ' ').trim();
  const text = e => clean(e && e.textContent);
  const absolute = u => { try { const x = new URL(u, location.href); return x.protocol === 'https:' ? x.href : ''; } catch (_) { return ''; } };
  const same = u => { try { return new URL(u).hostname.replace(/^www\./, '') === location.hostname.replace(/^www\./, ''); } catch (_) { return false; } };
  const image = root => { const i = root && root.querySelector('img'); return i ? absolute(i.getAttribute('data-src') || i.currentSrc || i.src) : ''; };
  const items = [], seen = new Set();
  const query = '__QUERY__';
  const norm = s => s.toLocaleLowerCase('tr-TR').normalize('NFD').replace(/[\u0300-\u036f]/g,'').replace(/ı/g,'i');
  function add(a, root, title, info) {
    const url = absolute(a.getAttribute('href'));
    title = clean(title).replace(/\s+izle$/i, '');
    if (!same(url) || seen.has(url) || !title || title.length > 200) return;
    if (query && !norm(title).includes(norm(query))) return;
    seen.add(url); items.push({ title, url, image: image(root), info: clean(info).slice(0,100) });
  }
  if (location.hostname.includes('fullhdfilmizlesene')) {
    document.querySelectorAll('a.tt').forEach(a => {
      const root=a.closest('.film') || a.parentElement;
      add(a,root,text(root.querySelector('.film-title')) || text(a),text(root.querySelector('.film-yil')));
    });
  } else if(location.hostname.includes('hdfilmcehennemi')) {
    // Film cards use .poster; series cards use .mini-poster and have /dizi/ URLs.
    document.querySelectorAll('a.poster,a.mini-poster').forEach(a=>{
      const isSeries=/\/dizi\//.test(a.href);
      add(a,a,a.title || text(a.querySelector('.poster-title')) || text(a),isSeries?'Dizi':text(a.querySelector('.poster-meta')) || 'Film');
    });
  } else if(location.hostname.includes('dizilla')) {
    document.querySelectorAll('a[href]').forEach(a => {
      const path=absolute(a.getAttribute('href'));
      if(!/\/dizi\/|\d+-sezon-\d+-bolum/.test(path)) return;
      const i=a.querySelector('img');
      add(a,a,a.title || (i && i.alt) || text(a),/\d+-sezon-\d+-bolum/.test(path)?'Yeni bölüm':'Dizi');
    });
  } else {
    document.querySelectorAll('a[href*="/diziler/"]').forEach(a=>{
      const i=a.querySelector('img'); const root=a.closest('article,.post,.item,li')||a;
      add(a,root,a.title||(i&&i.alt)||text(a),text(root.querySelector('.category,.genre,.year'))||'Dizi');
    });
  }
  // Search dropdowns often use different markup from catalogue cards.
  if(query) document.querySelectorAll('a[href]').forEach(a=> {
    const i=a.querySelector('img'); const title=a.title || (i && i.alt) || text(a);
    if(title && norm(title).includes(norm(query)) && !/kategori|category|\/tur\/|\/yil\//.test(a.href)) add(a,a,title,'Arama sonucu');
  });
  return JSON.stringify({items:items.slice(0,150), title:document.title,
    blocked:/just a moment|access denied|attention required|bir dakika/i.test(document.title), url:location.href});
})()
