package io.github.crustack.clapi.core.network

/**
 * Platform-safe process environment variable lookup.
 */
expect fun getEnvironmentVariable(name: String): String?
