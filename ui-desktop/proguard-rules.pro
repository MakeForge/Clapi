# Tinylog reflection and service loader keep rules
-keep class org.tinylog.** { *; }
-keep interface org.tinylog.** { *; }
-keep class io.github.makeforge.clapi.ui.desktop.logging.UiDiagnosticsLogWriter { *; }
-keep class io.github.makeforge.clapi.ui.desktop.logging.ClapiRollingFileWriter { *; }
-keepattributes *Annotation*, Signature, InnerClasses, EnclosingMethod
