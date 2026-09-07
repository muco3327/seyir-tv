package tv.seyir.app;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.os.*;
import android.view.*;
import android.view.inputmethod.InputMethodManager;
import android.widget.*;
import android.text.TextUtils;
import org.json.*;
import java.net.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;

@androidx.media3.common.util.UnstableApi
public final class MainActivity extends Activity implements SiteEngine.Listener {
    private FrameLayout root;
    private LinearLayout shell,body,detailPanel,browserPanel,streamPanel;
    private TextView status,heading,description;
    private ScrollView scroll;
    private SiteEngine engine;
    private Library library;
    private Source source=Source.FULLHD;
    private TitleItem selected;
    private TitleItem series;
    private final List<TitleItem> seriesEpisodes=new ArrayList<>();
    private final Set<Integer> expandedSeasons=new HashSet<>();
    private String query="",section="catalog";
    private boolean browserVisible=false;
    private boolean playWhenFound=false;
    private Runnable pendingAutoPlay;
    private boolean resolvingSource=false;
    private LinearLayout activeSourcePanel;
    private View defaultFocus;
    private View firstContentFocus;
    private int sourceSelection=0;
    private final List<TitleItem> catalog=new ArrayList<>();
    private final List<TitleItem> returnCatalog=new ArrayList<>();
    private String returnHeading="",returnStatus="";
    private int returnScrollY=0;
    private final LinkedHashMap<String,Map<String,String>> streams=new LinkedHashMap<>();
    private final ExecutorService images=Executors.newFixedThreadPool(3);
    private final android.util.LruCache<String,Bitmap> cache=new android.util.LruCache<String,Bitmap>(12*1024*1024){@Override protected int sizeOf(String key,Bitmap b){return b.getByteCount();}};
    private android.webkit.WebChromeClient.CustomViewCallback fullCallback;
    private View fullscreen;
    private int imageGeneration=0;

