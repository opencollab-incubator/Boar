dependencies {
    api(project(":common"))
    compileOnlyApi(libs.lombok)
    annotationProcessor(libs.lombok)

    compileOnly(libs.geyser.api)
    compileOnly(libs.geyser.core) {
        isTransitive = false
    }
    compileOnly(libs.mcprotocollib)
}

tasks {
    shadowJar {
        archiveFileName = "boar-geyser.jar"

        relocate("it.unimi.dsi.fastutil", "ac.boar.shaded.fastutil")
        relocate("com.fasterxml.jackson", "ac.boar.shaded.jackson")
        relocate("org.yaml.snakeyaml", "ac.boar.shaded.snakeyaml")
    }
}
