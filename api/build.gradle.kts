plugins {
    `maven-publish`
}

dependencies {
    compileOnlyApi(libs.annotations)
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
        }
    }
}
