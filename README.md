# Seyir TV

Android TV ve Mi Box için canlı TV kanal uygulaması. Güncel sürüm: **0.5.10** (53).

## Kurulum

[Son APK](https://raw.githubusercontent.com/muco3327/seyir-tv/main/dist/Seyir-TV-0.5.10.apk) dosyasını indirip mevcut uygulamanın üzerine kurun. Uygulamadaki **Güncelle** düğmesi de GitHub sürüm bilgisini kontrol eder.

## Kullanım

- **Ara**: kanal adı veya kategorisiyle arama.
- **Kategoriler**: kanal grubunu seçme.
- **Tüm kanallar**: ana listeye dönme.
- **OK**: seçili kanalı oynatma; **OK uzun bas**: favorilere ekleme/çıkarma.
- **Yenile**: GitHub listelerini tekrar tarama.
- Araç çubuğu sayfayla birlikte kayar.

Canlı TV, Media3 oynatıcısını kullanır. HLS kök listelerinin bilinen yönlendirmeleri ilk kanal adresinden yenilenir; yenileme sırasında oynatıcı ve tampon korunur. Sunucu erişimi, yayın kalitesi ve sürekliliği kaynak sağlayıcısına bağlıdır.

## Geliştirme ve doğrulama

Kök uygulama `tv.seyir.app`; `new tv` ayrı projedir. Android Studio JBR, Gradle 8.13 ve Android SDK 36 kullanılır. `./gradlew assembleRelease` APK oluşturur.

İmzalama için yerel `app/release.jks` ve Git'e eklenmeyen `.local/signing.properties` içindeki `storePassword` / `keyPassword` gerekir. CI ortamında aynı parolalar `SEYIR_STORE_PASSWORD` ve `SEYIR_KEY_PASSWORD` ortam değişkenlerinden alınabilir; keystore ayrıca güvenli şekilde sağlanmalıdır.

Bağlantı ve yönlendirme için 16 hedefli JUnit testi geçti. Önceki tam test paketinde kaldırılmış Dizilla sınıflarına başvuran testler vardır; tam paket şu anda derlenmez. Mi Box üzerinde güncel sürümün uçtan uca testi henüz yapılmadı.

Son değişiklikler ve doğrulama sınırları: [SEYIR_TV_FIXES.md](SEYIR_TV_FIXES.md).
