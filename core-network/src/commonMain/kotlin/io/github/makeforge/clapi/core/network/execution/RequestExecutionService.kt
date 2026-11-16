package io.github.makeforge.clapi.core.network.execution

import io.github.makeforge.clapi.core.model.RequestDefinition
import io.github.makeforge.clapi.core.model.opencollection.AwsSecretsManagerExternalSecret
import io.github.makeforge.clapi.core.model.opencollection.AzureKeyVaultExternalSecret
import io.github.makeforge.clapi.core.model.opencollection.DynamicVariable
import io.github.makeforge.clapi.core.model.opencollection.Environment
import io.github.makeforge.clapi.core.model.opencollection.ExternalSecretVariable
import io.github.makeforge.clapi.core.model.opencollection.FolderItem
import io.github.makeforge.clapi.core.model.opencollection.GcpSecretsManagerExternalSecret
import io.github.makeforge.clapi.core.model.opencollection.GraphQLRequestItem
import io.github.makeforge.clapi.core.model.opencollection.GrpcRequestItem
import io.github.makeforge.clapi.core.model.opencollection.HashicorpVaultExternalSecret
import io.github.makeforge.clapi.core.model.opencollection.HttpRequestItem
import io.github.makeforge.clapi.core.model.opencollection.KeeperSecurityExternalSecret
import io.github.makeforge.clapi.core.model.opencollection.OpenCollectionDocument
import io.github.makeforge.clapi.core.model.opencollection.RegularVariable
import io.github.makeforge.clapi.core.model.opencollection.SecretVariable
import io.github.makeforge.clapi.core.model.opencollection.VariableEntry
import io.github.makeforge.clapi.core.network.ApiClient
import io.github.makeforge.clapi.core.network.NetworkEvent
import io.github.makeforge.clapi.core.network.VariableResolver
import io.github.makeforge.clapi.core.network.azure.AzureKeyVaultClient
import io.github.makeforge.clapi.core.network.dynamicvar.DynamicVariableContext
import io.github.makeforge.clapi.core.network.dynamicvar.DynamicVariableRegistry
import io.github.makeforge.clapi.core.network.dynamicvar.DynamicVariableResolutionException
import io.github.makeforge.clapi.core.network.gcp.GcpSecretManagerClient
import io.github.makeforge.clapi.core.network.getEnvironmentVariable
import io.github.makeforge.clapi.core.network.grpc.DynamicGrpcClient
import io.github.makeforge.clapi.core.network.grpc.GrpcCallRequest
import io.github.makeforge.clapi.core.network.grpc.GrpcCallResponse
import io.github.makeforge.clapi.core.network.grpc.parseGrpcEndpoint
import io.github.makeforge.clapi.core.network.keeper.KeeperClient
import io.github.makeforge.clapi.core.network.vault.AwsProviderConfig
import io.github.makeforge.clapi.core.network.vault.AzureProviderConfig
import io.github.makeforge.clapi.core.network.vault.GcpProviderConfig
import io.github.makeforge.clapi.core.network.vault.KeeperProviderConfig
import io.github.makeforge.clapi.core.network.vault.SecretDeclaration
import io.github.makeforge.clapi.core.network.vault.SecretResolutionContext
import io.github.makeforge.clapi.core.network.vault.SecretResolutionService
import io.github.makeforge.clapi.core.network.vault.VaultProviderConfig
import io.github.makeforge.clapi.core.scripting.RequestContext
import kotlinx.coroutines.flow.Flow

