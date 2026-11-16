package io.github.makeforge.clapi.core.network.execution

import io.github.makeforge.clapi.core.model.RequestDefinition
import io.github.makeforge.clapi.core.model.opencollection.AuthApiKey
import io.github.makeforge.clapi.core.model.opencollection.AuthBasic
import io.github.makeforge.clapi.core.model.opencollection.AuthBearer
import io.github.makeforge.clapi.core.model.opencollection.AuthOAuth2
import io.github.makeforge.clapi.core.model.opencollection.GrpcRequestItem
import io.github.makeforge.clapi.core.network.grpc.GrpcCallRequest

/**
 * Extracts all candidate strings from a [RequestDefinition] that may contain `{{...}}` template
 * tokens (for secret scanning and dynamic variable resolution).
 */
fun RequestDefinition.extractTemplateStrings(): List<String> = buildList {
    add(url)
    for (header in headers) {
        if (header.enabled) {
            add(header.key)
            add(header.value)
        }
    }
    for (param in queryParams) {
        if (param.enabled) {
            add(param.key)
            add(param.value)
        }
    }
    for (cookie in cookies) {
        if (cookie.enabled) {
            add(cookie.key)
            add(cookie.value)
        }
    }
    for (paramValue in auth.params.values) {
        if (paramValue.isNotBlank()) {
            add(paramValue)
        }
    }
    body.content?.takeIf { it.isNotBlank() }?.let { add(it) }
    for (entry in body.formEntries) {
        if (entry.enabled) {
            add(entry.key)
            add(entry.value)
        }
    }
    for (entry in body.formDataEntries) {
        if (entry.enabled) {
            add(entry.key)
            add(entry.value)
            if (entry.description.isNotBlank()) {
                add(entry.description)
            }
        }
    }
    body.graphQl?.let { gql ->
        if (gql.query.isNotBlank()) add(gql.query)
        gql.variablesJson?.takeIf { it.isNotBlank() }?.let { add(it) }
        gql.operationName?.takeIf { it.isNotBlank() }?.let { add(it) }
    }
}

/**
 * Extracts all candidate strings from a [GrpcCallRequest] that may contain `{{...}}` template
 * tokens.
 */
fun GrpcCallRequest.extractTemplateStrings(): List<String> = buildList {
    add(host)
    add(fullMethodName)
    if (jsonPayload.isNotBlank()) {
        add(jsonPayload)
    }
    for ((k, v) in metadata) {
        add(k)
        add(v)
    }
}

/**
 * Extracts all candidate strings from a [GrpcRequestItem] that may contain `{{...}}` template
 * tokens.
 */
fun GrpcRequestItem.extractTemplateStrings(): List<String> = buildList {
    add(grpc.url)
    grpc.method?.takeIf { it.isNotBlank() }?.let { add(it) }
    grpc.message?.takeIf { it.isNotBlank() }?.let { add(it) }
    for (entry in grpc.metadata) {
        if (entry.disabled != true) {
            add(entry.name)
            add(entry.value)
        }
    }
    when (val auth = grpc.auth) {
        is AuthBearer -> auth.token?.takeIf { it.isNotBlank() }?.let { add(it) }
        is AuthBasic -> {
            auth.username?.takeIf { it.isNotBlank() }?.let { add(it) }
            auth.password?.takeIf { it.isNotBlank() }?.let { add(it) }
        }
        is AuthApiKey -> {
            auth.key?.takeIf { it.isNotBlank() }?.let { add(it) }
            auth.value?.takeIf { it.isNotBlank() }?.let { add(it) }
        }
        is AuthOAuth2 -> {
            auth.accessTokenUrl?.takeIf { it.isNotBlank() }?.let { add(it) }
            auth.refreshTokenUrl?.takeIf { it.isNotBlank() }?.let { add(it) }
            auth.authorizationUrl?.takeIf { it.isNotBlank() }?.let { add(it) }
            auth.credentials?.clientId?.takeIf { it.isNotBlank() }?.let { add(it) }
            auth.credentials?.clientSecret?.takeIf { it.isNotBlank() }?.let { add(it) }
            auth.resourceOwner?.username?.takeIf { it.isNotBlank() }?.let { add(it) }
            auth.resourceOwner?.password?.takeIf { it.isNotBlank() }?.let { add(it) }
        }
        else -> Unit
    }
}
