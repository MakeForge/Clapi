plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.kotlinSerialization)
}

kotlin {
    jvm("desktop")

    sourceSets {
        commonMain.dependencies {
            implementation(project(":core-model"))
            api(project(":core-scripting"))
            implementation(libs.coroutines.core)
            implementation(libs.serialization.json)
            implementation(libs.kotlinx.datetime)
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.serialization.kotlinx.json)
            implementation(libs.ktor.client.logging)
            implementation(libs.ktor.client.websockets)
            implementation(libs.ktor.client.auth)
            implementation(libs.koin.core)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.coroutines.test)
            implementation(libs.ktor.client.mock)
        }
        val desktopMain by getting {
            dependencies {
                implementation(libs.ktor.client.cio)
                implementation(libs.grpc.okhttp)
                implementation(libs.grpc.protobuf)
                implementation(libs.grpc.stub)
                implementation(libs.protobuf.java)
                implementation(libs.protobuf.java.util)
                implementation(libs.protoc.jar)
                implementation(libs.coroutines.guava)
                implementation(libs.keepassxc.proxy.access)
                implementation(libs.keeper.secrets.manager.core)
            }
        }
        val desktopTest by getting {
            dependencies {
                implementation(libs.grpc.netty.shaded)
            }
        }
    }
}
