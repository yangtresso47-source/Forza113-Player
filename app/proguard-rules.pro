# StreamVault ProGuard Rules
# ============================================================

# ── Kotlin ──────────────────────────────────────────────────
-dontwarn kotlin.**
-keep class kotlin.Metadata { *; }

# ── Hilt / Dagger ───────────────────────────────────────────
-keepclasseswithmembers class * {
    @dagger.* <methods>;
}
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-keep class * extends dagger.hilt.android.internal.managers.ViewComponentManager$FragmentContextWrapper { *; }

# ── Retrofit / OkHttp ───────────────────────────────────────
-keepattributes Signature
-keepattributes *Annotation*
-keep,allowobfuscation interface retrofit2.Call
-keep,allowobfuscation interface retrofit2.Callback
-dontwarn retrofit2.**
-keep class retrofit2.** { *; }
-keepclassmembers,allowobfuscation class * {
    @retrofit2.http.* <methods>;
}
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# ── GSON ────────────────────────────────────────────────────
-keepattributes Signature
-keepattributes EnclosingMethod
-keep class com.google.gson.** { *; }
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer
# Keep fields that GSON accesses via reflection
-keepclassmembers class com.streamvault.data.remote.** { <fields>; }

# ── Room ────────────────────────────────────────────────────
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-dontwarn androidx.room.paging.**

# ── Domain models (serialized by GSON / passed across modules) ──
-keep class com.streamvault.domain.model.** { *; }
-keep class com.streamvault.data.local.entity.** { *; }
-keep class com.streamvault.data.remote.xtream.model.** { *; }

# ── Media3 / ExoPlayer ─────────────────────────────────────
-keep class androidx.media3.** { *; }
-dontwarn androidx.media3.**

# ── Coroutines ──────────────────────────────────────────────
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}

# ── Compose ─────────────────────────────────────────────────
-dontwarn androidx.compose.**

# ── Coil (image loading) ───────────────────────────────────
-dontwarn coil3.**

# ── DataStore ───────────────────────────────────────────────
-keepclassmembers class * extends androidx.datastore.preferences.protobuf.GeneratedMessageLite {
    <fields>;
}

# ── General ─────────────────────────────────────────────────
-keepattributes SourceFile,LineNumberTable       # Better crash reports
-renamesourcefileattribute SourceFile
-keepattributes RuntimeVisibleAnnotations,RuntimeVisibleParameterAnnotations
