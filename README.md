# WarpAFK — Unplugged-AFK port for WarpMC

Port of [sakura-ryoko/unplugged-afk](https://github.com/sakura-ryoko/unplugged-afk) as a **WarpMC Paper plugin** using the native `FakePlayer API` (`org.purpurmc.purpur.player.FakePlayer`).

> Upstream is Fabric + Mixins (LGPL-3.0). This port is a clean-room Paper plugin that *calls* `FakePlayerFactory` — no mixin code copied. Config semantics kept compatible with upstream's `unplugged_afk.json`.

## Features (upstream parity)

- **Go unplugged:** `/unplug [duration] [reason]` — disconnect yourself, leave a bot (same UUID/name/skin/pos). Default 129600m = 90 days like upstream. Also `/afk` alias (toggleable).
- **Admin:** `/unplugged-admin spawn <player> [duration] [reason]`, `kick`, `info`, `list [players|unplugged|all]`, `save`, `reload`, `purge`, `set`, `help`
- **Persistence:** Active bots saved to `plugins/WarpAFK/unplugged.json` with inventory/gamemode/health and restored on restart (timeout adjusted for downtime). Real login always wins — bot is evicted via `ServerLoginPacketListenerImpl`.
- **Options:** `disable-damage`, `hide-player` + `hide-from-ops` (per-viewer, like upstream), `reset-health-on-death`
- **Messages:** MiniMessage broadcast templates, `hide-join-leave`, `display-duration`, `display-return-feedback`, kick message
- **Compat:** Pl3xMap / BlueMap / Dynmap / squaremap via `Bukkit.getOnlinePlayers()` + world players — bots appear in tab and on maps when not hidden. Skins preserved via `GameProfile` textures. Chunk loading via `RegionizedPlayerChunkLoader` (with fallback plugin tickets).

## Commands

### Player
- `/unplug [minutes|duration] [reason]` — leave a bot and disconnect. Duration parser supports `30m`, `4h`, `1d`, `1d2h30m`, `90s`. Requires `warpmc.afk.unplug`.

### Admin (`warpmc.afk.admin`)
- `/unplugged-admin` — about
- `save` / `reload` / `purge`
- `spawn <player> [duration] [reason]` — spawn for online or offline player
- `kick <player|uuid>`
- `info [player|uuid]` — detailed bot debug (respects `reduced-list-debug-info`)
- `list [players|unplugged|all]` — `players`/`all` require `advanced-admin-options`
- `set <key> <value>` — requires `advanced-admin-options`

## Building

Requires WarpMC jars in local maven (from parent build):

```bash
# from warpMC root:
./gradlew publishToMavenLocal
# then in warp-afk:
./gradlew buildPluginJar
# -> build/libs/warp-afk-*.jar
```

## Configuration

`plugins/WarpAFK/config.yml` mirrors upstream — see `src/main/resources/config.yml` and upstream [CONFIG.md](https://github.com/sakura-ryoko/unplugged-afk/blob/master/CONFIG.md):

```yaml
main:
  enabled: true
  debug: false
  reduced-list-debug-info: true
  advanced-admin-options: false
unplugged:
  default-timeout-minutes: 129600
  reset-health-on-death: true
  disable-damage: false
  hide-player: false
  hide-from-ops: false
commands:
  enable-unplug: true
  enable-afk: true
messages:
  broadcast: false
  hide-join-leave: false
  display-duration: false
  display-return-feedback: false
  kick-message: "<gold>Your player will be AFK"
```

## How it works

Spawning snapshots pos/inventory/health/effects *before* `FakePlayerBuilder.spawn()` to avoid 0,0,0 and starter-kit bugs, then copies to the bot. WarpMC's `FakeServerPlayer` is a full `ServerPlayer` with `FakeConnection`/`FakePacketListener`; it ticks, takes damage (or not), respawns, and fires `PlayerJoinEvent`/`PlayerQuitEvent`. Regionized chunk loader now supports fakes so farms stay ticking.

## License

MIT (this port) — upstream unplugged-afk is LGPL-3.0.

## License

LGPL-3.0, same as upstream [unplugged-afk](https://github.com/sakura-ryoko/unplugged-afk), whose config semantics and feature set this port is derived from. See [LICENSE](LICENSE).
