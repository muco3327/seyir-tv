import urllib.request
import re
import ssl

headers = {
    'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36',
    'Accept': 'text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8',
    'Accept-Language': 'tr-TR,tr;q=0.9,en-US;q=0.8,en;q=0.7'
}

ctx = ssl._create_unverified_context()

def fetch(url):
    req = urllib.request.Request(url, headers=headers)
    with urllib.request.urlopen(req, timeout=10, context=ctx) as resp:
        return resp.geturl(), resp.status, resp.read().decode('utf-8', errors='ignore')

for site in ["https://www.fullhdfilmizlesene.de/", "https://www.filmmodu.io/", "https://sezonlukdizi.vip/"]:
    try:
        furl, status, html = fetch(site)
        print(f"Site: {site} -> Final URL: {furl}, Status: {status}, Length: {len(html)}")
        title = re.findall(r'<title>(.*?)</title>', html, re.I)
        print(f"Title: {title}")
        # print first 5 hrefs
        all_hrefs = re.findall(r'href="([^"]+)"', html)
        print(f"Total hrefs: {len(all_hrefs)}, sample: {all_hrefs[:10]}")
    except Exception as e:
        print(f"Error for {site}: {e}")
