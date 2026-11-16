package io.github.makeforge.clapi.core.storage.importers

import io.github.makeforge.clapi.core.model.opencollection.FolderItem
import io.github.makeforge.clapi.core.model.opencollection.HttpRequestItem
import io.github.makeforge.clapi.core.model.opencollection.ScriptType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class InsomniaScriptImporterTest {

    @Test
    fun `imports and translates preRequest and afterResponse scripts on requests and folders`() {
        val yaml = """
            type: collection.insomnia.rest/5.0
            schema_version: "5.1"
            name: Scripted Insomnia Collection
            meta:
              id: wrk_scripts
            scripts:
              preRequest: |
                insomnia.baseEnvironment.set("colInit", "1");
            collection:
              - name: Auth Folder
                meta:
                  id: fld_auth
                scripts:
                  preRequest: |
                    insomnia.environment.set("folderToken", "fld-123");
                  afterResponse: |
                    insomnia.test("Folder status check", () => {
                      insomnia.expect(insomnia.response.status).to.eql(200);
                    });
                children:
                  - name: Fetch Token
                    url: "https://auth.example.com/token"
                    method: POST
                    meta:
                      id: req_fetch_token
                    scripts:
                      preRequest: |
                        insomnia.environment.set("nonce", "n-42");
                        insomnia.request.addHeader({key: "X-Nonce", value: "n-42"});
                      afterResponse: |
                        const body = insomnia.response.json();
                        insomnia.environment.set("accessToken", body.token);
                        insomnia.test("Token exists", () => {
                          insomnia.expect(body.token).to.be.a("string");
                        });
        """.trimIndent()

        val doc = InsomniaImporter.default().importToOpenCollection(yaml)

        // Collection-level script
        val colScripts = doc.request?.scripts.orEmpty()
        assertEquals(1, colScripts.size)
        assertEquals(ScriptType.BEFORE_REQUEST, colScripts[0].type)
        assertTrue(colScripts[0].code.contains("clapi.collectionVariables.set(\"colInit\", \"1\")"))

        // Folder-level scripts
        val folder = assertIs<FolderItem>(doc.items.first())
        val folderScripts = folder.request?.scripts.orEmpty()
        assertEquals(2, folderScripts.size)
        val folderPre = folderScripts.first { it.type == ScriptType.BEFORE_REQUEST }
        val folderPost = folderScripts.first { it.type == ScriptType.AFTER_RESPONSE }
        assertTrue(folderPre.code.contains("clapi.environment.set(\"folderToken\", \"fld-123\")"))
        assertTrue(folderPost.code.contains("clapi.test(\"Folder status check\""))
        assertTrue(folderPost.code.contains("clapi.expect(clapi.response.status).to.eql(200)"))

        // Request-level scripts
        val request = assertIs<HttpRequestItem>(folder.items.first())
        val reqScripts = request.runtime?.scripts
        assertNotNull(reqScripts)
        assertEquals(2, reqScripts.size)

        val preScript = reqScripts.first { it.type == ScriptType.BEFORE_REQUEST }
        assertTrue(preScript.code.contains("clapi.environment.set(\"nonce\", \"n-42\")"))
        assertTrue(preScript.code.contains("clapi.request.addHeader({key: \"X-Nonce\", value: \"n-42\"})"))

        val postScript = reqScripts.first { it.type == ScriptType.AFTER_RESPONSE }
        assertTrue(postScript.code.contains("const body = clapi.response.json()"))
        assertTrue(postScript.code.contains("clapi.environment.set(\"accessToken\", body.token)"))
        assertTrue(postScript.code.contains("clapi.test(\"Token exists\""))
        assertTrue(postScript.code.contains("clapi.expect(body.token).to.be.a(\"string\")"))
    }
}
