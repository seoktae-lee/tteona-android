# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.seoktaedev.tteona.**$$serializer { *; }
-keepclassmembers class com.seoktaedev.tteona.** {
    *** Companion;
}
-keepclasseswithmembers class com.seoktaedev.tteona.** {
    kotlinx.serialization.KSerializer serializer(...);
}
