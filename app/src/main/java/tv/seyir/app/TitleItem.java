package tv.seyir.app;

import org.json.JSONObject;
import org.json.JSONException;

public final class TitleItem {
    public final Source source;
    public final String title, url, image, info;
    public TitleItem(Source source, String title, String url, String image, String info) {
        this.source=source; this.title=title; this.url=url; this.image=image; this.info=info;
    }
    public String key() { return MediaPolicy.key(source.name(), url); }
    public JSONObject json() {
        JSONObject j=new JSONObject();
        try { j.put("source",source.name()); j.put("title",title); j.put("url",url); j.put("image",image); j.put("info",info); }
        catch(JSONException ignored) { }
        return j;
    }
    public static TitleItem read(JSONObject j, Source fallback) {
        Source s=fallback;
        try { s=Source.valueOf(j.optString("source",fallback.name())); } catch(IllegalArgumentException ignored) { }
        return new TitleItem(s,j.optString("title"),j.optString("url"),j.optString("image"),j.optString("info"));
    }
}
