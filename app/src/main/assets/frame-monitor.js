(function(){
  try {
    if (window.top === window.self) {
      Object.defineProperty(window, 'top', {
        get: function() { return window.parent && window.parent !== window ? window.parent : {}; },
        configurable: true
      });
    }
  } catch(_) {}
  if(window.__seyirMonitor)return;window.__seyirMonitor=true;
  let active=false,visible=false,until=0;const sent=new Set();
  function report(data){try{if(window.SeyirMedia)SeyirMedia.postMessage(JSON.stringify(data));}catch(_){}}
  function send(u){try{const x=new URL(u,location.href);if(x.protocol!=='https:'||! /\.(m3u8|mp4)(\?|$)/i.test(x.href)||sent.has(x.href))return;sent.add(x.href);report({url:x.href,page:location.href});}catch(_){}}
  function tick(){
    if(!active||Date.now()>until)return;
    document.querySelectorAll('#ad, .ad-box, [id*="ad-container"]').forEach(e => { try { e.remove(); } catch(_) {} });
    document.querySelectorAll('.skip-ad, .videoAdUiSkipButton, button.skip, [class*="skip-ad"], .vjs-skip-ad, .jw-skip, #ad-skip').forEach(b => { try { b.click(); } catch(_) {} });
    document.querySelectorAll('iframe').forEach(f=>{try{f.contentWindow.postMessage({seyir:'start',visible},'*')}catch(_){}});
    const video=document.querySelector('video');
    const ad=!!document.querySelector('.vjs-ad-playing,.vjs-ad-loading,.jw-flag-ads,.ima-ad-container:not([style*="display: none"])');
    if(video){video.muted=!visible;video.volume=visible?1:0;if(video.paused&&!ad)video.play().catch(()=>{});
      if(!ad&&(video.duration>180||video.duration===Infinity)){
        send(video.currentSrc);document.querySelectorAll('source').forEach(s=>send(s.src));
        performance.getEntriesByType('resource').forEach(r=>{if(!/doubleclick|googlesyndication|[\/_-]ads?[\/_-]/i.test(r.name))send(r.name)});
      }
    }
    document.querySelectorAll('script').forEach(s => {
      const m = (s.textContent || '').match(/https?:\/\/[^\s"'<>]+\.(?:m3u8|mp4)[^\s"'<>]*/g);
      if (m) m.forEach(send);
    });
    if(!video&&!ad){const b=document.querySelector('#play-video,.vjs-big-play-button,.jw-icon-display,.plyr__control--overlaid,.video-play-button');if(b&&!b.dataset.seyirStarted){b.dataset.seyirStarted='1';b.click();}}
  }
  window.addEventListener('message',e=>{if(e.source!==parent&&e.source!==window)return;if(e.data?.seyir==='start'){active=true;visible=!!e.data.visible;until=Date.now()+90000;tick();}if(e.data?.seyir==='stop'){active=false;document.querySelectorAll('iframe').forEach(f=>{try{f.contentWindow.postMessage({seyir:'stop'},'*')}catch(_){}});document.querySelectorAll('video,audio').forEach(v=>v.pause());}});
  setInterval(tick,1200);
})()
