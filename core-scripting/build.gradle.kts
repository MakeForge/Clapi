// Modified by MakeForge (2026)
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
                implementation(libs.javet)
                val osName = System.getProperty("os.name").lowercase()
                val osArch = System.getProperty("os.arch").lowercase()
                val isArm64 = osArch == "aarch64" || osArch == "arm64"
                when {
                    osName.contains("linux") -> {
                        if (isArm64) {
                            runtimeOnly(libs.javet.v8.linux.arm64)
                        } else {
                            runtimeOnly(libs.javet.v8.linux.x8664)
                        }
                    }
                    osName.contains("mac") || osName.contains("darwin") -> {
                        if (isArm64) {
                            runtimeOnly(libs.javet.v8.macos.arm64)
                        } else {
                            runtimeOnly(libs.javet.v8.macos.x8664)
                        }
                    }
                    osName.contains("win") -> {
                        runtimeOnly(libs.javet.v8.windows.x8664)
                    }
                }
            }
        }
    }
}
