import urllib.request

test_urls = [
    ("HDFilmCehennemi", "https://www.hdfilmcehennemi.now/thumb_/149x224-1/wp-content/uploads/2026/08/eternals-2021-izle.webp", "https://selcukflix.com/", "https://www.hdfilmcehennemi.now/"),
    ("FullHDFilm", "https://fullhdfilmizlesene.co/uploads/filmler/2024-06/karanligi-taramak-izle_f.webp", "https://selcukflix.com/", "https://fullhdfilmizlesene.co/"),
    ("FilmModu", "https://filmmodu.cc/uploads/filmler/2025-06/28-yil-sonra_xl.jpg", "https://filmmodu.cc/", "https://filmmodu.cc/"),
    ("JetFilm", "https://jetfilmizle.top/uploads/posters/webp/you-ll-never-find-me-izle.webp", "https://jetfilmizle.top/", "https://jetfilmizle.top/"),
    ("SezonlukDizi", "https://sezonlukdizi.cc/i/dizi/d/223.jpg", "https://sezonlukdizi.cc/", "https://sezonlukdizi.cc/"),
    ("Macellan", "https://file.macellan.online/images/180/270/80//mv5bzdfmy2jjymytnjzhoc00odjkltlimjytm2y4yzbjmwe0zgzjxkeyxkfqcgc-v1-fmjpg-ux1000--1773904868.jpg", "https://dizilla.now/", "https://file.macellan.online/"),
]

for name, url, bad_ref, good_ref in test_urls:
    print(f"=== Testing {name} ===")
    for ref_label, ref in [("Bad/Default Ref", bad_ref), ("Correct Ref", good_ref), ("No Ref", None)]:
        headers = {
            'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36',
            'Accept': 'image/avif,image/webp,image/apng,image/svg+xml,image/*,*/*;q=0.8'
        }
        if ref:
            headers['Referer'] = ref
        try:
            req = urllib.request.Request(url, headers=headers)
            with urllib.request.urlopen(req, timeout=8) as resp:
                print(f"  {ref_label} ({ref}): HTTP {resp.status} - size {len(resp.read())}")
        except Exception as e:
            print(f"  {ref_label} ({ref}): FAILED {e}")
