# Guardian arena prototype

## Current death flow — arena spectators

Fallen players automatically respawn as spectators inside the arena. They can fly
and see the boss health bar but cannot fight or cast. The arena closes when all
fighters fall. At the end, players return to their recorded gathering points and
original game modes. Disconnect/restart recovery preserves this return. The old
outside waiting-ground requirement has been removed. See
[the spectator update](guardian-spectators-2026-09-09.md); older outside-waiting
notes below are historical.
Current installed SHA-256: `96236d784118311681c98818ee70d073d726cf46831df357f9df830d2ce661ed`. Restart Lunar.


## Latest follow-up — stone statue, sky arrival, and safe respawn

The visible dormant entity is superseded by a custom vanilla-block effigy. Players
use its pedestal socket and rise alone; the living guardian drops from the sky
after the floor is complete, lands, then awakens. Respawn returns wait until the
connection has switched to the new player, with loaded and validated ground.
Installed in Lunar; source/installed SHA-256: `e89c65b88612b5244cfa3dffb2485fce6b7154aafb6045ed4dfeba975c24a239`. Restart Lunar.
Read [the follow-up report](guardian-arrival-2026-09-09.md). Earlier dormant-entity
notes below are historical. The church architecture and loot are unchanged.


## Agreed encounter direction

Players discover a ruined church with a broken block-built statue outside. A
Guardian Heart and note invite them to restore the statue and overcome its keeper.
Inserting the heart seals the gathered party into an unnaturally enormous arena:
walls erupt first, a patterned floor spreads outward, and the floor ascends within
walls reaching build height. There is no ceiling. The user now prefers a vast church
interior with ornate columns rather than relying on oversized block textures. The intended feeling is empty,
impossible architecture, with repeated church-like stonework and open sky overhead.

After victory the floor lowers and the walls withdraw, eventually revealing the
church restored to its original beautiful condition with treasure inside. The
church, statue, heart, restoration, and loot are now connected through the survival
encounter described in [guardian-church.md](guardian-church.md). The commands below
remain available for isolated arena tests.

Death eliminates a player for the rest of that encounter. They wait outside after
respawn. Rejoining survivors is explicitly deferred to a later session. Disconnects
also eliminate the player; logging back in does not bypass that rule.

## Try it in Lunar

Restart Lunar after installing the updated JAR. Use an Overworld location with open
sky immediately above the Guardian and gathered players. Other trees/buildings in
the large footprint are preserved; the final fighting floor is placed above them.
The surrounding five chunks in each direction must already be loaded, and very
high sites close to the build limit are rejected before anything changes.

Commands require operator permission:

```mcfunction
/summon elementalwands:fractured_guardian ~ ~ ~10
/ew guardian arena start
/ew guardian arena status
/ew guardian arena stop
```

`start` uses the nearest living Guardian within 32 blocks. The caller must be in
Survival/Adventure and within 20 blocks of it. The party is the eligible living
Survival/Adventure players within that 20-block gathering radius at activation.
Everyone must be standing on supported ground and dismounted. Finish a Hollow
Purple charge before starting. Peaceful and non-Overworld starts are rejected.

`arena stop` works from anywhere, including the server console, and safely returns
the group. During an arena, the old attack rehearsal/follow/fight controls are
disabled; `/ew guardian stop` near its boss also starts the return sequence.

Only one arena can run per server in this prototype. Status reports its stage,
survivor count, floor elevation, and wall height.

## Formation and combat

- The boundary seals immediately, before any wall animation. Players retain camera
  control but ride invisible cinematic carriers during formation and descent; spells pause during
  those cinematics without spending their cooldown or charge.
- Walls rise in 14 ticks (0.7 seconds). The floor expands over 46 ticks (2.3 seconds).
  The ascent takes 160 ticks (8 seconds).
- The interior is exactly 128 by 128 blocks. The final floor is at least 40 blocks
  above the gathering elevation, or 8 blocks above the highest block in its entire
  footprint, whichever is higher. At least 64 blocks of overhead clearance remain.
- The moving floor, lower tower skirt, and rapid walls are one synchronized visual
  entity. They do not replace original terrain. During the ascent, existing scenery
  can remain visible until the floor passes above it.
- One visible church-style shell persists through the entire fight: fluted quartz
  columns, layered bases/capitals, pointed arches, recessed luminous lancets, and
  a flat floor with standard one-block texture tiles and broad dark aisle inlays. All architecture stays at the
  perimeter. The floor and outer rim are a single batched top surface using vanilla
  block-atlas sprites at one texture repetition per block; expansion clips UVs rather
  than stretching them. There is no switch to a second visible block mesh at combat start.
- Invisible barrier-based backing provides the real floor and wall collision. It
  is unbreakable, explosion-resistant and immovable, with no item/drops, mob spawning,
  or light emission. The previous visible/luminous backing caused wall z-fighting
  and costly lighting/mesh rebuilds.
- Backing construction starts while the floor forms and continues through ascent,
  at 1,024 blocks per server tick. Arrival only waits if work remains. Players stay
  protected until the solid floor/walls are ready, then the Guardian awakens.
