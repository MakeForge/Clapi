package io.github.crustack.clapi.core.network.keeper

object KeeperRecordFilter {

    fun filterRecords(
        records: List<KeeperRecordSummary>,
        recordTitle: String,
        customFilters: Map<String, String> = emptyMap(),
    ): List<KeeperRecordSummary> {
        return records.filter { matchesFilter(it, recordTitle, customFilters) }
    }

    fun matchesFilter(
        record: KeeperRecordSummary,
        recordTitle: String,
        customFilters: Map<String, String> = emptyMap(),
    ): Boolean {
        val trimmedTitle = recordTitle.trim()
        if (trimmedTitle.isNotEmpty()) {
            val titleMatches = record.title.equals(trimmedTitle, ignoreCase = true) ||
                record.recordUid.equals(trimmedTitle, ignoreCase = true)
            if (!titleMatches) {
                return false
            }
        }

        if (customFilters.isEmpty()) {
            return true
        }

        return customFilters.all { (key, expectedValue) ->
            matchesSingleFilter(record, key, expectedValue)
        }
    }

    private fun matchesSingleFilter(
        record: KeeperRecordSummary,
        key: String,
        expectedValue: String,
    ): Boolean {
        val normalizedKey = key.trim().lowercase()
        val trimmedExpected = expectedValue.trim()

        when (normalizedKey) {
            "title", "name" -> return record.title.equals(trimmedExpected, ignoreCase = true)
            "type" -> return record.type.equals(trimmedExpected, ignoreCase = true)
            "uid", "recorduid" -> return record.recordUid.equals(trimmedExpected, ignoreCase = true)
        }

        val allFields = record.fields + record.customFields
        return allFields.any { field ->
            val labelMatches = field.label?.equals(key.trim(), ignoreCase = true) == true
            val typeMatches = field.type.equals(key.trim(), ignoreCase = true)

            if (labelMatches || typeMatches) {
                if (field.values.any { it.equals(trimmedExpected, ignoreCase = true) }) {
                    return@any true
                }
            }

            // Dotted host property match (e.g. host.hostname or host.port)
            if (field.type.equals("host", ignoreCase = true)) {
                if (normalizedKey == "host.hostname" || normalizedKey == "hostname") {
                    return@any field.values.any { valStr ->
                        valStr.contains("\"hostName\":\"$trimmedExpected\"", ignoreCase = true) ||
                            valStr.contains("\"hostName\": \"$trimmedExpected\"", ignoreCase = true) ||
                            valStr.equals(trimmedExpected, ignoreCase = true)
                    }
                }
                if (normalizedKey == "host.port" || normalizedKey == "port") {
                    return@any field.values.any { valStr ->
                        valStr.contains("\"port\":\"$trimmedExpected\"", ignoreCase = true) ||
                            valStr.contains("\"port\": \"$trimmedExpected\"", ignoreCase = true) ||
                            valStr.equals(trimmedExpected, ignoreCase = true)
                    }
                }
            }

            false
        }
    }

    fun extractTargetFieldValue(
        record: KeeperRecordSummary,
        targetField: String,
    ): String? {
        val normalizedTarget = targetField.trim()
        val lowerTarget = normalizedTarget.lowercase()

        // 1. Password
        if (lowerTarget.isEmpty() || lowerTarget == "password") {
            if (!record.password.isNullOrEmpty()) {
                return record.password
            }
        }

        // 2. Login / Username
        if (lowerTarget in setOf("login", "user", "username")) {
            val loginField = (record.fields + record.customFields).find {
                it.type.equals("login", ignoreCase = true) || it.label?.equals("login", ignoreCase = true) == true
            }
            if (loginField != null && loginField.values.isNotEmpty()) {
                return loginField.values.first()
            }
        }

        // 3. Match by field label (case-insensitive)
        val allFields = record.fields + record.customFields
        val fieldByLabel = allFields.find { it.label?.equals(normalizedTarget, ignoreCase = true) == true }
        if (fieldByLabel != null && fieldByLabel.values.isNotEmpty()) {
            return fieldByLabel.values.first()
        }

        // 4. Match by field type (case-insensitive)
        val fieldByType = allFields.find { it.type.equals(lowerTarget, ignoreCase = true) }
        if (fieldByType != null && fieldByType.values.isNotEmpty()) {
            // If it's a host field and the target was just "host", return the full string or parse
            return fieldByType.values.first()
        }

        // 5. Nested dotted property (e.g. host.hostName or host.port)
        if (lowerTarget.startsWith("host.")) {
            val subProp = lowerTarget.removePrefix("host.").trim()
            val hostField = allFields.find { it.type.equals("host", ignoreCase = true) }
            if (hostField != null && hostField.values.isNotEmpty()) {
                val hostRaw = hostField.values.first()
                val extracted = extractJsonSubProperty(hostRaw, subProp)
                if (extracted != null) return extracted
            }
        }

        // 6. Keeper Notation fallback (e.g. "field/login", "custom_field/clientId", "keeper://.../field/password")
        if (normalizedTarget.contains("/")) {
            val parts = normalizedTarget.removePrefix("keeper://").trim('/').split('/')
            val fieldCategory = if (parts.size >= 2) parts[parts.size - 2].lowercase() else ""
            val fieldName = parts.last()
            val candidateFields = when (fieldCategory) {
                "custom_field", "custom" -> record.customFields
                "field" -> record.fields
                else -> allFields
            }
            val match = candidateFields.find {
                it.label?.equals(fieldName, ignoreCase = true) == true ||
                    it.type.equals(fieldName, ignoreCase = true)
            }
            if (match != null && match.values.isNotEmpty()) {
                return match.values.first()
            }
            if (fieldName.equals("password", ignoreCase = true) && !record.password.isNullOrEmpty()) {
                return record.password
            }
        }

        return null
    }

    private fun extractJsonSubProperty(json: String, propName: String): String? {
        // Simple extraction for hostName or port from JSON string like {"hostName": "clapi.api.com", "port": "1234"}
        val regex = Regex("\"${propName}\"\\s*:\\s*\"?([^\",}]+)\"?", RegexOption.IGNORE_CASE)
        val match = regex.find(json)
        return match?.groupValues?.get(1)?.trim()
    }
}
