// Modified by MakeForge (2026)
plugins {
    alias(libs.plugins.kotlinMultiplatform) apply false
    alias(libs.plugins.kotlinSerialization) apply false
    alias(libs.plugins.composeMultiplatform) apply false
    alias(libs.plugins.composeCompiler) apply false
    alias(libs.plugins.nucleus) apply false
    alias(libs.plugins.cyclonedx.bom)
}

tasks.named<org.cyclonedx.gradle.CyclonedxAggregateTask>("cyclonedxBom") {
    schemaVersion = org.cyclonedx.Version.VERSION_16
    projectType = org.cyclonedx.model.Component.Type.APPLICATION
    jsonOutput = layout.buildDirectory.file("reports/sbom/bom.json")
}

allprojects {
    tasks.withType<org.cyclonedx.gradle.CyclonedxDirectTask>().configureEach {
        if (project.name in listOf("qa-tests", "sample-server")) {
            enabled = false
        } else {
            schemaVersion = org.cyclonedx.Version.VERSION_16
            projectType = org.cyclonedx.model.Component.Type.APPLICATION
            includeConfigs = listOf(
                "runtimeClasspath",
                "desktopRuntimeClasspath",
                "jvmRuntimeClasspath"
            )
            skipConfigs = listOf(
                "(?i).*test.*"
            )
        }
    }
}

// ─── Centralized version ─────────────────────────────────────────────────────
// Change the version only in gradle.properties: appVersion=x.y.z
val appVersion: String by project
val guavaVersion = libs.versions.guava.get()
val jsonVersion = libs.versions.json.get()

allprojects {
    group = "io.github.makeforge"
    version = appVersion

    configurations.configureEach {
        resolutionStrategy.eachDependency {
            if (requested.group == "com.google.guava" && requested.name == "guava") {
                useVersion(guavaVersion)
                because("Fix CVE-2026-102554")
            }
            if (requested.group == "org.json" && requested.name == "json") {
                useVersion(jsonVersion)
                because("Fix CVE-2026-59171")
            }
        }
    }

    tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask<*>>().configureEach {
        compilerOptions {
            freeCompilerArgs.add("-Xskip-metadata-version-check")
        }
    }

    plugins.withType<org.jetbrains.kotlin.gradle.plugin.KotlinBasePluginWrapper> {
        extensions.configure<org.jetbrains.kotlin.gradle.dsl.KotlinBaseExtension> {
            jvmToolchain(25)
        }
    }

    tasks.withType<Test>().configureEach {
        systemProperty("clapi.dir", "${project.layout.buildDirectory.get().asFile.absolutePath}/test-clapi-storage")
        systemProperty("skiko.data.path", "/tmp/skiko")
    }
}

// Force a single instance of every CodeMirror sub-package so that
// @codemirror/state instanceof checks pass in Node test environments.
plugins.withType<org.jetbrains.kotlin.gradle.targets.js.yarn.YarnPlugin> {
    extensions.configure<org.jetbrains.kotlin.gradle.targets.js.yarn.YarnRootExtension> {
        // Core — must match nested deps of @codemirror/language + @codemirror/view
        resolution("@codemirror/state",    "6.6.0")
        resolution("@codemirror/view",     "6.38.6")
        resolution("@codemirror/language", "6.11.3")
        resolution("@codemirror/commands", "6.8.0")
        resolution("@codemirror/lint",     "6.9.0")
        // Language packs
        resolution("@codemirror/lang-json",       "6.0.2")
        resolution("@codemirror/lang-xml",        "6.1.0")
        resolution("@codemirror/lang-html",       "6.4.9")
        resolution("@codemirror/lang-javascript", "6.2.3")
    }
}

// Convenience: start the Clapi sample API server from the repo root.
// Usage: ./gradlew runServer
tasks.register("runServer") {
    group = "application"
    description = "Starts the Clapi sample API server at http://localhost:8080"
    // dependsOn(":sample-server:run")
}
