// Modified by MakeForge (2026)
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import dev.nucleusframework.desktop.application.dsl.TargetFormat
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URI
import java.nio.file.Files
import java.util.concurrent.TimeUnit
import java.util.jar.Attributes
import java.util.jar.JarOutputStream
import java.util.jar.Manifest
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.nucleus)
    alias(libs.plugins.roborazzi)
}

kotlin {
    jvm("desktop") {
        mainRun {
            mainClass = "io.github.makeforge.clapi.ui.desktop.MainKt"
        }
    }

    sourceSets {
        val desktopMain by getting {
            dependencies {
                implementation(compose.desktop.currentOs)
                implementation(compose.runtime)
                implementation(compose.foundation)
                implementation(compose.material3)
                implementation(libs.nucleus.application)
                implementation(libs.nucleus.aot.runtime)
                implementation(libs.nucleus.core.runtime)
                implementation(libs.nucleus.decorated.window.tao)
                implementation(libs.nucleus.decorated.window.material3)
                implementation(libs.filekit.dialogs)
                implementation(project(":ui-shared"))
                implementation(libs.coroutines.core)
                implementation(libs.kotlin.logging)
                implementation(libs.slf4j.api)
                implementation(libs.tinylog.api)
                implementation(libs.tinylog.impl)
                implementation(libs.slf4j.tinylog)
            }
        }
        val desktopTest by getting {
            dependencies {
                implementation(libs.kotlin.test)
                implementation(libs.junit4)
                implementation(libs.compose.ui.test.junit4.desktop)
                implementation(project(":sample-server")) {
                    exclude(group = "ch.qos.logback")
                }
                implementation(libs.ktor.server.core)
                implementation(libs.ktor.server.netty)
                implementation(libs.ktor.server.websockets)
                implementation(libs.ktor.client.core)
                implementation(libs.ktor.client.cio)
                implementation(libs.serialization.json)
                implementation(libs.grpc.netty.shaded)
                implementation(libs.grpc.protobuf)
                implementation(libs.grpc.stub)
                implementation(libs.protobuf.java)
                implementation(libs.amqp.client)
                implementation(libs.compose.ui.tooling.preview)
                implementation(libs.roborazzi.compose.desktop.preview.scanner.support)
                implementation(libs.composable.preview.scanner.android)
            }
        }
    }
}

roborazzi {
    outputDir.set(file("src/desktopTest/snapshots/roborazzi"))
    @OptIn(ExperimentalRoborazziApi::class)
    generateComposePreviewDesktopTests {
        enable = true
        packages = listOf("io.github.makeforge.clapi.ui.desktop.preview")
    }
}

val copyScreenshots = tasks.register<Copy>("copyScreenshots") {
    group = "roborazzi"
    description = "Copies preview screenshots from src/desktopTest/snapshots/roborazzi to docs/public/generated/screenshots"
    mustRunAfter("recordRoborazziDesktop")
    from(layout.projectDirectory.dir("src/desktopTest/snapshots/roborazzi")) {
        include(
            "*MainScreenPreviews*",
            "*GitSyncPreviews*",
            "*EnvironmentEditDialogPreviews*",
            "*ImportSpecificationDialogPreviews*",
            "*SelectProtoFileDialogPreviews*",
            "*SettingsDialogPreviews*",
            "*MainScreenTestsPreviews*",
            "*MainScreenFolderPreviews*",
            "*MainScreenCollectionProtobufPreviews*",
        )
    }
    into(rootProject.file("docs/public/generated/screenshots"))
}

tasks.register("copyMainScreenPreviews") {
    group = "roborazzi"
    description = "Alias for copyScreenshots"
    dependsOn(copyScreenshots)
}

val devStorageDir = rootProject.file(".clapi-dev")

val jvmDev = tasks.register<JavaExec>("jvmDev") {
    group = "application"
    description = "Runs Clapi desktop in development mode with persistent, isolated storage in .clapi-dev/"
    mainClass.set("io.github.makeforge.clapi.ui.desktop.MainKt")
    classpath = kotlin.jvm("desktop").compilations.getByName("main").runtimeDependencyFiles +
                kotlin.jvm("desktop").compilations.getByName("main").output.allOutputs
    devStorageDir.mkdirs()
    systemProperty("clapi.dir", devStorageDir.absolutePath)
    systemProperty("clapi.dev", "true")
    if (System.getProperty("os.name").lowercase().contains("linux")) {
        environment("MALLOC_ARENA_MAX", "2")
    }
}

