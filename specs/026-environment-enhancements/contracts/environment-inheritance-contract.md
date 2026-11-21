# Contract: Environment Inheritance & Variable Overrides

## Overview
Defines the programmatic and user interface contracts for environment inheritance (`OpenCollection.Environment.extends`), resolution hierarchy, cycle prevention, and UI indicators.

## 1. Resolution Contract

```kotlin
interface EnvironmentVariableResolver {
    /**
     * Resolves the effective variable map for [targetEnv] by walking its inheritance chain.
     * Nearest-child variables take precedence over ancestor variables.
     * Detects cycles to prevent infinite recursion.
     */
    fun resolveEffectiveVariables(
        targetEnv: EnvState,
        allEnvironments: List<EnvState>,
    ): Map<String, String>

    /**
     * Returns detailed projection of all local and inherited variables for [targetEnv],
     * including source provenance and override status.
     */
    fun resolveVariableProjections(
        targetEnv: EnvState,
        allEnvironments: List<EnvState>,
    ): List<InheritedVariableInfo>

    /**
     * Computes the list of environments that [targetEnv] is permitted to extend without
     * creating a self-reference or circular dependency.
     */
    fun getEligibleParentEnvironments(
        targetEnv: EnvState,
        allEnvironments: List<EnvState>,
    ): List<EnvState>
}
```

### Invariants & Guarantees
1. **Determinism**: Given an environment configuration and variable set, resolution is strictly deterministic.
2. **Cycle Safety**: If environment `A` extends `B` and an external change sets `B` extends `A`, resolution detects `A` in the visited set and terminates safely without throwing `StackOverflowError`.
3. **Graceful Fallback**: If `extends` points to an environment that does not exist in `allEnvironments`, resolution yields `targetEnv`'s local variables without error.
4. **Secret Variable Handling**: Variables marked `secret = true` with blank values do not shadow ancestor variables with actual non-blank values.

---

## 2. UI Contract: Edit Environment Dialog

### Extends Selector
- **Test Tag**: `env-extends-dropdown`
- **Behavior**:
  - Displays currently selected parent environment name, or `"None"` if `extends == null`.
  - When clicked, opens a dropdown menu containing:
    - `"None"` (clears inheritance)
    - Each eligible environment from `getEligibleParentEnvironments(targetEnv, state.environments)`
  - If `eligibleEnvironments.isEmpty()`, dropdown control is disabled (`enabled = false`) and displays a tooltip/helper explaining no eligible environments exist.

### Override Indication
- **Test Tag**: `env-var-override-badge-{varName}`
- **Behavior**:
  - When a local variable has the same key as an inherited variable from `extends`, render a badge next to the variable key with text `"Overridden"`.
  - Hovering the badge reveals a tooltip: `"Overrides value from [ParentEnvName]"`.
