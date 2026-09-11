# Arena spectators — September 9, 2026

Death now automatically respawns an enrolled player into Spectator mode, inside
the arena, after Minecraft finishes its player-replacement lifecycle. They can fly
around and see the boss health bar, but cannot cast, damage the boss, interact with
blocks normally, or rejoin combat. Spectators remain within the arena bounds.
They do not count toward surviving players or boss health scaling. Disconnecting
also eliminates a fighter; reconnecting during the encounter allows spectating.

When all fighters fall, the tower withdraws. Victory, party wipe, cancellation,
and restart recovery return players to their recorded gathering positions and
restore the original Survival/Adventure game mode. Normal death loot rules remain:
this is a real death followed by a spectator respawn, not a keep-inventory revive.
Items and late XP are brought back to the summoning ground during cleanup.

The old required outside waiting-area search was removed, along with its extra
chunk/world-border margin. The caller's already-supported starting point serves
as the drop-return point. Surrounding cliffs, water, or a missing outside landing
spot no longer block admission. Existing arena construction, height, and player
clearance checks still protect the formation itself.

Original modes are written atomically alongside return coordinates before admission.
The journal remains backward compatible with older receipts that contain no mode
changes. Offline players retain their pending return and mode until reconnect.
Player data is saved before their receipt is retired. Returns still load and
validate ground and never use an unchecked bottom-Y fallback.

Verification includes real Minecraft death/automatic respawn, spectator casting
and damage exclusion, bounded spectator teleports, visible boss bar, Adventure
mode restoration, victory, whole-party wipe, retry, admission with the previous
outside waiting area deliberately removed down to the void, disconnect, and
restart/rejoin restoring the original mode. This tests server behavior; the next
Lunar playtest remains the visual/client confirmation.

## Installed build

Installed in Lunar Fabric 1.21.10 after clean build, package integrity, asset/layout
validators, whitespace checks, and the real-server lifecycle/recovery tests passed.

- Source/installed SHA-256: `96236d784118311681c98818ee70d073d726cf46831df357f9df830d2ce661ed`.
- Backup: `/Users/antonlabas/.lunarclient/profiles/1.21/mod-backups/elementalwands-before-spectators-20260909-143105.jar`.
- Evidence: `/private/tmp/guardian-spectator-verification-20260909/`.
- Restart Lunar to load the updated code.
