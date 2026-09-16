with open("scratch/captain_america_detail.html", "r", encoding="utf-8") as f:
    lines = f.readlines()

for i, line in enumerate(lines):
    if any(k in line.lower() for k in ["player", "source", "kaynak", "dublaj", "altyazı", "vtt", "vidmoly", "closeload", "embed", "iframe"]):
        if len(line.strip()) < 300:
            print(f"Line {i+1}: {line.strip()}")
