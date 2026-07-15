# Room resolves the generated implementation from the database class name.
-keepattributes RuntimeVisibleAnnotations,RuntimeInvisibleAnnotations,AnnotationDefault,Signature
-keep class * extends androidx.room.RoomDatabase { *; }

# Keep serializers generated for the archive wire format. Other serializable
# types are reached directly through their generated serializer references.
-keep,includedescriptorclasses class com.imgad.domain.port.**$$serializer { *; }
-keepclassmembers class com.imgad.domain.port.** {
    kotlinx.serialization.KSerializer serializer(...);
}
