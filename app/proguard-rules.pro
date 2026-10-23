# Project-specific R8/ProGuard rules for the Auxo app.
# Release minification is currently disabled in app/build.gradle.kts.
# Add keep rules here when enabling it (for example for serialization or
# reflection-based libraries added in later steps).

# SQLCipher loads its native library and calls back into these classes by name.
-keep class net.zetetic.database.** { *; }
