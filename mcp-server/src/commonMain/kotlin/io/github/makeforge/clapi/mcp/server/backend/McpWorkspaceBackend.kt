package io.github.makeforge.clapi.mcp.server.backend

import io.github.makeforge.clapi.core.model.ResponseMetrics
import io.github.makeforge.clapi.core.model.opencollection.DynamicVariableParameter
import io.github.makeforge.clapi.core.scripting.AssertionResult
import kotlinx.serialization.Serializable

const val MASKED_SECRET_PLACEHOLDER = "********"
const val REDACTED_SECRET_PLACEHOLDER = "[REDACTED_SECRET]"
const val DEFAULT_MAX_BODY_LENGTH = 4096
const val MIN_BODY_LENGTH = 256
const val MAX_BODY_LENGTH = 65536

@Serializable
data class WorkspaceTreeNodeDto(
    val id: String,
    val name: String,
    val kind: String,
    val method: String? = null,
    val url: String? = null,
    val slashPath: String,
    val children: List<WorkspaceTreeNodeDto> = emptyList(),
)

@Serializable
data class SanitizedKeyValueDto(
    val key: String,
    val value: String,
    val enabled: Boolean = true,
    val secret: Boolean = false,
    val description: String = "",
)

@Serializable
data class SanitizedVariableDto(
    val key: String,
    val value: String,
    val kind: String = "regular",
    val variableType: String = "string",
    val secret: Boolean = false,
    val isSet: Boolean = false,
    val enabled: Boolean = true,
    val dynamicScript: String? = null,
    val dynamicParameters: List<DynamicVariableParameter> = emptyList(),
    val sourceEnvName: String? = null,
    val isInherited: Boolean = false,
    val isOverridden: Boolean = false,
)

@Serializable
data class SanitizedAuthDto(
    val type: String = "NONE",
    val username: String? = null,
    val password: String? = null,
    val token: String? = null,
    val apiKey: String? = null,
    val apiValue: String? = null,
    val apiPlacement: String? = null,
    val oauth2Flow: String? = null,
    val oauth2AccessTokenUrl: String? = null,
    val oauth2RefreshTokenUrl: String? = null,
    val oauth2AuthorizationUrl: String? = null,
    val oauth2RedirectUri: String? = null,
    val oauth2ClientId: String? = null,
    val oauth2ClientSecret: String? = null,
    val oauth2CredentialsPlacement: String? = null,
    val oauth2Username: String? = null,
    val oauth2Password: String? = null,
    val oauth2Scope: String? = null,
    val isConfigured: Boolean = false,
)

@Serializable
data class SanitizedExternalSecretDeclarationDto(
    val name: String,
    val providerType: String,
    val path: String,
    val url: String? = null,
    val targetField: String = "password",
    val customFilters: Map<String, String> = emptyMap(),
    val enabled: Boolean = true,
)

@Serializable
data class FormDataEntryDto(
    val key: String,
    val type: String = "TEXT",
    val value: String = "",
    val description: String = "",
    val enabled: Boolean = true,
)

@Serializable
data class ProtoFileDto(
    val path: String,
    val syncToGit: Boolean = false,
)

@Serializable
data class ProtoImportPathDto(
    val path: String,
    val enabled: Boolean = true,
    val syncToGit: Boolean = false,
)

@Serializable
data class GrpcMetadataDto(
    val name: String,
    val value: String,
    val disabled: Boolean = false,
)

@Serializable
data class GrpcRequestConfigDto(
    val protoFilePath: String? = null,
    val grpcMethod: String? = null,
    val grpcMethodType: String = "unary",
    val metadata: List<GrpcMetadataDto> = emptyList(),
    val message: String? = null,
)

@Serializable
data class GraphQlVariantDto(
    val title: String,
    val query: String = "",
    val variables: String? = null,
    val selected: Boolean = false,
)

