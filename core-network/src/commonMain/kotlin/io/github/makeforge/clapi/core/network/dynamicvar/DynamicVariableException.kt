package io.github.makeforge.clapi.core.network.dynamicvar

open class DynamicVariableException(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)

class DynamicVariableResolutionException(
    message: String,
    cause: Throwable? = null,
) : DynamicVariableException(message, cause)