tasks.register("desktopRunDev") {
    group = "application"
    description = "Alias for jvmDev"
    dependsOn(jvmDev)
}

tasks.register("runDev") {
    group = "application"
    description = "Alias for jvmDev"
    dependsOn(jvmDev)
}

configurations.named("desktopRuntimeClasspath") {
    exclude(group = "org.jetbrains.compose.material", module = "material")
    exclude(group = "org.jetbrains.compose.material", module = "material-desktop")
}

private val strippedJarEntryPrefixes = listOf(
    "bin/2.",
)

private fun stripEmbeddedJarBlobs(jarFile: File) {
    val tempFile = File(jarFile.parentFile, "${jarFile.name}.slim.tmp")
    var removedAny = false
    ZipFile(jarFile).use { zipIn ->
        ZipOutputStream(FileOutputStream(tempFile).buffered()).use { zipOut ->
            val entries = zipIn.entries()
            while (entries.hasMoreElements()) {
                val entry = entries.nextElement()
                if (strippedJarEntryPrefixes.any { entry.name.startsWith(it) }) {
                    removedAny = true
                    continue
                }
                val newEntry = ZipEntry(entry.name).apply {
                    time = entry.time
                    comment = entry.comment
                    extra = entry.extra
                }
                zipOut.putNextEntry(newEntry)
                if (!entry.isDirectory) {
                    zipIn.getInputStream(entry).use { it.copyTo(zipOut) }
                }
                zipOut.closeEntry()
            }
        }
    }
    if (removedAny) {
        tempFile.copyTo(jarFile, overwrite = true)
    }
    tempFile.delete()
}

tasks.matching { it.name == "createDistributable" || it.name == "createReleaseDistributable" }.configureEach {
    val binariesDirProvider = layout.buildDirectory.dir("compose/binaries")
    notCompatibleWithConfigurationCache("Strips embedded protoc 2.x binaries from distributable JARs")
    doLast {
        val binariesDir = binariesDirProvider.get().asFile
        if (binariesDir.exists()) {
            binariesDir.walkTopDown()
                .filter { it.isFile && it.extension == "jar" && it.name.startsWith("protoc-jar-") }
                .forEach { stripEmbeddedJarBlobs(it) }
        }
    }
}

val stageDistributionLegalFiles = tasks.register<Sync>("stageDistributionLegalFiles") {
    group = "distribution"
    description = "Stages root LICENSE and NOTICE files into packaged app resources."
    from(rootProject.file("LICENSE"))
    from(rootProject.file("NOTICE"))
    into(layout.buildDirectory.dir("generated/distribution-legal/app-resources/common"))
}

val stageNsisLicenseFile = tasks.register<Copy>("stageNsisLicenseFile") {
    group = "distribution"
    description = "Stages root LICENSE as LICENSE.txt for the Windows NSIS installer."
    from(rootProject.file("LICENSE")) {
        rename { "LICENSE.txt" }
    }
    into(layout.buildDirectory.dir("generated/distribution-legal/nsis"))
}

tasks.named<org.gradle.jvm.tasks.Jar>("desktopJar") {
    from(rootProject.file("LICENSE")) { into("META-INF") }
    from(rootProject.file("NOTICE")) { into("META-INF") }
}

tasks.matching {
    it.name == "prepareAppResources" || it.name == "prepareSandboxedAppResources"
}.configureEach {
    dependsOn(stageDistributionLegalFiles)
}

tasks.matching {
    it.name != "stageNsisLicenseFile" && it.name.contains("Nsis", ignoreCase = true)
}.configureEach {
    dependsOn(stageNsisLicenseFile)
}

