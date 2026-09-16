import re

with open('c:/tv/app/src/main/java/tv/seyir/app/PlayerActivity.java', 'r', encoding='utf-8') as f:
    content = f.read()

replacement = '''    private void initialize() {
        if (player != null || url == null || closing || isFinishing() || video == null) return;
        try {
            if (url.contains("|")) {
                String[] parts = url.split("\\\\|", 2);
                url = parts[0];
                String[] params = parts[1].split("&");
                for (String p : params) {
                    if (p.toLowerCase().startsWith("user-agent=")) headers.put("User-Agent", p.substring(11));
                    else if (p.toLowerCase().startsWith("referer=")) headers.put("Referer", p.substring(8));
                    else if (p.toLowerCase().startsWith("origin=")) headers.put("Origin", p.substring(7));
                }
            }
            
            String ua = headers.containsKey("User-Agent") ? headers.get("User-Agent") : "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/114.0.0.0 Safari/537.36";'''

content = content.replace('''    private void initialize() {
        if (player != null || url == null || closing || isFinishing() || video == null) return;
        try {
            String ua = headers.containsKey("User-Agent") ? headers.get("User-Agent") : "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/114.0.0.0 Safari/537.36";''', replacement)

with open('c:/tv/app/src/main/java/tv/seyir/app/PlayerActivity.java', 'w', encoding='utf-8') as f:
    f.write(content)