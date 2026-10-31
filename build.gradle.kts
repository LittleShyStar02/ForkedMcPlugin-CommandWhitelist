import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
    java
    id("com.gradleup.shadow") version "9.6.1"
}

val pluginVersion = "2.15.0"

allprojects {
    group = "eu.endermite.commandwhitelist"
    version = pluginVersion

    repositories {
        mavenCentral()
        maven("https://repo.papermc.io/repository/maven-public/")
        maven("https://hub.spigotmc.org/nexus/content/repositories/snapshots/")
        maven("https://repo.bsdevelopment.org/releases")
    }
}

// Every module targets the oldest Java its platform can run on, so one jar loads everywhere from 1.13.
val javaTargets = mapOf("common" to 8, "bukkit" to 8, "paper" to 8, "waterfall" to 8, "folia" to 17, "velocity" to 17)

subprojects {
    apply(plugin = "java")

    extensions.configure<JavaPluginExtension> {
        disableAutoTargetJvm()
    }

    tasks.withType<JavaCompile>().configureEach {
        options.release.set(javaTargets[project.name] ?: 8)
        options.encoding = "UTF-8"
        options.compilerArgs.add("-Xlint:-options")
    }

    tasks.withType<ProcessResources>().configureEach {
        val props = mapOf("version" to pluginVersion)
        inputs.properties(props)
        filesMatching(listOf("plugin.yml", "bungee.yml", "velocity-plugin.json")) { expand(props) }
    }
}

dependencies {
    implementation(project(":velocity")) { isTransitive = false }
    implementation(libs.bstats.velocity)
}

// Step two of the build: the bundle already has its libraries relocated, Velocity keeps the Adventure of the proxy.
tasks.named<ShadowJar>("shadowJar") {
    dependsOn(":bundle:shadowJar")
    from(zipTree(project(":bundle").tasks.named<ShadowJar>("shadowJar").flatMap { it.archiveFile }))
    archiveBaseName.set("CommandWhitelist")
    archiveClassifier.set("")
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    relocate("org.bstats", "eu.endermite.commandwhitelist.lib.bstats")
    dependencies {
        exclude(dependency("com.google.code.gson:.*"))
    }
    exclude("META-INF/maven/**", "META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA", "module-info.class")
    manifest {
        // the classes are not mapped to anything, Paper does not have to remap the plugin
        attributes("paperweight-mappings-namespace" to "mojang")
    }
}

tasks.named("assemble") { dependsOn("shadowJar") }
tasks.named<Jar>("jar") { enabled = false }
