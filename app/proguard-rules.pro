# Project specific ProGuard rules for ZEN

# Preserve line numbers and source attributes for debugging
-keepattributes SourceFile,LineNumberTable
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod

# ===================================================================
# Room Persistence Library Keep Rules
# ===================================================================
-keep class * extends androidx.room.RoomDatabase
-keepclassmembers class * extends androidx.room.RoomDatabase {
    public static abstract <methods>;
}
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao class * { *; }
-keep class * extends androidx.room.RoomDatabase_Impl
-keep class androidx.room.** { *; }
-dontwarn androidx.room.paging.**

# Keep all database DAO and Database classes
-keep class com.example.database.** { *; }

# ===================================================================
# Data Models & POJOs
# ===================================================================
-keep class com.example.model.** { *; }
-keepclassmembers class com.example.model.** {
    @com.google.gson.annotations.SerializedName <fields>;
    <fields>;
    <methods>;
}

# ===================================================================
# Gson Serialization Rules
# ===================================================================
-keep class com.google.gson.** { *; }
-keep class sun.misc.Unsafe { *; }
-keep class * implements com.google.gson.TypeAdapter
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer

# ===================================================================
# Material Design & AndroidX
# ===================================================================
-keep class com.google.android.material.** { *; }
-dontwarn com.google.android.material.**

