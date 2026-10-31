plugins {
    `java-library`
}

dependencies {
    implementation(project(":bukkit")) { isTransitive = false }
    implementation(project(":common"))
    implementation(libs.adventure.api)
    implementation(libs.adventure.minimessage)
    implementation(libs.adventure.platform.bungeecord)
    implementation(libs.bstats.bungeecord)
    compileOnly(libs.waterfall.api)
}
