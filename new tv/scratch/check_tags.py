import re

# Check FilmModu
with open('scratch/filmmodu_home.html', 'r', encoding='utf-8', errors='ignore') as f:
    text = f.read()
print("=== FilmModu sample posters ===")
img_tags = re.findall(r'<img[^>]+>', text)
for tag in img_tags[:10]:
    print("IMG:", tag)

# Check FullHD
with open('scratch/current_fullhd.html', 'r', encoding='utf-8', errors='ignore') as f:
    text = f.read()
print("=== FullHD sample posters ===")
img_tags = re.findall(r'<img[^>]+>', text)
for tag in img_tags[:10]:
    print("IMG:", tag)

# Check JetFilm
with open('scratch/jetfilm_home.html', 'r', encoding='utf-8', errors='ignore') as f:
    text = f.read()
print("=== JetFilm sample posters ===")
img_tags = re.findall(r'<img[^>]+>', text)
for tag in img_tags[:10]:
    print("IMG:", tag)

# Check Dizigom
with open('scratch/live_dizigom.html', 'r', encoding='utf-8', errors='ignore') as f:
    text = f.read()
print("=== Dizigom sample posters ===")
img_tags = re.findall(r'<img[^>]+>', text)
for tag in img_tags[:10]:
    print("IMG:", tag)
