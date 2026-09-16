import urllib.request
import json
import time

repos = ['vecdi7/turkish', 'Lunedor/iptvTR', 'Efeisot/iptv', 'emrebaas/iptv', 'Hannstcott/SunshineTr', 'YoranYosipov/iptv-playlists', 'ikraikiram/sports-iptv']

for repo in repos:
    print(f"\nChecking {repo}...")
    url = f"https://api.github.com/repos/{repo}/git/trees/main?recursive=1"
    req = urllib.request.Request(url, headers={'User-Agent': 'Mozilla/5.0'})
    try:
        with urllib.request.urlopen(req) as r:
            tree = json.loads(r.read().decode()).get("tree", [])
            for f in tree:
                path = f.get("path", "")
                if path.endswith(".m3u") or path.endswith(".m3u8"):
                    raw_url = f"https://raw.githubusercontent.com/{repo}/main/{path}"
                    print(f"  Found M3U: {raw_url}")
                    # fetch head to see if it has bein
                    try:
                        m3u_req = urllib.request.Request(raw_url, headers={'User-Agent': 'Mozilla/5.0'})
                        with urllib.request.urlopen(m3u_req) as mr:
                            content = mr.read().decode('utf-8', errors='replace').lower()
                            if 'bein' in content and 'spor' in content:
                                print("    -> CONTAINS BEIN & SPOR!")
                            else:
                                print("    -> no bein")
                    except Exception as e:
                        print(f"    -> error fetching: {e}")
    except urllib.error.HTTPError as e:
        if e.code == 404:
            # try master branch
            url = f"https://api.github.com/repos/{repo}/git/trees/master?recursive=1"
            req = urllib.request.Request(url, headers={'User-Agent': 'Mozilla/5.0'})
            try:
                with urllib.request.urlopen(req) as r:
                    tree = json.loads(r.read().decode()).get("tree", [])
                    for f in tree:
                        path = f.get("path", "")
                        if path.endswith(".m3u") or path.endswith(".m3u8"):
                            raw_url = f"https://raw.githubusercontent.com/{repo}/master/{path}"
                            print(f"  Found M3U: {raw_url}")
                            try:
                                m3u_req = urllib.request.Request(raw_url, headers={'User-Agent': 'Mozilla/5.0'})
                                with urllib.request.urlopen(m3u_req) as mr:
                                    content = mr.read().decode('utf-8', errors='replace').lower()
                                    if 'bein sports 1' in content or 'bein' in content:
                                        print("    -> CONTAINS BEIN & SPOR!")
                                    else:
                                        print("    -> no bein")
                            except Exception as e2:
                                print(f"    -> error fetching: {e2}")
            except Exception:
                pass
    time.sleep(1)
