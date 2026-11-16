package io.github.makeforge.clapi.ui.shared.state

import androidx.compose.runtime.mutableStateListOf
import io.github.makeforge.clapi.core.model.AuthType
import io.github.makeforge.clapi.core.model.BodyType
import io.github.makeforge.clapi.core.model.HttpMethodType
import io.github.makeforge.clapi.core.model.KeyValueEntry
import io.github.makeforge.clapi.core.model.McpConnectionConfig
import io.github.makeforge.clapi.core.model.McpTransportType
import io.github.makeforge.clapi.core.model.MessageDirection
import io.github.makeforge.clapi.core.model.RequestKind
import io.github.makeforge.clapi.core.model.ResponseDefinition
import io.github.makeforge.clapi.core.model.ResponseMetrics
import io.github.makeforge.clapi.core.model.WebSocketLogMessage
import io.github.makeforge.clapi.core.model.opencollection.GraphQLBody
import io.github.makeforge.clapi.core.model.opencollection.GraphQLBodyVariant
import io.github.makeforge.clapi.core.model.opencollection.WebSocketMessage
import io.github.makeforge.clapi.core.model.opencollection.WebSocketMessageType
import io.github.makeforge.clapi.core.model.opencollection.WebSocketMessageVariant
import io.github.makeforge.clapi.core.network.ConnectionState
import io.github.makeforge.clapi.core.network.grpc.ProtoMethodInfo
import io.github.makeforge.clapi.ui.shared.platform.currentTimeMillis

/**
 * Pre-filled [AppState] instances and data fixtures for UI desktop previews, screenshots, and tests.
 */
object AppStateExamples {

    fun environments(): List<EnvState> = listOf(
        EnvState(
            "Development",
            listOf(
                MutableKeyValue("baseUrl", "http://localhost:8080"),
                MutableKeyValue("authToken", "dev-token-1234"),
            ),
        ),
        EnvState(
            "Staging",
            listOf(
                MutableKeyValue("baseUrl", "https://staging.api.example.com"),
                MutableKeyValue("authToken", "stg-token-abcd"),
            ),
        ),
        EnvState(
            "Production",
            listOf(
                MutableKeyValue("baseUrl", "https://api.example.com"),
                MutableKeyValue("authToken", "prod-token-xyz", secret = true),
            ),
        ),
    )

    fun globalVariables(): List<MutableKeyValue> = listOf(
        MutableKeyValue("appName", "Clapi"),
        MutableKeyValue("apiVersion", "v1"),
    )

    fun historyItems(now: Long = currentTimeMillis()): List<HistoryItem> = listOf(
        HistoryItem(
            requestId = "r1",
            method = HttpMethodType.GET,
            name = "Get all users",
            url = "{{baseUrl}}/users",
            timestamp = now - 300_000,
            collectionId = "c1",
            folderPath = emptyList(),
        ),
        HistoryItem(
            requestId = "r2",
            method = HttpMethodType.POST,
            name = "Create user",
            url = "{{baseUrl}}/users",
            timestamp = now - 600_000,
            collectionId = "c1",
            folderPath = emptyList(),
        ),
        HistoryItem(
            requestId = "r4",
            method = HttpMethodType.POST,
            name = "Login",
            url = "{{baseUrl}}/auth/login",
            timestamp = now - 900_000,
            collectionId = "c2",
            folderPath = emptyList(),
        ),
    )

    /**
     * Standard demo collections preserved for existing unit tests.
     */
    fun collections(): List<CollectionNode> = listOf(
        CollectionNode(
            "c1",
            "Users API",
            isFolder = true,
            kind = RequestKind.COLLECTION,
            children = mutableStateListOf(
                CollectionNode("r1", "Get all users", method = HttpMethodType.GET, url = "{{baseUrl}}/users"),
                CollectionNode("r2", "Create user", method = HttpMethodType.POST, url = "{{baseUrl}}/users"),
                CollectionNode("r3", "Update user", method = HttpMethodType.PUT, url = "{{baseUrl}}/users/1"),
            ),
        ),
        CollectionNode(
            "c2",
            "Auth",
            isFolder = true,
            kind = RequestKind.COLLECTION,
            children = mutableStateListOf(
                CollectionNode("r4", "Login", method = HttpMethodType.POST, url = "{{baseUrl}}/auth/login"),
                CollectionNode("r5", "Refresh", method = HttpMethodType.POST, url = "{{baseUrl}}/auth/refresh"),
            ),
        ),
    )

