# Sound

Gameplay code never plays vanilla `SoundEvents` directly. Every sound is a
mod-owned event in `registry/ModSounds`, named for the moment it belongs to.
`src/main/resources/assets/elementalwands/sounds.json` decides what each event
plays, so audio is replaced there without touching gameplay code.

## Current state

All five wand elements play generated audio from `sounds/spell/<element>/`.
The developer has listened to every set outside the game and accepted all five
for use; Wind is the one they want to revisit. None of it has been heard in
game yet. Every boss, mob, cutscene and world event still redirects to the
vanilla sound it used before, through a `"type": "event"` entry, and keeps that
vanilla sound's subtitle. Volume, pitch, category and position stay at the call
site.

## Generated sounds

Spell audio is synthesized, not sampled. `tools/sound/synth.py` holds the shared
building blocks and one module per element (`tools/sound/fire.py`, `wind.py`,
`stone.py`, `nature.py`, `space.py`) holds the recipes and the list of files they
produce. Each module's opening comment describes that element's voice and what
the developer has rejected. List a new module in `recipes()` in
`tools/build_sounds.py`.

```sh
python3 tools/build_sounds.py meteor --wav /tmp/listen   # rebuild matching sounds, with WAV copies
python3 tools/build_sounds.py --check                    # events, files and subtitles agree
```

Building needs `numpy` and `soundfile`. An Ogg file differs byte-for-byte on
every encode, so rebuild only the sounds whose recipe changed. Recipes are
written for pitch 1.0 at the call site. The exception is a sound passed to an
explosion, which Minecraft plays near 0.7 pitch; pre-shift it with `repitch`.

Keep sparse high clicks, such as sparks, inside the loud part of a sound. Once
the body has faded they are heard as stray ticks on the tail.

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
