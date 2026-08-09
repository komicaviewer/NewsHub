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

# AIDL descriptors and callback stubs cross the isolated-process Binder boundary.
-keep class tw.kevinzhang.extension_api.** { *; }
-keep interface tw.kevinzhang.extension_api.** { *; }

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
