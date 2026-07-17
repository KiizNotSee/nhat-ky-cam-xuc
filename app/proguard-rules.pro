# Keep WebView JS interface
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}
# Keep Kotlin
-keep class kotlin.** { *; }
