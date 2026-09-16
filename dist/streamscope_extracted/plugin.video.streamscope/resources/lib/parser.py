# -*- coding: utf-8 -*-
import re
import hashlib
import urllib.parse
import urllib.request
import ssl
import xbmc
import xbmcgui
from . import config

def clean_channel_name(name):
    if not name:
        return ""
    # Trim, lowercase, strip common prefixes/suffixes like (FHD), [HD], HEVC, TR:, etc.
    cleaned = re.sub(r"^[A-Z0-9]{1,4}[:|\-]\s*", "", name, flags=re.IGNORECASE)
    cleaned = re.sub(r"\[.*?\]|\(.*?\)", "", cleaned)
    cleaned = re.sub(r"\b(FHD|UHD|4K|HD|SD|HEVC|H\.265|1080p|720p)\b", "", cleaned, flags=re.IGNORECASE)
    cleaned = re.sub(r"[^a-zA-Z0-9\u00C0-\u017F]+", "", cleaned)
    return cleaned.lower().strip()

def parse_extinf_tags(line):
    tags = {}
    pattern = re.compile(r'([a-zA-Z0-9\-_]+)="([^"]*)"')
    for match in pattern.finditer(line):
        tags[match.group(1).lower()] = match.group(2)
    
    # Extract channel name after the last comma
    comma_idx = line.rfind(',')
    if comma_idx != -1:
        tags['name'] = line[comma_idx + 1:].strip()
    else:
        tags['name'] = tags.get('tvg-name', 'Bilinmeyen Kanal')
    return tags

def parse_m3u_content(content, source_name):
    channels = []
    lines = content.splitlines()
    current_tags = {}
    custom_ua = ""
    custom_ref = ""

    for line in lines:
        line = line.strip()
        if not line:
            continue

        if line.startswith("#EXTINF:"):
            current_tags = parse_extinf_tags(line)
        elif line.startswith("#EXTVLCOPT:"):
            if "http-user-agent=" in line:
                custom_ua = line.split("http-user-agent=", 1)[1].strip()
            elif "http-referrer=" in line:
                custom_ref = line.split("http-referrer=", 1)[1].strip()
        elif not line.startswith("#"):
            url = line
            ua = custom_ua
            ref = custom_ref

            # Handle pipe syntax url|User-Agent=...&Referer=...
            if "|" in url:
                stream_url, headers_part = url.split("|", 1)
                url = stream_url
                params = urllib.parse.parse_qs(headers_part)
                if "User-Agent" in params:
                    ua = params["User-Agent"][0]
                if "Referer" in params:
                    ref = params["Referer"][0]

            name = current_tags.get('name', 'Bilinmeyen Kanal')
            group = current_tags.get('group-title', 'Genel') or 'Genel'
            logo = current_tags.get('tvg-logo', '')
            tvg_id = current_tags.get('tvg-id', '')

            channels.append({
                'name': name,
                'group': group,
                'logo': logo,
                'tvg_id': tvg_id,
                'url': url,
                'ua': ua or config.get_setting_str("default_user_agent"),
                'ref': ref,
                'source_name': source_name
            })
            current_tags = {}
            custom_ua = ""
            custom_ref = ""

    return channels

def fetch_m3u(source_info):
    url = source_info['url']
    source_name = source_info['name']
    
    if url.startswith("http://") or url.startswith("https://"):
        headers = {'User-Agent': config.get_setting_str("default_user_agent")}
        timeout = config.get_setting_int("connection_timeout", 8)
        req = urllib.request.Request(url, headers=headers)
        ctx = ssl.create_default_context()
        ctx.check_hostname = False
        ctx.verify_mode = ssl.CERT_NONE
        with urllib.request.urlopen(req, timeout=timeout, context=ctx) as resp:
            raw_data = resp.read()
            text = raw_data.decode('utf-8', errors='ignore')
            return parse_m3u_content(text, source_name)
    else:
        # Local file path
        with open(url, 'r', encoding='utf-8', errors='ignore') as f:
            return parse_m3u_content(f.read(), source_name)

def build_aggregated_channels(sources_config):
    aggregated = {}
    p_dialog = xbmcgui.DialogProgressBG()
    p_dialog.create("StreamScope", "M3U Çalma Listeleri İndiriliyor...")

    total_sources = len(sources_config)
    for idx, src in enumerate(sources_config):
        pct = int(((idx) / max(1, total_sources)) * 100)
        p_dialog.update(pct, "StreamScope", f"{src['name']} ayrıştırılıyor...")
        try:
            items = fetch_m3u(src)
            for item in items:
                name = item['name']
                clean_name = clean_channel_name(name)
                # Primary key based on clean_name or tvg_id
                key_source = item['tvg_id'] if item.get('tvg_id') else clean_name
                ch_id = hashlib.md5(key_source.encode('utf-8')).hexdigest()

                if ch_id not in aggregated:
                    aggregated[ch_id] = {
                        'id': ch_id,
                        'clean_name': clean_name,
                        'display_name': name,
                        'group_title': item['group'],
                        'logo_url': item['logo'],
                        'tvg_id': item['tvg_id'],
                        'sources': []
                    }

                # Update missing logo or better display name
                if not aggregated[ch_id]['logo_url'] and item['logo']:
                    aggregated[ch_id]['logo_url'] = item['logo']

                # Append source
                aggregated[ch_id]['sources'].append({
                    'source_name': f"{src['name']} (Kaynak {len(aggregated[ch_id]['sources']) + 1})",
                    'url': item['url'],
                    'user_agent': item['ua'],
                    'referer': item['ref'],
                    'quality': 'HD'
                })
        except Exception as e:
            xbmc.log(f"[StreamScope] Hata ({src['name']}): {str(e)}", xbmc.LOGERROR)

    p_dialog.close()
    return aggregated
