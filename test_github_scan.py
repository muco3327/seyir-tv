# -*- coding: utf-8 -*-
import urllib.request
import urllib.parse
import json
import time

queries = ['iptv spor m3u', 'iptv turkey m3u']
since_date = '2026-03-16'
seen_repos = set()
m3u_files_found = []

for q in queries:
    enc = urllib.parse.quote(q + ' pushed:>' + since_date)
    url = 'https://api.github.com/search/repositories?q=' + enc + '&sort=updated&order=desc&per_page=10'
    req = urllib.request.Request(url, headers={'User-Agent': 'Mozilla/5.0', 'Accept': 'application/vnd.github.v3+json'})
    try:
        with urllib.request.urlopen(req) as r:
            data = json.loads(r.read().decode())
            items = data.get('items', [])
            print('Query: ' + q + ' -> ' + str(len(items)) + ' repos')
            for item in items:
                fn = item['full_name']
                if fn in seen_repos:
                    continue
                seen_repos.add(fn)
                branch = item.get('default_branch', 'main')
                print('  Repo: ' + fn + ' (branch: ' + branch + ')')
                
                tree_url = 'https://api.github.com/repos/' + fn + '/git/trees/' + branch + '?recursive=1'
                tree_req = urllib.request.Request(tree_url, headers={'User-Agent': 'Mozilla/5.0', 'Accept': 'application/vnd.github.v3+json'})
                try:
                    with urllib.request.urlopen(tree_req) as tr:
                        tree_data = json.loads(tr.read().decode())
                        for f in tree_data.get('tree', []):
                            path = f.get('path', '')
                            pl = path.lower()
                            size = f.get('size', 0) or 0
                            if (pl.endswith('.m3u') or pl.endswith('.m3u8')) and size > 1024:
                                raw_url = 'https://raw.githubusercontent.com/' + fn + '/' + branch + '/' + path
                                print('    M3U: ' + path + ' (size=' + str(size) + ') -> ' + raw_url)
                                m3u_files_found.append(raw_url)
                except Exception as e2:
                    print('    Tree error: ' + str(e2))
                time.sleep(1)
    except Exception as e:
        print('Query error ' + q + ': ' + str(e))
    time.sleep(2)

print('\nTotal M3U files found: ' + str(len(m3u_files_found)))
for u in m3u_files_found:
    print('  ' + u)