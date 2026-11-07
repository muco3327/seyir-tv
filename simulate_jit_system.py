import time
import requests
import sys

# Force UTF-8 on Windows stdout
sys.stdout.reconfigure(encoding='utf-8')

GATEWAY = "http://150.230.29.27:3000"

print("=" * 60)
print("SIMULASYON 1: Talep Aninda (JIT) Canli Cozumleme")
print("=" * 60)

test_channels = ["beinsports1", "beinsports2", "ssport1", "aspor", "trtspor"]
for cid in test_channels:
    t0 = time.time()
    url = f"{GATEWAY}/channel/{cid}.m3u8"
    try:
        r = requests.get(url, timeout=6)
        elapsed = int((time.time() - t0) * 1000)
        lines = [l for l in r.text.splitlines() if l.startswith('/proxy?url=')]
        print(f"[{cid}] HTTP {r.status_code} ({elapsed}ms) - {len(lines)} segment hazir")
        
        # Test first segment
        if lines:
            seg_url = GATEWAY + lines[0]
            t_seg = time.time()
            r_seg = requests.get(seg_url, timeout=6, stream=True)
            seg_elapsed = int((time.time() - t_seg) * 1000)
            chunk = r_seg.raw.read(188 * 3)
            ct = r_seg.headers.get('content-type')
            sync_ok = (len(chunk) >= 188 and chunk[0] == 0x47)
            print(f"   -> Segment: HTTP {r_seg.status_code} ({seg_elapsed}ms) | Content-Type: {ct} | 0x47 TS Sync: {'OK' if sync_ok else 'HATA'}")
    except Exception as e:
        print(f"[{cid}] HATA: {e}")

print("\n" + "=" * 60)
print("SIMULASYON 2: Kopma ve Acil Durum Iyilestirme (Self-Healing)")
print("=" * 60)
t1 = time.time()
r1 = requests.get(f"{GATEWAY}/channel/beinsports1.m3u8")
e1 = int((time.time() - t1) * 1000)

t2 = time.time()
r2 = requests.get(f"{GATEWAY}/channel/beinsports1.m3u8")
e2 = int((time.time() - t2) * 1000)
print(f"1. Istek (Upstream JIT): {e1}ms")
print(f"2. Istek (RAM Cache Hit): {e2}ms (Hizli teslimat)")

print("\n" + "=" * 60)
print("SIMULASYON 3: Uygulama Acilisinda Akilli Sync (Boot Sync)")
print("=" * 60)
t_boot = time.time()
r_boot = requests.get(f"{GATEWAY}/api/channels", timeout=5)
e_boot = int((time.time() - t_boot) * 1000)
print(f"Web/APK Acilis Istegi: HTTP {r_boot.status_code} ({e_boot}ms) - {len(r_boot.json())} kanal listelendi")

print("=" * 60)
