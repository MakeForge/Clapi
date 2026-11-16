package io.github.makeforge.clapi.ui.desktop.preview

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import io.github.makeforge.clapi.ui.shared.MainScreen
import io.github.makeforge.clapi.ui.shared.state.AppStateExamples
import io.github.makeforge.clapi.ui.shared.state.AppTheme
import io.github.makeforge.clapi.ui.shared.theme.ClapiTheme

// ── HTTP Request Previews ──────────────────────────────────────────────────
private const val width = 1920
private const val height = 1080

@Preview(widthDp = width, heightDp = height)
@Composable
fun MainScreenHttpPreviewLight() {
    val state = AppStateExamples.http().apply { settings.theme = AppTheme.LIGHT }
    ClapiTheme(appTheme = AppTheme.LIGHT) {
        MainScreen(state)
    }
}

@Preview(widthDp = width, heightDp = height)
@Composable
fun MainScreenHttpPreviewDark() {
    val state = AppStateExamples.http().apply { settings.theme = AppTheme.DARK }
    ClapiTheme(appTheme = AppTheme.DARK) {
        MainScreen(state)
    }
}

// ── gRPC Request Previews ──────────────────────────────────────────────────

@Preview(widthDp = width, heightDp = height)
@Composable
fun MainScreenGrpcPreviewLight() {
    val state = AppStateExamples.grpc().apply { settings.theme = AppTheme.LIGHT }
    ClapiTheme(appTheme = AppTheme.LIGHT) {
        MainScreen(state)
    }
}

@Preview(widthDp = width, heightDp = height)
@Composable
fun MainScreenGrpcPreviewDark() {
    val state = AppStateExamples.grpc().apply { settings.theme = AppTheme.DARK }
    ClapiTheme(appTheme = AppTheme.DARK) {
        MainScreen(state)
    }
}

// ── WebSocket Request Previews ─────────────────────────────────────────────

@Preview(widthDp = width, heightDp = height)
@Composable
fun MainScreenWebSocketPreviewLight() {
    val state = AppStateExamples.webSocket().apply { settings.theme = AppTheme.LIGHT }
    ClapiTheme(appTheme = AppTheme.LIGHT) {
        MainScreen(state)
    }
}

@Preview(widthDp = width, heightDp = height)
@Composable
fun MainScreenWebSocketPreviewDark() {
    val state = AppStateExamples.webSocket().apply { settings.theme = AppTheme.DARK }
    ClapiTheme(appTheme = AppTheme.DARK) {
        MainScreen(state)
    }
}

// ── GraphQL Request Previews ───────────────────────────────────────────────

@Preview(widthDp = width, heightDp = height)
@Composable
fun MainScreenGraphQlPreviewLight() {
    val state = AppStateExamples.graphQl().apply { settings.theme = AppTheme.LIGHT }
    ClapiTheme(appTheme = AppTheme.LIGHT) {
        MainScreen(state)
    }
}

@Preview(widthDp = width, heightDp = height)
@Composable
fun MainScreenGraphQlPreviewDark() {
    val state = AppStateExamples.graphQl().apply { settings.theme = AppTheme.DARK }
    ClapiTheme(appTheme = AppTheme.DARK) {
        MainScreen(state)
    }
}

// ── MCP Request Previews ───────────────────────────────────────────────────

@Preview(widthDp = width, heightDp = height)
@Composable
fun MainScreenMcpPreviewLight() {
    val state = AppStateExamples.mcp().apply { settings.theme = AppTheme.LIGHT }
    ClapiTheme(appTheme = AppTheme.LIGHT) {
        MainScreen(state)
    }
}

@Preview(widthDp = width, heightDp = height)
@Composable
fun MainScreenMcpPreviewDark() {
    val state = AppStateExamples.mcp().apply { settings.theme = AppTheme.DARK }
    ClapiTheme(appTheme = AppTheme.DARK) {
        MainScreen(state)
    }
}

// ── Default Previews (Aliased to HTTP for backward compatibility) ─────────

@Preview(widthDp = width, heightDp = height)
@Composable
fun MainScreenPreviewLight() {
    MainScreenHttpPreviewLight()
}

@Preview(widthDp = width, heightDp = height)
@Composable
fun MainScreenPreviewDark() {
    MainScreenHttpPreviewDark()
}
