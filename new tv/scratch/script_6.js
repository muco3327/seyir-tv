
/**
 * SetPlayer kurulumu.
 *
 * Oynatıcının kendisi assets/player/player.js içinde; burada yalnız
 * yapılandırma veriliyor ve sayaç/tema köprüsü bağlanıyor.
 */
(function () {
	'use strict';

	var C    = window.FSP;
	var base = document.getElementById('playerbase');

	function fail(text) {
		base.className = 'fsp-message';
		base.textContent = text;
	}

	/**
	 * ÖNYÜKLEMENİN TAMAMI KORUMANIN ARKASINDA.
	 *
	 * `SPG.hazir()` geri çağrıyı YALNIZ geliştirici araçları kapalıyken
	 * çalıştırıyor. Açıkken hiç çalıştırmıyor — yani `new SetPlayer(...)`
	 * kurulmuyor, `src` atanmıyor ve manifest isteği HİÇ ÇIKMIYOR.
	 *
	 * KORUMANIN ASIL DAYANAĞI BU SATIR. "Yakala, sonra sayfadan kaç" tek başına
	 * yetersiz bir plan: sayfadan çıkmak Chrome'un ağ kaydını yalnız ÜST SEVİYE
	 * gezinmede temizliyor, oynatıcı ise çoğunlukla iframe içinde açılıyor ve
	 * iframe'in kendi gezinmesi kaydı temizlemiyor. Gizlenecek kaydın hiç
	 * doğmaması, doğduktan sonra silinmeye çalışılmasından çok daha sağlam.
	 */
	window.SPG.hazir(function () {

	if (typeof window.SetPlayer !== 'function') { fail('Oynatıcı yüklenemedi.'); return; }

	base.innerHTML = '';

	var p = new SetPlayer(base, {
		src:       C.stream,
		title:     C.title,
		poster:    C.poster,
		tracks:    C.tracks || [],
		buffer:    C.buffer,
		autostart: C.autostart,
		resume:    C.resume,
		forward:   C.forward,
		id:        C.id,
		renk:      C.renk,
		renk2:     C.renk2,
		zemin:     C.zemin,
		metin:     C.metin,

		/**
		 * SAHTE KALİTE — tek gerçek akış var.
		 *
		 * İçerik tek kalitede hazırlanıyor. Menüde hiç seçenek olmaması
		 * izleyicide "oynatıcı bozuk" izlenimi bırakıyor; iki satır göstermek
		 * menüyü dolduruyor. Akış değişmediği için ek trafik de yok.
		 */
		qualities: ['1080p', '720p'],

		/**
		 * VAST REKLAM.
		 *
		 * `tag` ön reklam, `tag2` içeriğin ortasında. Ayarlar paneldeki
		 * mevcut anahtarlardan geliyor — v1'den beri aynı adlar.
		 *
		 * VPAID DESTEKLENMİYOR: reklam verenin JavaScript'ini oynatıcının
		 * içinde çalıştırmak, o koda izleyicinin oturumuna ve manifest
		 * jetonuna erişim vermek demek. VPAID etiketi gelirse reklam
		 * atlanıyor ve içerik oynuyor.
		 */
		reklam:    C.reklam
	});

	window.SP = p;

	/**
	 * OYNATICI KORUMAYA KAYDEDİLİYOR.
	 *
	 * `devGuard` AYARI ARTIK BURADAN GEÇMİYOR: koruma sayfanın en başında,
	 * `Guard::inline()` ile kuruluyor ve açık/kapalı bilgisi oraya gidiyor.
	 * Ayarı bir de buradan yollamak, iki ayrı yerden yönetilen tek bir anahtar
	 * demek olurdu — biri açık biri kapalı kaldığında hata sessiz.
	 *
	 * Kaydın işi tetiklenme ANINDA: koruma bu nesne üzerinden `hls`i yok ediyor
	 * ve `video`nun kaynağını söküyor. Yalnız duraklatmak yetmiyor;
	 * duraklatılmış video tamponunu doldurmaya, yani segment adreslerini ağ
	 * sekmesine yazmaya devam ediyor.
	 */
	window.SPG.kayit(p);

	// --- İzlenme sayacı ---
	/**
	 * Oynatma GERÇEKTEN başladığında bir kez sayılıyor. Sayfayı açıp
	 * oynatmadan kapatan ziyaretçi izlenme sayılmasın.
	 */
	if (C.counters) {
		var counted = false;

		p.video.addEventListener('playing', function () {
			if (counted) { return; }
			counted = true;

			try {
				fetch('/count.php', {
					method: 'POST',
					headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
					body: 'id=' + encodeURIComponent(C.id),
					keepalive: true
				});
			} catch (e) {}
		});
	}

	// --- Üst pencere (tema) köprüsü ---
	/**
	 * Hedef '*' — iframe'i hangi alan adının açtığını bilmiyoruz ve bilmek de
	 * gerekmiyor: gönderilen veri yalnız oynatma durumu, gizli bir şey yok.
	 */
	function send(type) {
		try {
			parent.postMessage({
				fsp: 1, tip: type,
				konum:   p.video.currentTime || 0,
				sure:    p.video.duration || 0,
				durakli: p.video.paused
			}, '*');
		} catch (e) {}
	}

	['play', 'pause', 'seeked', 'ended'].forEach(function (ad) {
		p.video.addEventListener(ad, function () { send(ad === 'ended' ? 'ended' : ad); });
	});

	setInterval(function () { send('zaman'); }, 1000);

	window.addEventListener('message', function (e) {
		var m = e.data;
		if (!m || m.fsp !== 1 || !m.komut) { return; }

		switch (m.komut) {
			case 'oynat':  p.video.play(); break;
			case 'durdur': p.video.pause(); break;
			case 'ara':    p.video.currentTime = Number(m.deger) || 0; break;
			case 'ses':    p.video.volume = Math.max(0, Math.min(1, Number(m.deger))); break;
			case 'sessiz': p.video.muted = !!m.deger; break;
			case 'durum':  send('durum'); break;
			case 'kes':    p.video.pause(); break;
		}
	});

	send('kuruldu');

	});
}());
