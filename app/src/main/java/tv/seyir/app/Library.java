package tv.seyir.app;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import java.util.ArrayList;
import java.util.List;

public final class Library {
    private final SharedPreferences prefs;
    public Library(Context c) { prefs=c.getSharedPreferences("library",Context.MODE_PRIVATE); }
    public List<TitleItem> list(String kind) {
        List<TitleItem> result=new ArrayList<>();
        try {
            JSONArray arr=new JSONArray(prefs.getString(kind,"[]"));
            for(int i=0;i<arr.length();i++) {
                TitleItem item=TitleItem.read(arr.getJSONObject(i),Source.FULLHD);
                if(item.source.owns(item.url)) result.add(item);
            }
        } catch(Exception ignored) { }
        return result;
    }
    public boolean contains(String kind,TitleItem item) { for(TitleItem t:list(kind)) if(t.key().equals(item.key())) return true; return false; }
    public void save(String kind,TitleItem item,boolean remove) {
        JSONArray arr=new JSONArray();
        if(!remove) arr.put(item.json());
        for(TitleItem old:list(kind)) if(!old.key().equals(item.key()) && arr.length()<100) arr.put(old.json());
        prefs.edit().putString(kind,arr.toString()).apply();
    }
    public long position(TitleItem item) { return prefs.getLong("position:"+item.key(),0); }
    public void progress(TitleItem item,long position,long duration) {
        if(position<0) return;
        boolean complete=duration>0 && duration-position<30000;
        prefs.edit().putLong("position:"+item.key(),complete?0:position).apply();
        save("history",item,complete);
    }
}
