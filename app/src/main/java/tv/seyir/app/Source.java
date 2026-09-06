package tv.seyir.app;

import java.net.URI;

public enum Source {
    FULLHD("FullHD Filmizlesene", "https://www.fullhdfilmizlesene.now/", "FİLM"),
    CEHENNEM("HD Film Cehennemi", "https://www.hdfilmcehennemi.nl/", "FİLM · DİZİ"),
    DIZILLA("Dizilla", "https://dizilla.now/", "DİZİ"),
    DIZIBOX("DiziBOX", "https://dizibox.now/", "DİZİ");

    public final String title, home, kind;
    Source(String title, String home, String kind) { this.title = title; this.home = home; this.kind = kind; }
    public boolean owns(String url) {
        if (!MediaPolicy.isHttps(url)) return false;
        try {
            URI uri = URI.create(url);
            String host = uri.getHost();
            if (host == null) return false;
            host = host.toLowerCase(java.util.Locale.ROOT);
            if (this == DIZIBOX && host.matches("^(?:[a-z0-9-]+\\.)*dizibox\\.[a-z]{2,6}$")) return true;
            if (this == DIZILLA && host.matches("^(?:[a-z0-9-]+\\.)*dizilla\\.[a-z]{2,6}$")) return true;
            if (this == FULLHD && host.matches("^(?:[a-z0-9-]+\\.)*fullhdfilmizlesene\\.[a-z]{2,6}$")) return true;
            if (this == CEHENNEM && host.matches("^(?:[a-z0-9-]+\\.)*hdfilmcehennemi\\.[a-z]{2,6}$")) return true;
            String expected = URI.create(home).getHost().replaceFirst("^www\\.", "");
            return host.equalsIgnoreCase(expected) || host.equalsIgnoreCase("www." + expected);
        } catch (RuntimeException e) { return false; }
    }
}
