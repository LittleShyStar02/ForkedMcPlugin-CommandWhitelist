import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
    java
    id("com.gradleup.shadow")
}

// Step one of the build: everything except Velocity, with the libraries moved to a private package.
dependencies {
    implementation(project(":common"))
    implementation(project(":bukkit"))
    implementation(project(":paper"))
    implementation(project(":folia"))
    implementation(project(":waterfall"))
}

tasks.named<ShadowJar>("shadowJar") {
    archiveClassifier.set("")
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    val lib = "eu.endermite.commandwhitelist.lib"
    relocate("net.kyori", "$lib.kyori")
    relocate("org.yaml.snakeyaml", "$lib.snakeyaml")
    relocate("io.github.thatsmusic99.configurationmaster", "$lib.configurationmaster")
    exclude("META-INF/maven/**", "META-INF/versions/**", "module-info.class")
}
