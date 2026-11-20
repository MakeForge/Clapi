package io.github.makeforge.clapi.ui.desktop.preview

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import io.github.makeforge.clapi.ui.shared.MainScreen
import io.github.makeforge.clapi.ui.shared.state.AppState
import io.github.makeforge.clapi.ui.shared.state.AppStateExamples
import io.github.makeforge.clapi.ui.shared.state.AppTheme
import io.github.makeforge.clapi.ui.shared.state.BottomTab
import io.github.makeforge.clapi.ui.shared.state.RequestEditorTab
import io.github.makeforge.clapi.ui.shared.state.TestResultEntry
import io.github.makeforge.clapi.ui.shared.theme.ClapiTheme

private const val width = 1600
private const val height = 800

private fun createTestsPreviewState(theme: AppTheme): AppState = AppStateExamples.http().apply {
    settings.theme = theme
    val active = activeTab ?: return@apply
    active.selectedEditorTab = RequestEditorTab.TESTS
    active.testScript = """// Assert HTTP status code
clapi.test("Status code is 201 Created", function () {
    clapi.expect(clapi.response.status).to.equal(201);
});

// Assert response time
clapi.test("Response time is under 200ms", function () {
    clapi.expect(clapi.response.responseTime).to.be.below(200);
});

// Assert JSON payload structure
clapi.test("User object contains id and username", function () {
    const data = JSON.parse(clapi.response.body);
    clapi.expect(data).to.have.property("id");
    clapi.expect(data.username).to.equal("alex_morgan");
    clapi.expect(data.status).to.equal("active");
});"""
    active.testResults.clear()
    active.testResults.addAll(
        listOf(
            TestResultEntry("Status code is 201 Created", true),
            TestResultEntry("Response time is under 200ms", true),
            TestResultEntry("User object contains id and username", true),
        ),
    )
    bottomPanelExpanded = true
    selectedBottomTab = BottomTab.TEST_RESULTS
}

@Preview(widthDp = width, heightDp = height)
@Composable
fun MainScreenTestsPreviewLight() {
    val state = createTestsPreviewState(AppTheme.LIGHT)
    ClapiTheme(appTheme = AppTheme.LIGHT) {
        MainScreen(state)
    }
}

@Preview(widthDp = width, heightDp = height)
@Composable
fun MainScreenTestsPreviewDark() {
    val state = createTestsPreviewState(AppTheme.DARK)
    ClapiTheme(appTheme = AppTheme.DARK) {
        MainScreen(state)
    }
}