// ─── Desktop application + native packaging ───────────────────────────────────
// Version flows from gradle.properties → root build.gradle.kts → project.version
// Change it in ONE place: gradle.properties  appVersion=x.y.z
nucleus.application {
    mainClass = "io.github.makeforge.clapi.ui.desktop.MainKt"
    val javaToolchains = project.extensions.getByType<JavaToolchainService>()
    javaHome = javaToolchains.launcherFor {
        languageVersion.set(JavaLanguageVersion.of(25))
    }.get().metadata.installationPath.asFile.absolutePath
    // Generational ZGC + JIT code & Direct Cache mildly: 208MB
    jvmArgs += listOf(
        "-Xms64m",
        "-Xmx2g",                        // High ceiling so large JSON/binary responses never crash the app
        "-XX:SoftMaxHeapSize=192m",      // ZGC keeps idle heap at/below 192 MB without excessive GC CPU churn
        "-XX:+UseZGC",
        "-XX:ZUncommitDelay=5",          // Returns spike RAM back to OS 5 seconds after closing a big tab
        "-XX:+UseStringDeduplication",   // Huge win for JSON payloads with repeated keys ("id", "type", "created_at")
        "-Xss256k",
        "-XX:MaxMetaspaceSize=128m",
        "-XX:ReservedCodeCacheSize=96m",
        "-XX:MaxDirectMemorySize=256m"   // Give Ktor/OkHttp/Skia enough direct buffer headroom for large downloads
    )

    buildTypes.release.proguard {
        isEnabled.set(false)
        optimize.set(false)
        configurationFiles.from(project.file("proguard-rules.pro"))
    }

    nativeDistributions {
        enableAotCache = true

        modules(
            "java.base",
            "java.datatransfer",
            "java.desktop",
            "java.instrument",
            "java.logging",
            "java.management",
            "java.naming",
            "java.net.http",
            "java.prefs",
            "java.security.jgss",
            "java.sql",
            "java.xml",
            "jdk.crypto.ec",
            "jdk.security.auth",
            "jdk.unsupported",
        )

        // Target every platform; each CI runner builds only what it supports.
        targetFormats(
            TargetFormat.Dmg,  // macOS
            TargetFormat.Nsis, // Windows (switched from Msi)
            TargetFormat.Deb,  // Linux (Debian)
            TargetFormat.Rpm,  // Linux (Fedora)
        )

        packageName    = "Clapi"
        packageVersion = project.version.toString()   // e.g. "1.0.0"
        description    = "Clapi – API testing for every platform"
        copyright      = "Copyright 2026 MakeForge; portions originally Copyright ReqLab Contributors"
        vendor         = "MakeForge"
        homepage       = "https://github.com/makeforge/Clapi"
        licenseFile.set(rootProject.file("LICENSE"))
        appResourcesRootDir.set(layout.buildDirectory.dir("generated/distribution-legal/app-resources"))
        cleanupNativeLibs = true

        macOS {
            bundleID = "io.github.makeforge.clapi"
            iconFile.set(project.file("src/desktopMain/resources/icons/clapi.icns"))
            // dmgPackageVersion inherits packageVersion automatically
        }
        windows {
            menuGroup          = "Clapi"
            iconFile.set(project.file("src/desktopMain/resources/icons/clapi.ico"))
            nsis {
                oneClick = false
                perMachine = false
                allowElevation = true
                allowToChangeInstallationDirectory = true
                createDesktopShortcut = true
                createStartMenuShortcut = true
                runAfterFinish = true
                license.set(layout.buildDirectory.file("generated/distribution-legal/nsis/LICENSE.txt"))
            }
        }
        linux {
            packageName        = "Clapi"
            debMaintainer      = "clapiproject@yahoo.com"
            appCategory        = "Development"
            iconFile.set(project.file("src/desktopMain/resources/icons/clapi-icon-256.png"))
        }
    }
}

