
# ── nodx release rules ──────────────────────────────────────────────
# Readable crash stacks after obfuscation.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# OkHttp's optional TLS providers — referenced reflectively, absent at
# runtime on Android; silence the missing-class warnings.
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjdk.**
