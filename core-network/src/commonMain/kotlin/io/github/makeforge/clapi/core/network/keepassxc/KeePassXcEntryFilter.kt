package io.github.makeforge.clapi.core.network.keepassxc

object KeePassXcEntryFilter {

    @Suppress("UNCHECKED_CAST")
    fun parseEntries(response: Map<String, Any>): List<KeePassXcEntry> {
        val entriesRaw = response["entries"] as? List<Map<String, Any>> ?: return emptyList()
        return entriesRaw.map { map ->
            val name = map["name"]?.toString().orEmpty()
            val login = map["login"]?.toString().orEmpty()
            val password = map["password"]?.toString()
            val uuid = map["uuid"]?.toString().orEmpty()
            val group = map["group"]?.toString()
            val totp = map["totp"]?.toString()
            val stringFieldsRaw = map["stringFields"] as? Map<String, Any> ?: emptyMap()
            val stringFields = stringFieldsRaw.mapValues { it.value.toString() }

            KeePassXcEntry(
                name = name,
                login = login,
                password = password,
                uuid = uuid,
                group = group,
                stringFields = stringFields,
                totp = totp,
            )
        }
    }

    fun filterEntries(
        entries: List<KeePassXcEntry>,
        customFilters: Map<String, String>,
    ): List<KeePassXcEntry> {
        if (customFilters.isEmpty()) return entries
        return entries.filter { entry ->
            customFilters.all { (filterKey, filterValue) ->
                matchesField(entry, filterKey, filterValue)
            }
        }
    }

    fun parseAndFilter(
        response: Map<String, Any>,
        customFilters: Map<String, String>,
    ): List<KeePassXcEntry> {
        return filterEntries(parseEntries(response), customFilters)
    }

    fun matchesField(entry: KeePassXcEntry, key: String, expectedValue: String): Boolean {
        val normalizedKey = key.trim().lowercase()
        val actualValue = when (normalizedKey) {
            "name", "title" -> entry.name
            "login", "user", "username" -> entry.login
            "uuid" -> entry.uuid
            "group" -> entry.group.orEmpty()
            else -> entry.stringFields[key] ?: entry.stringFields[normalizedKey].orEmpty()
        }
        return actualValue.equals(expectedValue.trim(), ignoreCase = true)
    }
}
