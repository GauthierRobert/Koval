# Moshi
-keep class com.koval.trainingplanner.data.remote.dto.** { *; }
-keep @com.squareup.moshi.JsonClass class * { *; }
-keepclassmembers class * { @com.squareup.moshi.Json <fields>; }

# Retrofit
-keep,allowobfuscation interface * { @retrofit2.http.* <methods>; }

# OkHttp
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**

# Firebase
-keep class com.google.firebase.** { *; }
