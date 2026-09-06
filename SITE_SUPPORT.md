# Seyir TV site adapter notları

Bu dosya, uygulamanın hangi siteyi hangi gerçek sayfa yapısıyla okuduğunu açıklar. Yeni bir kişi `Source.java`, `catalog.js`, `detail.js` ve `SiteEngine.java` ile birlikte bu dosyadan devam edebilir.

## FullHD Filmizlesene

- Ana katalog film kartları: `a.tt`, kart kapsayıcısı `.film`.
- Film sayfasında sağlayıcı seçimi: `.part-item[data-name]`.
- Sağlayıcı seçilince `#play-video` tıklanır ve oynatıcı `#plx iframe[data-src]` içine eklenir.
- Uygulama bu sırayı korur; çerçeveyi tek başına açmaz. Tek başına açmak, Rapidvid tarafında 403 üretebilir.
- Film desteklenir. Site tarafında dizi kataloğu gözlenmediği için bu kaynak “film” olarak kalır.

## HD Film Cehennemi

- Film kartı: `a.poster`.
- Dizi kartı: `a.mini-poster`, URL yolu `/dizi/...`.
- Film sayfasındaki gerçek kaynak düğmesi/çerçevesi sayfaya göre değiştiğinden uygulama yalnız gözlenmiş oynatma düğmelerini ve çerçeveleri listeler.
- 6 Eylül 2026 kontrolünde ana sayfadaki örnek `/dizi/from-42/` ve `/yabancidizi/` yolları sitenin kendi 404 sayfasına gitti. Bu, uygulamanın kaynak çözememesi değildir. Uygulama bu durumu açık hata olarak göstermelidir; 404 bağlantıya sahte bölüm/kaynak üretmez.

## Dizilla

- Dizi kartları: `/dizi/...` yolları.
- Dizi ana sayfasında sezonlar ayrı URL’lerdir: ör. `/silo-1-sezon`, `/silo-2-sezon`.
- Her sezon belgesindeki bölüm bağlantıları `a.text.block` ve URL biçimi `...-N-sezon-M-bolum`dur.
- Bölüm sayfası doğrudan `iframe` içinde yayın sağlayıcısı verir. Kontrol edilen Silo bölümü `https://four.pichive.online/iframe.php?...` kullanıyordu.
- Bu sağlayıcı erişimi Cloudflare tarafından engellenirse uygulama bunu “site sağlayıcısı engelledi” olarak gösterir. Uygulama sahte bir medya URL’si üretmez ve korumayı aşmaya çalışmaz.

## DiziBOX

- Dizi kartı: URL yolu `/diziler/...`.
- Sezon bağlantısı: `a.btn`, URL yolu `/dizi/<slug>/<N>-sezon-.../`.
- Sezon belgesindeki bölüm listesi: `a.season-episode`.
- Bölüm sayfası, aynı alan adındaki `player/king/king.php?...` çerçevesini kullanır.

## Uygulama davranışı

- Normal ekran tamamen Android TV arayüzüdür. WebView yalnızca sayfayı arka planda okuyup siteye özgü veriyi almak için kullanılır.
- “Site görünümü” yalnızca hata teşhisi/fallback içindir. Site WebView içinde beyaz veya engel sayfası verirse bu, ana uygulama ekranını değiştirmez.
- Dahili oynatıcı sadece gerçek `.m3u8` veya `.mp4` isteği alındığında açılır. Sağlayıcının DRM, Cloudflare veya oturum doğrulaması uyguladığı yayınlarda uygulama varsayım yapmaz.