// ─── Runnable fat JAR task ───────────────────────────────────────────────────
// Produces ui-desktop/build/distribute/Clapi-{version}.jar
// Includes app classes + runtime classpath and sets Main-Class so `java -jar` works.
// Implemented as a dedicated DefaultTask so desktopRuntimeClasspath is only resolved at
// execution time (after project JAR tasks have built their outputs and artifact transforms
// like Nucleus's CleanNativeLibsTransform can read them).
abstract class PackageClapiJarTask : DefaultTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val mainJar: RegularFileProperty

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val licenseFile: RegularFileProperty

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val noticeFile: RegularFileProperty

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val runtimeClasspath: ConfigurableFileCollection

    @get:Input
    abstract val mainClassName: Property<String>

    @get:OutputFile
    abstract val outputJar: RegularFileProperty

    @TaskAction
    fun packageJar() {
        val outFile = outputJar.get().asFile
        outFile.parentFile.mkdirs()

        val manifest = Manifest().apply {
            mainAttributes[Attributes.Name.MANIFEST_VERSION] = "1.0"
            mainAttributes[Attributes.Name.MAIN_CLASS] = mainClassName.get()
        }

        val mainJarFile = mainJar.get().asFile
        val allJars = buildList {
            add(mainJarFile)
            addAll(runtimeClasspath.files.filter { it.isFile && it.name.endsWith(".jar") })
        }

        val serviceMap = linkedMapOf<String, LinkedHashSet<String>>()
        val thirdPartyNotices = linkedMapOf<String, String>()
        val thirdPartyLicenses = linkedMapOf<String, String>()
        val seenEntries = hashSetOf("META-INF/", "META-INF/MANIFEST.MF")

        JarOutputStream(FileOutputStream(outFile).buffered(), manifest).use { jarOut ->
            // Embed Clapi's canonical LICENSE and NOTICE at the top level of META-INF.
            writeTextEntry(jarOut, seenEntries, "META-INF/LICENSE", licenseFile.get().asFile.readBytes())
            writeTextEntry(jarOut, seenEntries, "META-INF/NOTICE", noticeFile.get().asFile.readBytes())

            for (jarFile in allJars) {
                if (!jarFile.exists()) continue
                val isMainJar = jarFile == mainJarFile
                ZipFile(jarFile).use { zip ->
                    val entries = zip.entries()
                    while (entries.hasMoreElements()) {
                        val entry = entries.nextElement()
                        val name = entry.name

                        // Merge META-INF/services/* across all JARs so GraalVM Truffle languages
                        // and other ServiceLoader providers are not lost to duplicate exclusion.
                        if (name.startsWith("META-INF/services/")) {
                            if (!entry.isDirectory) {
                                val lines = zip.getInputStream(entry).bufferedReader().use { reader ->
                                    reader.readLines()
                                        .filter { line -> line.isNotBlank() && !line.trimStart().startsWith("#") }
                                }
                                serviceMap.getOrPut(name) { linkedSetOf() }.addAll(lines)
                            }
                            continue
                        }

                        // Preserve third-party NOTICE and LICENSE files from bundled dependencies
                        // rather than dropping duplicates or colliding with Clapi's own META-INF files.
                        if (!entry.isDirectory && isTopLevelMetaInfNotice(name)) {
                            if (!isMainJar) {
                                val text = zip.getInputStream(entry).bufferedReader(Charsets.UTF_8).use { it.readText().trim() }
                                if (text.isNotEmpty()) {
                                    thirdPartyNotices["${jarFile.name} ($name)"] = text
                                }
                            }
                            continue
                        }
                        if (!entry.isDirectory && isTopLevelMetaInfLicense(name)) {
                            if (!isMainJar) {
                                val text = zip.getInputStream(entry).bufferedReader(Charsets.UTF_8).use { it.readText().trim() }
                                if (text.isNotEmpty()) {
                                    thirdPartyLicenses["${jarFile.name} ($name)"] = text
                                }
                            }
                            continue
                        }

                        // Drop signature metadata that becomes invalid when jars are merged,
                        // and strip obsolete protoc 2.x binaries (ProtocJarParser uses 3.11.4).
                        if (isExcludedEntry(name)) continue
                        if (!seenEntries.add(name)) continue

                        val newEntry = ZipEntry(name).apply {
                            time = entry.time
                        }
                        jarOut.putNextEntry(newEntry)
                        if (!entry.isDirectory) {
                            zip.getInputStream(entry).use { it.copyTo(jarOut) }
                        }
                        jarOut.closeEntry()
                    }
                }
            }

            if (thirdPartyNotices.isNotEmpty()) {
                val mergedNotices = buildString {
                    thirdPartyNotices.forEach { (source, content) ->
                        append("===============================================================================\n")
                        append("NOTICE from ").append(source).append('\n')
                        append("===============================================================================\n\n")
                        append(content).append("\n\n")
                    }
                }
                writeTextEntry(jarOut, seenEntries, "META-INF/THIRD-PARTY-NOTICES.txt", mergedNotices.toByteArray(Charsets.UTF_8))
            }

            if (thirdPartyLicenses.isNotEmpty()) {
                val mergedLicenses = buildString {
                    thirdPartyLicenses.forEach { (source, content) ->
                        append("===============================================================================\n")
                        append("LICENSE from ").append(source).append('\n')
                        append("===============================================================================\n\n")
                        append(content).append("\n\n")
                    }
                }
                writeTextEntry(jarOut, seenEntries, "META-INF/THIRD-PARTY-LICENSES.txt", mergedLicenses.toByteArray(Charsets.UTF_8))
            }

            // Write merged META-INF/services/* entries.
            if (serviceMap.isNotEmpty() && seenEntries.add("META-INF/services/")) {
                jarOut.putNextEntry(ZipEntry("META-INF/services/"))
                jarOut.closeEntry()
            }
            for ((path, implementations) in serviceMap) {
                jarOut.putNextEntry(ZipEntry(path))
                jarOut.write((implementations.joinToString("\n") + "\n").toByteArray(Charsets.UTF_8))
                jarOut.closeEntry()
            }
        }
    }

    private fun writeTextEntry(jarOut: JarOutputStream, seenEntries: MutableSet<String>, path: String, bytes: ByteArray) {
        if (!seenEntries.add(path)) return
        jarOut.putNextEntry(ZipEntry(path))
        jarOut.write(bytes)
        jarOut.closeEntry()
    }

    private fun isTopLevelMetaInfNotice(name: String): Boolean {
        if (!name.startsWith("META-INF/", ignoreCase = true)) return false
        val rest = name.substring("META-INF/".length)
        if ('/' in rest) return false
        return rest.equals("NOTICE", ignoreCase = true) ||
            rest.equals("NOTICE.txt", ignoreCase = true) ||
            rest.equals("NOTICE.md", ignoreCase = true)
    }

    private fun isTopLevelMetaInfLicense(name: String): Boolean {
        if (!name.startsWith("META-INF/", ignoreCase = true)) return false
        val rest = name.substring("META-INF/".length)
        if ('/' in rest) return false
        return rest.equals("LICENSE", ignoreCase = true) ||
            rest.equals("LICENSE.txt", ignoreCase = true) ||
            rest.equals("LICENSE.md", ignoreCase = true)
    }

    private fun isExcludedEntry(name: String): Boolean {
        if (name.startsWith("bin/2.")) return true
        if (name.startsWith("META-INF/") && name.indexOf('/', startIndex = "META-INF/".length) == -1) {
            if (name.endsWith(".SF", ignoreCase = true) ||
                name.endsWith(".DSA", ignoreCase = true) ||
                name.endsWith(".RSA", ignoreCase = true)
            ) {
                return true
            }
        }
        return false
    }
}

