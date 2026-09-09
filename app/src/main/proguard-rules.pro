# R8 rules for File-Manager-Pro (1.0.0 hardening, #22).
#
# Everything here is additive: the AndroidX/Material libraries ship their own
# consumer rules; these cover the non-AndroidX stack (commons-compress, xz,
# junrar + its SLF4J bridge) and keep useful metadata for crash reports.

# Keep line numbers so release stack traces map back to source lines.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# SLF4J 1.x finds its binder by reflective class lookup
# (org.slf4j.impl.StaticLoggerBinder), which R8 cannot see statically.
# junrar logs through slf4j-api; slf4j-nop supplies the no-op binder.
-keep class org.slf4j.impl.** { *; }
-dontwarn org.slf4j.**

# junrar (RAR4 reader) — direct API usage, but keep entry points defensive.
-keep class com.github.junrar.** { *; }

# Optional codecs referenced by commons-compress that are not bundled on
# Android (zstd/brotli/lz4-java are loaded lazily and never reachable here).
-dontwarn com.github.luben.**
-dontwarn org.brotli.**
-dontwarn com.aayushatharva.**
-dontwarn org.apache.commons.compress.compressors.**
-dontwarn org.apache.commons.compress.archivers.sevenz.**
