# Melodia Lyrics — Fabric mod for Minecraft Java 26.3

A client-side Fabric mod that replicates the **Show the Lyrics** experience — synced lyric
bars floating in the world, a corner now-playing card, pastel accent palette, typewriter /
drop / rise / word animations — but wired to **your own music player**: Melodia writes its
live playback state to a small local file, and this mod turns it into Minecraft lyrics.

Works on Linux (and anywhere Melodia runs), needs no Windows media session, no accounts and
no network at runtime except lrclib lyric lookups.

```
            ┌ Minecraft ─────────────────────────┐
            │  ♪ title • artist   ────●────  +   │  ← corner widget (HUD)
 Melodia    │                                    │
 now_playing.json ──▶ MediaManager ─▶ 3D lyric bars floating in the world (or in-world 3D
            │        │                            │  if you prefer, via melodia config)
            │        └─▶ LyricsService (cache → lrclib → Melodia line bridge)
            └────────────────────────────────────┘
```

---

## How it works

1. **Melodia (the music player)** publishes its live state to
   `~/.config/melodia/now_playing.json` (`$XDG_CONFIG_HOME` is respected). It rewrites the
   file a few times per second with the current track, play position, album accent colour and
   the player's own synced-lyrics cursor.
2. **MelodiaStatusProvider** reads that file (10 s heartbeat, extrapolates the position
   between writes exactly like a real media session sample).
3. **LyricsService** finds synced lyrics for the song:
   disk cache (`config/melodia_lyrics/cache/*.lrc`) → **lrclib.net** (exact then best-match
   search, with YouTube-noise title cleaning) → **bridge fallback**: if nothing is on lrclib,
   the current + next line straight from Melodia (perfectly synced, since Melodia already
   syncs them).
4. **Lyrics3DRenderer** places the active line in the world ahead of you — alternating
   left/right of your view direction with a little height jitter — and **billboards** it at
   the camera, so it's easy to read while you play. Drawn normal + see-through, with an
   optional glow outline.
5. **PlayerWidgetRenderer** draws the corner card: `title • artist` and an accent progress bar.

## Feature-parity table (vs. the original)

| Show the Lyrics feature | Melodia Lyrics |
| --- | --- |
| Windows media session → track info | Melodia's `now_playing.json` (Linux/any OS) |
| Corner now-playing widget, 5 positions | ✔ same (`TOP_LEFT` default, progress bar, alpha/scale) |
| 3D world-space lyric bars | ✔ identical math (anchor, spread, follow, speed lead, keep-on-screen) |
| TYPEWRITER / DROP / RISE / WORDS animations | ✔ same constants and easing |
| ALBUM / palette / RAINBOW / CUSTOM accent | ✔ ALBUM comes from Melodia's album colour directly |
| Sync offset, wrap width, fonts, bold, uppercase, glow, shadow, bg | ✔ same options |
| Karaoke fill (even spread) | ✔ + **word-timed fill** using Melodia's per-word timings |
| Instrumental countdown dots, next-line preview | ✔ |
| YACL config screen + ModMenu | ✔ via ModMenu (ModMenu optional; YACL required) |
| lrclib fetch with cache | ✔ same strategy/UA |
| Works when the song isn't on lrclib | ✔ falls back to Melodia's own synced lines |

## Install

- Minecraft **26.3**, Fabric Loader **0.19.5+**, Fabric API **0.163.0+26.3**
- Melodia running and playing something (for the overlay to show)
- Drop `melodia-lyrics-0.2.0.jar` into `mods/`

## Build from GitHub

The repo includes the Gradle wrapper and an Actions workflow (`.github/workflows/build.yml`):

```bash
cd melodia-lyrics-mod
git init && git add . && git commit -m "Melodia Lyrics 0.2 (Show the Lyrics replica)"
git branch -M main
git remote add origin https://github.com/<you>/<repo>.git
git push -u origin main
```

Green run → **Artifacts** → `build/libs/melodia-lyrics-0.2.0.jar`.

Note: Minecraft 26.3 is unobfuscated, so the build has **no `mappings` line** on purpose.

## Configuration

`config/melodia-lyrics.json` (created on first launch). Key fields:

