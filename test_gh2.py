import urllib.request
import urllib.parse
import json

queries = ["iptv spor m3u"]
for q in queries:
    enc = urllib.parse.quote(q + " pushed:>2026-03-16")
    url = f"https://api.github.com/search/repositories?q={enc}&sort=updated&order=desc&per_page=5"
    req = urllib.request.Request(url, headers={'User-Agent': 'Mozilla/5.0'})
    try:
        with urllib.request.urlopen(req) as response:
            data = json.loads(response.read().decode())
            for item in data.get('items', []):
                print(item['full_name'], item['default_branch'])
    except Exception as e:
        print(f"Error {q}: {e}")
