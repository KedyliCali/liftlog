# kotlinx.serialization and Retrofit ship their own consumer rules; keep our API DTOs' generated serializers.
-keep,includedescriptorclasses class com.behlul.liftlog.data.remote.dto.**$$serializer { *; }
-keepclassmembers class com.behlul.liftlog.data.remote.dto.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
