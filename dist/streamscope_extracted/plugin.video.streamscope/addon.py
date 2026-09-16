# -*- coding: utf-8 -*-
import sys
import re
import urllib.parse
import xbmc
import xbmcgui
import xbmcplugin
import xbmcaddon

from resources.lib import config, storage, parser, player, tester

ADDON = xbmcaddon.Addon("plugin.video.streamscope")
HANDLE = int(sys.argv[1]) if len(sys.argv) > 1 and sys.argv[1].isdigit() else -1
BASE_URL = sys.argv[0] if len(sys.argv) > 0 else "plugin://plugin.video.streamscope/"

ACTIVE_PLAYER = None

def build_url(params):
    return f"{BASE_URL}?{urllib.parse.urlencode(params)}"

def set_modern_view():
    # Attempt to set Wall / InfoWall / Poster view for modern look
    try:
        # 53 = Shift / InfoWall in Estuary, 500 = Thumbnail Wall
        xbmc.executebuiltin("Container.SetViewMode(53)")
    except Exception:
        pass

def show_main_menu():
    groups = storage.get_categories()
    if not groups:
        refresh_playlists(silent=True)

    items = [
        ("📁  [B]Kategoriler[/B] [COLOR deepskyblue](Gruplar)[/COLOR]", {"action": "categories"}, "DefaultFolder.png"),
        ("📺  [B]Tüm Kanallar[/B] [COLOR springgreen](Alfabetik)[/COLOR]", {"action": "all_channels"}, "DefaultMovies.png"),
        ("🔍  [B]Kanal Ara &amp; Keşfet[/B]", {"action": "search_menu"}, "DefaultAddonsSearch.png"),
        ("🔤  [B]A-Z Hızlı Dizin[/B]", {"action": "alphabet_menu"}, "DefaultGenre.png"),
        ("🕒  [B]Son İzlenenler[/B]", {"action": "recent"}, "DefaultYear.png"),
        ("🔄  [B]Listeleri Yenile[/B] [COLOR orange](Senkronize Et)[/COLOR]", {"action": "refresh"}, "DefaultAddonProgram.png"),
        ("⚙️  [B]Ayarlar[/B]", {"action": "settings"}, "DefaultAddonSettings.png")
    ]

    for title, params, icon in items:
        item = xbmcgui.ListItem(title)
        item.setArt({'icon': icon, 'thumb': icon})
        is_folder = params.get("action") not in ["settings", "refresh"]
        xbmcplugin.addDirectoryItem(HANDLE, build_url(params), item, isFolder=is_folder)

    xbmcplugin.setContent(HANDLE, 'files')
    xbmcplugin.endOfDirectory(HANDLE)

def get_category_icon(name):
    name_l = name.lower()
    if any(k in name_l for k in ["spor", "sport", "football", "futbol"]):
        return "⚽", "springgreen"
    if any(k in name_l for k in ["sinema", "movie", "film", "dizi", "series"]):
        return "🎬", "gold"
    if any(k in name_l for k in ["haber", "news"]):
        return "📰", "crimson"
    if any(k in name_l for k in ["belgesel", "doc"]):
        return "🦁", "coral"
    if any(k in name_l for k in ["cocuk", "çocuk", "kid", "anim"]):
        return "🧸", "pink"
    if any(k in name_l for k in ["muzik", "müzik", "music"]):
        return "🎵", "mediumpurple"
    if any(k in name_l for k in ["ulusal", "genel", "turk", "türk"]):
        return "🇹🇷", "deepskyblue"
    return "📺", "lightslategray"

def show_categories():
    groups = storage.get_categories()
    if not groups:
        refresh_playlists(silent=False)
        groups = storage.get_categories()

    for g in groups:
        emoji, color = get_category_icon(g['group'])
        title = f"{emoji}  [B]{g['group']}[/B]  [COLOR {color}]({g['count']} Kanal)[/COLOR]"
        item = xbmcgui.ListItem(title)
        item.setArt({'icon': 'DefaultFolder.png'})
        url = build_url({"action": "channels", "group": g['group']})
        xbmcplugin.addDirectoryItem(HANDLE, url, item, isFolder=True)

    xbmcplugin.setContent(HANDLE, 'files')
    xbmcplugin.endOfDirectory(HANDLE)

def detect_channel_quality(name):
    name_upper = name.upper()
    if any(k in name_upper for k in ["4K", "UHD", "2160P"]):
        return "4K UHD", "crimson", 3840, 2160
    if any(k in name_upper for k in ["FHD", "1080P", "FULL HD"]):
        return "FHD", "gold", 1920, 1080
    if any(k in name_upper for k in ["HD", "720P"]):
        return "HD", "springgreen", 1280, 720
    return "SD", "lightgray", 720, 576

