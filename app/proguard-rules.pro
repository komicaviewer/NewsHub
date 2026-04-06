# -----------------------------------------------------------------------
# Gson
# -----------------------------------------------------------------------

# Required for TypeToken generic type resolution at runtime (used in AppCookieJar)
-keepattributes Signature
-keepattributes *Annotation*

# Keep Gson's own internals
-keep class com.google.gson.reflect.TypeToken { *; }
-keep class * extends com.google.gson.reflect.TypeToken

# ExtensionInfo / ExtensionIndex — deserialized from remote index.json
-keepclassmembers class tw.kevinzhang.marketplace.data.ExtensionInfo { *; }
-keepclassmembers class tw.kevinzhang.marketplace.data.ExtensionIndex { *; }

# SerializableCookie — serialized to/from SharedPreferences with TypeToken<List<...>>
-keepclassmembers class tw.kevinzhang.newshub.auth.AppCookieJar$SerializableCookie { *; }

# CommentRes — deserialized from Gamer API JSON response
-keepclassmembers class tw.kevinzhang.gamer_api.parser.CommentListParser$CommentRes { *; }

# -----------------------------------------------------------------------
# Debugging: preserve stack trace line numbers in release builds
# -----------------------------------------------------------------------
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