@Serializable
data class GraphQlRequestConfigDto(
    val method: String = "POST",
    val query: String = "",
    val operationName: String? = null,
    val variablesJson: String? = null,
    val variants: List<GraphQlVariantDto> = emptyList(),
)

@Serializable
data class WebSocketVariantDto(
    val title: String,
    val type: String = "text",
    val data: String = "",
    val selected: Boolean = false,
)

@Serializable
data class WebSocketRequestConfigDto(
    val messageFormat: String = "text",
    val timeout: String? = null,
    val keepAliveInterval: String? = null,
    val variants: List<WebSocketVariantDto> = emptyList(),
)

@Serializable
data class RabbitMqConnectionDto(
    val host: String = "localhost",
    val port: Int? = 5672,
    val vhost: String = "/",
    val useTls: Boolean = false,
)

@Serializable
data class RabbitMqExchangeDto(
    val name: String = "",
    val type: String = "direct",
    val durable: Boolean = true,
    val autoDelete: Boolean = false,
)

@Serializable
data class RabbitMqQueueDto(
    val name: String = "",
    val durable: Boolean = true,
    val exclusive: Boolean = false,
    val autoDelete: Boolean = false,
)

@Serializable
data class RabbitMqPropertiesDto(
    val contentType: String? = "application/json",
    val contentEncoding: String? = null,
    val deliveryMode: Int? = 2,
    val priority: Int? = null,
    val correlationId: String? = null,
    val replyTo: String? = null,
    val expiration: String? = null,
    val messageId: String? = null,
    val type: String? = null,
    val appId: String? = null,
)

@Serializable
data class RabbitMqSettingsDto(
    val connectionTimeoutMs: Long = 10_000L,
    val heartbeatSeconds: Int = 30,
    val publisherConfirms: Boolean = true,
    val mandatory: Boolean = false,
    val declareTopology: Boolean = false,
    val ackMode: String = "auto",
    val prefetchCount: Int = 50,
    val maxTimelineMessages: Int = 1_000,
    val rpcTimeoutMs: Long = 15_000L,
    val replyStrategy: String = "direct_reply_to",
    val useTlsClientCert: Boolean = false,
)

@Serializable
data class RabbitMqRequestConfigDto(
    val mode: String = "publish",
    val connection: RabbitMqConnectionDto = RabbitMqConnectionDto(),
    val exchange: RabbitMqExchangeDto? = null,
    val queue: RabbitMqQueueDto? = null,
    val routingKey: String = "",
    val replyQueue: String? = null,
    val properties: RabbitMqPropertiesDto? = null,
    val variants: List<WebSocketVariantDto> = emptyList(),
    val settings: RabbitMqSettingsDto = RabbitMqSettingsDto(),
)

@Serializable
data class SubscribeRabbitMqInput(
    val requestId: String,
    val maxMessages: Int = 10,
    val timeoutMs: Long = 5_000L,
    val environmentName: String? = null,
    val transientVariables: Map<String, String> = emptyMap(),
    val maxBodyLength: Int = DEFAULT_MAX_BODY_LENGTH,
)

@Serializable
data class SanitizedRabbitMqMessageDto(
    val timestamp: Long,
    val exchange: String,
    val routingKey: String,
    val contentType: String? = null,
    val headers: Map<String, String> = emptyMap(),
    val properties: RabbitMqPropertiesDto = RabbitMqPropertiesDto(),
    val bodyPreview: String = "",
    val byteLength: Long = 0L,
    val isBinary: Boolean = false,
    val _truncated: Boolean = false,
)

@Serializable
data class SubscribeRabbitMqResultDto(
    val requestId: String,
    val elapsedMs: Long,
    val messageCount: Int,
    val truncated: Boolean = false,
    val messages: List<SanitizedRabbitMqMessageDto> = emptyList(),
    val error: String? = null,
)

