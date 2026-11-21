-keep class hime.mp.core.NativeBridge { *; }
-keep class hime.mp.core.NativeSinkFactory { *; }
-keep class hime.mp.core.NativeBridge$EventListener { *; }
-keepclasseswithmembernames class * {
    native <methods>;
}
-keep class hime.mp.data.model.** { *; }
-dontwarn com.google.errorprone.annotations.**
