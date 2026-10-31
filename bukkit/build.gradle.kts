plugins {
    `java-library`
}

dependencies {
    api(project(":common"))
    compileOnly(libs.spigot.api)
    implementation(libs.adventure.minimessage)
    implementation(libs.adventure.platform.bukkit)
    implementation(libs.bstats.bukkit)
}
