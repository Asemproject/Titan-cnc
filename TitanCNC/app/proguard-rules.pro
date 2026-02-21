# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.kts.

# Keep Room entities
-keep class com.titancnc.data.model.** { *; }
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**

# Keep Hilt
-keep class * extends dagger.hilt.android.HiltAndroidApp
-keep class * extends android.app.Application

# Keep OpenCV
-keep class org.opencv.** { *; }
-dontwarn org.opencv.**

# Keep USB Serial
-keep class com.hoho.android.usbserial.** { *; }
-dontwarn com.hoho.android.usbserial.**

# Keep OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**

# Keep Gson
-keepattributes Signature
-keepattributes *Annotation*
-dontwarn sun.misc.**
-keep class com.google.gson.** { *; }

# Keep Compose
-keep class androidx.compose.** { *; }
-dontwarn androidx.compose.**

# General Android
-keepattributes SourceFile,LineNumberTable
-keepattributes *Annotation*
-keepattributes EnclosingMethod
-keepattributes InnerClasses
-keepattributes Signature
-keepattributes Exceptions

# Remove logging in release
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
    public static *** i(...);
    public static *** w(...);
    public static *** e(...);
}
