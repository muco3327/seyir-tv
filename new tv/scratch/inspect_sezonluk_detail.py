import urllib.request
import ssl
import re

ctx = ssl._create_unverified_context()
headers = {
    'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36',
    'Accept': 'text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8',
    'Accept-Language': 'tr-TR,tr;q=0.9,en-US;q=0.8,en;q=0.7'
}

req = urllib.request.Request("https://sezonlukdizi.cc/diziler/breaking-bad.html", headers=headers)
with urllib.request.urlopen(req, timeout=10, context=ctx) as resp:
    html = resp.read().decode('windows-1254', errors='ignore')

with open("scratch/breaking_bad_detail.html", "w", encoding="utf-8") as f:
    f.write(html)

print("Saved breaking_bad_detail.html len:", len(html))

# Inspect seasons and episodes
seasons = re.findall(r'<div[^>]*class="[^"]*season[^"]*"[^>]*>.*?</div>', html, re.DOTALL | re.I)
print("Seasons found:", len(seasons))

ep_links = re.findall(r'href="(/breaking-bad/\d+-sezon-\d+-bolum\.html)"', html)
print(f"Total episode links found: {len(ep_links)}, sample: {ep_links[:5]}")
