# Feature index

Look a feature up here before searching the source. Each row names the files that own
the feature and the reference that explains its rules.

How to read it:

- Java names are relative to `src/main/java/com/anton/elementalwands/`. A name with a
  package, such as `hall/PyreGate`, is the file `hall/PyreGate.java`. A bare name, such as
  `PyreKingRules`, is in the package named earlier in the same cell.
- Other paths (`art/`, `tools/`, `src/`) start at the repository root.
- Spells are listed by their player-facing name, followed by the saved ID in brackets
  where the two differ. The ID is what the code and saved worlds use.
- "Reference" is the page under [reference/](reference/) to read for behavior and
  constraints. Read only the section you need.

Keeping it true: every name in backticks on this page must exist. `./gradlew checkDocs`
(part of `./gradlew build`) fails when one does not, or when a source package is
missing from the package table. When you add, rename, move or retire a feature, update
its row in the same task. Use backticks here only for files, folders and classes.

## Packages

| Package | What lives there |
| --- | --- |
| `ElementalWandsMod`, `ElementalWandsClient` | Common and client start-up: registration and ticking managers |
| `item/` | The wand item and one ability handler per element |
| `data/` | Player attachments, affinities, the spell catalog, levels |
| `util/` | Spell managers and rules, progression, loadouts, temporary blocks, visual event channels |
| `entity/` | Spell projectiles and the Guardian; `entity/necromancer/`, `entity/undead/`, `entity/roc/`, `entity/pyreking/` hold the other bosses and mobs |
| `block/` | Temporary spell blocks and boss blocks |
| `client/` | Controls, HUD, screens, intro cameras; `client/renderer/`, `client/model/`, `client/particle/`, `client/overlay/`, `client/screen/`, `client/wand/`, and one 3D effects package per element: `client/blockfire/`, `client/blockwind/`, `client/blocknature/`, `client/blockspace/` |
| `network/` | Client requests and server state sync |
| `party/` | Parties and ally protection |
| `command/` | The operator and party commands |
| `arena/` | Guardian fight lifecycle and the Shattered Nave realm |
| `church/` | Guardian church: placement, ritual, loot, recovery |
| `crypt/` | Hollow Crypt realm, mausoleum and rewards |
| `summit/` | Storm Summit realm, aerie, gong, Roc encounter and intro |
| `hall/` | Burned Throne Hall realm and the Burned Gate |
| `world/` | World generation structures and locate arrival points |
| `registry/` | Item, block, entity, particle and sound registration |
| `mixin/` | Minecraft hooks, named after the feature they serve |

## Wand, progression and interface