    /**
     * Hierarchical collection structure with multiple folders and mixed request kinds,
     * representing a realistic production workspace for screenshots and marketing previews.
     */
    fun richExampleCollections(): List<CollectionNode> = listOf(
        CollectionNode(
            id = "c1",
            name = "Users API",
            isFolder = true,
            kind = RequestKind.COLLECTION,
            children = mutableStateListOf(
                CollectionNode(
                    id = "f-users",
                    name = "Users",
                    isFolder = true,
                    kind = RequestKind.FOLDER,
                    children = mutableStateListOf(
                        CollectionNode("r-http-1", "Get User Profile", method = HttpMethodType.GET, url = "{{baseUrl}}/users/42", kind = RequestKind.HTTP),
                        CollectionNode("r-http-2", "Create User", method = HttpMethodType.POST, url = "{{baseUrl}}/users", kind = RequestKind.HTTP),
                        CollectionNode("r-http-3", "List Users", method = HttpMethodType.GET, url = "{{baseUrl}}/users?page=1", kind = RequestKind.HTTP),
                    ),
                ),
                CollectionNode(
                    id = "f-grpc",
                    name = "Greeter Service",
                    isFolder = true,
                    kind = RequestKind.FOLDER,
                    children = mutableStateListOf(
                        CollectionNode("r-grpc-1", "Say Hello", kind = RequestKind.GRPC, url = "grpc://localhost:50051"),
                        CollectionNode("r-grpc-2", "Stream Greetings", kind = RequestKind.GRPC, url = "grpc://localhost:50051"),
                    ),
                ),
                CollectionNode(
                    id = "f-ws",
                    name = "Realtime Streams",
                    isFolder = true,
                    kind = RequestKind.FOLDER,
                    children = mutableStateListOf(
                        CollectionNode("r-ws-1", "Realtime Notifications", kind = RequestKind.WEBSOCKET, url = "wss://echo.websocket.org"),
                        CollectionNode("r-ws-2", "Chat Feed", kind = RequestKind.WEBSOCKET, url = "wss://chat.example.com/feed"),
                    ),
                ),
                CollectionNode(
                    id = "f-gql",
                    name = "Product Catalog",
                    isFolder = true,
                    kind = RequestKind.FOLDER,
                    children = mutableStateListOf(
                        CollectionNode("r-gql-1", "Get Product Details", kind = RequestKind.GRAPHQL, url = "{{baseUrl}}/graphql"),
                        CollectionNode("r-gql-2", "Search Products", kind = RequestKind.GRAPHQL, url = "{{baseUrl}}/graphql"),
                    ),
                ),
            ),
        ),
        CollectionNode(
            id = "c2",
            name = "Auth",
            isFolder = true,
            kind = RequestKind.COLLECTION,
            children = mutableStateListOf(
                CollectionNode("r4", "Login", method = HttpMethodType.POST, url = "{{baseUrl}}/auth/login", kind = RequestKind.HTTP),
                CollectionNode("r5", "Refresh", method = HttpMethodType.POST, url = "{{baseUrl}}/auth/refresh", kind = RequestKind.HTTP),
            ),
        ),
    )

    /**
     * Demo AppState for unit tests previously calling `AppState(withDemoData = true)`.
     */
    fun demo(openDefaultTab: Boolean = true): AppState {
        val state = AppState(openDefaultTab = openDefaultTab)
        state.collectionsExpanded = true
        state.environmentsExpanded = true
        state.environments.addAll(environments())
        state.globalVariables.addAll(globalVariables())
        state.historyItems.addAll(historyItems())
        state.collections.addAll(collections())
        state.ensureRequestRefsInitialized()
        state.syncHistoryWithCollections()
        return state
    }

