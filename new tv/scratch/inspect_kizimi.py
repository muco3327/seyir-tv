import re

with open('scratch/kizimi_kurtarin.html', 'r', encoding='utf-8') as f:
    html = f.read()

print('iframes:', re.findall(r'<iframe[^>]+>', html))
for m in re.finditer(r'data-player', html):
    print('data-player context:', html[max(0, m.start()-50):min(len(html), m.end()+150)])
for m in re.finditer(r'get_video_url', html):
    print('get_video_url context:', html[max(0, m.start()-100):min(len(html), m.end()+200)])
for m in re.finditer(r'player', html, re.IGNORECASE):
    chunk = html[max(0, m.start()-30):min(len(html), m.end()+50)]
    if 'video' in chunk.lower() or 'ajax' in chunk.lower() or 'source' in chunk.lower() or 'iframe' in chunk.lower():
        print('player context:', chunk)
