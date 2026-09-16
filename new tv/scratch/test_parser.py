import urllib.request, ssl, re

ctx = ssl.create_default_context()
ctx.check_hostname = False
ctx.verify_mode = ssl.CERT_NONE

req = urllib.request.Request('https://www.hdfilmcehennemi.now/film-izle-1/', headers={'User-Agent': 'Mozilla/5.0'})
with urllib.request.urlopen(req, context=ctx, timeout=10) as r:
    html = r.read().decode('utf-8', errors='ignore')

articles = re.findall(r'<article[^>]*>.*?</article>', html, re.DOTALL)
print('Total articles:', len(articles))

items = []
for a in articles:
    href = re.search(r'href=["\']([^"\']+)["\']', a)
    data_src = re.search(r'data-src=["\']([^"\']+)["\']', a)
    title = re.search(r'class="flbaslik">([^<]+)<', a)
    if not title:
        title = re.search(r'alt=["\']([^"\']+)["\']', a)
    
    if href and data_src and title:
        t = title.group(1).replace(' izle', '').replace(' Poster', '').strip()
        items.append((t, href.group(1), data_src.group(1)))

print(f"Extracted {len(items)} items.")
for item in items[:5]:
    print(item)
