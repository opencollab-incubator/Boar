import java.io.ByteArrayOutputStream

plugins {
    id("java")
    id("com.gradleup.shadow") version "8.3.0"
    id("com.modrinth.minotaur") version "2.+"
}

group = "ac.boar"
version = "2.0.0"

repositories {
    mavenCentral()
    maven("https://repo.opencollab.dev/main/")
}

dependencies {
    compileOnly("org.geysermc.geyser:core:2.10.1-SNAPSHOT") {
        exclude(group = "com.google.code.gson", module = "gson")
    }

    compileOnly("it.unimi.dsi:fastutil:8.5.15")
    compileOnly("org.projectlombok:lombok:1.18.36")
    annotationProcessor("org.projectlombok:lombok:1.18.36")

    compileOnly("com.google.code.gson:gson:2.3.1")
    testImplementation("com.google.code.gson:gson:2.3.1")

    implementation("it.unimi.dsi:fastutil:8.5.15")

    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:2.15.2")
}

tasks {
    shadowJar {
        archiveFileName = "boar.jar"

        relocate("it.unimi.dsi.fastutil", "ac.boar.shaded.fastutil")
        relocate("com.fasterxml.jackson", "ac.boar.shaded.jackson")
        relocate("org.yaml.snakeyaml", "ac.boar.shaded.snakeyaml")
    }
}

// Runs a standalone Geyser proxy with the freshly built Boar extension loaded.
// Everything (config, logs, extensions) lives under run/geyser so it survives between runs.
tasks.register<JavaExec>("runGeyser") {
    group = "boar"
    description = "Runs a standalone Geyser proxy with the Boar extension."

    dependsOn(tasks.shadowJar)

    val geyserDir = layout.projectDirectory.dir("run/geyser")
    val geyserJar = geyserDir.file("Geyser.jar")
    val extensionsDir = geyserDir.dir("extensions")

    // Deploy the built boar.jar into the Geyser extensions folder, clearing any
    // stale boar jars first so Geyser doesn't try to load two copies of the extension.
    doFirst {
        val extDir = extensionsDir.asFile
        extDir.mkdirs()
        extDir.listFiles { file ->
            file.isFile && file.name.startsWith("boar") && file.name.endsWith(".jar")
        }?.forEach { it.delete() }

        copy {
            from(tasks.shadowJar.get().archiveFile)
            into(extDir)
        }

        if (!geyserJar.asFile.exists()) {
            throw GradleException("Geyser.jar not found at ${geyserJar.asFile}. Place a standalone Geyser build there first.")
        }
    }

    workingDir = geyserDir.asFile
    classpath = files(geyserJar)
    mainClass.set("org.geysermc.geyser.platform.standalone.GeyserStandaloneBootstrap")

    // Forward console input so Geyser's interactive commands work, and default to
    // headless mode (pass --args="..." to override).
    standardInput = System.`in`
    args("--nogui")
}

modrinth {
    token = System.getenv("MODRINTH_TOKEN")
    versionName.set(getCommitHash())
    versionNumber.set(project.version.toString() + "-" + getCommitHash())
    changelog = getCommitMessage()
    projectId = "boar"
    versionType = "alpha"
    uploadFile.set(tasks.getByPath("shadowJar"))

    // Don't comment on this :)
    gameVersions = listOf("1.8","1.8.1","1.8.2","1.8.3","1.8.4","1.8.5","1.8.6","1.8.7","1.8.8","1.8.9","1.9","1.9.1","1.9.2","1.9.3","1.9.4","1.10","1.10.1","1.10.2","1.11","1.11.1","1.11.2","1.12","1.12.1","1.12.2","1.13","1.13.1","1.13.2","1.14","1.14.1","1.14.2","1.14.3","1.14.4","1.15","1.15.1","1.15.2","1.16","1.16.1","1.16.2","1.16.3","1.16.4","1.16.5","1.17","1.17.1","1.18","1.18.1","1.18.2","1.19","1.19.1","1.19.2","1.19.3","1.19.4","1.20","1.20.1","1.20.2","1.20.3","1.20.4","1.20.5","1.20.6","1.21","1.21.1","1.21.2","1.21.3","1.21.4","1.21.5","1.21.6","1.21.7","1.21.8","1.21.9", "1.21.10", "1.21.11");
    loaders = listOf("geyser")
}

fun getCommitMessage(): String {
    val stdout = ByteArrayOutputStream()
    exec {
        commandLine("git", "log", "-1", "--pretty=%B")
        standardOutput = stdout
    }
    return stdout.toString().trim()
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