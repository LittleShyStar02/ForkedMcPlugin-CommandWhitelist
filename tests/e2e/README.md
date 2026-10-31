# Real server tests

`run.js` downloads the server software, starts it on `127.0.0.1` with `online-mode=false`, connects mineflayer bots and checks what the plugin allows. Running it accepts the Minecraft EULA for these servers (https://aka.ms/MinecraftEULA), so read it first.

Keep the work directory outside of a synchronized folder such as Google Drive.

```bash
npm install
JAVA8=/path/to/java8 JAVA21=/path/to/java21 JAVA25=/path/to/java25 node run.js <work dir> <plugin jar> paper-1.13.2,paper-1.21.11,folia-1.21.11,velocity-3.5.1,waterfall-1.21
```

Targets are listed in `TARGETS` at the top of `run.js`. `spigot-1.21.11` needs `spigot-1.21.11.jar` built with BuildTools in `<work dir>/jars`.

Results are saved in `<work dir>/results`, server logs in `<work dir>/run`. The process exits with 1 when a check fails.
