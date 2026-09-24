# Seyir TV — 2026-09-23 düzeltmeleri

## 0.5.23 — oynatıcı araç çubuğu sadeleştirmesi

- Oynatıcıdaki alt araç satırında geri sar, ileri sar, boyut, kalite, ses dili, altyazı ve hız düğmeleri görsel olarak gizlendi. Temel oynat/duraklat düğmesi ile ilerleme çubuğu korunur.
- Düğmelerin Java bağları ve oynatma mantığı korunur; gizli kontroller gerektiğinde kaynak koddan yeniden görünür yapılabilir. PlayerActivity davranış kodu değiştirilmedi, yalnızca `activity_player.xml` görünürlüğü güncellendi.

## 0.5.22 — diğer Türkiye beIN Sports alternatifleri

- Yalnızca beIN Sport(s) 1–5, Max 1–2 ve Haber adları için denetlenen yerel grup/ülke işaretleri ile Atom, Zeus, CDN, MAHSUN, forestgump, TV247, ace, tal, talip ve tul son ekleri tanınır. Yabancı ülke/dil reddi önce uygulanır.
- Atom Spor kategorisindeki beIN adlarına ATOM eklenir; diğer kanal ailelerine dokunulmaz. Sağlayıcı birleştirme anahtarı genişletildi; URL ve istek başlıkları aynı kaldı.
- Güncel mahsun listesinde 115 beIN Sports kaydının 74'ü kabul, 41 yabancı kayıt ret. Önceki 19 kabulden 55 kaynak kaydı artış var; bu ayrı kanal sayısı değildir.
- Atom'un iki beIN 1 kaynağı, listedeki kendi başlıklarıyla uygulama dışında HTTP 403 döndürüyor; bu güncelleme sunucu erişimini düzeltmez.
- Hedefli filtre/kalite testleri ve release APK derlemesi çalıştırıldı. PlayerActivity değiştirilmedi.

## 0.5.21 — Atom ve Zeus listeleme

- Güncel kadirsener1/mahsun playlist içinde BEIN SPORTS 1-ATOM ve BeIN Sport 1-ZEUS kayıtları mevcut; grupları Spor, diğer Zeus kayıtlarınınki Spor-Neon. Önceki filtre sağlayıcı son ekleri ve eksik TR etiketi nedeniyle bunları eliyordu.
- Yabancı ülke/dil ve yetişkin kontrollerinden sonra yalnızca beIN Sport(s) 1–5 Atom/Zeus + Spor/Spor-Neon eşleşmesine izin verilir. Genel yabancı beIN filtresi gevşetilmez.
- ChannelQuality.groupKey sağlayıcı son ekini korur; SportsManager ad temizliği Atom/Zeus son ekini silse bile kaynaklar birbirine ve etiketsiz kanala birleşmez. PlayerActivity ve URL/başlık işleme değiştirilmedi.
- 7 hedefli ChannelFilter/ChannelQuality testi ve release derleme başarılı. Yayınların oynatılabilirliği bu listeleme düzeltmesiyle garanti edilmez; canlı yayın testi yapılmadı.

## 0.5.20 — simgeler, kartlar ve boş durumlar

- HomeIcon yalnızca ana ekrana ait çizgi simgelerini çizer; arama, ayarlar, filtre, favori, kapatma ve kalite seçenekleri aynı çizgi stilindedir. Erişilebilir düğme açıklamaları korunur.
- Logo kutusu 132×90 dp, gri arka plan ve ortalanmış kanal adı kullanılır. Odakta başlık aydınlanır; açık kart mint çerçeveyle seçili kalır.
- Kalite seçenekleri 150 dp düğmeler halinde, ekran genişliğine göre satırlara bölünür. Önceki kırpılma düzeltmesi korunur.
- Yükleme sırasında iki satır statik kart yer tutucusu gösterilir; veri geldiğinde 180 ms geçiş uygulanır. Boş favorilerde yıldız ve Kanal ekle eylemi bulunur.
- Release derleme ve lintVital başarılı. Oynatıcı/kaynak dosyaları SHA-256 karşılaştırması ve play metodu metin karşılaştırmasıyla değişmeden doğrulandı. Cihaz üzerinde görsel doğrulama yapılmadı.

## 0.5.19 — kalite düğmesi kırpılması

- HomeStyle satır/sütunlarının çocuk kırpması kapatıldı. Metin düğmeleri odakta büyütülmez; beyaz çerçeve korunur. Kart büyüme animasyonu devam eder.
- Kalite seçeneklerinde sabit 46 dp yükseklik yerine en az 48 dp ve içeriğe göre yükseklik kullanılır. İki satır metin ve çerçeve çevresinde boşluk sağlanır.
- Oynatıcı ve kaynak dosyaları değiştirilmedi; önceki SHA-256 imzaları korundu.

## 0.5.18 — ana ekran görsel yenilemesi (2026-09-24)

- Aktif ana ekran TvHomeActivity; görünüm yardımcıları HomeStyle. Üstte Favoriler/Kanallar/Ara sekmeleri ve Ayarlar simgesi, başlığın yanında kategori filtresi bulunur. Üst alan sayfayla kayar.
- Daha geniş logo kartları, tek satır kanal adı, küçük favori rozeti ve beyaz odak çerçevesi kullanılır. Kalite etiketleri sadece açılır panelde görünür. Geçişler 160–180 ms, bulanıklık kullanılmaz.
- Odak dinleyicisi stil animasyonunu koruyarak seçili kontrolü kaydeder. Yoğunluğa göre sütun sayısı hesaplaması düzeltildi.
- PlayerActivity, SportsManager, activity_player.xml ve TvHomeActivity.play metodu değiştirilmedi. Önceki koruma imzalarıyla karşılaştırıldı. ChannelPresentation ve yayın URL listelerine dokunulmadı.
- Önceki arayüz backup_ui_0517 altında yerel olarak saklandı. Son izlenenler eklenmedi.
- Sürüm 0.5.18 / 61. Release derleme/lint kontrolü yapıldı; bağlı yerel emülatör offline olduğundan cihaz üstü görsel doğrulama yapılamadı.

