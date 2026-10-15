[RU](README.md) | **EN**

# Folderito

A music player for Android: the library is built around the **folders** on
your device — you can play selected folders or individually selected tracks.
Written in Kotlin + Jetpack Compose.

## Features

### Library
- **Folder-based library** — an expandable folder tree with stats (path,
  folder/track counts, total duration).
- **Track and folder selection** — checkboxes (folders have three states:
  none / partial / all), a “Selected: N tracks · ≈ X min” bar with transport
  controls and a shortcut to the player.
- **Favorites and hidden folders** — holding a folder tile opens a menu:
  add to favorites (a “Favorites” section after playlists, the tile gets a
  star, the folder stays in the tree) or hide (the folder disappears from
  the tree, search and the song list). Both lists are managed in Settings.
- **Search** by title, artist and album: results are grouped into “Folders”
  (expanded in place) and “Songs”.
- **Quick play** — double-tapping a track with an empty selection plays its
  whole folder; a bar with the folder name and transport controls appears
  at the bottom.

### Playlists
- **.m3u/.m3u8 playlists** — read from shared storage and shown in the tree
  (tap to expand, hold to play, checkbox to select).
- **Saving a playlist** — a button in the song list writes the displayed
  list (selection or the playing folder) to an .m3u file.

### Player
- **Player** — the cover acts as a record sleeve with a vinyl record sliding
  out to the right (a photo image that spins during playback, one revolution
  in 5.4 s), “album / folder” chips, seeking,
  shuffle / prev / play / next / repeat (off → all → one).
- **Audiobook settings** — speed 0.5–2×, skip silence, “speech clarity”
  (a voice preset that overrides the equalizer).
- **Sleep timer** with presets and a countdown in the side menu.
- **Equalizer** — the system one (android.media.audiofx): 8 presets +
  custom, real device bands, bass boost, surround sound.
- **Tooltips** — holding a header button opens a window with a description.

### Looks and data
- **Color schemes** — Default, Amber, Mist, Olive, Steel (with its own dark
  pair): five base colors unfold into a full design-token table; plus
  dark/light theme and following the system setting.
- **Language** — Russian / English / German (or system).
- Background playback with a media notification (Media3 / ExoPlayer);
  resume after restart and from a media button (Tasker/MacroDroid,
  a car stereo).

## How to build

1. Install **Android Studio** (latest version): https://developer.android.com/studio
2. Open the project folder (File → Open). Studio will download the SDK and
   Gradle itself — accept the prompts.
3. Enable developer mode and **USB debugging** on your phone
   (Settings → About phone → tap “Build number” 7 times; then
   Settings → Developer options → USB debugging).
4. Connect the phone, pick it in the device list and press **Run ▶**.

Building the APK without Android Studio (JDK 17 in PATH required):
`gradlew.bat assembleDebug` → `app\build\outputs\apk\debug\app-debug.apk`.

A release build (`gradlew.bat assembleRelease` →
`app\build\outputs\apk\release\app-release.apk`) is signed with a keystore
configured in `local.properties` (`release.storeFile`, `release.storePassword`,
`release.keyAlias`, `release.keyPassword`); the keystore file itself is not
in git — keep it and the passwords somewhere safe, they are required for
every update.

## Technologies

| Layer | Solution |
|---|---|
| UI | Jetpack Compose, Material 3, Manrope font (weights 500–800) |
| Playback | Media3 ExoPlayer + MediaSessionService |
| Data | MediaStore: audio (audio-only permission — Google Play friendly), .m3u via MediaStore.Files / MediaStore.Audio.Playlists |
| Equalizer | android.media.audiofx: Equalizer, BassBoost, Virtualizer, DynamicsProcessing (speed, skip silence) |
| Settings & state | DataStore Preferences: theme, skin, language, equalizer, speed, queue (incl. shuffle base), selection, expanded folders, favorites, hidden folders, saved position |

## Roadmap

- Choosing root folders for the library (partly solved by hiding folders)
- Album art from embedded tags (fallback — gradient)
- Home screen widgets
- Playlist export with relative paths (“portable” files)

## Structure

```
app/src/main/java/com/foldersound/
├── FsApp.kt                  — the app, singletons (repository, connection)
├── MainActivity.kt           — entry point, permissions, theme
├── data/                     — models, MediaStore repository, DataStore
├── playback/                 — service, MediaController, equalizer
└── ui/
    ├── theme/                — color tokens, skins, typography
    ├── i18n/                 — RU/EN/DE strings, plural forms
    ├── common/               — covers, sliders, checkboxes, dialogs, gestures
    ├── icons/                — vector icons
    ├── library/              — main screen: tree, favorites/hidden, bars
    ├── player/               — “Now playing” screen
    ├── songs/                — song list screen (+ playlist saving)
    ├── eq/                   — equalizer screen
    ├── sleep/                — sleep timer screen
    └── settings/             — color schemes, theme, language, seeking, folder lists
```

## Credits

The vinyl record illustration in the player is by
[takestudio / Pngtree](https://ru.pngtree.com/freepng/vintage-vinyl-record-cutout_21097493.html)
(free license with attribution).

## License

[Apache License 2.0](LICENSE). The code may be freely used, modified and
distributed, including commercially, provided the copyright notice is
preserved. The Manrope font is distributed under a separate license
(SIL Open Font License).