def render_channel_items(channels):
    for ch in channels:
        name = ch['display_name']
        src_count = ch.get('source_count', 1)
        q_label, q_color, width, height = detect_channel_quality(name)

        # Modern Badges formatting
        src_badge = f" [COLOR deepskyblue]({src_count} Kaynak)[/COLOR]" if src_count > 1 else ""
        quality_badge = f" [COLOR {q_color}][B]{q_label}[/B][/COLOR]"
        title = f"[COLOR white]{name}[/COLOR]{quality_badge}{src_badge}"

        item = xbmcgui.ListItem(title)
        item.setInfo('video', {
            'title': name,
            'genre': ch.get('group_title', 'Genel'),
            'plot': f"StreamScope Çoklu Kaynak IPTV\nAktif Kaynak Sayısı: {src_count}\nKalite Seviyesi: {q_label}\nOtomatik Kopma Koruması: Aktif"
        })

        # Official Kodi media stream flags (1080p, H264, AAC)
        item.addStreamInfo('video', {
            'codec': 'h264',
            'width': width,
            'height': height,
            'aspect': 1.78
        })
        item.addStreamInfo('audio', {
            'codec': 'aac',
            'channels': 2,
            'language': 'tur'
        })

        # Logos and Fanart
        logo = ch.get('logo_url')
        if logo and (logo.startswith("http://") or logo.startswith("https://")):
            item.setArt({
                'thumb': logo,
                'icon': logo,
                'poster': logo,
                'clearlogo': logo,
                'fanart': ADDON.getAddonInfo('fanart')
            })
        else:
            item.setArt({
                'icon': 'DefaultVideo.png',
                'thumb': 'DefaultVideo.png',
                'fanart': ADDON.getAddonInfo('fanart')
            })

        item.setProperty('IsPlayable', 'false')

        # Modern Context menu (TV kumandası uzun basma / menü tuşu veya mobil sağ tık)
        context_menu = [
            ("⚡  Kaynak Seç...", f"RunPlugin({build_url({'action': 'select_source', 'id': ch['id']})})"),
            ("🩺  Kanalı Test Et", f"RunPlugin({build_url({'action': 'test_channel', 'id': ch['id']})})")
        ]
        item.addContextMenuItems(context_menu)

        url = build_url({"action": "play", "id": ch['id'], "source": 0})
        xbmcplugin.addDirectoryItem(HANDLE, url, item, isFolder=False)

    xbmcplugin.setContent(HANDLE, 'movies')
    xbmcplugin.endOfDirectory(HANDLE)
    set_modern_view()

def show_channels(group=None):
    channels = storage.get_channels(group=group)
    if not channels:
        refresh_playlists(silent=True)
        channels = storage.get_channels(group=group)
    render_channel_items(channels)

def show_all_channels():
    channels = storage.get_channels()
    if not channels:
        refresh_playlists(silent=True)
        channels = storage.get_channels()
    render_channel_items(channels)

def show_recent():
    channels = storage.get_recent_channels()
    if not channels:
        xbmcgui.Dialog().notification("StreamScope", "Henüz izleme geçmişi yok.", xbmcgui.NOTIFICATION_INFO, 2500)
    render_channel_items(channels)

def show_search_menu():
    history = storage.get_search_history(limit=8)

    items = [
        ("🔍  [B]Yeni Arama Yap...[/B]", {"action": "do_search"}, "DefaultAddonsSearch.png")
    ]

    for q in history:
        items.append((f"🕒  \"{q}\"", {"action": "run_search", "query": q}, "DefaultYear.png"))

    if history:
        items.append(("🗑️  [COLOR crimson]Arama Geçmişini Temizle[/COLOR]", {"action": "clear_history"}, "DefaultDelete.png"))

    for title, params, icon in items:
        item = xbmcgui.ListItem(title)
        item.setArt({'icon': icon})
        xbmcplugin.addDirectoryItem(HANDLE, build_url(params), item, isFolder=(params.get("action") not in ["clear_history"]))

    xbmcplugin.setContent(HANDLE, 'files')
    xbmcplugin.endOfDirectory(HANDLE)

def show_alphabet_menu():
    alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
    for letter in alphabet:
        title = f"🔤  [B][COLOR deepskyblue]{letter}[/COLOR][/B] ile Başlayan Kanallar"
        item = xbmcgui.ListItem(title)
        item.setArt({'icon': 'DefaultGenre.png'})
        url = build_url({"action": "by_letter", "letter": letter})
        xbmcplugin.addDirectoryItem(HANDLE, url, item, isFolder=True)

    xbmcplugin.setContent(HANDLE, 'files')
    xbmcplugin.endOfDirectory(HANDLE)

