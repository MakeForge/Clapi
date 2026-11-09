# Ideas
Various ideas for new features

## Agent Readiness
Status: TODO
- Allow to enable a custom MCP so any AI Agents can execute requests, imports, etc.

## Secrets
Status: Done
- Implement Secret Variables with the OpenCollection given schema (environments can have externalSecrets, see @opencollection_v1.0.0.json)
- For now only support type HashiCorpVault (server)
- External Secret Providers should be configurable in Clapi global Settings -> new Tab Secrets Manager
- In this new setting tab you should be able to define the credentials needed for any Secret Manager (for now only hashicorp-vault-server)
- For HashiCorp vault settings we need: Server Url, mount path (optional, defaults to "secret"), namespace (optional), vault token (password like input)
- The not security relevant settings like server url, mount path, namespace can simply be stored in plain PlatformStorage
- For vault token extend PlatformStorage to have `putSecretString`/`getSecretString` and use `ksafe` library to store it encrypted
- In Environments editor there should now be new tab `External Secrets` where user can choose the secret manager type (currently only `hashicorp-vault-server`) and add/remove secrets (see OpenCollection Hashi Corp Vault external secret type) by setting its name and path, etc.
- External Secrets should be usable just like normal variables, e.g. in Request editor it should then be possible to use the external secrets in a similar way , with syntax like `{{_secret.secretName}}`, also CTRL+Space should show below variables also any suitable secret for easier usage
- When a request that uses a secret is then executed it should fetch the external secret from its secret manager
- To fetch a secret use ktor client for HashiCorp Vault API, could look something like:
  ```kotlin
  suspend fun fetchSecret(
        vaultUrl: String,
        token: String,
        secretPath: String,
        mountPath: String = "secret",
        namespace: String? = null
    ): Map<String, String> { // TODO: if possible use concrete type instead of Map
        val cleanUrl = vaultUrl.trimEnd('/')
        val cleanPath = secretPath.trimStart('/')
        val endpoint = "$cleanUrl/v1/$mountPath/data/$cleanPath"

        val response = httpClient.get(endpoint) {
            header("X-Vault-Token", token)
            if (!namespace.isNullOrBlank()) {
                header("X-Vault-Namespace", namespace)
            }
            contentType(ContentType.Application.Json)
        }

        if (!response.status.isSuccess()) {
            throw IllegalStateException("Vault query failed [HTTP ${response.status.value}]: ${response.status.description}")
        }

        val envelope = response.body<VaultKvV2Response>()
        
        // Convert JsonObject primitives into standard String pairs
        return envelope.data.data.mapValues { (_, jsonElement) ->
            jsonElement.jsonPrimitive.content
        }
    }
  ```
- Clapi will only allow to read secrets from secret-managers, not to add secrets to the external service like HashiCorp Vault
- for qa-tests:
  - I already added @qa-tests/secret-managers-compose.yml that spins up hashicorp vault with predefined secret:  secret/payment/foo client_id="foo_client_123" client_secret="sk_live_secret_456"
  - can be fetched then via e.g. curl -H "X-Vault-Token: root-dev-token" http://127.0.0.1:8200/v1/secret/data/payment/foo 
- In OpenCollection export the HashiCorp Vault credentials will not be included, only the environment's externalSecrets with path, name etc.

## Variable secret true
Status: Done
- in OpenCollection schema a variable can have `secret` field set to true, then the variable has no inline value
- instead, if this variable is used, at runtime its value will be resolved, in order:
  1. Is there an process env variable with that exact name?
  2. Is there an environmanet externalSecret defined with that exact name?
  3. If none match, error

## gRPC Support
- import .proto files
- json payload
- support proto files referencing other proto files
- import globally available proto files (e.g. google timeranges etc.)
