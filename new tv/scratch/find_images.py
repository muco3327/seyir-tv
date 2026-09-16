import glob, re, urllib.request

headers = {
    'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36',
    'Accept': 'image/avif,image/webp,image/apng,image/svg+xml,image/*,*/*;q=0.8'
}

for fn in glob.glob('scratch/*.html'):
    with open(fn, 'r', encoding='utf-8', errors='ignore') as f:
        content = f.read()
    matches = re.findall(r'https?://[^\s"\'<>]+\.(?:jpg|jpeg|png|webp)', content)
    if matches:
        print(f'{fn}: {len(matches)} images, e.g. {matches[0]}')
