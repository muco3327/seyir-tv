import urllib.request
import ssl
import re

ctx = ssl._create_unverified_context()
headers = {
    'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36',
    'Accept': 'text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8',
    'Accept-Language': 'tr-TR,tr;q=0.9,en-US;q=0.8,en;q=0.7'
}

def fetch(url):
    req = urllib.request.Request(url, headers=headers)
    with urllib.request.urlopen(req, timeout=10, context=ctx) as resp:
        return resp.read().decode('utf-8', errors='ignore')

print("--- 1. FILMMODU.CC ---")
try:
    h = fetch("https://filmmodu.cc/")
    # Find film cards
    cards = re.findall(r'<div[^>]*class="[^"]*movie[^"]*"[^>]*>.*?<a\s+href="([^"]+)".*?<img\s+[^>]*src="([^"]+)".*?alt="([^"]+)"', h, re.DOTALL)
    print("Filmmodu cards found via movie class:", len(cards))
    if not cards:
        # Check all movie links
        movie_links = re.findall(r'href="((?:https://filmmodu\.cc)?/[^"/]+-izle|/[^"/]+-film-izle|/film/[^"]+)"', h)
        print("Movie links count:", len(movie_links))
        if movie_links:
            sample_link = movie_links[0]
            if not sample_link.startswith('http'):
                sample_link = "https://filmmodu.cc" + sample_link
            print("Sample FilmModu movie link:", sample_link)
            detail = fetch(sample_link)
            with open("scratch/filmmodu_detail.html", "w", encoding="utf-8") as f:
                f.write(detail)
            print("Saved scratch/filmmodu_detail.html")
    with open("scratch/filmmodu_home.html", "w", encoding="utf-8") as f:
        f.write(h)
    print("Saved scratch/filmmodu_home.html")
except Exception as e:
    print("Filmmodu error:", e)

print("\n--- 2. SEZONLUKDIZI.CC ---")
try:
    h = fetch("https://sezonlukdizi.cc/")
    with open("scratch/sezonluk_home.html", "w", encoding="utf-8") as f:
        f.write(h)
    print("Saved scratch/sezonluk_home.html")
    # Find series links
    series_links = re.findall(r'href="((?:https://sezonlukdizi\.cc)?/dizi/[^"]+)"', h)
    print("Sezonluk series links:", len(series_links))
    if series_links:
        s_link = series_links[0]
        if not s_link.startswith('http'):
            s_link = "https://sezonlukdizi.cc" + s_link
        print("Sample Sezonluk series link:", s_link)
        s_detail = fetch(s_link)
        with open("scratch/sezonluk_detail.html", "w", encoding="utf-8") as f:
            f.write(s_detail)
        print("Saved scratch/sezonluk_detail.html")
except Exception as e:
    print("Sezonluk error:", e)

print("\n--- 3. JETFILMIZLE.TOP ---")
try:
    h = fetch("https://jetfilmizle.top/")
    with open("scratch/jetfilm_home.html", "w", encoding="utf-8") as f:
        f.write(h)
    print("Saved scratch/jetfilm_home.html")
    jet_links = re.findall(r'href="((?:https://jetfilmizle\.top)?/[^"/]+-izle[^"]*)"', h)
    print("Jetfilm links:", len(jet_links))
    if jet_links:
        j_link = jet_links[0]
        if not j_link.startswith('http'):
            j_link = "https://jetfilmizle.top" + j_link
        print("Sample Jetfilm link:", j_link)
        j_detail = fetch(j_link)
        with open("scratch/jetfilm_detail.html", "w", encoding="utf-8") as f:
            f.write(j_detail)
        print("Saved scratch/jetfilm_detail.html")
except Exception as e:
    print("Jetfilm error:", e)
