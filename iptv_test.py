import urllib.request
import urllib.error
import sys

def test_url(url, headers):
    req = urllib.request.Request(url, headers=headers)
    try:
        with urllib.request.urlopen(req, timeout=5) as response:
            status = response.getcode()
            content_type = response.headers.get('Content-Type', '')
            
            first_bytes = response.read(100).decode('utf-8', errors='ignore')
            
            if 'text/html' in content_type.lower():
                return f"[BASARISIZ] Sunucu 200 OK verdi ama video yerine HTML/Hata sayfasi gonderdi! (Sahte calisiyor)"
                
            if '#EXTM3U' in first_bytes:
                return f"[BASARILI] Gercek HLS (.m3u8) video akisi bulundu!"
            elif first_bytes.startswith('G') or content_type in ['video/mp2t', 'video/mp4']: 
                return f"[BASARILI] Gercek TS/MPEG video akisi bulundu!"
            else:
                return f"[BASARILI] Yayin aktif! (Icerik tipi: {content_type})"
                
    except urllib.error.HTTPError as e:
        if e.code == 403:
            return f"[BASARISIZ] 403 Yasak (Token suresi bitmis veya erisim engeli var!)"
        return f"[BASARISIZ] HTTP Hatasi {e.code}"
    except urllib.error.URLError as e:
        return f"[BASARISIZ] Baglanti kurulamadi ({e.reason})"
    except Exception as e:
        return f"[BASARISIZ] Zaman Asimi / Hata ({str(e)})"

if __name__ == '__main__':
    url = "https://cdnlivetv.tv/secure/api/v1/6a288d2b81d8192bb76ccbfe/playlist.m3u8?token=NmEyODhkMmI4MWQ4MTkyYmI3NmNjYmZlOjE3ODc5NzUxMzI0NTE6Y2RubGl2ZXR2LnR2OjgwMzQzNzkyYWI2Zjk4MWMuOTQ1MDRlYTg0MDc5ZjhiZjZkZGI2MGRkNTMwM2QyMDg3ZjY5ZTkxYjI4YTYxNTMyMzNkZDdlYzUyYTMyNjgwNA"
    headers = {
        'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36',
        'Referer': 'https://cdnlivetv.tv/',
        'Origin': 'https://cdnlivetv.tv'
    }
    print(f"Test Edilen URL:\n{url}\n")
    print("Sonuc: " + test_url(url, headers))