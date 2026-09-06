package tv.seyir.app;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.webkit.*;
import androidx.webkit.WebViewCompat;
import androidx.webkit.WebViewFeature;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Reads the rendered page. No JavascriptInterface is exposed to third-party content. */
public final class SiteEngine {
    public interface Listener {
        void catalog(List<TitleItem> items, String message);
        void detail(JSONObject data);
        void stream(String url, Map<String,String> headers);
        void status(String message);
    }
    public final WebView web;
    private final Listener listener;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final String catalogScript, detailScript, resolveScript;
    private int generation=0;
    private Source source=Source.FULLHD;
    private String query="",mode="catalog",requestPage="";
    private boolean searching=false,destroyed=false;
    private String pendingAction=null;
    private boolean accessBlocked=false;
    private String frameHost="";
    private boolean captureActive=false;
    private int catalogDelivered=-1;
    private final Set<String> streams=Collections.synchronizedSet(new HashSet<>());

    @SuppressLint({"SetJavaScriptEnabled", "RequiresFeature"})
    public SiteEngine(Activity activity,Listener listener) {
        this.listener=listener;
        catalogScript=asset(activity,"catalog.js"); detailScript=asset(activity,"detail.js");resolveScript=asset(activity,"resolve.js");
        web=new WebView(activity);
        WebSettings s=web.getSettings();
        s.setJavaScriptEnabled(true); s.setDomStorageEnabled(true);
        s.setAllowFileAccess(false); s.setAllowContentAccess(false);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setSupportMultipleWindows(true); s.setJavaScriptCanOpenWindowsAutomatically(false);
        s.setUseWideViewPort(true); s.setLoadWithOverviewMode(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(web,true);
        if(WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)&&WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)){
            Set<String> origins=new HashSet<>();for(Source s0:Source.values()){String host=Uri.parse(s0.home).getHost().replaceFirst("^www\\.","");origins.add("https://"+host);origins.add("https://*."+host);}origins.add("https://rapidvid.net");origins.add("https://*.rapidvid.net");origins.add("https://pichive.online");origins.add("https://*.pichive.online");origins.add("https://vidmoly.to");origins.add("https://*.vidmoly.to");origins.add("https://vidmoly.net");origins.add("https://*.vidmoly.net");
            WebViewCompat.addWebMessageListener(web,"SeyirMedia",origins,(view,message,origin,main,reply)->{
                if(!captureActive||destroyed||message.getData()==null||message.getData().length()>16384)return;
                try{JSONObject data=new JSONObject(message.getData());String u=data.optString("url"),page=data.optString("page");if(!MediaPolicy.isVideo(u)||!MediaPolicy.isHttps(page)||!Objects.equals(Uri.parse(page).getHost(),origin.getHost()))return;if(streams.size()<40&&streams.add(u)){Map<String,String> h=new HashMap<>();h.put("Referer",page);h.put("User-Agent",web.getSettings().getUserAgentString());listener.stream(u,h);}}catch(Exception ignored){}
            });
            WebViewCompat.addDocumentStartJavaScript(web,asset(activity,"frame-monitor.js"),origins);
        }
        web.setWebChromeClient(new WebChromeClient()); // Popup windows and permission requests are not granted.
        web.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView v,WebResourceRequest r) {
                return !MediaPolicy.isHttps(r.getUrl().toString());
            }
            @Override public void onPageStarted(WebView v,String url,Bitmap favicon) {
                if(!destroyed&&!url.equals("about:blank")&&streams.isEmpty()) listener.status(mode.equals("catalog")?"İçerikler yükleniyor…":"Kaynak hazırlanıyor…");
            }
            @Override public void onPageFinished(WebView v,String url) {
                if(destroyed) return;
                int g=generation;
                web.evaluateJavascript("/sorry, you have been blocked|you are unable to access/i.test(document.body?.innerText||'')",result->{if(g==generation&&"true".equals(result))blocked();});
                if(mode.equals("catalog")) {
                    if(!query.isEmpty()&&!searching) { searching=true; enterSearch(g); }
                    else scheduleRead(g,0);
                } else if(mode.equals("detail")){if(pendingAction!=null){String id=pendingAction;pendingAction=null;web.evaluateJavascript(detailScript,ignored->action(id));}else readDetail(g);}
                else if(mode.equals("frame")) startFrame(g,0);
            }
            @Override public void onReceivedError(WebView v,WebResourceRequest r,WebResourceError e) {
                if(r.isForMainFrame()) listener.status("Kaynağa bağlanılamadı. Yenile veya başka kaynak seç.");
            }
            @Override public void onReceivedHttpError(WebView v,WebResourceRequest r,WebResourceResponse e) {
                if((e.getStatusCode()==403||e.getStatusCode()==451)&&(r.isForMainFrame()||frameHost.equals(r.getUrl().getHost()))){blocked();return;}
                if(r.isForMainFrame()&&e.getStatusCode()>=400)
                    listener.status("Kaynak yanıtı: "+e.getStatusCode()+". Site görünümünü açarak kontrol edebilirsin.");
            }
            @Override public WebResourceResponse shouldInterceptRequest(WebView v,WebResourceRequest r) {
                String url=r.getUrl().toString();
                if(captureActive&&"GET".equals(r.getMethod())&&MediaPolicy.isVideo(url)) {
                    Map<String,String> headers=new HashMap<>();
                    for(Map.Entry<String,String> h:r.getRequestHeaders().entrySet())
                        if(h.getKey().equalsIgnoreCase("Referer")||h.getKey().equalsIgnoreCase("Origin")||h.getKey().equalsIgnoreCase("User-Agent")) headers.put(h.getKey(),h.getValue());
                    int g=generation;
                    handler.post(()->{if(!destroyed&&captureActive&&g==generation&&streams.size()<40&&streams.add(url))listener.stream(url,headers);});
                }
                return null;
            }
        });
    }
    private static String asset(Activity a,String name) {
        try(InputStream in=a.getAssets().open(name);ByteArrayOutputStream out=new ByteArrayOutputStream()) {
            byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1)out.write(b,0,n);
            return out.toString(StandardCharsets.UTF_8.name());
        }catch(IOException e){throw new IllegalStateException(e);}
    }
    public void catalog(Source source,String query) {
        captureActive=false;pendingAction=null;web.onResume();
        accessBlocked=false;frameHost="";
        this.source=source;this.query=query.trim();mode="catalog";searching=false;
        generation++;handler.removeCallbacksAndMessages(null);streams.clear();
        requestPage=source.home;web.stopLoading();web.loadUrl(source.home);scheduleRead(generation,0);
    }
    public void detail(TitleItem item) {
        captureActive=false;pendingAction=null;web.onResume();
        accessBlocked=false;frameHost="";
        if(!item.source.owns(item.url))return;
        source=item.source;mode="detail";query="";generation++;
        handler.removeCallbacksAndMessages(null);streams.clear();requestPage=item.url;
        web.stopLoading();web.loadUrl(item.url);
        int g=generation;handler.postDelayed(()->readDetail(g),2500);
    }
    public void frame(String url) {
        if(!MediaPolicy.isHttps(url))return;
        captureActive=true;web.onResume();
        accessBlocked=false;frameHost=Uri.parse(url).getHost();
        String ref=web.getUrl(); mode="frame";generation++;
        handler.removeCallbacksAndMessages(null);streams.clear();
        if(source==Source.DIZILLA||source==Source.FULLHD||source==Source.DIZIBOX){
            // Keep the embed in its original parent page, with its existing cookies and navigation context.
            if(!requestPage.equals(web.getUrl()))web.loadUrl(requestPage);
            else startFrame(generation,0);
            int g=generation;handler.postDelayed(()->{if(!destroyed&&g==generation&&streams.isEmpty()&&!accessBlocked)listener.status("Yayın alınamadı. Oynatıcı reklam veya kullanıcı tıklaması bekliyor olabilir. Site oynatıcısını aç.");},90000);
            return;
        }
        Map<String,String> headers=new HashMap<>();if(ref!=null)headers.put("Referer",ref);
        web.loadUrl(url,headers);
        int g=generation;
        handler.postDelayed(()->{if(!destroyed&&g==generation&&streams.isEmpty()&&!accessBlocked)listener.status("Dahili yayın bağlantısı henüz alınamadı. Site oynatıcısını gösterip oynat düğmesine bas.");},20000);
    }
    private void blocked(){accessBlocked=true;listener.status("Yayın sunucusu erişimi engelledi. Bu, videonun olmadığı anlamına gelmez. Bölüm sayfasını tarayıcıda açabilirsin.");}
    public void showOriginalPage(){
        if((source==Source.DIZILLA||source==Source.FULLHD||source==Source.DIZIBOX)&&!requestPage.equals(web.getUrl())){generation++;handler.removeCallbacksAndMessages(null);accessBlocked=false;mode="frame";web.loadUrl(requestPage);}
    }
    public void category(String url){
        if(!source.owns(url))return;
        query="";mode="catalog";searching=false;generation++;
        handler.removeCallbacksAndMessages(null);streams.clear();web.loadUrl(url);scheduleRead(generation,0);
    }
    public void categories(java.util.function.Consumer<JSONObject> callback){
        web.evaluateJavascript("(function(){const items=[],seen=new Set();document.querySelectorAll('a[href]').forEach(a=>{const label=(a.textContent||'').trim();const u=new URL(a.href,location.href);if(u.origin===location.origin&&label.length>1&&label.length<40&&/kategori|category|genre|\\/tur\\/|\\/dizi-turu\\//i.test(u.pathname)&&!seen.has(u.href)){seen.add(u.href);items.push({label,url:u.href});}});return JSON.stringify({items})})()",value->{try{callback.accept(decode(value));}catch(Exception ignored){callback.accept(new JSONObject());}});
    }
    private void startFrame(int g,int attempt) {
        handler.postDelayed(()->{
            if(destroyed||g!=generation||accessBlocked||!mode.equals("frame")||attempt>74)return;
            web.evaluateJavascript(resolveScript,value->{if(destroyed||g!=generation)return;try{JSONArray urls=decode(value).optJSONArray("urls");if(urls!=null)for(int n=0;n<urls.length();n++){String u=urls.optString(n);if(MediaPolicy.isVideo(u)&&streams.add(u)){Map<String,String> h=new HashMap<>();h.put("Referer",web.getUrl());h.put("User-Agent",web.getSettings().getUserAgentString());listener.stream(u,h);}}}catch(Exception ignored){}});
            if(streams.isEmpty())startFrame(g,attempt+1);
        },1200);
    }
    private void enterSearch(int g) {
        String js="(function(){const e=document.querySelector('input[type=search],input.aratxt,input[placeholder*=Arama],input[placeholder*=Film],input[aria-label=\"Search input\"],input[type=text]');if(!e)return false;const set=Object.getOwnPropertyDescriptor(HTMLInputElement.prototype,'value').set;set.call(e,"+JSONObject.quote(query)+");e.dispatchEvent(new Event('input',{bubbles:true}));e.dispatchEvent(new Event('change',{bubbles:true}));e.dispatchEvent(new KeyboardEvent('keyup',{key:'a',bubbles:true}));const b=Array.from(document.querySelectorAll('button')).find(x=>x.textContent.trim()==='Ara');if(b)b.click();return true})()";
        web.evaluateJavascript(js,result->{if(g==generation){if(!"true".equals(result))listener.status("Bu kaynağın arama alanı bulunamadı. Site görünümünü kullanabilirsin.");scheduleRead(g,0);}});
    }
    private void scheduleRead(int g,int attempt) {
        handler.postDelayed(()->{
            if(destroyed||g!=generation||!mode.equals("catalog"))return;
            if(!query.isEmpty()&&!searching){if(attempt<12)scheduleRead(g,attempt+1);return;}
            if(!source.owns(web.getUrl()==null?"":web.getUrl()))return;
            web.evaluateJavascript(catalogScript.replace("'__QUERY__'",JSONObject.quote(query)),value->{
                if(g!=generation||destroyed)return;
                try {
                    JSONObject data=decode(value);JSONArray arr=data.optJSONArray("items");List<TitleItem> list=new ArrayList<>();
                    if(arr!=null)for(int i=0;i<arr.length();i++) {
                        TitleItem item=TitleItem.read(arr.getJSONObject(i),source);
                        if(source.owns(item.url))list.add(item);
                    }
                    if(!list.isEmpty()){if(catalogDelivered!=g){catalogDelivered=g;listener.catalog(list,query.isEmpty()?"Kaynağın güncel kataloğu":list.size()+" sonuç");}}
                    else if(attempt<12)scheduleRead(g,attempt+1);
                    else listener.catalog(list,data.optBoolean("blocked")?"Site doğrulama istiyor. Site görünümünü aç.":query.isEmpty()?"İçerik alınamadı. Yenile veya site görünümünü aç.":"Arama sonucu alınamadı. Farklı ad veya kaynak dene.");
                }catch(Exception e){if(attempt<12)scheduleRead(g,attempt+1);else listener.catalog(new ArrayList<>(),"Sayfa okunamadı. Site görünümünü aç.");}
            });
        },attempt==0?1600:1200);
    }
    private static JSONObject decode(String value)throws JSONException {
        Object decoded=new JSONTokener(value).nextValue();
        return decoded instanceof String?new JSONObject((String)decoded):new JSONObject();
    }
    public void readDetail(int g) {
        if(destroyed||g!=generation||!mode.equals("detail"))return;
        web.evaluateJavascript(detailScript,value->{if(!destroyed&&g==generation)try{JSONObject data=decode(value);listener.detail(data);if(data.optBoolean("seasonLoading"))handler.postDelayed(()->readDetail(g),1200);}catch(Exception ignored){listener.status("Detay okunamadı. Site görünümünü aç.");}});
    }
    public void action(String id) {
        if(!id.matches("[0-9]+"))return;
        captureActive=true;web.onResume();
        if(!mode.equals("detail")){mode="detail";generation++;handler.removeCallbacksAndMessages(null);streams.clear();pendingAction=id;web.loadUrl(requestPage);return;}
        streams.clear();
        String followUp=source==Source.FULLHD?";setTimeout(()=>document.querySelector('#play-video,.video-play-button')?.click(),350)":"";
        web.evaluateJavascript("document.querySelector('[data-seyir-action=\""+id+"\"]')?.click()"+followUp,null);
        int g=generation;handler.postDelayed(()->readDetail(g),1200);handler.postDelayed(()->readDetail(g),3000);handler.postDelayed(()->readDetail(g),6000);handler.postDelayed(()->readDetail(g),10000);
    }
    public void nextPage() {
        int g=generation;
        web.evaluateJavascript("(function(){const e=Array.from(document.querySelectorAll('a,button')).find(x=>/^(İleri|Sonraki|Sonraki Sayfa|Next)$/i.test(x.textContent.trim()));if(e){e.click();return true}return false})()",result->{
            if("true".equals(result))scheduleRead(g,0);else listener.status("Sonraki sayfa bulunamadı.");
        });
    }
    public void fullscreen() {
        web.evaluateJavascript("(function(){const b=document.querySelector('.vjs-fullscreen-control,.jw-icon-fullscreen,.plyr__control[data-plyr=fullscreen],button[title*=\"ullscreen\" i],button[aria-label*=\"ullscreen\" i]');if(b){b.click();return true}const v=document.querySelector('video');if(v&&v.requestFullscreen){v.requestFullscreen();return true}return false})()",result->{if(!"true".equals(result))listener.status("Bu site tam ekran düğmesini uygulamaya açmıyor. Dahili oynatıcıyı kullan.");});
    }
    public void stopPlayback(){
        captureActive=false;
        mode="idle";
        generation++;handler.removeCallbacksAndMessages(null);streams.clear();
        web.evaluateJavascript("document.querySelectorAll('video,audio').forEach(v=>{v.pause();v.muted=true;v.src=''})",null);
        web.stopLoading();web.loadUrl("about:blank");web.onPause();
    }
    public void pause(){web.onPause();web.evaluateJavascript("document.querySelectorAll('video').forEach(v=>v.pause())",null);}
    public void resume(){web.onResume();}
    public void destroy(){destroyed=true;generation++;handler.removeCallbacksAndMessages(null);web.stopLoading();web.destroy();}
}
