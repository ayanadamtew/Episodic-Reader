# Episodic Reader (Android v1.1) — ProGuard / R8 Release Rules

# Preserve Room entities, database projections, and generated implementations
-keep class com.episodicreader.data.db.entity.** { *; }
-keep class com.episodicreader.data.db.model.** { *; }
-keep class com.episodicreader.data.db.AppDatabase_Impl { *; }

# Preserve starter pack asset data classes
-keep class com.episodicreader.data.seed.** { *; }

# Keep kotlinx.serialization runtime serializers
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
