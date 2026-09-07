(function(){
 try{if(window.top===window.self){Object.defineProperty(window,'top',{get:()=>window.parent&&window.parent!==window?window.parent:{},configurable:true});}}catch(_){}
 window.postMessage({seyir:'start',visible:!!window.__seyirVisible},'*');
 document.querySelectorAll('iframe').forEach(f=>{try{f.contentWindow.postMessage({seyir:'start',visible:!!window.__seyirVisible},'*')}catch(_){}});
 const urls=new Set();const add=u=>{try{const x=new URL(u,location.href);if(x.protocol==='https:'&&/\.(m3u8|mp4)(\?|$)/i.test(x.href))urls.add(x.href)}catch(_){}};
 document.querySelectorAll('video,source').forEach(v=>{add(v.src);add(v.currentSrc);if(v.tagName==='VIDEO'){v.muted=!window.__seyirVisible;v.volume=window.__seyirVisible?1:0;v.play().catch(()=>{});}});
 performance.getEntriesByType('resource').forEach(r=>add(r.name));
 document.querySelectorAll('script').forEach(s=>{const m=(s.textContent||'').match(/https?:\/\/[^\s"'<>]+\.(?:m3u8|mp4)[^\s"'<>]*/g);if(m)m.forEach(add);});
 document.querySelectorAll('.skip-ad,.videoAdUiSkipButton,button.skip,[class*="skip-ad"],.vjs-skip-ad,.jw-skip,#ad-skip').forEach(b=>{try{b.click();}catch(_){}});
 if(!window.__seyirClicked){const b=document.querySelector('#play-video,.video-play-button,.vjs-big-play-button,.jw-icon-display,.plyr__control--overlaid,.play-button,#play-button,#playButton,.fp-play,.p2p-play,button[aria-label="Play"],button[title="Play"],.player button,.feather-play');if(b){window.__seyirClicked=true;b.click();}}
 return JSON.stringify({urls:Array.from(urls)});
})()
