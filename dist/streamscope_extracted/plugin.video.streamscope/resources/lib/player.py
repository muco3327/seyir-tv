# -*- coding: utf-8 -*-
import time
import xbmc
import xbmcgui
from . import config, storage

class StreamScopePlayer(xbmc.Player):
    def __init__(self):
        super(StreamScopePlayer, self).__init__()
        self.channel = None
        self.sources = []
        self.current_idx = 0
        self.user_stopped = False
        self.retry_count = 0
        self.max_retries = config.get_setting_int("max_retries", 3)
        self.auto_failover = config.get_setting_bool("auto_failover", True)
        self.playback_started_time = 0

    def start_playback(self, channel, source_index=0):
        self.channel = channel
        self.sources = channel.get('sources', [])
        self.current_idx = source_index
        self.user_stopped = False
        self.retry_count = 0

        if not self.sources or self.current_idx >= len(self.sources):
            xbmcgui.Dialog().notification("StreamScope", "Kullanılabilir yayın kaynağı bulunamadı!", xbmcgui.NOTIFICATION_ERROR, 3000)
            return False

        storage.record_watch(channel['id'])
        return self._play_current_source()

    def _play_current_source(self):
        if self.current_idx >= len(self.sources):
            return False

        src = self.sources[self.current_idx]
        url = src['url']
        ua = src.get('user_agent') or config.get_setting_str("default_user_agent")
        ref = src.get('referer', '')

        # Build headers for Kodi player
        headers = []
        if ua:
            headers.append(f"User-Agent={ua}")
        if ref:
            headers.append(f"Referer={ref}")

        final_url = url
        if headers:
            final_url = f"{url}|{'&'.join(headers)}"

        listitem = xbmcgui.ListItem(self.channel.get('display_name', 'Kanal'))
        listitem.setInfo('video', {
            'title': self.channel.get('display_name', 'Kanal'),
            'genre': self.channel.get('group_title', 'Genel'),
            'plot': f"Kaynak: {src.get('source_name', 'Bilinmeyen')}\nToplam Kaynak: {len(self.sources)}"
        })

        if self.channel.get('logo_url'):
            listitem.setArt({
                'thumb': self.channel['logo_url'],
                'icon': self.channel['logo_url'],
                'poster': self.channel['logo_url']
            })

        # InputStream.Adaptive for HLS / DASH / MPD
        if config.get_setting_bool("use_inputstream", True):
            listitem.setProperty('inputstream', 'inputstream.adaptive')
            listitem.setProperty('inputstream.adaptive.manifest_type', 'hls')
            if headers:
                listitem.setProperty('inputstream.adaptive.stream_headers', '&'.join(headers))

        xbmc.log(f"[StreamScope] Oynatılıyor: '{self.channel.get('display_name')}' -> Kaynak {self.current_idx + 1}/{len(self.sources)} ({src.get('source_name')})", xbmc.LOGINFO)

        self.playback_started_time = time.time()
        self.play(final_url, listitem)
        return True

    def onAVStarted(self):
        # Playback successfully started and streaming
        self.retry_count = 0
        src = self.sources[self.current_idx] if self.current_idx < len(self.sources) else {}
        xbmc.log(f"[StreamScope] Yayın stabil: {self.channel.get('display_name')}", xbmc.LOGINFO)

    def onPlayBackError(self):
        xbmc.log(f"[StreamScope] Yayın hatası algılandı: {self.channel.get('display_name') if self.channel else ''}", xbmc.LOGWARNING)
        self._handle_stream_failure()

    def onPlayBackStopped(self):
        # Check if stopped prematurely (e.g. within 3 seconds of starting, or drop before user action)
        duration_played = time.time() - self.playback_started_time
        if not self.user_stopped and duration_played < 5.0 and self.channel:
            xbmc.log(f"[StreamScope] Beklenmedik kopma algılandı ({duration_played:.1f}sn sonra).", xbmc.LOGWARNING)
            self._handle_stream_failure()
        else:
            self.user_stopped = True

    def _handle_stream_failure(self):
        if not self.auto_failover or not self.channel or not self.sources:
            return

        # Check if there is another alternative source
        if self.current_idx + 1 < len(self.sources):
            self.current_idx += 1
            next_src = self.sources[self.current_idx]

            if config.get_setting_bool("show_notification", True):
                xbmcgui.Dialog().notification(
                    "StreamScope",
                    f"Kopma Algılandı! Yedek {self.current_idx + 1}/{len(self.sources)} açılıyor...",
                    xbmcgui.NOTIFICATION_WARNING,
                    3500
                )

            time.sleep(0.5)
            self._play_current_source()
        elif self.retry_count < self.max_retries:
            self.retry_count += 1
            self.current_idx = 0
            if config.get_setting_bool("show_notification", True):
                xbmcgui.Dialog().notification(
                    "StreamScope",
                    f"Yayın yenileniyor (Deneme {self.retry_count}/{self.max_retries})...",
                    xbmcgui.NOTIFICATION_INFO,
                    3000
                )
            time.sleep(1.0)
            self._play_current_source()
        else:
            xbmcgui.Dialog().notification(
                "StreamScope",
                "Tüm kaynaklar tükendi, yayın açılamadı!",
                xbmcgui.NOTIFICATION_ERROR,
                4000
            )

    def stop_manually(self):
        self.user_stopped = True
        self.stop()
