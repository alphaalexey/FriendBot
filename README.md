<p align="center">
    <img width="100px" style="border-radius: 15px" src="docs/icon.jpg" />
</p>

# FriendBot
*VK bot for searching friends*

# Build
## Gradle
```bash
$ ./gradlew stage
```

# Usage
### Arguments:
```
-g | --groupId $BOT_GROUP      - ID группы, от имени которой работает бот
-a | --accessToken $BOT_TOKEN  - VK токен
-s | --settings $BOT_SETTINGS  - LongPoll settings (optional)
```

### Basic usage
```bash
$ ./build/install/FriendBot/bin/FriendBot -g $BOT_GROUP -a $BOT_TOKEN
```

# License
FriendBot is distributed under GNU General Public License v3.0
