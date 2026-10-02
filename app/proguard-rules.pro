# kotlinx.serialization keeps its generated serializers via these members; without
# them R8 strips the companions that NavKey lookups rely on at runtime.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**

-keepclassmembers class com.example.arrowpuzzle.** {
    *** Companion;
}
-keepclasseswithmembers class com.example.arrowpuzzle.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Navigation 3 resolves entries by key type at runtime.
-keep class com.example.arrowpuzzle.*Key { *; }
-keep class com.example.arrowpuzzle.*Key$* { *; }
