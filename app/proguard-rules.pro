# -----------------------------------------------------------------------
# Gson
# -----------------------------------------------------------------------

# Required for TypeToken generic type resolution at runtime (used in SourceCookieJar)
-keepattributes Signature
-keepattributes *Annotation*

# Keep Gson's own internals
-keep class com.google.gson.reflect.TypeToken { *; }
-keep class * extends com.google.gson.reflect.TypeToken

# Marketplace models deserialized by Gson through reflection. Keep their class
# identities, fields, and constructors stable for the current repo formats.
-keep class tw.kevinzhang.marketplace.data.RepoMetadata { *; }
-keep class tw.kevinzhang.marketplace.data.RemoteExtensionDto { *; }
-keep class tw.kevinzhang.marketplace.data.RemoteSourceDto { *; }

# SerializableCookie — serialized to/from SharedPreferences with TypeToken<List<...>>
-keepclassmembers class tw.kevinzhang.newshub.auth.SourceCookieJar$StoredCookie { *; }

# Extension API is loaded across APK/class-loader boundaries. Keep its JVM ABI,
# including Kotlin compatibility default-method bridges, stable for extensions.
-keep class tw.kevinzhang.extension_api.** { *; }
-keep interface tw.kevinzhang.extension_api.** { *; }
-keep class tw.kevinzhang.extension_api.**$DefaultImpls { *; }

# Extensions are non-minified APKs loaded through PathClassLoader's parent-first
# delegation. Their references to the host-provided Kotlin, coroutines, OkHttp,
# and Okio runtime must therefore retain both JVM class and member names.
# Optimisation remains enabled; only ABI-changing shrink/obfuscation is blocked.
-keep,allowoptimization class kotlin.** { *; }
-keep,allowoptimization class kotlinx.coroutines.** { *; }
-keep,allowoptimization class okhttp3.** { *; }
-keep,allowoptimization class okio.** { *; }

# CommentRes — deserialized from Gamer API JSON response
-keepclassmembers class tw.kevinzhang.gamer_api.parser.CommentListParser$CommentRes { *; }

# -----------------------------------------------------------------------
# OkHttp: suppress warnings for optional platform-specific TLS providers
# (BouncyCastle, Conscrypt, OpenJSSE are not present on Android)
# -----------------------------------------------------------------------
-dontwarn org.bouncycastle.jsse.**
-dontwarn org.conscrypt.**
-dontwarn org.openjsse.**

# -----------------------------------------------------------------------
# Debugging: preserve stack trace line numbers in release builds
# -----------------------------------------------------------------------
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
