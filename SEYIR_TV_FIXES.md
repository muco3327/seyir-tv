# Seyir TV — 2026-09-23 düzeltmeleri

## 0.5.10 — canlı TV arayüzü ve GitHub yayını

- Devam et, Site görünümü ve tek kaynaklı gereksiz sekme satırı kaldırıldı. Film afişi oranındaki kartlar kanal logosuna uygun kısa kartlara çevrildi.
- Tüm kanallar düğmesi ve OK uzun bas ile favori ekleme/çıkarma eklendi. Eski film/dizi favorileri canlı kanal olarak gösterilmez.
- Bilgi ekranı, yükleme/boş liste metinleri ve Canlı TV başlığı güncellendi. Araç çubuğu sayfayla kayar.
- Bu sürümde PlayerActivity, SportsManager ve yayın yenileme sınıflarına yeni davranış değişikliği yapılmadı; önceki 0.5.9 düzeltmeleri yayıma dahildir.
- Sürüm 0.5.10 / 53. version.json, dist/Seyir-TV-0.5.10.apk adresini kullanır. Güncel README canlı TV kullanımını anlatır.
- Yayın güvenlik incelemesinin ardından imzalama parolaları Git dışındaki .local/signing.properties dosyasına taşındı; app/release.jks yerelde korunup Git takibinden çıkarıldı. Önceki Git geçmişi yeniden yazılmadı. CI için keystore ve parola secrets kurulumu gerekir.

## 0.5.9 — tamponu koruyarak kök yayın listesini yenileme

- Kullanıcı 0.5.8'de dönemsel siyah ekran/yükleme bildirdi. Önceki hata toparlanması oynatıcıyı release/initialize ile yeniden kurduğu için tamponu kaybediyordu. Ekran görüntüsü tek başına her beklemenin nedenini doğrulamaz.
- RefreshingPlaylistDataSource, kök HLS listesine ait öğrenilmiş yönlendirme adresleri yeniden istendiğinde ilk kanal adresini kullanır. Açılan yanıtın güncel getUri değeri korunur; göreli segmentler yeni oturumun taban adresi üzerinden ayrıştırılır.
- PlaylistRoute yalnızca ilk liste ve onun bilinen yönlendirmelerini eşleştirir. Alt kalite listeleri, video parçaları ve anahtar istekleri olduğu gibi geçirilir. Durum her oynatıcı/kaynak için ayrıdır.
- Liste yenilenirken ExoPlayer serbest bırakılmaz. 0.5.8'deki sınırlı hata toparlanması son çare olarak korunur.
- 16 hedefli JUnit testi ve assembleDebug başarılı. Gerçek Mi Box üzerinde kesintisiz görüntü/ses doğrulaması henüz yok. Yeni testler yönlendirme politikasını doğrular; Android DataSource entegrasyonunun cihaz testi değildir.
- Güncel APK: dist/Seyir-TV-0.5.9.apk, versionCode 52.

## 0.5.8 — oynatırken HTTP hatasından toparlanma

- Kullanıcı 30–40 saniye sonra IO_BAD_HTTP_STATUS bildirdi. İki örnek kanalın yönlendirme sonrası aynı liste URL'si tekrar kullanıldığında 23. saniyeden itibaren HTTP 403 gözlendi; 64. saniyeye kadar devam etti. Ayrı kontrolde ilk kanal URL'si her tur yeniden açıldığında 71 saniye boyunca 7 turda iki kanal da manifest 200 / segment 206 verdi. Bu HTTP kontrolleri kesintisiz video oynatma testi değildir.
- PlayerActivity, canlı yayın HTTP 403/404/408/410/429/5xx, ağ kesintisi/zaman aşımı ve canlı pencerenin gerisinde kalma hatalarında asıl kanal URL'sinden yeniden açar. Önceden tanınan biçim korunur; fazladan probe oturumu açılmaz.
- Canlı yayın her açılışta seekToDefaultPosition kullanır. Önceki canlı yayın konumu yeniden uygulanmaz ve çıkışta geçmişe yazılmaz.
- LiveRecovery: 1/2/4 saniyelik en fazla üç ardışık deneme, ardından yedek kaynak veya hata. 15 saniye sürekli oynatma deneme bütçesini yeniler; kısa READY/error döngüsü yenilemez. Yeniden deneme onStop/çıkışta iptal edilir.
- Manuel tekrar dene de oynatıcıyı asıl URL'den kurar. Hata ekranı mevcutsa gerçek HTTP kodunu içerir; URL veya token göstermez.
- 13 hedefli JUnit testi başarılı, assembleDebug başarılı. Tam test paketinin önceki Dizilla sınıfı sorunu devam ediyor. Mi Box üzerinde doğrulama yapılmadı.
- Güncel APK: dist/Seyir-TV-0.5.8.apk, versionCode 51.

