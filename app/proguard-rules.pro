# FFmpegKit
-keep class com.arthenica.ffmpegkit.** { *; }
-dontwarn com.arthenica.ffmpegkit.**

# Room
-keep class androidx.room.** { *; }

# Hilt
-keep class dagger.hilt.** { *; }
-keep class * extends dagger.hilt.android.HiltAndroidApp
-keepclasseswithmembers class * {
    @dagger.hilt.android.AndroidEntryPoint <init>(...);
}

# Media3
-keep class androidx.media3.** { *; }

# Keep data models used by Room / Gson-like reflection
-keep class com.builtdifferent.audio8d.data.db.** { *; }
