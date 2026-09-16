# -*- coding: utf-8 -*-
import time
import urllib.request
import ssl
import xbmc
import xbmcgui
from . import config, storage

def test_source(url, user_agent="", referer="", timeout=5):
    headers = {
        'User-Agent': user_agent or config.get_setting_str("default_user_agent")
    }
    if referer:
        headers['Referer'] = referer

    start = time.time()
    ctx = ssl.create_default_context()
    ctx.check_hostname = False
    ctx.verify_mode = ssl.CERT_NONE

    try:
        req = urllib.request.Request(url, headers=headers, method='HEAD')
        with urllib.request.urlopen(req, timeout=timeout, context=ctx) as resp:
            latency = int((time.time() - start) * 1000)
            return True, latency, resp.status
    except Exception:
        pass

    try:
        headers['Range'] = 'bytes=0-1024'
        req = urllib.request.Request(url, headers=headers)
        with urllib.request.urlopen(req, timeout=timeout, context=ctx) as resp:
            latency = int((time.time() - start) * 1000)
            return True, latency, resp.status
    except Exception:
        latency = int((time.time() - start) * 1000)
        return False, latency, 0

def test_channel(channel_id):
    ch = storage.get_channel(channel_id)
    if not ch or not ch.get('sources'):
        xbmcgui.Dialog().ok("StreamScope", "Test edilecek kaynak bulunamadı.")
        return

    lines = [f"[B]{ch['display_name']}[/B] ({len(ch['sources'])} Kaynak):"]
    for idx, src in enumerate(ch['sources']):
        is_ok, latency, code = test_source(src['url'], src.get('user_agent'), src.get('referer'))
        status_str = f"[COLOR green]ONLINE[/COLOR] ({latency}ms)" if is_ok else "[COLOR red]OFFLINE[/COLOR]"
        lines.append(f"• Kaynak {idx + 1} ({src['source_name']}): {status_str}")
        storage.update_source_status(src['id'], 'online' if is_ok else 'offline', latency)

    xbmcgui.Dialog().textviewer("StreamScope - Kanal Test Sonucu", "\n\n".join(lines))
