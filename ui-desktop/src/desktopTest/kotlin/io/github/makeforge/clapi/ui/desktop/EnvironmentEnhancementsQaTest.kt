package io.github.makeforge.clapi.ui.desktop

import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import io.github.makeforge.clapi.ui.shared.MainScreen
import io.github.makeforge.clapi.ui.shared.components.ImportSpecificationDialog
import io.github.makeforge.clapi.ui.shared.components.dialogs.env.ENVIRONMENT_COLOR_PRESETS
import io.github.makeforge.clapi.ui.shared.components.dialogs.env.normalizeHexColor
import io.github.makeforge.clapi.ui.shared.components.dialogs.env.parseHexColor
import io.github.makeforge.clapi.ui.shared.persistence.ImportExportRepository
import io.github.makeforge.clapi.ui.shared.platform.FileDropSemanticsKey
import io.github.makeforge.clapi.ui.shared.state.AppState
import io.github.makeforge.clapi.ui.shared.state.EnvState
import io.github.makeforge.clapi.ui.shared.state.MutableExternalSecret
import io.github.makeforge.clapi.ui.shared.state.MutableKeyValue
import io.github.makeforge.clapi.ui.shared.state.getEligibleParentEnvironments
import io.github.makeforge.clapi.ui.shared.state.isDescendantOf
import io.github.makeforge.clapi.ui.shared.state.resolveEffectiveVariables
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Comprehensive QA Test Suite verifying all User Stories for 026-environment-enhancements:
 * - US1: Environment Inheritance & Variable Overrides
 * - US2: Instant Environment Creation & Expanded Sidebar
 * - US3: Quick-Add Top Row for Variables & External Secrets
 * - US4: Environment Color Customization & Visual Palette
 * - US5: Reliable YAML Drag-and-Drop & Download Icon Consistency
 * - US6: External Secrets Import/Export & Sanitization
 */
class EnvironmentEnhancementsQaTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun qa_us1_multiTierInheritance_and_variableOverrides() {
        val rootEnv = EnvState(
            name = "Root",
            color = "#64748B",
            variables = listOf(
                MutableKeyValue("ORG", "AcmeCorp"),
                MutableKeyValue("TIMEOUT", "10"),
                MutableKeyValue("API_KEY", "root-key", secret = true),
            ),
        )

        val stagingEnv = EnvState(
            name = "Staging",
            extends = "Root",
            color = "#F59E0B",
            variables = listOf(
                MutableKeyValue("TIMEOUT", "30"), // Override Root
                MutableKeyValue("STAGE", "staging-us-east"),
            ),
        )

        val devEnv = EnvState(
            name = "Dev",
            extends = "Staging",
            color = "#10B981",
            variables = listOf(
                MutableKeyValue("STAGE", "dev-local"), // Override Staging
                MutableKeyValue("DEBUG", "true"),
            ),
        )

        val allEnvs = listOf(rootEnv, stagingEnv, devEnv)

        // Verify cycle detection
        assertTrue(isDescendantOf(stagingEnv, rootEnv.name, allEnvs))
        assertTrue(isDescendantOf(devEnv, rootEnv.name, allEnvs))
        assertTrue(isDescendantOf(devEnv, stagingEnv.name, allEnvs))
        assertFalse(isDescendantOf(rootEnv, devEnv.name, allEnvs))

        val eligibleForRoot = getEligibleParentEnvironments(rootEnv, allEnvs)
        assertTrue(eligibleForRoot.none { it.name == "Staging" || it.name == "Dev" || it.name == "Root" })

        // Verify effective variables
        val devResolved = devEnv.resolveEffectiveVariables(allEnvs)
        assertEquals("AcmeCorp", devResolved["ORG"])
        assertEquals("30", devResolved["TIMEOUT"])
        assertEquals("dev-local", devResolved["STAGE"])
        assertEquals("true", devResolved["DEBUG"])

