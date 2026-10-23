# ============================================================
# PDF Helper — ProGuard/R8 rules
# Kept minimal but sufficient for PDFBox-Android + Compose.
# ============================================================

# --- Keep line numbers for readable crash stacks in releases ---
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# --- Kotlin metadata (needed by reflection-heavy libs) ---
-keepattributes RuntimeVisibleAnnotations,RuntimeVisibleParameterAnnotations
-keepattributes Signature,InnerClasses,EnclosingMethod,*Annotation*

# ============================================================
# PDFBox-Android (com.tom-roush:pdfbox-android)
# PDFBox uses reflection for COS types, fonts, and loggers.
# ============================================================
-keep class com.tom_roush.pdfbox.** { *; }
-keep class com.tom_roush.fontbox.** { *; }
-keep class com.tom_roush.harmony.** { *; }
-dontwarn com.tom_roush.**

# Apache Commons Logging bridge used internally by PDFBox
-dontwarn org.apache.commons.logging.**
-keep class org.apache.commons.logging.** { *; }

# BouncyCastle (PDFBox crypto for password-protect)
-keep class org.bouncycastle.** { *; }
-dontwarn org.bouncycastle.**

# ============================================================
# AndroidX Compose (mostly covered by consumer rules; keep the bits R8
# sometimes misses when reflection is involved in previews/tooling)
# ============================================================
-keep class androidx.compose.runtime.** { *; }
-dontwarn androidx.compose.**

# ============================================================
# Kotlin Coroutines
# ============================================================
-keepclassmembernames class kotlinx.** {
    volatile <fields>;
}
-dontwarn kotlinx.coroutines.**

# ============================================================
# Kotlin reflection & serialization-adjacent
# ============================================================
-keep class kotlin.Metadata { *; }
-keep class kotlin.reflect.** { *; }
-dontwarn kotlin.reflect.**

# ============================================================
# Data classes / serialized models (safe default — keeps constructors)
# ============================================================
-keepclassmembers class xyz.ximanta.pdfhelper.** {
    <init>(...);
}

# ============================================================
# Suppress harmless warnings from transitive deps
# ============================================================
-dontwarn javax.annotation.**
-dontwarn java.awt.**
-dontwarn javax.imageio.**
-dontwarn javax.swing.**
-dontwarn javax.media.jai.**
-dontwarn org.w3c.dom.**
-dontwarn org.xml.sax.**
