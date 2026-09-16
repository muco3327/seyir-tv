import urllib.request
import ssl
import re

ctx = ssl._create_unverified_context()
headers = {
    'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36',
    'Accept': 'text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8',
    'Accept-Language': 'tr-TR,tr;q=0.9,en-US;q=0.8,en;q=0.7'
}

print("Fetching SezonlukDizi diziler list & episode...")
req1 = urllib.request.Request("https://sezonlukdizi.cc/diziler.asp", headers=headers)
with urllib.request.urlopen(req1, timeout=10, context=ctx) as resp:
    html1 = resp.read().decode('windows-1254', errors='ignore')

with open("scratch/sezonluk_diziler.html", "w", encoding="utf-8") as f:
    f.write(html1)

print("Saved scratch/sezonluk_diziler.html")

# Episode page
req2 = urllib.request.Request("https://sezonlukdizi.cc/lanterns/1-sezon-4-bolum.html", headers=headers)
with urllib.request.urlopen(req2, timeout=10, context=ctx) as resp:
    html2 = resp.read().decode('windows-1254', errors='ignore')

with open("scratch/sezonluk_ep.html", "w", encoding="utf-8") as f:
    f.write(html2)

print("Saved scratch/sezonluk_ep.html")
