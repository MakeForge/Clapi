plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.kotlinSerialization)
}

kotlin {
    jvm()

    jvmToolchain(21)

    sourceSets {
        jvmTest {
            kotlin.srcDir("src/test/kotlin")
            resources.srcDir("src/test/resources")
            dependencies {
                implementation(project(":core-model"))
                implementation(project(":core-scripting"))
                implementation(project(":core-storage"))
                implementation(project(":core-network"))
                implementation(project(":feature-requests"))
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
            }
        }
    }
}

tasks.withType<Test>().configureEach {
    useJUnit()
    dependsOn(":sample-server:installDist")
}