## 0.5.16 — kalite bağlantılarının ayrılması (2026-09-24)

- Düzenlemeden önce app klasörü, yapılandırmalar, sürüm ve notlar backup_quality_20260924-092405 klasörüne kopyalandı. Yedek Git'e eklenmedi.
- ChannelQuality kalite etiketini birleştirme anahtarına ekler. UHD, HD, SD, açık 1080p/720p ve belirsiz kalite birbirine karışmaz. Kaynaklar son ekleneni başa taşıma yerine liste sırasını korur; yedekler aynı kalite etiketindeki kayıtlar arasındadır.
- PlayerActivity kalite düğmesi seçilmesi istenen çözünürlük yerine oynatılan gerçek yüksekliği gösterir. Aynı yükseklikteki parçalar kalite menüsünde silinmez; bildirilen bit hızı ve fps görünür. Yayın bilgisi diyalogu gerçek çözünürlüğü ve kaynak tarafından bildirilen ortalama bit hızını gösterir.
- 22 hedefli JUnit testi başarılı. Kaynak etiketinin gerçekten UHD olduğunu garanti etmez; düşük bit hızlı veya upscale edilmiş kaynağı uygulama düzeltemez. Mi Box görüntü testi yapılmadı.
- Sürüm 0.5.16 / 59.

## 0.5.15 — Türkiye kanal filtresi (2026-09-24)

- ChannelFilter, JSON başlangıç listesine ve GitHub M3U kayıtlarına birleştirmeden önce uygulanır; yabancı URL'ler Türk kanalın yedeklerine karışmaz.
- İzin listesi Türkiye ulusal kanalları, Türkiye spor markaları ve sinema kanallarını kapsar. TV4 ve belirtilen yerel kanallar kabul edilmez. General kategorisi izin vermez. beIN Sports/Connect için TR/Türkiye etiketleri gerekir; yabancı dil/ülke etiketi varsa reddedilir.
- Filtre ad ve kategori metadatasına dayanır; video ses dilini analiz etmez. Yanlış etiketli yayınların dili bu yöntemle doğrulanamaz. Bilinmeyen isimler ihtiyatlı olarak elenir; izin listesi ChannelFilter.java içinde bakımı yapılabilir.
- Kullanıcının yerel kanal örnekleri, ulusal kanallar, yabancı/Türk beIN ve sinema için 4 regresyon testi geçti. Sürüm 0.5.15 / 58. Oynatıcı kodu değiştirilmedi.

## 0.5.14 — kompakt başlık

- Logo ve altı işlem tek 40 dp satıra alındı. Başlık ve durum/kanal sayısı yan yana yerleştirildi. Dikey boşluklar azaltıldı; üst alan sayfayla kaymaya devam eder.
- Değişiklik MainActivity üst alanı ile sınırlı; oynatma ve güncelleme motoru aynı. Sürüm 0.5.14 / 57.

## 0.5.13 — üst gezinme alanı

- Bilgi düğmesi kaldırıldı. Marka, Yenile ve Güncelle üst satırda; Tüm kanallar, Ara, Kategoriler ve Favoriler eşit genişlikte ikinci satırda yer alır. Ayrı sağa itilmiş kategori düğmesi ve yatay araç çubuğu kaldırıldı.
- Başlık ve durum satırı gezinmenin altında kalır; tüm alan sayfayla kayar. Kanal odaklama, oynatıcı, kaynak motoru ve API güncelleme denetimi değiştirilmedi.
- Sürüm 0.5.13 / 56, sürüme özel GitHub APK ve version.json ile yayımlanır.

## 0.5.12 — GitHub API üzerinden güncel sürüm bilgisi

- 0.5.11 yayınından hemen sonra raw dosya adresi benzersiz query ile bile eski 0.5.10 bilgisini döndürdü. Aynı anda contents API, raw+json Accept başlığıyla 0.5.11 bilgisini doğru döndürdü.
- AppUpdater sürüm bilgisini artık GitHub contents API üzerinden ister. APK indirmesi sürüme özel raw bağlantısını kullanmaya devam eder. API hatası/rate-limit, güncel diye gizlenmek yerine manuel kontrolde hata olarak gösterilir.
- Son sürüm 0.5.12 / 55. Kurulu eski uygulamanın bu kodu alabilmesi için bir defalık elle kurulum gerekebilir.

## 0.5.11 — uygulama içi güncelleme önbelleği

- Kullanıcı 0.5.9'un güncel gösterildiğini bildirdi. GitHub yanıtında max-age=300 ve cache HIT gözlendi; cihazdaki yanıt görülmediği için CDN önbelleği olası nedendir.
- AppUpdater her sürüm denetimine benzersiz check parametresi ve no-cache başlıkları ekler. Eksik/geçersiz versionCode güncel kabul edilmez; sunucu sürümü kurulu sürümden eskiyse açıkça gösterilir. Denetimler paylaşılan yürütücü kullanır.
- Sürüm 0.5.11 / 54; oynatıcı ve kaynak yükleme kodları değişmedi. Eski kurulumun yeni denetim kodunu alması için gerekirse bir defalık elle APK kurulumu gerekir.

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
