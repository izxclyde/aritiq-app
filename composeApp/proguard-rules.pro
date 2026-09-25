# Aritiq R8/ProGuard rules.
# AGP + Compose + kotlinx.serialization ship most of what we need; this file only covers the parts
# R8 cannot see: reflective/annotation-driven code and the SQLDelight generated interface.

# --- kotlinx.serialization -------------------------------------------------
# Serializers are generated as `Companion.serializer()` and looked up reflectively by the
# polymorphic / `decodeFromString` paths.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class com.aritiq.calcnote.data.export.** {
    *** Companion;
}
-keepclasseswithmembers class com.aritiq.calcnote.data.export.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.aritiq.calcnote.data.export.**$$serializer { *; }

# --- SQLDelight ------------------------------------------------------------
# Generated queries/adapters are instantiated via generated `Database.Schema` references.
-keep class com.aritiq.calcnote.data.db.** { *; }

# --- Koin ------------------------------------------------------------------
# Modules/definitions are bound by lambda, and `koinInject<VM>()` resolves by generic type token.
-keep class org.koin.** { *; }
-keep class * extends org.koin.core.module.Module { *; }

# --- Enum values used as DB / preference keys -----------------------------
# NoteProcessor + settings persist ViewMode/SortOrder/ThemeMode names as strings.
-keepclassmembers enum com.aritiq.calcnote.** {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# Keep line numbers so crash reports stay readable, but hide the original file name.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
