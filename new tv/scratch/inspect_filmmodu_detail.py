import urllib.request
import ssl
import re

ctx = ssl._create_unverified_context()
headers = {
    'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36',
    'Accept': 'text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8',
    'Accept-Language': 'tr-TR,tr;q=0.9,en-US;q=0.8,en;q=0.7'
}

req = urllib.request.Request("https://filmmodu.cc/film/captain-america-brave-new-world-izle/", headers=headers)
with urllib.request.urlopen(req, timeout=10, context=ctx) as resp:
    html = resp.read().decode('utf-8', errors='ignore')

with open("scratch/captain_america_detail.html", "w", encoding="utf-8") as f:
    f.write(html)

print("Saved captain_america_detail.html len:", len(html))

# Inspect iframes, player tags, sources
iframes = re.findall(r'<iframe[^>]+src="([^"]+)"', html)
print("Iframes:", iframes)

# Look for data-src or player-related attributes or scripts
player_scripts = re.findall(r'(?:source|player|video|vtt|subtitles|iframe|vidmoly|rapid|closeload)[^"\';\n]*', html, re.I)
print("Player script tokens:", player_scripts[:15])

# Look for language switchers or player tabs
tabs = re.findall(r'<div[^>]*class="[^"]*(?:player|source|dil|part|tab|server)[^"]*"[^>]*>.*?</div>', html, re.DOTALL | re.I)
print(f"Tabs count: {len(tabs)}")
