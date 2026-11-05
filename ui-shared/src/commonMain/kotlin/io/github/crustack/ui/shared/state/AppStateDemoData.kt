package io.github.crustack.ui.shared.state

import androidx.compose.runtime.mutableStateListOf
import io.github.crustack.core.model.AuthType
import io.github.crustack.core.model.BodyType
import io.github.crustack.core.model.HttpMethodType
import io.github.crustack.ui.shared.platform.currentTimeMillis

internal object AppStateDemoData {

    fun environments(): List<EnvState> = listOf(
        EnvState("Development", listOf(
            MutableKeyValue("baseUrl", "http://localhost:8080"),
            MutableKeyValue("authToken", "dev-token-1234"),
        )),
        EnvState("Staging", listOf(
            MutableKeyValue("baseUrl", "https://staging.api.example.com"),
            MutableKeyValue("authToken", "stg-token-abcd"),
        )),
        EnvState("Production", listOf(
            MutableKeyValue("baseUrl", "https://api.example.com"),
            MutableKeyValue("authToken", "prod-token-xyz", secret = true),
        )),
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

    fun collections(): List<CollectionNode> = listOf(
        CollectionNode("c1", "Users API", isFolder = true, children = mutableStateListOf(
            CollectionNode("r1", "Get all users", method = HttpMethodType.GET, url = "{{baseUrl}}/users"),
            CollectionNode("r2", "Create user", method = HttpMethodType.POST, url = "{{baseUrl}}/users"),
            CollectionNode("r3", "Update user", method = HttpMethodType.PUT, url = "{{baseUrl}}/users/1"),
        )),
        CollectionNode("c2", "Auth", isFolder = true, children = mutableStateListOf(
            CollectionNode("r4", "Login", method = HttpMethodType.POST, url = "{{baseUrl}}/auth/login"),
            CollectionNode("r5", "Refresh", method = HttpMethodType.POST, url = "{{baseUrl}}/auth/refresh"),
        )),
    )
}
