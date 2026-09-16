# -*- coding: utf-8 -*-
import urllib.request

url = "https://raw.githubusercontent.com/YoranYosipov/iptv-playlists/main/tr-az.m3u"
req = urllib.request.Request(url, headers={'User-Agent': 'Mozilla/5.0'})
with urllib.request.urlopen(req) as r:
    content = r.read().decode(errors='replace')

lines = content.split('\n')
sport_channels = []
name = None
logo = ""
group = ""
for line in lines:
    line = line.strip()
    if line.startswith("#EXTINF:"):
        comma = line.find(',')
        if comma != -1:
            name = line[comma+1:].strip()
        if 'tvg-logo="' in line:
            s = line.index('tvg-logo="') + 10
            e = line.index('"', s)
            logo = line[s:e]
        if 'group-title="' in line:
            s = line.index('group-title="') + 13
            e = line.index('"', s)
            group = line[s:e]
    elif not line.startswith("#") and name and (line.startswith("http://") or line.startswith("https://")):
        lname = name.lower()
        if any(kw in lname for kw in ["sport", "spor", "bein", "s sport", "ssport"]) or "sport" in group.lower():
            sport_channels.append((name, group, line, logo))
        name = None
        logo = ""

print("Total sport channels found:", len(sport_channels))
for n, g, u, l in sport_channels:
    print("  " + n + " [" + g + "] -> " + u[:70])