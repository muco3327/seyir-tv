import urllib.request, ssl, re

ctx = ssl.create_default_context()
ctx.check_hostname = False
ctx.verify_mode = ssl.CERT_NONE

req = urllib.request.Request(
    'https://www.hdfilmcehennemi.now/film/mayday-2026-izle/',
    headers={'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) Chrome/124.0.0.0'}
)

with urllib.request.urlopen(req, context=ctx, timeout=10) as resp:
    html = resp.read().decode('utf-8', errors='ignore')

print('Title:', re.findall(r'<title>(.*?)</title>', html))
print('Nonce:', re.findall(r'["\']nonce["\']\s*:\s*["\']([a-f0-9]+)["\']', html))
print('Data-post-id:', re.findall(r'data-post-id=["\'](\d+)["\']', html))
print('Data-player-name:', re.findall(r'data-player-name=["\']([^"\']+)["\']', html))
print('Player buttons full matches:')
for m in re.finditer(r'<[a-z0-9]+[^>]+data-player-name=[^>]+>.*?</[a-z0-9]+>', html, re.S):
    print(m.group(0))
