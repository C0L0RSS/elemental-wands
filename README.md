# Elemental Wands (Fabric) — Minecraft 1.21.10

This project adds 4 magic wands:
- **Fire Wand**: shoots a flame beam, explodes on hit, and flings nearby blocks upward as falling blocks.
- **Wind Wand**: launches you ~8 blocks high and gives slow falling for a glide.
- **Stone Wand**: right-click throws the targeted block as a falling block; **SHIFT + right-click** creates a 3×3 stone wall.
- **Ice Wand**: shoots a snow beam; freezes blocks into ice and applies stacking slowness on hit.

## How to build the .jar (so you can put it in your mods folder)

1) Install **Fabric Loader** for Minecraft 1.21.10  
2) Install **Fabric API** for Minecraft 1.21.10  
3) Open this project in **IntelliJ** or **VS Code** with Gradle support  
4) Run the Gradle task: **build** (outputs a jar into `build/libs/`)

You want the file like:
`build/libs/elementalwands-1.0.0.jar`

Drop that `.jar` into:
`~/.minecraft/mods/`

## Notes
- This is a starter version (no mana system yet).
- If you want multiplayer + balance, we can add cooldown tuning, durability, and recipes.
