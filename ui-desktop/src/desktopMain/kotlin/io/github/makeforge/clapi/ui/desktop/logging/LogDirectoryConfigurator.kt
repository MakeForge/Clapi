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
        if (!Configuration.isFrozen()) {
            Configuration.set("writer2.file", "${logDir.absolutePath}/clapi.{date:yyyy-MM-dd}.{count}.log")
            Configuration.set("writer2.latest", "${logDir.absolutePath}/clapi.log")
        }

        val previousHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching {
                AppLogging.logger("CrashHandler").error(throwable) {
                    "Uncaught fatal exception on thread '${thread.name}': ${throwable.message}"
                }
                ProviderRegistry.getLoggingProvider().shutdown()
            }
            previousHandler?.uncaughtException(thread, throwable)
        }

        return logDir
    }

    fun shutdown() {
        runCatching {
            ProviderRegistry.getLoggingProvider().shutdown()
        }
    }
}
