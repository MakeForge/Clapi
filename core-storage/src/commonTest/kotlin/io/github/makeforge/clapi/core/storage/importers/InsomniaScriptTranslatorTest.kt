package io.github.makeforge.clapi.core.storage.importers

import kotlin.test.Test
import kotlin.test.assertEquals

class InsomniaScriptTranslatorTest {

    @Test
    fun `translates environment and variable scopes`() {
        val input = """
            insomnia.environment.set("env", "env value");
            insomnia.baseEnvironment.set("baseEnv", "base env value");
            insomnia.variables.set("var", "var value");
            insomnia.collectionVariables.set("collectionEnv", "collection variable value");
            const env = insomnia.environment.get("env");
            const variable = insomnia.variables.get("var");
            const baseEnv = insomnia.baseEnvironment.get("baseEnv");
            insomnia.environment.unset("env");
            insomnia.collectionVariables.unset("baseEnv");
        """.trimIndent()

        val expected = """
            clapi.environment.set("env", "env value");
            clapi.collectionVariables.set("baseEnv", "base env value");
            clapi.variables.set("var", "var value");
            clapi.collectionVariables.set("collectionEnv", "collection variable value");
            const env = clapi.environment.get("env");
            const variable = clapi.variables.get("var");
            const baseEnv = clapi.collectionVariables.get("baseEnv");
            clapi.environment.unset("env");
            clapi.collectionVariables.unset("baseEnv");
        """.trimIndent()

        assertEquals(expected, InsomniaScriptTranslator.convertScript(input))
    }

    @Test
    fun `translates test and expect assertions`() {
        val input = """
            const jsonBody = insomnia.response.json();
            insomnia.test('Check the ID', () => {
              insomnia.expect(jsonBody.id).to.eql('abc-123');
              insomnia.expect(insomnia.response.status).to.eql(200);
              insomnia.expect(insomnia.response.code).to.be.oneOf([200, 201]);
            });
        """.trimIndent()

        val expected = """
            const jsonBody = clapi.response.json();
            clapi.test('Check the ID', () => {
              clapi.expect(jsonBody.id).to.eql('abc-123');
              clapi.expect(clapi.response.status).to.eql(200);
              clapi.expect(clapi.response.code).to.be.oneOf([200, 201]);
            });
        """.trimIndent()

        assertEquals(expected, InsomniaScriptTranslator.convertScript(input))
    }

    @Test
    fun `translates request manipulation and sendRequest`() {
        val input = """
            insomnia.request.method = 'POST';
            insomnia.request.url.addQueryParams('k1=v1');
            insomnia.request.addHeader({key: 'X-Header-Name-1', value: 'value1'});
            insomnia.request.removeHeader('X-Header-Name-1');
            const cookies = insomnia.response.cookies.toObject();
            insomnia.sendRequest('https://httpbin.org/anything', (err, resp) => {
              insomnia.environment.set('prevResponse', resp.code);
            });
        """.trimIndent()

        val expected = """
            clapi.request.method = 'POST';
            clapi.request.url.addQueryParams('k1=v1');
            clapi.request.addHeader({key: 'X-Header-Name-1', value: 'value1'});
            clapi.request.removeHeader('X-Header-Name-1');
            const cookies = clapi.response.cookies.toObject();
            clapi.sendRequest('https://httpbin.org/anything', (err, resp) => {
              clapi.environment.set('prevResponse', resp.code);
            });
        """.trimIndent()

        assertEquals(expected, InsomniaScriptTranslator.convertScript(input))
    }

    @Test
    fun `translates parentFolders to folder namespace`() {
        val input = """
            const myFolder = insomnia.parentFolders.get("MyFolder");
            const byId = insomnia.parentFolders.getById("fld_1");
            const byName = insomnia.parentFolders.getByName("MyFolder");
        """.trimIndent()

        val expected = """
            const myFolder = clapi.folder.get("MyFolder");
            const byId = clapi.folder.getById("fld_1");
            const byName = clapi.folder.getByName("MyFolder");
        """.trimIndent()

        assertEquals(expected, InsomniaScriptTranslator.convertScript(input))
    }

    @Test
    fun `preserves blank scripts and unrelated JavaScript`() {
        assertEquals("", InsomniaScriptTranslator.convertScript(""))
        assertEquals("   ", InsomniaScriptTranslator.convertScript("   "))
        val plainJs = "const x = 1 + 2; console.log(x);"
        assertEquals(plainJs, InsomniaScriptTranslator.convertScript(plainJs))
    }
}
