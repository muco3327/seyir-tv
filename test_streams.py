import urllib.request
import json

# Test 1: sports.json remote fetch
try:
    req = urllib.request.Request("https://raw.githubusercontent.com/muco3327/seyir-tv/main/sports.json", headers={'User-Agent': 'SeyirTV-Sports'})
    with urllib.request.urlopen(req) as r:
        data = json.loads(r.read().decode())
        print(f"Remote sports.json: {len(data)} channels")
except Exception as e:
    print(f"Remote sports.json ERROR: {e}")

# Test 2: Check if existing stream URLs work
test_urls = [
    "https://andro.2385437.xyz/checklist/androstreamlivebs1.m3u8",
    "https://biraz.1386503.xyz/checklist/androstreamlivebs1.m3u8",
    "https://bein-esp-xumo.amagi.tv/playlistR1080p.m3u8",
    "https://trt.daioncdn.net/trtspor/master.m3u8?app=web&platform=trtspor",
    "https://rnttwmjcin.turknet.ercdn.net/lcpmvefbyo/aspor/aspor.m3u8"
]

for u in test_urls:
    try:
        req = urllib.request.Request(u, headers={
            'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36',
            'Referer': 'https://taraftarium24.xyz/',
            'Origin': 'https://taraftarium24.xyz'
        })
        with urllib.request.urlopen(req, timeout=5) as r:
            data = r.read(512).decode(errors='replace')
            print(f"OK {r.getcode()} {u[:60]}... content={data[:80]}")
    except Exception as e:
        print(f"FAIL {u[:60]}... {e}")
