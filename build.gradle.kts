import java.net.URI
import java.util.zip.ZipFile

plugins {
    id("java")
    id("java-library")

    alias(libs.plugins.minotaur)
    alias(libs.plugins.shadow) apply false
}

allprojects {
    apply {
        plugin("java")
        plugin("java-library")
        plugin("com.gradleup.shadow")
    }

    group = "ac.boar"
    version = "2.0.0"

    repositories {
        mavenCentral()
        maven("https://repo.opencollab.dev/main/")
    }

    java {
        toolchain {
            languageVersion = JavaLanguageVersion.of(21)
        }
    }
}

modrinth {
    token = System.getenv("MODRINTH_TOKEN")
    versionName.set(getCommitHash())
    versionNumber.set(project.version.toString() + "-" + getCommitHash())
    changelog = getCommitMessage()
    projectId = "boar"
    versionType = "alpha"
    uploadFile.set(project(":geyser").tasks.named("shadowJar"))

    // Don't comment on this :)
    gameVersions = listOf("1.8","1.8.1","1.8.2","1.8.3","1.8.4","1.8.5","1.8.6","1.8.7","1.8.8","1.8.9","1.9","1.9.1","1.9.2","1.9.3","1.9.4","1.10","1.10.1","1.10.2","1.11","1.11.1","1.11.2","1.12","1.12.1","1.12.2","1.13","1.13.1","1.13.2","1.14","1.14.1","1.14.2","1.14.3","1.14.4","1.15","1.15.1","1.15.2","1.16","1.16.1","1.16.2","1.16.3","1.16.4","1.16.5","1.17","1.17.1","1.18","1.18.1","1.18.2","1.19","1.19.1","1.19.2","1.19.3","1.19.4","1.20","1.20.1","1.20.2","1.20.3","1.20.4","1.20.5","1.20.6","1.21","1.21.1","1.21.2","1.21.3","1.21.4","1.21.5","1.21.6","1.21.7","1.21.8","1.21.9", "1.21.10", "1.21.11", "26.1", "26.1.1", "26.1.2", "26.2");
    loaders = listOf("geyser")
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

fun getCommitMessage(): String {
    return providers.exec {
        commandLine("git", "log", "-1", "--pretty=%B")
    }.standardOutput.asText.get().trim()
}

// Thanks to https://gist.github.com/JonasGroeger/7620911 :tm:
fun getCommitHash(): String {
    val gitFolder = "$projectDir/.git/"
    val takeFromHash = 7
    val head = File(gitFolder + "HEAD").readText().split(":")
    val isCommit = head.size == 1

    if(isCommit) return head[0].trim().take(takeFromHash)

    val refHead = File(gitFolder + head[1].trim())
    return refHead.readText().trim().take(takeFromHash);
}
