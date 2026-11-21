rootProject.name = "Clapi"

pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.PREFER_PROJECT)
    repositories {
        google()
        mavenCentral()
    }
}

include(
    ":editor-core",
    ":editor-ui",
    ":core-model",
    ":core-network",
    ":core-storage",
    ":core-scripting",
    ":qa-tests",
    ":ui-shared",
    ":ui-desktop",
    ":sample-server"
)
