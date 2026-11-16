package io.github.makeforge.clapi.core.network.gcp

import kotlinx.serialization.Serializable

/**
 * Configuration for accessing secrets in Google Cloud Platform (GCP) Secret Manager.
 *
 * @param projectId The GCP project ID or project number (e.g. "my-test-project" or "123456789012").
 * @param secretId The secret resource name (e.g. "payment-credentials", "stripe-api-key").
 * @param version Target version snapshot (e.g. "latest", "1", "2") or version alias. Defaults to "latest".
 * @param propertyKey Optional JSON property key to extract from multi-value secret payload.
 * @param accessToken Bearer access token for GCP Secret Manager API authentication.
 * @param endpointUrl Base endpoint URL for Secret Manager REST API (default "https://secretmanager.googleapis.com").
 */
data class GcpSecretManagerConfig(
    val projectId: String,
    val secretId: String,
    val version: String = "latest",
    val propertyKey: String? = null,
    val accessToken: String,
    val endpointUrl: String = "https://secretmanager.googleapis.com",
)

@Serializable
internal data class GcpSecretPayload(
    val data: String? = null
)

@Serializable
internal data class GcpAccessSecretVersionResponse(
    val name: String? = null,
    val payload: GcpSecretPayload? = null
)
