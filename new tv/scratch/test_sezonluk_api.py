import urllib.request
import urllib.parse
import ssl
import json
import re

ctx = ssl._create_unverified_context()
headers = {
    'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36',
    'Referer': 'https://sezonlukdizi.cc/lanterns/1-sezon-4-bolum.html',
    'X-Requested-With': 'XMLHttpRequest',
    'Content-Type': 'application/x-www-form-urlencoded; charset=UTF-8'
}

bid = "96873" # Lanterns 1.Sezon 4.Bolum

for dil in [1, 0]: # 1 = Altyazi, 0 = Dublaj
    data = urllib.parse.urlencode({'bid': bid, 'dil': dil}).encode('utf-8')
    req = urllib.request.Request("https://sezonlukdizi.cc/ajax/dataAlternatif22.asp", data=data, headers=headers)
    with urllib.request.urlopen(req, timeout=10, context=ctx) as resp:
        resp_text = resp.read().decode('utf-8', errors='ignore')
        print(f"Dil: {dil} -> Alternatif Response: {resp_text[:300]}")
        try:
            res_json = json.loads(resp_text)
            if res_json.get("status") == "success":
                for alt in res_json.get("data", []):
                    alt_id = alt.get("id")
                    title = alt.get("baslik")
                    # Fetch embed
                    embed_data = urllib.parse.urlencode({'id': alt_id}).encode('utf-8')
                    embed_req = urllib.request.Request("https://sezonlukdizi.cc/ajax/dataEmbed22.asp", data=embed_data, headers=headers)
                    with urllib.request.urlopen(embed_req, timeout=10, context=ctx) as embed_resp:
                        embed_html = embed_resp.read().decode('utf-8', errors='ignore')
                        srcs = re.findall(r'<iframe[^>]+src="([^"]+)"', embed_html)
                        print(f"  Alt {alt_id} ({title}) -> Iframes: {srcs}")
        except Exception as e:
            print("  Parse error:", e)
