import base64
import hashlib
from Crypto.Cipher import AES
import re
import json

secret = "!!22xx!!90!!"
h = hashlib.sha256(secret.encode('utf-8')).digest()
b64 = base64.b64encode(h).decode('utf-8')
key = b64[:32].encode('utf-8')
iv = b'\x00' * 16

def decrypt(ct_b64):
    cipher = AES.new(key, AES.MODE_CBC, iv)
    ct = base64.b64decode(ct_b64)
    pt = cipher.decrypt(ct)
    pad = pt[-1]
    pt = pt[:-pad]
    return pt.decode('utf-8', errors='ignore')

with open('scratch/live_hdf.html', 'r', encoding='utf-8', errors='ignore') as f:
    text = f.read()

m = re.search(r'"secureData"\s*:\s*"([^"]+)"', text)
if m:
    dec = decrypt(m.group(1))
    data = json.loads(dec)
    print("Decrypted successfully! Keys:", list(data.keys()))
    # find movies
    for k, v in data.items():
        if isinstance(v, list) and v and isinstance(v[0], dict):
            print(f"Key {k}: {len(v)} items")
            for item in v[:5]:
                print("  Title:", item.get("original_title") or item.get("title"))
                print("  poster_url:", item.get("poster_url"))
                print("  face_url:", item.get("face_url"))
                print("  object_poster_url:", item.get("object_poster_url"))
else:
    print("No secureData found in live_hdf.html")
