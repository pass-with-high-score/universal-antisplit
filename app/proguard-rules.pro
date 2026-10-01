# Universal Anti-Split Proguard / R8 Rules

# JNI & Native methods
-keepclasseswithmembernames class * {
    native <methods>;
}
-keep class app.pwhs.universalantisplit.engine.RustAntiSplitBridge { *; }

# Google apksig (APK signing engine)
-keep class com.android.apksig.** { *; }
-dontwarn com.android.apksig.**

# BouncyCastle (Keystore & X.509 cert generation)
-keep class org.bouncycastle.** { *; }
-dontwarn org.bouncycastle.**

# ARSCLib (resources.arsc binary parsing & chunk merging)
-keep class com.reandroid.** { *; }
-dontwarn com.reandroid.**

# Room Database
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**

# Koin Dependency Injection
-keep class * extends io.insert.koin.core.module.Module { *; }
-dontwarn io.insert.koin.**

# Models & Entities
-keep class app.pwhs.universalantisplit.domain.** { *; }
-keep class app.pwhs.universalantisplit.data.db.entity.** { *; }

# Coil
-dontwarn coil.**

# Timber
-dontwarn timber.log.**
