# Doble Parsi V1.0.0 - release shrinking rules
# Copyright (c) 2026 QW-AI-Code - https://github.com/QW-AI-Code
#
# R8 is enabled so the single universal APK is not carrying the whole
# material-icons-extended / Compose surface that is never referenced.

# Keep the app itself intact: services, the notification listener and the
# activity are all reached through the manifest / the system.
-keep class com.qwaicode.dobleparsi.** { *; }
-keepclassmembers class com.qwaicode.dobleparsi.** { *; }

# Android entry points referenced only from the manifest.
-keep public class * extends android.app.Service
-keep public class * extends android.service.notification.NotificationListenerService
-keep public class * extends android.app.Application

# OkHttp / Okio ship their own rules; silence the optional platform bits.
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# Kotlin metadata + coroutines internals.
-dontwarn kotlinx.coroutines.**
-keepclassmembers class kotlin.Metadata { *; }

# Compose keeps what it needs through its own consumer rules; nothing extra here.
