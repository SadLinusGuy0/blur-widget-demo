# Adding One UI wallpaper blur to your own widgets

Third-party Android widgets **can** hook into Samsung One UI Home's native
wallpaper blur — the same frosted-glass effect used by Samsung's own Weather,
Clock, and Calendar widgets. Samsung doesn't document this, but it works on any
One UI 5+ device.

The widget itself never renders a blur. It just declares the right metadata and
paints a **semi-transparent background**; the launcher detects that, captures the
wallpaper region behind the widget, blurs it, and draws it underneath. Your
background colour then tints the result.

This guide walks through the three requirements and the opacity model. For a
working reference, see [`BlurWidget.kt`](../app/src/main/java/com/example/blurwidgetdemo/BlurWidget.kt),
[`widget_blur.xml`](../app/src/main/res/layout/widget_blur.xml),
[`widget_provider_blur.xml`](../app/src/main/res/xml/widget_provider_blur.xml), and
[`attrs.xml`](../app/src/main/res/values/attrs.xml).

---

## The three requirements

### 1. The root view must have `android:id="@android:id/background"`

This is the critical piece. One UI Home's launcher walks each widget's view
hierarchy (`BackgroundViewKt.findBackgroundView()`) looking for a view with the
framework ID `@android:id/background` (`0x01020000`). If it isn't found, blur is
never applied.

Once found, the launcher reads the alpha channel of that view's background
(`ColorDrawable` or `GradientDrawable`). **The alpha must be between 1 and 254** —
fully transparent (0) or fully opaque (255) both disable blur.

```xml
<!-- res/layout/widget_layout.xml -->
<FrameLayout
    xmlns:android="http://schemas.android.com/apk/res/android"
    android:id="@android:id/background"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:background="#80FFFFFF">  <!-- alpha 128 = semi-transparent white -->

    <!-- your widget content here -->

</FrameLayout>
```

### 2. `app:widgetStyle="colorful"` in the appwidget-provider XML

The launcher checks for a "colorful" widget-style flag (`0x1`). Without it the
widget is treated as legacy/monotone and blur is skipped.

Define the Samsung attributes once in `res/values/attrs.xml`:

```xml
<resources>
    <attr name="widgetStyle" format="integer">
        <flag name="colorful" value="0x1" />
        <flag name="monotone" value="0x2" />
    </attr>

    <attr name="widgetSize" format="integer">
        <flag name="small" value="0x1" />
        <flag name="wideSmall" value="0x2" />
        <flag name="medium" value="0x4" />
        <flag name="large" value="0x8" />
        <flag name="extraLarge" value="0x10" />
        <flag name="extraLargeLong" value="0x20" />
    </attr>
</resources>
```

Then reference them from `res/xml/widget_provider.xml` using the `app:` namespace:

```xml
<appwidget-provider
    xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:initialLayout="@layout/widget_layout"
    ...
    app:widgetStyle="colorful"
    app:widgetSize="small|medium|large" />
```

### 3. `app:widgetSize` must resolve to a non-`Unknown` value

The launcher reads `widgetSize` to decide sizing behaviour. If it resolves to
`Unknown` (the default when omitted), `getShouldApplyWidgetBackground()` returns
false and blur is skipped. Set it to whichever sizes your widget supports.

---

## What the launcher does

When all three conditions are met, One UI Home:

1. Captures the wallpaper region behind the widget (`WidgetBlurManager.getCroppedScreenShot()`).
2. Applies a Gaussian blur to that crop.
3. Draws the blurred wallpaper behind your widget's view.
4. Your widget's semi-transparent background tints the blur.

---

## Controlling opacity

The background alpha directly controls the blur appearance — lower alpha means
more transparency and more visible blur:

| Alpha | Hex         | Effect                                            |
|-------|-------------|---------------------------------------------------|
| ~38   | `#26FFFFFF` | Glass — highly transparent, strong blur visible   |
| ~102  | `#66FFFFFF` | Light — moderate transparency                     |
| ~178  | `#B2FFFFFF` | Medium — mostly opaque with subtle blur           |
| ~240  | `#F0FFFFFF` | Solid — nearly opaque, minimal blur               |

Set the background colour at runtime via `RemoteViews`:

```kotlin
val alpha = 102 // light opacity, must stay in 1..254
val bgColor = Color.argb(alpha, 255, 255, 255) // white tint (use 0,0,0 for dark)
views.setInt(android.R.id.background, "setBackgroundColor", bgColor)
```

This repo stores per-widget hue/saturation/value/alpha in `SharedPreferences` and
rebuilds the colour with `Color.HSVToColor` — see `BlurWidget.tintColor()`.

---

## Bonus: featured-widget labels

One UI Home can show a label below "featured" widgets (like Samsung's own). Add a
`featuredWidget` attribute and reference it from the provider XML:

```xml
<!-- attrs.xml -->
<attr name="featuredWidget" format="integer">
    <flag name="small" value="0x2" />
    <flag name="medium" value="0x8" />
    <flag name="large" value="0x10" />
</attr>
```

```xml
<!-- widget_provider.xml -->
app:featuredWidget="small|medium|large"
```

The label text comes from the receiver's `android:label` in the manifest. Users
can toggle labels under **Featured widgets** in One UI Home settings.

---

## Checklist

- [ ] Root widget view has `android:id="@android:id/background"`
- [ ] Root view background is a colour with alpha between 1 and 254
- [ ] `attrs.xml` defines `widgetStyle` and `widgetSize`
- [ ] `appwidget-provider` XML sets `app:widgetStyle="colorful"`
- [ ] `appwidget-provider` XML sets `app:widgetSize` to at least one size
- [ ] Device is running Samsung One UI 5 or later

## Tested on

- Samsung Galaxy, One UI 7 / Android 15, One UI Home launcher
