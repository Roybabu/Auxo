# Project-specific R8/ProGuard rules for the Auxo app.
# Release builds are minified with R8 (see app/build.gradle.kts). Add keep rules here for
# libraries that look classes up by name (reflection, serialization).

# SQLCipher loads its native library and calls back into these classes by name.
-keep class net.zetetic.database.** { *; }

# Keep readable crash reports: file names and line numbers survive, and the mapping file turns them back into names.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Tink (used for the Android Keystore backed keys) reads the fields of its protobuf messages by reflection.
-keepclassmembers class * extends com.google.crypto.tink.shaded.protobuf.GeneratedMessageLite {
    <fields>;
}
-dontwarn com.google.errorprone.annotations.**
-dontwarn javax.annotation.**
