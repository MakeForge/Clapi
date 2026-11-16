import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
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
                implementation(project(":ui-shared"))
                implementation(libs.coroutines.core)
                implementation(libs.coroutines.swing)
            }
        }
        val desktopTest by getting {
            dependencies {
                implementation(libs.kotlin.test)
                implementation(libs.junit4)
                implementation(libs.compose.ui.test.junit4.desktop)
                implementation(project(":sample-server"))
                implementation(libs.ktor.server.core)
                implementation(libs.ktor.server.netty)
                implementation(libs.ktor.client.core)
                implementation(libs.ktor.client.cio)
                implementation(libs.serialization.json)
                implementation(libs.compose.ui.tooling.preview)
                implementation(libs.roborazzi.compose.desktop.preview.scanner.support)
                implementation(libs.composable.preview.scanner.android)
            }
        }
    }
}

roborazzi {
    @OptIn(ExperimentalRoborazziApi::class)
    generateComposePreviewDesktopTests {
        enable = true
        packages = listOf("io.github.makeforge.clapi.ui.desktop.preview")
//        outputDir.set(
//            rootProject.file("docs/screenshots")
//        )
    }
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
    doLast {
        val binariesDir = layout.buildDirectory.dir("compose/binaries").get().asFile
        if (binariesDir.exists()) {
            binariesDir.walkTopDown()
                .filter { it.isFile && it.extension == "jar" && it.name.startsWith("protoc-jar-") }
                .forEach { stripEmbeddedJarBlobs(it) }
        }
    }
}

// ─── Desktop application + native packaging ───────────────────────────────────
// Version flows from gradle.properties → root build.gradle.kts → project.version
// Change it in ONE place: gradle.properties  appVersion=x.y.z
compose.desktop {
    application {
        mainClass = "io.github.makeforge.clapi.ui.desktop.MainKt"

        nativeDistributions {
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
                "jdk.unsupported",
            )

            // Target every platform; each CI runner builds only what it supports.
            targetFormats(
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.Dmg,  // macOS
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.Msi,  // Windows
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.Deb,  // Linux (Debian)
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.Rpm,  // Linux (Fedora)
            )

            packageName    = "Clapi"
            packageVersion = project.version.toString()   // e.g. "1.0.0"
            description    = "Clapi – API testing for every platform"
            copyright      = "Clapi Contributors"
            vendor         = "Clapi"

            macOS {
                bundleID = "io.github.makeforge.clapi"
                iconFile.set(project.file("src/desktopMain/resources/icons/clapi.icns"))
                // dmgPackageVersion inherits packageVersion automatically
            }
            windows {
                dirChooser         = true
                perUserInstall     = true
                shortcut           = true
                menuGroup          = "Clapi"
                iconFile.set(project.file("src/desktopMain/resources/icons/clapi.ico"))
            }
            linux {
                packageName        = "clapi"
                debMaintainer      = "makeforge@yahoo.com"
                appCategory        = "Development"
                iconFile.set(project.file("src/desktopMain/resources/icons/clapi-icon-256.png"))
            }
        }
    }
}

