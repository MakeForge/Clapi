package io.github.makeforge.clapi.core.logging

import io.github.oshai.kotlinlogging.KLogger
import io.github.oshai.kotlinlogging.KotlinLogging

/**
 * Common logging facade for Clapi components and modules.
 * Delegates to kotlin-logging (and SLF4J / Tinylog on JVM desktop).
 */
object AppLogging {
    fun logger(name: String): KLogger = KotlinLogging.logger(name)
    inline fun <reified T : Any> logger(): KLogger = KotlinLogging.logger(T::class.simpleName ?: "App")
}
