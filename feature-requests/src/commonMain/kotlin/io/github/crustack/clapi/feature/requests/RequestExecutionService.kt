package io.github.crustack.clapi.feature.requests

import io.github.crustack.clapi.core.model.RequestBody
import io.github.crustack.clapi.core.model.RequestDefinition
import io.github.crustack.clapi.core.model.opencollection.AwsSecretsManagerExternalSecret
import io.github.crustack.clapi.core.model.opencollection.AzureKeyVaultExternalSecret
import io.github.crustack.clapi.core.model.opencollection.ExternalSecretVariable
import io.github.crustack.clapi.core.model.opencollection.FolderItem
import io.github.crustack.clapi.core.model.opencollection.GraphQLRequestItem
import io.github.crustack.clapi.core.model.opencollection.GrpcRequestItem
import io.github.crustack.clapi.core.model.opencollection.HashicorpVaultExternalSecret
import io.github.crustack.clapi.core.model.opencollection.HttpRequestItem
import io.github.crustack.clapi.core.model.opencollection.OpenCollectionDocument
import io.github.crustack.clapi.core.model.opencollection.RegularVariable
import io.github.crustack.clapi.core.model.opencollection.SecretVariable
import io.github.crustack.clapi.core.model.opencollection.VariableEntry
import io.github.crustack.clapi.core.network.ApiClient
import io.github.crustack.clapi.core.network.NetworkEvent
import io.github.crustack.clapi.core.network.VariableResolver
import io.github.crustack.clapi.core.network.azure.AzureKeyVaultClient
import io.github.crustack.clapi.core.network.getEnvironmentVariable
import io.github.crustack.clapi.core.network.grpc.DynamicGrpcClient
import io.github.crustack.clapi.core.network.grpc.GrpcCallRequest
import io.github.crustack.clapi.core.network.grpc.GrpcCallResponse
import io.github.crustack.clapi.core.network.dynamicvar.DynamicVariableRegistry
import io.github.crustack.clapi.core.network.grpc.parseGrpcEndpoint
import io.github.crustack.clapi.core.network.vault.SecretDeclaration
import io.github.crustack.clapi.core.network.vault.SecretResolutionService
import kotlinx.coroutines.flow.Flow

