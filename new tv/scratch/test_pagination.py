import urllib.request, ssl, re

ctx = ssl.create_default_context()
ctx.check_hostname = False
ctx.verify_mode = ssl.CERT_NONE

urls = [
    'https://www.hdfilmcehennemi.now/film-izle-1/',
    'https://www.hdfilmcehennemi.now/film-izle-1/page/2/',
    'https://www.hdfilmcehennemi.now/tur/aksiyon-izle-1/',
    'https://www.hdfilmcehennemi.now/tur/aksiyon-izle-1/page/2/',
    'https://www.hdfilmcehennemi.now/yil/2024/',
    'https://www.hdfilmcehennemi.now/yil/2024/page/2/'
]

for u in urls:
    req = urllib.request.Request(u, headers={'User-Agent': 'Mozilla/5.0'})
    try:
        with urllib.request.urlopen(req, context=ctx, timeout=10) as r:
            html = r.read().decode('utf-8', errors='ignore')
            cards = re.findall(r'<a[^>]+href=["\'](https://www\.hdfilmcehennemi\.now/film/[^"\']+/)["\']', html)
            posters = re.findall(r'<img[^>]+data-src=["\']([^"\']+)["\']', html)
            print(f"URL: {u} -> Status {r.status}, Cards: {len(cards)}, Posters: {len(posters)}")
    except Exception as e:
        print(f"URL: {u} -> FAILED: {e}")