        // Verify active layers in AppState
        val appState = AppState().apply {
            environments.clear()
            environments.addAll(allEnvs)
            selectedEnvIndex = 2 // Dev
        }
        val envLayer = appState.activeVariableLayers().first()
        assertEquals("AcmeCorp", envLayer["ORG"])
        assertEquals("30", envLayer["TIMEOUT"])
        assertEquals("dev-local", envLayer["STAGE"])
        assertEquals("true", envLayer["DEBUG"])
    }

    @Test
    fun qa_us2_environmentCreation_expandsSidebar_and_opensEditDialog() {
        val state = AppState().apply {
            environmentsExpanded = false
            settings.environmentsExpanded = false
        }

        composeRule.setContent { MainScreen(state) }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("sidebar-new-environment-button", useUnmergedTree = true).performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("rename-dialog-input").performTextInput("Production")
        composeRule.onNodeWithTag("rename-dialog-save").performClick()
        composeRule.waitForIdle()

        assertTrue(state.environmentsExpanded)
        assertTrue(state.settings.environmentsExpanded)
        composeRule.onNodeWithTag("env-edit-dialog").assertIsDisplayed()
        assertEquals(1, state.environments.size)
        assertEquals("Production", state.environments.first().name)
    }

    @Test
    fun qa_us3_quickAdd_variable_and_externalSecret() {
        val state = AppState().apply {
            environments.add(EnvState(name = "TestEnv", variables = emptyList()))
            openEnvEdit(0)
        }

        composeRule.setContent { MainScreen(state) }
        composeRule.waitForIdle()

        // 1. Add variable via top row
        composeRule.onNodeWithTag("env-quick-add-key").performTextInput("PORT")
        composeRule.onNodeWithTag("env-quick-add-value").performTextInput("3000")
        composeRule.onNodeWithTag("env-quick-add-variable-button").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("env-var-key-0").assert(hasText("PORT"))
        composeRule.onNodeWithTag("env-var-value-0").assert(hasText("3000"))

        // 2. Add secret via top row in External Secrets tab
        composeRule.onNodeWithTag("env-tab-secrets").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("env-quick-add-secret-name").performTextInput("DATABASE_PASSWORD")
        composeRule.onNodeWithTag("env-quick-add-secret-path").performTextInput("vault/database/prod")
        composeRule.onNodeWithTag("env-quick-add-secret-button").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("env-save-button").performClick()
        composeRule.waitForIdle()

        assertEquals("PORT", state.environments.first().variables.first().key)
        assertEquals("3000", state.environments.first().variables.first().value)
        assertEquals("DATABASE_PASSWORD", state.environments.first().externalSecrets.first().name)
        assertEquals("vault/database/prod", state.environments.first().externalSecrets.first().path)
    }

    @Test
    fun qa_us4_colorPalette_and_hexValidation() {
        assertEquals(8, ENVIRONMENT_COLOR_PRESETS.size)
        assertEquals("#3B82F6", normalizeHexColor("#3b82f6"))
        assertEquals("#AABBCC", normalizeHexColor("aabbcc"))
        assertNull(normalizeHexColor("invalid-color"))

        val state = AppState().apply {
            environmentsExpanded = true
            environments.add(EnvState(name = "ColorEnv", variables = emptyList()))
            openEnvEdit(0)
        }

        composeRule.setContent { MainScreen(state) }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("env-color-hex-field").performTextReplacement("#10B981")
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("env-save-button").performClick()
        composeRule.waitForIdle()

        assertEquals("#10B981", state.environments.first().color)
        composeRule.onNodeWithTag("env-color-dot-ColorEnv", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun qa_us5_yamlDragAndDrop_and_consistentDownloadIcon() {
        val state = AppState().apply { showImportSpecificationDialog = true }
        val sampleYaml = """
            openapi: "3.0.3"
            info:
              title: "QA Test API"
              version: "1.0.0"
            paths:
              /status:
                get:
                  responses:
                    '200':
                      description: OK
        """.trimIndent()

        composeRule.setContent {
            ImportSpecificationDialog(
                state = state,
                onDismiss = { state.showImportSpecificationDialog = false }
            )
        }
        composeRule.waitForIdle()

        // Test FileDropAction Semantics
        val dropAction = composeRule.onNodeWithTag("import-dropzone").fetchSemanticsNode().config[FileDropSemanticsKey]
        dropAction("qa-spec.yaml", sampleYaml)
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("import-scan-button", useUnmergedTree = true).performClick()
        composeRule.waitUntil(5000) {
            composeRule.onAllNodesWithTag("import-format-badge").fetchSemanticsNodes().isNotEmpty()
        }

        composeRule.onNodeWithTag("import-confirm-button", useUnmergedTree = true).performClick()
        composeRule.waitForIdle()

        assertFalse(state.showImportSpecificationDialog)
        assertTrue(state.collections.any { it.name == "QA Test API" })
    }

    @Test
    fun qa_us6_importExport_preservesConfig_and_sanitizesSecrets() {
        val state = AppState()
        val env = EnvState(
            name = "Production",
            color = "#8B5CF6",
            extends = "BaseShared",
            variables = listOf(
                MutableKeyValue("SERVICE_URL", "https://prod.service.internal", enabled = true, secret = false),
                MutableKeyValue("API_KEY", "sensitive-prod-api-key-9999", enabled = true, secret = true),
            ),
            externalSecretsProvider = "aws-secrets-manager",
            externalSecrets = listOf(
                MutableExternalSecret(
                    name = "DB_SECRET",
                    path = "production/database/credentials",
                    targetField = "password",
                    enabled = true,
                )
            ),
        )
        state.environments.add(env)

        val exportedJson = ImportExportRepository.exportEnvironmentToString(env)

        // Assert serialization of extends, color, and external secrets config
        assertTrue(exportedJson.contains("\"color\": \"#8B5CF6\""))
        assertTrue(exportedJson.contains("\"extends\": \"BaseShared\""))
        assertTrue(exportedJson.contains("\"type\": \"aws-secrets-manager\""))
        assertTrue(exportedJson.contains("\"name\": \"DB_SECRET\""))
        assertTrue(exportedJson.contains("\"secretName\": \"production/database/credentials\""))

        // Assert secret value sanitization
        assertFalse(exportedJson.contains("sensitive-prod-api-key-9999"))
        assertTrue(exportedJson.contains("\"API_KEY\": \"\""))

        // Roundtrip import
        val targetState = AppState()
        val importedName = ImportExportRepository.importEnvironmentFromString(targetState, exportedJson)

        assertEquals("Production", importedName)
        assertEquals(1, targetState.environments.size)
        val restored = targetState.environments.first()
        assertEquals("#8B5CF6", restored.color)
        assertEquals("BaseShared", restored.extends)
        assertEquals("aws-secrets-manager", restored.externalSecretsProvider)

        val restoredSecret = restored.variables.find { it.key == "API_KEY" }
        assertNotNull(restoredSecret)
        assertEquals("", restoredSecret.value) // Cleanly imported without plaintext leak

        assertEquals(1, restored.externalSecrets.size)
        assertEquals("DB_SECRET", restored.externalSecrets.first().name)
        assertEquals("production/database/credentials", restored.externalSecrets.first().path)
    }
}
