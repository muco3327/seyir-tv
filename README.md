# Seyir TV

Mi Box ve Android TV için, dört kaynağı yerel Android TV arayüzünde gösteren uygulama.

### Son düzeltme (v0.2.0)
- **Dizi Oynatma Düzeltildi:** Dizilla ve DiziBOX üzerindeki bölüm iframe ve video player tetikleyicileri düzeltildi; sezon/bölüm seçildiğinde video akışı otomatik yakalanıp doğrudan başlatılıyor.
- **Profesyonel Alt Kontrol Çubuğu:** Dahili oynatıcı kontrolleri tamamen alt panelde toplandı; ilerleme çubuğu, süre, ileri/geri sarma, ekran sığdırma modları, kalite/çözünürlük seçici, ses/altyazı seçici ve oynatma hızı eklendi.
- **Çözünürlük ve Kalite Seçimi:** Yayındaki tüm çözünürlükleri (1080p, 720p, 480p, 360p vb.) algılar ve anlık geçiş sağlar; varsayılan olarak "Otomatik" (Adaptive Bitrate) modu destekler.
- **Ekran Sığdırma (Resize):** Orijinal (FIT), Kırpma (ZOOM) ve Tam Ekran Uzatma (FILL) modları arasında geçiş imkanı.

## Kurulum

`dist/Seyir-TV-0.2.0.apk` dosyasını Mi Box'a aktarın ve açıp yükleyin. Uygulama TV'nin uygulamalar listesinde **Seyir TV** adıyla görünür. Eklenti URL'si, API anahtarı veya sunucu kurulumu istemez.

## Kullanım

- Üstten FullHD Filmizlesene, HD Film Cehennemi veya Dizilla'yı seçin.
- Kartları kumandanın yön tuşlarıyla gezin; OK ile detay açın.
- **Ara**, seçili kaynağın aramasını kullanır. Üç kaynakta eşzamanlı ortak arama henüz yoktur.
- Detay sayfasında varsa bölüm veya **İzle** kaynağını seçin.
- Oynatılabilir HLS/MP4 isteği yakalanırsa dahili oynatıcı açılır.
- Kaynak otomatik açılamazsa **Site oynatıcısını göster** ile uygulama içinde siteyi açabilirsiniz. Bu görünümün kumanda uyumu siteye bağlıdır.
- Dahili oynatıcıda kalite, ses ve altyazı menüleri kaynağın sunduğu parçaları listeler. Web sayfasındaki ayrı altyazı dosyalarının tamamı henüz çıkarılmıyor.
- Favoriler ve dahili oynatıcıdaki ilerleme yalnızca cihazda saklanır. Site oynatıcısında ilerleme kaydı desteklenmez.

## Doğrulama durumu

- Debug APK ve Android test APK'sı başarıyla derlendi.
- JVM medya güvenliği testleri ve sayfa yapısı testleri çalıştırılır.
- Android Lint: 0 hata, 22 uyarı. Uyarılar ağırlıklı olarak yerelleştirme, yeni Android sürümleri ve UI metinleriyle ilgilidir.
- Dört gerçek WebView/DOM fixture testi yazıldı ve test paketi derlendi; cihazda çalıştırılmadı.
- Geliştirme bilgisayarının normal tarayıcısında FullHD ve HD Film Cehennemi ana sayfaları açıldı, DOM kart yapıları incelendi. Dizilla ana sayfası HTTP 200 ile alındı.
- FullHD ve HD Film Cehennemi doğrudan HTTP istemcisine 403 döndürdüğü için katalog cihazın WebView motoruyla okunur.
- **Android emülatörü/Mi Box üzerinde uçtan uca katalog, arama ve gerçek video oynatma henüz doğrulanmadı. Üç kaynağın veya bütün içeriklerin çalıştığı iddia edilmez.** Emülatör durum sorgulamasının izin isteği reddedildiğinde cihaz testleri durduruldu.

## Mimari

- Java 17, Android Gradle Plugin 8.13.2, Gradle 8.13.
- Android 8.0+ (API 26), compile SDK 36, target SDK 35.
- Media3 1.8.0: HLS/MP4, kaynakta mevcut ses/altyazı/kalite parçaları.
- `SiteEngine`: WebView içinde gerçek sayfayı yükler; yerel `catalog.js` ve `detail.js` ile DOM okur. Dış sayfalara bir JavaScript/native köprüsü açılmaz.
- `Source`: Kullanıcının verdiği dört HTTPS adresi.
- `SITE_SUPPORT.md`: Her sağlayıcının katalog, sezon ve oynatıcı yapısı ile canlı kontrol notları.
- `Library`: cihaz içi favoriler ve izleme geçmişi.
- Ücretli API, bulut servisi veya aylık sunucu bağımlılığı yoktur.

Kaynak sitelerin adresleri, HTML yapıları ve video sunucuları değiştiğinde sağlayıcı kodlarının güncellenmesi gerekir. Mevcut sürüm yalnızca gözlenen `.m3u8`/`.mp4` medya isteklerini dahili oynatıcıya aktarır; DRM, erişim doğrulaması veya her özel oynatıcı için çözüm sağlamaz.

## Derleme

Android Studio ile bu klasörü açabilirsiniz. Android SDK 36 ve JDK 17+ gerekir. Bu bilgisayardaki kurulu Android Studio/Gradle yollarını kullanan yardımcı komut:

```powershell
.\tools\build.ps1 -Check
```

Derleme çıktısı: `dist/Seyir-TV-0.2.0.apk`.
Android test paketi: `app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk`.

`tools/test-device.ps1`, yalnızca bu proje için `emulator-5580` ve `.local/avd/SeyirTest.avd` test ortamını kullanır; gerçek Mi Box'a bağlanmaz. Test cihazı başlatma/kurulum/ekran kaydı adımları için ilgili ortam izinleri gerekir.

APK, bu bilgisayarın Android debug anahtarıyla imzalanmıştır. Dağıtım sürümüne geçerken kalıcı imzalama anahtarı hazırlanmalıdır; mevcut anahtar değiştirilirse yerinde güncelleme yapılamaz.
