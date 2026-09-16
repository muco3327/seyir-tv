
window.FSP = {
	stream:    "/manifests/aPzQJbjfjfOI/master.txt?verify=1788955200-jpleJr0cSMKMeL7QGdTJ6%2FfnAx4AVFanEK9oFM%2BkEOE%3D",
	type:      "hls",
	title:     "",
	poster:    "",
	autostart: false,
	resume:    true,
	forward:   true,
	quality:   true,
	counters:  true,

	/**
	 * OYNATICI KANITI — indirme yöneticilerine karşı.
	 *
	 * `player.js` bunu her manifest isteğine `X-Sp` başlığı olarak ekliyor;
	 * `manifest.php` de onu istiyor. IDM ve curl ADRESİ tekrar oynatıyor,
	 * başlığı değil — kanıt olmadan manifest 404.
	 *
	 * Ayrıntı ve dürüst sınır: `core/client.php` → `hasProof()`.
	 */

	/**
	 * `devGuard` BURADAN KALDIRILDI — koruma artık sayfanın başında kuruluyor.
	 *
	 * Ayar `Guard::inline($blockDevtools)` çağrısına gidiyor (bkz. `<head>`).
	 * Aynı anahtarı bir de buraya yazmak, tek bir ayarın iki ayrı yerden
	 * yönetilmesi demek olurdu ve ikisi ayrışınca hata sessiz kalırdı.
	 */

	/** Oynatıcı tamponu (saniye) — bkz. config.php FSP_BUFFER_SECONDS. */
	buffer:    90,
	/** Altyazı ve önizleme — manifest'ten değil, oynatıcı ayarından. */
	tracks:    [{"file":"https://srv.bahcelievler.cfd/hdfilm/Mayday.2026.1080p/subtitle/tur.vtt","label":"Türkçe","lang":"tur","forced":false,"kind":"captions"},{"file":"https://srv.beyoglu.cfd/hdfilm/Mayday.2026.1080p/subtitle/eng.vtt","label":"İngilizce","lang":"eng","forced":false,"kind":"captions"},{"file":"https://srv.esenler.cfd/hdfilm/Mayday.2026.1080p/subtitle/eng-forced.vtt","label":"İngilizce","lang":"eng","forced":true,"kind":"captions"},{"file":"https://srv.esenyurt.cfd/hdfilm/Mayday.2026.1080p/subtitle/tur-forced.vtt","label":"Türkçe","lang":"tur","forced":true,"kind":"captions"},{"file":"https://srv.pendik.cfd/hdfilm/Mayday.2026.1080p/preview/thumbnails.vtt","kind":"thumbnails"}],
	/**
	 * Oynatıcı vurgu renkleri — panelden yönetiliyor.
	 *
	 * Değerler `admin/pages/player.php` içinde `#rrggbb` biçimine süzülüyor;
	 * buraya ham gelen bir dize CSS değişkenine yazılacağı için o süzgeç
	 * güvenliğin parçası, kozmetik değil.
	 */
	renk:      "#ff2d2d",
	renk2:     "#ff7a18",
	zemin:     "#12121a",
	metin:     "#ffffff",
	reklam:    null,
	id:        "aPzQJbjfjfOI"};
