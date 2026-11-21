// Modified by MakeForge (2026)
plugins {
    alias(libs.plugins.kotlinMultiplatform)
}

kotlin {
    jvm("desktop")

    sourceSets {
        commonMain.dependencies {
            api(project(":core-model"))
            implementation(libs.serialization.json)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}
