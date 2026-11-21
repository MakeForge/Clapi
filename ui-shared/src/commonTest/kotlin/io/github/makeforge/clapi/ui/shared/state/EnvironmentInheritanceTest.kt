package io.github.makeforge.clapi.ui.shared.state

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EnvironmentInheritanceTest {

    @Test
    fun testSingleLevelInheritance() {
        val baseEnv = EnvState(
            name = "Base",
            variables = listOf(
                MutableKeyValue("BASE_URL", "https://api.example.com"),
                MutableKeyValue("TIMEOUT", "30"),
            ),
        )
        val devEnv = EnvState(
            name = "Dev",
            extends = "Base",
            variables = listOf(
                MutableKeyValue("DEBUG", "true"),
            ),
        )
        val all = listOf(baseEnv, devEnv)

        val resolved = devEnv.resolveEffectiveVariables(all)
        assertEquals("https://api.example.com", resolved["BASE_URL"])
        assertEquals("30", resolved["TIMEOUT"])
        assertEquals("true", resolved["DEBUG"])
    }

    @Test
    fun testChildVariableOverridesParent() {
        val baseEnv = EnvState(
            name = "Base",
            variables = listOf(
                MutableKeyValue("BASE_URL", "https://api.example.com"),
                MutableKeyValue("TIMEOUT", "30"),
            ),
        )
        val prodEnv = EnvState(
            name = "Prod",
            extends = "Base",
            variables = listOf(
                MutableKeyValue("BASE_URL", "https://api.prod.example.com"),
            ),
        )
        val all = listOf(baseEnv, prodEnv)

        val resolved = prodEnv.resolveEffectiveVariables(all)
        assertEquals("https://api.prod.example.com", resolved["BASE_URL"])
        assertEquals("30", resolved["TIMEOUT"])
    }

    @Test
    fun testMultiTierInheritance() {
        val rootEnv = EnvState(
            name = "Root",
            variables = listOf(
                MutableKeyValue("TENANT", "global"),
                MutableKeyValue("REGION", "us-east-1"),
                MutableKeyValue("RETRIES", "3"),
            ),
        )
        val stagingEnv = EnvState(
            name = "Staging",
            extends = "Root",
            variables = listOf(
                MutableKeyValue("REGION", "eu-west-1"),
            ),
        )
        val featureEnv = EnvState(
            name = "Feature",
            extends = "Staging",
            variables = listOf(
                MutableKeyValue("FEATURE_FLAG", "enabled"),
                MutableKeyValue("RETRIES", "5"),
            ),
        )
        val all = listOf(rootEnv, stagingEnv, featureEnv)

        val resolved = featureEnv.resolveEffectiveVariables(all)
        assertEquals("global", resolved["TENANT"])
        assertEquals("eu-west-1", resolved["REGION"]) // overridden by Staging
        assertEquals("5", resolved["RETRIES"])         // overridden by Feature
        assertEquals("enabled", resolved["FEATURE_FLAG"])
    }

    @Test
    fun testCycleDetectionDoesNotCauseStackOverflow() {
        val envA = EnvState(name = "EnvA", extends = "EnvB", variables = listOf(MutableKeyValue("K1", "V1")))
        val envB = EnvState(name = "EnvB", extends = "EnvA", variables = listOf(MutableKeyValue("K2", "V2")))
        val all = listOf(envA, envB)

        val resolvedA = envA.resolveEffectiveVariables(all)
        assertEquals("V1", resolvedA["K1"])
        assertEquals("V2", resolvedA["K2"])

        val resolvedB = envB.resolveEffectiveVariables(all)
        assertEquals("V1", resolvedB["K1"])
        assertEquals("V2", resolvedB["K2"])
    }

    @Test
    fun testSelfExtensionDoesNotCauseStackOverflow() {
        val env = EnvState(name = "SelfLoop", extends = "SelfLoop", variables = listOf(MutableKeyValue("KEY", "VAL")))
        val resolved = env.resolveEffectiveVariables(listOf(env))
        assertEquals("VAL", resolved["KEY"])
    }

    @Test
    fun testNonexistentParentEnvironmentGracefullyHandled() {
        val env = EnvState(name = "Orphan", extends = "DeletedParent", variables = listOf(MutableKeyValue("A", "1")))
        val resolved = env.resolveEffectiveVariables(listOf(env))
        assertEquals("1", resolved["A"])
        assertEquals(1, resolved.size)
    }

    @Test
    fun testGetEligibleParentEnvironmentsPreventsSelfAndCycles() {
        val envA = EnvState(name = "A")
        val envB = EnvState(name = "B", extends = "A")
        val envC = EnvState(name = "C", extends = "B")
        val envIndependent = EnvState(name = "X")
        val all = listOf(envA, envB, envC, envIndependent)

        // For A: cannot select A (self), cannot select B (since B extends A), cannot select C (since C extends B extends A)
        val eligibleForA = getEligibleParentEnvironments(envA, all).map { it.name }
        assertFalse(eligibleForA.contains("A"))
        assertFalse(eligibleForA.contains("B"))
        assertFalse(eligibleForA.contains("C"))
        assertTrue(eligibleForA.contains("X"))

        // For C: can select A, B, or X, but cannot select C
        val eligibleForC = getEligibleParentEnvironments(envC, all).map { it.name }
        assertTrue(eligibleForC.contains("A"))
        assertTrue(eligibleForC.contains("B"))
        assertTrue(eligibleForC.contains("X"))
        assertFalse(eligibleForC.contains("C"))
    }

    @Test
    fun testResolveVariableProjections() {
        val parent = EnvState(
            name = "Parent",
            variables = listOf(
                MutableKeyValue("INHERITED_ONLY", "parent-val"),
                MutableKeyValue("OVERRIDDEN", "original-val"),
            ),
        )
        val child = EnvState(
            name = "Child",
            extends = "Parent",
            variables = listOf(
                MutableKeyValue("LOCAL_ONLY", "child-val"),
                MutableKeyValue("OVERRIDDEN", "child-override-val"),
            ),
        )
        val all = listOf(parent, child)

        val projections = resolveVariableProjections(child, all)

        val localOnly = projections.first { it.key == "LOCAL_ONLY" }
        assertFalse(localOnly.isInherited)
        assertFalse(localOnly.isOverridden)
        assertEquals("child-val", localOnly.value)

        val overridden = projections.first { it.key == "OVERRIDDEN" }
        assertFalse(overridden.isInherited)
        assertTrue(overridden.isOverridden)
        assertEquals("original-val", overridden.overriddenValue)
        assertEquals("child-override-val", overridden.value)

        val inheritedOnly = projections.first { it.key == "INHERITED_ONLY" }
        assertTrue(inheritedOnly.isInherited)
        assertFalse(inheritedOnly.isOverridden)
        assertEquals("parent-val", inheritedOnly.value)
        assertEquals("Parent", inheritedOnly.sourceEnvName)
    }
}
