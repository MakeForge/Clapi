package io.github.makeforge.clapi.core.network

import io.github.makeforge.clapi.core.logging.AppLogging

class Slf4jNetworkLogger(tag: String = "Network") : NetworkLogger {
    private val logger = AppLogging.logger(tag)

    override fun debug(message: String) {
        logger.debug { message }
    }

    override fun info(message: String) {
        logger.info { message }
    }

    override fun error(message: String, throwable: Throwable?) {
        if (throwable != null) {
            logger.error(throwable) { message }
        } else {
            logger.error { message }
        }
    }
}
