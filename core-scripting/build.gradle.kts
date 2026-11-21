plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.kotlinSerialization)
    id("org.jetbrains.kotlinx.kover") version "0.9.1"
}

kotlin {
    jvm("desktop")

    sourceSets {
        commonMain.dependencies {
            implementation(project(":core-model"))
            implementation(libs.coroutines.core)
            implementation(libs.serialization.json)
            implementation(libs.koin.core)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.coroutines.test)
        }

        val desktopMain by getting {
            dependencies {
                implementation(libs.graalvm.polyglot)
                implementation(libs.graalvm.js)
            }
        }
    }
}

// GraalVM Polyglot requires module access on standard JDK 21 (non-GraalVM JDK).
tasks.matching { it.name == "desktopTest" }
    .configureEach {
        (this as Test).jvmArgs(
            "-Dpolyglot.engine.WarnInterpreterOnly=false",
        )
    }