```json
{
  "playerWidgetPosition": "TOP_LEFT",
  "showPlayerWidget": true,
  "playerWidgetScale": 1.0,
  "playerWidgetAlpha": 0.95,
  "autoHide": false,
  "showLyrics": true,
  "accentColor": "ICE",
  "lyricsColorHex": 13500410,
  "timeOffsetMs": 120,
  "textDistance3D": 15.0,
  "speedLead3D": 0.5,
  "anchorFollow3D": 1.0,
  "sideSpread3D": 50.0,
  "keepOnScreen3D": false,
  "textHeightOffset3D": 2.5,
  "lyricsAnimation": "TYPEWRITER",
  "lyricsWrapWidth": 110,
  "lyricsFont": "DEFAULT",
  "lyricsFontScale": 1.25,
  "boldActiveLyric": false,
  "lyricsUppercase": false,
  "lyricsGlowEffect": false,
  "lyricsOutlineColorHex": 16777215,
  "lyricsDropShadow": true,
  "lyricsBgAlpha": 0.0,
  "activeLineHighlight": false,
  "showInstrumentalCountdown": false,
  "showNextLinePreview": false,

  "melodiaStatusFile": "",
  "showHud": false,
  "hudAnchor": "above_hotbar",
  "hudScale": 1.0,
  "hudMaxWidth": 560,
  "hudBackground": true,
  "hudBackgroundOpacity": 110
}
```

| Field | Meaning |
| --- | --- |
| `showLyrics` | Master switch for the 3D (and HUD) lyric bars |
| `accentColor` | `ALBUM` (Melodia's album colour), `RAINBOW`, `CUSTOM` (+ `lyricsColorHex`), or a palette name: CREAM, HONEY, BLUSH, CORAL, MINT, ICE, LAVENDER, ORCHID, SAGE, SLATE |
| `textDistance3D` | Blocks ahead of the camera the bar appears (3–24) |
| `speedLead3D` | Leads the bar further ahead the faster you move (seconds of travel; 0 = off) |
| `anchorFollow3D` | How much the bar travels with you at speed (0 = pinned in world) |
| `sideSpread3D` | Max degrees left/right of your view; sides alternate with jitter |
| `keepOnScreen3D` | Clamp spread so text stays inside your FOV |
| `textHeightOffset3D` | Vertical offset from eye level (−2…+5) |
| `lyricsAnimation` | `TYPEWRITER` / `DROP` / `RISE` / `WORDS` |
| `lyricsFont` | `DEFAULT` / `UNIFORM` / `ALT` / `ILLAGER` |
| `lyricsGlowEffect` | Outline mode (see-through-through-walls + shadow + bg are then off, as in the original) |
| `activeLineHighlight` | Karaoke fill; word-timed when Melodia reports word timings |
| `showInstrumentalCountdown` | Dots fill during a ≥14 s gap before the next line |
| `showNextLinePreview` | Dimmed upcoming line |
| `melodiaStatusFile` | Optional absolute path override to `now_playing.json` |
| `showHud` | Optional flat HUD karaoke overlay (off by default) |

### Configure (ModMenu + YACL)

With **ModMenu** and **YACL** installed, open *Mods → Melodia Lyrics → Configure* for the full
GUI (widget position/scale/opacity, accent, 3D world settings, text style, extras, timing,
HUD). Every option is also reachable through `/melodia` commands for headless setups.

| Command | Effect |
| --- | --- |
| `/melodia`, `/melodia lyrics` | Toggle 3D lyrics |
| `/melodia widget` / `widget <corner>` | Toggle / place the now-playing card (`top_left`, `top_right`, `bottom_left`, `bottom_right`, `center`) |
| `/melodia hud` | Toggle the flat HUD overlay |
| `/melodia accent <mode>` | `album`, `rainbow`, `custom`, or a palette name |
| `/melodia animation <style>` | `typewriter`, `drop`, `rise`, `words` |
| `/melodia offset <ms>` | Sync offset (−1000…1000) |
| `/melodia status` | What the mod currently sees — great for debugging |
| `/melodia reload` | Re-read the config + status file path |

Env override: `MELODIA_STATUS_FILE=/path/now_playing.json`.

## Project layout

```
build.gradle / gradle.properties / settings.gradle   Fabric Loom, Java 25, split source sets
src/main/resources/fabric.mod.json
src/client/resources/assets/melodia_lyrics/icon.png
src/client/java/com/melodia/lyrics/
    MelodiaLyricsClient.java        entrypoint: wiring + 5-tick poll (same as the original)
    Commands.java                   /melodia config commands
    config/LyricsConfig.java        JSON config (same field names as the original)
    media/                          TrackInfo, MediaProvider, MediaManager,
                                    MelodiaStatusProvider (≈ WindowsMediaSessionProvider),
                                    MelodiaSnapshot (words, accent, line bridge)
    lyrics/                         LyricLine, Lyrics, LrcParser, LrclibClient, LyricsService
    render/                         Lyrics3DRenderer, PlayerWidgetRenderer, AccentColor,
                                    HudKaraokeRenderer (our optional HUD extra)
```

## License

MIT. Not affiliated with FireStund or Melodia. "Show the Lyrics" was used as the functional
reference; this project contains none of its code.
