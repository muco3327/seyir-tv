import urllib.request
import ssl
import re
from concurrent.futures import ThreadPoolExecutor

ctx = ssl._create_unverified_context()
headers = {
    'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36',
    'Accept': 'text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8',
    'Accept-Language': 'tr-TR,tr;q=0.9,en-US;q=0.8,en;q=0.7'
}

candidates = [
    # FilmModu mirrors
    "https://filmmodu.org",
    "https://filmmodu.cc",
    "https://filmmodu.site",
    "https://filmmodu.top",
    "https://filmmodu1.org",
    "https://filmmodu2.org",
    "https://filmmodu3.org",
    "https://filmmodu5.org",
    "https://filmmodu10.org",
    "https://filmmodu12.org",
    "https://filmmodu15.org",
    "https://filmmodu20.org",

    # FullHDFilmizlesene mirrors
    "https://www.fullhdfilmizlesene.com",
    "https://www.fullhdfilmizlesene.pw",
    "https://www.fullhdfilmizlesene.cx",
    "https://fullhdfilmizlesene.cx",
    "https://www.fullhdfilmizlesene.net",
    "https://fullhdfilmizlesene.cc",
    "https://www.fullhdfilmizlesene.tv",
    "https://www.fullhdfilmizlesene.site",
    "https://www.fullhdfilmizlesene.top",
    "https://www.fullhdfilmizle.org",
    "https://fullhdfilmizle.pw",

    # JetFilm / FilmMakinesi / FilmKovasi
    "https://jetfilmizle.top",
    "https://jetfilmizle.ws",
    "https://jetfilmizle.tv",
    "https://jetfilmizle.de",
    "https://filmmakinesi.pw",
    "https://filmmakinesi.top",
    "https://filmmakinesi.org",
    "https://filmkovasi.org",
    "https://filmkovasi.com",
    "https://filmkovasi.pw",
    "https://filmkovasi.cc",

    # Dizilla / SezonlukDizi / YabanciDizi
    "https://sezonlukdizi.cc",
    "https://yabancidizi.news",
    "https://diziyo.vip",
    "https://diziyo.org",
    "https://dizibox.tv",
    "https://dizibox.top",
    "https://dizibox.pw",
    "https://dizilla.now",
    "https://dizilla.club"
]

def check(url):
    try:
        req = urllib.request.Request(url, headers=headers)
        with urllib.request.urlopen(req, timeout=4, context=ctx) as resp:
            furl = resp.geturl()
            content = resp.read(15000).decode('utf-8', errors='ignore')
            if "masak.hmb.gov.tr" in content or "ihbarweb.org.tr" in content or "engellenmistir" in content.lower():
                return None
            title_match = re.findall(r'<title>(.*?)</title>', content, re.I)
            title = title_match[0].strip() if title_match else "No Title"
            return f"[LIVE] {url} -> Final: {furl} | Title: {title[:40]}"
    except Exception as e:
        return None

with ThreadPoolExecutor(max_workers=25) as executor:
    results = executor.map(check, candidates)
    for r in results:
        if r:
            print(r, flush=True)
