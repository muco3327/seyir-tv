import urllib.request, ssl, urllib.parse, re

ctx = ssl.create_default_context()
ctx.check_hostname = False
ctx.verify_mode = ssl.CERT_NONE

data = urllib.parse.urlencode({
    'action': 'load_page_content',
    'page': '5',
    'content_type': 'tr-altyazi-film',
    'term_id': '6'
}).encode('utf-8')

req = urllib.request.Request(
    'https://www.hdfilmcehennemi.now/wp-admin/admin-ajax.php',
    data=data,
    headers={
        'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36',
        'Referer': 'https://www.hdfilmcehennemi.now/film-izle-1/',
        'Content-Type': 'application/x-www-form-urlencoded'
    }
)
try:
    with urllib.request.urlopen(req, context=ctx, timeout=10) as r:
        html = r.read().decode('utf-8', errors='ignore')
        cards = re.findall(r'<a[^>]+href=["\'](https://www\.hdfilmcehennemi\.now/film/[^"\']+)["\']', html)
        print('Status:', r.status, 'Cards:', len(cards))
        for c in cards[:5]:
            print('Card:', c)
        if not cards:
            print('HTML snippet:', html[:500])
except Exception as e:
    print('Failed:', e)