@Serializable
data class SanitizedRequestDto(
    val id: String,
    val name: String,
    val slashPath: String,
    val collectionId: String,
    val parentFolderId: String? = null,
    val kind: String = "http",
    val summary: String? = null,
    val method: String? = null,
    val url: String = "",
    val queryParams: List<SanitizedKeyValueDto> = emptyList(),
    val headers: List<SanitizedKeyValueDto> = emptyList(),
    val auth: SanitizedAuthDto = SanitizedAuthDto(),
    val bodyType: String? = null,
    val bodyContent: String? = null,
    val formDataEntries: List<FormDataEntryDto> = emptyList(),
    val variables: List<SanitizedVariableDto> = emptyList(),
    val preRequestScript: String? = null,
    val testScript: String? = null,
    val grpc: GrpcRequestConfigDto? = null,
    val graphql: GraphQlRequestConfigDto? = null,
    val websocket: WebSocketRequestConfigDto? = null,
    val rabbitmq: RabbitMqRequestConfigDto? = null,
)

@Serializable
data class SanitizedFolderDto(
    val id: String,
    val name: String,
    val slashPath: String,
    val collectionId: String,
    val parentFolderId: String? = null,
    val auth: SanitizedAuthDto = SanitizedAuthDto(),
    val headers: List<SanitizedKeyValueDto> = emptyList(),
    val variables: List<SanitizedVariableDto> = emptyList(),
    val preRequestScript: String? = null,
    val testScript: String? = null,
    val children: List<WorkspaceTreeNodeDto> = emptyList(),
)

@Serializable
data class SanitizedCollectionDto(
    val id: String,
    val name: String,
    val slashPath: String,
    val summary: String? = null,
    val version: String? = null,
    val storageType: String = "LOCAL",
    val gitRemoteUrl: String? = null,
    val gitBranch: String? = null,
    val auth: SanitizedAuthDto = SanitizedAuthDto(),
    val headers: List<SanitizedKeyValueDto> = emptyList(),
    val variables: List<SanitizedVariableDto> = emptyList(),
    val preRequestScript: String? = null,
    val testScript: String? = null,
    val protoFiles: List<ProtoFileDto> = emptyList(),
    val importPaths: List<ProtoImportPathDto> = emptyList(),
    val children: List<WorkspaceTreeNodeDto> = emptyList(),
)

@Serializable
data class SanitizedEnvironmentDto(
    val name: String,
    val color: String? = null,
    val extends: String? = null,
    val description: String? = null,
    val isActive: Boolean = false,
    val variables: List<SanitizedVariableDto> = emptyList(),
    val externalSecretsProvider: String? = null,
    val externalSecrets: List<SanitizedExternalSecretDeclarationDto> = emptyList(),
)

@Serializable
data class EnvironmentsOverviewDto(
    val activeEnvironment: String? = null,
    val globalVariables: List<SanitizedVariableDto> = emptyList(),
    val environments: List<SanitizedEnvironmentDto> = emptyList(),
)

@Serializable
data class DeleteResultDto(
    val deleted: Boolean,
    val id: String,
    val name: String,
    val kind: String,
)

@Serializable
data class SanitizedConsoleEntryDto(
    val timestamp: Long,
    val level: String = "INFO",
    val message: String,
)

@Serializable
data class ExecutionSnapshot(
    val requestId: String,
    val slashPath: String,
    val executedAtEpochMillis: Long,
    val statusCode: Int,
    val statusText: String,
    val contentType: String? = null,
    val isBinary: Boolean = false,
    val totalBytes: Long = 0,
    val sanitizedRequestHeaders: List<SanitizedKeyValueDto> = emptyList(),
    val sanitizedResponseHeaders: List<SanitizedKeyValueDto> = emptyList(),
    val sanitizedBodyText: String = "",
    val metrics: ResponseMetrics,
    val assertions: List<AssertionResult> = emptyList(),
    val scriptLogs: List<String> = emptyList(),
    val scriptError: String? = null,
)

