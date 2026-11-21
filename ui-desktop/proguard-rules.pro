# ── General JVM / Kotlin attributes ──────────────────────────────────────────
-keepattributes *Annotation*, Signature, InnerClasses, EnclosingMethod, Exceptions, SourceFile, LineNumberTable, RuntimeVisibleAnnotations, RuntimeInvisibleAnnotations, RuntimeVisibleParameterAnnotations, RuntimeVisibleTypeAnnotations
-keep class kotlin.Metadata { *; }

# Keep native methods across all libraries (JNI: Skia/Skiko, Javet V8, Nucleus Tao, FileKit, JNA)
-keepclasseswithmembernames,includedescriptorclasses class * {
    native <methods>;
}

# Keep main entry point
-keep class io.github.makeforge.clapi.ui.desktop.MainKt {
    public static void main(java.lang.String[]);
}

# ── Kotlinx Serialization & KAML ─────────────────────────────────────────────
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class io.github.makeforge.clapi.**$$serializer { *; }
-keepclassmembers class io.github.makeforge.clapi.** {
    *** Companion;
}
-keepclasseswithmembers class io.github.makeforge.clapi.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep @kotlinx.serialization.Serializable class * { *; }
-keep class com.charleskorn.kaml.** { *; }

# ── Kotlinx Coroutines ───────────────────────────────────────────────────────
# Keep Job/JobSupport hierarchy intact so ProGuard does not strip direct
# superinterfaces referenced by invokespecial default interface methods.
-keep class kotlinx.coroutines.** { *; }
-keep interface kotlinx.coroutines.** { *; }

# ── Compose Multiplatform, Skiko & Nucleus Framework ─────────────────────────
-keep class dev.nucleusframework.** { *; }
-keep interface dev.nucleusframework.** { *; }
-keep class org.jetbrains.skiko.** { *; }
-keep class org.jetbrains.skia.** { *; }
-keep class androidx.compose.ui.res.** { *; }
-keep class io.github.makeforge.clapi.ui.shared.generated.resources.** { *; }

# ── FileKit (native file dialogs) ────────────────────────────────────────────
-keep class io.github.vinceglb.filekit.** { *; }

# ── Logging: Tinylog + SLF4J + kotlin-logging ────────────────────────────────
-keep class org.tinylog.** { *; }
-keep interface org.tinylog.** { *; }
-keep class org.slf4j.** { *; }
-keep class io.github.oshai.kotlinlogging.** { *; }
-keep class io.github.makeforge.clapi.ui.desktop.logging.UiDiagnosticsLogWriter { *; }
-keep class io.github.makeforge.clapi.ui.desktop.logging.ClapiRollingFileWriter { *; }

# ── Javet (V8 JS engine via JNI & reflection) ────────────────────────────────
-keep class com.caoccao.javet.** { *; }
-keep interface com.caoccao.javet.** { *; }

# ── Ktor (CIO client & embedded Netty MCP server) ────────────────────────────
-keep class io.ktor.** { *; }
-keep interface io.ktor.** { *; }
-keep class io.netty.** { *; }
-keep interface io.netty.** { *; }

# ── gRPC, Protobuf, OkHttp, protoc-jar ───────────────────────────────────────
-keep class io.grpc.** { *; }
-keep interface io.grpc.** { *; }
-keep class com.google.protobuf.** { *; }
-keep class com.github.os72.protocjar.** { *; }
-keep class com.squareup.okhttp.** { *; }
-keep class okhttp3.** { *; }
-keep class okio.** { *; }

# ── OpenAPI / Swagger Parser, Jackson 2 & 3, NetworkNT JSON Schema ───────────
-keep class io.swagger.v3.** { *; }
-keep class com.fasterxml.jackson.** { *; }
-keep class tools.jackson.** { *; }
-keep class com.networknt.schema.** { *; }
-keep class org.yaml.snakeyaml.** { *; }

# ── Secrets & Security (KSafe, KeePassXC, Keeper, BouncyCastle, JNA) ─────────
-keep class eu.anifantakis.lib.ksafe.** { *; }
-keep class org.purejava.keepassxc.** { *; }
-keep class com.keepersecurity.secretsManager.** { *; }
-keep class org.bouncycastle.** { *; }
-keep class com.sun.jna.** { *; }
-keep interface com.sun.jna.** { *; }
-keep class * extends com.sun.jna.Structure { *; }
-keep class * implements com.sun.jna.Library { *; }
-keep class org.json.** { *; }

# ── Koin ─────────────────────────────────────────────────────────────────────
-keep class org.koin.** { *; }

# ── Optional/compile-only references in third-party JARs ─────────────────────
-dontwarn kotlin.**
-dontwarn kotlinx.**
-dontwarn org.jetbrains.**
-dontwarn androidx.**
-dontwarn dev.nucleusframework.**
-dontwarn io.ktor.**
-dontwarn io.netty.**
-dontwarn io.grpc.**
-dontwarn com.google.**
-dontwarn com.fasterxml.jackson.**
-dontwarn tools.jackson.**
-dontwarn com.networknt.**
-dontwarn io.swagger.**
-dontwarn org.yaml.snakeyaml.**
-dontwarn com.caoccao.javet.**
-dontwarn org.tinylog.**
-dontwarn org.slf4j.**
-dontwarn org.bouncycastle.**
-dontwarn com.sun.jna.**
-dontwarn org.purejava.**
-dontwarn com.keepersecurity.**
-dontwarn com.squareup.**
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn io.github.vinceglb.**
-dontwarn eu.anifantakis.**
-dontwarn sun.misc.Unsafe
-dontwarn java.lang.invoke.**
-dontwarn javax.**
-dontwarn org.apache.**
-dontwarn org.codehaus.**
-dontwarn org.conscrypt.**
-dontwarn org.openjsse.**
-dontwarn org.eclipse.**
-dontwarn org.ietf.**
-dontwarn org.w3c.**
-dontwarn org.xml.**
-dontwarn com.oracle.**
-dontwarn com.ibm.**
-dontwarn android.**
-dontwarn dalvik.**
-dontwarn net.bytebuddy.**
-dontwarn jnr.**
-dontwarn com.jcraft.**
-dontwarn com.aayushatharva.**
-dontwarn com.ning.**
-dontwarn com.barchart.**
-dontwarn com.jessecorbett.**
-dontwarn com.github.luben.**
-dontwarn com.github.marschall.**
-dontwarn lzma.**
-dontwarn org.tukaani.**
-dontwarn org.brotli.**
-dontwarn reactor.**
-dontwarn rx.**
-dontwarn io.reactivex.**
-dontwarn com.typesafe.**
-dontwarn ch.qos.logback.**
-dontwarn org.apache.logging.**
-dontwarn org.jboss.**
-dontwarn com.google.errorprone.**
-dontwarn org.checkerframework.**
-dontwarn org.jspecify.**
-dontwarn javax.annotation.**
-dontwarn org.codehaus.mojo.animal_sniffer.**
