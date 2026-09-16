import re
with open('c:/tv/app/src/main/java/tv/seyir/app/MainActivity.java', 'r', encoding='utf-8') as f:
    content = f.read()

content = content.replace('Source.FULLHD', 'Source.SPORTS')
content = content.replace('SportsManager.loadChannels(this,channels->{', 'SportsManager.loadChannels(this,channels->{')
# Let's just fix loadChannels calls manually by finding them.
lines = content.split('\n')
for i in range(len(lines)):
    if 'SportsManager.loadChannels(this,' in lines[i]:
        lines[i] = lines[i].replace('channels->{', 'channels->{', 1)
        # We need to find the matching closing bracket for this lambda and insert the second argument.
        # Actually it's simpler:
        lines[i] = lines[i].replace('SportsManager.loadChannels(this,channels->{', 'SportsManager.loadChannels(this, channels->{', 1)

with open('c:/tv/app/src/main/java/tv/seyir/app/MainActivity.java', 'w', encoding='utf-8') as f:
    f.write('\n'.join(lines))
