package io.github.makeforge.clapi.ui.desktop

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import io.github.makeforge.clapi.core.model.opencollection.DynamicVariableParameter
import io.github.makeforge.clapi.ui.shared.MainScreen
import io.github.makeforge.clapi.ui.shared.components.dialogs.dynamicvar.DynamicVariableEditorDialog
import io.github.makeforge.clapi.ui.shared.platform.DocsLinks
import io.github.makeforge.clapi.ui.shared.state.AppStateExamples
import io.github.makeforge.clapi.ui.shared.state.MutableKeyValue
import io.github.makeforge.clapi.ui.shared.theme.ClapiTheme
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DynamicVariableEditorDialogTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun switchVariableTypeToDynamic_replacesValueWithEditScriptButton_andOpensSubdialog() {
        val state = AppStateExamples.demo().apply {
            environments.first().variables.clear()
            environments.first().variables.add(MutableKeyValue("secondsFromNow", ""))
            openEnvEdit(0)
        }

        composeRule.setContent { MainScreen(state) }
        composeRule.waitForIdle()

        // Initially Normal: value field exists
        composeRule.onNodeWithTag("env-var-value-0").assertIsDisplayed()

        // Click type toggle twice: Normal -> Secret -> Dynamic
        composeRule.onNodeWithTag("env-var-type-toggle-0").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("env-var-type-toggle-0").performClick()
        composeRule.waitForIdle()

        // Value field is replaced by Edit Script button
        composeRule.onNodeWithTag("env-var-value-0").assertDoesNotExist()
        composeRule.onNodeWithTag("env-var-edit-script-0").assertIsDisplayed()

        // Click Edit Script button to open DynamicVariableEditorDialog
        composeRule.onNodeWithTag("env-var-edit-script-0").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("dynamic-var-editor-dialog").assertIsDisplayed()

        // Add a parameter "seconds" with default "10"
        composeRule.onNodeWithTag("dynamic-var-add-param-button").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("dynamic-var-param-name-0").performTextReplacement("seconds")
        composeRule.onNodeWithTag("dynamic-var-param-default-0").performTextReplacement("10")
        composeRule.waitForIdle()

        // Signature preview should show {{secondsFromNow(seconds)}}
        composeRule.onNodeWithTag("dynamic-var-signature-preview")
            .assert(hasText("{{secondsFromNow(seconds)}}", substring = true))

        // Save the dynamic variable subdialog
        composeRule.onNodeWithTag("dynamic-var-save-button").performClick()
        composeRule.waitForIdle()

        // Save environment
        composeRule.onNodeWithTag("env-save-button").performClick()
        composeRule.waitForIdle()

        val savedVar = state.environments.first().variables.first()
        assertTrue(savedVar.isDynamic)
        assertEquals("secondsFromNow", savedVar.key)
        assertEquals(1, savedVar.dynamicParameters.size)
        assertEquals("seconds", savedVar.dynamicParameters.first().name)
        assertEquals("10", savedVar.dynamicParameters.first().defaultValue)
    }

    @Test
    fun dynamicVariableTestPanel_executesScriptWithParametersAndRequestContext() {
        val kv = MutableKeyValue(
            key = "computeHeader",
            value = "",
            isDynamic = true,
            dynamicScript = """
                console.log("Evaluating for", context.request.method);
                return context.request.method + ":" + (Number(args.offset) + 5);
            """.trimIndent(),
            dynamicParameters = listOf(
                DynamicVariableParameter(name = "offset", defaultValue = "10")
            ),
        )

        composeRule.setContent {
            ClapiTheme {
                DynamicVariableEditorDialog(
                    kv = kv,
                    onDismiss = {},
                )
            }
        }
        composeRule.waitForIdle()

        // Click Run Test
        composeRule.onNodeWithTag("dynamic-var-run-test-button").performClick()

        // Wait until dynamic-var-test-return-value appears
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag("dynamic-var-test-return-value").fetchSemanticsNodes().isNotEmpty()
        }

        composeRule.onNodeWithTag("dynamic-var-test-status").assert(hasText("Success", substring = true))
        composeRule.onNodeWithTag("dynamic-var-test-return-value").assert(hasText("GET:15", substring = true))
        composeRule.onNodeWithTag("dynamic-var-test-logs").assert(hasText("Evaluating for GET", substring = true))
    }

    @Test
    fun dynamicVariableTestPanel_displaysErrorWhenScriptThrows() {
        val kv = MutableKeyValue(
            key = "failingVar",
            value = "",
            isDynamic = true,
            dynamicScript = "throw new Error('Custom failure in dynamic variable');",
        )

        composeRule.setContent {
            ClapiTheme {
                DynamicVariableEditorDialog(
                    kv = kv,
                    onDismiss = {},
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("dynamic-var-run-test-button").performClick()

        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag("dynamic-var-test-error").fetchSemanticsNodes().isNotEmpty()
        }

        composeRule.onNodeWithTag("dynamic-var-test-status").assert(hasText("Error", substring = true))
        composeRule.onNodeWithTag("dynamic-var-test-error")
            .assert(hasText("Custom failure in dynamic variable", substring = true))
    }

    @Test
    fun dynamicVariableEditorDialog_showsRequestContextHint_andOpensDocsLink() {
        val openedUrls = mutableListOf<String>()
        val fakeUriHandler = object : UriHandler {
            override fun openUri(uri: String) {
                openedUrls += uri
            }
        }
        val kv = MutableKeyValue(
            key = "myDynVar",
            value = "",
            isDynamic = true,
            dynamicScript = "return context.request.url;",
        )

        val previousDevProp = System.getProperty("clapi.dev")
        try {
            System.setProperty("clapi.dev", "false")
            assertEquals(
                "https://makeforge.github.io/Clapi/environments/dynamic-variables",
                DocsLinks.dynamicVariables,
            )

            composeRule.setContent {
                ClapiTheme {
                    CompositionLocalProvider(LocalUriHandler provides fakeUriHandler) {
                        DynamicVariableEditorDialog(
                            kv = kv,
                            onDismiss = {},
                        )
                    }
                }
            }
            composeRule.waitForIdle()

            composeRule.onNodeWithTag("dynamic-var-context-hint")
                .assertIsDisplayed()
                .assert(hasText("context.request", substring = true))

            composeRule.onNodeWithTag("dynamic-var-docs-link")
                .assertIsDisplayed()
                .performClick()

            assertEquals(
                listOf("https://makeforge.github.io/Clapi/environments/dynamic-variables"),
                openedUrls,
            )

            // Verify local dev mode switches DocsLinks base URL to http://localhost:5173
            System.setProperty("clapi.dev", "true")
            assertEquals(
                "http://localhost:5173/environments/dynamic-variables",
                DocsLinks.dynamicVariables,
            )
        } finally {
            if (previousDevProp == null) {
                System.clearProperty("clapi.dev")
            } else {
                System.setProperty("clapi.dev", previousDevProp)
            }
        }
    }
}
