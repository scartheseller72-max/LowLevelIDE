# Termux internals
-keep class com.termux.** { *; }
-dontwarn com.termux.**

# Apache Commons Compress (SPI / reflection)
-keep class org.apache.commons.compress.** { *; }
-dontwarn org.apache.commons.compress.**
-keep class org.tukaani.xz.** { *; }
-dontwarn org.tukaani.xz.**

# OkHttp / Okio
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.conscrypt.**
-dontwarn javax.annotation.**

# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keep,includedescriptorclasses class com.example.lowlevelide.**$$serializer { *; }
-keepclassmembers class com.example.lowlevelide.** {
    *** Companion;
}
-keepclasseswithmembers class com.example.lowlevelide.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Our JS bridge — methods called by name from CodeMirror WebView
-keepclassmembers class com.example.lowlevelide.files.FileInterface {
    public *;
}
-keepattributes JavascriptInterface
