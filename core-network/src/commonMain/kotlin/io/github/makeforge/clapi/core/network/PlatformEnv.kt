package io.github.makeforge.clapi.core.network

/**
 * Platform-safe process environment variable lookup.
 */
expect fun getEnvironmentVariable(name: String): String?
