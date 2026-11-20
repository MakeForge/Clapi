package io.github.makeforge.clapi.core.network

import io.github.makeforge.clapi.core.model.opencollection.DynamicVariableParameter
import kotlin.random.Random
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

/**
 * Represents a dynamic variable handler for [VariableResolver].
 * Provides token matching, resolution logic, and documentation metadata for autocomplete dropdowns.
 */
interface DynamicVariable {
    /** The canonical variable token name, typically starting with '$' (e.g. "$timestamp"). */
    val name: String

    /** Short human-readable description for UI tooltips and autocomplete dropdowns. */
    val description: String

    /** Sample value representing the resolved output. */
    val example: String get() = ""

    /** Declared parameters for parameterized dynamic variables. */
    val parameters: List<DynamicVariableParameter> get() = emptyList()

    /**
     * Returns true if this handler can resolve the given variable token.
     * Implementations accept both prefixed (e.g. "$timestamp") and unprefixed (e.g. "timestamp") tokens.
     */
    fun matches(token: String): Boolean

    /**
     * Resolves the token to its dynamic string value, or null if it cannot be resolved.
     */
    fun resolve(token: String): String?
}

object TimestampDynamicVariable : DynamicVariable {
    override val name: String = "\$timestamp"
    override val description: String = "Current Unix epoch milliseconds"
    override val example: String = "1710495000000"

    override fun matches(token: String): Boolean =
        token.removePrefix("$").equals("timestamp", ignoreCase = true)

    override fun resolve(token: String): String =
        Clock.System.now().toEpochMilliseconds().toString()
}

object TimestampMsFromNowDynamicVariable : DynamicVariable {
    private val pattern = Regex("^\\$?timestampMsFromNow\\(\\s*(-?\\d+)\\s*\\)$", RegexOption.IGNORE_CASE)

    override val name: String = "\$timestampMsFromNow(offsetMs)"
    override val description: String = "UTC UNIX epoch timestamp in millis offset after now"
    override val example: String = "1710495000100"
    override val parameters: List<DynamicVariableParameter> = listOf(
        DynamicVariableParameter(name = "offsetMs", defaultValue = "100", description = "Milliseconds after now")
    )

    override fun matches(token: String): Boolean {
        val norm = token.removePrefix("$")
        return norm.equals("timestampMsFromNow", ignoreCase = true) ||
            norm.equals("timestampMsFromNow(offsetMs)", ignoreCase = true) ||
            pattern.matches(token)
    }

    override fun resolve(token: String): String? {
        val groups = pattern.find(token)?.groupValues ?: return null
        val offsetMs = groups[1].toLongOrNull() ?: return null
        return (Clock.System.now().toEpochMilliseconds() + offsetMs).toString()
    }
}

object TimestampMsBeforeNowDynamicVariable : DynamicVariable {
    private val pattern = Regex("^\\$?timestampMsBeforeNow\\(\\s*(-?\\d+)\\s*\\)$", RegexOption.IGNORE_CASE)

    override val name: String = "\$timestampMsBeforeNow(offsetMs)"
    override val description: String = "UTC UNIX epoch timestamp in millis offset before now"
    override val example: String = "1710494999900"
    override val parameters: List<DynamicVariableParameter> = listOf(
        DynamicVariableParameter(name = "offsetMs", defaultValue = "100", description = "Milliseconds before now")
    )

    override fun matches(token: String): Boolean {
        val norm = token.removePrefix("$")
        return norm.equals("timestampMsBeforeNow", ignoreCase = true) ||
            norm.equals("timestampMsBeforeNow(offsetMs)", ignoreCase = true) ||
            pattern.matches(token)
    }

    override fun resolve(token: String): String? {
        val groups = pattern.find(token)?.groupValues ?: return null
        val offsetMs = groups[1].toLongOrNull() ?: return null
        return (Clock.System.now().toEpochMilliseconds() - offsetMs).toString()
    }
}

object IsoTimestampDynamicVariable : DynamicVariable {
    override val name: String = "\$isoTimestamp"
    override val description: String = "Current ISO-8601 timestamp"
    override val example: String = "2026-03-15T09:30:00Z"

    override fun matches(token: String): Boolean =
        token.removePrefix("$").equals("isoTimestamp", ignoreCase = true)

    override fun resolve(token: String): String =
        Clock.System.now().toString()
}

object IsoTimestampFromNowMsDynamicVariable : DynamicVariable {
    private val pattern = Regex("^\\$?isoTimestampFromNowMs\\(\\s*(-?\\d+)\\s*\\)$", RegexOption.IGNORE_CASE)

