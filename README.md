# WHITECLIENT 1.1.0 (Fabric, Minecraft 1.21.11)

Client-side visuals mod. No mixins, no cheats, works on any server.

## Features
| Category | Module | What it does |
|---|---|---|
| Visuals | **Halo** | Glowing golden ring with spikes above your head (follows head tilt) |
| Visuals | **Wings** | Spiky white glowing line-art wings that flap |
| Visuals | **Aura** | Soft white glow over/around your player + orbiting sparkles |
| Visuals | **Block Trail** | Glowing white squares on the blocks you walk over, fading out |
| World | **Galaxy Sky** | Twinkling stars + milky-way band with nebula glow |
| World | **Snow** | Soft glowing snow / dust floating around you |
| World | **3D Text** | Big blocky extruded 3D words that pop into the world one by one |
| HUD | **Watermark** | Dark pill at the top: logo / name / ping / FPS |
| Menu | **Menu Background** | Animated orange energy veins (or purple-pink aurora / mono) on the title screen and pause menu |
| Menu | **Pause Mods Button** | Adds a full-width **Mods** button to the pause menu that opens the WHITECLIENT menu |

Halo, Wings and Aura show in third person (F5).

## Controls
- **Right Shift** – open the WHITECLIENT menu (rebind in Options > Controls)
- Pause menu → **Mods**
- `/whiteclient` – open the menu
- `/whiteclient text <message>` – set the 3D text
- `/whiteclient reload` – reload `config/whiteclient.json`

Every module has settings (colours, sizes, speeds...). Click a module card to expand them.

## Install
Put `whiteclient-1.1.0.jar` and **Fabric API** (for 1.21.11) in `.minecraft/mods`, launch with Fabric Loader for 1.21.11.

## Build from source
Needs Java 21.
```
gradlew build
```
The JAR is written to `build/libs/whiteclient-1.1.0.jar`.
