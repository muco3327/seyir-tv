package tv.seyir.app;

import java.net.URI;

public enum Source {
    FULLHD("FullHD Filmizlesene", "https://www.fullhdfilmizlesene.now/", "FİLM"),
    CEHENNEM("HD Film Cehennemi", "https://www.hdfilmcehennemi.nl/", "FİLM · DİZİ"),
    DIZILLA("Dizilla", "https://dizilla.now/", "DİZİ"),
    DIZIBOX("DiziBOX", "https://www.dizibox.live/", "DİZİ");

    public final String title, home, kind;
    Source(String title, String home, String kind) { this.title = title; this.home = home; this.kind = kind; }
    public boolean owns(String url) {
        if (!MediaPolicy.isHttps(url)) return false;
        try {
            URI uri = URI.create(url);
            String host = uri.getHost();
            String expected = URI.create(home).getHost().replaceFirst("^www\\.", "");
            return "https".equalsIgnoreCase(uri.getScheme()) && host != null &&
                (host.equalsIgnoreCase(expected) || host.equalsIgnoreCase("www." + expected));
        } catch (RuntimeException e) { return false; }
    }
}