val packageClapiJar = tasks.register<PackageClapiJarTask>("packageClapiJar") {
    val desktopJarTask = tasks.named<org.gradle.jvm.tasks.Jar>("desktopJar")
    val desktopRuntimeClasspath = configurations.named("desktopRuntimeClasspath")

    mainClassName.set("io.github.makeforge.clapi.ui.desktop.MainKt")
    mainJar.set(desktopJarTask.flatMap { it.archiveFile })
    licenseFile.set(rootProject.file("LICENSE"))
    noticeFile.set(rootProject.file("NOTICE"))
    runtimeClasspath.from(desktopRuntimeClasspath)
    outputJar.set(layout.buildDirectory.file("distribute/Clapi-${project.version}.jar"))

    description = "Builds a runnable fat JAR at build/distribute/Clapi-{version}.jar"
    group = "distribution"
}

/**
 * Helper used by both fat-JAR and jlink native-distributable runtime smoke verification tasks.
 */
fun runPackagedSmokeSuite(
    launcherPrefix: List<String>,
    workDir: File,
    runStdioMcp: Boolean,
    runShutdownOnly: Boolean,
) {
    fun runChecked(label: String, args: List<String>, timeoutSec: Long = 60L): String {
        val pb = ProcessBuilder(launcherPrefix + args).directory(workDir).redirectErrorStream(true)
        val proc = pb.start()
        val output = proc.inputStream.bufferedReader().readText()
        if (!proc.waitFor(timeoutSec, TimeUnit.SECONDS)) {
            proc.destroyForcibly()
            throw GradleException("$label timed out after ${timeoutSec}s. Output:\n$output")
        }
        if (proc.exitValue() != 0) {
            throw GradleException("$label failed with exit=${proc.exitValue()}. Output:\n$output")
        }
        return output
    }

    val smokeDir = Files.createTempDirectory("clapi-packaged-smoke-").toFile()
    try {
        if (runShutdownOnly) {
            runChecked("shutdown-smoke-test", listOf("--shutdown-smoke-test", "--dir", smokeDir.absolutePath))
        }

        val packagedOut = runChecked("packaged-smoke-test", listOf("--packaged-smoke-test", "--dir", smokeDir.absolutePath))
        if (!packagedOut.contains("CLAPI_SMOKE_OK checks=STORAGE_ROUNDTRIP,SCRIPT_ENGINE_V8,APP_DISPOSE")) {
            throw GradleException("packaged-smoke-test missing CLAPI_SMOKE_OK marker. Output:\n$packagedOut")
        }

        // Verify --mcp-http-headless live HTTP JSON-RPC initialize + clean stdin-EOF exit
        val mcpHttpDir = File(smokeDir, "mcp-http").apply { mkdirs() }
        val httpProc = ProcessBuilder(
            launcherPrefix + listOf("--mcp-http-headless", "--mcp-port", "0", "--dir", mcpHttpDir.absolutePath)
        ).directory(workDir).redirectErrorStream(true).start()
        try {
            val reader = httpProc.inputStream.bufferedReader()
            var readyUrl: String? = null
            val allLines = mutableListOf<String>()
            val deadline = System.currentTimeMillis() + 30_000L
            while (System.currentTimeMillis() < deadline) {
                val line = reader.readLine() ?: break
                allLines += line
                val match = Regex("""CLAPI_MCP_READY\s+url=(\S+)""").find(line)
                if (match != null) {
                    readyUrl = match.groupValues[1]
                    break
                }
            }
            if (readyUrl == null) {
                throw GradleException("mcp-http-headless did not print CLAPI_MCP_READY. Output:\n${allLines.joinToString("\n")}")
            }

            val conn = URI(readyUrl).toURL().openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.connectTimeout = 5_000
            conn.readTimeout = 5_000
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("Accept", "application/json, text/event-stream")
            val initBody = """{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-06-18","capabilities":{},"clientInfo":{"name":"smoke","version":"1.0"}}}"""
            conn.outputStream.use { it.write(initBody.toByteArray()) }
            val code = conn.responseCode
            val respBody = (if (code in 200..299) conn.inputStream else conn.errorStream)?.bufferedReader()?.readText().orEmpty()
            conn.disconnect()
            if (code != 200 || !respBody.contains("protocolVersion")) {
                throw GradleException("mcp-http-headless initialize failed: HTTP $code body=$respBody")
            }

            // Trigger clean shutdown via stdin EOF
            httpProc.outputStream.close()
            if (!httpProc.waitFor(15, TimeUnit.SECONDS)) {
                httpProc.destroyForcibly()
                throw GradleException("mcp-http-headless did not exit cleanly after stdin EOF")
            }
            if (httpProc.exitValue() != 0) {
                throw GradleException("mcp-http-headless exited with non-zero code ${httpProc.exitValue()}")
            }
        } finally {
            if (httpProc.isAlive) httpProc.destroyForcibly()
        }

        if (runStdioMcp) {
            val mcpStdioDir = File(smokeDir, "mcp-stdio").apply { mkdirs() }
            val stdioProc = ProcessBuilder(
                launcherPrefix + listOf("--mcp", "--dir", mcpStdioDir.absolutePath)
            ).directory(workDir).start()
            try {
                val initLine = """{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-06-18","capabilities":{},"clientInfo":{"name":"smoke","version":"1.0"}}}"""
                stdioProc.outputStream.bufferedWriter().use { w ->
                    w.write(initLine)
                    w.newLine()
                    w.flush()
                }
                val stdout = stdioProc.inputStream.bufferedReader().readText()
                val stderr = stdioProc.errorStream.bufferedReader().readText()
                if (!stdioProc.waitFor(20, TimeUnit.SECONDS)) {
                    stdioProc.destroyForcibly()
                    throw GradleException("mcp stdio smoke test timed out. stdout=$stdout stderr=$stderr")
                }
                if (stdioProc.exitValue() != 0 || !stdout.contains("protocolVersion")) {
                    throw GradleException("mcp stdio smoke test failed (exit=${stdioProc.exitValue()}). stdout=$stdout stderr=$stderr")
                }
            } finally {
                if (stdioProc.isAlive) stdioProc.destroyForcibly()
            }
        }
    } finally {
        smokeDir.deleteRecursively()
    }
}

