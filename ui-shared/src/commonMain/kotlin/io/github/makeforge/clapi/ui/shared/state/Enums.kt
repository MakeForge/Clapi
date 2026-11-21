package io.github.makeforge.clapi.ui.shared.state



// ── Enumerations ────────────────────────────────────────────────

enum class RequestEditorTab(val label: String) {
    PARAMS("Params"), HEADERS("Headers"), BODY("Body"),
    AUTH("Auth"), PRE_REQUEST("Pre-request"), TESTS("Post-request"),
    VARIABLES("Variables")
}

enum class GrpcEditorTab(val label: String) {
    MESSAGE("Message"), HEADERS("Headers"), AUTH("Auth"), SCRIPTS("Scripts")
}

enum class WebSocketEditorTab(val label: String) {
    MESSAGE("Message"),
    HEADERS("Headers"),
    AUTH("Auth"),
    SETTINGS("Settings"),
    SCRIPTS("Scripts"),
    VARIABLES("Variables"),
    DOCS("Docs")
}

enum class GraphQLEditorTab(val label: String) {
    BODY("Body"),
    HEADERS("Headers"),
    PARAMS("Params"),
    AUTH("Auth"),
    SCRIPTS("Scripts"),
    SETTINGS("Settings"),
    DOCS("Docs")
}

enum class RabbitMqEditorTab(val label: String) {
    MESSAGE("Message"),
    AMQP("AMQP"),
    HEADERS("Headers & Props"),
    AUTH("Auth"),
    SCRIPTS("Scripts"),
    SETTINGS("Settings"),
    DOCS("Docs")
}

enum class ResponseTab(val label: String) {
    BODY("Body"), HEADERS("Headers"), COOKIES("Cookies"), TIMING("Timing"), RAW("Raw")
}

enum class BottomTab(val label: String) {
    CONSOLE("Console"), TEST_RESULTS("Test Results"), LOGS("Logs")
}

enum class LogLevel { INFO, SUCCESS, WARNING, ERROR }

enum class AppTheme { DARK, LIGHT, SYSTEM }
enum class ResponseLayout { RIGHT, BOTTOM }

/** Display mode for the response view panel (expanded in main window, collapsed to top-right strip, or detached into floating window). */
enum class ResponseDisplayMode { EXPANDED, COLLAPSED, DETACHED }

/** Top-level workspace mode — determines which main panel is shown. */
enum class WorkspaceMode(val label: String) {
    HTTP("HTTP"),
    REALTIME("Realtime"),
    GRAPHQL("GraphQL"),
}

enum class HeaderKind { SYSTEM, USER, INHERITED }

fun normalizeApiKeyPlacement(value: String?): String =
    if (value.equals("query", ignoreCase = true)) "query" else "header"
