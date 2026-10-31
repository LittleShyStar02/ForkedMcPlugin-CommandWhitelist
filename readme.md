Command  Whitelist is a plugin that allows you to control 
precisely what commands players can see and use.

[![CodeFactor Grade](https://img.shields.io/codefactor/grade/github/youhavetrouble/commandwhitelist?style=flat-square)](https://www.codefactor.io/repository/github/youhavetrouble/commandwhitelist)
[![GitHub all releases](https://img.shields.io/github/downloads/youhavetrouble/commandwhitelist/total?style=flat-square&label=direct%20release%20downloads)](https://github.com/YouHaveTrouble/CommandWhitelist/releases)
[![Discord](https://img.shields.io/discord/821565102108573706?style=flat-square&color=%237289da&label=Discord)](https://discord.gg/j8KK5dGBps)
[![Spiget Rating](https://img.shields.io/spiget/rating/81326?label=Spigot%20rating&style=flat-square)](https://www.spigotmc.org/resources/81326/)

![bStats Servers](https://img.shields.io/bstats/servers/8705?label=Bukkit%20servers%20using%20CW&style=flat-square)
![bStats Servers](https://img.shields.io/bstats/servers/8704?label=Proxy%20servers%20using%20CW&style=flat-square)

<h3>Plugin Features</h3>
<ul>
<li>Lock selected commands behind permission
<li>Overwrite default "no such command" message with your branding
<li>Play configurable sounds when a command is denied</li>
<li>Block tab completion</li>
<li>Block command execution</li>
<li>Block completion and execution of specified subcommands</li>
</ul>

<b>One jar for everything</b>: the same `CommandWhitelist-<version>.jar` works on all the software below.

<b>Compatible versions</b>: 1.13+

<b>Compatible software</b>:
- Bukkit-based servers (Spigot, Paper, Purpur, etc)
- Folia
- Waterfall (NOT Bungeecord!)
- Velocity

The groups available to a player are merged: `default` plus every group the player has the `commandwhitelist.group.<name>` permission of.

<b>How commands are compared</b>:
- `WHITELIST` compares exactly: with `ban` listed, `/Ban` is a different command and is denied.
- `BLACKLIST` ignores case, namespace and aliases: with `ban` listed, `/BAN`, `/minecraft:ban` and aliases of it are denied, and so is `/execute run ban`. Some server versions really run `/BAN` as `ban`.

The plugin does not replace permissions: a blacklist cannot know what other commands do on behalf of the player.

<h3>Building and testing</h3>

Requires JDK 21. The jar ends up in `build/libs`.

```bash
./gradlew shadowJar test
```

`tests/e2e` starts real servers on localhost and checks the plugin with [mineflayer](https://github.com/PrismarineJS/mineflayer) bots, see `tests/e2e/README.md`.

[Download latest release](https://github.com/YouHaveTrouble/CommandWhitelist/releases/latest)

Having some issues? Make sure to check out <a href="https://github.com/YouHaveTrouble/CommandWhitelist/wiki">Plugin Wiki</a>.

Found a bug or you think plugin is missing an important feature? <a href="https://github.com/YouHaveTrouble/CommandWhitelist/issues">Create an issue</a>.
