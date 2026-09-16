import urllib.request
import json
import time

def search_github(q):
    url = "https://api.github.com/search/repositories?q=" + urllib.parse.quote(q) + "&sort=updated&order=desc"
    req = urllib.request.Request(url, headers={'User-Agent': 'Mozilla/5.0'})
    try:
        with urllib.request.urlopen(req) as r:
            return json.loads(r.read().decode()).get("items", [])
    except Exception as e:
        print(f"Error for {q}: {e}")
        return []

items1 = search_github("iptv tr m3u")
time.sleep(2)
items2 = search_github("iptv turkey m3u")
time.sleep(2)
items3 = search_github("iptv spor m3u")

repos = set()
for item in items1 + items2 + items3:
    repos.add(item['full_name'])

print(f"Found {len(repos)} repos. Checking for premium channels...")

# We will just print the list of repos to see if there's a good one
for r in repos:
    print(r)
