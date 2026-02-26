# --- General Optimizations ---
-optimizationpasses 5
-allowaccessmodification
-mergeinterfacesaggressively

# --- Kotlin Serialization ---
-keepattributes *Annotation*, EnclosingMethod, Signature
-keepclassmembernames class * {
    @kotlinx.serialization.SerialName <fields>;
}

# --- Retrofit & OkHttp ---
-keepattributes Signature, RuntimeVisibleAnnotations, RuntimeInvisibleAnnotations, *Annotation*
-dontwarn okhttp3.**
-dontwarn retrofit2.**
-keep class retrofit2.** { *; }
-keepattributes ElementPrecision, *Annotation*, Signature, InnerClasses, EnclosingMethod
-keepclassmembers,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}

# --- Hilt / Dagger ---
-keep class dagger.hilt.android.internal.** { *; }
-dontwarn dagger.hilt.android.processor.**

# --- Room ---
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**

# --- Proton VPN Libs ---
-keep class me.proton.vpn.** { *; }
-keep class ru.protonmod.next.data.network.** { *; }

# Preserve line numbers for non-obfuscated stack traces (optional, increases size slightly)
#-keepattributes SourceFile,LineNumberTable
