plugins {
    `java-library`
}

dependencies {
    implementation(project(":bukkit"))
    compileOnly(libs.folia.api)
}
