import urllib.request
import re
import json

headers = {
    'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36',
    'Accept': 'text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8',
    'Accept-Language': 'tr-TR,tr;q=0.9,en-US;q=0.8,en;q=0.7'
}

import ssl
ctx = ssl._create_unverified_context()

def fetch(url):
    req = urllib.request.Request(url, headers=headers)
    with urllib.request.urlopen(req, timeout=10, context=ctx) as resp:
        return resp.read().decode('utf-8', errors='ignore')

print("=== 1. FULLHDFILMIZLESENE ===")
try:
    h1 = fetch("https://www.fullhdfilmizlesene.de/")
    cards = re.findall(r'<li[^>]*class="[^"]*item[^"]*"[^>]*>.*?<a\s+href="([^"]+)".*?<img\s+[^>]*src="([^"]+)".*?alt="([^"]+)"', h1, re.DOTALL)
    print(f"Cards found via regex: {len(cards)}")
    if not cards:
        # try another regex
        links = re.findall(r'href="(https://www\.fullhdfilmizlesene\.de/film/[^"]+)"', h1)
        print(f"Direct movie links: {len(links)}")
        if links:
            print("First link:", links[0])
            detail = fetch(links[0])
            with open("scratch/fullhd_sample.html", "w", encoding="utf-8") as f:
                f.write(detail)
            print("Saved fullhd_sample.html")
    else:
        print("First card:", cards[0])
        detail = fetch(cards[0][0])
        with open("scratch/fullhd_sample.html", "w", encoding="utf-8") as f:
            f.write(detail)
except Exception as e:
    print("Fullhd error:", e)

print("\n=== 2. FILMMODU ===")
try:
    h2 = fetch("https://www.filmmodu.io/")
    links2 = re.findall(r'href="(https://www\.filmmodu\.io/film/[^"]+|/film/[^"]+|https://www\.filmmodu\.io/[^"/]+-izle|/[^"/]+-izle)"', h2)
    print(f"Filmmodu links found: {len(links2)}")
    if links2:
        sample = links2[0] if links2[0].startswith('http') else "https://www.filmmodu.io" + links2[0]
        print("First link:", sample)
        detail2 = fetch(sample)
        with open("scratch/filmmodu_sample.html", "w", encoding="utf-8") as f:
            f.write(detail2)
        print("Saved filmmodu_sample.html")
except Exception as e:
    print("Filmmodu error:", e)

print("\n=== 3. SEZONLUKDIZI ===")
try:
    h3 = fetch("https://sezonlukdizi.vip/")
    links3 = re.findall(r'href="(https://sezonlukdizi\.vip/diziler/[^"]+|/diziler/[^"]+|https://sezonlukdizi\.vip/dizi/[^"]+|/dizi/[^"]+)"', h3)
    print(f"Sezonlukdizi links found: {len(links3)}")
    if links3:
        sample3 = links3[0] if links3[0].startswith('http') else "https://sezonlukdizi.vip" + links3[0]
        print("First link:", sample3)
        detail3 = fetch(sample3)
        with open("scratch/sezonluk_sample.html", "w", encoding="utf-8") as f:
            f.write(detail3)
        print("Saved sezonluk_sample.html")
except Exception as e:
    print("Sezonluk error:", e)
