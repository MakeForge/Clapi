package io.github.makeforge.clapi.core.network.gcp

enum class GcpAuthMethod(val id: String) {
    SERVICE_ACCOUNT_KEY("service_account_key"),
    APPLICATION_DEFAULT_CREDENTIALS("application_default_credentials");

    companion object {
        fun fromId(id: String?): GcpAuthMethod =
            entries.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: SERVICE_ACCOUNT_KEY
    }
}
