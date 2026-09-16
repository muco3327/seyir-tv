import urllib.request, ssl, re

ctx = ssl.create_default_context()
ctx.check_hostname = False
ctx.verify_mode = ssl.CERT_NONE

req = urllib.request.Request('https://www.hdfilmcehennemi.now/', headers={'User-Agent': 'Mozilla/5.0'})
with urllib.request.urlopen(req, context=ctx, timeout=10) as r:
    html = r.read().decode('utf-8', errors='ignore')

genres = re.findall(r'<a[^>]+href=["\']https://www\.hdfilmcehennemi\.now/tur/([^/"\']+)/?["\'][^>]*>(.*?)</a>', html)
print('Found genres:', len(genres))
seen = set()
for slug, title in genres:
    clean_title = re.sub(r'<[^>]+>', '', title).strip()
    if slug not in seen and clean_title:
        seen.add(slug)
        print(f'("{clean_title}", "{slug}"),')