def show_channels_by_letter(letter):
    channels = storage.get_channels_by_letter(letter)
    if not channels:
        xbmcgui.Dialog().notification("StreamScope", f"'{letter}' ile başlayan kanal bulunamadı.", xbmcgui.NOTIFICATION_INFO, 2500)
    render_channel_items(channels)

def do_search():
    kb = xbmc.Keyboard("", "Kanal Adı Girin (Örn: TRT, Spor, Sinema)")
    kb.doModal()
    if kb.isConfirmed():
        query = kb.getText().strip()
        if query:
            storage.save_search_query(query)
            channels = storage.get_channels(search_query=query)
            if not channels:
                xbmcgui.Dialog().notification("StreamScope", f"'{query}' için kanal bulunamadı.", xbmcgui.NOTIFICATION_INFO, 3000)
            render_channel_items(channels)

def run_search(query):
    storage.save_search_query(query)
    channels = storage.get_channels(search_query=query)
    if not channels:
        xbmcgui.Dialog().notification("StreamScope", f"'{query}' için kanal bulunamadı.", xbmcgui.NOTIFICATION_INFO, 3000)
    render_channel_items(channels)

def clear_search_history():
    storage.clear_search_history()
    xbmcgui.Dialog().notification("StreamScope", "Arama geçmişi temizlendi.", xbmcgui.NOTIFICATION_INFO, 2500)
    xbmc.executebuiltin("Container.Refresh")

def select_source(channel_id):
    ch = storage.get_channel(channel_id)
    if not ch or not ch.get('sources'):
        xbmcgui.Dialog().ok("StreamScope", "Kaynak bulunamadı.")
        return

    options = []
    for idx, s in enumerate(ch['sources']):
        status = f" [{s.get('status', 'online').upper()}]" if s.get('status') else ""
        options.append(f"{idx + 1}. {s.get('source_name', 'Kaynak')}{status}")

    sel = xbmcgui.Dialog().select(f"{ch['display_name']} - Kaynak Seç", options)
    if sel != -1:
        play_channel(channel_id, sel)

def refresh_playlists(silent=False):
    sources = config.get_sources_config()
    if not sources:
        if not silent:
            xbmcgui.Dialog().ok(
                "StreamScope",
                "Etkin M3U kaynağı bulunamadı!\nLütfen eklenti ayarlarından M3U URL veya dosya yolu ekleyin."
            )
            ADDON.openSettings()
        return

    aggregated = parser.build_aggregated_channels(sources)
    storage.save_aggregated_channels(aggregated)

    total_channels = len(aggregated)
    total_sources = sum(len(c.get('sources', [])) for c in aggregated.values())
    if not silent:
        xbmcgui.Dialog().ok(
            "StreamScope",
            f"Senkronizasyon Tamamlandı!\n\nToplam Benzersiz Kanal: {total_channels}\nToplam Kaynak Linki: {total_sources}"
        )
    xbmc.executebuiltin("Container.Refresh")

def play_channel(channel_id, source_index=0):
    global ACTIVE_PLAYER
    ch = storage.get_channel(channel_id)
    if not ch:
        xbmcgui.Dialog().notification("StreamScope", "Kanal bilgisi okunamadı!", xbmcgui.NOTIFICATION_ERROR, 3000)
        return

    ACTIVE_PLAYER = player.StreamScopePlayer()
    ACTIVE_PLAYER.start_playback(ch, source_index=int(source_index))

def router():
    params = dict(urllib.parse.parse_qsl(sys.argv[2][1:])) if len(sys.argv) > 2 and sys.argv[2] else {}
    action = params.get("action")

    if action is None:
        show_main_menu()
    elif action == "categories":
        show_categories()
    elif action == "channels":
        show_channels(params.get("group"))
    elif action == "all_channels":
        show_all_channels()
    elif action == "search_menu":
        show_search_menu()
    elif action == "do_search":
        do_search()
    elif action == "run_search":
        run_search(params.get("query", ""))
    elif action == "clear_history":
        clear_search_history()
    elif action == "alphabet_menu":
        show_alphabet_menu()
    elif action == "by_letter":
        show_channels_by_letter(params.get("letter", "A"))
    elif action == "recent":
        show_recent()
    elif action == "play":
        play_channel(params.get("id"), params.get("source", 0))
    elif action == "select_source":
        select_source(params.get("id"))
    elif action == "test_channel":
        tester.test_channel(params.get("id"))
    elif action == "refresh":
        refresh_playlists(silent=False)
    elif action == "settings":
        ADDON.openSettings()

if __name__ == "__main__":
    router()
