import urllib.request, ssl, re, urllib.parse, json

ctx = ssl.create_default_context()
ctx.check_hostname = False
ctx.verify_mode = ssl.CERT_NONE

# 1. Get ajax video url
data = urllib.parse.urlencode({
    'action': 'get_video_url',
    'nonce': 'f5ab3378fa',
    'post_id': '25173',
    'player_name': 'SetPlay',
    'part_key': ''
}).encode('utf-8')

req = urllib.request.Request(
    'https://www.hdfilmcehennemi.now/wp-admin/admin-ajax.php',
    data=data,
    headers={
        'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) Chrome/124.0.0.0',
        'Referer': 'https://www.hdfilmcehennemi.now/film/mayday-2026-izle/',
        'X-Requested-With': 'XMLHttpRequest',
        'Content-Type': 'application/x-www-form-urlencoded; charset=UTF-8'
    }
)

with urllib.request.urlopen(req, context=ctx) as r:
    res = json.loads(r.read().decode('utf-8'))
    setplay_url = res['data']['url']
    print('SetPlay URL:', setplay_url)

# 2. Fetch setplay page
req2 = urllib.request.Request(
    setplay_url,
    headers={
        'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) Chrome/124.0.0.0',
        'Referer': 'https://www.hdfilmcehennemi.now/'
    }
)

with urllib.request.urlopen(req2, context=ctx) as r2:
    setplay_html = r2.read().decode('utf-8')

print('Setplay HTML length:', len(setplay_html))
for m in re.finditer(r'<script[^>]*>(.*?)</script>', setplay_html, re.DOTALL):
    s = m.group(1).strip()
    if s:
        print('=== SCRIPT ===')
        print(s[:2000])
