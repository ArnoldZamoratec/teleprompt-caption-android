# Keep kotlinx.serialization metadata for @Serializable navigation routes and JSON converters.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers @kotlinx.serialization.Serializable class com.arnoldcode.glassprompt.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
