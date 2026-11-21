package io.github.makeforge.clapi.core.scripting

import kotlin.time.TimeSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Sandboxed JavaScript execution engine for user-defined dynamic variables.
 *
 * Wraps user scripts in an isolated IIFE with:
 * - Read-only `context.request` (`url`, `method`, `headers`, `name`)
 * - Bound parameter variables and `args` / `context.args`
 * - Captured `console.log` / `info` / `warn` / `error` output
 * - `btoa` / `atob` Base64 utilities
 * - Enforced execution timeout via [evaluateJsWithTimeout]
 */
class ClapiDynamicVariableEngine(
    private val evaluator: (String, Long) -> String = ::evaluateJsWithTimeout,
) : DynamicVariableEngine {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    override suspend fun executeDynamicVariable(
        script: String,
        context: DynamicScriptContext,
        timeoutMs: Long,
    ): DynamicScriptExecutionResult =
        try {
            withContext(Dispatchers.Default) {
                evaluateDynamicVariable(script, context, timeoutMs)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            DynamicScriptExecutionResult(
                success = false,
                errorMessage = e.message ?: "Dynamic variable script execution failed",
            )
        }

    override fun evaluateDynamicVariable(
        script: String,
        context: DynamicScriptContext,
        timeoutMs: Long,
    ): DynamicScriptExecutionResult {
        val mark = TimeSource.Monotonic.markNow()
        if (script.isBlank()) {
            return DynamicScriptExecutionResult(
                success = true,
                returnValue = "",
                durationMs = mark.elapsedNow().inWholeMilliseconds,
            )
        }
        return try {
            val program = buildDynamicScriptEnvelope(script, context)
            val rawResult = evaluator(program, timeoutMs)
            val elapsedMs = mark.elapsedNow().inWholeMilliseconds
            parseExecutionResult(rawResult, elapsedMs)
        } catch (e: Exception) {
            val elapsedMs = mark.elapsedNow().inWholeMilliseconds
            DynamicScriptExecutionResult(
                success = false,
                durationMs = elapsedMs,
                errorMessage = e.message ?: "Dynamic variable script evaluation error",
            )
        }
    }

    private fun parseExecutionResult(rawJson: String, elapsedMs: Long): DynamicScriptExecutionResult {
        if (rawJson.isBlank()) {
            return DynamicScriptExecutionResult(
                success = false,
                durationMs = elapsedMs,
                errorMessage = "Empty response from JavaScript runtime",
            )
        }
        return try {
            val obj = json.parseToJsonElement(rawJson).jsonObject
            val errorField = (obj["error"] as? JsonPrimitive)?.contentOrNull
            val logs = obj["logs"]?.jsonArray?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull } ?: emptyList()
            val successField = (obj["success"] as? JsonPrimitive)?.booleanOrNull
            if (errorField != null && (successField == false || !obj.containsKey("success"))) {
                DynamicScriptExecutionResult(
                    success = false,
                    returnValue = null,
                    durationMs = elapsedMs,
                    consoleLogs = logs,
                    errorMessage = errorField,
                )
            } else {
                val retVal = (obj["returnValue"] as? JsonPrimitive)?.contentOrNull ?: ""
                DynamicScriptExecutionResult(
                    success = true,
                    returnValue = retVal,
                    durationMs = elapsedMs,
                    consoleLogs = logs,
                    errorMessage = null,
                )
            }
        } catch (e: Exception) {
            DynamicScriptExecutionResult(
                success = false,
                durationMs = elapsedMs,
                errorMessage = "Failed to parse script output: ${e.message ?: rawJson}",
            )
        }
    }

    internal companion object {
        private val JS_IDENTIFIER_REGEX = Regex("^[a-zA-Z_$][a-zA-Z0-9_$]*$")
        private val RESERVED_WORDS = setOf(
            "break", "case", "catch", "class", "const", "continue", "debugger", "default",
            "delete", "do", "else", "export", "extends", "false", "finally", "for",
            "function", "if", "import", "in", "instanceof", "new", "null", "return",
            "super", "switch", "this", "throw", "true", "try", "typeof", "var", "void",
            "while", "with", "yield", "let", "static", "enum", "await", "implements",
            "package", "protected", "interface", "private", "public", "arguments", "eval",
            "context", "request", "args", "console"
        )

        fun buildDynamicScriptEnvelope(
            userScript: String,
            context: DynamicScriptContext,
        ): String = buildString {
            val req = context.request ?: RequestContext()
            val validParamEntries = context.args.entries.filter { (k, _) ->
                JS_IDENTIFIER_REGEX.matches(k) && k !in RESERVED_WORDS
            }

            append("(function(){\n")
            append("var __logs = [];\n")
            append(
                """
                function __fmtLog(argsList) {
                    var parts = [];
                    for (var i = 0; i < argsList.length; i++) {
                        var v = argsList[i];
                        if (v === undefined) { parts.push('undefined'); continue; }
                        if (v === null) { parts.push('null'); continue; }
                        if (typeof v === 'object') {
                            try { parts.push(JSON.stringify(v)); } catch (_e) { parts.push('[Object]'); }
                            continue;
                        }
                        parts.push(String(v));
                    }
                    __logs.push(parts.join(' '));
                }
                var console = {
                    log: function() { __fmtLog(arguments); },
                    info: function() { __fmtLog(arguments); },
                    warn: function() { __fmtLog(arguments); },
                    error: function() { __fmtLog(arguments); }
                };
                globalThis.console = console;
                if (typeof globalThis.btoa !== 'function') {
                    var __b64chars = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/=';
                    globalThis.btoa = function(input) {
                        var str = String(input);
                        var output = '';
                        for (var block = 0, charCode, i = 0, map = __b64chars;
                             str.charAt(i | 0) || (map = '=', i % 1);
                             output += map.charAt(63 & block >> 8 - i % 1 * 8)) {
                            charCode = str.charCodeAt(i += 3/4);
                            if (charCode > 0xFF) throw new Error("'btoa' failed: character out of Latin1 range.");
                            block = block << 8 | charCode;
                        }
                        return output;
                    };
                    globalThis.atob = function(input) {
                        var str = String(input).replace(/=+$/, '');
                        var output = '';
                        if (str.length % 4 == 1) throw new Error("'atob' failed: invalid base64 string.");
                        for (var bc = 0, bs = 0, buffer, i = 0;
                             buffer = str.charAt(i++);
                             ~buffer && (bs = bc % 4 ? bs * 64 + buffer : buffer, bc++ % 4)
                                 ? output += String.fromCharCode(255 & bs >> (-2 * bc & 6)) : 0) {
                            buffer = __b64chars.indexOf(buffer);
                        }
                        return output;
                    };
                }
                """.trimIndent()
            )
            append("\n")

            // Construct read-only headers and request context
            append("var __rawHeaders = ").append(encodeMapToJson(req.headers)).append(";\n")
            append("var __roHeaders = new Proxy(__rawHeaders, {\n")
            append("  get: function(t, p) {\n")
            append("    if (typeof p === 'string') {\n")
            append("      if (p in t) return t[p];\n")
            append("      var low = p.toLowerCase();\n")
            append("      for (var k in t) { if (k.toLowerCase() === low) return t[k]; }\n")
            append("    }\n")
            append("    return t[p];\n")
            append("  },\n")
            append("  set: function() { return true; },\n")
            append("  deleteProperty: function() { return true; }\n")
            append("});\n")

            append("var __rawReq = {\n")
            append("  url: ").append(encodeStringToJson(req.url)).append(",\n")
            append("  method: ").append(encodeStringToJson(req.method)).append(",\n")
            append("  name: ").append(encodeStringToJson(req.name)).append(",\n")
            append("  headers: __roHeaders\n")
            append("};\n")
            append("var __roReq = new Proxy(__rawReq, {\n")
            append("  get: function(t, p) { return p === 'headers' ? __roHeaders : t[p]; },\n")
            append("  set: function() { return true; },\n")
            append("  deleteProperty: function() { return true; }\n")
            append("});\n")

            // Construct args object (both positional and named keys)
            append("var __rawArgs = {};\n")
            context.positionalArgs.forEachIndexed { idx, value ->
                append("__rawArgs[").append(idx).append("] = ").append(encodeAnyToJson(value)).append(";\n")
            }
            append("__rawArgs.length = ").append(context.positionalArgs.size).append(";\n")
            context.args.forEach { (key, value) ->
                append("__rawArgs[").append(encodeStringToJson(key)).append("] = ").append(encodeAnyToJson(value)).append(";\n")
            }
            append("var args = new Proxy(__rawArgs, {\n")
            append("  set: function() { return true; },\n")
            append("  deleteProperty: function() { return true; }\n")
            append("});\n")
            append("var request = __roReq;\n")
            append("var context = new Proxy({ request: __roReq, args: args }, {\n")
            append("  set: function() { return true; },\n")
            append("  deleteProperty: function() { return true; }\n")
            append("});\n")

            val paramNamesJsArray = validParamEntries.joinToString(",") { encodeStringToJson(it.key) }
            val paramValuesJsArray = validParamEntries.joinToString(",") { encodeAnyToJson(it.value) }
            append("var __paramNames = [").append(paramNamesJsArray).append("];\n")
            append("var __paramValues = [").append(paramValuesJsArray).append("];\n")
            append("var __userCode = ").append(encodeStringToJson(userScript)).append(";\n")

            append(
                """
                try {
                    var __fnArgs = ['context', 'request', 'args'].concat(__paramNames).concat([__userCode]);
                    var __fn = Function.apply(null, __fnArgs);
                    var __hasTopReturn = false;
                    try {
                        new Function('context', 'request', 'args', 'class __C { static {\n' + __userCode + '\n} }');
                    } catch (__syntaxCheckErr) {
                        __hasTopReturn = true;
                    }
                    var __res;
                    if (__hasTopReturn) {
                        __res = __fn.apply(null, [context, request, args].concat(__paramValues));
                    } else {
                        var __evalWrapperArgs = ['context', 'request', 'args'].concat(__paramNames).concat([
                            'return eval(' + JSON.stringify(__userCode) + ');'
                        ]);
                        var __evalFn = Function.apply(null, __evalWrapperArgs);
                        __res = __evalFn.apply(null, [context, request, args].concat(__paramValues));
                    }
                    var __strVal = '';
                    if (__res === null || __res === undefined) {
                        __strVal = '';
                    } else if (typeof __res === 'object') {
                        __strVal = JSON.stringify(__res);
                    } else {
                        __strVal = String(__res);
                    }
                    return JSON.stringify({
                        success: true,
                        returnValue: __strVal,
                        logs: __logs
                    });
                } catch (__err) {
                    var __errMsg = (__err && (__err.stack || __err.message)) ? String(__err.stack || __err.message) : String(__err);
                    return JSON.stringify({
                        success: false,
                        error: __errMsg,
                        logs: __logs
                    });
                }
                """.trimIndent()
            )
            append("\n})()")
        }

        private fun encodeStringToJson(value: String?): String =
            if (value == null) "null" else JsonPrimitive(value).toString()

        private fun encodeMapToJson(map: Map<String, String>): String {
            val obj = JsonObject(map.mapValues { (_, v) -> JsonPrimitive(v) })
            return obj.toString()
        }

        private fun encodeAnyToJson(value: Any?): String = when (value) {
            null -> "null"
            is String -> JsonPrimitive(value).toString()
            is Number -> JsonPrimitive(value).toString()
            is Boolean -> JsonPrimitive(value).toString()
            is JsonElement -> value.toString()
            else -> JsonPrimitive(value.toString()).toString()
        }
    }
}
