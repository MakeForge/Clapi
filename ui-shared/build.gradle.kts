plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

val generatedI18nDir = layout.buildDirectory.dir("generated/source/i18n/commonMain/kotlin")
val generatedBuildInfoDir = layout.buildDirectory.dir("generated/source/buildinfo/commonMain/kotlin")

val generateI18nBundles by tasks.registering {
    val inputDir = layout.projectDirectory.dir("src/commonMain/resources/i18n")
    val outputDir = generatedI18nDir

    inputs.dir(inputDir)
    outputs.dir(outputDir)

    doLast {
        val outFile = outputDir.get().file("io/github/makeforge/ui/shared/i18n/GeneratedTranslationBundles.kt").asFile
        outFile.parentFile.mkdirs()

        fun encodeKotlinString(raw: String): String {
            val builder = StringBuilder(raw.length + 32)
            builder.append('"')
            raw.forEach { ch ->
                when (ch) {
                    '\\' -> builder.append("\\\\")
                    '"' -> builder.append("\\\"")
                    '\n' -> builder.append("\\n")
                    '\r' -> Unit
                    '$' -> builder.append("\\$")
                    else -> builder.append(ch)
                }
            }
            builder.append('"')
            return builder.toString()
        }

        val en = inputDir.file("en.json").asFile.readText()
        val es = inputDir.file("es.json").asFile.readText()
        val fr = inputDir.file("fr.json").asFile.readText()
        val de = inputDir.file("de.json").asFile.readText()

        outFile.writeText(
            """
            package io.github.makeforge.clapi.ui.shared.i18n

            internal object GeneratedTranslationBundles {
                private val bundles: Map<String, String> = mapOf(
                    "en" to ${encodeKotlinString(en)},
                    "es" to ${encodeKotlinString(es)},
                    "fr" to ${encodeKotlinString(fr)},
                    "de" to ${encodeKotlinString(de)},
                )

                fun forCode(code: String): String? = bundles[code]
            }
            """.trimIndent() + "\n",
        )
    }
}

val generateBuildInfo by tasks.registering {
    val outputDir = generatedBuildInfoDir
    val appVersion = rootProject.findProperty("appVersion")?.toString() ?: "dev"

    inputs.property("appVersion", appVersion)
    outputs.dir(outputDir)

    doLast {
        val outFile = outputDir.get().file("io/github/makeforge/ui/shared/build/GeneratedBuildInfo.kt").asFile
        outFile.parentFile.mkdirs()

        outFile.writeText(
            """
            package io.github.makeforge.clapi.ui.shared.build

            internal object GeneratedBuildInfo {
                const val APP_VERSION: String = "$appVersion"
            }
            """.trimIndent() + "\n",
        )
    }
}

kotlin {
    jvm("desktop") {
        compilerOptions {
            freeCompilerArgs.add("-Xskip-metadata-version-check")
        }
    }

    compilerOptions {
        freeCompilerArgs.add("-Xskip-metadata-version-check")
    }

    sourceSets {
        val commonMain by getting {
            kotlin.srcDir(generatedI18nDir)
            kotlin.srcDir(generatedBuildInfoDir)
            dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.components.resources)
            implementation(compose.components.uiToolingPreview)

            api(project(":core-model"))
            api(project(":editor-core"))
            api(project(":editor-ui"))
            api(project(":core-network"))
            api(project(":core-storage"))
            implementation(project(":core-scripting"))
            implementation(libs.coroutines.core)
            implementation(libs.serialization.json)
            implementation(libs.ksafe)
            implementation(libs.kotlinx.datetime)
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.serialization.kotlinx.json)
            implementation(libs.ktor.client.logging)
            }
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.coroutines.test)
        }
        val desktopMain by getting {
            dependencies {
                implementation(libs.ksafe)
                implementation(libs.ktor.client.cio)
                implementation(libs.coroutines.swing)

                implementation(compose.desktop.currentOs)
                // 2. Provides the desktop rendering engine to the IDE
                implementation(compose.uiTooling)
            }
        }
    }
}

tasks.matching {
    it.name.startsWith("compileKotlin") || it.name == "compileCommonMainKotlinMetadata"
}.configureEach {
    dependsOn(generateI18nBundles)
    dependsOn(generateBuildInfo)
}

tasks.withType<Test>().configureEach {
    useJUnit()
    systemProperty("java.util.prefs.userRoot", layout.buildDirectory.dir("tmp/prefs").get().asFile.absolutePath)
}

compose.resources {
    packageOfResClass = "io.github.makeforge.clapi.ui.shared.generated.resources"
}