@Serializable
data class ExecutionResultDto(
    val requestId: String,
    val slashPath: String,
    val executedAtEpochMillis: Long,
    val statusCode: Int,
    val statusText: String,
    val contentType: String? = null,
    val requestHeaders: List<SanitizedKeyValueDto> = emptyList(),
    val responseHeaders: List<SanitizedKeyValueDto> = emptyList(),
    val bodyPreview: String = "",
    val metrics: ResponseMetrics,
    val assertions: List<AssertionResult> = emptyList(),
    val scriptLogs: List<String> = emptyList(),
    val scriptError: String? = null,
    val _truncated: Boolean = false,
    val _binary: Boolean = false,
    val _totalBytes: Long = 0,
    val _hint: String? = null,
)

@Serializable
data class InspectResponseResultDto(
    val requestId: String,
    val jsonPath: String? = null,
    val byteOffset: Int,
    val returnedBytes: Int,
    val totalBytes: Int,
    val hasMore: Boolean,
    val content: String,
)

@Serializable
data class TestSuiteItemResultDto(
    val requestId: String,
    val name: String,
    val slashPath: String,
    val statusCode: Int,
    val passed: Boolean,
    val durationMs: Long,
    val assertions: List<AssertionResult> = emptyList(),
    val error: String? = null,
)

@Serializable
data class TestSuiteResultDto(
    val targetName: String,
    val totalRequests: Int,
    val passedRequests: Int,
    val failedRequests: Int,
    val totalAssertions: Int,
    val passedAssertions: Int,
    val failedAssertions: Int,
    val durationMs: Long,
    val results: List<TestSuiteItemResultDto> = emptyList(),
)

@Serializable
data class ExportResultDto(
    val collectionName: String,
    val format: String,
    val content: String,
)

@Serializable
data class GitSyncResultDto(
    val collectionName: String,
    val action: String,
    val branch: String,
    val remoteUrl: String,
    val status: String,
    val modifiedFiles: List<String> = emptyList(),
    val message: String,
)

// ── Tool Input DTOs ──────────────────────────────────────────────────────

@Serializable
data class ManageCollectionInput(
    val action: String,
    val collectionId: String? = null,
    val name: String? = null,
    val summary: String? = null,
    val version: String? = null,
    val auth: SanitizedAuthDto? = null,
    val headers: List<SanitizedKeyValueDto>? = null,
    val preRequestScript: String? = null,
    val testScript: String? = null,
    val protoFiles: List<ProtoFileDto>? = null,
    val importPaths: List<ProtoImportPathDto>? = null,
    val confirm: Boolean = false,
)

@Serializable
data class ManageFolderInput(
    val action: String,
    val folderId: String? = null,
    val parentId: String? = null,
    val name: String? = null,
    val auth: SanitizedAuthDto? = null,
    val headers: List<SanitizedKeyValueDto>? = null,
    val preRequestScript: String? = null,
    val testScript: String? = null,
    val confirm: Boolean = false,
)

@Serializable
data class UpsertRequestInput(
    val requestId: String? = null,
    val parentId: String? = null,
    val name: String? = null,
    val kind: String? = null,
    val summary: String? = null,
    val method: String? = null,
    val url: String? = null,
    val queryParams: List<SanitizedKeyValueDto>? = null,
    val headers: List<SanitizedKeyValueDto>? = null,
    val auth: SanitizedAuthDto? = null,
    val bodyType: String? = null,
    val bodyContent: String? = null,
    val formDataEntries: List<FormDataEntryDto>? = null,
    val variables: List<SanitizedVariableDto>? = null,
    val preRequestScript: String? = null,
    val testScript: String? = null,
    val grpc: GrpcRequestConfigDto? = null,
    val graphql: GraphQlRequestConfigDto? = null,
    val websocket: WebSocketRequestConfigDto? = null,
    val rabbitmq: RabbitMqRequestConfigDto? = null,
)

@Serializable
data class UpsertEnvironmentInput(
    val name: String,
    val newName: String? = null,
    val color: String? = null,
    val extends: String? = null,
    val description: String? = null,
    val externalSecretsProvider: String? = null,
    val externalSecrets: List<SanitizedExternalSecretDeclarationDto>? = null,
)