    @Override public void onCreate(Bundle state){
        super.onCreate(state);getWindow().getDecorView().setSystemUiVisibility(5894);
        library=new Library(this);root=new FrameLayout(this);root.setBackgroundColor(Ui.BG);setContentView(root);
        try{engine=new SiteEngine(this,this);}catch(Exception e){new AlertDialog.Builder(this).setTitle("Android System WebView gerekli").setMessage("Cihazdaki Android System WebView uygulamasını güncelleyip yeniden aç.").setPositiveButton("Kapat",(d,w)->finish()).show();return;}
        root.addView(engine.web,new FrameLayout.LayoutParams(-1,-1));
        engine.web.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        engine.web.setFocusable(false);
        engine.web.setFocusableInTouchMode(false);
        engine.web.setWebChromeClient(new android.webkit.WebChromeClient(){
            @Override public void onShowCustomView(View v,CustomViewCallback cb){
                if(fullscreen!=null){cb.onCustomViewHidden();return;}fullscreen=v;fullCallback=cb;
                root.addView(v,new FrameLayout.LayoutParams(-1,-1));v.requestFocus();getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            }
            @Override public void onHideCustomView(){hideFullscreen();}
        });
        createShell();
        if(state!=null)try{source=Source.valueOf(state.getString("source",Source.FULLHD.name()));}catch(Exception ignored){}
        openSource(source);
        AppUpdater.check(this, false);
    }
    private void createShell(){
        shell=Ui.column(this);shell.setBackgroundColor(Ui.BG);int pad=Ui.dp(this,24);shell.setPadding(pad,Ui.dp(this,16),pad,Ui.dp(this,10));
        scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setBackgroundColor(Ui.BG);scroll.addView(shell);root.addView(scroll,new FrameLayout.LayoutParams(-1,-1));
        LinearLayout top=Ui.row(this);
        TextView brand=Ui.text(this,"▶ SEYİR TV",20,Ui.MINT);Ui.bold(brand);shell.addView(brand);Ui.gap(shell,8);
        Button searchButton=Ui.button(this,"Ara",this::search);top.addView(searchButton);defaultFocus=searchButton;space(top);
        top.addView(Ui.button(this,"Favoriler",()->showLibrary("favorites")));space(top);
        top.addView(Ui.button(this,"Devam et",()->showLibrary("history")));space(top);
        top.addView(Ui.button(this,"Bilgi",()->new AlertDialog.Builder(this).setTitle("Seyir TV · 0.3.0")
            .setMessage("Film, dizi ve canlı spor yayınları tek ekranda.\n\nYön tuşları: gezin\nOK: seç\nGeri: önceki ekran\n\nFavoriler ve izleme ilerlemesi bu cihazda saklanır. Kaynaklar kendi sitelerinden yüklenir.\n\nHarici hesap veya eklenti kurulumu gerekmez.")
            .setPositiveButton("Tamam",null).show()));space(top);
        top.addView(Ui.button(this,"Güncelle",()->AppUpdater.check(this,true)));space(top);
        top.addView(Ui.button(this,"Yenile",()->{if(selected!=null)openDetail(selected);else openSource(source);}));space(top);
        top.addView(Ui.button(this,"Site görünümü",this::showBrowser));
        HorizontalScrollView toolsBar=new HorizontalScrollView(this);toolsBar.setHorizontalScrollBarEnabled(false);toolsBar.addView(top);shell.addView(toolsBar);Ui.gap(shell,14);
        LinearLayout tabs=Ui.row(this);
        for(Source s:Source.values()){Button b=Ui.button(this,s.title,()->openSource(s));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,Ui.dp(this,46),1);lp.setMargins(0,0,Ui.dp(this,8),0);tabs.addView(b,lp);}
        shell.addView(tabs);Ui.gap(shell,14);
        shell.post(()->{if(!browserVisible&&getCurrentFocus()==null)defaultFocus.requestFocus();});
        LinearLayout label=Ui.row(this);heading=Ui.text(this,"Keşfet",22,Ui.WHITE);Ui.bold(heading);label.addView(heading,new LinearLayout.LayoutParams(0,-2,1));
        label.addView(Ui.button(this,"Kategoriler",this::showCategories));shell.addView(label);Ui.gap(shell,6);
        status=Ui.text(this,"Kaynak yükleniyor…",13,Ui.MUTED);shell.addView(status);Ui.gap(shell,12);
        body=Ui.column(this);shell.addView(body,new LinearLayout.LayoutParams(-1,-2));
        TextView footer=Ui.text(this,"OK  Seç     ·     Yön tuşları  Gezin     ·     Geri  Önceki ekran",12,Ui.MUTED);footer.setPadding(0,Ui.dp(this,8),0,0);shell.addView(footer);
    }
    private void space(LinearLayout l){View v=new View(this);l.addView(v,new LinearLayout.LayoutParams(Ui.dp(this,8),1));}
    private void openSource(Source s){
        series=null;seriesEpisodes.clear();expandedSeasons.clear();
        returnCatalog.clear();returnHeading="";returnScrollY=0;
        resolvingSource=false;activeSourcePanel=null;
        hideBrowser();playWhenFound=false;source=s;selected=null;section="catalog";query="";streams.clear();catalog.clear();body.removeAllViews();imageGeneration++;
        heading.setText(s.title);
        if(s==Source.SPORTS){
            status.setText("Canlı spor yayınları yükleniyor…");
            loading();
            SportsManager.loadChannels(this,channels->{
                if(source!=Source.SPORTS||selected!=null)return;
                List<TitleItem> items=new ArrayList<>();
                for(SportsManager.SportChannel ch:channels){
                    items.add(ch.toTitleItem());
                }
                catalog(items,items.size()+" kanal");
            });
            return;
        }
        status.setText("Güncel içerikler yükleniyor…");loading();engine.catalog(s,"");
    }
    private void loading(){TextView t=Ui.text(this,"İzleyeceğin bir sonraki hikâyeyi buluyoruz…",18,Ui.MUTED);t.setPadding(12,Ui.dp(this,48),12,12);body.addView(t);}
    @Override public void catalog(List<TitleItem> items,String message){
        if(selected!=null||!section.equals("catalog"))return;
        catalog.clear();catalog.addAll(items);status.setText(source.kind+"  ·  "+message);renderCatalog(items);
    }
    private void renderCatalog(List<TitleItem> items){
        body.removeAllViews();imageGeneration++;int g=imageGeneration;
        if(items.isEmpty()){body.addView(Ui.text(this,"İçerik bulunamadı",20,Ui.WHITE));Ui.gap(body,12);body.addView(Ui.button(this,"Siteyi uygulama içinde aç",this::showBrowser));return;}
        int width=getResources().getDisplayMetrics().widthPixels;float density=getResources().getDisplayMetrics().density;
        int columns=Math.max(3,Math.min(6,(int)(width/density/155)));int cardWidth=(int)(width/density-48)/columns-12;
        GridLayout grid=new GridLayout(this);grid.setColumnCount(columns);
        View firstCard=null;
        for(TitleItem item:items){
            LinearLayout card=Ui.column(this);Ui.focus(card);card.setPadding(Ui.dp(this,5),Ui.dp(this,5),Ui.dp(this,5),Ui.dp(this,8));card.setContentDescription(item.title+", "+item.source.title);
            ImageView poster=new ImageView(this);
            if(item.source==Source.SPORTS){
                poster.setScaleType(ImageView.ScaleType.FIT_CENTER);
                poster.setPadding(Ui.dp(this,8),Ui.dp(this,8),Ui.dp(this,8),Ui.dp(this,8));
            } else {
                poster.setScaleType(ImageView.ScaleType.CENTER_CROP);
            }
            poster.setBackground(Ui.shape(Ui.PANEL,0,this));poster.setContentDescription(item.title);
            card.addView(poster,new LinearLayout.LayoutParams(-1,Ui.dp(this,cardWidth*1.30f)));loadPoster(poster,item.image,item.source,g);
            TextView name=Ui.text(this,item.title,14,Ui.WHITE);Ui.bold(name);name.setMaxLines(2);name.setMinLines(2);name.setEllipsize(TextUtils.TruncateAt.END);name.setPadding(5,8,5,2);card.addView(name);
            String info=item.info;if(section.equals("history")){long p=library.position(item)/60000;info=p>0?p+". dakikadan devam":"İzlemeye devam et";}
            TextView meta=Ui.text(this,info.isEmpty()?item.source.kind:info,11,Ui.MUTED);meta.setSingleLine();meta.setEllipsize(TextUtils.TruncateAt.END);meta.setPadding(5,0,5,0);card.addView(meta);
            card.setOnClickListener(v->openDetail(item));GridLayout.LayoutParams lp=new GridLayout.LayoutParams();lp.width=Ui.dp(this,cardWidth);lp.setMargins(0,0,Ui.dp(this,12),Ui.dp(this,14));grid.addView(card,lp);
            if(firstCard==null)firstCard=card;
        }
        body.addView(grid);if(section.equals("catalog")&&query.isEmpty()&&source!=Source.SPORTS)body.addView(Ui.button(this,"Sonraki sayfa →",()->{status.setText("Sonraki sayfa yükleniyor…");engine.nextPage();}));
        View focus=firstCard;firstContentFocus=focus;body.post(()->{if(!browserVisible&&selected==null&&focus!=null&&g==imageGeneration){focus.requestFocus();}});
    }
    private void loadPoster(ImageView view,String url,Source itemSource,int g){
        if(!MediaPolicy.isHttps(url))return;Bitmap cached=cache.get(url);if(cached!=null){view.setImageBitmap(cached);return;}
        images.execute(()->{
            if(g!=imageGeneration)return;
            HttpURLConnection conn=null;
            try{
                conn=(HttpURLConnection)new URL(url).openConnection();conn.setConnectTimeout(8000);conn.setReadTimeout(8000);
                if(itemSource!=Source.SPORTS)conn.setRequestProperty("Referer",itemSource!=null?itemSource.home:source.home);
                else conn.setRequestProperty("User-Agent","Mozilla/5.0");
                if(conn.getResponseCode()!=200)return;
                try(InputStream in=conn.getInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream()){
                    byte[] buf=new byte[8192];int n;while((n=in.read(buf))!=-1){out.write(buf,0,n);if(out.size()>3*1024*1024)return;}
                    byte[] bytes=out.toByteArray();BitmapFactory.Options o=new BitmapFactory.Options();o.inJustDecodeBounds=true;BitmapFactory.decodeByteArray(bytes,0,bytes.length,o);o.inSampleSize=Math.max(1,o.outWidth/360);o.inJustDecodeBounds=false;
                    Bitmap bitmap=BitmapFactory.decodeByteArray(bytes,0,bytes.length,o);if(bitmap!=null){cache.put(url,bitmap);runOnUiThread(()->{if(!isDestroyed()&&g==imageGeneration)view.setImageBitmap(bitmap);});}
                }
            }catch(Exception ignored){}finally{if(conn!=null)conn.disconnect();}
        });
    }
    private void search(){
        EditText input=new EditText(this);input.setSingleLine();input.setHint("Film, dizi veya kanal adı");input.setText(query);
        new AlertDialog.Builder(this).setTitle(source.title+" içinde ara").setView(input)
            .setPositiveButton("Ara",(d,w)->{
                String q=input.getText().toString().trim();if(q.isEmpty())return;
                hideBrowser();selected=null;section="catalog";query=q;heading.setText("“"+q+"”");body.removeAllViews();loading();
                if(source==Source.SPORTS){
                    status.setText("Spor kanalları taranıyor…");
                    SportsManager.loadChannels(this,channels->{
                        List<TitleItem> items=new ArrayList<>();
                        String lower=q.toLowerCase(Locale.ROOT);
                        for(SportsManager.SportChannel ch:channels){
                            if(ch.name.toLowerCase(Locale.ROOT).contains(lower)||ch.category.toLowerCase(Locale.ROOT).contains(lower)){
                                items.add(ch.toTitleItem());
                            }
                        }
                        catalog(items,items.size()+" kanal");
                    });
                } else {
                    status.setText("Kaynakta aranıyor…");engine.catalog(source,q);
                }
                ((InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(input.getWindowToken(),0);
            }).setNegativeButton("Vazgeç",null).show();input.requestFocus();
    }
    private void showLibrary(String kind){
        hideBrowser();selected=null;section=kind;heading.setText(kind.equals("favorites")?"Favorilerin":"Kaldığın yerden");status.setText("Bu cihazda saklanır");renderCatalog(library.list(kind));
    }
    private void openDetail(TitleItem item){
        if(item==null)return;
        if(item.source==Source.SPORTS){
            SportsManager.SportChannel ch=SportsManager.getChannel(item.url);
            if(ch==null)ch=SportsManager.getChannel(item.title);
            if(ch!=null){playSport(item,ch);return;}
        }
        if(selected==null&&!catalog.isEmpty()){
            returnCatalog.clear();returnCatalog.addAll(catalog);returnHeading=heading.getText().toString();returnStatus=status.getText().toString();returnScrollY=scroll.getScrollY();
        }
        if(!isEpisode(item)){if(series==null||!series.url.equals(item.url)){seriesEpisodes.clear();expandedSeasons.clear();}series=item;}
        scroll.post(()->scroll.scrollTo(0,0));
        resolvingSource=false;activeSourcePanel=null;
        boolean isEp=isEpisode(item);
        hideBrowser();playWhenFound=true;selected=item;source=item.source;streams.clear();imageGeneration++;heading.setText(item.title);
        status.setText(isEp?"Bölüm yayını aranıyor ve hazırlanıyor…":"Film yayını aranıyor ve hazırlanıyor…");
        body.removeAllViews();detailPanel=Ui.column(this);body.addView(detailPanel);
        LinearLayout controls=Ui.row(this);controls.addView(Ui.button(this,"‹ Listeye dön",this::returnToList));space(controls);
        Button favorite=Ui.button(this,library.contains("favorites",item)?"★ Favorilerde":"☆ Favorilere ekle",()->{});
        favorite.setOnClickListener(v->{boolean remove=library.contains("favorites",item);library.save("favorites",item,remove);favorite.setText(remove?"☆ Favorilere ekle":"★ Favorilerde");});controls.addView(favorite);detailPanel.addView(controls);Ui.gap(detailPanel,12);
        description=Ui.text(this,"",14,Ui.MUTED);description.setMaxLines(4);detailPanel.addView(description);
        streamPanel=Ui.column(this);body.addView(streamPanel);engine.detail(item);
    }
    private void playSport(TitleItem item,SportsManager.SportChannel ch){
        if(ch==null||ch.urls.isEmpty())return;
        Intent i=new Intent(this,PlayerActivity.class);
        i.putExtra("item",item.json().toString());
        i.putExtra("url",ch.getPrimaryUrl());
        if(ch.urls.size()>1)i.putExtra("fallbackUrls",ch.urls.toArray(new String[0]));
        if(ch.headers!=null&&!ch.headers.isEmpty())i.putExtra("headers",new JSONObject(ch.headers).toString());
        startActivity(i);
    }
    @Override public void detail(JSONObject data){
        if(selected==null||detailPanel==null)return;
        if(resolvingSource){JSONArray found=data.optJSONArray("frames");if(found!=null&&found.length()>0){String frame=found.optString(0);if(MediaPolicy.isHttps(frame)){resolvingSource=false;engine.frame(frame);}}return;}
        while(detailPanel.getChildCount()>3)detailPanel.removeViewAt(detailPanel.getChildCount()-1);
        description.setText(data.optString("description"));Ui.gap(detailPanel,12);
        JSONArray frames=data.optJSONArray("frames"),actions=data.optJSONArray("actions"),episodes=data.optJSONArray("episodes");
        boolean isEp=isEpisode(selected);
        if(isEp)addEpisodeNavigation(episodes);
        boolean isSeriesOverview=!isEp&&episodes!=null&&episodes.length()>0;
        if(isSeriesOverview){
            playWhenFound=false;
            detailPanel.addView(Ui.text(this,"Sezonlar ve Bölümler",20,Ui.WHITE));Ui.gap(detailPanel,10);
            renderEpisodes(episodes);
            detailPanel.addView(Ui.button(this,"Site oynatıcısını göster",this::showBrowser));
            status.setText("Sezonu açıp bir bölüm seç");
            return;
        }

        JSONObject dublajAction=findAction(actions,"dublaj","tr dub","türkçe ses");
        JSONObject altyaziAction=findAction(actions,"altyaz","alt yazı","sub","orijinal","orjinal");
        boolean hasBothLanguages=dublajAction!=null&&altyaziAction!=null;

        LinearLayout actionCard=Ui.column(this);
        actionCard.setBackground(Ui.shape(Ui.PANEL,0,this));
        actionCard.setPadding(Ui.dp(this,16),Ui.dp(this,14),Ui.dp(this,16),Ui.dp(this,14));
        TextView autoStatus=Ui.text(this,"🎬 Yayın sunucusuna bağlanılıyor, video hazır olduğunda otomatik başlayacak…",14,Ui.MINT);
        actionCard.addView(autoStatus);
        Ui.gap(actionCard,10);
        LinearLayout playBtnRow=Ui.row(this);

        if(hasBothLanguages){
            Button btnDub=Ui.button(this,"🇹🇷 Türkçe Dublaj ile Oynat",()->{
                playWhenFound=true;resolvingSource=true;
                status.setText("Türkçe Dublaj yayını hazırlanıyor…");
                autoStatus.setText("🎬 Türkçe Dublaj hazırlanıyor, otomatik başlatılacak…");
                triggerAction(dublajAction);
            });
            Button btnSub=Ui.button(this,"📝 Türkçe Altyazı ile Oynat",()->{
                playWhenFound=true;resolvingSource=true;
                status.setText("Türkçe Altyazı yayını hazırlanıyor…");
                autoStatus.setText("🎬 Türkçe Altyazı hazırlanıyor, otomatik başlatılacak…");
                triggerAction(altyaziAction);
            });
            playBtnRow.addView(btnDub);space(playBtnRow);playBtnRow.addView(btnSub);
            actionCard.addView(playBtnRow);
            detailPanel.addView(actionCard);
            Ui.gap(detailPanel,12);
            showLanguageDialog(dublajAction,altyaziAction,autoStatus);
            btnDub.requestFocus();
        } else {
            Button btnPlay=Ui.button(this,"▶ Hemen Oynat",()->{
                playWhenFound=true;
                if(!streams.isEmpty())play(bestStream());
                else autoStatus.setText("🎬 Yayın aranıyor, otomatik başlatılacak…");
            });
            playBtnRow.addView(btnPlay);
            actionCard.addView(playBtnRow);
            detailPanel.addView(actionCard);
            Ui.gap(detailPanel,12);
            btnPlay.requestFocus();

            playWhenFound=true;
            if(dublajAction!=null){resolvingSource=true;triggerAction(dublajAction);}
            else if(altyaziAction!=null){resolvingSource=true;triggerAction(altyaziAction);}
            else if(actions!=null&&actions.length()>0){JSONObject a=actions.optJSONObject(0);if(a!=null){resolvingSource=true;triggerAction(a);}}
            else if(frames!=null&&frames.length()>0){String frame=frames.optString(0);if(MediaPolicy.isHttps(frame)){resolvingSource=true;engine.frame(frame);}}
        }

        LinearLayout altSources=Ui.column(this);altSources.setVisibility(View.GONE);
        if(actions!=null&&actions.length()>2){
            for(int i=0;i<actions.length();i++){
                JSONObject a=actions.optJSONObject(i);if(a==null)continue;String lbl=a.optString("label");
                altSources.addView(Ui.button(this,"Kaynak: "+lbl,()->{playWhenFound=true;resolvingSource=true;triggerAction(a);}));Ui.gap(altSources,6);
            }
        } else if(frames!=null&&frames.length()>1){
            for(int i=0;i<frames.length();i++){
                String frame=frames.optString(i);if(!MediaPolicy.isHttps(frame))continue;String host=android.net.Uri.parse(frame).getHost();
                altSources.addView(Ui.button(this,"Kaynak "+(i+1)+" ("+host+")",()->{playWhenFound=true;engine.frame(frame);}));Ui.gap(altSources,6);
            }
        }
        altSources.addView(Ui.button(this,"Site oynatıcısını göster",this::showBrowser));
        Button toggleAlt=Ui.button(this,"Alternatif Kaynaklar ▾",()->{});
        toggleAlt.setOnClickListener(v->{boolean visible=altSources.getVisibility()==View.VISIBLE;altSources.setVisibility(visible?View.GONE:View.VISIBLE);toggleAlt.setText(visible?"Alternatif Kaynaklar ▾":"Alternatif Kaynakları Gizle ▴");});
        detailPanel.addView(toggleAlt);detailPanel.addView(altSources);
        status.setText(hasBothLanguages?"Dil seçimi bekleniyor…":"Yayın hazırlanıyor, otomatik başlatılacak…");
    }
    private void triggerAction(JSONObject a){
        if(a==null)return;
        String frame=a.optString("frame","");
        if(!frame.isEmpty()&&MediaPolicy.isHttps(frame)){
            resolvingSource=false;
            engine.frame(frame);
        } else {
            engine.action(a.optString("id"));
        }
    }
    private void showLanguageDialog(JSONObject dublajAction,JSONObject altyaziAction,TextView autoStatus){
        new AlertDialog.Builder(this).setTitle("Dil Seçeneği")
            .setMessage("Bu içerik hem Türkçe Dublaj hem de Türkçe Altyazı seçeneklerine sahiptir. Nasıl izlemek istersiniz?")
            .setPositiveButton("🇹🇷 Türkçe Dublaj",(d,w)->{
                playWhenFound=true;resolvingSource=true;status.setText("Türkçe Dublaj yayını hazırlanıyor…");
                if(autoStatus!=null)autoStatus.setText("🎬 Türkçe Dublaj hazırlanıyor, otomatik başlatılacak…");
                triggerAction(dublajAction);
            })
            .setNegativeButton("📝 Türkçe Altyazı",(d,w)->{
                playWhenFound=true;resolvingSource=true;status.setText("Türkçe Altyazı yayını hazırlanıyor…");
                if(autoStatus!=null)autoStatus.setText("🎬 Türkçe Altyazı hazırlanıyor, otomatik başlatılacak…");
                triggerAction(altyaziAction);
            })
            .setCancelable(true).show();
    }
    private JSONObject findAction(JSONArray actions,String... keywords){
        if(actions==null)return null;
        for(int i=0;i<actions.length();i++){
            JSONObject a=actions.optJSONObject(i);if(a==null)continue;
            String label=a.optString("label","").toLowerCase(Locale.ROOT);
            for(String kw:keywords)if(label.contains(kw.toLowerCase(Locale.ROOT)))return a;
        }
        return null;
    }
    private void renderEpisodes(JSONArray episodes){
        TreeMap<Integer,List<TitleItem>> seasons=new TreeMap<>();
        for(int i=0;i<episodes.length();i++)try{JSONObject entry=episodes.getJSONObject(i);TitleItem episode=TitleItem.read(entry,source);if(source.owns(episode.url))seasons.computeIfAbsent(entry.optInt("season",0),k->new ArrayList<>()).add(episode);}catch(Exception ignored){}
        seriesEpisodes.clear();for(List<TitleItem> list:seasons.values()){list.sort(Comparator.comparingInt(MainActivity::episodeNumber));seriesEpisodes.addAll(list);}
        for(Map.Entry<Integer,List<TitleItem>> group:seasons.entrySet()){
            String label=group.getKey()==0?"Bölüm seç":group.getKey()+". Sezon · "+group.getValue().size()+" bölüm";
            LinearLayout episodeList=Ui.column(this);episodeList.setPadding(Ui.dp(this,20),Ui.dp(this,8),0,Ui.dp(this,8));
            for(TitleItem episode:group.getValue()){episodeList.addView(Ui.button(this,episode.title,()->openDetail(episode)));Ui.gap(episodeList,6);}
            boolean expanded=expandedSeasons.contains(group.getKey());episodeList.setVisibility(expanded?View.VISIBLE:View.GONE);
            Button seasonButton=Ui.button(this,(expanded?"▾ ":"▸ ")+label,()->{});
            seasonButton.setOnClickListener(v->{boolean show=episodeList.getVisibility()!=View.VISIBLE;episodeList.setVisibility(show?View.VISIBLE:View.GONE);if(show)expandedSeasons.add(group.getKey());else expandedSeasons.remove(group.getKey());seasonButton.setText((show?"▾ ":"▸ ")+label);});
            detailPanel.addView(seasonButton);detailPanel.addView(episodeList);Ui.gap(detailPanel,8);
        }
    }
    private static boolean isEpisode(TitleItem item){return item!=null&&MediaPolicy.isEpisode(item.url);}
    private static int episodeNumber(TitleItem item){java.util.regex.Matcher m=java.util.regex.Pattern.compile("-sezon-([0-9]+)-bolum").matcher(item.url);return m.find()?Integer.parseInt(m.group(1)):0;}
    private void addEpisodeNavigation(JSONArray entries){
        TitleItem previous=null,next=null;
        for(int i=0;i<seriesEpisodes.size();i++)if(seriesEpisodes.get(i).url.equals(selected.url)){if(i>0)previous=seriesEpisodes.get(i-1);if(i+1<seriesEpisodes.size())next=seriesEpisodes.get(i+1);break;}
        if(entries!=null)for(int i=0;i<entries.length();i++)try{JSONObject e=entries.getJSONObject(i);TitleItem candidate=TitleItem.read(e,source);if(!source.owns(candidate.url))continue;String relation=e.optString("relation");if(previous==null&&relation.equals("previous"))previous=candidate;if(next==null&&relation.equals("next"))next=candidate;}catch(Exception ignored){}
        LinearLayout navigation=Ui.row(this);TitleItem p=previous,n=next;
        Button prev=Ui.button(this,"‹ Önceki bölüm",()->{if(p!=null)openDetail(p);});prev.setEnabled(p!=null);prev.setFocusable(p!=null);prev.setAlpha(p==null?0.4f:1);navigation.addView(prev);space(navigation);
        if(series!=null){navigation.addView(Ui.button(this,"Sezonlara dön",()->openDetail(series)));space(navigation);}
        Button following=Ui.button(this,"Sonraki bölüm ›",()->{if(n!=null)openDetail(n);});following.setEnabled(n!=null);following.setFocusable(n!=null);following.setAlpha(n==null?0.4f:1);navigation.addView(following);detailPanel.addView(navigation);Ui.gap(detailPanel,12);
    }
    @Override public void stream(String url,Map<String,String> headers){
        if(selected==null||streams.containsKey(url))return;streams.put(url,headers);
        status.setText("Video bağlantısı bulundu. Dahili oynatıcı açılıyor…");
        if(browserVisible)updateBrowserToolbar();
        LinearLayout targetPanel=activeSourcePanel!=null?activeSourcePanel:streamPanel!=null?streamPanel:detailPanel;
        if(targetPanel!=null){
            if(streams.size()==1&&targetPanel==activeSourcePanel)targetPanel.removeAllViews();
            Button b=Ui.button(this,"▶ Dahili Oynatıcıda Oynat ("+streams.size()+")",()->play(url));
            targetPanel.addView(b);Ui.gap(targetPanel,6);
            if(!browserVisible)b.requestFocus();
        }
        if(playWhenFound||isEpisode(selected)){
            if(pendingAutoPlay!=null)engine.web.removeCallbacks(pendingAutoPlay);
            pendingAutoPlay=()->{if(!streams.isEmpty()){playWhenFound=false;play(bestStream());}};
            engine.web.postDelayed(pendingAutoPlay,400);
        }
    }
    private void selectSource(LinearLayout panel){
        int selection=++sourceSelection;
        playWhenFound=false;streams.clear();if(activeSourcePanel!=null)activeSourcePanel.removeAllViews();activeSourcePanel=panel;
        panel.setPadding(Ui.dp(this,20),Ui.dp(this,8),0,Ui.dp(this,8));panel.addView(Ui.text(this,"Videolar aranıyor…",14,Ui.MUTED));status.setText("Seçilen kaynak açılıyor…");
        panel.postDelayed(()->{if(selection==sourceSelection&&activeSourcePanel==panel&&streams.isEmpty()&&!status.getText().toString().startsWith("Yayın sunucusu")){panel.removeAllViews();panel.addView(Ui.text(this,"Oynatıcı bekleniyor; reklam varsa bitmesi beklenir…",14,Ui.MUTED));}},15000);
        panel.postDelayed(()->{if(selection==sourceSelection&&activeSourcePanel==panel&&streams.isEmpty()){resolvingSource=false;if(!status.getText().toString().startsWith("Yayın sunucusu")){panel.removeAllViews();panel.addView(Ui.text(this,"Dahili bağlantı alınamadı. Site oynatıcısını başlatabilirsin.",14,Ui.MUTED));}}},91000);
    }
    private void showCategories(){
        if(selected!=null){openSource(source);status.setText("Katalog yüklenince Kategoriler düğmesine bas.");return;}
        if(source==Source.SPORTS){
            SportsManager.loadChannels(this,channels->{
                Set<String> catSet=new LinkedHashSet<>();
                catSet.add("Tüm Spor Kanalları");
                for(SportsManager.SportChannel ch:channels){
                    if(!ch.category.isEmpty())catSet.add(ch.category);
                }
                String[] cats=catSet.toArray(new String[0]);
                new AlertDialog.Builder(this).setTitle("Spor Kategorisi Seç").setItems(cats,(d,w)->{
                    String chosen=cats[w];
                    heading.setText(chosen);
                    List<TitleItem> items=new ArrayList<>();
                    for(SportsManager.SportChannel ch:channels){
                        if(w==0||ch.category.equalsIgnoreCase(chosen)){
                            items.add(ch.toTitleItem());
                        }
                    }
                    catalog(items,items.size()+" kanal");
                }).show();
            });
            return;
        }
        engine.categories(data->{JSONArray items=data.optJSONArray("items");if(items==null||items.length()==0){new AlertDialog.Builder(this).setMessage("Bu sayfada kategori bağlantısı bulunamadı. Katalog yüklendikten sonra tekrar dene.").setPositiveButton("Tamam",null).show();return;}String[] labels=new String[items.length()];for(int n=0;n<labels.length;n++)labels[n]=items.optJSONObject(n).optString("label");new AlertDialog.Builder(this).setTitle("Kategori seç").setItems(labels,(d,w)->{JSONObject item=items.optJSONObject(w);selected=null;section="catalog";query="";heading.setText(item.optString("label"));body.removeAllViews();loading();engine.category(item.optString("url"));}).show();});
    }
    private void returnToList(){
        if(returnCatalog.isEmpty()){openSource(source);return;}
        hideBrowser();engine.stopPlayback();selected=null;series=null;seriesEpisodes.clear();expandedSeasons.clear();activeSourcePanel=null;resolvingSource=false;streams.clear();section="catalog";
        heading.setText(returnHeading);status.setText(returnStatus);catalog.clear();catalog.addAll(returnCatalog);renderCatalog(new ArrayList<>(returnCatalog));scroll.post(()->scroll.scrollTo(0,returnScrollY));
    }
    private String bestStream(){
        String fallback=null;
        for(String candidate:streams.keySet()){
            if(MediaPolicy.isAd(candidate)) continue;
            if(fallback==null)fallback=candidate;
            String lower=candidate.toLowerCase(Locale.ROOT);
            if(!lower.matches(".*(^|[/_.?&=-])(audio|sound|aac|mp3)([/_.?&=-]|$).*")&&
               (lower.contains("master")||lower.contains("index")||lower.contains("playlist")))return candidate;
        }
        for(String candidate:streams.keySet()){
            if(MediaPolicy.isAd(candidate)) continue;
            if(!candidate.toLowerCase(Locale.ROOT).matches(".*(^|[/_.?&=-])(audio|sound|aac|mp3)([/_.?&=-]|$).*"))return candidate;
        }
        return fallback;
    }
    private void play(String url){
        if(selected==null||url==null||url.isEmpty())return;
        Map<String,String> selectedHeaders=streams.containsKey(url)?new HashMap<>(streams.get(url)):new HashMap<>();
        engine.stopPlayback();
        Intent i=new Intent(this,PlayerActivity.class);
        i.putExtra("item",selected.json().toString());
        i.putExtra("url",url);
        if(!selectedHeaders.isEmpty()){
            i.putExtra("headers",new JSONObject(selectedHeaders).toString());
        }
        ArrayList<String> fallbacks=new ArrayList<>();
        for(String u:streams.keySet()){
            if(!u.equals(url)&&MediaPolicy.isVideo(u))fallbacks.add(u);
        }
        if(!fallbacks.isEmpty()){
            i.putExtra("fallbackUrls",fallbacks.toArray(new String[0]));
        }
        startActivity(i);
    }
    private void showBrowser(){
        if(browserVisible)return;browserVisible=true;scroll.setVisibility(View.GONE);
        engine.showOriginalPage();
        engine.web.evaluateJavascript("window.__seyirVisible=true;window.postMessage({seyir:'start',visible:true},'*')",null);
        engine.web.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_AUTO);
        engine.web.setFocusable(true);engine.web.setFocusableInTouchMode(true);
        engine.web.evaluateJavascript("document.activeElement?.blur();document.querySelectorAll('input,textarea').forEach(e=>e.blur())",null);
        FrameLayout.LayoutParams lp=new FrameLayout.LayoutParams(-1,-1);lp.topMargin=Ui.dp(this,62);engine.web.setLayoutParams(lp);
        browserPanel=Ui.column(this);browserPanel.setBackgroundColor(Ui.BG);root.addView(browserPanel,new FrameLayout.LayoutParams(-1,Ui.dp(this,62)));updateBrowserToolbar();engine.web.requestFocus();
    }
    private void updateBrowserToolbar(){
        if(browserPanel==null)return;browserPanel.removeAllViews();LinearLayout row=Ui.row(this);row.setPadding(8,6,8,6);
        row.addView(Ui.button(this,"‹ Uygulamaya dön",this::hideBrowser));space(row);
        row.addView(Ui.button(this,"Sayfada geri",()->{if(engine.web.canGoBack())engine.web.goBack();}));space(row);
        row.addView(Ui.button(this,"Yenile",()->engine.web.reload()));space(row);
        row.addView(Ui.button(this,"Tam ekran",()->engine.fullscreen()));space(row);
        if(!streams.isEmpty())row.addView(Ui.button(this,"▶ Dahili oynatıcı",()->{
            ArrayList<String> urls=new ArrayList<>(streams.keySet());String[] labels=new String[urls.size()];for(int n=0;n<labels.length;n++)labels[n]="Kaynak "+(n+1)+" · "+android.net.Uri.parse(urls.get(n)).getHost();
            if(urls.size()==1)play(urls.get(0));else new AlertDialog.Builder(this).setTitle("Video seç").setItems(labels,(d,w)->play(urls.get(w))).show();
        }));
        browserPanel.addView(row);
    }
    private void hideBrowser(){
        if(!browserVisible)return;hideFullscreen();browserVisible=false;root.removeView(browserPanel);browserPanel=null;engine.web.setLayoutParams(new FrameLayout.LayoutParams(-1,-1));
        engine.web.clearFocus();engine.web.setFocusable(false);engine.web.setFocusableInTouchMode(false);
        engine.web.evaluateJavascript("window.__seyirVisible=false;window.postMessage({seyir:'stop'},'*');document.querySelectorAll('video,audio').forEach(v=>v.pause())",null);
        engine.web.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);scroll.setVisibility(View.VISIBLE);scroll.bringToFront();
        shell.post(()->{View focus=selected!=null&&detailPanel!=null?detailPanel:firstContentFocus;if(focus!=null&&focus.isAttachedToWindow())focus.requestFocus();});
    }
    private void hideFullscreen(){if(fullscreen!=null){root.removeView(fullscreen);fullscreen=null;if(fullCallback!=null){fullCallback.onCustomViewHidden();fullCallback=null;}getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);}}
    @Override public void status(String message){if(status!=null)status.setText(message);if(message.startsWith("Yayın sunucusu")&&activeSourcePanel!=null&&streams.isEmpty()){activeSourcePanel.removeAllViews();activeSourcePanel.addView(Ui.text(this,message,14,Ui.MUTED));}}
    @Override public boolean dispatchKeyEvent(KeyEvent event){
        if(!browserVisible&&event.getAction()==KeyEvent.ACTION_DOWN&&
            (event.getKeyCode()==KeyEvent.KEYCODE_DPAD_UP||event.getKeyCode()==KeyEvent.KEYCODE_DPAD_DOWN||
             event.getKeyCode()==KeyEvent.KEYCODE_DPAD_LEFT||event.getKeyCode()==KeyEvent.KEYCODE_DPAD_RIGHT||
             event.getKeyCode()==KeyEvent.KEYCODE_DPAD_CENTER||event.getKeyCode()==KeyEvent.KEYCODE_ENTER)){
            View current=getCurrentFocus();
            if(current==null||current==engine.web||current instanceof EditText){
                View fallback=selected==null&&firstContentFocus!=null&&firstContentFocus.isAttachedToWindow()?firstContentFocus:defaultFocus;if(fallback!=null)fallback.requestFocus();
                return true;
            }
        }
        return super.dispatchKeyEvent(event);
    }
    @Override public void onBackPressed(){if(fullscreen!=null)hideFullscreen();else if(browserVisible)hideBrowser();else if(selected!=null)returnToList();else if(!section.equals("catalog")||!query.isEmpty())openSource(source);else new AlertDialog.Builder(this).setMessage("Seyir TV kapatılsın mı?").setPositiveButton("Çık",(d,w)->finish()).setNegativeButton("Kal",null).show();}
    @Override protected void onResume(){super.onResume();if(engine!=null)engine.resume();}
    @Override protected void onPause(){if(engine!=null)engine.pause();super.onPause();}
    @Override protected void onDestroy(){imageGeneration++;images.shutdownNow();if(engine!=null)engine.destroy();super.onDestroy();}
    @Override protected void onSaveInstanceState(Bundle state){state.putString("source",source.name());super.onSaveInstanceState(state);}
}
