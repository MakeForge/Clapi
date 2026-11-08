package io.github.crustack.clapi.core.network

actual fun getEnvironmentVariable(name: String): String? = System.getenv(name)
