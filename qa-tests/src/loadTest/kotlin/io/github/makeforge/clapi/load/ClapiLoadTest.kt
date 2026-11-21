package io.github.makeforge.clapi.load

import io.github.makeforge.clapi.core.model.McpConnectionConfig
import io.github.makeforge.clapi.core.model.McpHttpMode
import io.github.makeforge.clapi.core.network.mcp.McpClient
import io.github.makeforge.clapi.mcp.server.backend.ExecutionResultDto
import io.github.makeforge.clapi.mcp.server.backend.InspectResponseResultDto
import io.github.makeforge.clapi.mcp.server.backend.SanitizedRequestDto
import io.github.makeforge.clapi.mcp.server.backend.TestSuiteResultDto
import io.github.makeforge.clapi.testsupport.DummyApiServer
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.HttpTimeoutConfig
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private data class LoadProfileConfig(
    val name: String,
    val largeJsonRequests: Int,
    val largeJsonItems: Int,
    val rapidHttpIterations: Int,
    val grpcIterations: Int,
    val grpcLargePayloadBytes: Int,
    val binaryIterations: Int,
    val binarySizeMb: Int,
    val cooldownWaitMs: Long,
    val postGcWaitMs: Long,
    val maxPeakRssMb: Double,
    val maxCooldownHeapMb: Double,
    val maxThreadDelta: Int,
) {
    companion object {
        fun fromSystemProperty(): LoadProfileConfig =
            when (System.getProperty("clapi.loadTest.profile", "standard").trim().lowercase()) {
                "quick" -> LoadProfileConfig(
                    name = "quick",
                    largeJsonRequests = 12,
                    largeJsonItems = 8_000,       // ~1.5 MB each
                    rapidHttpIterations = 30,
                    grpcIterations = 20,
                    grpcLargePayloadBytes = 131_072, // 128 KB
                    binaryIterations = 5,
                    binarySizeMb = 2,
                    cooldownWaitMs = 12_000L,
                    postGcWaitMs = 3_000L,
                    maxPeakRssMb = 1_024.0,
                    maxCooldownHeapMb = 256.0,
                    maxThreadDelta = 8,
                )
                "heavy" -> LoadProfileConfig(
                    name = "heavy",
                    largeJsonRequests = 65,       // Exceeds ResponseSnapshotCache(50) cap
                    largeJsonItems = 25_000,      // ~4.8 MB each
                    rapidHttpIterations = 250,
                    grpcIterations = 120,
                    grpcLargePayloadBytes = 524_288, // 512 KB
                    binaryIterations = 20,
                    binarySizeMb = 5,
                    cooldownWaitMs = 18_000L,
                    postGcWaitMs = 6_000L,
                    maxPeakRssMb = 1_536.0,
                    maxCooldownHeapMb = 384.0,
                    maxThreadDelta = 10,
                )
                else -> LoadProfileConfig(
                    name = "standard",
                    largeJsonRequests = 55,       // Exceeds ResponseSnapshotCache(50) cap
                    largeJsonItems = 15_000,      // ~2.8 MB each
                    rapidHttpIterations = 100,
                    grpcIterations = 60,
                    grpcLargePayloadBytes = 262_144, // 256 KB
                    binaryIterations = 10,
                    binarySizeMb = 4,
                    cooldownWaitMs = 15_000L,
                    postGcWaitMs = 5_000L,
                    maxPeakRssMb = 1_280.0,
                    maxCooldownHeapMb = 320.0,
                    maxThreadDelta = 8,
                )
            }
    }
}

class ClapiLoadTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val json = Json { ignoreUnknownKeys = true }

    private lateinit var dummyApiServer: DummyApiServer
    private lateinit var sutProcess: ClapiSutProcess
    private lateinit var httpClient: HttpClient
    private lateinit var reportDir: File

    @Before
    fun setUp() {
        val timestamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        val baseReportDir = File(System.getProperty("clapi.loadTest.reportDir", "build/reports/load-test"))
        reportDir = File(baseReportDir, timestamp).apply { mkdirs() }

        dummyApiServer = DummyApiServer(recordRequests = false)
        dummyApiServer.start()

        val jdkHome = File(
            System.getProperty("clapi.jdkHome")
                ?: System.getProperty("java.home")
        )
        val fatJar = File(
            requireNotNull(System.getProperty("clapi.fatJar")) {
                "System property 'clapi.fatJar' must point to packaged Clapi fat JAR."
            }
        )

        sutProcess = ClapiSutProcess(
            jdkHome = jdkHome,
            fatJar = fatJar,
            workspaceDir = File(tempFolder.root, "clapi-workspace"),
            jfrOutputFile = File(reportDir, "clapi-load.jfr"),
            logOutputFile = File(reportDir, "sut.log"),
        )
        sutProcess.start()

        httpClient = HttpClient(CIO) {
            expectSuccess = false
            install(HttpTimeout) {
                requestTimeoutMillis = HttpTimeoutConfig.INFINITE_TIMEOUT_MS
                socketTimeoutMillis = HttpTimeoutConfig.INFINITE_TIMEOUT_MS
            }
        }
    }

    @After
    fun tearDown() {
        runCatching { httpClient.close() }
        runCatching { sutProcess.stopGracefully() }
        runCatching { dummyApiServer.stop() }
    }

    @Test
    fun `sustained multi-protocol load test via embedded MCP server with memory profiling`() = runBlocking {
        val profile = LoadProfileConfig.fromSystemProperty()
        val startedAt = System.currentTimeMillis()
        val jdkHome = File(System.getProperty("clapi.jdkHome") ?: System.getProperty("java.home"))
        val sampler = ProcessMemorySampler(jdkHome = jdkHome, pid = sutProcess.pid)
        val snapshots = mutableListOf<MemorySnapshot>()
        val ops = linkedMapOf<String, Int>()

        println("=== Clapi Load Test [profile=${profile.name}] ===")
        println("SUT PID: ${sutProcess.pid} | MCP URL: ${sutProcess.mcpUrl}")
        println("DummyApiServer HTTP: ${dummyApiServer.baseUrl()} | gRPC: ${dummyApiServer.grpcEndpoint()}")
        println("Report Dir: ${reportDir.absolutePath}")

        // ── Phase 0: Baseline ──────────────────────────────────────────────
        sampler.establishNmtBaseline()
        val baseline = sampler.captureSnapshot(label = "baseline", triggerGcBeforeHistogram = true)
        snapshots += baseline
        println("[Phase 0: Baseline] RSS=${fmtMb(baseline.os.rssMb)} Heap=${fmtMb(baseline.heapLiveMb)} Threads=${baseline.threads.totalThreads}")

        val mcpClient = McpClient(this, httpClient, callTimeoutMs = 60_000)
        val init = mcpClient.connect(
            McpConnectionConfig(
                url = sutProcess.mcpUrl,
                httpMode = McpHttpMode.STREAMABLE_2025_06_18,
            )
        )
        assertEquals("clapi-mcp-server", init.serverInfo.name)

        // Setup environment, secret variable, and collection (with proto files for gRPC)
        mcpClient.callTool("clapi_upsert_environment", buildJsonObject {
            put("name", "LoadEnv")
        }).also { assertFalse(it.isError, "Failed to create environment: $it") }

        mcpClient.callTool("clapi_set_variable", buildJsonObject {
            put("scope", "environment")
            put("target", "LoadEnv")
            put("key", "baseUrl")
            put("kind", "regular")
            put("variableType", "string")
            put("value", dummyApiServer.baseUrl())
        }).also { assertFalse(it.isError, "Failed to set baseUrl: $it") }
        mcpClient.callTool("clapi_set_variable", buildJsonObject {
            put("scope", "environment")
            put("target", "LoadEnv")
            put("key", "grpcEndpoint")
            put("kind", "regular")
            put("variableType", "string")
            put("value", "grpc://${dummyApiServer.grpcEndpoint()}")
        }).also { assertFalse(it.isError, "Failed to set grpcEndpoint: $it") }
        mcpClient.callTool("clapi_set_variable", buildJsonObject {
            put("scope", "environment")
            put("target", "LoadEnv")
            put("key", "apiSecret")
            put("kind", "secret")
            put("variableType", "string")
            put("value", "load-test-secret-token-987654321")
        }).also { assertFalse(it.isError, "Failed to set apiSecret: $it") }
        mcpClient.callTool("clapi_set_active_environment", buildJsonObject {
            put("name", "LoadEnv")
        }).also { assertFalse(it.isError, "Failed to set active environment: $it") }

        mcpClient.callTool("clapi_manage_collection", buildJsonObject {
            put("action", "create")
            put("name", "LoadSuite")
            put("protoFiles", buildJsonArray {
                add(buildJsonObject { put("path", dummyApiServer.protoFilePath()) })
            })
            put("importPaths", buildJsonArray {
                add(buildJsonObject {
                    put("path", dummyApiServer.metadataProtoFilePath())
                    put("enabled", true)
                })
            })
        }).also { assertFalse(it.isError, "Failed to create collection: $it") }

        // Author reusable requests
        val httpEchoReq = upsertRequest(mcpClient, buildJsonObject {
            put("parentId", "LoadSuite")
            put("name", "HTTP Echo JSON")
            put("kind", "http")
            put("method", "POST")
            put("url", "{{baseUrl}}/echo-json")
            put("headers", buildJsonArray {
                add(buildJsonObject {
                    put("key", "Authorization")
                    put("value", "Bearer {{apiSecret}}")
                })
                add(buildJsonObject {
                    put("key", "Content-Type")
                    put("value", "application/json")
                })
            })
            put("bodyType", "JSON")
            put("bodyContent", """{"ping":"clapi-load","token":"{{apiSecret}}"}""")
            put(
                "testScript",
                """
                clapi.test("status 200", function() {
                    clapi.expect(clapi.response.code).to.equal(200);
                });
                """.trimIndent()
            )
        })

        val graphqlReq = upsertRequest(mcpClient, buildJsonObject {
            put("parentId", "LoadSuite")
            put("name", "GraphQL Users")
            put("kind", "graphql")
            put("url", "{{baseUrl}}/graphql")
            put("graphql", buildJsonObject {
                put("method", "POST")
                put("query", "query { users { id name } }")
            })
            put(
                "testScript",
                """
                clapi.test("graphql 200", function() {
                    clapi.expect(clapi.response.code).to.equal(200);
                });
                """.trimIndent()
            )
        })

        val grpcUnaryReq = upsertRequest(mcpClient, buildJsonObject {
            put("parentId", "LoadSuite")
            put("name", "gRPC SayHello")
            put("kind", "grpc")
            put("url", "{{grpcEndpoint}}")
            put("grpc", buildJsonObject {
                put("protoFilePath", dummyApiServer.protoFilePath())
                put("grpcMethod", "helloworld.Greeter/SayHello")
                put("grpcMethodType", "unary")
                put("metadata", buildJsonArray {
                    add(buildJsonObject {
                        put("name", "x-custom-meta")
                        put("value", "load-meta")
                    })
                })
                put("message", """{"name": "ClapiLoad"}""")
            })
        })

        val grpcLargeReq = upsertRequest(mcpClient, buildJsonObject {
            put("parentId", "LoadSuite")
            put("name", "gRPC LargePayload")
            put("kind", "grpc")
            put("url", "{{grpcEndpoint}}")
            put("grpc", buildJsonObject {
                put("protoFilePath", dummyApiServer.protoFilePath())
                put("grpcMethod", "helloworld.Greeter/SayHelloAgain")
                put("grpcMethodType", "unary")
                put("message", """{"name": "large:${profile.grpcLargePayloadBytes}"}""")
            })
        })

        val binaryReq = upsertRequest(mcpClient, buildJsonObject {
            put("parentId", "LoadSuite")
            put("name", "Binary Download")
            put("kind", "http")
            put("method", "GET")
            put("url", "{{baseUrl}}/load/binary?sizeMb=${profile.binarySizeMb}")
        })

        // ── Phase 1: Warmup ────────────────────────────────────────────────
        repeat(5) {
            executeAndAssertOk(mcpClient, httpEchoReq.id)
            executeAndAssertOk(mcpClient, grpcUnaryReq.id)
        }
        val warmup = sampler.captureSnapshot(label = "warmup", triggerGcBeforeHistogram = true)
        snapshots += warmup
        println("[Phase 1: Warmup] RSS=${fmtMb(warmup.os.rssMb)} Heap=${fmtMb(warmup.heapLiveMb)} Threads=${warmup.threads.totalThreads}")

        // ── Phase 2: Large JSON + ResponseSnapshotCache Eviction + JSONPath ──
        var largeJsonCount = 0
        var inspectCount = 0
        for (i in 1..profile.largeJsonRequests) {
            val req = upsertRequest(mcpClient, buildJsonObject {
                put("parentId", "LoadSuite")
                put("name", "LargeJson-$i")
                put("kind", "http")
                put("method", "GET")
                put("url", "{{baseUrl}}/load/json?items=${profile.largeJsonItems}&paddingBytes=64")
                put(
                    "testScript",
                    """
                    clapi.test("large json 200", function() {
                        clapi.expect(clapi.response.code).to.equal(200);
                    });
                    """.trimIndent()
                )
            })
            val exec = executeAndAssertOk(mcpClient, req.id)
            assertTrue(exec._truncated, "Expected large JSON response to be truncated in preview")
            largeJsonCount++

            if (i % 5 == 0) {
                val inspectRes = mcpClient.callTool("clapi_inspect_response", buildJsonObject {
                    put("requestId", req.id)
                    put("jsonPath", "$.items[0].name")
                })
                assertFalse(inspectRes.isError, "clapi_inspect_response failed: $inspectRes")
                val inspectDto = json.decodeFromString(
                    InspectResponseResultDto.serializer(),
                    inspectRes.content.single().text!!,
                )
                assertEquals("User-0", inspectDto.content)
                inspectCount++
            }
        }
        ops["large_json_requests"] = largeJsonCount
        ops["jsonpath_inspections"] = inspectCount
        val afterLargeJson = sampler.captureSnapshot(label = "after_large_json", triggerGcBeforeHistogram = false)
        snapshots += afterLargeJson
        println("[Phase 2: Large JSON ($largeJsonCount)] RSS=${fmtMb(afterLargeJson.os.rssMb)} Heap=${fmtMb(afterLargeJson.heapLiveMb)}")

        // ── Phase 3: High-Throughput HTTP & GraphQL ────────────────────────
        repeat(profile.rapidHttpIterations) { idx ->
            val targetId = if (idx % 2 == 0) httpEchoReq.id else graphqlReq.id
            executeAndAssertOk(mcpClient, targetId)
        }
        ops["rapid_http_graphql"] = profile.rapidHttpIterations

        // Run a test suite pass on a small dedicated folder
        mcpClient.callTool("clapi_manage_folder", buildJsonObject {
            put("action", "create")
            put("parentId", "LoadSuite")
            put("name", "SuiteFolder")
        })
        upsertRequest(mcpClient, buildJsonObject {
            put("parentId", "LoadSuite/SuiteFolder")
            put("name", "SuitePing")
            put("kind", "http")
            put("method", "GET")
            put("url", "{{baseUrl}}/users")
            put("testScript", """clapi.test("ok", function() { clapi.expect(clapi.response.code).to.equal(200); });""")
        })
        val suiteRes = mcpClient.callTool("clapi_run_test_suite", buildJsonObject {
            put("targetId", "LoadSuite/SuiteFolder")
        })
        assertFalse(suiteRes.isError)
        val suiteDto = json.decodeFromString(TestSuiteResultDto.serializer(), suiteRes.content.single().text!!)
        assertEquals(1, suiteDto.passedRequests)
        ops["test_suite_runs"] = 1

        // ── Phase 4: gRPC Burst & Large Messages ───────────────────────────
        repeat(profile.grpcIterations) { idx ->
            val targetId = if (idx % 3 == 0) grpcLargeReq.id else grpcUnaryReq.id
            val exec = executeAndAssertOk(mcpClient, targetId)
            assertTrue(
                exec.statusCode == 0 || exec.statusCode == 200,
                "Expected gRPC OK (0 or 200), got ${exec.statusCode} (${exec.statusText})",
            )
        }
        ops["grpc_calls"] = profile.grpcIterations

        // ── Phase 5: Binary Downloads & Collection Export ──────────────────
        repeat(profile.binaryIterations) {
            val exec = executeAndAssertOk(mcpClient, binaryReq.id)
            assertTrue(exec._binary, "Expected binary flag on /load/binary response")
        }
        val exportRes = mcpClient.callTool("clapi_export_collection", buildJsonObject {
            put("collectionId", "LoadSuite")
            put("format", "opencollection-json")
        })
        assertFalse(exportRes.isError, "Collection export failed: $exportRes")
        ops["binary_downloads"] = profile.binaryIterations
        ops["collection_exports"] = 1

        // ── Phase 6: Peak Snapshot ─────────────────────────────────────────
        val peak = sampler.captureSnapshot(
            label = "peak",
            triggerGcBeforeHistogram = false,
            saveThreadDumpTo = File(reportDir, "jstack-peak.txt"),
        )
        val nmtDiffPeak = sampler.captureNmtDiff()
        snapshots += peak
        println("[Phase 6: Peak] RSS=${fmtMb(peak.os.rssMb)} HWM=${fmtMb(peak.os.hwmMb)} Heap=${fmtMb(peak.heapLiveMb)} Threads=${peak.threads.totalThreads}")

        // Disconnect MCP client before cooldown
        mcpClient.disconnect()

        // ── Phase 7: Cooldown & Leak / ZGC Uncommit Check ──────────────────
        println("[Phase 7: Cooldown] Waiting ${profile.cooldownWaitMs}ms for ZGC uncommit...")
        delay(profile.cooldownWaitMs)
        sampler.triggerGc()
        delay(profile.postGcWaitMs)

        val cooldown = sampler.captureSnapshot(
            label = "cooldown",
            triggerGcBeforeHistogram = true,
            saveThreadDumpTo = File(reportDir, "jstack-cooldown.txt"),
        )
        val nmtDiffCooldown = sampler.captureNmtDiff()
        snapshots += cooldown
        println(
            "[Phase 7: Cooldown] RSS=${fmtMb(cooldown.os.rssMb)} Heap=${fmtMb(cooldown.heapLiveMb)} " +
                "UnaccountedNative=${fmtMb(cooldown.unaccountedNativeMb)} Threads=${cooldown.threads.totalThreads}"
        )

        // ── Evaluate Budgets & Write Report ────────────────────────────────
        val threadDelta = cooldown.threads.totalThreads - warmup.threads.totalThreads
        val checks = listOf(
            ThresholdCheckResult(
                name = "No Deadlocks (Peak)",
                actual = if (peak.threads.deadlocksDetected) "DEADLOCK" else "0",
                threshold = "0",
                passed = !peak.threads.deadlocksDetected,
            ),
            ThresholdCheckResult(
                name = "No Deadlocks (Cooldown)",
                actual = if (cooldown.threads.deadlocksDetected) "DEADLOCK" else "0",
                threshold = "0",
                passed = !cooldown.threads.deadlocksDetected,
            ),
            ThresholdCheckResult(
                name = "Thread Leak Delta (Cooldown - Warmup)",
                actual = "$threadDelta (${cooldown.threads.totalThreads} vs ${warmup.threads.totalThreads})",
                threshold = "<= ${profile.maxThreadDelta}",
                passed = threadDelta <= profile.maxThreadDelta,
            ),
            ThresholdCheckResult(
                name = "Peak OS RSS",
                actual = fmtMb(peak.os.rssMb),
                threshold = "<= ${fmtMb(profile.maxPeakRssMb)}",
                passed = peak.os.rssMb <= profile.maxPeakRssMb,
            ),
            ThresholdCheckResult(
                name = "Post-Cooldown Heap Live Set",
                actual = fmtMb(cooldown.heapLiveMb),
                threshold = "<= ${fmtMb(profile.maxCooldownHeapMb)}",
                passed = cooldown.heapLiveMb <= profile.maxCooldownHeapMb,
            ),
        )

        val failed = checks.filterNot { it.passed }
        var heapDumpFile: File? = null
        if (failed.isNotEmpty()) {
            heapDumpFile = File(reportDir, "failure.hprof")
            runCatching { sampler.dumpHeap(heapDumpFile!!) }
        }

        // Stop SUT cleanly before analyzing clapi-load.jfr so dumponexit=true has flushed completely.
        sutProcess.stopGracefully()
        val jfrAllocations = JfrAllocationAnalyzer.analyze(
            jdkHome = jdkHome,
            jfrFile = File(reportDir, "clapi-load.jfr"),
        )

        val durationSec = (System.currentTimeMillis() - startedAt) / 1000.0
        val report = LoadTestReport(
            profile = profile.name,
            startedAtEpochMs = startedAt,
            durationSeconds = durationSec,
            operationsExecuted = ops,
            snapshots = snapshots,
            nmtDiffPeakVsBaseline = nmtDiffPeak,
            nmtDiffCooldownVsBaseline = nmtDiffCooldown,
            jfrAllocations = jfrAllocations,
            checks = checks,
        )
        LoadTestReportWriter.write(reportDir, report)
        println("Report written to: ${File(reportDir, "summary.md").absolutePath}")

        if (failed.isNotEmpty()) {
            val summary = failed.joinToString("\n") { " - ${it.name}: actual=${it.actual}, threshold=${it.threshold}" }
            error("Load test threshold(s) exceeded (heap dump saved to ${heapDumpFile?.absolutePath}):\n$summary")
        }
    }

    private suspend fun upsertRequest(
        client: McpClient,
        args: kotlinx.serialization.json.JsonObject,
    ): SanitizedRequestDto {
        val res = client.callTool("clapi_upsert_request", args)
        assertFalse(res.isError, "clapi_upsert_request failed: $res")
        return json.decodeFromString(SanitizedRequestDto.serializer(), res.content.single().text!!)
    }

    private suspend fun executeAndAssertOk(
        client: McpClient,
        requestId: String,
    ): ExecutionResultDto {
        val res = client.callTool("clapi_execute_request", buildJsonObject {
            put("requestId", requestId)
        })
        assertFalse(res.isError, "clapi_execute_request($requestId) failed: $res")
        val dto = json.decodeFromString(ExecutionResultDto.serializer(), res.content.single().text!!)
        assertTrue(dto.assertions.all { it.passed }, "Assertions failed for $requestId: ${dto.assertions}")
        return dto
    }

    private fun fmtMb(v: Double?): String =
        if (v == null) "n/a" else String.format(Locale.US, "%.1f MB", v)
}