@Serializable
data class SetVariableInput(
    val scope: String,
    val target: String? = null,
    val key: String,
    val action: String = "upsert",
    val kind: String = "regular",
    val variableType: String = "string",
    val value: String = "",
    val enabled: Boolean = true,
    val dynamicScript: String? = null,
    val dynamicParameters: List<DynamicVariableParameter> = emptyList(),
)

@Serializable
data class ExecuteRequestInput(
    val requestId: String,
    val environmentName: String? = null,
    val transientVariables: Map<String, String> = emptyMap(),
    val maxBodyLength: Int = DEFAULT_MAX_BODY_LENGTH,
)

@Serializable
data class InspectResponseInput(
    val requestId: String,
    val jsonPath: String? = null,
    val byteOffset: Int = 0,
    val length: Int = DEFAULT_MAX_BODY_LENGTH,
)

@Serializable
data class RunTestSuiteInput(
    val targetId: String,
    val environmentName: String? = null,
    val stopOnFirstFailure: Boolean = false,
)

@Serializable
data class ImportSpecInput(
    val rawContent: String? = null,
    val filePath: String? = null,
    val collectionNameOverride: String? = null,
)

@Serializable
data class ExportCollectionInput(
    val collectionId: String,
    val format: String = "opencollection-yaml",
)

@Serializable
data class GitSyncInput(
    val collectionId: String,
    val action: String,
    val commitMessage: String? = null,
)

// ── Backend Interface ────────────────────────────────────────────────────

interface McpWorkspaceBackend {
    suspend fun listTree(
        collectionFilter: String? = null,
        kindFilter: String? = null,
        query: String? = null,
    ): List<WorkspaceTreeNodeDto>

    suspend fun getCollection(idOrPath: String): SanitizedCollectionDto
    suspend fun manageCollection(input: ManageCollectionInput): SanitizedCollectionDto
    suspend fun deleteCollection(idOrPath: String, confirm: Boolean): DeleteResultDto

    suspend fun manageFolder(input: ManageFolderInput): SanitizedFolderDto
    suspend fun deleteFolder(idOrPath: String, confirm: Boolean): DeleteResultDto

    suspend fun getRequest(idOrPath: String): SanitizedRequestDto
    suspend fun upsertRequest(input: UpsertRequestInput): SanitizedRequestDto
    suspend fun deleteRequest(idOrPath: String, confirm: Boolean): DeleteResultDto

    suspend fun listEnvironments(includeVariables: Boolean = true): EnvironmentsOverviewDto
    suspend fun getEnvironmentDetails(name: String): SanitizedEnvironmentDto
    suspend fun setActiveEnvironment(name: String?): EnvironmentsOverviewDto
    suspend fun upsertEnvironment(input: UpsertEnvironmentInput): SanitizedEnvironmentDto
    suspend fun deleteEnvironment(name: String, confirm: Boolean): DeleteResultDto
    suspend fun setVariable(input: SetVariableInput): SanitizedVariableDto

    suspend fun executeRequest(input: ExecuteRequestInput): ExecutionResultDto
    suspend fun subscribeRabbitMq(input: SubscribeRabbitMqInput): SubscribeRabbitMqResultDto
    suspend fun inspectResponse(input: InspectResponseInput): InspectResponseResultDto
    suspend fun getLatestResponse(idOrPath: String, maxBodyLength: Int = DEFAULT_MAX_BODY_LENGTH): ExecutionResultDto?
    suspend fun runTestSuite(input: RunTestSuiteInput): TestSuiteResultDto

    suspend fun importSpecification(input: ImportSpecInput): SanitizedCollectionDto
    suspend fun exportCollection(input: ExportCollectionInput): ExportResultDto
    suspend fun gitSync(input: GitSyncInput): GitSyncResultDto

    suspend fun getConsoleLogs(limit: Int = 100): List<SanitizedConsoleEntryDto>
}
