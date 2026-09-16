# -*- coding: utf-8 -*-
import xbmcaddon

ADDON = xbmcaddon.Addon("plugin.video.streamscope")

DEFAULT_SOURCES = [
    {"index": 1, "name": "LİSTE 1", "url": "https://raw.githubusercontent.com/kadirsener1/mahsun/main/playlist.m3u"},
    {"index": 2, "name": "LİSTE 2", "url": "https://raw.githubusercontent.com/omerdenizhan/IPTV-M3U/refs/heads/main/m3u/turkiye.m3u"},
    {"index": 3, "name": "LİSTE 3", "url": "https://raw.githubusercontent.com/omerdenizhan/IPTV-M3U/refs/heads/main/m3u/turkiye-iptv-org.m3u"},
    {"index": 4, "name": "LİSTE 4", "url": "https://raw.githubusercontent.com/myiptv2/iptv-playlist/main/kanallar.m3u"},
    {"index": 5, "name": "LİSTE 5", "url": "https://iptv-org.github.io/iptv/countries/tr.m3u"}
]

def get_setting_bool(setting_id, default=False):
    val = ADDON.getSetting(setting_id)
    if val == "":
        return default
    return val.lower() == "true"

def get_setting_int(setting_id, default=0):
    val = ADDON.getSetting(setting_id)
    try:
        return int(float(val))
    except (ValueError, TypeError):
        return default

def get_setting_str(setting_id, default=""):
    val = ADDON.getSetting(setting_id)
    return val if val != "" else default

def get_sources_config():
    sources = []
    for i in range(1, 6):
        default_def = DEFAULT_SOURCES[i-1] if i <= len(DEFAULT_SOURCES) else None
        def_enabled = True if default_def else False
        def_name = default_def["name"] if default_def else f"Kaynak {i}"
        def_url = default_def["url"] if default_def else ""

        enabled = get_setting_bool(f"src{i}_enabled", def_enabled)
        name = get_setting_str(f"src{i}_name", def_name)
        url = get_setting_str(f"src{i}_url", def_url).strip()
        if enabled and url:
            sources.append({
                "index": i,
                "name": name,
                "url": url
            })
    return sources if sources else DEFAULT_SOURCES
