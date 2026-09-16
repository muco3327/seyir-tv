import urllib.request
import ssl
import re

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
    "https://www.fullhdfilmizlesene.org",
    "https://www.fullhdfilmizlesene.net",
    "https://www.fullhdfilmizlesene.tv",
    "https://fullhdfilmizlesene.tv",
    "https://www.filmmodu.org",
    "https://www.filmmodu1.cx",
    "https://filmmodu.cx",
    "https://www.filmmodu.net",
    "https://filmmodu.tv",
    "https://filmmodu.in",
    "https://www.filmmodu.in",
    "https://www.filmkovasi.org",
    "https://filmkovasi.net",
    "https://filmkovasi.com",
    "https://filmkovasi.top",
    "https://www.filmizlesene.pw",
    "https://filmizlesene.com",
    "https://720pizle.me",
    "https://720pizle.org",
    "https://www.720pizle.org",
    "https://jetfilmizle.org",
    "https://jetfilmizle.net",
    "https://jetfilmizle.co",
    "https://filmmakinesi.pw",
    "https://filmmakinesi.net",
    "https://filmmakinesi.co",
    "https://filmmakinesi.de",
    "https://www.filmmakinesi.net",
    "https://www.filmmodu11.org",
    "https://filmmodu12.org",
    "https://hdfilmcehennemi.cx",
    "https://recaptchatv.top",

    # Series
    "https://dizilla.now",
    "https://dizilla.club",
    "https://dizilla.top",
    "https://dizibox.pw",
    "https://dizibox.tv",
    "https://www.dizibox.tv",
    "https://dizibox.vip",
    "https://diziyo.org",
    "https://diziyo.vip",
    "https://diziyo1.vip",
    "https://yabancidizi.org",
    "https://yabancidizi.pw",
    "https://yabancidizi.co",
    "https://sezonlukdizi.org",
    "https://sezonlukdizi.net",
    "https://sezonlukdizi.pw",
    "https://sezonlukdizi.tv",
    "https://dizipal944.com",
    "https://dizipal945.com",
    "https://dizipal946.com",
    "https://diziwatch.net",
    "https://diziwatch.org",
    "https://diziwatch.tv",
    "https://diziwatch.co"
]

print("Testing candidates...")
for url in candidates:
    try:
        req = urllib.request.Request(url, headers=headers)
        with urllib.request.urlopen(req, timeout=5, context=ctx) as resp:
            furl = resp.geturl()
            status = resp.status
            content = resp.read(20480).decode('utf-8', errors='ignore')
            if "masak.hmb.gov.tr" in content or "ihbarweb.org.tr" in content or "engellenmistir" in content.lower():
                pass # blocked
            else:
                title_match = re.findall(r'<title>(.*?)</title>', content, re.I)
                title = title_match[0].strip() if title_match else ""
                print(f"[OK] {url} -> Final: {furl} | Title: {title[:40]} | Len: {len(content)}")
    except Exception as e:
        pass
