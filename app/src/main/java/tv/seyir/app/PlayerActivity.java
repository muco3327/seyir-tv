package tv.seyir.app;

import android.app.*;
import android.os.Bundle;
import android.view.*;
import android.webkit.CookieManager;
import android.widget.*;
import androidx.media3.common.*;
import androidx.media3.datasource.*;
import androidx.media3.exoplayer.*;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import androidx.media3.ui.PlayerView;
import androidx.media3.ui.AspectRatioFrameLayout;
import org.json.JSONObject;
import java.util.*;

@androidx.media3.common.util.UnstableApi
public final class PlayerActivity extends Activity {
    private ExoPlayer player;
    private PlayerView video;
    private TitleItem title;
    private Library library;
    private TextView status;
    private LinearLayout toolbar;
    private Button playButton,settingsButton;
    private String url;
    private final Map<String,String> headers=new HashMap<>();
    private long position;
    private boolean shouldPlay=true,closing=false;
    private final Runnable hideControls=()->{if(player!=null&&player.isPlaying()&&!insideToolbar(getCurrentFocus()))overlays(false);};

    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        try{
            title=TitleItem.read(new JSONObject(getIntent().getStringExtra("item")),Source.FULLHD);
            url=getIntent().getStringExtra("url");
            if(!MediaPolicy.isVideo(url)){finish();return;}
            String rawHeaders=getIntent().getStringExtra("headers");
            if(rawHeaders!=null){
                JSONObject h=new JSONObject(rawHeaders);
                Iterator<String> it=h.keys();while(it.hasNext()){String k=it.next();if(k.equalsIgnoreCase("Referer")||k.equalsIgnoreCase("Origin")||k.equalsIgnoreCase("User-Agent"))headers.put(k,h.optString(k));}
            }
        }catch(Exception e){finish();return;}
        library=new Library(this);position=state==null?library.position(title):state.getLong("position",0);
        setContentView(R.layout.activity_player);hideBars();
        toolbar=findViewById(R.id.top_bar);video=findViewById(R.id.player_view);status=findViewById(R.id.player_status);
        video.setControllerAutoShow(false);video.setControllerShowTimeoutMs(5000);
        toolbar.addView(Ui.button(this,"‹ Geri",this::closePlayer));
        TextView name=Ui.text(this,title.title,16,Ui.WHITE);name.setMaxLines(2);name.setPadding(12,0,12,0);toolbar.addView(name,new LinearLayout.LayoutParams(0,-2,1));
        playButton=Ui.button(this,"Duraklat",this::togglePlay);toolbar.addView(playButton);
        settingsButton=Ui.button(this,"Ayarlar",this::settings);toolbar.addView(settingsButton);
    }

    private void initialize(){
        if(player!=null||url==null||closing||isFinishing()||video==null)return;
        try{
            // Read WebView cookies on the UI thread and never forward them to a different origin.
            String cookie=CookieManager.getInstance().getCookie(url);
            android.net.Uri origin=android.net.Uri.parse(url);
            DefaultHttpDataSource.Factory http=new DefaultHttpDataSource.Factory().setDefaultRequestProperties(headers)
                .setConnectTimeoutMs(15000).setReadTimeoutMs(20000).setAllowCrossProtocolRedirects(false);
            ResolvingDataSource.Factory data=new ResolvingDataSource.Factory(http,spec->{
                boolean same=Objects.equals(origin.getScheme(),spec.uri.getScheme())&&Objects.equals(origin.getHost(),spec.uri.getHost())&&origin.getPort()==spec.uri.getPort();
                return same&&cookie!=null?spec.withAdditionalHeaders(Collections.singletonMap("Cookie",cookie)):spec;
            });
            player=new ExoPlayer.Builder(this,new DefaultRenderersFactory(this).setEnableDecoderFallback(true))
                .setMediaSourceFactory(new DefaultMediaSourceFactory(data)).setSeekBackIncrementMs(10000).setSeekForwardIncrementMs(10000).build();
            player.setAudioAttributes(new AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(),true);
            player.setTrackSelectionParameters(player.getTrackSelectionParameters().buildUpon().setPreferredAudioLanguage("tr").setPreferredTextLanguage("tr").build());
            video.setPlayer(player);
            player.addListener(new Player.Listener(){
                @Override public void onIsPlayingChanged(boolean playing){playButton.setText(playing?"Duraklat":"Oynat");if(playing)scheduleHide();}
                @Override public void onPlaybackStateChanged(int s){
                    if(player==null||closing)return;
                    if(s==Player.STATE_READY){status.setText("OK: oynat/duraklat · Sağ/sol: 10 sn · Yukarı: menü");library.save("history",title,false);scheduleHide();}
                    else if(s==Player.STATE_BUFFERING){status.setVisibility(View.VISIBLE);status.setText("Video yükleniyor…");}
                    else if(s==Player.STATE_ENDED){library.progress(title,player.getDuration(),player.getDuration());overlays(true);status.setText("Bölüm / film tamamlandı");}
                }
                @Override public void onPlayerError(PlaybackException e){showError(e.getErrorCodeName());}
            });
            MediaItem.Builder item=new MediaItem.Builder().setUri(url);
            String path=android.net.Uri.parse(url).getPath();
            if((path!=null&&path.toLowerCase(Locale.ROOT).endsWith(".m3u8"))||url.toLowerCase(Locale.ROOT).contains(".m3u8"))item.setMimeType(MimeTypes.APPLICATION_M3U8);
            player.setMediaItem(item.build());player.seekTo(position);player.prepare();player.setPlayWhenReady(shouldPlay);video.requestFocus();
        }catch(RuntimeException e){release();showError(e.getClass().getSimpleName());}
    }
    private void showError(String code){
        if(isFinishing()||isDestroyed())return;
        overlays(true);status.setText("Video açılamadı: "+code);
        new AlertDialog.Builder(this).setTitle("Video açılamadı").setMessage("Hata: "+code+"\nBaşka bir video kaynağı seçebilir veya tekrar deneyebilirsin.")
            .setPositiveButton("Kaynağa dön",(d,w)->closePlayer()).setNegativeButton("Tekrar dene",(d,w)->{if(player==null)initialize();else{player.prepare();player.play();}}).show();
    }
    private void togglePlay(){if(player==null)return;if(player.getPlayWhenReady())player.pause();else player.play();overlays(true);scheduleHide();}
    private void seek(long delta){if(player==null)return;long end=player.getDuration(),target=Math.max(0,player.getCurrentPosition()+delta);if(end>0)target=Math.min(end,target);player.seekTo(target);overlays(true);status.setText((delta<0?"◀ ":"▶ ")+Math.abs(delta/1000)+" saniye · "+(target/60000)+":"+String.format(Locale.ROOT,"%02d",target/1000%60));scheduleHide();}
    private void settings(){
        new AlertDialog.Builder(this).setTitle("Oynatıcı ayarları").setItems(new String[]{"Görüntü kalitesi","Ses dili","Altyazı","Oynatma hızı","Ekrana sığdır / yakınlaştır","Baştan başlat"},(d,w)->{
            if(w<3)tracks(w==0?C.TRACK_TYPE_VIDEO:w==1?C.TRACK_TYPE_AUDIO:C.TRACK_TYPE_TEXT);
            else if(w==3)new AlertDialog.Builder(this).setTitle("Oynatma hızı").setItems(new String[]{"0.75×","1×","1.25×","1.5×","2×"},(x,n)->{if(player!=null)player.setPlaybackSpeed(new float[]{.75f,1,1.25f,1.5f,2}[n]);}).show();
            else if(w==4)video.setResizeMode(video.getResizeMode()==AspectRatioFrameLayout.RESIZE_MODE_FIT?AspectRatioFrameLayout.RESIZE_MODE_ZOOM:AspectRatioFrameLayout.RESIZE_MODE_FIT);
            else if(player!=null)player.seekTo(0);
        }).show();
    }
    private void tracks(int type){
        if(player==null)return;List<String> labels=new ArrayList<>();List<TrackSelectionOverride> overrides=new ArrayList<>();
        labels.add(type==C.TRACK_TYPE_TEXT?"Kapalı":"Otomatik");overrides.add(null);
        for(Tracks.Group group:player.getCurrentTracks().getGroups())if(group.getType()==type)for(int i=0;i<group.length;i++)if(group.isTrackSupported(i)){
            Format f=group.getTrackFormat(i);String label=type==C.TRACK_TYPE_VIDEO?(f.height>0?f.height+"p":"Video"):(f.label!=null?f.label:f.language!=null?f.language:"Parça "+(i+1));labels.add(label);overrides.add(new TrackSelectionOverride(group.getMediaTrackGroup(),i));
        }
        if(labels.size()<=1){
            new AlertDialog.Builder(this).setTitle(type==C.TRACK_TYPE_VIDEO?"Görüntü kalitesi":type==C.TRACK_TYPE_AUDIO?"Ses dili":"Altyazı")
                .setMessage("Bu yayında seçilebilir alternatif parça bulunamadı.").setPositiveButton("Tamam",null).show();
            return;
        }
        new AlertDialog.Builder(this).setTitle(type==C.TRACK_TYPE_VIDEO?"Görüntü kalitesi":type==C.TRACK_TYPE_AUDIO?"Ses dili":"Altyazı")
            .setItems(labels.toArray(new String[0]),(d,w)->{if(player==null)return;TrackSelectionParameters.Builder p=player.getTrackSelectionParameters().buildUpon().clearOverridesOfType(type).setTrackTypeDisabled(type,type==C.TRACK_TYPE_TEXT&&w==0);if(w>0)p.setOverrideForType(overrides.get(w));player.setTrackSelectionParameters(p.build());}).show();
    }
    private boolean insideToolbar(View view){while(view!=null){if(view==toolbar)return true;view=view.getParent() instanceof View?(View)view.getParent():null;}return false;}
    private void hideBars(){getWindow().getDecorView().setSystemUiVisibility(5894);}
    private void overlays(boolean show){if(toolbar==null)return;toolbar.setVisibility(show?View.VISIBLE:View.GONE);status.setVisibility(show?View.VISIBLE:View.GONE);if(show)video.showController();else{video.hideController();video.requestFocus();}hideBars();}
    private void scheduleHide(){video.removeCallbacks(hideControls);video.postDelayed(hideControls,5000);}
    private void release(){
        if(video!=null)video.removeCallbacks(hideControls);
        if(player==null)return;ExoPlayer old=player;player=null;
        position=old.getCurrentPosition();shouldPlay=old.getPlayWhenReady();if(position>1000)library.progress(title,position,old.getDuration());video.setPlayer(null);old.release();
    }
    private void closePlayer(){closing=true;release();finish();}
    @Override public boolean dispatchKeyEvent(KeyEvent e){
        if(player==null)return super.dispatchKeyEvent(e);int k=e.getKeyCode();
        if(e.getAction()!=KeyEvent.ACTION_DOWN)return super.dispatchKeyEvent(e);
        if(k==KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE){if(e.getRepeatCount()==0)togglePlay();return true;}
        if(k==KeyEvent.KEYCODE_MEDIA_PLAY){player.play();return true;}if(k==KeyEvent.KEYCODE_MEDIA_PAUSE){player.pause();return true;}
        if(k==KeyEvent.KEYCODE_MEDIA_FAST_FORWARD||k==KeyEvent.KEYCODE_MEDIA_NEXT){seek(30000);return true;}
        if(k==KeyEvent.KEYCODE_MEDIA_REWIND||k==KeyEvent.KEYCODE_MEDIA_PREVIOUS){seek(-30000);return true;}
        if(k==KeyEvent.KEYCODE_MENU||k==KeyEvent.KEYCODE_INFO){overlays(true);settingsButton.requestFocus();return true;}
        if(insideToolbar(getCurrentFocus())){if(k==KeyEvent.KEYCODE_DPAD_DOWN){overlays(false);return true;}return super.dispatchKeyEvent(e);}
        if(video.isControllerFullyVisible()&&getCurrentFocus()!=video)return super.dispatchKeyEvent(e);
        if(k==KeyEvent.KEYCODE_DPAD_CENTER||k==KeyEvent.KEYCODE_ENTER){if(e.getRepeatCount()==0)togglePlay();return true;}
        if(k==KeyEvent.KEYCODE_DPAD_RIGHT){seek(e.getRepeatCount()>4?30000:10000);return true;}
        if(k==KeyEvent.KEYCODE_DPAD_LEFT){seek(e.getRepeatCount()>4?-30000:-10000);return true;}
        if(k==KeyEvent.KEYCODE_DPAD_UP){overlays(true);playButton.requestFocus();return true;}
        if(k==KeyEvent.KEYCODE_DPAD_DOWN){overlays(false);return true;}
        return super.dispatchKeyEvent(e);
    }
    @Override public void onBackPressed(){if(toolbar!=null&&toolbar.getVisibility()==View.VISIBLE)overlays(false);else closePlayer();}
    @Override public void onWindowFocusChanged(boolean focus){super.onWindowFocusChanged(focus);if(focus)hideBars();}
    @Override protected void onStart(){super.onStart();initialize();}
    @Override protected void onStop(){release();super.onStop();}
    @Override protected void onDestroy(){release();super.onDestroy();}
    @Override protected void onSaveInstanceState(Bundle state){state.putLong("position",player==null?position:player.getCurrentPosition());super.onSaveInstanceState(state);}
}
