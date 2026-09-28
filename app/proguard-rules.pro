# Media3 keeps its own consumer ProGuard rules; nothing app-specific is
# required for reflection here since MediaSessionService callbacks are
# resolved by class name at the framework level.
-keepattributes *Annotation*
-dontwarn org.jetbrains.annotations.**
