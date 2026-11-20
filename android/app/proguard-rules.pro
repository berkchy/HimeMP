-keep class com.berkchy.himemp.core.NativeBridge { *; }
-keep class com.berkchy.himemp.core.NativeSinkFactory { *; }
-keep class com.berkchy.himemp.core.NativeBridge$EventListener { *; }
-keepclasseswithmembernames class * {
    native <methods>;
}
-keep class com.berkchy.himemp.data.model.** { *; }
