with open('c:/tv/app/src/main/java/tv/seyir/app/SportsManager.java', 'r', encoding='utf-8') as f:
    content = f.read()

import re
content = re.sub(r'\} else if \(line\.contains\("http-referrer="\)\) \{.*?(?:currentHeaders\.put\("Referer".*?\}|)', 
    r'} else if (line.contains("http-referrer=")) {\n                                currentHeaders.put("Referer", line.substring(line.indexOf("=") + 1).trim());\n                            } else if (line.contains("http-origin=")) {\n                                currentHeaders.put("Origin", line.substring(line.indexOf("=") + 1).trim());\n                            }', 
    content, flags=re.DOTALL)

with open('c:/tv/app/src/main/java/tv/seyir/app/SportsManager.java', 'w', encoding='utf-8') as f:
    f.write(content)