- Carriers and floor share the same motion curve and simulation-tick samples. Rider
  camera and entity-render previous positions are set together after passenger updates.
  This replaces per-tick player teleports and the Guardian's independent position lerp.
  The floor caches its entity-tick time because the client advances world time afterward.
- Sneaking cannot detach a passenger during the cinematic. Every completion, abort,
  death/disconnect, return, and server-stop path releases its carriers; loaded old
  carriers self-remove. Players receive a position correction only at handoffs.
- Wind movement and Space blinks remain usable in the interior. Same-world
  teleports, old rifts, pearls, and dimension travel cannot leave the encounter.
  Flight above the wall tops is corrected by the boundary; there is no visible roof.
- The Guardian's arena participant/boss-bar range is 192 blocks. Its navigation can
  reach the room's edges, and leap landings use the new arena bounds. Attack damage,
  reach, cooldowns, and health-scaling formula are unchanged. Normal encounters keep
  their previous bounds. The much larger room still needs human combat-balance testing.
- Temporary wand structures can be created inside the room; the foundation cannot
  be replaced by a spell. Ordinary block-item building is disabled in the combat room.
- Eliminated players and late arrivals cannot enter. A full wipe, Peaceful change,
  operator stop, boss death/removal, or missing boss ends the encounter.

## Return, persistence, and safety

Survivors descend for eight seconds. The walls withdraw over 1.5 seconds. Surviving
players return to their original gathering positions, or a nearby supported safe
position if those changed. Death drops and experience remaining in the room move
to the outside waiting point. An interrupted formation returns from its current
stage without first jumping to the final floor. A living Guardian returns passive
at full solo health with its previous gravity, invulnerability, AI, and follow range.

Before admission, the manager writes an atomic recovery receipt beneath the current
world save at `elementalwands/guardian-arena.json`. It records the originally empty
sky prism, Guardian home/settings, player return positions, and only the chunk force
flags added by this encounter. It never snapshots or overwrites buildings or containers.
The encounter keeps those chunks available until cleanup finishes.

Cleanup sweeps only that originally empty final sky prism, including temporary
spell remnants. It is spread over server ticks. World changes are saved before the
receipt is retired, and player data is saved before removing their return point.
Offline return records survive encounter completion. Restarting aborts the old fight,
returns players as they reconnect, restores the Guardian, removes the old structure,
and releases its own chunk flags while preserving pre-existing force flags.

## Validation and next playtest

The September 8 church/lift follow-up passed the clean build, required asset/package checks,
and both dedicated-server fixtures described below. It is installed in Lunar; see
[the handoff](MOD_HANDOFF.md) for the backup and checksum.

`./gradlew build` includes arena geometry, boundary, sealed-roster, and recovery-file
checks in `checkGuardianBeam`, alongside the existing Guardian regressions.

The runtime fixture additionally checks carrier ownership/dismount locking, shared
render-frame floor/feet coordinates, absence of per-tick teleport corrections,
invisible backing surfaces, and zero backing light emission.

Optional real dedicated-server tests use a separate disposable world under
`build/arena-smoke-run`, a localhost-only ephemeral port, and test-only player objects:

```sh
./gradlew -I tools/guardian_arena_smoke.init.gradle runServer --args nogui
./gradlew -I tools/guardian_arena_smoke.init.gradle -ParenaRecovery runServer --args nogui
```

The first command tests formation, a real combat floor, no ceiling, foundation
protection, teleport restrictions, actual death, respawn exclusion, victory return,
terrain preservation, and chunk ownership; it then stops a second encounter during
combat. The second tests recovery of that saved encounter. The runner requires the
existing accepted `run/eula.txt` and fails unless its completion marker is present.
Neither test fixture nor test metadata enters the release mod JAR.

The first regular-size floor build crashed when its first visible tile requested
`minecraft:textures/atlas/blocks.png` from `AtlasManager.getAtlasTexture`. That API
expects the definition ID `Atlases.BLOCKS` (`minecraft:blocks`); the render layer
continues to use the texture-file ID. This is corrected in `GuardianArenaFloor`.

A separate optional client check loads actual Minecraft textures in an isolated
game directory, exercises the production floor submission into real vertex buffers
at partial/full sizes, then exits without opening a world:

```sh
./gradlew -I tools/guardian_floor_client_smoke.init.gradle runClient
```

When the development asset download is unavailable, an existing Lunar asset cache
can be used read-only:

```sh
./gradlew -I tools/guardian_floor_client_smoke.init.gradle -PfloorClientAssets="$HOME/.lunarclient/shared/assets" -x downloadAssets runClient
```

The September 9 atlas fix passed this real-client check with the local Lunar cache.
Evidence was copied to `/private/tmp/guardian-floor-atlas-verification-20260909/`.

It verifies that the old lookup fails, the corrected atlas and sprites resolve,
all vertices lie at the correct height, tile sizes remain at most one block, and
the full floor/rim remains one batched submission. It does not measure full-fight
FPS or replace a human Lunar playtest.

These are server-side tests, **not a human client playtest or proof of the visual
experience**. Next in Lunar: judge the wall eruption, full-room visibility, moving
floor/camera smoothness, scale, flight and dash corrections, the existing leap,
performance with real clients, and return reveal. Then tune the prototype before
authoring church architecture or the heart ritual.
