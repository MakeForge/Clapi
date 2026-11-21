package io.github.makeforge.clapi.core.network

actual fun getEnvironmentVariable(name: String): String? = System.getenv(name)
