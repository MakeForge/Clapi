// Modified by MakeForge (2026)
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.TaskAction
import org.gradle.process.ExecOperations
import javax.inject.Inject

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.kotlinSerialization)
}

kotlin {
    jvm {
        val testCompilation = compilations.getByName("test")
        compilations.create("loadTest") {
            associateWith(testCompilation)
            defaultSourceSet {
                kotlin.srcDir("src/loadTest/kotlin")
                resources.srcDir("src/loadTest/resources")
            }
        }
    }

    jvmToolchain(25)

    sourceSets {
        jvmTest {
            kotlin.srcDir("src/test/kotlin")
            resources.srcDir("src/test/resources")
            dependencies {
                implementation(project(":core-model"))
                implementation(project(":core-scripting"))
                implementation(project(":core-storage"))
                implementation(project(":core-network"))
                implementation(project(":ui-shared"))
                implementation(project(":sample-server"))
                implementation(libs.kotlin.test)
                implementation(libs.coroutines.test)
                implementation(libs.kotlinx.datetime)
                implementation(libs.serialization.json)
                implementation(libs.ktor.client.core)
                implementation(libs.ktor.client.content.negotiation)
                implementation(libs.ktor.serialization.kotlinx.json)
                implementation(libs.ktor.client.websockets)
                implementation(libs.ktor.client.cio)
                implementation(libs.ktor.server.core)
                implementation(libs.ktor.server.netty)
                implementation(libs.ktor.server.websockets)
                implementation(libs.ktor.server.content.negotiation)
                implementation(libs.grpc.netty.shaded)
                implementation(libs.grpc.protobuf)
                implementation(libs.grpc.stub)
                implementation(libs.protobuf.java)
                implementation(libs.protobuf.java.util)
                implementation(libs.protoc.jar)
                implementation(libs.coroutines.guava)
                implementation(libs.junit4)
                implementation(libs.keepassxc.proxy.access)
                implementation(libs.keeper.secrets.manager.core)
                implementation(libs.amqp.client)
            }
        }
    }
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.BaseKotlinCompile>().configureEach {
    if (name.startsWith("compileTestKotlin")) {
        val testClasspath = configurations.findByName("jvmTestCompileClasspath")
            ?: configurations.findByName("testCompileClasspath")
        if (testClasspath != null) {
            friendPaths.from(
                testClasspath.incoming.files.filter { file ->
                    file.name.startsWith("core-17.") && file.path.contains("keepersecurity")
                }
            )
        }
    }
}

tasks.withType<Test>().configureEach {
    useJUnit()
    dependsOn(":sample-server:installDist")
}



// 1. Define a Configuration-Cache compliant custom task
abstract class DockerComposeTask @Inject constructor(
    private val execOperations: ExecOperations
) : DefaultTask() {

    @get:InputDirectory
    @get:Optional
    abstract val dockerDirectory: DirectoryProperty

    @get:Input
    abstract val composeSubcommand: ListProperty<String> // e.g. ["up", "-d", "--wait"] or ["down", "-v", "--remove-orphans"]

    @TaskAction
    fun execute() {
        val dir = dockerDirectory.orNull?.asFile
        if (dir == null || !dir.exists() || !dir.isDirectory) {
            return
        }

        val composeFiles = dir.listFiles { file ->
            file.isFile && file.name.contains("compose") && (file.extension == "yml" || file.extension == "yaml")
        }?.sortedBy { it.name } ?: emptyList()

        if (composeFiles.isEmpty()) {
            return
        }

        val fileArgs = composeFiles.flatMap { listOf("-f", it.absolutePath) }
        val fullArgs = listOf("docker", "compose") + fileArgs + composeSubcommand.get()

        execOperations.exec {
            workingDir = dir
            commandLine = fullArgs
        }
    }
}

// 2. Register dockerComposeUp
val dockerComposeUp = tasks.register<DockerComposeTask>("dockerComposeUp") {
    group = "docker"
    description = "Discovers and spins up all compose files in docker/"
    dockerDirectory.set(layout.projectDirectory.dir("docker"))
    composeSubcommand.set(listOf("up", "-d", "--wait"))
}

// 3. Register dockerComposeDown
val dockerComposeDown = tasks.register<DockerComposeTask>("dockerComposeDown") {
    group = "docker"
    description = "Tears down all compose files in docker/"
    dockerDirectory.set(layout.projectDirectory.dir("docker"))
    composeSubcommand.set(listOf("down", "-v", "--remove-orphans"))
}

// 4. Hook into test lifecycle (disabled when CI manages compose up/down across multiple test modules)
val externalDockerCompose = providers.gradleProperty("externalDockerCompose")
    .map { it.equals("true", ignoreCase = true) }
    .getOrElse(false)

tasks.named<Test>("jvmTest") { // Or tasks.named<Test>("test")
    if (!externalDockerCompose) {
        dependsOn(dockerComposeUp)
        finalizedBy(dockerComposeDown)
    }
}

tasks.register<JavaExec>("runDummyServer") {
    group = "application"
    description = "Runs the DummyApiServer with HTTP, WebSocket, and gRPC endpoints for manual testing"
    classpath = kotlin.jvm().compilations.getByName("test").output.classesDirs +
        kotlin.jvm().compilations.getByName("test").runtimeDependencyFiles
    mainClass.set("io.github.makeforge.clapi.testsupport.DummyApiServerKt")
}

// 5. Dedicated out-of-process load & memory profiling test task (never runs on normal build/check)
val loadTestCompilation = kotlin.jvm().compilations.getByName("loadTest")
val javaToolchains = project.extensions.getByType<JavaToolchainService>()
val jdk25Home = javaToolchains.launcherFor {
    languageVersion.set(JavaLanguageVersion.of(25))
}.map { it.metadata.installationPath.asFile.absolutePath }

tasks.register<Test>("loadTest") {
    group = "verification"
    description = "Runs automated out-of-process load and memory profiling tests against the packaged Clapi fat JAR."
    useJUnit()
    dependsOn(":ui-desktop:packageClapiJar")

    testClassesDirs = loadTestCompilation.output.classesDirs
    classpath = loadTestCompilation.compileDependencyFiles +
        loadTestCompilation.runtimeDependencyFiles +
        loadTestCompilation.output.allOutputs

    val fatJarPath = project(":ui-desktop").layout.buildDirectory
        .file("distribute/Clapi-${project.version}.jar")
        .get().asFile.absolutePath
    val reportDir = layout.buildDirectory.dir("reports/load-test").get().asFile.absolutePath
    val profile = (project.findProperty("loadProfile") as String?) ?: "standard"

    systemProperty("clapi.jdkHome", jdk25Home.get())
    systemProperty("clapi.fatJar", fatJarPath)
    systemProperty("clapi.loadTest.reportDir", reportDir)
    systemProperty("clapi.loadTest.profile", profile)

    // Always execute when explicitly invoked and stream progress logs.
    outputs.upToDateWhen { false }
    testLogging {
        showStandardStreams = true
        events("passed", "skipped", "failed")
    }
}