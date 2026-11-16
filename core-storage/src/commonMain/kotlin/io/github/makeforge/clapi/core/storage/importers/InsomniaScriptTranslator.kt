package io.github.makeforge.clapi.core.storage.importers

/**
 * Translates Insomnia v5 (`insomnia.*`) pre-request and after-response scripts into Clapi (`clapi.*`) scripts.
 */
object InsomniaScriptTranslator {

    /**
     * Converts Insomnia `insomnia.*` API calls to the Clapi `clapi.*` equivalent.
     *
     * Also applies Postman `pm.*` fallback conversions because Insomnia v5 scripts
     * may contain `pm.*` aliases.
     */
    fun convertScript(script: String): String {
        if (script.isBlank()) return script
        return script
            // ── Response chain assertions (MUST precede insomnia.response generic replacement) ──
            .replace("insomnia.response.to.have.status(", "clapi.response.statusIs(")
            .replace("insomnia.response.to.have.header(", "clapi.response.hasHeader(")
            .replace(Regex("""\binsomnia\.response\.to\.be\.ok\b"""), "clapi.response.statusOk()")

            // ── Execution control ────────────────────────────────────────────
            .replace(
                Regex("""\binsomnia\.execution\.setNextRequest\s*\("""),
                "clapi.execution.setNextRequest(",
            )
            .replace(
                Regex("""\binsomnia\.execution\.skipRequest\s*\("""),
                "clapi.execution.skipRequest(",
            )
            .replace("insomnia.sendRequest(", "clapi.sendRequest(")

            // ── Folder scoping ───────────────────────────────────────────────
            .replace("insomnia.parentFolders.getById(", "clapi.folder.getById(")
            .replace("insomnia.parentFolders.getByName(", "clapi.folder.getByName(")
            .replace("insomnia.parentFolders.get(", "clapi.folder.get(")
            .replace("insomnia.parentFolders", "clapi.folder")

            // ── Assertion & Test APIs ────────────────────────────────────────
            .replace("insomnia.test(", "clapi.test(")
            .replace("insomnia.expect(", "clapi.expect(")

            // ── Variable Scopes & HTTP Context ───────────────────────────────
            .replace("insomnia.baseEnvironment.", "clapi.collectionVariables.")
            .replace("insomnia.baseEnvironment", "clapi.collectionVariables")
            .replace("insomnia.collectionVariables.", "clapi.collectionVariables.")
            .replace("insomnia.collectionVariables", "clapi.collectionVariables")
            .replace("insomnia.environment.", "clapi.environment.")
            .replace("insomnia.environment", "clapi.environment")
            .replace("insomnia.variables.", "clapi.variables.")
            .replace("insomnia.variables", "clapi.variables")
            .replace("insomnia.globals.", "clapi.globals.")
            .replace("insomnia.globals", "clapi.globals")
            .replace("insomnia.response.", "clapi.response.")
            .replace("insomnia.response", "clapi.response")
            .replace("insomnia.request.", "clapi.request.")
            .replace("insomnia.request", "clapi.request")
            .replace("insomnia.cookies.", "clapi.cookies.")
            .replace("insomnia.cookies", "clapi.cookies")
            .replace("insomnia.info.", "clapi.info.")
            .replace("insomnia.info", "clapi.info")
            .replace("insomnia.iterationData.", "clapi.iterationData.")
            .replace("insomnia.iterationData", "clapi.iterationData")

            // ── Also handle pm.* calls if present inside Insomnia scripts ────
            .replace("pm.response.to.have.status(", "clapi.response.statusIs(")
            .replace("pm.response.to.have.header(", "clapi.response.hasHeader(")
            .replace(Regex("""\bpm\.response\.to\.be\.ok\b"""), "clapi.response.statusOk()")
            .replace("pm.sendRequest(", "clapi.sendRequest(")
            .replace("pm.test(", "clapi.test(")
            .replace("pm.expect(", "clapi.expect(")
            .replace("pm.response", "clapi.response")
            .replace("pm.request", "clapi.request")
            .replace("pm.environment", "clapi.environment")
            .replace("pm.globals", "clapi.globals")
            .replace("pm.collectionVariables", "clapi.collectionVariables")
            .replace("pm.variables", "clapi.variables")
            .replace("pm.cookies", "clapi.cookies")
    }
}
