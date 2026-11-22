# WHITECLIENT 1.5.0 (Fabric, Minecraft 1.21.11)

Client-side visuals mod in a white/black style. Works on any server, no cheats.

## Features
| Category | Module | What it does |
|---|---|---|
| Cosmetics | **Halo** | White glowing halo with spikes, also on other players |
| Cosmetics | **Wings** | Spiky white glowing wings, also on other players |
| Cosmetics | **Aura** | Soft white glow over your player + sparkles |
| Cosmetics | **Block Trail** | Glowing squares on the blocks you walk over |
| Cosmetics | **Pets** | A pet floating next to you: glowing Jellyfish (kwal), Wisp, Butterfly or a Dog, Cat, Fox, Parrot, Bee, Allay, Axolotl, Frog, Rabbit, Panda, Chicken, Bat, Vex. Only you see it; it can't be hit or push you |
| PvP | **Target ESP** | Effects that spin around the entity you hit / look at: Shackles (glowing chains), Orbit, Spiral, Cards, Ring, Ghosts, Constellation. Visual only |
| World | **Galaxy Sky** | Twinkling stars + milky way |
| World | **Snow** | Glowing snow / dust in the air |
| World | **Rain FX** | Glowing rain streaks, ripples on the ground, splash droplets + rainy colour mood |
| Music | **Spotify** | Now-playing card (cover, title, artist, progress, current lyric) |
| Music | **3D Text** | Big white 3D words that pop up in time with your Spotify lyrics (or your own text) |
| HUD | **Watermark** | Logo / name / ping / FPS pill |
| Menu | **Main Menu** | WHITECLIENT title screen. **Showcase** layout (default): big planet logo + text menu on the left, your player on grey silk curtains on the right with little planets floating by. **Centered** layout: the older centred buttons |
| Menu | **Menu Background** | Animated white energy (or silk / aurora / mono) on title + pause screen |
| Menu | **Rounded Buttons** | Rounded see-through buttons on the title + pause screen |
| Menu | **Pause Mods Button** | Opens the WHITECLIENT menu (own row next to NoRisk / ModMenu buttons) |
| Menu | **Main Menu → Walking Player** | Your skin walks into the title screen, turns to you and follows your mouse |
| Client | **Theme** | Change every menu colour (accent, background, panel, cards, text, toggles), opacity, roundness |
| Client | **Wardrobe** | Free custom skins from PNG files + cosmetics & pet picker with a 3D preview. No pay-to-win, only a Support button |

## Menu (Right Shift)
- **Home:** big category cards (Cosmetics, PvP, World, Music, HUD, Menu) + Wardrobe / Search / Theme / Support.
  Start typing to search.
- **Window:** click a card. The window floats: drag it by the top bar or the **drag** handle, resize it with **resize**
  in the corner (it remembers where you put it). Click a module to see its settings next to the list.
- **Colours:** click any colour to open the picker (hue / saturation / brightness + presets).
  **Theme** recolours the whole menu.

## Wardrobe / custom skins
Put a 64x64 skin `.png` in `.minecraft/config/whiteclient/skins/` (button **Open Skins Folder**), press **Reload**
and click it. Turn on **Slim Arms** for Alex-style skins. Custom skins are client-side: only you see them.

Cosmetics on other players: invisible players are skipped unless they wear armor.

## Spotify
**Without logging in (Windows):** just open the Spotify desktop app. WHITECLIENT reads the song from
the Spotify window. Lyrics sync starts from the next song that begins while you are in-game.

**Perfect sync (any OS), one-time setup:**
1. Go to https://developer.spotify.com/dashboard and click **Create app**.
2. Any name/description. Redirect URI: `http://127.0.0.1:25588/callback` (click Add). Tick **Web API**. Save.
3. Open the app's settings and copy the **Client ID**.
4. In Minecraft: Right Shift → **Music** → Spotify → paste the Client ID → **Login**
   (or `/whiteclient spotify login <clientId>`). Accept in the browser tab that opens.

Lyrics come from lrclib.net. If the lyrics are a bit early/late, change **Lyrics Offset**.

## Controls
- **Right Shift** – WHITECLIENT menu
- `/whiteclient text <message>` – show your own 3D text
- `/whiteclient spotify login|logout|status`
- `/whiteclient reload` – reload `config/whiteclient.json`

## Install
Put `whiteclient-1.5.0.jar` and **Fabric API** (1.21.11) in `.minecraft/mods`.

## Build from source
Gradle needs Java 25 (Loom 1.19); the mod targets Java 21. `gradlew build` → `build/libs/`.