/**
 * Executes the exact fat JAR produced for release through legal-file verification,
 * storage/Javet V8 packaged smoke checks, and live MCP HTTP & stdio handshakes.
 */
tasks.register("verifyPackagedRuntime") {
    dependsOn(packageClapiJar)
    notCompatibleWithConfigurationCache("Inspects fat JAR legal files and launches external smoke test processes")
    val jarFileProvider = packageClapiJar.flatMap { it.outputJar }
    val javaToolchains = project.extensions.getByType<JavaToolchainService>()
    val javaExecutable = javaToolchains.launcherFor {
        languageVersion.set(JavaLanguageVersion.of(25))
    }.get().executablePath.asFile.absolutePath
    val rootDirFile = project.rootDir

    description = "Verifies packaged Clapi fat JAR legal files, storage/V8 smoke test, and MCP HTTP/stdio handshakes."
    group = "verification"

    doLast {
        val jarFile = jarFileProvider.get().asFile
        if (!jarFile.exists()) {
            throw GradleException("Packaged fat JAR not found at ${jarFile.absolutePath}")
        }
        ZipFile(jarFile).use { zip ->
            if (zip.getEntry("META-INF/LICENSE") == null) {
                throw GradleException("Missing META-INF/LICENSE in ${jarFile.name}")
            }
            if (zip.getEntry("META-INF/NOTICE") == null) {
                throw GradleException("Missing META-INF/NOTICE in ${jarFile.name}")
            }
        }
        runPackagedSmokeSuite(
            launcherPrefix = listOf(javaExecutable, "-jar", jarFile.absolutePath),
            workDir = rootDirFile,
            runStdioMcp = true,
            runShutdownOnly = true,
        )
    }
}