    /**
     * Alias for [http].
     */
    fun default(): AppState = http()

    /**
     * Example AppState with an HTTP GET request tab open with complete headers, params, and response.
     */
    fun http(): AppState {
        val now = currentTimeMillis()
        val state = AppState(openDefaultTab = false)
        state.collectionsExpanded = true
        state.environmentsExpanded = false
        state.environments.addAll(environments())
        state.globalVariables.addAll(globalVariables())
        state.historyItems.addAll(historyItems(now))

        state.collections.addAll(richExampleCollections())
        state.collectionExpandedState["c1"] = true
        state.collectionExpandedState["f-users"] = true
        state.collectionExpandedState["f-grpc"] = false
        state.collectionExpandedState["f-ws"] = false
        state.collectionExpandedState["f-gql"] = false
        state.collectionExpandedState["c2"] = false

        state.ensureRequestRefsInitialized()
        state.syncHistoryWithCollections()

        val httpTab = RequestTabState(
            id = "r-http-1",
            name = "Get User Profile",
            kind = RequestKind.HTTP,
        ).apply {
            method = HttpMethodType.GET
            url = "{{baseUrl}}/users/42"
            headers.clear()
            headers.add(MutableKeyValue("Accept", "application/json"))
            headers.add(MutableKeyValue("Authorization", "Bearer {{authToken}}"))
            params.clear()
            params.add(MutableKeyValue("include", "profile,settings"))
            params.add(MutableKeyValue("locale", "en-US"))
            response = ResponseDefinition(
                requestId = "r-http-1",
                statusCode = 200,
                statusText = "OK",
                headers = listOf(
                    KeyValueEntry("content-type", "application/json; charset=utf-8"),
                    KeyValueEntry("cache-control", "private, max-age=120"),
                    KeyValueEntry("x-request-id", "req-c918f7a2"),
                    KeyValueEntry("server", "Ktor/3.1.2"),
                ),
                cookies = emptyList(),
                bodyText = """{
  "id": 42,
  "username": "alex_morgan",
  "email": "alex.morgan@example.com",
  "fullName": "Alex Morgan",
  "role": "Staff Engineer",
  "team": "Platform Core",
  "preferences": {
    "theme": "dark",
    "notifications": true,
    "locale": "en-US"
  },
  "created_at": "2026-03-15T09:30:00Z"
}""",
                contentType = "application/json",
                executedAtEpochMillis = now - 15_000,
                metrics = ResponseMetrics(
                    statusCode = 200,
                    responseTimeMs = 48L,
                    responseSizeBytes = 384L,
                ),
            )
            markSaved()
        }

        val grpcTab = RequestTabState(
            id = "r-grpc-1",
            name = "Say Hello",
            kind = RequestKind.GRPC,
        ).apply {
            url = "grpc://localhost:50051"
            grpcMethod = "helloworld.Greeter/SayHello"
            markSaved()
        }

        val gqlTab = RequestTabState(
            id = "r-gql-1",
            name = "Product Details",
            kind = RequestKind.GRAPHQL,
        ).apply {
            url = "{{baseUrl}}/graphql"
            markSaved()
        }

        state.openTabs.addAll(listOf(httpTab, grpcTab, gqlTab))
        state.activeTabIndex = 0
        state.selectedCollectionId = "c1"
        state.selectedRequestId = "r-http-1"
        state.selectedRequestIds.clear()
        state.selectedRequestIds.add("r-http-1")
        state.lastSelectedSidebarItemId = "r-http-1"

        return state
    }