class RequestExecutionService(
    private val apiClient: ApiClient,
    private val adapter: HttpExecutionAdapter = DefaultHttpExecutionAdapter,
    private val grpcClient: DynamicGrpcClient? = null,
    private val secretResolutionService: SecretResolutionService = SecretResolutionService(),
    val dynamicVariableRegistry: DynamicVariableRegistry = DynamicVariableRegistry(),
) {
    private fun toDeclarations(externalSecrets: List<ExternalSecretVariable>): List<SecretDeclaration> =
        externalSecrets.mapNotNull { sec ->
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
                is GcpSecretsManagerExternalSecret -> SecretDeclaration(
                    name = sec.name,
                    path = sec.secretId,
                    secretId = sec.secretId,
                    url = sec.projectId,
                    targetField = sec.targetField,
                    customFilters = sec.customFilters,
                    enabled = sec.disabled != true,
                    providerType = "gcp-secrets-manager",
                    version = sec.version,
                )
                is KeeperSecurityExternalSecret -> SecretDeclaration(
                    name = sec.name,
                    path = sec.recordTitle,
                    recordTitle = sec.recordTitle,
                    targetField = sec.targetField,
                    customFilters = sec.customFilters,
                    enabled = sec.disabled != true,
                    providerType = "keeper-security-cloud",
                )
                else -> null
            }
        }

    private fun extractSecretVarNames(secretVariables: List<VariableEntry>): Set<String> =
        secretVariables.filter {
            it.disabled != true && it is SecretVariable
        }.map { it.name }.toSet()

    suspend fun resolveSecrets(
        texts: Iterable<String>,
        externalSecrets: List<ExternalSecretVariable> = emptyList(),
        secretVariables: List<VariableEntry> = emptyList(),
        context: SecretResolutionContext,
    ): Map<String, String> {
        val declarations = if (externalSecrets.isNotEmpty()) {
            toDeclarations(externalSecrets)
        } else {
            context.declarations
        }
        val secretVarNames = if (secretVariables.isNotEmpty()) {
            extractSecretVarNames(secretVariables)
        } else {
            context.secretVariableNames
        }
        return secretResolutionService.resolveAllSecrets(
            texts = texts,
            context = context.copy(
                declarations = declarations,
                secretVariableNames = secretVarNames,
            ),
        )
    }

    suspend fun resolveSecrets(
        texts: Iterable<String>,
        externalSecrets: List<ExternalSecretVariable> = emptyList(),
        secretVariables: List<VariableEntry> = emptyList(),
        variableValues: Map<String, String> = emptyMap(),
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
        gcpProjectId: String = "",
        gcpAccessToken: String = "",
        gcpEndpointUrl: String = "https://secretmanager.googleapis.com",
        gcpClient: GcpSecretManagerClient? = null,
        keeperConfigJson: String = "",
        keeperClient: KeeperClient? = null,
        processEnvProvider: (String) -> String? = ::getEnvironmentVariable,
    ): Map<String, String> = resolveSecrets(
        texts = texts,
        externalSecrets = externalSecrets,
        secretVariables = secretVariables,
        context = SecretResolutionContext(
            vault = VaultProviderConfig(
                url = vaultUrl,
                token = vaultToken,
                mountPath = mountPath,
                namespace = namespace,
            ),
            aws = AwsProviderConfig(
                region = awsRegion,
                accessKeyId = awsAccessKeyId,
                secretAccessKey = awsSecretAccessKey,
                sessionToken = awsSessionToken,
                endpointUrl = awsEndpointUrl,
                versionStage = awsVersionStage,
            ),
            azure = AzureProviderConfig(
                vaultName = azureVaultName,
                tenantId = azureTenantId,
                clientId = azureClientId,
                clientSecret = azureClientSecret,
                authorityHost = azureAuthorityHost,
                apiVersion = azureApiVersion,
                clientOverride = azureClient,
            ),
            gcp = GcpProviderConfig(
                projectId = gcpProjectId,
                accessToken = gcpAccessToken,
                endpointUrl = gcpEndpointUrl,
                clientOverride = gcpClient,
            ),
            keeper = KeeperProviderConfig(
                configJson = keeperConfigJson,
                clientOverride = keeperClient,
            ),
            variableValues = variableValues,
            processEnvProvider = processEnvProvider,
        ),
    )

    suspend fun executeWithSecrets(
        request: RequestDefinition,
        variableLayers: List<Map<String, String>> = emptyList(),
        externalSecrets: List<ExternalSecretVariable> = emptyList(),
        secretVariables: List<VariableEntry> = emptyList(),
        context: SecretResolutionContext,
    ): Flow<NetworkEvent> {
        val textsToScan = request.extractTemplateStrings()
        val effectiveContext = if (context.variableLayers.isEmpty() && variableLayers.isNotEmpty()) {
            context.copy(variableLayers = variableLayers)
        } else {
            context
        }
        val secretMap = resolveSecrets(
            texts = textsToScan,
            externalSecrets = externalSecrets,
            secretVariables = secretVariables,
            context = effectiveContext,
        )
        val effectiveLayers = if (secretMap.isNotEmpty()) listOf(secretMap) + variableLayers else variableLayers
        return execute(request, effectiveLayers)
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
        gcpProjectId: String = "",
        gcpAccessToken: String = "",
        gcpEndpointUrl: String = "https://secretmanager.googleapis.com",
        gcpClient: GcpSecretManagerClient? = null,
        keeperConfigJson: String = "",
        keeperClient: KeeperClient? = null,
        processEnvProvider: (String) -> String? = ::getEnvironmentVariable,
    ): Flow<NetworkEvent> {
        val mergedVariables = buildMap { for (layer in variableLayers.asReversed()) putAll(layer) }
        return executeWithSecrets(
            request = request,
            variableLayers = variableLayers,
            externalSecrets = externalSecrets,
            secretVariables = secretVariables,
            context = SecretResolutionContext(
                vault = VaultProviderConfig(
                    url = vaultUrl,
                    token = vaultToken,
                    mountPath = mountPath,
                    namespace = namespace,
                ),
                aws = AwsProviderConfig(
                    region = awsRegion,
                    accessKeyId = awsAccessKeyId,
                    secretAccessKey = awsSecretAccessKey,
                    sessionToken = awsSessionToken,
                    endpointUrl = awsEndpointUrl,
                    versionStage = awsVersionStage,
                ),
                azure = AzureProviderConfig(
                    vaultName = azureVaultName,
                    tenantId = azureTenantId,
                    clientId = azureClientId,
                    clientSecret = azureClientSecret,
                    authorityHost = azureAuthorityHost,
                    apiVersion = azureApiVersion,
                    clientOverride = azureClient,
                ),
                gcp = GcpProviderConfig(
                    projectId = gcpProjectId,
                    accessToken = gcpAccessToken,
                    endpointUrl = gcpEndpointUrl,
                    clientOverride = gcpClient,
                ),
                keeper = KeeperProviderConfig(
                    configJson = keeperConfigJson,
                    clientOverride = keeperClient,
                ),
                variableLayers = variableLayers,
                variableValues = mergedVariables,
                processEnvProvider = processEnvProvider,
            ),
        )
    }

    suspend fun executeGrpcWithSecrets(
        requestItem: GrpcRequestItem,
        protoFilePathOverride: String? = null,
        variableLayers: List<Map<String, String>> = emptyList(),
        externalSecrets: List<ExternalSecretVariable> = emptyList(),
        secretVariables: List<VariableEntry> = emptyList(),
        context: SecretResolutionContext,
        includePaths: List<String> = emptyList(),
    ): GrpcCallResponse {
        val textsToScan = requestItem.extractTemplateStrings()
        val effectiveContext = if (context.variableLayers.isEmpty() && variableLayers.isNotEmpty()) {
            context.copy(variableLayers = variableLayers)
        } else {
            context
        }
        val secretMap = resolveSecrets(
            texts = textsToScan,
            externalSecrets = externalSecrets,
            secretVariables = secretVariables,
            context = effectiveContext,
        )
        val effectiveLayers = if (secretMap.isNotEmpty()) listOf(secretMap) + variableLayers else variableLayers
        return executeGrpc(requestItem, protoFilePathOverride, effectiveLayers, includePaths)
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
        gcpProjectId: String = "",
        gcpAccessToken: String = "",
        gcpEndpointUrl: String = "https://secretmanager.googleapis.com",
        gcpClient: GcpSecretManagerClient? = null,
        keeperConfigJson: String = "",
        keeperClient: KeeperClient? = null,
        processEnvProvider: (String) -> String? = ::getEnvironmentVariable,
        includePaths: List<String> = emptyList(),
    ): GrpcCallResponse {
        val mergedVariables = buildMap { for (layer in variableLayers.asReversed()) putAll(layer) }
        return executeGrpcWithSecrets(
            requestItem = requestItem,
            protoFilePathOverride = protoFilePathOverride,
            variableLayers = variableLayers,
            externalSecrets = externalSecrets,
            secretVariables = secretVariables,
            includePaths = includePaths,
            context = SecretResolutionContext(
                vault = VaultProviderConfig(
                    url = vaultUrl,
                    token = vaultToken,
                    mountPath = mountPath,
                    namespace = namespace,
                ),
                aws = AwsProviderConfig(
                    region = awsRegion,
                    accessKeyId = awsAccessKeyId,
                    secretAccessKey = awsSecretAccessKey,
                    sessionToken = awsSessionToken,
                    endpointUrl = awsEndpointUrl,
                    versionStage = awsVersionStage,
                ),
                azure = AzureProviderConfig(
                    vaultName = azureVaultName,
                    tenantId = azureTenantId,
                    clientId = azureClientId,
                    clientSecret = azureClientSecret,
                    authorityHost = azureAuthorityHost,
                    apiVersion = azureApiVersion,
                    clientOverride = azureClient,
                ),
                gcp = GcpProviderConfig(
                    projectId = gcpProjectId,
                    accessToken = gcpAccessToken,
                    endpointUrl = gcpEndpointUrl,
                    clientOverride = gcpClient,
                ),
                keeper = KeeperProviderConfig(
                    configJson = keeperConfigJson,
                    clientOverride = keeperClient,
                ),
                variableLayers = variableLayers,
                variableValues = mergedVariables,
                processEnvProvider = processEnvProvider,
            ),
        )
    }

    fun execute(
        request: RequestDefinition,
        variableLayers: List<Map<String, String>> = emptyList(),
        userDynamicVariables: List<DynamicVariable> = emptyList(),
    ): Flow<NetworkEvent> {
        val effectiveLayers = if (userDynamicVariables.isNotEmpty()) {
            val resolvedDynMap = resolveUserDynamicTokensSync(request, variableLayers, userDynamicVariables)
            if (resolvedDynMap.isNotEmpty()) listOf(resolvedDynMap) + variableLayers else variableLayers
        } else {
            variableLayers
        }
        return apiClient.execute(request, effectiveLayers)
    }

    private fun resolveUserDynamicTokensSync(
        request: RequestDefinition,
        variableLayers: List<Map<String, String>>,
        userDynamicVariables: List<DynamicVariable>,
    ): Map<String, String> {
        val activeUserDynMap = userDynamicVariables
            .filter { it.disabled != true && it.name.isNotBlank() }
            .associateBy { it.name.trim() }
        if (activeUserDynMap.isEmpty()) return emptyMap()

        val reqCtx = RequestContext(
            url = VariableResolver.resolve(request.url, variableLayers, removeUnresolved = false),
            method = request.method.name,
            headers = request.headers.filter { it.enabled }.associate {
                VariableResolver.resolve(it.key, variableLayers, removeUnresolved = false) to
                    VariableResolver.resolve(it.value, variableLayers, removeUnresolved = false)
            },
            name = request.name,
        )

        val tokens = dynamicVariableRegistry.extractTokens(request.extractTemplateStrings())
        val resolved = LinkedHashMap<String, String>()
        for (rawToken in tokens) {
            val parsed = VariableResolver.parseVariableToken(rawToken)
            val dynVar = activeUserDynMap[parsed.name] ?: continue
            if (parsed.hasMalformedSyntax) {
                throw DynamicVariableResolutionException(
                    "Malformed dynamic variable invocation '{{$rawToken}}' for variable '${dynVar.name}'"
                )
            }
            val scriptCtx = VariableResolver.buildScriptContext(dynVar, parsed.args, reqCtx)
            val execResult = VariableResolver.defaultDynamicEngine.evaluateDynamicVariable(dynVar.script, scriptCtx)
            if (!execResult.success) {
                throw DynamicVariableResolutionException(
                    "Dynamic variable '${dynVar.name}' execution failed: ${execResult.errorMessage ?: "Unknown error"}"
                )
            }
            resolved[rawToken.trim()] = execResult.returnValue ?: ""
        }
        return resolved
    }

    fun execute(
        requestItem: HttpRequestItem,
        parentHierarchy: List<FolderItem> = emptyList(),
        collectionRoot: OpenCollectionDocument? = null,
        variableLayers: List<Map<String, String>> = emptyList(),
        environment: Environment? = null,
    ): Flow<NetworkEvent> {
        val request = adapter.toExecutableRequest(requestItem, parentHierarchy, collectionRoot)
        val layers = if (variableLayers.isNotEmpty()) {
            variableLayers
        } else {
            adapter.buildVariableLayers(
                requestItem = requestItem,
                environment = environment,
                parentHierarchy = parentHierarchy,
                collectionRoot = collectionRoot,
            )
        }
        val userDynamicVariables = adapter.collectDynamicVariables(
            requestItem = requestItem,
            environment = environment,
            parentHierarchy = parentHierarchy,
            collectionRoot = collectionRoot,
        )
        return execute(request, layers, userDynamicVariables)
    }

    fun execute(
        requestItem: GraphQLRequestItem,
        parentHierarchy: List<FolderItem> = emptyList(),
        collectionRoot: OpenCollectionDocument? = null,
        variableLayers: List<Map<String, String>> = emptyList(),
        environment: Environment? = null,
    ): Flow<NetworkEvent> {
        val request = adapter.toExecutableRequest(requestItem, parentHierarchy, collectionRoot)
        val runtimeVars = requestItem.runtime?.variables?.mapNotNull { v ->
            (v as? RegularVariable)?.let { it.name to (it.value?.asString ?: "") }
        }?.toMap() ?: emptyMap()
        val layers = if (variableLayers.isNotEmpty()) {
            variableLayers
        } else {
            adapter.buildVariableLayers(
                runtimeVariables = runtimeVars,
                environment = environment,
                parentHierarchy = parentHierarchy,
                collectionRoot = collectionRoot,
            )
        }
        val userDynamicVariables = adapter.collectDynamicVariables(
            runtimeVariables = runtimeVars,
            environment = environment,
            parentHierarchy = parentHierarchy,
            collectionRoot = collectionRoot,
        )
        return execute(request, layers, userDynamicVariables)
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
            is io.github.makeforge.clapi.core.model.opencollection.AuthBearer -> {
                val token = VariableResolver.resolve(auth.token.orEmpty(), variableLayers, removeUnresolved = false)
                if (token.isNotBlank() && resolvedMetadata.none { it.first.equals("authorization", ignoreCase = true) }) {
                    resolvedMetadata.add("authorization" to "Bearer $token")
                }
            }
            is io.github.makeforge.clapi.core.model.opencollection.AuthApiKey -> {
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
