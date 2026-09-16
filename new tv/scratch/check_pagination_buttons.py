import urllib.request, ssl, re

ctx = ssl.create_default_context()
ctx.check_hostname = False
ctx.verify_mode = ssl.CERT_NONE

req = urllib.request.Request('https://www.hdfilmcehennemi.now/film-izle-1/', headers={'User-Agent': 'Mozilla/5.0'})
with urllib.request.urlopen(req, context=ctx, timeout=10) as r:
    html = r.read().decode('utf-8', errors='ignore')

# Check pagination or load more buttons
matches = re.findall(r'<[^>]+(?:pagination|load-more|daha-fazla|page|nav)[^>]*>.*?</[^>]+>', html, re.I)
for m in matches[:10]:
    print(m)

# Check all links containing "page" or "sayfa" or numbers
links = re.findall(r'<a[^>]+href=["\'][^"\']+["\'][^>]*>.*?</a>', html)
for l in links:
    if any(w in l.lower() for w in ['page', 'sayfa', 'sonraki', 'ileri', 'next']):
        print('Pagination link:', l)