## 0.5.7 — kullanıcı geri bildirimi sonrası düzeltme

0.5.6 düzeltmesinde yalnızca .m3u8 yolunu HLS saymak hataydı. Ekrandaki iki örnek (TR:BEINSPORTS 1 HQ ve TR:BEIN CONNECT 1 HD) .m3u URL'sinden gerçek HLS döndürüyor.

- Yeni StreamProbe, canlı yayın açılmadan önce HTTP durumunu, içerik türünü ve ilk 512 baytı arka planda kontrol ediyor. HLS, uzantısız veya .m3u adresinde de tanınıyor. HTML/JSON hatası ve desteklenmeyen DASH ayrı hata veriyor. TS/MP4 Media3 çıkarıcılarına bırakılıyor.
- Kontrol başarısızsa yedek kaynağa geçiliyor. Ekrandan çıkıldığında bekleyen kontrol sonucu uygulanmıyor.
- GitHub iç içe liste çözümlemesi #EXT-X- içeren gerçek HLS listesini son video parçasına indirmiyor.
- 9 JUnit testi başarılı. StreamProbe.inspect iki gerçek örnek üzerinde Java ile çalıştırıldı; ikisini de HLS olarak tanıdı.
- İki taze yayın listesinin yönlendirme sonrası adresinden çözülen video parçaları HTTP 206 / video/mp2t döndürdü (4096 bayt). Önceki eski liste/orijinal URL ile yapılan deneme 404 döndürdüğünden bu kontrol güncel liste ve nihai adresle tekrarlandı.
- APK derlemesi başarılı. Mi Box görüntü/ses testi yapılmadı; tüm kanal listesinin çalıştığı doğrulanmış değildir.
- Güncel APK: dist/Seyir-TV-0.5.7.apk; versionCode 50. Aşağıdaki bölüm önceki 0.5.6 çalışmasının tarihçesidir.

Kök proje: `tv.seyir.app`. `new tv` ayrı projedir. Güncel `Source.java` yalnızca SPORTS içerir.

## Değişiklikler

- `StreamRequest`: kaynak URL'si ve HTTP başlıklarını ayrı tutar; başlıklardaki &, + ve boşlukları kayıpsız taşır.
- `PlayerActivity`: tüm SPORTS kaynaklarını HLS olarak zorlamaz; yalnızca .m3u8 yoluna HLS türü verir, diğerleri Media3 algılamasına bırakılır. Yedek kaynak önceki kaynağın başlıklarını devralmaz. İlk kaynak yedek listesinde tekrar denenmez.
- `SportsManager`: JSON kaynaklarının başlıkları her URL'ye ayrı eklenir. M3U kaynakları URL ve başlıkları birlikte değerlendirilerek birleştirilir. Tek paylaşılan yürütücü kullanılır; eski taramalar yeni sonuçların üstüne yazamaz. Kanal sayısı oynatıldığı doğrulanmış kanal sayısı olarak sunulmaz.
- `GithubScanner`: liste kabulünde HEAD yerine GET ve EXTM3U başlığı kontrolü yapılır. Bu kontrol tek tek yayınların çalıştığı anlamına gelmez.
- `MainActivity`: başka ekrandayken kanal taraması durum metnini değiştirmez; oynatıcıya kaynak başlıkları URL üzerinden aktarılır.

## Doğrulama

- Android Studio JBR ile `:app:assembleDebug --offline --no-daemon --console=plain --no-problems-report`: başarılı.
- `StreamRequestTest`: JUnit 4.13.2 ile ayrı derlenip çalıştırıldı, 3/3 başarılı.
- Tam birim test paketi mevcut eski testler nedeniyle derlenemiyor: DizillaParserTest kaldırılmış DizillaParser sınıfına, MediaPolicyTest kaldırılmış DIZILLA/FULLHD enum değerlerine başvuruyor. Bunlar bu değişiklikte silinmedi veya devre dışı bırakılmadı.
- Mi Box cihaz testi ve canlı yayınların erişilebilirlik testi yapılmadı. Uzantısız HLS ve DASH kaynakları ayrıca cihaz/format desteği açısından doğrulanmalı.
- APK sürüm kimliği mevcut 0.5.6 / 49 olarak korundu. Dağıtım kopyası: `dist/Seyir-TV-0.5.6-duzeltme.apk`.

Çalışma ağacında bu işlem öncesinden bulunan değişiklikler korundu. Yeni bir agent önce bu dosyayı, ardından StreamRequest, PlayerActivity, SportsManager ve GithubScanner dosyalarını okuyabilir.
