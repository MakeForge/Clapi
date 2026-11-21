package io.github.makeforge.clapi.ui.desktop.logging

import io.github.makeforge.clapi.core.logging.AppLogging
import io.github.makeforge.clapi.mcp.server.transport.StdioMcpServerRunner
import org.tinylog.configuration.Configuration
import org.tinylog.provider.ProviderRegistry
import java.io.File

object LogDirectoryConfigurator {

    fun configure(args: Array<String>): File {
        val storageDir = StdioMcpServerRunner.resolveStorageDirectory(args)
        val logDir = File(storageDir, "logs")
        logDir.mkdirs()

        System.setProperty("clapi.log.dir", logDir.absolutePath)
        ClapiRollingFileWriter.reconfigure(logDir)
        if (!Configuration.isFrozen()) {
            Configuration.set("writer2.file", "${logDir.absolutePath}/clapi.{date:yyyy-MM-dd}.{count}.log")
            Configuration.set("writer2.latest", "${logDir.absolutePath}/clapi.log")
        }

        val previousHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching {
                GlobalCrashHandler.handleException(throwable, thread.name)
            }
            if (previousHandler != null && !throwable.message.orEmpty().startsWith("SYNTHETIC_CRASH_EXCEPTION")) {
                previousHandler.uncaughtException(thread, throwable)
            }
        }

        return logDir
    }

    fun shutdown() {
        runCatching {
            ClapiRollingFileWriter.flushCurrent()
            ProviderRegistry.getLoggingProvider().shutdown()
        }
    }
}
