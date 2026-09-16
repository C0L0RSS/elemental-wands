# One-page starter guide — September 12, 2026

The welcome scroll and hub Guide are now one concise Getting Started page. It
covers the actual hub binding, Basic / Technique / Ultimate bindings, remapping,
element-specific Flux, buying/equipping spells and permanent purchases. It ends
with an invitation to explore. Next / Back buttons and the page counter are gone.

The startup lore and Guardian walkthrough are removed. The offering chest now
gives a short socket-use hint in the world instead of pointing to removed Guide
content. A larger world-driven story remains future work. First-join acknowledgement,
progression, store, loadouts, and the Guide button keep their existing behavior.
Players who already saw the introduction can reopen it through H → Guide.

Verification: Gradle build and existing regressions passed. The native client
rendered the simplified guide for a new, unaffiliated player; its Open Hub button
successfully opened the initial element chooser. Existing category/UI/control
checks passed, as did whitespace and JAR integrity checks. The screenshot at
`docs/wand-guide-verification/guide-simple.png` is actual Minecraft rendering with
fixture state over the title panorama. No human onboarding playtest is claimed.

Installed in the existing Lunar Fabric 1.21.10 mods folder. Source and installed
JAR SHA-256 match:
`62ea9f888238e08ad8a438da53e7c1f961dddbd0dc138996dad8257ea3e2e4fa`.

Previous JAR backup:
`.local-backups/lunar/20260912-193111/elementalwands-2.2.0.jar`.
Restart Lunar to load the update.
