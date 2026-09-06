(function(){
 window.postMessage({seyir:'start',visible:!!window.__seyirVisible},'*');
 const urls=new Set();const add=u=>{try{const x=new URL(u,location.href);if(x.protocol==='https:'&&/\.(m3u8|mp4)(\?|$)/i.test(x.href))urls.add(x.href)}catch(_){}};
 document.querySelectorAll('video,source').forEach(v=>{add(v.src);add(v.currentSrc);if(v.tagName==='VIDEO'){v.muted=!window.__seyirVisible;v.volume=window.__seyirVisible?1:0;v.play().catch(()=>{});}});
 performance.getEntriesByType('resource').forEach(r=>add(r.name));
 if(!window.__seyirClicked){const b=document.querySelector('#play-video,.video-play-button,.vjs-big-play-button,.jw-icon-display,.plyr__control--overlaid,.play-button,#play-button,#playButton,.fp-play,.p2p-play,button[aria-label="Play"],button[title="Play"]');if(b){window.__seyirClicked=true;b.click();}}
 return JSON.stringify({urls:Array.from(urls)});
})()