    /**
     * Example AppState with a gRPC request tab open showing proto, method, message payload, and response.
     */
    fun grpc(): AppState {
        val now = currentTimeMillis()
        val state = AppState(openDefaultTab = false)
        state.collectionsExpanded = true
        state.environmentsExpanded = false
        state.environments.addAll(environments())
        state.globalVariables.addAll(globalVariables())
        state.historyItems.addAll(historyItems(now))

        state.collections.addAll(richExampleCollections())
        state.collectionExpandedState["c1"] = true
        state.collectionExpandedState["f-users"] = false
        state.collectionExpandedState["f-grpc"] = true
        state.collectionExpandedState["f-ws"] = false
        state.collectionExpandedState["f-gql"] = false
        state.collectionExpandedState["c2"] = false

        state.ensureRequestRefsInitialized()
        state.syncHistoryWithCollections()

        val httpTab = RequestTabState(
            id = "r-http-1",
            name = "Get User Profile",
            kind = RequestKind.HTTP,
        ).apply {
            url = "{{baseUrl}}/users/42"
            markSaved()
        }

        val grpcTab = RequestTabState(
            id = "r-grpc-1",
            name = "Say Hello",
            kind = RequestKind.GRPC,
        ).apply {
            url = "grpc://localhost:50051"
            protoFilePath = "greeter.proto"
            grpcMethod = "helloworld.Greeter/SayHello"
            grpcMethodType = "unary"
            availableMethods = listOf(
                ProtoMethodInfo(
                    serviceFullName = "helloworld.Greeter",
                    methodName = "SayHello",
                    fullMethodName = "helloworld.Greeter/SayHello",
                    inputTypeName = "helloworld.HelloRequest",
                    outputTypeName = "helloworld.HelloReply",
                    isClientStreaming = false,
                    isServerStreaming = false,
                ),
                ProtoMethodInfo(
                    serviceFullName = "helloworld.Greeter",
                    methodName = "StreamGreetings",
                    fullMethodName = "helloworld.Greeter/StreamGreetings",
                    inputTypeName = "helloworld.HelloRequest",
                    outputTypeName = "helloworld.HelloReply",
                    isClientStreaming = false,
                    isServerStreaming = true,
                ),
            )
            bodyType = BodyType.JSON
            grpcMessageBody = """{
  "name": "Clapi Engineer",
  "clientVersion": "1.19.0"
}"""
            grpcMetadata.clear()
            grpcMetadata.add(MutableKeyValue("x-client-id", "clapi-desktop"))
            grpcMetadata.add(MutableKeyValue("authorization", "Bearer {{authToken}}"))
            response = ResponseDefinition(
                requestId = "r-grpc-1",
                statusCode = 0,
                statusText = "OK",
                headers = listOf(
                    KeyValueEntry("content-type", "application/grpc"),
                    KeyValueEntry("grpc-status", "0"),
                    KeyValueEntry("grpc-message", "OK"),
                ),
                cookies = emptyList(),
                bodyText = """{
  "message": "Hello, Clapi Engineer! Welcome to Clapi gRPC.",
  "timestamp": "2026-11-14T22:45:10Z"
}""",
                contentType = "application/json",
                executedAtEpochMillis = now - 8000,
                metrics = ResponseMetrics(
                    statusCode = 0,
                    responseTimeMs = 19L,
                    responseSizeBytes = 112L,
                ),
            )
            markSaved()
        }

        val wsTab = RequestTabState(
            id = "r-ws-1",
            name = "Realtime Notifications",
            kind = RequestKind.WEBSOCKET,
        ).apply {
            url = "wss://echo.websocket.org"
            markSaved()
        }

        state.openTabs.addAll(listOf(httpTab, grpcTab, wsTab))
        state.activeTabIndex = 1
        state.selectedCollectionId = "c1"
        state.selectedRequestId = "r-grpc-1"
        state.selectedRequestIds.clear()
        state.selectedRequestIds.add("r-grpc-1")
        state.lastSelectedSidebarItemId = "r-grpc-1"

        return state
    }

