# Heart Fight Tracker (Fabric, MC 1.21.11, client-side)

Keybinds (rebind in Controls > "Heart Fight Tracker"):
- `[`  start tracker      - `]`  stop tracker (wipes all data)      - `\`  open HUD settings

Settings screen: width, height, scale, X/Y position, opacity. Saved to config/heartfight.json.

## Build the .jar
Option A (no setup): push this folder to a GitHub repo -> Actions tab -> "build" -> download the
`heartfight-jar` artifact (unzip it; the .jar inside goes in your Dawn Client mods folder).
Option B (local): install JDK 21 + Gradle 9.2+, run `gradle build`, jar is in build/libs/heartfight-1.0.0.jar
(not the -sources jar).
