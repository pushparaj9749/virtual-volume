# Virtual Volume keeps no reflection-heavy libraries; R8 rules below cover the
# framework entry points that are instantiated by the Android system.

# Manifest-declared components (Service / TileService / BroadcastReceiver) are kept by the
# aapt-generated rules, these keeps make that explicit and survive config changes.
-keep class dev.virtualvolume.app.overlay.OverlayService { *; }
-keep class dev.virtualvolume.app.tile.VolumeTileService { *; }
-keep class dev.virtualvolume.app.boot.BootReceiver { *; }

# Jetpack Compose keeps its own rules; nothing extra is required.

-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

-dontwarn org.jetbrains.annotations.**
