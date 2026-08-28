# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renameSourcefileattribute SourceFile

# Keep Hilt classes
-keep class dagger.hilt.** { *; }
-keep class * extends dagger.hilt.android.internal.managers.ViewComponentManager$FragmentContextWrapper { *; }

# Keep Room classes
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-dontwarn androidx.room.paging.**

# Keep EPUB library classes
-keep class nl.siegmann.epublib.** { *; }
-dontwarn nl.siegmann.epublib.**

# PDFBox can use this optional JPEG2000 decoder when an app supplies it.
-dontwarn com.gemalto.jp2.JP2Decoder

# Keep data classes
-keep class com.huangder.lumibooks.data.** { *; }
-keep class com.huangder.lumibooks.domain.** { *; }
# Detailed reader diagnostics must not build strings on release/benchmark hot paths.
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
}
