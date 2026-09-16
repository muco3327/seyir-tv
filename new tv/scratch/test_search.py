import urllib.request, ssl, re, urllib.parse

ctx = ssl.create_default_context()
ctx.check_hostname = False
ctx.verify_mode = ssl.CERT_NONE

# Test search with /?s=query or /arama/query or search ajax
queries = ['batman', 'avatar']

for q in queries:
    search_url = f'https://www.hdfilmcehennemi.now/?s={urllib.parse.quote(q)}'
    req = urllib.request.Request(search_url, headers={'User-Agent': 'Mozilla/5.0'})
    try:
        with urllib.request.urlopen(req, context=ctx, timeout=10) as r:
            html = r.read().decode('utf-8', errors='ignore')
            cards = re.findall(r'<a[^>]+href=["\'](https://www\.hdfilmcehennemi\.now/film/[^"\']+/)["\']', html)
            print(f"Search '{q}' -> Status {r.status}, Cards: {len(cards)}")
            if cards:
                print('Sample card:', cards[0])
    except Exception as e:
        print(f"Search '{q}' -> Error:", e)
