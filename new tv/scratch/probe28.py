import requests, re
from html.parser import HTMLParser
from pathlib import Path
class Links(HTMLParser):
    def handle_starttag(self,tag,attrs):
        a=dict(attrs)
        if tag=='a' and '28-' in a.get('href',''): print(a.get('href'),a.get('title',''))
for name,url in [('fullhd','https://fullhdfilmizlesene.co/arama/28'),('jet','https://jetfilmizle.top/?s=28+yil+sonra')]:
    try:
        r=requests.get(url,timeout=20)
        Path('scratch/'+name+'28.html').write_text(r.text,encoding='utf-8')
        print(name,r.status_code,r.url)
        Links().feed(r.text)
    except Exception as e: print(type(e).__name__)
