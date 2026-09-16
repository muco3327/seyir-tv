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
    # Movies
    "https://selcukflix.com",
    "https://hdfilmcehennemi.life",
    "https://hdfilmcehennemi.top",
    "https://hdfilmcehennemi.cx",
    "https://hdfilmcehennemi.net",
    "https://www.fullhdfilmizlesene.pw",
    "https://fullhdfilmizlesene.pw",
    "https://www.fullhdfilmizlesene.tv",
    "https://fullhdfilmizle.cx",
    "https://www.filmmodu.org",
    "https://www.filmmodu1.cx",
    "https://filmmodu.cx",
    "https://www.filmmodu11.org",
    "https://filmmodu12.org",
    "https://filmkovasi.org",
    "https://filmkovasi.net",
    "https://filmkovasi.top",
    "https://www.filmizlesene.pw",
    "https://720pizle.me",
    "https://720pizle.org",
    "https://jetfilmizle.org",
    "https://filmmakinesi.pw",
    "https://filmmakinesi.net",
    "https://filmmakinesi.de",
    "https://recaptchatv.top",

    # Series
    "https://dizilla.now",
    "https://dizilla.club",
    "https://dizilla.top",
    "https://dizibox.pw",
    "https://dizibox.tv",
    "https://dizibox.vip",
    "https://diziyo.org",
    "https://yabancidizi.org",
    "https://yabancidizi.pw",
    "https://sezonlukdizi.org",
    "https://sezonlukdizi.net",
    "https://dizipal944.com",
    "https://dizipal945.com",
    "https://diziwatch.net",
    "https://diziwatch.org"
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

with ThreadPoolExecutor(max_workers=20) as executor:
    results = executor.map(check, candidates)
    for r in results:
        if r:
            print(r, flush=True)
