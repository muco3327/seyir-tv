with open("scratch/inspect_sezonluk_js.py", "r") as f:
    pass

import urllib.request
import ssl
import re

ctx = ssl._create_unverified_context()
req = urllib.request.Request("https://sezonlukdizi.cc/js/site.min.js?v=0.82", headers={'User-Agent': 'Mozilla/5.0'})
with urllib.request.urlopen(req, timeout=10, context=ctx) as resp:
    js = resp.read().decode('utf-8', errors='ignore')

idx = js.find("dataEmbed22.asp")
if idx != -1:
    print(js[max(0, idx - 200): min(len(js), idx + 500)])

idx2 = js.find("dataAlternatif22.asp")
if idx2 != -1:
    print("\n--- ALTERNATIF ---\n")
    print(js[max(0, idx2 - 200): min(len(js), idx2 + 500)])
