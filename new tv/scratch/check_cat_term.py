import urllib.request, ssl, re

ctx = ssl.create_default_context()
ctx.check_hostname = False
ctx.verify_mode = ssl.CERT_NONE

req = urllib.request.Request('https://www.hdfilmcehennemi.now/tur/aksiyon-izle-1/', headers={'User-Agent': 'Mozilla/5.0'})
with urllib.request.urlopen(req, context=ctx, timeout=10) as r:
    html = r.read().decode('utf-8', errors='ignore')

data_type = re.search(r'data-type="([^"]+)"', html)
data_term = re.search(r'data-term-id="([^"]+)"', html)
print('data-type:', data_type.group(1) if data_type else None)
print('data-term-id:', data_term.group(1) if data_term else None)
