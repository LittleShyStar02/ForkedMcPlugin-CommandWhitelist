plugins {
    `java-library`
}

dependencies {
    implementation(project(":common"))
    compileOnly(libs.velocity.api)
    annotationProcessor(libs.velocity.api)
    compileOnly(libs.bstats.velocity)
}
