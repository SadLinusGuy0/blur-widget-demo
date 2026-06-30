# Integration guides

The detailed implementation notes for Blur Widget Demo now live in the GitHub
Wiki, where the longer XML and Kotlin examples are easier to read and maintain.

- [One UI Blur Integration](https://github.com/thatjoshguy67/blur-widget-demo/wiki/One-UI-Blur-Integration)
  explains how the home-screen wallpaper blur works, including the
  `@android:id/background` requirement, Samsung `widgetStyle` metadata, and
  opacity model.
- [Lock Screen Widgets](https://github.com/thatjoshguy67/blur-widget-demo/wiki/Lock-Screen-Widgets)
  explains the 1x1 and 2x1 Samsung lock-screen widget setup, including
  monotone provider metadata and the optional ServiceBox RemoteViews path.

For the working source, see:

- [`BlurWidget.kt`](../app/src/main/java/com/example/blurwidgetdemo/BlurWidget.kt)
- [`LockScreenMessageWidgets.kt`](../app/src/main/java/com/example/blurwidgetdemo/LockScreenMessageWidgets.kt)
- [`widget_provider_blur.xml`](../app/src/main/res/xml/widget_provider_blur.xml)
- [`lockscreen_message_1x1_widget_info.xml`](../app/src/main/res/xml/lockscreen_message_1x1_widget_info.xml)
- [`lockscreen_message_2x1_widget_info.xml`](../app/src/main/res/xml/lockscreen_message_2x1_widget_info.xml)
- [`attrs.xml`](../app/src/main/res/values/attrs.xml)
