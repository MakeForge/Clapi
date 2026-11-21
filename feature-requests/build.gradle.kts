plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.kotlinSerialization)
}

kotlin {
    jvm("desktop")

    sourceSets {
        commonMain.dependencies {
            implementation(project(":core-model"))
            implementation(project(":core-network"))
            implementation(project(":core-storage"))
            implementation(project(":core-scripting"))
            implementation(libs.coroutines.core)
            implementation(libs.koin.core)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.coroutines.test)
        }
    }
}
