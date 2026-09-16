import urllib.request
import urllib.parse
import json

queries = ["iptv turkey m3u", "turkish iptv m3u", "turkiye iptv m3u", "iptv spor m3u", "iptv bein m3u"]
for q in queries:
    enc = urllib.parse.quote(q + " pushed:>2026-03-16")
    url = f"https://api.github.com/search/repositories?q={enc}&sort=updated&order=desc&per_page=5"
    req = urllib.request.Request(url, headers={'User-Agent': 'Mozilla/5.0'})
    try:
        with urllib.request.urlopen(req) as response:
            data = json.loads(response.read().decode())
            print(f"{q}: {len(data.get('items', []))} items")
    except Exception as e:
        print(f"Error {q}: {e}")
