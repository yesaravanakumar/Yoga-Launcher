# Yoga Launcher release shrinking rules
# The WebView page calls Kotlin by method name through window.Android.<method>(), so R8 must not rename or remove these.
-keepattributes JavascriptInterface,*Annotation*,Signature,InnerClasses,EnclosingMethod
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}
-keep class com.yoga.launcher.YogaBridge { *; }
# Services / receivers declared in the manifest are kept automatically; keep the names stable for settings screens and logs.
-keep class com.yoga.launcher.BadgeService { *; }
-keep class com.yoga.launcher.LockService { *; }
-keep class com.yoga.launcher.PushReceiver { *; }
# Kotlin metadata is not needed at runtime
-dontwarn kotlin.**