| Feature | Owners | Reference |
| --- | --- | --- |
| Cast dispatch: which code a slot press runs | `util/WandLoadouts` (its cast method sends many spells straight to their manager and the rest to the element handler) | [Progression](reference/progression-and-controls.md#implementation-boundaries) |
| Wand item, shared cooldowns and ultimate charge, the fractured beam (no affinity) | `item/UniversalWandItem`, `item/AbstractWandItem` | [Spells](reference/spells.md#shared-rules) |
| Spell catalog: names, saved IDs, prices, categories, and the cooldown and reach text the hub shows (written by hand there, so change it with the spell) | `data/WandSpells` | [Progression](reference/progression-and-controls.md) |
| Affinity and saved player state | `data/WizardAffinity`, `data/EWAttachments`, `data/ElementProgress` | [Progression](reference/progression-and-controls.md) |
| Loadouts, slot validation, combat lock | `util/WandLoadouts` | [Progression](reference/progression-and-controls.md) |
| Flux, purchases, levels, XP, spell books | `util/WandProgression`, `data/ElementLevels`, `util/SpellBooks`, `item/SpellBookItem` | [Progression](reference/progression-and-controls.md) |
| Damage attribution and XP from hits | `util/SpellCombat` | [Spells](reference/spells.md#shared-rules) |
| Key bindings and held input | `client/WandControls`, `mixin/WandKeyboardMixin`, `mixin/WandMouseMixin` | [Progression](reference/progression-and-controls.md) |
| Wand hub screen (store, loadout, guide, controls) and the first-join welcome | `client/screen/WandHubScreen`, `client/WandWelcome` | [Progression](reference/progression-and-controls.md) |
| Spell HUD and its position editor | `client/overlay/WandHudOverlay`, `client/WandHudLayout`, `client/screen/WandHudEditScreen`, `client/SpellIcons` | [Progression](reference/progression-and-controls.md) |
| Client copy of synced player data | `client/ClientPlayerData` | [Progression](reference/progression-and-controls.md) |
| Packets and state sync | `network/ModNetworking` | [Progression](reference/progression-and-controls.md#implementation-boundaries) |
| Parties and ally protection | `party/PartyManager`, `PartyStore`, `WandAllies`, `WandExplosionBehavior`, `command/PartyCommands` | [Progression](reference/progression-and-controls.md#parties) |
| 3D wand model and glass core | `client/wand/WandMesh`, `WandRenderer`, `WandItemModel`; source `art/wand/design.json`, exporter `tools/prepare_wand_assets.py` | [Wand rendering](reference/wand-rendering.md) |
| Temporary blocks and their restoration | `util/TemporaryBlockManager` | [Spells](reference/spells.md#shared-rules) |
| Cast effects from the wand tip; first-person fade | `util/SpellCastVisuals`, `client/renderer/SpellViewClearance` | [Spells](reference/spells.md#shared-rules) |
| Aim raycasts and beam particles shared by spells | `util/WandUtils` | [Spells](reference/spells.md#shared-rules) |
| Spell regeneration, burn damage credit, fire from wand explosions | `util/SpellBuffs`, `mixin/SpellRegenerationMixin`, `mixin/SpellBurnDamageMixin`, `mixin/WandExplosionFireMixin` | [Spells](reference/spells.md#shared-rules) |
| Particle types and their factories | `registry/ModParticles`, `client/particle/ModParticleFactories`, `ElementalBillboardParticle`, `ElementalParticleFactory` and one factory class per element | [Art](reference/art-and-assets.md) |
| Retiring the old guide books from inventories | `util/WandGuide` | [Progression](reference/progression-and-controls.md) |
| Borrowed equipment and kept inventories | `util/EquipmentReceipt`, `util/SoulboundInventoryCarrier` | [Spells](reference/spells.md#shared-rules) |
| Sound events and generated audio | `registry/ModSounds`, `tools/build_sounds.py` | [Sound](reference/sound.md) |
| Registration | `registry/ModItems`, `ModBlocks`, `ModSpellBlocks`, `ModEntities`, `ModParticles` | — |

## Spells

A slot press reaches `util/WandLoadouts` first. It sends some spells straight to their
manager and the rest to the element's handler in `item/`, which also holds that element's
basic cooldowns and cast gating. If a change to a spell's cooldown or cast conditions is
not in its manager, look in the handler and in `util/WandLoadouts`.

### Fire

| Spell | Owners | Reference |
| --- | --- | --- |
| Fireball (inferno_wave) | `item/FireAbilityHandler`, `entity/InfernoWaveEntity`, `block/InfernoFlameBlock`, `client/blockfire/InfernoFx` | [Spells](reference/spells.md#fire) |
| Dragon's Pyre (dragons_pyre) | `item/FireAbilityHandler`, `entity/PyreFrontEntity`, `block/PyreFlameBlock`, `block/PyreCoalsBlock`, `client/blockfire/PyreFx` | [Spells](reference/spells.md#fire) |
| Meteor | `util/MeteorManager`, `client/renderer/FireMeteorRenderer`, `client/blockfire/MeteorFx` | [Spells](reference/spells.md#fire) |
| Flamethrower (heat and overheat) | `util/FireBuildManager`, `FireBuildRules`, `client/blockfire/FlameFx` | [Spells](reference/spells.md#fire) |
| Fire Leap (fire_hop) | `util/FireLeapManager`, `FireLeapRules`, `client/FireLeapClient`, `client/FireLeapPreview`, `mixin/FireLeapFallMixin`, `mixin/FireLeapMotionMixin`, `client/blockfire/LeapFx` | [Spells](reference/spells.md#fire) |
| Flashover | `util/FlashoverManager`, `FlashoverRules`, `entity/FlashoverEmberEntity`, `client/blockfire/EmberFx` | [Spells](reference/spells.md#fire) |
| Block-fire visuals shared by all Fire spells | `client/blockfire/BlockFireEffects`, `BlockFireKit`, `BlockFireMesh`, `util/BlockFireEvents`; study in `art/fire/blockfire/` | [Spells](reference/spells.md#fire), [Art](reference/art-and-assets.md) |

### Wind

| Spell | Owners | Reference |
| --- | --- | --- |
| Sky Shear | `item/WindAbilityHandler`, `entity/VacuumBladeEntity`, `entity/WindFanRules`, `client/blockwind/ShearFx` | [Spells](reference/spells.md#wind) |
| Waylay Dash | `item/WindAbilityHandler`, `client/blockwind/DashFx` | [Spells](reference/spells.md#wind) |
| Zephyr Strike | `item/WindAbilityHandler`, `util/ZephyrStrikeManager`, `item/ZephyrWingsItem`, `mixin/ScreenHandlerMixin` (keeps the borrowed wings out of chests), `client/blockwind/ZephyrFx` | [Spells](reference/spells.md#wind) |
| Gale Daggers | `item/WindAbilityHandler`, `util/GaleDaggers`, `entity/GaleDaggerEntity`, `client/renderer/GaleDaggerRenderer`, `client/blockwind/DaggerFx` | [Spells](reference/spells.md#wind) |
| Updraft | `util/UpdraftManager`, `mixin/UpdraftFallMixin`, `client/blockwind/UpdraftFx` | [Spells](reference/spells.md#wind) |
| Block-wind visuals shared by all Wind spells | `client/blockwind/BlockWindEffects`, `BlockWindKit`, `BlockWindMesh`, `util/BlockWindEvents`; study in `art/wind/blockwind/` | [Spells](reference/spells.md#wind), [Art](reference/art-and-assets.md) |

### Stone

| Spell | Owners | Reference |
| --- | --- | --- |
| Gathered Mass | `item/StoneAbilityHandler`, `util/StoneClusterManager`, `StoneClusterRules`, `entity/StoneClusterEntity`, `client/renderer/StoneClusterRenderer` | [Spells](reference/spells.md#stone) |
| Stone Wall | `item/StoneAbilityHandler`, `block/StoneWallBlock` | [Spells](reference/spells.md#stone) |
| Titan Dome | `item/StoneAbilityHandler`, `util/TitanDomeManager` | [Spells](reference/spells.md#stone) |
| Faultline | `util/FaultlineManager`, `entity/FaultlineSpikeEntity`, `client/renderer/FaultlineSpikeRenderer`, `block/StoneSpikeBlock` | [Spells](reference/spells.md#stone) |
| Juggernaut (stone_charge) | `util/StoneChargeManager`, `StoneTechniqueRules`, `mixin/StoneMotionMixin`, `mixin/StoneStaggerMixin`, `mixin/StoneDamageMixin` | [Spells](reference/spells.md#stone) |

### Nature

| Spell | Owners | Reference |
| --- | --- | --- |
| Seed and the mark it leaves | `item/NatureAbilityHandler`, `entity/SeedProjectileEntity`, `util/NatureCombat`, `client/renderer/NatureSeedRenderer`, `client/SeedMarkClientEffects` | [Spells](reference/spells.md#nature) |
| Thornbite (thorn_lash) | `item/NatureAbilityHandler`, `entity/ThornLashEntity`, `util/ThornLashRules`, `client/renderer/ThornLashRenderer`, `ThornbiteVisual` | [Spells](reference/spells.md#nature) |
| Sentry Bloom flowers (tendril_bloom): throwing, shooting, collecting, breaking | `item/NatureAbilityHandler`, `util/FlowerTurretManager`, `util/WandLoadouts` (routes the break and collect clicks), `entity/SentryBloomEntity`, `entity/SentryBloomPodEntity`, `block/NatureSeedlingBlock`, `registry/ModSpellBlocks` (the stem block's settings), `client/renderer/SentryBloomRenderer`, `SentryBloomPodRenderer` | [Spells](reference/spells.md#nature) |
| Springbloom | `util/SpringbloomManager`, `SpringbloomRules`, `SpringbloomFootprint`, `entity/SpringbloomEntity`, `block/SpringbloomBlock`, `mixin/SpringbloomFallMixin`, `mixin/SpringbloomMovementMixin`, `client/renderer/SpringbloomRenderer` | [Spells](reference/spells.md#nature) |
| Full Bloom (overgrowth) | `item/NatureAbilityHandler`, `util/FullBloomManager` | [Spells](reference/spells.md#nature) |
| Block-nature visuals shared by all Nature spells | `client/blocknature/BlockNatureEffects`, `BlockNatureKit`, `BlockNatureMesh`, `NatureFx`, `util/BlockNatureEvents`, `client/renderer/NatureMeshes`; study in `art/nature/blocknature/` | [Spells](reference/spells.md#nature), [Art](reference/art-and-assets.md) |

### Space

| Spell | Owners | Reference |
| --- | --- | --- |
| Singularity Bolt | `item/SpaceAbilityHandler`, `entity/SingularityBoltEntity`, `client/blockspace/BoltFx` | [Spells](reference/spells.md#space) |
| Blink Rift | `item/SpaceAbilityHandler`, `client/blockspace/BlinkFx` | [Spells](reference/spells.md#space) |
| Astral Double | `util/AstralDoubleManager`, `entity/AstralOrbEntity`, `entity/AstralDoubleEntity`, `client/renderer/AstralDoubleRenderer`, `client/blockspace/AstralFx` | [Spells](reference/spells.md#space) |
| Gravity Well | `util/GravityWellManager`, `entity/GravityBombEntity`, `client/blockspace/WellFx` | [Spells](reference/spells.md#space) |
| Hollow Purple | `util/HollowPurpleChargeManager`, `entity/HollowPurpleOrbEntity`, `client/blockspace/PurpleFx` | [Spells](reference/spells.md#space) |
| Void-glass visuals shared by all Space spells | `client/blockspace/BlockSpaceEffects`, `BlockSpaceKit`, `BlockSpaceMesh`, `util/BlockSpaceEvents`; study in `art/space/blockspace/` | [Spells](reference/spells.md#space), [Art](reference/art-and-assets.md) |

## Bosses, mobs and their realms

Shared by every boss: `entity/WandBoss` (how spells treat a boss), `entity/BossFightLog`
(the operator fight log), `mixin/BossRealmDeathMixin` (nothing drops in a realm), and the
cinematic plumbing: `client/BossIntroCamera`, `client/IntroClip`, and the hooks that give a
scene the camera, HUD, input, fog and light, `mixin/IntroCameraMixin`, `IntroGameRendererMixin`,
`IntroHudMixin`, `IntroInputMixin`, `IntroLookMixin`, `IntroFogMixin`, `IntroLightmapMixin`.

### Fractured Guardian (Stone)

| Feature | Owners | Reference |
| --- | --- | --- |
| Fight loop and state | `entity/FracturedGuardianEntity`, `entity/GuardianBossCombat` | [Guardian combat](reference/guardian-combat.md) |
| Tuning and rules | `entity/GuardianCombatRules`, `GuardianPhaseRules`, `GuardianShellRules`, `GuardianPulseRules`, `GuardianVolleyRules`, `GuardianFanRules` | [Guardian combat](reference/guardian-combat.md#code-and-art-ownership) |
| Beam, leap, thrown rock, shockwave and cover | `entity/GuardianBeamAttack`, `GuardianBeamGeometry`, `GuardianBeamTiming`, `GuardianLeapAttack`, `GuardianLeapRules`, `GuardianRockEntity`, `GuardianThrowSocket`, `GuardianWaveSurface`, `GuardianWallImpact` | [Guardian combat](reference/guardian-combat.md) |
| Intro cinematic | `entity/GuardianIntro`, `GuardianIntroTrack`, `GuardianIntroZombieEntity`, `client/GuardianIntroClient` | [Church and arena](reference/church-and-arena.md#the-intro-the-zombie) |
| Core pulse: its timing and pull strength, and the pull as each player's client applies it | `entity/GuardianPulseRules`, `client/GuardianPullClient` | [Guardian combat](reference/guardian-combat.md#delayed-slams-and-the-core-pulse) |
| Model and drawn attacks | `client/model/FracturedGuardianModel`, `client/renderer/FracturedGuardianRenderer`, `GuardianBeamVisual`, `GuardianWaveVisual`, `GuardianFanVisual`, `GuardianLeapVisual`, `GuardianBurnVisual`, `GuardianUnstableVisual`, `GuardianHeldRockLayer`, `GuardianRockRenderer` | [Guardian combat](reference/guardian-combat.md#code-and-art-ownership) |
| Fight admission, wipe, victory, recovery, and keeping players in the fight | `arena/GuardianArenaManager`, `GuardianArenaRules`, `GuardianArenaJournal`, `mixin/GuardianArenaPlayerMixin`, `mixin/GuardianArenaWorldMixin` | [Church and arena](reference/church-and-arena.md) |
| Shattered Nave realm | `arena/ShatteredNave`, `ShatteredNaveShafts`, `client/NaveShaftEffects`, `mixin/NaveFogMixin` (fog here and in the Burned Throne Hall), `command/NaveCommands`; layout `art/guardian_nave/build_layout.py` | [Church and arena](reference/church-and-arena.md#the-shattered-nave) |
| Church structure, ritual, loot and protection | `church/GuardianChurchManager`, `GuardianChurchPlacement`, `GuardianChurchTerrain`, `GuardianChurchLocator`, `GuardianChurchLoot`, `GuardianRitualBlock`, `GuardianSocketBlock`, `ChurchLayout`, `client/GuardianOfferingHint`, `mixin/GuardianChurchExplosionMixin`, `mixin/GuardianChurchPistonMixin`, `mixin/GuardianChurchPlacementMixin`; layout `tools/build_guardian_church.py` | [Church and arena](reference/church-and-arena.md#discovery-and-ritual) |
| Operator commands and fight log | `command/GuardianCommands`, `entity/GuardianFightLog` | [Guardian combat](reference/guardian-combat.md#rehearsal-and-verification) |
| Art sources and exporter | `art/fractured_guardian/`, `tools/prepare_guardian_assets.py` | [Guardian combat](reference/guardian-combat.md#code-and-art-ownership) |

### Hollow Necromancer (Nature)

| Feature | Owners | Reference |
| --- | --- | --- |
| Fight loop, state and tuning | `entity/necromancer/NecromancerEntity`, `NecromancerCombat`, `NecromancerRules` | [Necromancer combat](reference/necromancer-combat.md) |
| His spells' entities, models and renderers | `entity/necromancer/SoulBoltEntity`, `SoulFireballEntity`, `GraspingHandEntity`, `HarvestSoulEntity`, `NecromancerSoulEntity` (the soul split); `client/model/SoulBoltModel`, `SoulFireballModel`, `GraspingHandModel`, `HarvestSoulModel`, `NecromancerSoulModel`; `client/renderer/SoulBoltRenderer`, `SoulFireballRenderer`, `GraspingHandRenderer`, `HarvestSoulRenderer`, `NecromancerSoulRenderer` | [Necromancer combat](reference/necromancer-combat.md) |
| Siege minions | `entity/necromancer/NecromancerMinion` | [Necromancer combat](reference/necromancer-combat.md#sieges) |
| Intro cinematic | `entity/necromancer/NecromancerIntro`, `IntroSoulEntity`, `IntroZombieEntity`, `client/NecromancerIntroClient` | [Necromancer combat](reference/necromancer-combat.md#intro-cinematic) |
| Transformation cinematic | `entity/necromancer/NecromancerTransformScene`, `NecromancerTransformTrack`, `TransformSoulEntity`, `client/NecromancerTransformClient`; scene `art/hollow_necromancer/transform/build_scene.py` | [Necromancer combat](reference/necromancer-combat.md#phase-two-the-colossus) |
| Life Drain visuals | `client/NecromancerDrainEffects`, `client/NecromancerDrainSocket`, `client/overlay/NecromancerDrainOverlay` | [Necromancer combat](reference/necromancer-combat.md#art-and-verification) |
| Soul light | `util/SoulGlow`, `block/SoulGlowBlock`, `client/renderer/SoulLightPool`; texture `tools/prepare_soul_light.py` | [Necromancer combat](reference/necromancer-combat.md#soul-light) |
| Model and renderer | `client/model/NecromancerModel`, `client/renderer/NecromancerRenderer` | [Necromancer combat](reference/necromancer-combat.md#art-and-verification) |
| Hollow Crypt realm, fight flow and rewards | `crypt/HollowCryptManager`, `HollowCryptRealm`, `HollowCryptPerches`, `HollowCryptRewards`; layout `art/hollow_crypt/build_layout.py` | [Church and arena](reference/church-and-arena.md#hollow-crypt-realm) |
| Graveyard and mausoleum entry | `world/HollowGraveyardStructure`, `world/GraveyardBlight`, `crypt/MausoleumArchBlock`, `MausoleumVeilBlock`, `MausoleumWindowBlock`, `MausoleumModel`, `client/MausoleumHint` | [Church and arena](reference/church-and-arena.md#hollow-crypt-realm) |
| Operator commands and fight log | `command/NecromancerCommands`, `command/CryptCommands`, `entity/necromancer/NecromancerFightLog` | [Necromancer combat](reference/necromancer-combat.md#art-and-verification) |
| Art source and exporter | `art/hollow_necromancer/v2/build_art.py` | [Necromancer combat](reference/necromancer-combat.md#art-and-verification) |

### Hollow undead (night mobs and siege waves)

| Feature | Owners | Reference |
| --- | --- | --- |
| Crawler, archer and brute | `entity/undead/HollowUndeadEntity`, `HollowCrawlerEntity`, `HollowArcherEntity`, `HollowBruteEntity` | [Hollow undead](reference/hollow-undead.md) |
| Night spawning | `entity/undead/HollowUndeadSpawns` | [Hollow undead](reference/hollow-undead.md#spawning-and-daylight) |
| Clip timings (generated) and exporter | `entity/undead/HollowUndeadClips`, `art/hollow_necromancer/undead/build_runtime.py` | [Hollow undead](reference/hollow-undead.md#assets) |
| Renderer | `client/renderer/HollowUndeadRenderer` | [Hollow undead](reference/hollow-undead.md) |

### Storm Roc (Wind)

| Feature | Owners | Reference |
| --- | --- | --- |
| Fight loop, state and tuning | `entity/roc/StormRocEntity`, `StormRocCombat`, `StormRocRules` | [Storm Summit](reference/storm-summit.md#phase-one) |
| Poses, hurtboxes, flight path | `entity/roc/StormRocPose`, `RocBodyPartEntity`, `RocApproach` | [Storm Summit](reference/storm-summit.md#phase-one) |
| How tornadoes, lightning strikes and warning circles are drawn | `client/renderer/RocEffectRenderer`, `entity/roc/RocEffectEntity` (what the server sends; damage stays in the fight loop); tornado texture `art/roc_effects/build_tornado_texture.py` | [Storm Summit](reference/storm-summit.md#phase-one) |
| Fight roster and lifecycle | `summit/StormRocEncounter` | [Storm Summit](reference/storm-summit.md) |
| Storm Summit realm | `summit/StormSummit`, `StormSummitManager`, `StormSummitLayout` (generated), `SummitRelicBlock`; layout `art/storm_summit/build_layout.py` | [Storm Summit](reference/storm-summit.md#the-realm) |
| Aerie and gong: who is called and which fight slot they get | `summit/StormAerie`, `StormAerieLayout` (generated), `StormGongBlock`, `StormGongBlockEntity`, `world/StormAerieStructure`, `client/renderer/StormGongRenderer`; layout `art/storm_aerie/build_layout.py` | [Storm Summit](reference/storm-summit.md#the-aerie) |
| Where called players land on the summit | `summit/StormSummit` (the arrival point), `summit/StormRocIntro` (carries them up) | [Storm Summit](reference/storm-summit.md#the-realm) |
| Intro cinematic (the snatch), in place of the loading screen | `summit/StormRocIntro`, `StormRocIntroTrack`, `client/StormRocIntroClient`, `client/StormRocIntroPuppets`, `mixin/StormRocIntroLoadingMixin`, `mixin/StormRocIntroHideMixin`; scene `art/storm_roc/intro/build_intro.py` | [Storm Summit](reference/storm-summit.md) |
| The Roc's model, renderer, crackle and gaze | `client/model/StormRocModel`, `client/renderer/StormRocRenderer`, `StormRocCrackleLayer`, `client/StormRocMotion` | [Storm Summit](reference/storm-summit.md#authoring) |
| Cloud sea and phase-two storm sky and rain | `client/SummitCloudEffects`, `client/SummitStormClient`, `mixin/SummitStormRainMixin`, `mixin/SummitStormWeatherMixin` | [Storm Summit](reference/storm-summit.md#phase-change-and-phase-two) |
| Operator commands | `command/SummitCommands` | [Storm Summit](reference/storm-summit.md#visiting) |
| Art sources and exporters | `art/storm_roc_mod/build_mod.py`, `art/storm_roc/build_runtime.py` | [Storm Summit](reference/storm-summit.md#authoring) |

### Pyre King (Fire)

| Feature | Owners | Reference |
| --- | --- | --- |
| Fight loop and hits | `entity/pyreking/PyreKingCombat`, `PyreKingEntity` | [Pyre King](reference/pyre-king.md#the-fight) |
| Moves, chooser, tuning, clip tempo | `entity/pyreking/PyreKingRules` | [Pyre King](reference/pyre-king.md#the-fight) |
| Weapon tracks and thrown-sword aim | `entity/pyreking/PyreKingTracks`, `PyreKingAim`, `PyreKingClips` (generated) | [Pyre King](reference/pyre-king.md#ownership) |
| His fire on the floor | `block/PyreKingFireBlock` | [Pyre King](reference/pyre-king.md#the-fight) |
| Intro cinematic | `entity/pyreking/PyreKingIntro`, `PyreKingIntroTrack`, `client/PyreKingIntroClient`; scene `art/pyre_king/intro/build_intro.py` | [Pyre King](reference/pyre-king.md#the-intro) |
| Model, renderer, blade fire and chains | `client/model/PyreKingModel`, `client/renderer/PyreKingRenderer`, `PyreKingEffectsLayer` | [Pyre King](reference/pyre-king.md#blade-fire-and-chains) |
| Burned Gate (the way in) | `hall/PyreGate`, `PyreSealBlock`, `PyreGateVeilBlock`, `PyreGateLayout` (generated), `world/PyreGateStructure`; layout `art/pyre_gate/build_layout.py` | [Pyre King](reference/pyre-king.md#the-burned-gate) |
| Burned Throne Hall realm | `hall/PyreHall`, `PyreHallManager`, `client/PyreSeaHaze`, `mixin/PyreSeaFogColorMixin`, `mixin/NaveFogMixin`, `command/HallCommands`; layout `art/pyre_hall/build_layout.py` | [Pyre King](reference/pyre-king.md#the-burned-throne-hall) |
| Operator commands | `command/PyreKingCommands` | [Pyre King](reference/pyre-king.md#commands) |
| Art source and exporter | `art/pyre_king/build_art.py` | [Pyre King](reference/pyre-king.md#ownership) |

## World generation

| Feature | Owners | Reference |
| --- | --- | --- |
| Structure registration | `world/ModWorldGen` | [Church and arena](reference/church-and-arena.md) |
| Where a locate teleport link lands | `world/StructureArrival` and `mixin/LocateArrivalMixin` route the request; each structure owns its own spot: `world/HollowGraveyardStructure`, `world/StormAerieStructure`, `world/PyreGateStructure`, `church/ChurchLayout` | [Church and arena](reference/church-and-arena.md) |

## Tests and checks

| What | Where | Reference |
| --- | --- | --- |
| Contract checks run by the build | `GuardianBeamContractTest`, `NecromancerContractTest`, `PyreKingContractTest`, `PartyStoreTest`, `StormRocGazeContractTest`, wired in `build.gradle` | [Testing](reference/testing.md) |
| Server and client fixtures | Helper classes under `src/guardianTest/java/`; runners in `tools/` named after the area and ending in smoke.init.gradle | [Testing](reference/testing.md#selecting-a-fixture) |
| Asset and export checks | `tools/check_assets.py` | [Art](reference/art-and-assets.md) |
| This index and the docs' links | `gradle/docs-check.gradle` | [Documentation rules](README.md) |

## Retired code still in the source

Nothing in play reaches these. Do not treat them as current behavior.

| What | Files | Note |
| --- | --- | --- |
| Overgrowth oak, Nature's former ultimate | `util/OvergrowthManager`, `OvergrowthThrowRules`, `NatureTreeLayout`, `entity/OvergrowthSeedEntity`, `entity/AwakenedTreeEntity`, `block/NatureOakBlock`, `NatureOakLogBlock`, `NatureOakTrunkBlock`, `NatureGrowthBlock` | Kept at the developer's request for possible later use |
| Entangle vines and root knots | `util/EntangleTracker`, `client/EntangleClientEffects`, `client/overlay/EntangleHudOverlay`, `block/NatureRootKnotBlock` | No spell applies them; see [status](STATUS.md) |
| Fire spirit and stone zombie | `entity/FireSpiritEntity`, `entity/StoneZombieEntity` | Registered entities from an earlier design; no current spell summons them |
