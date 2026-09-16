import urllib.request
import json
url = "https://api.github.com/repos/hmtnvac-cpu/M3u-sports-/git/trees/main?recursive=1"
req = urllib.request.Request(url, headers={'User-Agent': 'Mozilla/5.0'})
try:
    with urllib.request.urlopen(req) as response:
        data = json.loads(response.read().decode())
        for f in data.get('tree', []):
            print(f['path'])
except Exception as e:
    print(f"Error: {e}")