    /**
     * Example AppState with a WebSocket request tab open showing active connection, message variants, and live events.
     */
    fun webSocket(): AppState {
        val now = currentTimeMillis()
        val state = AppState(openDefaultTab = false)
        state.collectionsExpanded = true
        state.environmentsExpanded = false
        state.environments.addAll(environments())
        state.globalVariables.addAll(globalVariables())
        state.historyItems.addAll(historyItems(now))

        state.collections.addAll(richExampleCollections())
        state.collectionExpandedState["c1"] = true
        state.collectionExpandedState["f-users"] = false
        state.collectionExpandedState["f-grpc"] = false
        state.collectionExpandedState["f-ws"] = true
        state.collectionExpandedState["f-gql"] = false
        state.collectionExpandedState["c2"] = false

        state.ensureRequestRefsInitialized()
        state.syncHistoryWithCollections()

        val httpTab = RequestTabState(
            id = "r-http-1",
            name = "Get User Profile",
            kind = RequestKind.HTTP,
        ).apply {
            url = "{{baseUrl}}/users/42"
            markSaved()
        }

        val wsTab = RequestTabState(
            id = "r-ws-1",
            name = "Realtime Notifications",
            kind = RequestKind.WEBSOCKET,
        ).apply {
            url = "wss://echo.websocket.org"
            webSocketConnectionState = ConnectionState.CONNECTED
            selectedWebSocketTab = WebSocketEditorTab.MESSAGE
            webSocketOutgoingPayload = """{
  "action": "subscribe",
  "channel": "deployments",
  "auth": "{{authToken}}"
}"""
            webSocketMessageVariants.clear()
            webSocketMessageVariants.addAll(
                listOf(
                    WebSocketMessageVariant(
                        title = "Subscribe",
                        selected = true,
                        message = WebSocketMessage(
                            type = WebSocketMessageType.JSON,
                            data = """{"action": "subscribe", "channel": "deployments", "auth": "{{authToken}}"}""",
                        ),
                    ),
                    WebSocketMessageVariant(
                        title = "Heartbeat",
                        selected = false,
                        message = WebSocketMessage(
                            type = WebSocketMessageType.JSON,
                            data = """{"action": "ping"}""",
                        ),
                    ),
                ),
            )
            activeWebSocketVariantIndex = 0
            webSocketLogMessages.clear()
            webSocketLogMessages.addAll(
                listOf(
                    WebSocketLogMessage(
                        id = "ws-1",
                        timestamp = now - 12_000,
                        direction = MessageDirection.SENT,
                        payload = """{"action": "subscribe", "channel": "deployments", "auth": "{{authToken}}"}""",
                    ),
                    WebSocketLogMessage(
                        id = "ws-2",
                        timestamp = now - 11_800,
                        direction = MessageDirection.RECEIVED,
                        payload = """{"event": "subscribed", "channel": "deployments", "status": "active"}""",
                    ),
                    WebSocketLogMessage(
                        id = "ws-3",
                        timestamp = now - 5_000,
                        direction = MessageDirection.RECEIVED,
                        payload = """{"event": "deployment_status", "service": "auth-service", "version": "v2.4.1", "state": "SUCCESS"}""",
                    ),
                ),
            )
            markSaved()
        }

        val gqlTab = RequestTabState(
            id = "r-gql-1",
            name = "Product Details",
            kind = RequestKind.GRAPHQL,
        ).apply {
            url = "{{baseUrl}}/graphql"
            markSaved()
        }

        state.openTabs.addAll(listOf(httpTab, wsTab, gqlTab))
        state.activeTabIndex = 1
        state.selectedCollectionId = "c1"
        state.selectedRequestId = "r-ws-1"
        state.selectedRequestIds.clear()
        state.selectedRequestIds.add("r-ws-1")
        state.lastSelectedSidebarItemId = "r-ws-1"

        return state
    }

