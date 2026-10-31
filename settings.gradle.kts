pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

rootProject.name = "CommandWhitelist"

include("common", "bukkit", "paper", "folia", "velocity", "waterfall", "bundle")
