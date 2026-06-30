# Blur Widget Demo

[![Android CI](https://github.com/SadLinusGuy0/blur-widget-demo/actions/workflows/android.yml/badge.svg)](https://github.com/SadLinusGuy0/blur-widget-demo/actions/workflows/android.yml)
[![Latest release](https://img.shields.io/github/v/release/SadLinusGuy0/blur-widget-demo)](https://github.com/SadLinusGuy0/blur-widget-demo/releases/latest)
[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)

A working proof-of-concept showing that **third-party Android widgets can use One UI Home's native wallpaper blur** — the frosted-glass effect behind
Samsung's own Weather, Clock, and Calendar widgets. Samsung doesn't document
this, but it works on any One UI 7.0+ device.

The app ships a resizable home-screen widget whose **tint and opacity you can
tune live** through a configuration screen, plus tiny lock-screen message
widgets that show the Samsung lock/AOD metadata pattern.

> **Just want the technique?** See the wiki guides for
> [One UI blur integration](https://github.com/thatjoshguy67/blur-widget-demo/wiki/One-UI-Blur-Integration)
> and [lock-screen widgets](https://github.com/thatjoshguy67/blur-widget-demo/wiki/Lock-Screen-Widgets).

## Download

Grab a signed APK from the [latest release](https://github.com/SadLinusGuy0/blur-widget-demo/releases/latest)
and sideload it on a Samsung One UI 7.0+ device. Releases are cut automatically
when a `v*` tag is pushed (see [Releases](#releases) below).

## How the blur works (in one paragraph)

The widget never renders a blur itself. It declares a couple of Samsung-specific
attributes and paints a **semi-transparent background** on a view tagged
`@android:id/background`. The launcher detects that, captures the wallpaper behind
the widget, blurs it, and draws it underneath — your background colour tints the
result. Three things must be true (root `@android:id/background` view, an alpha
between 1–254, and `app:widgetStyle="colorful"` + a real `app:widgetSize`). The
[One UI blur integration wiki guide](https://github.com/thatjoshguy67/blur-widget-demo/wiki/One-UI-Blur-Integration)
covers each one.

## Building

This is a standard Gradle/Android Studio project (AGP 8.9, Kotlin 2.2, JDK 17,
`minSdk` 26).

### One-time setup: GitHub Packages auth

The UI is built with [`tribalfs/oneui-design`](https://github.com/tribalfs/oneui-design),
which is published to **GitHub Packages**. GitHub Packages requires
authentication even for public packages, so you need a token before the build can
resolve dependencies:

1. Create a [Personal Access Token](https://github.com/settings/tokens) with the
   single scope **`read:packages`**.
2. Copy the template and fill in your details:
   ```bash
   cp github.properties.example github.properties
   ```
   ```properties
   ghUsername=your-github-username
   ghAccessToken=ghp_your_read_packages_token
   ```

`github.properties` is gitignored — your token never gets committed.

### Build and install

```bash
./gradlew assembleDebug                 # build the debug APK
./gradlew installDebug                  # build + install on a connected device
```

Then open the app, tap **Add widget**, place it on a Samsung home screen, and use
the configuration screen to adjust tint and opacity.

## Lock screen widgets

The demo also includes two Samsung lock-screen widgets:

- **Blur Message 1x1** — a tiny centered message.
- **Blur Message 2x1** — the same randomized message treatment in a wider slot.

On a Samsung device, open the lock screen editor, choose **Widgets**, then add
one of the Blur Message widgets. Each refresh chooses a short message such as
`Glass`, `Calm`, or `Breathe`.

For your own app, the important pieces are:

- A separate `AppWidgetProvider` receiver per lock-screen size.
- `android:widgetCategory="0x2000"` in each lock-screen provider XML.
- Samsung monotone metadata via `samsung.appwidget.monotone.info`.
- `app:targetHost="lock_and_aod|cover"` and the matching `tiny`, `small`, or
  `medium` size attributes.
- A `REQUEST_SERVICEBOX_REMOTEVIEWS` receiver if you want to support Samsung's
  SystemUI/ServiceBox RemoteViews request path.

The [Lock Screen Widgets wiki guide](https://github.com/thatjoshguy67/blur-widget-demo/wiki/Lock-Screen-Widgets)
has the full setup.

## Continuous integration

[`.github/workflows/android.yml`](.github/workflows/android.yml) builds the debug
APK and runs lint on every push and pull request to `main`. Because CI also needs
to resolve the GitHub Packages dependency, add two repository secrets
(**Settings → Secrets and variables → Actions**):

| Secret              | Value                                            |
|---------------------|--------------------------------------------------|
| `GH_PACKAGES_USER`  | A GitHub username                                |
| `GH_PACKAGES_TOKEN` | A token with `read:packages` scope               |

`settings.gradle.kts` reads credentials from `github.properties` locally and falls
back to these environment variables on CI.

## Releases

[`.github/workflows/release.yml`](.github/workflows/release.yml) builds a **signed
release APK** and attaches it to a GitHub Release whenever a `v*` tag is pushed:

```bash
git tag v1.0.0
git push origin v1.0.0
```

You can also trigger it manually from the **Actions → Release** tab (provide the
tag to publish). Signing uses a dedicated keystore stored in repository secrets:

| Secret                    | Value                                              |
|---------------------------|----------------------------------------------------|
| `RELEASE_KEYSTORE_BASE64` | The release keystore, base64-encoded               |
| `RELEASE_STORE_PASSWORD`  | Keystore password                                  |
| `RELEASE_KEY_PASSWORD`    | Key password (same as store password for PKCS12)   |
| `RELEASE_KEY_ALIAS`       | Key alias                                           |

To build a signed release locally, copy your keystore details into a gitignored
`keystore.properties` (`storeFile`, `storePassword`, `keyAlias`, `keyPassword`)
and run `./gradlew assembleRelease`. Without it, release builds are left unsigned.

## Project structure

```
app/src/main/
├── java/com/example/blurwidgetdemo/
│   ├── BlurWidget.kt              # AppWidgetProvider — picks layout, applies tint
│   ├── LockScreenMessageWidgets.kt # 1x1/2x1 Samsung lock-screen widgets
│   ├── WidgetConfigActivity.kt    # One UI tint/opacity config + live preview
│   ├── MainActivity.kt            # Onboarding + "Add widget" entry point
│   └── AboutActivity.kt           # About / credits screen
├── res/
│   ├── layout/widget_blur.xml     # Widget layout with @android:id/background
│   ├── layout/lockscreen_message_*.xml # Lock-screen message RemoteViews layouts
│   ├── xml/widget_provider_blur.xml  # Provider: widgetStyle + widgetSize + previews
│   ├── xml/lockscreen_message_*_widget_info.xml # Lock-screen provider metadata
│   └── values/attrs.xml           # Samsung custom widget attribute definitions
└── AndroidManifest.xml
docs/INTEGRATION.md                # Links to the full GitHub Wiki guides
```

## Credits

- [oneui-design / oneui-core](https://github.com/tribalfs/oneui-design) and the
  [One UI Project](https://github.com/OneUIProject) — One UI components, icons,
  and the colour picker. (MIT)

## License

[MIT](LICENSE) © Josh Skinner ([thatjoshguy](https://tjg.gg))