/**
 * Executes the jlink-trimmed native distributable launcher produced by `createDistributable`
 * to verify JPMS modules, native libs, bundled legal files, storage/V8, and MCP HTTP handshake.
 */
tasks.register("verifyDistributableRuntime") {
    dependsOn("createDistributable")
    notCompatibleWithConfigurationCache("Inspects unpacked native distributable and launches native smoke processes")
    val appDirProvider = layout.buildDirectory.dir("compose/binaries/main/app")
    val rootDirFile = project.rootDir

    description = "Verifies unpacked jlink native distributable legal files, storage/V8 smoke test, and MCP HTTP handshake."
    group = "verification"

    doLast {
        val appDir = appDirProvider.get().asFile
        if (!appDir.exists()) {
            throw GradleException("Unpacked distributable directory not found at ${appDir.absolutePath}")
        }
        val osName = System.getProperty("os.name").lowercase()
        val launcher: File = when {
            osName.contains("mac") -> File(appDir, "Clapi.app/Contents/MacOS/Clapi")
            osName.contains("win") -> File(appDir, "Clapi/Clapi.exe")
            else -> File(appDir, "Clapi/bin/Clapi")
        }
        if (!launcher.exists()) {
            val listing = appDir.walkTopDown().maxDepth(4).joinToString("\n") { it.relativeTo(appDir).path }
            throw GradleException("Native launcher not found at ${launcher.absolutePath}. Found:\n$listing")
        }
        val hasLicense = appDir.walkTopDown().any { it.isFile && it.name.equals("LICENSE", ignoreCase = true) }
        val hasNotice = appDir.walkTopDown().any { it.isFile && it.name.equals("NOTICE", ignoreCase = true) }
        if (!hasLicense || !hasNotice) {
            throw GradleException("Missing bundled LICENSE ($hasLicense) or NOTICE ($hasNotice) under ${appDir.absolutePath}")
        }
        runPackagedSmokeSuite(
            launcherPrefix = listOf(launcher.absolutePath),
            workDir = rootDirFile,
            runStdioMcp = false,
            runShutdownOnly = false,
        )
    }
}

tasks.register<Exec>("runClapiJar") {
    dependsOn("packageClapiJar")
    val jarFile = layout.buildDirectory.file("distribute/Clapi-${project.version}.jar")
    val iconFile = project.file("src/desktopMain/resources/icons/clapi-icon-256.png")
    val isMac = System.getProperty("os.name").lowercase().contains("mac")

    workingDir = project.rootDir
    val cmd = mutableListOf("java")
    if (isMac) {
        cmd += "-Xdock:name=Clapi"
        cmd += "-Xdock:icon=${iconFile.absolutePath}"
    }
    cmd += "-jar"
    cmd += jarFile.get().asFile.absolutePath
    if (System.getProperty("os.name").lowercase().contains("linux")) {
        environment("MALLOC_ARENA_MAX", "2")
    }
    commandLine(cmd)
    description = "Runs the packaged Clapi fat JAR (macOS uses app dock icon/name)."
    group = "application"
}

tasks.withType<Test>().configureEach {
    useJUnit()
    systemProperty("java.util.prefs.userRoot", layout.buildDirectory.dir("tmp/prefs").get().asFile.absolutePath)
    // When running Roborazzi tasks, skip regular unit/integration tests (like sample-server tests)
    val isRoborazziRun = gradle.startParameter.taskNames.any {
        it.contains("Roborazzi", ignoreCase = true) || it.contains("Screenshots", ignoreCase = true)
    }
    if (isRoborazziRun) {
        filter {
            // Roborazzi generates test classes under the `roborazzi` package or ending in `PreviewTest`
            includeTestsMatching("*Roborazzi*")
            includeTestsMatching("*Preview*")
        }
    }
}

