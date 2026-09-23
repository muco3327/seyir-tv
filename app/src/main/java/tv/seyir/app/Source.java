package tv.seyir.app;

import java.net.URI;

public enum Source {
    SPORTS("Canlı TV", "https://raw.githubusercontent.com/muco3327/seyir-tv/main/sports.json", "CANLI YAYIN");

    public final String title, home, kind;
    Source(String title, String home, String kind) { this.title = title; this.home = home; this.kind = kind; }
    public boolean owns(String url) {
        if (this == SPORTS) return true;
        return false;
    }
}
