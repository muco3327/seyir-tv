with open("scratch/jetfilm_detail.html", "r", encoding="utf-8") as f:
    lines = f.readlines()

for i, line in enumerate(lines):
    if any(k in line.lower() for k in ["iframe", "player", "vidmoly", "rapid", "pichive", "kaynak", "dublaj", "altyazı", "vtt"]):
        if len(line.strip()) < 300:
            print(f"Line {i+1}: {line.strip()}")
