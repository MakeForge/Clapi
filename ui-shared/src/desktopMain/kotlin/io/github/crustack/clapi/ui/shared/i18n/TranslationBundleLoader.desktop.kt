package io.github.crustack.clapi.ui.shared.i18n

actual fun loadTranslationBundle(languageCode: String): String? {
    val path = "/i18n/$languageCode.json"
    return runCatching {
        object {}.javaClass.getResourceAsStream(path)
            ?.bufferedReader()
            ?.use { it.readText() }
    }.getOrNull()
}
