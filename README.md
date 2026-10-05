# Zooming Mirror

**English** · [Português (Brasil)](docs/readme/pt-BR.md)

An Android mirror: the front camera fills the whole screen and you pinch to
zoom in. There is nothing else on screen — no buttons, no status or navigation
bar — except the zoom level, and only while you pinch. Built with
[Expo](https://expo.dev), React Native, TypeScript and a small Kotlin view on
CameraX.

## Using it

- Open the app and allow the camera. The front camera shows up full screen.
- **Pinch out** to zoom in, **pinch in** to zoom back out, up to 10×. The spot
  between your fingers stays put, and moving both fingers pans around.
- **Drag with one finger** to move around the zoomed image.
- While a pinch is changing the zoom, a translucent circle low on the screen
  shows the level with two decimals; it fades out as soon as you let go. A
  one-finger drag never shows it.
- The screen stays on while the app is open.
- Swipe from the edge to bring the system bars back for a moment.

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
for one. The camera keeps streaming at 1× into a `TextureView`, and the pinch
scales and moves that view instead — the same on every phone. A `SurfaceView`,
the faster default, does not follow view transforms, which is why the preview
runs in CameraX's `COMPATIBLE` mode. The stream is requested at 16:9 so a
portrait screen crops as little of the picture as possible.

The camera view is a local native module (`modules/mirror-view`), so it does not
exist in Expo Go. Use the APK from a release or a development build.

| Command             | What it does                                   |
| ------------------- | ---------------------------------------------- |
| `npm run lint`      | ESLint                                         |
| `npm run typecheck` | TypeScript                                     |
| `npm run format`    | Prettier                                       |
| `npm run release`   | Versioning script (`-- --dry-run` to rehearse) |

Planned and possible improvements live in [`docs/plans`](docs/plans) (in
Portuguese), one file per plan.

### Architecture

- `App.tsx` — the root; hides the system bars and renders the mirror.
- `src/Mirror.tsx` — asks for the camera permission and shows the mirror.
- `modules/mirror-view/` — local native module (Kotlin): `MirrorView` binds the
  front camera with CameraX and turns pinches and drags into zoom and pan;
  `PinchZoom` scales and moves the preview, `ZoomIndicator` is the zoom
  circle.
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
