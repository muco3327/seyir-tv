import urllib.request, ssl, re, json

ctx = ssl.create_default_context()
ctx.check_hostname = False
ctx.verify_mode = ssl.CERT_NONE

headers = {
    'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36'
}

urls = [
    "https://www.hdfilmcehennemi.now/film/hizmetci-2025-izle-48/",
    "https://www.hdfilmcehennemi.now/film/olumcul-dovus-2021-izle/",
    "https://www.hdfilmcehennemi.now/film/kizimi-kurtarin/",
    "https://www.hdfilmcehennemi.now/film/kacak/",
    "https://www.hdfilmcehennemi.now/film/sampiyon-2008-izle/"
]

for url in urls:
    print("Testing:", url)
    try:
        req = urllib.request.Request(url, headers=headers)
        with urllib.request.urlopen(req, context=ctx, timeout=10) as r:
            html = r.read().decode('utf-8', errors='ignore')
        
        # Check nonce and post id
        nonce_m = re.search(r'videoAjax\s*=\s*\{[^}]*nonce\s*:\s*[\'"]([a-f0-9]+)[\'"]', html) or re.search(r'[\'"]nonce[\'"]\s*:\s*[\'"]([a-f0-9]+)[\'"]', html)
        nonce = nonce_m.group(1) if nonce_m else None
        
        post_id_m = re.search(r'data-post-id=[\'"](\d+)[\'"]', html)
        post_id = post_id_m.group(1) if post_id_m else None
        
        players = list(set(re.findall(r'data-player-name=[\'"]([^\'"]+)[\'"]', html)))
        print(f"  Nonce: {nonce}, PostId: {post_id}, Players: {players}")
        
        if nonce and post_id:
            for p in (players or ['SetPlay']):
                data = urllib.parse.urlencode({
                    'action': 'get_video_url',
                    'nonce': nonce,
                    'post_id': post_id,
                    'player_name': p,
                    'part_key': ''
                }).encode('utf-8')
                post_req = urllib.request.Request(
                    'https://www.hdfilmcehennemi.now/wp-admin/admin-ajax.php',
                    data=data,
                    headers={
                        'User-Agent': headers['User-Agent'],
                        'Referer': url,
                        'X-Requested-With': 'XMLHttpRequest',
                        'Content-Type': 'application/x-www-form-urlencoded'
                    }
                )
                with urllib.request.urlopen(post_req, context=ctx, timeout=10) as pr:
                    resp_body = pr.read().decode('utf-8', errors='ignore')
                    print(f"    Player {p} response: {resp_body[:100]}")
    except Exception as e:
        print("  Error:", e)
