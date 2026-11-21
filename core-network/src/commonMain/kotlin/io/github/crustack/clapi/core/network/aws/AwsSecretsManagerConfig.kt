package io.github.crustack.clapi.core.network.aws

data class AwsSecretsManagerConfig(
    val secretId: String,
    val accessKey: String,
    val secretKey: String,
    val region: String = "us-east-1",
    val endpointUrl: String? = null,
    val sessionToken: String? = null,
    val versionStage: String? = "AWSCURRENT",
)
