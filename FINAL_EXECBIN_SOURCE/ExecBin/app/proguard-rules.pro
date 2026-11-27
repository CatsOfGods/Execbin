# ================================
# PROGUARD RULES - FIREBASE SAFE
# Updated for 2-Step Verification
# ================================

# ================================
# ESSENTIAL ATTRIBUTES
# ================================
-keepattributes Signature
-keepattributes *Annotation*
-keepattributes EnclosingMethod
-keepattributes InnerClasses
-keepattributes Exceptions

# ================================
# ANDROID COMPONENTS
# ================================
-keep public class * extends android.app.Activity
-keep public class * extends android.app.Application
-keep public class * extends android.app.Service
-keep public class * extends android.content.BroadcastReceiver
-keep public class * extends android.content.ContentProvider

# ================================
# FIREBASE & FIRESTORE (EXPANDED)
# ================================
-keep class com.google.firebase.** { *; }
-keep class com.google.android.gms.** { *; }
-dontwarn com.google.firebase.**
-dontwarn com.google.android.gms.**

# Keep Firestore serialization/deserialization
-keep class com.google.firebase.firestore.** { *; }
-keepclassmembers class * {
    @com.google.firebase.firestore.PropertyName <methods>;
    @com.google.firebase.firestore.DocumentId <methods>;
    @com.google.firebase.firestore.ServerTimestamp <methods>;
    @com.google.firebase.firestore.Exclude <methods>;
}

# ================================
# APP DATA CLASSES (Firestore models)
# All data classes that Firestore deserializes
# MUST be kept with no-arg constructors + fields
# ================================
-keep class com.example.execbin.ChatUser { *; }
-keep class com.example.execbin.Msg { *; }
-keep class com.example.execbin.Device { *; }

# Keep Firestore model fields only (not entire package)
-keepclassmembers class com.example.execbin.database.CachedMessage {
    <fields>;
}

# ================================
# FCM SERVICE
# ================================
-keep class com.example.execbin.MyFirebaseMessagingService { *; }

# ================================
# DEVICE UTILS (uses reflection for device info)
# ================================
-keep class com.example.execbin.DeviceUtils { *; }

# ================================
# SECURITY: Keep crypto classes for PIN + E2E encryption
# ================================
-keep class java.security.MessageDigest { *; }
-keep class java.security.** { *; }
-keep class javax.crypto.** { *; }
-keep class javax.crypto.spec.** { *; }
-keep class android.security.keystore.** { *; }
-dontwarn java.security.**
-dontwarn javax.crypto.**

# ================================
# ROOM DATABASE + SQLCIPHER (encrypted)
# ================================
-keep class com.example.execbin.database.** { *; }
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.**
-keep class net.zetetic.** { *; }
-keep class net.zetetic.database.** { *; }
-dontwarn net.zetetic.**

# ================================
# ENCRYPTED SHARED PREFERENCES
# ================================
-keep class androidx.security.crypto.** { *; }
-keep class com.google.crypto.tink.** { *; }
-dontwarn com.google.crypto.tink.**

# ================================
# PLAY INTEGRITY
# ================================
-keep class com.google.android.play.core.integrity.** { *; }
-dontwarn com.google.android.play.core.integrity.**

# ================================
# FIREBASE CRASHLYTICS
# ================================
-keep class com.google.firebase.crashlytics.** { *; }
-dontwarn com.google.firebase.crashlytics.**

# ================================
# ANDROIDX / APPCOMPAT
# ================================
-keep class androidx.appcompat.widget.SwitchCompat { *; }
-keep class androidx.recyclerview.widget.** { *; }
-keep class androidx.swiperefreshlayout.widget.** { *; }
-keep class androidx.core.view.** { *; }
-dontwarn androidx.**

# ================================
# STRIP DEBUG LOGS (release builds only)
# ================================
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
    public static *** i(...);
    public static *** w(...);
}
# NOTE: e() kept for crash diagnostics only

# ================================
# KOTLIN
# ================================
-keep class kotlin.Metadata { *; }
-keep class kotlin.reflect.** { *; }
-keepclassmembers class **$WhenMappings {
    <fields>;
}
-dontwarn kotlin.**
-dontwarn kotlinx.**

# Keep Kotlin data class component functions for Firestore models only
-keepclassmembers class com.example.execbin.ChatUser { public final <methods>; }
-keepclassmembers class com.example.execbin.Msg { public final <methods>; }
-keepclassmembers class com.example.execbin.Device { public final <methods>; }

# ================================
# OBFUSCATION (AGGRESSIVE)
# ================================
-repackageclasses ''
-allowaccessmodification
-flattenpackagehierarchy ''
-overloadaggressively

# Obfuscate E2E encryption functions aggressively
-keep,allowobfuscation class com.example.execbin.MainActivity {
    private *** deriveSharedKey(...);
    private *** encryptMessage(...);
    private *** decryptMessage(...);
}

# Remove all source file names and line info in release
-renamesourcefileattribute ''

# ================================
# OPTIMIZATION (AGGRESSIVE)
# ================================
-optimizationpasses 7
-dontusemixedcaseclassnames

# Aggressive optimizations
-optimizations !code/simplification/arithmetic,!code/simplification/cast,!field/*,!class/merging/*

# Remove unused code paths
-assumenosideeffects class java.lang.String {
    public static java.lang.String valueOf(boolean);
    public static java.lang.String valueOf(char);
    public static java.lang.String valueOf(int);
    public static java.lang.String valueOf(long);
    public static java.lang.String valueOf(float);
    public static java.lang.String valueOf(double);
}

# Remove Kotlin intrinsics checks in release
-assumenosideeffects class kotlin.jvm.internal.Intrinsics {
    public static void checkNotNullParameter(...);
    public static void checkNotNullExpressionValue(...);
    public static void checkExpressionValueIsNotNull(...);
    public static void checkNotNull(...);
    public static void checkParameterIsNotNull(...);
    public static void checkReturnedValueIsNotNull(...);
    public static void throwUninitializedPropertyAccessException(...);
}

# ================================
# STANDARD RULES
# ================================
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

-keep class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator *;
}

-keepclasseswithmembernames class * {
    native <methods>;
}

# Keep R classes
-keepclassmembers class **.R$* {
    public static <fields>;
}

# ================================
# SUPPRESS WARNINGS
# ================================
-dontwarn com.google.errorprone.annotations.**
-dontwarn javax.annotation.**
-dontwarn org.checkerframework.**
-dontwarn org.codehaus.mojo.animal_sniffer.**
-dontwarn sun.misc.Unsafe