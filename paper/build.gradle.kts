plugins {
    `java-library`
}

dependencies {
    implementation(project(":bukkit"))
    compileOnly(libs.paper.api)
    compileOnly(libs.paper.mojangapi)
}
