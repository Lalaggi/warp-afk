# WarpAFK — Unplugged-AFK port for WarpMC

Port of https://github.com/sakura-ryoko/unplugged-afk as a **WarpMC Paper plugin** using the native `FakePlayer API` (`org.purpurmc.purpur.player.FakePlayer`).

> Upstream is Fabric + Mixins (LGPL-3.0). This port is a clean-room Paper plugin that *calls* `FakePlayerFactory` — no mixin code copied. Config semantics kept compatible.

## Features (parity)

- `/unplug [minutes] [reason]` — disconnect yourself, leave a bot (same UUID/name, same pos). Default 129600m = 90 days like upstream.
- `/afk` alias
- `/unplugged-admin spawn <player> [minutes] [reason]` — spawn bot for offline/online player
- `/unplugged-admin kick <player>` — remove bot
- `/unplugged-admin info <player>` — show bot details
- `/unplugged-admin list` — list active bots
- `/unplugged-admin save|reload|purge|set <key> <value>` — persistence & config
- Persists active bots to `plugins/WarpAFK/unplugged.json` and restores on restart (adjusted for downtime)
- Honors `unplugged.disable-damage`, `hide-player`, `reset-health-on-death` via `FakePlayerBuilder`
- Real player re-login evicts bot (WarpMC's `ServerLoginPacketListenerImpl` already does; plugin also cleans persistence)

## Building

Requires WarpMC jars in local maven (from parent build):

```bash
# from warpMC root:
./gradlew publishToMavenLocal
# then in warp-afk:
gradle build   # or ./gradlew if wrapper added
# -> build/libs/warp-afk-0.1.0-SNAPSHOT.jar
```

Or add `mavenLocal()` and depend on `org.purpurmc.purpur:purpur-api:26.1.2` via `https://repo.purpurmc.org/snapshots`.

## Configuration

`plugins/WarpAFK/config.yml` is copied from `src/main/resources/config.yml`. Key options mirror upstream:

- `unplugged.default-timeout-minutes`
- `unplugged.reset-health-on-death`
- `unplugged.disable-damage`
- `unplugged.hide-player`
- `messages.broadcast`, `hide-join-leave`, `kick-message`

## License

MIT (this port) — upstream unplugged-afk is LGPL-3.0 (see https://github.com/sakura-ryoko/unplugged-afk/blob/master/LICENSE).
