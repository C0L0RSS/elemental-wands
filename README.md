# Elemental Wands (Fabric) - Minecraft 1.21.10

Elemental Wands is a Fabric mod that adds five combat wands (Fire, Wind, Stone, Nature, Space) plus a Titan Sword.

Current mod version: `2.2.0`

## Requirements

- Minecraft `1.21.10`
- Fabric Loader `0.17.2+`
- Fabric API `0.138.4+1.21.10`
- Java `21`

## Controls

- `Right Click`: cast primary ability
- `Shift + Right Click`: cast secondary ability
- `X` (default keybind): cast ultimate ability

All wands share a short global cooldown and individual per-ability cooldowns. A HUD overlay appears when holding a wand to show ability readiness/cooldowns.

## Wands and Abilities

### Fire Wand

- Primary: **Inferno Wave** projectile
- Secondary: **Magma Surf** (speed burst + temporary fire trail)
- Ultimate: **Maximum Meteor** (high-impact meteor strike)
- Passive while held: fire resistance

### Wind Wand

- Primary: **Vacuum Blades** (dual blades)
- Secondary: **Waylay Dash** (up to 3 dash charges, recharging system, chain dash scaling)
- Ultimate: **Calamity Tornado**

### Stone Wand

- Primary: **Tectonic Spikes** (line of temporary stone spikes, damage + knock-up)
- Secondary: **Aegis** defensive earth ability
- Ultimate: **Titan Dome**

### Nature Wand

- Primary: **Seedling Shot** (thrown seed — deals impact damage on a direct hit and plants a lingering seedling where it lands; hit damage scales with the target's Entangle stacks)
- Secondary: **Tendril Bloom** (sends homing tendrils from your planted seedlings toward nearby enemies, blooming around them)
- Ultimate: **Overgrowth** (pollen cloud that amplifies your nearby seedlings)
- Passive while held: **Verdant Step** (lily pads bloom across water as you walk, letting you cross ponds)

### Space Wand

- Primary: **Singularity Bolt**
- Secondary: **Blink Rift** (short-range blink that leaves a rift for swapping)
- Ultimate: **Event Horizon**

### Titan Sword

- Fireproof, netherite-tier sword item included in the tools tab

## Building

From the project root:

```bash
./gradlew build
```

Build artifacts are written to:

- `build/libs/elementalwands-2.2.0.jar`
- `build/libs/elementalwands-2.2.0-sources.jar`

Install the mod jar by placing it in your Minecraft mods folder:

- macOS/Linux: `~/.minecraft/mods/`
- Windows: `%APPDATA%\\.minecraft\\mods\\`

## Development

Useful Gradle tasks:

- `./gradlew runClient` - launch a dev client
- `./gradlew build` - compile + package jars

## License

MIT (see `LICENSE`)
