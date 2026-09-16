import glob
from bs4 import BeautifulSoup
import json

# Check SezonlukDizi
print("--- SezonlukDizi ---")
with open('scratch/sezonluk_diziler.html', 'r', encoding='windows-1254', errors='ignore') as f:
    soup = BeautifulSoup(f.read(), 'html.parser')
cards = soup.select(".afis .column, #filtreSonuclari .column, a.column")
print(f"Cards found: {len(cards)}")
missing_posters = 0
for c in cards[:20]:
    img = c.select_first("img")
    p = ""
    if img:
        p = img.get("data-src") or img.get("src") or ""
    title = c.get("title") or (c.select_first(".header, .description") and c.select_first(".header, .description").text)
    if not p or p.startswith("data:image"):
        print(f"  Missing or data URI poster for {title}: {p[:40]}")
        missing_posters += 1
    else:
        print(f"  OK: {title} -> {p}")

print("--- FilmModu ---")
with open('scratch/filmmodu_home.html', 'r', encoding='utf-8', errors='ignore') as f:
    soup = BeautifulSoup(f.read(), 'html.parser')
cards = soup.select(".movie_box, .editor_selection .own-carousel__item, article.movie_box")
print(f"FilmModu cards: {len(cards)}")
for c in cards[:10]:
    img = c.select_first("img")
    p = ""
    if img:
        p = img.get("data-src") or img.get("src") or ""
    if p.startswith("data:image"):
        src_elem = c.select_first("picture source")
        if src_elem:
            p = (src_elem.get("data-srcset") or src_elem.get("srcset") or "").split(" ")[0]
    title = (c.select_first(".title, h2, h3") and c.select_first(".title, h2, h3").text) or ""
    print(f"  {title[:25]} -> {p}")

print("--- FullHDFilm ---")
with open('scratch/current_fullhd.html', 'r', encoding='utf-8', errors='ignore') as f:
    soup = BeautifulSoup(f.read(), 'html.parser')
cards = soup.select("a.image.sldurl, ul.mov-list > li a[href], div.poster-item a[href], div.film-item a[href], article.item a[href]")
print(f"FullHD cards: {len(cards)}")
for a in cards[:10]:
    el = a.parent or a
    img = a.select_first("img") or el.select_first("img")
    p = ""
    if img:
        p = img.get("data-src") or img.get("src") or ""
    title = a.get("title") or (el.select_first(".title, h2, h3") and el.select_first(".title, h2, h3").text) or ""
    print(f"  {title[:25]} -> {p}")

print("--- JetFilm ---")
with open('scratch/jetfilm_home.html', 'r', encoding='utf-8', errors='ignore') as f:
    soup = BeautifulSoup(f.read(), 'html.parser')
cards = soup.select("div.group.relative, article, a[href*='-izle']")
print(f"Jetfilm cards: {len(cards)}")
for c in cards[:10]:
    img = c.select_first("img")
    p = ""
    if img:
        p = img.get("data-src") or img.get("src") or ""
    title = (c.select_first("h3, h2, .title") and c.select_first("h3, h2, .title").text) or ""
    print(f"  {title[:25]} -> {p}")
