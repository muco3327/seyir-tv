import urllib.request
import re

headers = {
    'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36'
}

req = urllib.request.Request('https://www.hdfilmcehennemi.now/film-izle-1/', headers=headers)
html = urllib.request.urlopen(req).read().decode('utf-8', errors='ignore')

links = re.findall(r'<a[^>]+href=["\'](https://www\.hdfilmcehennemi\.now/[^"\']+/)["\']', html)
movie_links = [l for l in links if not any(x in l for x in ['/tur/', '/yil/', '/page/', '/film-izle', '/tag/'])]

print("Sample movie links:", movie_links[:3])

for m_url in movie_links[:3]:
    print("\n--- Checking", m_url, "---")
    m_req = urllib.request.Request(m_url, headers=headers)
    m_html = urllib.request.urlopen(m_req).read().decode('utf-8', errors='ignore')
    
    # Check post-id and nonce
    post_id = re.search(r'data-post-id=["\'](\d+)["\']', m_html)
    nonce = re.search(r'["\']nonce["\']\s*:\s*["\']([a-f0-9]+)["\']', m_html)
    ajaxurl = re.search(r'["\']ajaxurl["\']\s*:\s*["\']([^"\']+)["\']', m_html)
    
    print("Post ID:", post_id.group(1) if post_id else None)
    print("Nonce:", nonce.group(1) if nonce else None)
    print("AjaxURL:", ajaxurl.group(1) if ajaxurl else None)
    
    # Check all player buttons
    buttons = re.findall(r'<[^>]+data-player-name=[^>]+>', m_html)
    for b in buttons:
        print("Player button:", b)