    override val name: String = "\$isoTimestampFromNowMs(offsetMs)"
    override val description: String = "UTC ISO-8601 timestamp offset in millis after now"
    override val example: String = "2026-03-15T09:30:00.100Z"
    override val parameters: List<DynamicVariableParameter> = listOf(
        DynamicVariableParameter(name = "offsetMs", defaultValue = "100", description = "Milliseconds after now")
    )

    override fun matches(token: String): Boolean {
        val norm = token.removePrefix("$")
        return norm.equals("isoTimestampFromNowMs", ignoreCase = true) ||
            norm.equals("isoTimestampFromNowMs(offsetMs)", ignoreCase = true) ||
            pattern.matches(token)
    }

    override fun resolve(token: String): String? {
        val groups = pattern.find(token)?.groupValues ?: return null
        val offsetMs = groups[1].toLongOrNull() ?: return null
        val targetMs = Clock.System.now().toEpochMilliseconds() + offsetMs
        return Instant.fromEpochMilliseconds(targetMs).toString()
    }
}

object IsoTimestampBeforeNowMsDynamicVariable : DynamicVariable {
    private val pattern = Regex("^\\$?isoTimestampBeforeNowMs\\(\\s*(-?\\d+)\\s*\\)$", RegexOption.IGNORE_CASE)

    override val name: String = "\$isoTimestampBeforeNowMs(offsetMs)"
    override val description: String = "UTC ISO-8601 timestamp offset in millis before now"
    override val example: String = "2026-03-15T09:29:59.900Z"
    override val parameters: List<DynamicVariableParameter> = listOf(
        DynamicVariableParameter(name = "offsetMs", defaultValue = "100", description = "Milliseconds before now")
    )

    override fun matches(token: String): Boolean {
        val norm = token.removePrefix("$")
        return norm.equals("isoTimestampBeforeNowMs", ignoreCase = true) ||
            norm.equals("isoTimestampBeforeNowMs(offsetMs)", ignoreCase = true) ||
            pattern.matches(token)
    }

    override fun resolve(token: String): String? {
        val groups = pattern.find(token)?.groupValues ?: return null
        val offsetMs = groups[1].toLongOrNull() ?: return null
        val targetMs = Clock.System.now().toEpochMilliseconds() - offsetMs
        return Instant.fromEpochMilliseconds(targetMs).toString()
    }
}

object RandomIntDynamicVariable : DynamicVariable {
    override val name: String = "\$randomInt"
    override val description: String = "Random integer (0..1000)"
    override val example: String = "42"

    override fun matches(token: String): Boolean =
        token.removePrefix("$").equals("randomInt", ignoreCase = true)

    override fun resolve(token: String): String =
        Random.nextInt(0, 1000).toString()
}

object RandomIntRangeDynamicVariable : DynamicVariable {
    private val randomIntPattern = Regex("^\\$?randomInt\\(\\s*(-?\\d+)\\s*,\\s*(-?\\d+)\\s*\\)$", RegexOption.IGNORE_CASE)

    override val name: String = "\$randomInt(min, max)"
    override val description: String = "Random integer in range [min, max]"
    override val example: String = "42"
    override val parameters: List<DynamicVariableParameter> = listOf(
        DynamicVariableParameter(name = "min", defaultValue = "0", description = "Minimum value (inclusive)"),
        DynamicVariableParameter(name = "max", defaultValue = "1000", description = "Maximum value (inclusive)"),
    )

    override fun matches(token: String): Boolean {
        val norm = token.removePrefix("$")
        return norm.equals("randomInt(min, max)", ignoreCase = true) || randomIntPattern.matches(token)
    }

    override fun resolve(token: String): String? {
        val groups = randomIntPattern.find(token)?.groupValues ?: return null
        val minInclusive = groups[1].toIntOrNull() ?: return null
        val maxInclusive = groups[2].toIntOrNull() ?: return null
        if (minInclusive > maxInclusive) return null
        return Random.nextInt(minInclusive, maxInclusive + 1).toString()
    }
}

@OptIn(ExperimentalUuidApi::class)
object GuidDynamicVariable : DynamicVariable {
    override val name: String = "\$guid"
    override val description: String = "Random UUID v4"
    override val example: String = "c8928b9d-4786-44ec-b4c6-fbcfe4bc933f"

    override fun matches(token: String): Boolean {
        val norm = token.removePrefix("$")
        return norm.equals("guid", ignoreCase = true) || norm.equals("uuid", ignoreCase = true)
    }

    override fun resolve(token: String): String = Uuid.random().toString()
}
