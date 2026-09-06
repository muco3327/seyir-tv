package tv.seyir.app;

import android.test.InstrumentationTestCase;
import android.webkit.*;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

/** Executes the actual extraction JS against fixture DOM in Android's real WebView. */
public class CatalogInstrumentationTest extends InstrumentationTestCase {
    private JSONObject extract(String base,String html,String query)throws Exception{
        String js;
        try(InputStream in=getInstrumentation().getTargetContext().getAssets().open("catalog.js");ByteArrayOutputStream out=new ByteArrayOutputStream()){
            byte[] b=new byte[4096];int n;while((n=in.read(b))!=-1)out.write(b,0,n);js=out.toString(StandardCharsets.UTF_8.name());
        }
        final String script=js.replace("'__QUERY__'",JSONObject.quote(query));
        CountDownLatch latch=new CountDownLatch(1);AtomicReference<String> result=new AtomicReference<>();AtomicReference<WebView> ref=new AtomicReference<>();
        getInstrumentation().runOnMainSync(()->{
            WebView w=new WebView(getInstrumentation().getTargetContext());ref.set(w);w.getSettings().setJavaScriptEnabled(true);
            w.setWebViewClient(new WebViewClient(){@Override public void onPageFinished(WebView view,String url){view.evaluateJavascript(script,value->{result.set(value);latch.countDown();});}});
            w.loadDataWithBaseURL(base,"<!doctype html><html><body>"+html+"</body></html>","text/html","UTF-8",null);
        });
        try{assertTrue("WebView parser timeout",latch.await(20,TimeUnit.SECONDS));return new JSONObject((String)new JSONTokener(result.get()).nextValue());}
        finally{getInstrumentation().runOnMainSync(()->ref.get().destroy());}
    }
    public void testFullHdCardsAndDeduplication()throws Exception{
        JSONObject r=extract(Source.FULLHD.home,"<div class='film'><a class='tt' href='/film/test/'>Deneme izle</a><h2><span class='film-title'>Deneme</span></h2><span class='film-yil'>2024</span></div><a class='tt' href='/film/test/'>Deneme izle</a>","");
        assertEquals(1,r.getJSONArray("items").length());assertEquals("Deneme",r.getJSONArray("items").getJSONObject(0).getString("title"));
    }
    public void testCehennemAndForeignLinks()throws Exception{
        JSONObject r=extract(Source.CEHENNEM.home,"<a class='poster' href='/movie/' title='Bir Film'></a><a class='mini-poster' href='/dizi/bir-dizi/' title='Bir Dizi'></a><a class='poster' href='https://ad.example.org/' title='Reklam'></a>","");
        assertEquals(2,r.getJSONArray("items").length());
        assertEquals("Dizi",r.getJSONArray("items").getJSONObject(1).getString("info"));
    }
    public void testDizillaEpisodeAndSeries()throws Exception{
        JSONObject r=extract(Source.DIZILLA.home,"<a href='/dizi/deneme'>Deneme</a><a href='/deneme-1-sezon-2-bolum' title='Deneme 1. Sezon 2. Bölüm'>Bölüm</a><a href='/arsiv'>Keşfet</a>","");
        assertEquals(2,r.getJSONArray("items").length());
    }
    public void testTurkishSearchDoesNotReturnUnrelatedCatalogue()throws Exception{
        JSONObject r=extract(Source.DIZILLA.home,"<a href='/dizi/isik'>Işık</a><a href='/dizi/deneme'>Deneme</a>","ışık");
        assertEquals(1,r.getJSONArray("items").length());assertEquals("Işık",r.getJSONArray("items").getJSONObject(0).getString("title"));
    }
}