tasks.register("analyzeReleaseSize") {
    group = "verification"
    description = "Analyzes desktopRuntimeClasspath dependencies, fat JAR contents, and native distribution sizes."

    dependsOn("packageClapiJar")
    notCompatibleWithConfigurationCache("Inspects runtime artifacts and archive files directly")

    val runtimeConfig = configurations.named("desktopRuntimeClasspath")
    val buildDirProvider = layout.buildDirectory
    val versionStr = project.version.toString()

    doLast {
        val buildDir = buildDirProvider.get().asFile
        val resolvedArtifacts = runtimeConfig.get().resolvedConfiguration.resolvedArtifacts

        val dependencySizes = resolvedArtifacts.map { artifact ->
            val id = artifact.moduleVersion.id
            "${id.group}:${id.name}:${id.version}" to artifact.file.length()
        }.sortedByDescending { it.second }

        val totalDepsBytes = dependencySizes.sumOf { it.second }

        println("\n=====================================================================")
        println(" 1. DESKTOP RUNTIME DEPENDENCIES (${dependencySizes.size} artifacts)")
        println("=====================================================================")
        println(String.format("Total Input JARs Size: %.2f MB\n", totalDepsBytes / (1024.0 * 1024.0)))

        dependencySizes.take(25).forEachIndexed { index, (name, bytes) ->
            val mb = bytes / (1024.0 * 1024.0)
            val pct = if (totalDepsBytes > 0) (bytes.toDouble() / totalDepsBytes) * 100 else 0.0
            println(String.format("%2d. %6.2f MB (%5.1f%%)  %s", index + 1, mb, pct, name))
        }

        val fatJar = File(buildDir, "distribute/Clapi-$versionStr.jar")
        if (fatJar.exists()) {
            println("\n=====================================================================")
            println(" 2. FINAL FAT JAR ANALYSIS: ${fatJar.name}")
            println("=====================================================================")
            println(String.format("Compressed File Size on Disk: %.2f MB", fatJar.length() / (1024.0 * 1024.0)))

            val packageCompressed = mutableMapOf<String, Long>()
            val packageUncompressed = mutableMapOf<String, Long>()
            val largestEntries = mutableListOf<Triple<String, Long, Long>>()

            ZipFile(fatJar).use { zip ->
                val entries = zip.entries()
                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()
                    if (entry.isDirectory) continue

                    val comp = entry.compressedSize.coerceAtLeast(0L)
                    val uncomp = entry.size.coerceAtLeast(0L)
                    largestEntries.add(Triple(entry.name, comp, uncomp))

                    val parts = entry.name.split("/")
                    val groupKey = when {
                        parts.size >= 4 -> parts.take(3).joinToString(".")
                        parts.size > 1 -> parts.dropLast(1).joinToString("/")
                        else -> "(root)"
                    }
                    packageCompressed[groupKey] = (packageCompressed[groupKey] ?: 0L) + comp
                    packageUncompressed[groupKey] = (packageUncompressed[groupKey] ?: 0L) + uncomp
                }
            }

            println("\nTop 15 Packages / Directories inside Fat JAR (Compressed / Uncompressed):")
            packageCompressed.entries.sortedByDescending { it.value }.take(15).forEachIndexed { idx, (pkg, compBytes) ->
                val uncompBytes = packageUncompressed[pkg] ?: 0L
                println(
                    String.format(
                        "%2d. %6.2f MB comp  |  %6.2f MB raw  -  %s",
                        idx + 1,
                        compBytes / (1024.0 * 1024.0),
                        uncompBytes / (1024.0 * 1024.0),
                        pkg
                    )
                )
            }

            println("\nTop 10 Largest Single Files inside Fat JAR:")
            largestEntries.sortedByDescending { it.second }.take(10).forEachIndexed { idx, (name, comp, uncomp) ->
                println(
                    String.format(
                        "%2d. %6.2f MB comp  |  %6.2f MB raw  -  %s",
                        idx + 1,
                        comp / (1024.0 * 1024.0),
                        uncomp / (1024.0 * 1024.0),
                        name
                    )
                )
            }
        }

        val binariesDir = File(buildDir, "compose/binaries")
        if (binariesDir.exists()) {
            val installers = binariesDir.walkTopDown()
                .filter { it.isFile && it.extension.lowercase() in setOf("dmg", "msi", "deb", "rpm", "exe") }
                .toList()

            if (installers.isNotEmpty()) {
                println("\n=====================================================================")
                println(" 3. NATIVE DISTRIBUTIONS (compose/binaries)")
                println("=====================================================================")
                installers.forEach { file ->
                    println(String.format(" - %6.2f MB  %s", file.length() / (1024.0 * 1024.0), file.relativeTo(buildDir)))
                }
            }
        }
        println("\n=====================================================================\n")
    }
}