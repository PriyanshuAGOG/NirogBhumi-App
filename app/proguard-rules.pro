# ProGuard/R8 rules for the release build (isMinifyEnabled + isShrinkResources).
#
# Scope note: this app deliberately does NOT use Firestore POJO mapping
# (toObject<T>()/@PropertyName), Gson/Moshi, or kotlinx.serialization - every
# Firestore read goes through map-based access (CloudDocument / values["field"]),
# so there are no reflectively-instantiated model classes that R8 could strip.
# The Firebase, AndroidX, Compose, Coroutines, Glance and ZXing dependencies all
# ship their own consumer ProGuard rules inside their AARs, which R8 applies
# automatically, so they don't need re-declaring here. What remains is the small
# set of project-wide attributes R8 would otherwise drop.

# Readable crash reports. Without these, every Crashlytics stack trace from a
# release build is fully obfuscated (a.b.c(SourceFile)) and effectively useless
# for triage. Keeping the line table + source file name, then renaming the
# source file to a constant, gives deobfuscatable traces (via the uploaded
# mapping.txt) without leaking original file names into the shipped APK.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Preserve annotations, generic signatures, and exception tables - several
# libraries (Firebase, Play Integrity, Health Connect, coroutines) read these
# reflectively, and dropping them can turn a working debug build into a release
# build that fails at runtime with no compile-time warning.
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod,Exceptions

# Kotlin coroutines: the coroutines AAR ships most of what R8 needs, but guard
# the service-loader / main-dispatcher edges that have historically been
# stripped under R8 full mode.
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-dontwarn kotlinx.coroutines.**
