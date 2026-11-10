import java.net.URI
import java.util.zip.ZipFile

plugins {
    id("java")
    id("java-library")

    alias(libs.plugins.indra)
    alias(libs.plugins.indra.publishing) apply false
    alias(libs.plugins.minotaur) apply false
    alias(libs.plugins.shadow) apply false
}

allprojects {
    group = "ac.boar"
    // dev2 is the CubeCraft-focused branch. CI publishes this version to
    // repo.opencollab.dev/maven-snapshots on each dev2 push, so consumers can
    // pin "dev2-SNAPSHOT" and always get the latest dev2 build. Master keeps
    // its own semver snapshot version.
    version = "dev2-SNAPSHOT"
}

subprojects {
    apply {
        plugin("java")
        plugin("java-library")
        plugin("net.kyori.indra")
        plugin("net.kyori.indra.publishing")
    }

    repositories {
        mavenCentral()
        maven("https://repo.opencollab.dev/main/")
    }

    java {
        toolchain {
            languageVersion = JavaLanguageVersion.of(21)
        }
    }

    indra {
        github("opencollab-incubator", "Boar") {
            ci(true)
            issues(true)
            scm(true)
        }

        mitLicense()

        javaVersions {
            // Strict mode makes indra pin the compile toolchain to exactly 21.
            // Without it, indra compiles with whatever JDK runs Gradle, and
            // Lombok breaks inside newer javac versions (for example JDK 26).
            strictVersions(true)
            target(21)
        }

        publishSnapshotsTo("boar", "https://repo.opencollab.dev/maven-snapshots")
        publishReleasesTo("boar", "https://repo.opencollab.dev/maven-releases")
    }

    tasks.named("requireTagged") {
        enabled = false
    }
}

tasks.register<Exec>("runGeyser") {
    group = "boar"
    description = "Build the Boar Geyser extension and launch the standalone Geyser proxy in ./run/geyser."

    dependsOn(":geyser:shadowJar")

    val builtJar = project(":geyser").layout.buildDirectory.file("libs/boar-geyser.jar")
    val geyserDir = rootProject.projectDir.resolve("run/geyser")
    val extensionsDir = geyserDir.resolve("extensions")

    inputs.file(builtJar)

    doFirst {
        geyserDir.mkdirs()
        val geyserJar = geyserDir.resolve("Geyser.jar")
        if (!geyserJar.exists()) {
            val url = "https://download.geysermc.org/v2/projects/geyser/versions/latest/builds/latest/downloads/standalone"
            logger.lifecycle("[runGeyser] Geyser.jar missing, downloading latest standalone from $url")
            URI(url).toURL().openStream().use { input ->
                geyserJar.outputStream().use { output -> input.copyTo(output) }
            }
            logger.lifecycle("[runGeyser] downloaded Geyser.jar -> $geyserJar")
        }
        extensionsDir.mkdirs()
        val src = builtJar.get().asFile
        val dst = extensionsDir.resolve("boar-geyser.jar")

        extensionsDir.listFiles { f -> f.isFile && f.extension == "jar" && f != dst }?.forEach { jar ->
            if (readExtensionId(jar) == "boar") {
                jar.delete()
                logger.lifecycle("[runGeyser] removed stale Boar extension ${jar.name}")
            }
        }

        src.copyTo(dst, overwrite = true)
        logger.lifecycle("[runGeyser] installed ${src.name} -> $dst")
        logger.lifecycle("[runGeyser] launching Geyser standalone in $geyserDir")
    }

    workingDir = geyserDir
    commandLine("java", "-jar", "Geyser.jar")
    standardInput = System.`in`
}

// Read the `id` field from a Geyser extension.yml inside a jar, or null if it isn't an extension.
fun readExtensionId(jar: File): String? {
    return try {
        ZipFile(jar).use { zip ->
            val entry = zip.getEntry("extension.yml") ?: return null
            zip.getInputStream(entry).bufferedReader().useLines { lines ->
                lines.map { it.trim() }
                    .firstOrNull { it.startsWith("id:") }
                    ?.substringAfter("id:")
                    ?.trim()
                    ?.trim('"', '\'')
            }
        }
    } catch (e: Exception) {
        null
    }
}