// ─── Runnable fat JAR task ───────────────────────────────────────────────────
// Produces ui-desktop/build/distribute/Clapi-{version}.jar
// Includes app classes + runtime classpath and sets Main-Class so `java -jar` works.
tasks.register<org.gradle.jvm.tasks.Jar>("packageClapiJar") {
    val mainClassName = "io.github.makeforge.clapi.ui.desktop.MainKt"
    val desktopJarTask = tasks.named<org.gradle.jvm.tasks.Jar>("desktopJar")
    val desktopRuntimeClasspath = configurations.named("desktopRuntimeClasspath")

    notCompatibleWithConfigurationCache("Merges META-INF/services entries at execution time from runtime JARs")

    dependsOn(desktopJarTask)

    archiveBaseName.set("Clapi")
    archiveVersion.set(project.version.toString())
    archiveClassifier.set("")
    destinationDirectory.set(layout.buildDirectory.dir("distribute"))
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE

    manifest {
        attributes["Main-Class"] = mainClassName
    }

    // GraalVM Truffle languages (js, regex, etc.) each register themselves via
    // META-INF/services/. DuplicatesStrategy.EXCLUDE would silently drop all but the
    // first entry, causing "No language for id regex found" at runtime.
    // Fix: exclude services from all sources, merge them manually in doFirst, then
    // include the merged result (added first so it wins the EXCLUDE dedup).
    val mergedServicesDir = layout.buildDirectory.dir("tmp/merged-services")
    from(mergedServicesDir)

    // Include compiled classes/resources from the desktop jar (services merged separately).
    from(desktopJarTask.map { zipTree(it.archiveFile.get().asFile) }) {
        exclude("META-INF/services/**")
    }

    // Include all runtime dependency jars so the artifact is self-contained (services merged separately).
    from(
        desktopRuntimeClasspath.map { files ->
            files
                .filter { it.name.endsWith(".jar") }
                .map { zipTree(it) }
        }
    ) {
        exclude("META-INF/services/**")
    }

    // Drop signature metadata that becomes invalid when jars are merged,
    // and strip obsolete protoc 2.x binaries (ProtocJarParser uses 3.11.4).
    exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA")
    exclude("bin/2.*/**")

    // Collect and merge all META-INF/services entries from every input JAR so that all
    // service providers are present in the service loader.
    doFirst {
        val outDir = mergedServicesDir.get().asFile
        outDir.deleteRecursively()

        val serviceMap = mutableMapOf<String, LinkedHashSet<String>>()

        val allJars = buildList {
            add(desktopJarTask.get().archiveFile.get().asFile)
            addAll(desktopRuntimeClasspath.get().filter { it.name.endsWith(".jar") })
        }

        allJars.forEach { jarFile ->
            if (!jarFile.exists()) return@forEach
            ZipFile(jarFile).use { zip ->
                val entries = zip.entries()
                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()
                    if (!entry.name.startsWith("META-INF/services/") || entry.isDirectory) continue

                    val lines = zip.getInputStream(entry).bufferedReader().use { reader ->
                        reader.readLines()
                            .filter { line -> line.isNotBlank() && !line.trimStart().startsWith("#") }
                    }
                    serviceMap.getOrPut(entry.name) { linkedSetOf() }.addAll(lines)
                }
            }
        }

        serviceMap.forEach { (path, implementations) ->
            val file = outDir.resolve(path)
            file.parentFile.mkdirs()
            file.writeText(implementations.joinToString("\n") + "\n")
        }
    }

    description = "Builds a runnable fat JAR at build/distribute/Clapi-{version}.jar"
    group = "distribution"
}

/**
 * Executes the exact fat JAR produced for release in headless shutdown mode.
 * This catches incomplete/stale runtime contents (for example, a missing
 * generated coroutine lambda) before an artifact is uploaded.
 */
tasks.register<Exec>("verifyPackagedRuntime") {
    dependsOn("packageClapiJar")
    val jarFile = layout.buildDirectory.file("distribute/Clapi-${project.version}.jar")

    workingDir = project.rootDir
    commandLine(
        "java",
        "-jar",
        jarFile.get().asFile.absolutePath,
        "--shutdown-smoke-test",
    )
    description = "Runs the packaged Clapi JAR through its headless shutdown path."
    group = "verification"
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
    commandLine(cmd)
    description = "Runs the packaged Clapi fat JAR (macOS uses app dock icon/name)."
    group = "application"
}

tasks.withType<Test>().configureEach {
    useJUnit()
    systemProperty("java.util.prefs.userRoot", layout.buildDirectory.dir("tmp/prefs").get().asFile.absolutePath)
}

tasks.withType<JavaExec>().configureEach {
    if (name == "run") {
        jvmArgs(
            "-Dapple.awt.application.name=Clapi",
            "-Djava.awt.application.name=Clapi",
            "-Xdock:name=Clapi",
        )
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