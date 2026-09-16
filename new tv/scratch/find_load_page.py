import urllib.request, ssl, re

ctx = ssl.create_default_context()
ctx.check_hostname = False
ctx.verify_mode = ssl.CERT_NONE

req = urllib.request.Request('https://www.hdfilmcehennemi.now/film-izle-1/', headers={'User-Agent': 'Mozilla/5.0'})
with urllib.request.urlopen(req, context=ctx, timeout=10) as r:
    html = r.read().decode('utf-8', errors='ignore')

# Search for load_page_content in html
for m in re.finditer(r'load_page_content', html):
    start = max(0, m.start() - 200)
    end = min(len(html), m.end() + 200)
    print('Context around load_page_content:')
    print(html[start:end])
