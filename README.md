# Zooming Mirror

**English** · [Português (Brasil)](docs/readme/pt-BR.md)

An Android mirror: the front camera fills the whole screen and you pinch to
zoom in. There is nothing else on screen — no buttons, no status or navigation
bar — except a value while it changes (the zoom or the light) and the settings
when you ask for them. Built with [Expo](https://expo.dev), React Native,
TypeScript and a small Kotlin view on CameraX.

## Using it

- Open the app and allow the camera. The front camera shows up full screen.
- **Pinch out** to zoom in, **pinch in** to zoom back out, up to 10×. The spot
  between your fingers stays put, and moving both fingers pans around.
- **Drag with one finger** to move around the zoomed image.
- **Double tap** to go to 1×, and again to go back to the zoom you were at.
- **Long press** to freeze the image; zoom and drag still work on it. Long
  press again to go live.
- **Slide up or down along the right edge** to make the image brighter or
  darker.
- **Swipe inwards from the right edge**, at the height of the zoom circle, to
  show a gear; tap it for the settings.
- While the zoom or the light changes, a translucent circle low on the screen
  shows the value, and fades out as soon as you let go. A one-finger drag
  never shows it.
- The screen stays on while the app is open.
- Swipe from the top or bottom edge to bring the system bars back for a moment.

### Settings

| Setting            | Default | What it does                                                       |
| ------------------ | ------- | ------------------------------------------------------------------ |
| Automatic light    | on      | While zoomed, exposure and white balance follow what is on screen. |
| Face priority      | on      | The camera's own face-priority metering, where it has one.         |
| High resolution    | on      | Asks the camera for up to 4K instead of its default (≤ 1080p).     |
| Sharpening (GPU)   | off     | Experimental: bicubic enlarging plus edge sharpening on the GPU.   |
| How others see you | off     | Shows the image without mirroring it.                              |

Settings the phone cannot do are greyed out. The bottom of the screen shows the
size the camera actually delivers and the app version.

If the camera permission was denied, the screen stays black: tap it to be asked
again, or to land on the app settings once Android stops asking.

## Install

Download the APK from the
[latest release](https://github.com/fershibli/zooming-mirror/releases/latest)
and open it on the phone (allow installs from that source when asked).

## Development

```bash
npm install
npm run android   # expo run:android — needs the Android SDK locally
```

### How the zoom works

Most front cameras have no zoom of their own, so the app does not ask the camera
for one. The camera keeps streaming at 1× and the zoom enlarges the picture —
the same on every phone. The stream is requested at 16:9, so a portrait screen
crops as little of it as possible, and at up to 4K when **High resolution** is
on: CameraX caps the preview at 1080p unless the app passes its own resolution
strategy.

The picture reaches the screen one of two ways:

- **Standard:** CameraX's `PreviewView` in `COMPATIBLE` mode (a `TextureView`;
  a `SurfaceView` does not follow view transforms), scaled and moved as a whole
  view. The GPU composes the camera texture straight to the screen, so a big
  stream still adds detail when zoomed.
- **GPU (experimental):** an OpenGL ES pass of its own. Every screen pixel is
  mapped through the zoom to the camera frame and sampled with Catmull-Rom
  (bicubic) when enlarging, then a second pass applies contrast-adaptive
  sharpening, stronger the further it zooms. If the GPU path cannot start, the
  app falls back to the standard one and greys the setting out.

With **Automatic light**, the camera meters exposure and white balance on the
visible area after every gesture. Cameras that cannot take a metering area get
a slow loop instead, which reads the brightness of what is on screen and nudges
the exposure compensation. The manual slide on the right edge adds its own
compensation on top.

The camera view is a local native module (`modules/mirror-view`), so it does not
exist in Expo Go. Use the APK from a release or a development build.

| Command             | What it does                                   |
| ------------------- | ---------------------------------------------- |
| `npm run lint`      | ESLint                                         |
| `npm run typecheck` | TypeScript                                     |
| `npm run format`    | Prettier                                       |
| `npm run release`   | Versioning script (`-- --dry-run` to rehearse) |

### Architecture

- `App.tsx` — the root; hides the system bars and renders the mirror.
- `src/Mirror.tsx` — asks for the camera permission and shows the mirror, the
  gear and the settings.
- `src/Settings.tsx`, `src/GearButton.tsx` — the settings screen and the hidden
  gear that opens it; `src/store/settings.ts` keeps the settings (Zustand +
  AsyncStorage).
- `modules/mirror-view/` — local native module (Kotlin):
  - `MirrorView` binds the camera and ties everything together;
  - `MirrorGestures` turns touches into zoom, pan, double tap, freeze, the
    exposure slide and the gear swipe;
  - `PinchZoom` is the zoom as numbers; `PreviewRenderer` and `GlRenderer`
    (with `GlShaders`) turn it into pixels;
  - `Exposure` meters the light, `ValueIndicator` is the translucent circle.
- `plugins/` — config plugin injecting the release signing into the generated
  Gradle project.

### Branches

| Branch | Role                                         | What it triggers                                                      |
| ------ | -------------------------------------------- | --------------------------------------------------------------------- |
| `main` | The published line. Open pull requests here. | **CI** + **Release**, which versions, tags and fires **Android APK**. |

Pull requests run **CI** only (lint, typecheck, bundle, prebuild).

### Versioning and deploy

Versioning runs on a script of its own (`scripts/release.js`) with no external
dependencies: on every push to `main` — that is, every merged pull request —
the **Release** workflow reads the conventional commits since the last tag,
bumps the semver, writes the Android `versionCode`
(`major * 1000000 + minor * 1000 + patch`), prepends `CHANGELOG.md` and creates
the annotated `v*` tag. It then fires **Android APK** through
`workflow_dispatch`, which runs `expo prebuild --clean` plus Gradle on the
runner (no EAS) and attaches the signed APK to the GitHub Release.

Only `feat`, `fix`, `perf`, `refactor` and `revert` commits produce a version;
a `!` after the type or a `BREAKING CHANGE` in the body makes it a major. When
squash-merging, give the pull request a conventional title, since that is the
commit the script reads. The first release takes the version already in
`package.json` (`1.0.0`).

### Signing

`signing/temp-release.keystore` is a **throwaway key**, committed on purpose so
the APK comes out signed without anyone configuring secrets (store and key
password `android`, alias `zooming-mirror`). It is enough to install and update
the app on a phone and **is not suitable for the Play Store** — anyone with
access to the repository can sign with it.

To swap in a real key, set the secrets `ANDROID_KEYSTORE_BASE64`,
`ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS` and `ANDROID_KEY_PASSWORD`;
the workflow then uses those and ignores the throwaway one. The signature
changes with it, so an install made with the old key has to be removed first.

## License

Personal use.
