# The First Wizard and the End

The final boss: a pool of living ink over the End that fights as a copy of the first player
to enter. Its design and the reasons behind it are in its
[design notes](../design/first-wizard.md). This page covers what is in the game.

It is being built in the order given in the design notes. What is not built yet is listed
at the end of each section and in [status](../STATUS.md).

## The End

The End belongs to this boss (the developer's decision, Nov 18; reasons in the
[design notes](../design/first-wizard.md#the-end-belongs-to-this-boss)). Built November 18,
2026, and checked by a hosted fixture only: nobody has walked it in Lunar.

**No dragon fight.** The End's server world drops the `EnderDragonFight` it makes
(`EndDragonFightMixin`), so there is no dragon, boss bar, crystal logic, vanilla exit
portal or gateway. Vanilla only ticks and saves a fight that exists, so the dragon-fight
data in level.dat is left as it was, and every other vanilla use of the fight already
allows for none. A dragon saved in an old world's chunks is discarded as it loads. New
worlds do not generate the main island's spikes: the `end_spike` feature is removed from
`minecraft:the_end` through Fabric's biome API. The biome's mob spawns are untouched, so
enderman farms off the island keep working.

**The arena.** The main island is rebuilt round the End's origin. Offsets are from the
centre block (0, 63, 0).

- **Fight floor:** every cell within 44.5 of the centre, flat at y=63, so players stand at
  y=64. End stone, patterned with end stone brick spokes and rings, purpur where they
  cross, an obsidian line near the edge and an obsidian centre. Nothing stands on it and
  nothing on it does damage. These numbers are the boss's contract (`EndFighter`).
- **Outside it:** a flush kerb of brick with a broken one-block wall (open to the south),
  a skirt that falls away into the natural island, and ten broken obsidian pillars 52 out,
  none due south, with their fallen pieces. No crystals, no cages.
- **Arrival:** (0, 64, 42), just inside the south edge, facing north.

`art/end_island/build_layout.py` authors it from fixed noise, not a seed. `--install`
writes nine 48x48 tiles and the way home's basin to `structure/end_island/`; `--check`
detects drift; the default run writes a rough top-down and side picture to
`.local-previews/end-island/`. It refuses anything on the floor, a hole in it, blocks
outside its palette, and a way home that a reset could not cover again.

**Laying it out** (`EndArenaManager`). It starts when the server starts, if the boss is
undefeated and the arena is not laid out with the installed tiles (a new world, a first
start after installing the mod, or changed tiles), so the first entry does not wait. In a
new world the whole run takes about fifteen seconds.

1. **The check**, one chunk column per tick, over everything the layout changes: the tiles'
   square (144 blocks across) from their bottom (y=39) up to the floor, and above the floor
   up to y=128 within 64 of the centre. It looks for block entities (chests, barrels,
   shulker boxes, furnaces, signs, hoppers, beds and the rest; vanilla's end portal and
   gateway blocks are not counted) and dragon eggs. It does not judge by block palette:
   endermen carry blocks onto the island.
2. **Held.** If any block entity is found, nothing is changed. The positions go to the log,
   to operators in chat (when they join, and each time someone is refused for it) and to
   `/ew end status` (twelve listed, then a count). The portal is shut until an operator
   clears them and runs `/ew end layout`, or runs `/ew end layout force`. Nothing checks
   again by itself; a restart does.
3. **Forced**, every block entity found is broken as a player breaks it: its drops fall,
   containers spill their contents, and a shulker box drops as one item holding its
   contents. Anything that appears during the clear is broken the same way. Nothing is
   deleted outright, and items already lying on the island are left alone.
4. **Dragon eggs** never hold the layout. Each is lifted out (the count is saved first, so a
   crash cannot lose one) and, when the layout ends, set down on the kerb just off the
   floor's south edge beside the arrival, side by side eastward from (2, 64, 45)
   (`EndArena.EGG_SPOT`). While the boss is undefeated that spot is protected, so a
   Survival player cannot take or teleport an egg until the win.
5. **The clear.** Tiles hold only built blocks, so what vanilla put there is cleared by
   code: one chunk column per tick, every block that is not air above the floor within 64
   of the centre; below the floor, the spikes' obsidian becomes end stone where the island
   round it is solid and air where it is not, and the bedrock fountain becomes end stone.
   End crystals are discarded, never detonated, and so is any that loads later while the
   boss is undefeated. Vanilla's twenty old gateways are removed.
6. **The tiles** go down, one per tick.

It never runs by itself after the win: from then the island is the players', and
`/ew end layout` refuses without `force`.

**Protection.** While the boss is undefeated, the footprint (72 from the centre) is held
like the other realms: Survival and Adventure players cannot break or place there, pour or
fill a bucket, light fire, use bone meal, or touch a dragon egg, and explosions and Hollow
Purple destroy nothing there. The server tells each client whether the island is held
(`EndArenaManager.GuardPayload`), so the client refuses the same block and bucket uses and
nothing flickers; a bucket is also refused through the item path, by its aim, for a player
standing in the footprint or aiming into it. Spells' temporary blocks work on the floor as
anywhere. Outside the footprint the End is ordinary, and after the win so is the island.

**The portal** (`EndGate`, through `EndPortalArrivalMixin`). Every trip into the End goes
by these rules; trips out of it, and the credits, are vanilla's.

- `EndGate.mayEnter` is the one place the five-shard key will live. Shards are not in the
  game, so it lets everyone through.
- Before anyone moves, their way back is saved: a standing spot beside the portal they
  used, two blocks clear of every portal block, facing it. With no safe spot near, the way
  back is world spawn.
- Players arrive at the arrival spot. Vanilla's obsidian platform is never made.
- **Nobody is left in a portal.** The End portal has no collision and a stronghold's has
  lava under it, so a refused player is moved in the same tick to the spot beside the
  portal their way back would have been (world spawn if there is none) and told why. A
  refusal saves nothing; an admission saves the way back once.
- Refusals: the record is unreadable; the five-shard key; Peaceful before the win (the
  boss does not wake on it, as at the Burned Gate); a fight of another party; having
  fallen in the running fight (until it ends); the arena not laid out yet ("try again in a
  moment"; the layout is started if it was not running); the layout held by block entities
  (the message says an operator must clear the island or force the layout).
- While a fight runs, members of the fighting party (anyone `PartyManager` allies with
  someone on its roster) go through and join it.
- **Not players.** A mount or vehicle carrying a player never goes through: its riders are
  taken off and refused as above, and the mount is set beside the portal with them. A
  thrown ender pearl is lost in the portal, so it never carries its thrower across. Other
  entities go through only when no fight runs and the arena is laid out.
- Creative and spectating players are refused only by an unreadable record, the key or an
  arena that is not ready, and never start a fight.

**The encounter** (`EndEncounter`; record in `elementalwands/end.json`).

- A Survival or Adventure player who arrives while the boss is undefeated and no fight
  runs starts one. The boss comes from the factory the boss registers
  (`EndEncounter.setBossFactory`), is placed once the floor's chunks tick, and wears that
  player's skin. The First Wizard then plays [its intro](#the-intro) and starts the fight
  when the scene ends, with whoever stands in it as the roster; a boss with no intro starts
  at once. Party members already on the island join; anyone else on it is sent back. With no factory, or if the
  boss cannot be made or placed, the player is told so and sent back.
- Members of the party who arrive later join through `EndFighter.addParticipant`. This is
  new: the other bosses seal their roster at the start. During the intro they watch it
  from where it has got to, and the fight takes them in when it starts.
- A fighter who falls keeps their belongings, wakes at their way back, and is out of that
  fight. Spectating is not built.
- A fighter beyond the floor's edge (with a little slack) or below its surface is set back
  on the nearest edge, facing in, the fall forgiven. Their teleports must end on the floor;
  nobody else may teleport into the arena while the fight runs.
- During a fight, vanilla mobs (endermen and the rest) are removed from the footprint and
  cannot spawn into it, and the arena's chunks stay loaded. A mob that is persistent,
  named, leashed, carrying a passenger, riding something or tamed is never removed, and
  neither are entities tagged `elementalwands.end_actor` (`EndEncounter.ACTOR_TAG`), for any
  server-side actor a scene may need. (The intro's dragon is drawn only on clients.)
- **Peaceful.** A fight running when the difficulty becomes Peaceful ends without a result
  and its fighters go to their ways back. The boss itself, if it went passive for
  Peaceful, engages again when the difficulty rises, and a kill while it is passive for
  Peaceful is not the roster's win.
- **A wipe** (nobody left standing): the boss is stopped, which clears what it left, and
  removed. The portal opens again.
- **A win** (`fightComplete`): `defeated` is saved, a basin of vanilla end portal blocks
  opens at the floor's centre (vanilla's own trip home and credits), and vanilla's gateway
  feature opens four gateways 96 out at the compass points, which find their own exits.
  Ten seconds later a boss still standing is stopped and removed. From then entering
  starts no fight and players arrive at the arrival spot.
- **A stop, crash, reload or relog** ends a fight without a result. A reload sends its
  fighters back at once; after a stop or crash they go on their next login. A boss left in
  the chunks by a crash is removed when the next fight places a new one.
- **Nobody on the island without the fight.** While the boss is undefeated, a Survival or
  Adventure player inside the footprint whom no fight holds is sent to their way back, or
  to world spawn if none is recorded: at login, and otherwise within a second (the check
  runs every 20 ticks). That covers a player who stayed after a stop and one who came by
  another way than the portal, such as an old return gateway from the outer islands or a
  command; they start no fight and need not have the key. Players elsewhere in the End,
  on the outer islands or the main island beyond 72 blocks, are left alone. An operator
  taken there by `/ew end enter` may stay until they leave the End or log out.

**Commands** (`/ew end`).

| Command | Who | Effect |
| --- | --- | --- |
| `status` | Operators | Layout (with what holds it, if anything), defeated or not, whether the intro's dragon is dead, the way home and gateways, the fight (and how far its intro has got), ways back held |
| `enter` | Operators | Records where you stand and takes you to the arrival spot. No fight starts, and you may stay. If the arena is not ready it says so (and starts laying it out); try again in a moment |
| `begin` | Operators | As if you had arrived through the portal: the fight starts for you. Survival or Adventure only, not on Peaceful |
| `intro` | Operators | The short intro (the condensing) again for the fighters where they stand, and the fight starts afresh after it; with no fight running it is `begin` for you, opening with the short intro |
| `intro full` | Operators | The same with the full scene, the dragon included, whatever the saved state |
| `reset` | Operators | Ends any fight, makes the boss undefeated, closes the way home and the gateways the mod opened |
| `layout` | Operators | Checks the island and lays the arena out again; stops if block entities stand in the way. Not during a fight; after the win it refuses and says what `force` would do |
| `layout force` | Operators | Lays it out regardless, breaking block entities as a player would (contents drop); after the win the way home stays open |
| `leave` | Anyone in the End | Takes you to your way back |

**Owners.**

| Concern | Owner |
| --- | --- |
| Floor, skirt, pillars, way home basin | `art/end_island/build_layout.py` |
| Geometry (in step with the builder) | `end/EndArena` |
| No dragon fight, no spikes, start-up | `end/EndTakeover`, `mixin/EndDragonFightMixin` |
| Laying out, protection, way home, gateways, record, moving players | `end/EndArenaManager` |
| The client's copy of the protection | `EndArenaManager.GuardPayload`, received in `ElementalWandsClient` |
| Portal rules and ways back | `end/EndGate`, `mixin/EndPortalArrivalMixin` |
| Roster, fight lifecycle, recovery, mobs | `end/EndEncounter`, `mixin/EndArenaSpawnMixin` |
| Explosions | `mixin/EndArenaExplosionMixin` |
| The seam with the boss | `end/EndFighter` |
| Commands | `command/EndCommands` |

**Verification.** `py -3 art/end_island/build_layout.py --check` verifies the tiles. The
fixture `end/EndTakeoverSmoke` (in the test source set) runs through
`integrated_fixture_host.init.gradle` with `-PfixtureMain=com.anton.elementalwands.end.EndTakeoverSmoke
-PfixtureReceipt=END`, against a stand-in boss and simulated players; what it asserts is
written in the class (among it: refused players set beside the portal, a chest and shulker
box holding the layout and then dropping everything when forced, eggs set down, the
outer-island player left alone, buckets, named and tamed mobs, a mounted player and a
pearl, Peaceful). It checks the client's copy of the protection flag only on the
integrated host. It shows the rules in a fresh hosted world, not how the island looks.

**Not built.**

- The five-shard key (`EndGate.mayEnter` lets everyone through).
- Spectating for the fallen, rewards, and a hint at the portal while a fight runs.
- The fallen pillars' look and the floor's pattern have only been seen in the builder's
  rough picture, not in the game.
- Mods that rework the main island or the dragon fight will clash; that is accepted
  (design notes) and belongs on the mod page.

## The fight

Both phases are built (November 18, 2026): phase one's ten spells, and at half health the
change to phase two, its floating body with its tentacles and its five ink moves. It has been checked by the
contract check and a hosted client fixture; nobody has fought it in Lunar. Its health,
speeds and timings are first guesses.

**Summoning.** `/summon elementalwands:first_wizard` makes a copy that fights on a round
floor as wide as the End's, centred where it was summoned, against any Survival or
Adventure player within 34 blocks, and wears the nearest one's skin. In the End the
encounter makes it (`EndEncounter.setBossFactory`, registered in `ElementalWandsMod`)
and starts it with `startFight`.

| Command (operators only) | Effect |
| --- | --- |
| `/ew wizard fight` | The nearest copy within 64 blocks fights whoever is nearby |
| `/ew wizard calm` | It stands passive and drops the spell in hand; what it already cast burns out |
| `/ew wizard move <name>` | It casts that one move at you, then stands passive. It only hurts in Survival or Adventure; a move that is not built is refused, and so is an ink move before phase two. A rehearsed Hollow Purple ignores its health and wait |
| `/ew wizard phase two` | It is brought down to half health and the change to phase two starts as soon as the move in hand allows, fighting or passive (a passive copy plays it, then stands passive in phase two); its cinematic takes the camera of every player in the fight |
| `/ew wizard status` | Its phase, move in hand, health, lingering effects, roster and the moves built |
| `/ew wizard stop` | It stands passive and everything it left is cleared |

The names are `fireball`, `meteor`, `sky_shear`, `waylay_dash`, `stone_wall`, `faultline`,
`thornbite`, `sentry_bloom`, `blink_rift` and `gravity_well`, and phase two's `slam`,
`sweep`, `leap`, `flame` and `hollow_purple`.

**The copy.** It is the player's size (the design's wizard duel) and holds the Wizard's
Wand in the skin owner's main hand.

- **The skin is carried, not looked up.** The copy holds the owner's profile in synced
  data, as the vanilla mannequin does, and clients resolve it through the game's skin
  cache. It keeps the skin for the whole fight, slim arms and outer layers included, when
  its owner dies or logs off.
- **A plain skin with ink floating round it** (`FirstWizardRenderer`, `client/blockink/CopyFx`;
  the developer asked for the skin left alone, Nov 18). The skin is drawn as the player's
  own, with nothing over it. What shows it is a copy is a loose cloud of ink motes, about one
  in seven violet, in the block style: each rises from somewhere on its body, drifts outward
  and slowly round it, and shrinks away above its head. They are sparse enough that the skin
  is plainly seen. Ink, not Space's stars or glass.
- **It stays findable.** The skin is never lit below block light 10, so the copy reads at
  midnight, and the violet motes are drawn full-bright.
- **Poses are a player's:** it walks with its steps, raises its wand arm overhead while a
  spell forms and swings it at the release.

**Free between spells** (`FirstWizardCombat`). It holds a middle distance from its target
(7 to 15 blocks): it walks in when further, backs off when nearer, and strafes between,
changing sides every two to four seconds and at the floor's edge. It steers straight, with
no pathfinding, turning at most 12° a tick. A sprinting player outruns it. It stands still
while a spell forms. Nothing staggers or interrupts it (the `WandBoss` rule).

**It never leaves its floor.** It keeps itself 1.5 blocks inside the edge and is set
straight back if anything puts it outside; every spot a spell picks is clamped inside too.

**How it chooses** (`FirstWizardRules`). When its gap runs out it weighs every built spell
whose range holds its target's distance, tilted by what the target just did, and picks one
by chance. It never casts a spell three times running, and seldom twice. Nothing fits
(only spells out of range are built): it moves and looks again a quarter of a second later.

| Spell | Element | Meant for (blocks) | Weight | Built |
| --- | --- | --- | --- | --- |
| Fireball | Fire | 6–34 | 5 | yes |
| Meteor | Fire | 8–40 | 3 | yes |
| Sky Shear | Wind | 4–22 | 4 | yes |
| Waylay Dash | Wind | 8–30 | 3 | yes |
| Stone Wall | Stone | 6–30 | 2 | yes |
| Faultline | Stone | 10–26 | 4 | yes |
| Thornbite | Nature | 0–14 | 4 | yes |
| Sentry Bloom | Nature | 6–40 | 2 | yes |
| Blink Rift | Space | anywhere | 1 | yes |
| Gravity Well | Space | 6–28 | 3 | yes |

What the target just did tilts the weights:

- **Crowding it** (anyone within 4.5): Blink Rift heavily, Thornbite.
- **Running at it:** Faultline, Sky Shear, Stone Wall. **Running away:** Fireball, Waylay
  Dash, Gravity Well.
- **Standing still** for 1.5 s: Meteor, Faultline. **In the air:** Fireball up, Sky Shear
  down (they would jump it). **Eating or drinking:** Fireball, Meteor, Waylay Dash.

**The opener** is the skin owner's element's: Fireball for Fire (and for no element), Sky
Shear for Wind, Faultline for Stone, Thornbite for Nature, Gravity Well for Space. While
that spell is not built it opens with the nearest built attack: the same element's other
attack first, then the nearest in the table. Blink Rift only moves the copy and never
opens. Today every element opens with the Fireball.

**Combinations.** The design's three are scripted follow-ups, cast 0.15 s after the first
when it is built: Stone Wall then the Fireball (75%), a Gravity Well then a Meteor
centred on the well, landing while it still pulls (50%), Blink Rift then Thornbite (70%).
A fourth is not the design's: Faultline then a Fireball at the player it threw (50%).

**Gaps** between spells are 0.45 to 0.9 s alone. Each player after the first shortens them
by 15%, up to four players (the roadmap's co-op pressure).

**Targets in co-op.** It keeps its target through a spell and picks again when the spell
ends, never the player it just cast at; nearer players are likelier. A puff of ink over a
player's head marks a new target.

**A spell's parts.** Every spell forms in the open, then its aim locks, then it is released,
then the copy is free. Forming starts at once and lasts at least half a second; the aim
locks no earlier than forming begins and no later than the release. A sidestep after the
lock dodges it. What a spell leaves (a flight, burning ground, later a falling meteor, a
flower, a well) runs on after the copy is free and is cleared with everything else when
the fight is stopped, when the copy dies or is removed, and when the server stops.

**Damage** is decided on the server, by the spell, and only to players the encounter
admits. It is sized like the Pyre King's for plain diamond to netherite armor: a heavy hit
(21 raw) costs about 8 of 20 health there, a medium one (16) about 5.5, and a second on
lingering ground (8) about 2. Spell hits use `first_wizard_spell`, which a raised shield
stops (and is knocked aside for 3 s); lingering ground uses `first_wizard_ground`, which
ignores shields. Neither is fire damage, so Fire Resistance does not help.

**Fireball** (`entity/firstwizard/spell/FireballSpell`). Ink rises off the wand arm and gathers over the copy's
head; for 1.2 s it swells into a ball 3 blocks across, taking fire's colour from a fifth of
the way in. Its aim locks a quarter of a second before the throw, leading a moving target
by where they will be when it arrives. It flies at 16 blocks a second toward their feet and
on past to the floor if they have stepped aside. The body it touches takes a heavy hit;
anyone else within 2.5 of the burst a medium one. The burst leaves burning ground 5 blocks
across for 5 s: low licks of flame for its first half second, then flames that burn on
contact and once a second. The flames are the copy's own, drawn by its client drawer from
the Fire kit's cubes with purple tips and black ink where smoke would be; they are not
blocks, so nothing spreads or is lit. Every tongue of the ball and its flight cools the
same way, and it sheds ink as it flies.

**Meteor** (`entity/firstwizard/spell/MeteorSpell`, drawn by `client/blockink/MeteorFx`). The
copy raises its wand for a second while a knot of ink gathers over it; then a circle 9
blocks in radius is marked on the floor where its target will be half a second on, and the
ink is flung up to form a stepped ball of fire 16 blocks across (twice the player's) high
over the mark. Half a second later it drops and falls for 3 s; the copy is free a moment after.
After a Gravity Well (the combination) the circle is centred on the well instead. The mark
is an ink ring with a violet edge, a dashed purple ring round the inner part, and a shadow
of ink that fills in from the middle as the meteor nears; the edge flashes in the last half
second. The impact is a heavy hit within 4.5 of the middle and a medium one out to the rim
(a jump does not clear it), then the inner part burns for 4 s like the Fireball's ground.
Dodge it by leaving the circle: from the middle at a sprint there is time to spare.

**Sky Shear** (`entity/firstwizard/spell/SkyShearSpell`, drawn by `client/blockink/SkyShearFx`).
Three crescents of ink form stacked over the copy's head for 0.9 s, taking Wind's pale
colour. Then they leave 0.7 s apart, each aimed at where its target stands as it leaves: it
drops to the floor in front of the copy, widens to 14 blocks across over its first few
blocks and runs along the floor at 20 blocks a second for 40 blocks or to the floor's edge,
then falls apart into ink. Each has a solid pale rim at its front, see-through air behind,
a band of ink behind that over a flat ink shadow, and echoes trailing it that cool through
purple to ink and splat on the floor. A crescent strikes a player whose feet are less than
0.6 over the floor as it passes, a medium hit once per crescent; nobody shelters anyone.
**Jump it**: a jump started 1 to 6 ticks (up to 0.3 s) before a crescent reaches you clears
it; earlier or later does not. The same timing clears all three, and a player who jumped one
too late lands with a tenth of a second to spare for the next. Sidestepping does not work:
they are as wide as a third of the floor. The gap is longer than the "under half a second"
first asked for, because a vanilla jump lasts 0.6 s: closer crescents could not each be
jumped, one late jump would doom the next, and the hurt cooldown would swallow a hit.

**Waylay Dash** (`entity/firstwizard/spell/WaylayDashSpell`, drawn by
`client/blockink/WaylayDashFx`). Its line is fixed as it begins: through its target and 10
blocks past, 12 to 26 blocks long, ending inside the floor and short of anything solid. For
half a second ink runs along the floor on that line, pale streaks of wind skimming it, while
wind gathers behind the copy and cools to ink. Then it crosses the line in 0.4 s (moved a
step a tick through `Cast.step`, so clients slide it) and ends facing back toward its target.
Anyone it passes through takes a medium hit, once. Its path stays a low wall of churning
wind and ink two blocks wide over a pool of ink, pale where it has just passed and cooling
to black; it arms half a second after the dash starts and burns like lingering ground for
3 s more (a `GroundZone` strip). **Step off the line during the tell**, and out of the wake
sideways: one step leaves it. It is also how the copy closes on a player who runs away.

**Blink Rift** (`entity/firstwizard/spell/BlinkRiftSpell`). Ink pools at the arrival point for half a second
(rings of Space's colour cooling to purple and black on it), while the copy stays where it
is; then it sinks away into a pool there and rises in a column of ink at the arrival,
which drains. Crowded, it goes to the spot on the floor furthest from everyone. Otherwise
it goes round its target to the side at its middle distance; once Thornbite is built it
goes behind them instead, 5 to 7 blocks past, to set Thornbite up. It does no damage.
Clients snap it to the arrival rather than sliding it across the floor.

**Gravity Well** (`entity/firstwizard/spell/GravityWellSpell`, drawn by
`client/blockink/GravityWellFx`). A dark orb of Space's colour forms out of ink over the
copy's head for 0.9 s and is lobbed in a one-second arc to where its target stood as it was
thrown. Where it lands a well opens flat on the floor: a segmented ring at its 11-block
reach, a spiral running in across the floor that cools from Space's colour to purple and
ink, a dashed ring at the burst's edge and a low ink core. For 4 s it pulls everyone in
reach toward its middle and does no damage; a 0.8 s buildup (still pulling) speeds the
spiral and flashes the burst ring, then the middle bursts for a heavy hit within 5. The pull
is added on each player's own client to their movement, through the Guardian's pull
payload, so input still counts: walking outward loses ground, sprinting outward gains a
little, and a dash, blink or leap gets out easily. One well at a time.

**Stone Wall** (`entity/firstwizard/spell/StoneWallSpell`, drawn by `client/blockink/StoneFx`;
its block `block/FirstWizardWallBlock`). Ink runs out along a line on the floor for a second:
across the line from the copy to its target, a few blocks behind where they are heading,
about 27 long and kept inside the floor. Then a wall rises there a row a tick, 5 tall and 2
thick: real blocks of the player's stone veined with ink and violet, which stop players,
their spells and the copy's own (its Fireball bursts on it). It fills only air, so the
floor and anything a player placed are never replaced. A player on the line as it rises
takes a medium hit and is set down on the nearer side. It stands 6 s, ink running down its
faces, then crumbles from the top into stone that cools to ink as it falls and splats.
Players cannot mine or blow it up anywhere. Stopping the fight, the copy's death or removal
and the server stopping take it down at once; after a crash each block clears itself, by a
block tick saved with its chunk (a running wall holds its blocks in memory, so a newer wall
on the same cells is never taken down by an older one's tick). Dodge it by getting across the line, or along it to an end,
while the ink runs; then the Fireball that follows comes down the open lane.

**Faultline** (`entity/firstwizard/spell/FaultlineSpell`, drawn by `client/blockink/StoneFx`).
Ink runs out from the copy toward its target for 0.9 s and shows the whole wave: its two
edges and veins of ink between, 5 blocks wide at the copy and 12 by 50 blocks out, to the
floor's edge. It follows the target until 0.3 s before the release, when its aim locks and
the edges flare violet. Then the player's Faultline spikes, at boss size (about 2.4 blocks
tall), erupt down it at 30 blocks a second and cool to ink and sink. Anyone it reaches takes
a medium hit once and is thrown about 7 blocks up; the fall from the throw does not hurt.
A jump does not clear it; a sidestep after the lock does (from 10 blocks out a sprint from
its middle clears the edge in time). Anything solid in its way, such as a Stone Wall, stops
the lanes it blocks. The copy is free 0.3 s after the release, and half the time throws a
Fireball at where the thrown player will land.

**Thornbite** (`entity/firstwizard/spell/ThornbiteSpell`, drawn by `client/blockink/ThornbiteFx`).
The player's stepped flytrap and braided vines at boss size, in the Nature kit's colours
(`NatureInk`, the frame's `nature` mesh). For 0.8 s it gathers out of ink over the copy's wand,
just above and ahead of its head, and takes Nature's green as it grows into a mouth 3 blocks
across, turning with the copy toward its target. Its aim locks 0.2 s before the lunge, on the
line to where the target stands then. It lunges up to 16 blocks along that line in about a
third of a second, dropping to body height by the target's distance, and snaps on the first
player within reach: a heavy hit. A bite sends a pulse of gold back along the vines, and when
it reaches the copy it is healed by 4% of its maximum health, with sap leaves and gold spiralling
up round it. A miss, a shield or a wall heals nothing. It holds shut a moment and draws back,
the vines and then the mouth cooling through purple into ink. **Step off the line after the
lock**: from 5 blocks a walk is enough, from 3 a sprint. After Blink Rift lands behind a
player it is the follow-up: the bite then comes from 5 to 7 blocks behind them, with the
same 0.8 s of forming to turn and move.

**Sentry Bloom** (`entity/firstwizard/spell/SentryBloomSpell`; the flower
`entity/firstwizard/InkBloomEntity`, drawn by `client/renderer/InkBloomRenderer`; the rest by
`client/blockink/SentryBloomFx`). A bud forms out of ink over the copy's head for 0.8 s; its
spot is fixed at 0.5 s, when ink starts to pool there, ringed with green shoots, and the bud is
lobbed there in a high arc: 9 to 13 blocks from the copy, off to one side of its target and as
far from every player as the floor allows. A flower about 6 blocks tall unfurls there over
1.5 s: the player's Sentry Bloom head mesh at about 3.4 times its size on a tall stem of the
player's square beams, stained with ink from the floor up behind a violet line. It then spits a
seed every 1.5 s at a player it has a clear line to, taking turns in co-op, its head nodding
toward whom it will shoot. Seeds fly straight at 12 blocks a second and lead a moving target;
a sidestep dodges them. A seed that hits is a light blow (the lingering ground's 8) and stores
2% of the copy's maximum health in the flower, up to 10%: five sockets up its stem light gold
one per hit, the light climbing from the roots, so how full it is reads from across the arena.
**Break it**: it is a hostile mob to the players' spells, weapons and their own Sentry Bloom
flowers, with 50 health (30 more for each player after the first), and only the fight's
players can hurt it; it cannot be moved. Broken, its petals tear off, its light falls dark and
everything it stored is wasted. 12 s after it opens a flower still standing delivers: what it
stored flows in an arc of gold into the copy and heals it (breaking it while it flows still
wastes it), and it wilts into ink. One flower at a time, and not again for 20 s after one ends;
that wait is one of the fight's leftovers, so stopping the fight clears it.

**Around the fight.**

- A purple boss bar shows its health. Health is 1000, plus 60% for each player after the
  first.
- **In the End** it fights its roster only, and only they can hurt it. A late party member
  (`addParticipant`) grows it to the larger party and keeps its share of health.
  `fightComplete` is true once it has died fighting. `stopFight` stands it passive and
  clears everything it left.
- **Summoned**, it grows to however many players are near, and with nobody near for 10 s
  it resets to full health and waits.
- A saved copy keeps its skin and floor. One that fought for the End loads passive and
  waits for its encounter.
- It drops nothing, gives no reward, has no death animation and only vanilla placeholder
  sounds: those are parked with the other bosses'.
- Phase two is below.

### Phase two

At half health its left arm falls off, its left flank swells and bursts, and three tentacles
come out of it (the developer's idea; reasons in the
[design notes](../design/first-wizard.md#phase-two)). The face and the wand arm stay human:
the human side casts, the ink side strikes. The wound is always her left and the wand is in
her right hand from the change on, whichever hand the skin's owner uses (the developer, Nov 19).

**The change** (`spell/PhaseChange`, started by `FirstWizardCombat`), every time the fight
reaches half health. Built November 19 and seen only in the fixture: nobody has watched it in
Lunar.

- In phase one its health never goes below half, however hard the blow (a `/kill` excepted):
  it is held there until the change begins, which is when the spell in hand has finished.
  Nothing hurts it while the change plays, cinematic, tell and sweep alike.
- **The fight pauses.** As it begins, everything phase one left is cleared at once (burning
  ground, a falling meteor, a well, a wall, a flower, a follow-up that was coming), so nobody
  is hit by it when control returns. Everyone in the fight watches a cinematic
  (`FirstWizardChangeScene`): their camera is the scene's, the HUD gives way to a letterbox,
  their body is held where it stood and turned toward the copy (a player caught in the air
  sinks to the floor, and none of it is a fall), they cannot be hurt (only `/kill` and the
  void reach them), and they cannot hit, cast or use anything. The copy turns to face the
  players' middle and holds still.
- **The cinematic,** 17 seconds (`PhaseChange.CINEMATIC`), in eight shots: the blow throws it
  back; its left arm is wrenched back and out and jerks in twitches while ink beads out of the
  seam at the shoulder; the arm comes away from the socket on strands of ink that stretch and
  snap, drops, tumbles and lands by her feet (a real arm in the skin, sleeve included), lies
  there a moment, darkens through a bruise of violet into ink and melts into a pool that
  soaks into the floor. Then her left flank swells in five slow surges on a heartbeat, lumps
  of her own shirt pushing out of her side with veins of ink under them and ink oozing
  through; as it swells her head bends slowly to her right, away from it, further than a neck
  goes (about 64 degrees, with a twitch or two), the camera holding on her face. The
  swelling bursts, the tentacles erupt out of it, and she lifts off the floor into the float.
  The camera glides back to each player for the last second.
- **Skipping.** Held Sneak skips it for that player, who gets their view back while the
  others watch (still held); it ends early only once every watcher has skipped, no sooner than
  a second in, as the intro's skip works. The copy is then at the cinematic's end at once and
  the tell follows.
- **Who watches.** A player who joins the fight while it plays (a late party member, a player
  who walks onto a summoned copy's floor) is taken in and watches from where it has got to.
  One who leaves the fight (logs out, dies, goes to another world, stops being a fighter) is
  let go at once and never rejoins held; with nobody left fighting the copy is in phase two at
  once.
- **Then the fight resumes,** a fifth of a second after control returns: the weapon lowers to
  the floor at full reach, its reach is ringed in ink, and the violet ripple runs up it; then
  the one set piece: it sweeps a full circle low across the floor round the copy in a second
  and a half, 9 blocks out, starting on its left. Feet below 0.6 as it passes take a medium
  hit: **jump it**. Under its arch (within 1.5) is safe, as under the Pyre King's chain. A
  player standing where the circle starts and ends is passed twice and jumps twice.
- Phase two begins when the sweep is done, 20 seconds after the change began. Cut short (the
  fight stopped or calmed, the copy removed, everyone gone, the server stopping) the
  cinematic lets everyone go first and the copy is in phase two at once, never between. A copy
  saved during the change loads in phase two; one saved while held at half health loads held,
  and changes when it next fights. A summoned copy that resets goes back to phase one.
- Sounds are vanilla placeholders: a crack at each big twitch, a wet tear and snap, a thud as
  the arm lands, a heartbeat under each surge, the neck cracking, and the burst.

**The body.** Drawn by `client/renderer/FirstWizardRenderer` (the model, the torn arm, the
swelling, the head, the wound's ink), `client/blockink/ChangeFx` (the ink at the shoulder, the
strands, the melting arm's pool, the ink oozing from the swelling) and
`client/blockink/TendrilFx` (the tentacles and the wound; their rest pose is `TendrilRest`, what each does in a move
`TendrilMoves`), from `client/blockink/WizardBody`:
during the change everything comes from the change's baked track
(`FirstWizardChangeTrack`), on the change's synced clock, so everyone near sees the same body
whether or not their camera is in the cinematic.

- **The arm** is drawn on its own from the snap, the skin's arm and sleeve in the skin, where
  the track has it; as it melts it is tinted dark and flattened into the floor.
- **The swelling** is three boxes of the torso's own skin (they take the texture of its left
  half, so the shirt itself stretches) pushing out of its left side, the torso widening toward
  them, under a veins layer (`art/first_wizard/build_ink.py`, `first_wizard_veins.png`) that
  darkens in as it swells.
- **The head** stays bent to her right through phase two, at about 46 degrees (a little less
  than the cinematic's peak, the face still readable), the neck slipped toward that side, and
  twitches now and then (every eight to eleven seconds).
- **The wound.** From the burst her left arm is gone, the torso narrowed toward the wand arm,
  and its left side covered in ink with a ragged violet edge (the wound layers from
  `art/first_wizard/build_ink.py`, one per arm width). The rest of the skin stays plain; the
  motes float round it as in phase one.
- **It floats** (the developer, Nov 18). Nothing carries it: it hovers 0.9 off the floor with
  a slow bob, its legs hanging relaxed. The entity itself stays on the floor; its box grows
  to 0.8 wide and 2.7 tall so it covers the floating body. It drifts rather than walks: the
  server eases it into and out of each move, at up to 5 blocks a second (its walk was 4.3),
  and it holds 3.5 to 8 from its target (7 to 15 in phase one). A sprint still outruns it.
- **The tentacles: huge octopus arms from her left flank** (the developer, Nov 18 to 19). Three
  of them, each eight to nine blocks long, about 0.4 thick where it leaves her left flank and
  tapering to a point. They are drawn as smooth tubes, a ring of faces swept along each one's
  curve and shaded round it, in the ink atlas's matte black with a violet line down the back
  and a row of pale violet suckers along the underside. No joints, stars or glass.
- **At rest they are spread apart** (the developer, Nov 19: "spread out instead of jumbled",
  one bent up or down, a little thinner; the pose `client/blockink/TendrilRest` was chosen on
  Nov 20 from three designs). They leave the wound together and part at once, like an
  octopus's arms hovering in open water, each in a space of its own and never across her
  torso or face seen from the front or her right:
  - one hangs down in front of her left side, sweeps out low over the floor some five
    blocks, and lifts its tip a little, curling it forward and back;
  - one reaches out and back at shoulder height in a long S, its tip curling up behind her;
  - the weapon rears up from her left shoulder, leaning forward, and hooks forward and in
    high over her head like a scorpion's tail.

  Slow waves travel along each from the root to the tip, each at its own rhythm; the tips
  quest, and each outer third slowly curls tighter and opens again. Each bends in plan as well
  as in height, so no side sees a whole tentacle end on; from close behind her left the
  shoulder-height one still folds into a coil in front of the weapon's stem. The fight
  fixture's `-PwizardPose=<Name>` draws a design study (`TendrilPose<Name>`) instead.
- **The whole body works in every move** (the developer, Nov 20: "I want the tentacles to be
  a part of its attacks not just freeze"). Each tentacle springs from its rest pose into what
  the move asks of it and back, on a spring of its own that overshoots a little and settles;
  a move that follows straight on from another passes from one pose into the next. The blend
  is of how each tentacle bends along its length (`TendrilCurve.blend`), so it sweeps from
  one pose into a very different one instead of folding into a little loop near her. Nothing
  holds still: a held pose keeps its waves, and through the long holds (the Flame's breath,
  Hollow Purple's forming) the arms heave, sway and weave slowly and widely enough to read
  from across the floor. An arm planted on the floor stays put in the world while she moves
  or turns, apart from its deliberate re-grips (if she turns far from it, it lifts and plants
  again, one at a time), throwing up a little ink where it strikes. They never cross her
  front or dip below the floor. The moves are below; while the
  human hand casts one of the ten spells they flare out and up round it, the weapon's tip
  curling toward the gathering ink, recoil as it is thrown and spring back, and stream behind
  her through a Waylay Dash.
- One tentacle is the weapon. For a Slam it uncoils from its hook and rears, then whips down
  along the line and lies there; for a Sweep it swings low round the arc, trailing behind its
  tip. It then springs back into its hook. Every point comes from the move's own clock with
  the partial tick, and the strike blends in and out on its own spring over whatever else the
  weapon is doing, so nothing steps from tick to tick. Meanwhile the other two plant behind
  her as counterweight for the Slam, leaning back as it rears and jolting as it lands, and
  rise high out of the Sweep's path, turning back against the swing.
- **The tell.** Before every tentacle attack a violet ripple runs from the shoulder to the tip,
  swelling it as it goes.

**How it chooses in phase two.** It keeps the ten spells, from its human hand; within 9 blocks
they weigh 0.3 of their weight, so up close the tentacles dominate and at range it mostly casts.
Its gaps are 0.65 of phase one's. Leap then Flame is a scripted follow-up (75%).

| Move | Meant for (blocks) | Weight | Notes |
| --- | --- | --- | --- |
| Slam | 0–11 | 6 | More when the target is in the air |
| Sweep | 0–8.5 | 6 | More when the target runs at it or crowds it; less when they are in the air |
| Leap | 5–18 | 5 | More when the target runs away or eats |
| Flame | 0–7 | 2 | Mostly as the Leap's follow-up |
| Hollow Purple | 6–60 | — | Only at or below a quarter of its health, and not within 25 s of the last. It is not weighed: once it may come and its target is in range, it is the next move |

**The marks on the floor** of phase two (the change's ring, the Sweep's arc, the Slam's line,
the Leap's circle) are flat decals laid just above the floor in the floor-ink textures: a strip
repeated along the mark with a rounded cap at each open end, closed into a ring for a ring,
and a pool of ink for the Leap's landing. Each has a glow layer of its violet rim and veins,
drawn full-bright: faint while the mark only warns, then bright and pulsing once the strike is
coming (the Slam's lock, the Sweep's swing, the Leap's last half second, the change's sweep).

**Slam** (`spell/SlamSpell`). The weapon rises high over it while a line 12 long and 2 wide is
marked in ink toward its target, following them no faster than the copy turns. At 0.7 s the
aim locks and the line's edges flare violet; at 1.1 s the tentacle comes straight down along
it: a heavy hit to every body on it. A jump does not clear it. **Sidestep after the lock**: a
walk of a block and a half is enough. It lies there a moment, then draws back.

**Sweep** (`spell/SweepSpell`). The weapon draws back low on the wound's side while the ripple
runs; at 0.7 s its arc is fixed and marked at its reach, 100° either side of the line to its
target, and at 0.9 s it swings through the arc in half a second, 9 blocks out. A medium hit to
feet below 0.6. **Jump it** (a jump started 1 to 7 ticks before it arrives clears it), or be
out of its reach, behind it, or under its arch.

**Leap** (`spell/LeapSpell`). The tentacles throw her: two plant their tips on the floor behind
and beside her and load like springs, bunching into an S as she sinks, while the weapon rears
toward the landing, and the ripple runs along all three; at 0.5 s a circle 3.5 in radius is
marked where its target will be when it lands (led by their movement, at most 18 away, on the
floor), and at 0.8 s the planted two straighten and shove her off on an arc 3.5 high, their
tips pinned a moment as she rises before they peel away. In the air all three stream behind
her like a squid's arms, then swing forward, spread and reach down for the circle, and as she
lands 0.8 s later they slap down round the pool's rim and spring back to rest: a heavy hit to
every body in the circle, which a jump does not clear. **Leave the circle**: from its middle a
walk is enough. It lands facing the way it flew.

**Flame** (`spell/FlameSpell`). It roots itself: two tentacles reach wide and grip the floor
round her as an octopus's arms do, one before her and one behind. Each rises from her flank
into an elbow that heaves and sways, drops onto the floor, lies along it and curls its tip
into a grip. They jolt at each of the breath's half-second burns, and twice in the breath
each slips a little further out and grips again. The weapon arches high behind her head like
a scorpion's tail, its sting aimed down the cone; while she breathes it weaves like a cobra's
head, side to side and up and down, lags behind her as she turns, its sting flexing, and
pumps forward into each burn. They rip free as the breath ends, tips first. Fire gathers in its human
hand for half a second, then it breathes a cone of its own fire (the Fire kit's flame,
orange at the base, purple at the tips, ink where smoke would be) 9 blocks long and 24° each
side for 2.5 s. From the start of the cast to its end it turns at most 2° a tick after its
target and cannot move. Standing in the cone costs 8 every half second (first_wizard_ground,
which ignores shields): about 4 health a second in diamond or netherite against burning
ground's 2, and the whole breath is no kill there. Only at a target within 10. **Get behind
it**: a player who dodged the Leap's landing round behind it is never turned on; sprinting
round it close in outruns the turn. Drawn by `client/blockink/InkMovesFx`, with the marks of
the Slam, Sweep, Leap and the change.

**Dragon's breath.** When the breath ends it leaves a cloud of cooled breath 5 blocks out along
the cone: a vanilla area effect cloud owned by the copy, radius 3, for 15 s, with dragon's
breath particles and no effect, so it hurts nobody. A glass bottle used in it gives dragon's
breath and shrinks it, as vanilla's dragon cloud does: vanilla only bottles a cloud whose
owner is the Ender Dragon, and `mixin/InkBreathBottleMixin` lets one owned by the First
Wizard pass too. It carries `EndEncounter.ACTOR_TAG`, so the End's encounter keeps it, and is
cleared with everything else the copy left (`Cast.adopt`).

**Hollow Purple, the finisher** (`spell/HollowPurpleSpell`, drawn by
`client/blockink/HollowPurpleFx`). Only at or below a quarter of its health, and not within
25 s of the last. Two tentacles grip the floor wide, as the Flame's do, and strain harder as
the orb grows: three times each slides further out and grips again, tighter, and their
heaving quickens. The weapon climbs her left side, swaying, and feeds ink into the orb, broad
violet swells running up it into the orb one after another; at the release all three take
the recoil, flung and dragged back, and spring back. It holds its human hand out: a blue and a
red star rise out of it, circle in and merge over three seconds into a purple orb with a heart
of ink (the longest forming in the fight, and its longest opening). Meanwhile a lane is
marked in ink from the copy to the floor's edge, 12 wide, turning after its target at most
0.6° a tick. It locks 0.65 s before the release, its edges flaring violet. The orb leaves from
before its hand at a third of its size, opens to 12 blocks across over its first 10 blocks
(its heart twice the player's, its shell above the player's) and rolls down the lane at 14
blocks a second (the player's flies at 26): 28 to every body it touches, once, a jump not
clearing it (about 12 to 13 of 20 in diamond or netherite). It breaks no blocks, and collapses
at the end of the lane. **Leave the lane**: far out, a sprint aside after the lock; close in,
by circling out while it forms, which the lane is too slow to follow.

Everything phase two does follows the fight's rules: hits are decided on the server through
`Cast.hit` with `FirstWizardRules`' constants, every mark is on the floor, nothing staggers
it, co-op target changes and `addParticipant` scaling hold, and what it leaves is cleared by
`stopFight`, its death and the server stopping.

**Adding a spell.** The `BossSpell` interface's Javadoc says what a spell must do and how
to add one: a class under `entity/firstwizard/spell/` with one line in `BossSpells`, a
drawer under `client/blockink/` with one line per effect kind in `InkDrawers`, and its
timings in the contract check and a case in the fight fixture. Its row in the move table
is already there. The Fireball is the example of a thrown spell; Blink Rift of one that
forms where it appears. `GroundZone` is lingering damaging ground, a disc or a strip, ready
for reuse.

**Owners.**

| Concern | Owner |
| --- | --- |
| Move table, chooser, opener, follow-ups, gaps, tuning | `entity/firstwizard/FirstWizardRules` |
| The fight loop, targets, steps, what spells leave | `entity/firstwizard/FirstWizardCombat` |
| State, skin, damage rules, the End's seam | `entity/firstwizard/FirstWizardEntity` |
| The floor's geometry | `entity/firstwizard/Arena` |
| What clients draw of a spell | `entity/firstwizard/FirstWizardEffectEntity` |
| The spell interface and registry | `entity/firstwizard/spell/BossSpell`, `Cast`, `Casting`, `Lingering`, `Timing`, `BossSpells` |
| Fireball, Blink Rift, lingering ground | `entity/firstwizard/spell/FireballSpell`, `BlinkRiftSpell`, `GroundZone` |
| The copy's model and the wound's ink | `client/renderer/FirstWizardRenderer` |
| Ink effects: meshes, primitives, drawers | `client/blockink/InkEffects`, `AtlasMesh`, `InkKit`, `InkDrawers`, `CopyFx`, `FireballFx`, `BlinkRiftFx` |
| Marks on the floor (the change's ring, the Sweep's arc, the Slam's line, the Leap's circle) | `client/blockink/FloorDecal` (flat textured quads in the floor-ink textures, `art/first_wizard/floor_ink/`), drawn by `InkMovesFx` |
| Ink atlas, the swelling's veins and the wound's textures | `art/first_wizard/build_ink.py` (`--install` writes the game's copies; `--check` verifies them) |
| Stone Wall, Faultline; their drawer, block and stone atlas | `entity/firstwizard/spell/StoneWallSpell`, `FaultlineSpell`; `client/blockink/StoneFx`; `block/FirstWizardWallBlock`; `art/first_wizard/build_stone.py` (`--install`, `--check`) |
| Thornbite, Sentry Bloom; the flower; their drawers and Nature's colours | `entity/firstwizard/spell/ThornbiteSpell`, `SentryBloomSpell`; `entity/firstwizard/InkBloomEntity`; `client/renderer/InkBloomRenderer`; `client/blockink/ThornbiteFx`, `SentryBloomFx`, `NatureInk` (the frame's `nature` mesh, the Nature kit's atlas) |
| Sky Shear, Waylay Dash; their drawers and Wind's colours | `entity/firstwizard/spell/SkyShearSpell`, `WaylayDashSpell`; `client/blockink/SkyShearFx`, `WaylayDashFx`, `WindInk` (the frame's `wind` mesh, the Wind kit's atlas) |
| The phase change, the ink moves and their geometry | `entity/firstwizard/spell/PhaseChange`, `SlamSpell`, `SweepSpell`, `LeapSpell`, `FlameSpell`, `HollowPurpleSpell`, `Tendrils` |
| The change's cinematic: its script (camera and body), its watchers, the camera on each client | `art/first_wizard/change/build_change.py` (`--install` writes `cinematics/first_wizard_change.json`; `--check` detects drift); `entity/firstwizard/FirstWizardChangeTrack`, `FirstWizardChangeScene`; `client/FirstWizardChangeClient`, `client/BossIntroCamera` |
| The changing and torn body, tentacles and wound; the moves' marks, flame and orb | `client/renderer/FirstWizardRenderer`; `client/blockink/WizardBody`, `ChangeFx`, `TendrilFx`, `TendrilRest`, `TendrilMoves`, `TendrilCurve`, `TendrilRig`, `InkMovesFx`, `HollowPurpleFx`; veins and wound layers `art/first_wizard/build_ink.py` |
| Bottling the flame's cloud | `mixin/InkBreathBottleMixin` |
| Commands | `command/FirstWizardCommands` |

**Verification.**

- `./gradlew build` runs `checkFirstWizard` (`FirstWizardContractTest`): every built spell's
  timings are in order and it forms in the open for at least half a second, the armored
  damage sums, the move table, the chooser (never three running, never a spell that is not
  built, never outside a spell's range), the openers, the follow-ups, the gaps, and that
  every aim point and blink spot is on the floor. `WindSpellsContract` holds Sky Shear to
  vanilla's jump (the window that clears a crescent, early and late jumps struck, three in
  step) and Waylay Dash's line, hit and wake to the floor. `NatureSpellsContract` holds
  Thornbite's warning, reach, bite and heal and that a sidestep after the lock clears it, and
  Sentry Bloom's seeds, store, health, cooldown and spot (on the floor, away from the player).
  `InkMovesContract` holds phase two: the change's beats (in order, with room, 12 to 18 s of
  cinematic, the tell only after control returns, the wound on the left), the baked track in
  step with them (its beats, the swelling surging on the server's heartbeat, the torn arm on
  the floor clear of her feet before it melts, the head's peak and phase two's bend, the
  camera above the floor and near her), its full sweep jumped at every bearing, each ink move's warning, dodge and armored damage, the Sweep held to vanilla's
  jump, the Flame's turn against a player behind it, Hollow Purple's lane against a sprint at
  every distance, its rarity (never above a quarter health or within 25 s), phase two's pace,
  and every line, circle and lane on the floor.
- `first_wizard_fight_smoke.init.gradle` runs a hidden, muted client at midnight against an
  unarmored Survival player with 1000 health, on a round floor as wide as the End's: the
  Fireball's hit, a sidestep after its aim lock, its burning ground burning and ending,
  Blink Rift landing on the floor (crowded and not), Sky Shear stood in, jumped with real
  client jumps and jumped early and late, Waylay Dash on its line, beside it and in its wake
  (`client/WindSpellCases`), Thornbite biting and healing, sidestepped, and after Blink Rift
  from behind (bitten and sidestepped), and Sentry Bloom delivering what its seeds stored,
  every seed sidestepped then the fight stopped, and broken (`client/FirstWizardNatureCases`),
  phase two (`client/FirstWizardPhaseTwoCases`: the change held at half while a Fireball
  finishes, not hurt during it, its full sweep struck standing and jumped with a real client
  jump, phase two's box; the body standing and striding; each ink move's hit and dodge; Leap
  then Flame with the player behind it; the cloud bottled; Hollow Purple only below a quarter
  health; everything cleared when it dies in phase two), the change's cinematic
  (`client/FirstWizardChangeCases`: phase one's burning ground cleared as it begins; the
  player watching, held, unhurt and unable to hurt the copy or attack; the wand in its right
  hand; control back at its end and the full sweep striking once from its left; stopped
  mid-way, a player leaving the fight mid-way, a player joining mid-way, and Sneak held to
  skip it; and a close look by day with nobody watching), and a free fight started by
  `startFight` through the change and phase two to its death. It saves screenshots to
  `build/first-wizard-fight-smoke/screenshots/` (or the run's own directory with
  `-PwizardRun`). They show how it draws in the fixture, not a Lunar look. For motion,
  `-PwizardRecord=case[:every],...` also saves every tick (or every n-th) of those cases as
  `rec-<case>-<tick>.png`, and `py -3 tools/frames_to_gif.py <screenshots> rec-<case> <out.gif>`
  turns them into a GIF with Pillow (`--fps`, `--every`, `--from`, `--to`, `--crop`, `--max-mb`).
  `-PwizardOrbit=<blocks>` widens the `phase-drift` case's turn round her (7.5 holds the whole
  rest pose of the tentacles and her body in frame). `-PwizardView=<side>` (left, behind,
  right, front, front-left, behind-left) films the phase-two cases from that side of her
  instead, to review a move's motion from all round; `phase-fireball` casts a spell from the
  human hand in phase two (its one heavy hit), for the tentacles' lighter touch.
- `py -3 art/first_wizard/build_ink.py --check` (also in `tools/check_assets.py`).
- `py -3 art/first_wizard/build_stone.py --check` (also in `tools/check_assets.py`).
- `py -3 art/first_wizard/change/build_change.py --check` (also in `tools/check_assets.py`);
  the builder itself refuses a scene whose arm does not land and lie clear of her feet, whose
  swelling does not reach the burst, or whose head does not settle at phase two's bend.

**Not built.**

- Its own sounds, a death, rewards (parked).
- **Tuning.** Health, the middle distance, speeds, gaps and every timing are first guesses.

## The intro

The scene that opens the fight, with the pool of ink it comes out of (the developer's
decisions: [design notes](../design/first-wizard.md#the-intro)). Built November 18, 2026,
and seen only in the fixture's stills: the developer has not seen it, and nobody has
watched it in Lunar. Sounds are sparse vanilla placeholders, with no music.

**The pool.** While the boss is undefeated and no fight runs (before the first visit, and
again after a wipe) a pool of living ink hangs over the island: 26 blocks in radius, its
middle 38 above the floor, so it never crowds the fight. It is a mass of ink columns, domed
above and sagging below, its belly rippling slowly with a dark purple crest crossing it now
and then, its outline wavering inside a violet meniscus; blobs bulge out of it and drops
stretch, fall and splat on the floor. Matte ink, not Space's glass: no stars or panes. While
a fight runs the sky is empty (the pool became the copy); after the win it is gone for good.
Each client draws it (`client/blockink/EndSkyFx`, about 700 cubes in one mesh a frame) from
anywhere within 400 blocks of the island's centre; after a wipe it spreads back over three
seconds. The server tells each client in the End what the sky holds (`end/EndSky`: the pool,
a scene and its clock, or nothing) when that changes and on arrival.

**The full scene** plays the first time in a world, while the dragon is alive.

| Seconds | Beat |
| --- | --- |
| 0–6.5 | Fade in on the island from far off: the ink over it like a hole in the sky, the dragon circling under it. |
| 6.5–12 | Behind the players at the arrival, tilting up to the pool; the dragon crosses overhead. |
| 12–18 | Under the pool's belly, the dragon passing close. |
| 18–22 | It breaks from its circle and climbs under the pool's south side, nose up. |
| 22.5–27 | It breathes into the ink; a patch of violet blooms under the surface. |
| 27–31.5 | The bloom hangs there; the dragon waits under it. |
| 31.5–32.4 | It turns to flee; the pool sends the breath back, stretched into a stream with an ink sheath, and catches it. |
| 32.4–43 | It is held in its own breath, swirling round it, struck six times; from 34 s vanilla's death rays and dissolve. |
| 43–47 | It bursts; what is left is drawn up into the bloom, cooling to ink. |
| 47–51.5 | Quiet: the sky and the pool. |
| 51.5–55.5 | The pool's south edge comes down as a hanging lobe and touches the floor 18 blocks in front of the players. |
| 56–70.5 | It pours onto the spot and the copy forms out of it, boots first (legs by 60 s, body 63.5, arms 67, head 69.5), while the pool drains toward that edge and shrinks to nothing. |
| 69.75–71.25 | The last strand pulls into its wand hand; the wand appears. |
| 71.5–74 | It raises its head: the first player's face. |
| 74.5–79.5 | Below it, looking up past it at the empty sky; its name. |
| 79.5–82.5 | The camera returns to the player; the fight starts. |

**The short scene** plays once the dragon is dead (later attempts, 38.5 s): 7.5 s from
behind the players looking up at the waiting pool, then the condensing exactly as above,
from the edge coming down to the fight.

**The dragon is an actor drawn only on clients** (`client/FirstWizardIntroDragon`): the
vanilla `EnderDragonEntity` and its vanilla renderer, never added to a world. Each client
places it from the track every tick (where it is, its yaw, its wing beat, when it is
struck, how far its death has run; its neck and tail follow its path as vanilla's do),
adds a nose pitch and roll the vanilla model lacks, and lights it to read against the sky.
So it has no AI, hurts nobody, breaks nothing, has no boss bar, drops nothing and plays no
global death sound. Its death is vanilla's rays and dissolve, held to three-quarters of
vanilla's length. The death is saved (`dragonDead` in `elementalwands/end.json`) as its
rays begin; a world whose vanilla record says the dragon was killed before the takeover
counts as dead too.

**What the server does** (`end/EndIntro`, owned by `EndEncounter`). It runs the clock;
holds every watcher where they stand, facing the forming spot, unhurt and unable to cast,
hit or use anything; keeps the copy on its spot (0.5, 64, 24.5), passive, wearing the first
player's skin from the scene's start, and impossible to hurt (only `/kill` reaches it); sets
how far it has formed (`FirstWizardEntity.formed`, synced); plays the sounds; and saves the
dragon's death. When it ends the copy is whole and the encounter starts the fight with
everyone standing.

**What each client does.** `client/FirstWizardIntroClient` flies the camera along the track
(hard cuts between shots), letterboxes the screen, shows the name and the skip prompt, and
tells the pool, the dragon and the forming look the scene's time. `BossIntroCamera` shares
the camera, HUD and input hooks with the other bosses' scenes. `client/blockink/EndSkyFx`
draws the pool, the breath, the bloom, the stream back, the remains, the lobe, the pour, the
pool at the copy's feet and the violet line of ink climbing it. `client/blockink/CopyForming`
grows each part of the copy's model out of what has formed (legs up from the boots, body
from the hips, arms down from the shoulders, head from the neck), keeps its head bowed until
it raises it, and holds back the wand, the shadow and the motes round the copy until it is
whole; it asks for the skin before the first boot, so the boot already wears it.

**Skipping.** Held Sneak skips it for that player, who gets their view back while the
others watch. It ends early only once every watcher has skipped (no sooner than a second
in); the copy is then whole and the fight starts at once.

**Late party members** arrive at the arrival spot, are held there and watch from where the
scene has got to; the fight takes them in when it starts.

**Cut short.** An operator reset, a reload, the server stopping, the copy removed or killed,
or nobody left watching (logged out, dead, gone) ends the fight as a wipe does, and the scene
lets everyone go first. An operator starting the copy's fight (`/ew wizard fight`) mid-scene
ends the scene and starts the fight properly. A copy left by a crash is removed when the next
fight places a new one.

**Owners.**

| Concern | Owner |
| --- | --- |
| The scene's script: camera, dragon, breath, pool, pour, forming, both versions | `art/first_wizard/intro/build_intro.py` (`--install` writes `cinematics/first_wizard_intro.json`; `--check` detects drift) |
| The track as server and clients read it | `end/EndIntroTrack` |
| The server scene, holding, sounds, the dragon's death saved | `end/EndIntro`, `EndArenaManager.dragonDead` |
| What each client's sky holds; the skip | `end/EndSky` |
| Starting it, late members, cutting it short, `/ew end intro` | `end/EndEncounter`, `command/EndCommands` |
| Camera, letterbox, name, skip | `client/FirstWizardIntroClient`, `client/BossIntroCamera` |
| The dragon | `client/FirstWizardIntroDragon` |
| The pool and the scene's ink | `client/blockink/EndSkyFx` |
| The copy forming | `client/blockink/CopyForming` (called from `FirstWizardRenderer`), `FirstWizardEntity.formed` |

**Verification.** `py -3 art/first_wizard/intro/build_intro.py --check` (also in
`tools/check_assets.py`). `first_wizard_intro_smoke.init.gradle` runs a hidden, muted client.
It visits the island (the pool overhead), then a Survival player goes in with `/ew end
begin` and the real boss, and the full scene plays through its own camera, a still at each
beat in `build/first-wizard-intro-smoke/screenshots/` (with `-PwizardIntroVideo` also a frame
every other tick, `intro-*.png`). It checks that the player is held at the arrival and
unhurt; the dragon is drawn during the scene and gone after its burst, and its death is
saved; the copy wears the player's profile, is unformed until the pour, wears the player's
loaded skin at its first boot and cannot be hurt during the scene; and the fight follows with
the copy whole and the sky empty. Then a wipe brings the pool back; a second run plays only
the condensing and one skip ends it at once with the fight started; `/ew end intro full`
plays the dragon again, and a reset mid-scene, then the copy's removal mid-scene, each leave
nobody held and no fight; a win leaves the sky empty. A late party member joining mid-scene
needs a second client and is not checked. The stills show how it draws in the fixture, not a
Lunar look.

**Not built.**

- Its own sounds and any music (parked).
- **Tuning.** Every timing and camera move is a first pass the developer has not seen.