class RequestExecutionService(
    private val apiClient: ApiClient,
    private val adapter: HttpExecutionAdapter = DefaultHttpExecutionAdapter,
    private val grpcClient: DynamicGrpcClient? = null,
    private val secretResolutionService: SecretResolutionService = SecretResolutionService(),
    val dynamicVariableRegistry: DynamicVariableRegistry = DynamicVariableRegistry(),
) {
    suspend fun resolveSecrets(
        texts: Iterable<String>,
        externalSecrets: List<ExternalSecretVariable> = emptyList(),
        secretVariables: List<VariableEntry> = emptyList(),
        vaultUrl: String? = null,
        vaultToken: String? = null,
        mountPath: String = "secret",
        namespace: String? = null,
        awsRegion: String = "us-east-1",
        awsAccessKeyId: String? = null,
        awsSecretAccessKey: String? = null,
        awsSessionToken: String? = null,
        awsEndpointUrl: String? = null,
        awsVersionStage: String? = "AWSCURRENT",
        azureVaultName: String = "",
        azureTenantId: String = "",
        azureClientId: String = "",
        azureClientSecret: String = "",
        azureAuthorityHost: String = "https://login.microsoftonline.com",
        azureApiVersion: String = "7.4",
        azureClient: AzureKeyVaultClient? = null,
        processEnvProvider: (String) -> String? = ::getEnvironmentVariable,
    ): Map<String, String> {
        val declarations = externalSecrets.mapNotNull { sec ->
            when (sec) {
                is HashicorpVaultExternalSecret -> SecretDeclaration(
                    name = sec.name,
                    path = sec.path,
                    enabled = sec.disabled != true,
                )
                is AwsSecretsManagerExternalSecret -> SecretDeclaration(
                    name = sec.name,
                    path = sec.secretName,
                    secretId = sec.secretName,
                    targetField = sec.targetField,
                    customFilters = sec.customFilters,
                    enabled = sec.disabled != true,
                    providerType = "aws-secrets-manager",
                )
                is AzureKeyVaultExternalSecret -> SecretDeclaration(
                    name = sec.name,
                    path = sec.secretName,
                    secretId = sec.secretName,
                    url = sec.vaultName,
                    targetField = sec.targetField,
                    customFilters = sec.customFilters,
                    enabled = sec.disabled != true,
                    providerType = "azure-key-vault",
                )
                else -> null
            }
        }
        val secretVarNames = secretVariables.filter {
            it.disabled != true && it is SecretVariable
        }.map { it.name }.toSet()

        return secretResolutionService.resolveAllSecrets(
            texts = texts,
            declarations = declarations,
            secretVariableNames = secretVarNames,
            vaultUrl = vaultUrl,
            vaultToken = vaultToken,
            mountPath = mountPath,
            namespace = namespace,
            awsRegion = awsRegion,
            awsAccessKeyId = awsAccessKeyId,
            awsSecretAccessKey = awsSecretAccessKey,
            awsSessionToken = awsSessionToken,
            awsEndpointUrl = awsEndpointUrl,
            awsVersionStage = awsVersionStage,
            azureVaultName = azureVaultName,
            azureTenantId = azureTenantId,
            azureClientId = azureClientId,
            azureClientSecret = azureClientSecret,
            azureAuthorityHost = azureAuthorityHost,
            azureApiVersion = azureApiVersion,
            azureClient = azureClient,
            processEnvProvider = processEnvProvider,
        )
    }

    suspend fun executeWithSecrets(
        request: RequestDefinition,
        variableLayers: List<Map<String, String>> = emptyList(),
        externalSecrets: List<ExternalSecretVariable> = emptyList(),
        secretVariables: List<VariableEntry> = emptyList(),
        vaultUrl: String? = null,
        vaultToken: String? = null,
        mountPath: String = "secret",
        namespace: String? = null,
        awsRegion: String = "us-east-1",
        awsAccessKeyId: String? = null,
        awsSecretAccessKey: String? = null,
        awsSessionToken: String? = null,
        awsEndpointUrl: String? = null,
        awsVersionStage: String? = "AWSCURRENT",
        azureVaultName: String = "",
        azureTenantId: String = "",
        azureClientId: String = "",
        azureClientSecret: String = "",
        azureAuthorityHost: String = "https://login.microsoftonline.com",
        azureApiVersion: String = "7.4",
        azureClient: AzureKeyVaultClient? = null,
        processEnvProvider: (String) -> String? = ::getEnvironmentVariable,
    ): Flow<NetworkEvent> {
        val textsToScan = mutableListOf<String>()
        textsToScan.add(request.url)
        request.headers.forEach { textsToScan.add(it.value) }
        request.queryParams.forEach { textsToScan.add(it.value) }
        request.body?.content?.let { textsToScan.add(it) }
        val secretMap = resolveSecrets(
            texts = textsToScan,
            externalSecrets = externalSecrets,
            secretVariables = secretVariables,
            vaultUrl = vaultUrl,
            vaultToken = vaultToken,
            mountPath = mountPath,
            namespace = namespace,
            awsRegion = awsRegion,
            awsAccessKeyId = awsAccessKeyId,
            awsSecretAccessKey = awsSecretAccessKey,
            awsSessionToken = awsSessionToken,
            awsEndpointUrl = awsEndpointUrl,
            awsVersionStage = awsVersionStage,
            azureVaultName = azureVaultName,
            azureTenantId = azureTenantId,
            azureClientId = azureClientId,
            azureClientSecret = azureClientSecret,
            azureAuthorityHost = azureAuthorityHost,
            azureApiVersion = azureApiVersion,
            azureClient = azureClient,
            processEnvProvider = processEnvProvider,
        )
        val effectiveLayers = if (secretMap.isNotEmpty()) listOf(secretMap) + variableLayers else variableLayers
        return execute(request, effectiveLayers)
    }

    suspend fun executeGrpcWithSecrets(
        requestItem: GrpcRequestItem,
        protoFilePathOverride: String? = null,
        variableLayers: List<Map<String, String>> = emptyList(),
        externalSecrets: List<ExternalSecretVariable> = emptyList(),
        secretVariables: List<VariableEntry> = emptyList(),
        vaultUrl: String? = null,
        vaultToken: String? = null,
        mountPath: String = "secret",
        namespace: String? = null,
        awsRegion: String = "us-east-1",
        awsAccessKeyId: String? = null,
        awsSecretAccessKey: String? = null,
        awsSessionToken: String? = null,
        awsEndpointUrl: String? = null,
        awsVersionStage: String? = "AWSCURRENT",
        azureVaultName: String = "",
        azureTenantId: String = "",
        azureClientId: String = "",
        azureClientSecret: String = "",
        azureAuthorityHost: String = "https://login.microsoftonline.com",
        azureApiVersion: String = "7.4",
        azureClient: AzureKeyVaultClient? = null,
        processEnvProvider: (String) -> String? = ::getEnvironmentVariable,
        includePaths: List<String> = emptyList(),
    ): GrpcCallResponse {
        val textsToScan = mutableListOf<String>()
        textsToScan.add(requestItem.grpc.url)
        textsToScan.add(requestItem.grpc.message.orEmpty())
        requestItem.grpc.metadata.forEach { textsToScan.add(it.value) }
        val secretMap = resolveSecrets(
            texts = textsToScan,
            externalSecrets = externalSecrets,
            secretVariables = secretVariables,
            vaultUrl = vaultUrl,
            vaultToken = vaultToken,
            mountPath = mountPath,
            namespace = namespace,
            awsRegion = awsRegion,
            awsAccessKeyId = awsAccessKeyId,
            awsSecretAccessKey = awsSecretAccessKey,
            awsSessionToken = awsSessionToken,
            awsEndpointUrl = awsEndpointUrl,
            awsVersionStage = awsVersionStage,
            azureVaultName = azureVaultName,
            azureTenantId = azureTenantId,
            azureClientId = azureClientId,
            azureClientSecret = azureClientSecret,
            azureAuthorityHost = azureAuthorityHost,
            azureApiVersion = azureApiVersion,
            azureClient = azureClient,
            processEnvProvider = processEnvProvider,
        )
        val effectiveLayers = if (secretMap.isNotEmpty()) listOf(secretMap) + variableLayers else variableLayers
        return executeGrpc(requestItem, protoFilePathOverride, effectiveLayers, includePaths)
    }

    fun execute(
        request: RequestDefinition,
        variableLayers: List<Map<String, String>> = emptyList()
    ): Flow<NetworkEvent> = apiClient.execute(request, variableLayers)

    fun execute(
        requestItem: HttpRequestItem,
        parentHierarchy: List<FolderItem> = emptyList(),
        collectionRoot: OpenCollectionDocument? = null,
        variableLayers: List<Map<String, String>> = emptyList()
    ): Flow<NetworkEvent> {
        val request = adapter.toExecutableRequest(requestItem, parentHierarchy, collectionRoot)
        val layers = if (variableLayers.isNotEmpty()) {
            variableLayers
        } else {
            adapter.buildVariableLayers(
                requestItem = requestItem,
                parentHierarchy = parentHierarchy,
                collectionRoot = collectionRoot
            )
        }
        return apiClient.execute(request, layers)
    }

    fun execute(
        requestItem: GraphQLRequestItem,
        parentHierarchy: List<FolderItem> = emptyList(),
        collectionRoot: OpenCollectionDocument? = null,
        variableLayers: List<Map<String, String>> = emptyList()
    ): Flow<NetworkEvent> {
        val request = adapter.toExecutableRequest(requestItem, parentHierarchy, collectionRoot)
        val layers = if (variableLayers.isNotEmpty()) {
            variableLayers
        } else {
            adapter.buildVariableLayers(
                runtimeVariables = requestItem.runtime?.variables?.mapNotNull { v ->
                    (v as? RegularVariable)?.let { it.name to (it.value?.asString ?: "") }
                }?.toMap() ?: emptyMap(),
                parentHierarchy = parentHierarchy,
                collectionRoot = collectionRoot,
            )
        }
        return apiClient.execute(request, layers)
    }

    suspend fun executeGrpc(
        request: GrpcCallRequest,
        variableLayers: List<Map<String, String>> = emptyList(),
    ): GrpcCallResponse {
        val client = requireNotNull(grpcClient) { "DynamicGrpcClient is not configured on RequestExecutionService" }
        val resolvedPayload = VariableResolver.resolve(request.jsonPayload, variableLayers, removeUnresolved = false)
        val resolvedMetadata = request.metadata.map { (k, v) ->
            VariableResolver.resolve(k, variableLayers, removeUnresolved = false) to
                VariableResolver.resolve(v, variableLayers, removeUnresolved = false)
        }
        return client.executeUnary(
            request.copy(
                jsonPayload = resolvedPayload,
                metadata = resolvedMetadata,
            )
        )
    }

    suspend fun executeGrpc(
        requestItem: GrpcRequestItem,
        protoFilePathOverride: String? = null,
        variableLayers: List<Map<String, String>> = emptyList(),
        includePaths: List<String> = emptyList(),
    ): GrpcCallResponse {
        val resolvedUrl = VariableResolver.resolve(requestItem.grpc.url, variableLayers, removeUnresolved = false)
        val endpoint = parseGrpcEndpoint(resolvedUrl)
        val effectiveProtoPath = protoFilePathOverride
            ?: requestItem.grpc.protoFilePath.orEmpty()
        val resolvedPayload = VariableResolver.resolve(requestItem.grpc.message.orEmpty(), variableLayers, removeUnresolved = false)
        val resolvedMetadata = mutableListOf<Pair<String, String>>()
        for (entry in requestItem.grpc.metadata) {
            if (entry.disabled != true && entry.name.isNotBlank()) {
                resolvedMetadata.add(
                    VariableResolver.resolve(entry.name, variableLayers, removeUnresolved = false) to
                        VariableResolver.resolve(entry.value, variableLayers, removeUnresolved = false)
                )
            }
        }
        when (val auth = requestItem.grpc.auth) {
            is io.github.crustack.clapi.core.model.opencollection.AuthBearer -> {
                val token = VariableResolver.resolve(auth.token.orEmpty(), variableLayers, removeUnresolved = false)
                if (token.isNotBlank() && resolvedMetadata.none { it.first.equals("authorization", ignoreCase = true) }) {
                    resolvedMetadata.add("authorization" to "Bearer $token")
                }
            }
            is io.github.crustack.clapi.core.model.opencollection.AuthApiKey -> {
                val key = VariableResolver.resolve(auth.key.orEmpty(), variableLayers, removeUnresolved = false)
                val value = VariableResolver.resolve(auth.value.orEmpty(), variableLayers, removeUnresolved = false)
                if (key.isNotBlank() && resolvedMetadata.none { it.first.equals(key, ignoreCase = true) }) {
                    resolvedMetadata.add(key to value)
                }
            }
            else -> Unit
        }

        return executeGrpc(
            request = GrpcCallRequest(
                requestId = requestItem.info.name,
                host = endpoint.host,
                port = endpoint.port,
                useTls = endpoint.useTls,
                protoFilePath = effectiveProtoPath,
                fullMethodName = requestItem.grpc.method.orEmpty(),
                jsonPayload = resolvedPayload,
                metadata = resolvedMetadata,
                includePaths = includePaths,
            ),
            variableLayers = emptyList(),
        )
    }
}
