# --- General Optimizations ---
# NOTE: Do NOT add -mergeinterfacesaggressively here. It causes VerifyError crashes
# on Android 10 (API 29) by producing bytecode incompatible with the stricter ART
# verifier when used with Jetpack Compose's complex interface hierarchy (ANDROID-190).
-optimizationpasses 1
-allowaccessmodification

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

# --- AmneziaWG Library ---
# DnsSettings and other classes in this library are Java records / used via JNI from the
# native Go backend. Without this rule R8 strips them, causing NoClassDefFoundError at runtime.
-keep class org.amnezia.awg.** { *; }

# --- JNI Interfaces ---
-keep class ru.protonmod.next.vpn.NextConfigGenerator { *; }
-keep class ru.protonmod.next.vpn.IpSubnetCalculatorImpl { *; }
-keep class ru.protonmod.next.vpn.AntiTamperBridge { *; }
-keep class ru.protonmod.next.vpn.SentryBridge { *; }
-keep class ru.protonmod.next.utils.crypto.** { *; }
-keep class ru.protonmod.next.data.network.AuthNativeBridgeImpl { *; }
-keep class ru.protonmod.next.data.network.NativeLoginResult { *; }
-keep class ru.protonmod.next.vpn.NextVpnManager { *; }
-keep class ru.protonmod.next.vpn.NextVpnManager$NativeResponse { *; }
-keep class ru.protonmod.next.vpn.AmneziaVpnManager$ObfuscationParams { *; }
-keep class ru.protonmod.next.utils.ProtonLogger { *; }
-keep class ru.protonmod.next.FlavorInitializer { *; }
-keepclassmembers class ru.protonmod.next.FlavorInitializer {
    @androidx.annotation.Keep <methods>;
    public static void initialize(android.content.Context);
}

# --- Resource Integrity (Anti-Tamper) ---
# Preserve strings and assets used for integrity checks by the native code.
# These are retrieved via getIdentifier or AAssetManager and would otherwise be stripped.
-keepclassmembers class ru.protonmod.next.R$string {
    <fields>;
}

# Preserve assets (fonts, etc.)
-keep class ru.protonmod.next.R$font { *; }
-keep class ru.protonmod.next.R$raw { *; }

-keepclassmembers class ru.protonmod.next.FlavorInitializer {
    @androidx.annotation.Keep <methods>;
    public static void initialize(android.content.Context);
}

# --- Resource Integrity (Anti-Tamper) ---
# Preserve strings used for integrity checks by the native code.
# These are retrieved via getIdentifier and would otherwise be stripped.
-keepclassmembers class ru.protonmod.next.R$string {
    <fields>;
}


# Preserve line numbers for non-obfuscated stack traces (optional, increases size slightly)
#-keepattributes SourceFile,LineNumberTable

# --- WindowManager Extensions (OEM provided) ---
-dontwarn androidx.window.extensions.**
-dontwarn androidx.window.sidecar.**
