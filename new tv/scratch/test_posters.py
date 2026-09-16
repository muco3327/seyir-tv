import urllib.request
import re

headers = {
    'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36',
    'Accept': 'image/avif,image/webp,image/apng,image/svg+xml,image/*,*/*;q=0.8'
}

with open('scratch/live_hdf.html', 'r', encoding='utf-8', errors='ignore') as f:
    text = f.read()

posters = re.findall(r'"poster_url":"([^"]+)"', text)
print('Found posters in live_hdf:', len(posters))
for p in posters[:3]:
    p_clean = p.replace('\\/', '/')
    print('Testing original:', p_clean)
    try:
        req = urllib.request.Request(p_clean, headers=headers)
        with urllib.request.urlopen(req, timeout=5) as r:
            print('Original status:', r.status, len(r.read()))
    except Exception as e:
        print('Original failed:', e)
    
    p_replaced = p_clean.replace('https://images-macellan-online.cdn.ampproject.org/i/s/', 'https://')
    print('Testing replaced:', p_replaced)
    try:
        req = urllib.request.Request(p_replaced, headers=headers)
        with urllib.request.urlopen(req, timeout=5) as r:
            print('Replaced status:', r.status, len(r.read()))
    except Exception as e:
        print('Replaced failed:', e)