    /**
     * Example AppState with a GraphQL request tab open showing query, variables, and response.
     */
    fun graphQl(): AppState {
        val now = currentTimeMillis()
        val state = AppState(openDefaultTab = false)
        state.collectionsExpanded = true
        state.environmentsExpanded = false
        state.environments.addAll(environments())
        state.globalVariables.addAll(globalVariables())
        state.historyItems.addAll(historyItems(now))

        state.collections.addAll(richExampleCollections())
        state.collectionExpandedState["c1"] = true
        state.collectionExpandedState["f-users"] = false
        state.collectionExpandedState["f-grpc"] = false
        state.collectionExpandedState["f-ws"] = false
        state.collectionExpandedState["f-gql"] = true
        state.collectionExpandedState["c2"] = false

        state.ensureRequestRefsInitialized()
        state.syncHistoryWithCollections()

        val httpTab = RequestTabState(
            id = "r-http-1",
            name = "Get User Profile",
            kind = RequestKind.HTTP,
        ).apply {
            url = "{{baseUrl}}/users/42"
            markSaved()
        }

        val gqlTab = RequestTabState(
            id = "r-gql-1",
            name = "Get Product Details",
            kind = RequestKind.GRAPHQL,
        ).apply {
            url = "{{baseUrl}}/graphql"
            graphQlMethod = "POST"
            selectedGraphQLTab = GraphQLEditorTab.BODY
            graphQlQuery = """query GetProductDetails(${'$'}id: ID!) {
  product(id: ${'$'}id) {
    id
    title
    category
    price
    inStock
    rating {
      score
      reviewsCount
    }
  }
}"""
            graphQlVariables = """{
  "id": "prod_4920"
}"""
            headers.clear()
            headers.add(MutableKeyValue("Content-Type", "application/json"))
            headers.add(MutableKeyValue("Authorization", "Bearer {{authToken}}"))
            response = ResponseDefinition(
                requestId = "r-gql-1",
                statusCode = 200,
                statusText = "OK",
                headers = listOf(
                    KeyValueEntry("content-type", "application/json; charset=utf-8"),
                    KeyValueEntry("x-gql-complexity", "14"),
                ),
                cookies = emptyList(),
                bodyText = """{
  "data": {
    "product": {
      "id": "prod_4920",
      "title": "Ergonomic Mechanical Keyboard",
      "category": "Peripherals",
      "price": 149.99,
      "inStock": true,
      "rating": {
        "score": 4.9,
        "reviewsCount": 128
      }
    }
  }
}""",
                contentType = "application/json",
                executedAtEpochMillis = now - 10_000,
                metrics = ResponseMetrics(
                    statusCode = 200,
                    responseTimeMs = 62L,
                    responseSizeBytes = 286L,
                ),
            )
            markSaved()
        }

        state.openTabs.addAll(listOf(httpTab, gqlTab))
        state.activeTabIndex = 1
        state.selectedCollectionId = "c1"
        state.selectedRequestId = "r-gql-1"
        state.selectedRequestIds.clear()
        state.selectedRequestIds.add("r-gql-1")
        state.lastSelectedSidebarItemId = "r-gql-1"

        return state
    }

    /**
     * Example AppState with an MCP connection tab open.
     */
    fun mcp(): AppState {
        val now = currentTimeMillis()
        val state = AppState(openDefaultTab = false)
        state.collectionsExpanded = true
        state.environmentsExpanded = false
        state.environments.addAll(environments())
        state.globalVariables.addAll(globalVariables())
        state.historyItems.addAll(historyItems(now))

        state.collections.addAll(richExampleCollections())
        state.collectionExpandedState["c1"] = true
        state.collectionExpandedState["c2"] = false

        state.ensureRequestRefsInitialized()
        state.syncHistoryWithCollections()

        val httpTab = RequestTabState(
            id = "r-http-1",
            name = "Get User Profile",
            kind = RequestKind.HTTP,
        ).apply {
            url = "{{baseUrl}}/users/42"
            markSaved()
        }

        val mcpTab = RequestTabState(
            id = "r-mcp-1",
            name = "Sample MCP Server",
            kind = RequestKind.MCP,
        ).apply {
            url = "http://localhost:8080/mcp"
            mcpConfig = McpConnectionConfig(
                transport = McpTransportType.STREAMABLE_HTTP,
                url = "http://localhost:8080/mcp",
            )
            markSaved()
        }

        state.openTabs.addAll(listOf(httpTab, mcpTab))
        state.activeTabIndex = 1
        state.selectedCollectionId = "c1"

        return state
    }
}
