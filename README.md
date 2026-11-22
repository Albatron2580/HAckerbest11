# WHITECLIENT 1.2.0 (Fabric, Minecraft 1.21.11)

Client-side visuals mod in a white/black style. Works on any server, no cheats.

## Features
| Category | Module | What it does |
|---|---|---|
| Visuals | **Halo** | White glowing halo with spikes, also on other players |
| Visuals | **Wings** | Spiky white glowing wings, also on other players |
| Visuals | **Aura** | Soft white glow over your player + sparkles |
| Visuals | **Block Trail** | Glowing squares on the blocks you walk over |
| World | **Galaxy Sky** | Twinkling stars + milky way |
| World | **Snow** | Glowing snow / dust in the air |
| Music | **Spotify** | Now-playing card (cover, title, artist, progress, current lyric) |
| Music | **3D Text** | Big white 3D words that pop up in time with your Spotify lyrics (or your own text) |
| HUD | **Watermark** | Logo / name / ping / FPS pill |
| Menu | **Menu Background** | Animated white energy (or aurora / mono) on title + pause screen |
| Menu | **Rounded Buttons** | Rounded see-through buttons on the title + pause screen |
| Menu | **Pause Mods Button** | Opens the WHITECLIENT menu (own row next to NoRisk / ModMenu buttons) |

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
Put `whiteclient-1.2.0.jar` and **Fabric API** (1.21.11) in `.minecraft/mods`.

## Build from source
Gradle needs Java 25 (Loom 1.19); the mod targets Java 21. `gradlew build` → `build/libs/`.
