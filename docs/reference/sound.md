# Sound

Gameplay code never plays vanilla `SoundEvents` directly. Every sound is a
mod-owned event in `registry/ModSounds`, named for the moment it belongs to.
`src/main/resources/assets/elementalwands/sounds.json` decides what each event
plays, so audio is replaced there without touching gameplay code.

## Current state

All 342 events still redirect to the vanilla sound they used before, through
`"type": "event"` entries, and keep that vanilla sound's subtitle. No custom
audio ships yet. Volume, pitch, category and position stay at the call site.

## Event names

| Pattern | Used for |
| --- | --- |
| `spell.<element>.<spell>.<moment>` | Player spells, including spell projectiles and managers |
| `entity.<mob>.<action>.<moment>` | Boss and mob attacks; `entity.<mob>.voice.<kind>` for calls shared across attacks; `ambient`, `hurt`, `death`, `step` for the standard slots |
| `cinematic.<scene>.<cue>` | Intro and transformation cutscenes |
| `world.<place>.<moment>` | Arena, church, crypt and aerie lifecycle sounds |

`<spell>` is the player-facing spell name. Sounds layered at one moment carry a
layer word, for example `impact_boom` and `impact_crack`. The Java constant is
the id in upper case with dots as underscores.

One event means one moment. Give a new moment its own event even when it reuses
audio; several events may point at the same file.

## Adding or replacing a sound

1. For a new moment, add a constant to `ModSounds` and an entry to `sounds.json`.
2. To replace audio, put a mono Ogg Vorbis file under
   `assets/elementalwands/sounds/` and point the event at it:

   ```json
   "spell.fire.inferno_wave.cast_ignite": {
     "subtitle": "subtitles.elementalwands.spell.fire.inferno_wave.cast",
     "sounds": ["elementalwands:spell/fire/inferno_wave_cast"]
   }
   ```

   List several files to get random variation. Stereo files play at full volume
   everywhere instead of from a position, so use mono for anything in the world.
3. Add the subtitle text to the language file when the subtitle key changes.
4. When one custom file replaces several layered events, point the main layer
   at the file and remove the other layers' play calls and events.

Explosions take a registry entry: use `ModSounds.entry(...)`.

Block place and break sounds on mod blocks still use vanilla `BlockSoundGroup`
values in `ModBlocks` and `ModSpellBlocks`.
