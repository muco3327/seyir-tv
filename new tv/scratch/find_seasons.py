with open("scratch/breaking_bad_detail.html", "r", encoding="utf-8") as f:
    lines = f.readlines()

for i, line in enumerate(lines):
    if any(k in line.lower() for k in ["sezon", "tab", "bolum", "accordion", "menu"]):
        if len(line.strip()) < 200:
            print(f"Line {i+1}: {line.strip()}")
