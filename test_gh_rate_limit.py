import urllib.request
import json
import time

urls = [
    "https://api.github.com/search/repositories?q=iptv+turkey+m3u",
    "https://api.github.com/search/repositories?q=turkish+iptv+m3u",
    "https://api.github.com/search/repositories?q=turkiye+iptv+m3u",
    "https://api.github.com/search/repositories?q=iptv+spor+m3u",
    "https://api.github.com/search/repositories?q=iptv+bein+m3u"
]

for url in urls:
    req = urllib.request.Request(url, headers={'User-Agent': 'Mozilla/5.0'})
    try:
        with urllib.request.urlopen(req) as response:
            print(f"{url}: {response.getcode()}")
    except urllib.error.HTTPError as e:
        print(f"Error {url}: {e.code}")
    time.sleep(1)
