// Modified by MakeForge (2026)
package io.github.makeforge.clapi.testsupport

import org.junit.Assume
import kotlin.test.fail

/**
 * Guards integration tests that depend on external local Docker services (Keycloak, RabbitMQ, Vault).
 *
 * - In CI (`CI=true`), an unreachable required service **fails** the test explicitly so container
 *   startup failures can never pass silently as skipped tests.
 * - Locally (`CI != true`), an unreachable service skips via [Assume.assumeTrue] for fast offline runs.
 */
object CiTestGuard {

    fun isCiEnvironment(envProvider: (String) -> String? = System::getenv): Boolean =
        envProvider("CI")?.equals("true", ignoreCase = true) == true

    fun requireServiceOrSkip(
        serviceName: String,
        endpoint: String,
        isReachable: Boolean,
        localHint: String = "",
        envProvider: (String) -> String? = System::getenv,
    ) {
        if (isReachable) return
        val hintSuffix = if (localHint.isNotBlank()) " ($localHint)" else ""
        if (isCiEnvironment(envProvider)) {
            fail("$serviceName not reachable at $endpoint in CI — required Docker service failed to start$hintSuffix")
        } else {
            Assume.assumeTrue("Skipping: $serviceName not reachable at $endpoint$hintSuffix", false)
        }
    }
}
