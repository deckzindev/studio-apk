# ProGuard / R8 Rules for GitBuilder

# Moshi and Adapters
-keepattributes *Annotation*, Signature, InnerClasses, EnclosingMethod
-keep class com.example.data.** { *; }
-keep class com.example.data.*JsonAdapter { *; }
-keepclassmembers class com.example.data.*JsonAdapter {
    public <init>(com.squareup.moshi.Moshi);
    public <init>();
}
-keepclassmembers class * {
    @com.squareup.moshi.Json <fields>;
    @com.squareup.moshi.JsonClass <fields>;
}

# Room Database
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**
-keep class com.example.data.db.** { *; }

# OkHttp & Retrofit
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }

# Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
