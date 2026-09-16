import urllib.request
import urllib.parse
import json

enc = urllib.parse.quote("iptv turkey m3u pushed:>2026-03-16")
url = f"https://api.github.com/search/repositories?q={enc}&sort=updated&order=desc&per_page=5"
req = urllib.request.Request(url, headers={'User-Agent': 'Mozilla/5.0'})
try:
    with urllib.request.urlopen(req) as response:
        data = json.loads(response.read().decode())
        for item in data.get('items', []):
            print(item['full_name'], item['default_branch'])
            # Fetch tree
            t_url = f"https://api.github.com/repos/{item['full_name']}/git/trees/{item['default_branch']}?recursive=1"
            t_req = urllib.request.Request(t_url, headers={'User-Agent': 'Mozilla/5.0'})
            with urllib.request.urlopen(t_req) as t_resp:
                t_data = json.loads(t_resp.read().decode())
                for f in t_data.get('tree', []):
                    if f['path'].endswith('.m3u') or f['path'].endswith('.m3u8'):
                        print("  Found:", f['path'])
except Exception as e:
    print(f"Error: {e}")
