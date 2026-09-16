import urllib.request
import ssl
import re

ctx = ssl._create_unverified_context()
headers = {
    'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36'
}

req = urllib.request.Request("https://sezonlukdizi.cc/js/site.min.js?v=0.82", headers=headers)
with urllib.request.urlopen(req, timeout=10, context=ctx) as resp:
    js = resp.read().decode('utf-8', errors='ignore')

# Search for ajax endpoints in site.min.js
endpoints = re.findall(r'[\'"][^\'"]*\.asp[^\'"]*[\'"]', js)
print("ASP Endpoints:", set(endpoints))

ajax_calls = re.findall(r'\$\.ajax\(\{url:[\'"]([^\'"]+)[\'"]', js)
print("Ajax URLs:", set(ajax_calls))

# Search for embed or player loading in js
player_funcs = [m for m in re.finditer(r'(?:embed|alternatif|player|video)', js, re.I)]
print("Matches found:", len(player_funcs))
for m in player_funcs[:10]:
    start = max(0, m.start() - 50)
    end = min(len(js), m.end() + 100)
    print("Snippet:", js[start:end])
