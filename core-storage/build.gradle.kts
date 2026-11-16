plugins {
    alias(libs.plugins.kotlinMultiplatform)
}

kotlin {
    jvm("desktop")

    sourceSets {
        commonMain.dependencies {
            implementation(project(":core-model"))
            implementation(libs.coroutines.core)
            implementation(libs.serialization.json)
            implementation(libs.koin.core)
            implementation(libs.kaml)
        }
        val desktopMain by getting {
            dependencies {
                implementation(libs.sqldelight.runtime)
                implementation(libs.sqldelight.coroutines)
                implementation(libs.networknt.json.schema)
                implementation(libs.swagger.parser)
                implementation("io.swagger.core.v3:swagger-models:3.0.0")
            }
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.coroutines.test)
        }
    }
}
