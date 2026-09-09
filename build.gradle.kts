import java.util.zip.ZipFile

plugins {
    java
}

group = "com.plexon"
version = "2.3.1"

val pluginVersion = version.toString()

repositories {
    mavenLocal()
    mavenCentral()
    maven {
        name = "papermc"
        url = uri("https://repo.papermc.io/repository/maven-public/")
    }
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(25))
    withSourcesJar()
    withJavadocJar()
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:26.2.build.121-stable")
    compileOnly("com.zpkdxgames:PlexonCore:1.0.0")

    testImplementation(platform("org.junit:junit-bom:6.1.3"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(25)
    options.compilerArgs.addAll(listOf("-Xlint:all", "-Xlint:deprecation", "-Xlint:-processing"))
}

tasks.processResources {
    val resourceProperties = mapOf("version" to pluginVersion)
    inputs.properties(resourceProperties)
    filteringCharset = "UTF-8"
    filesMatching("plugin.yml") {
        expand(resourceProperties)
    }
}

tasks.test {
    useJUnitPlatform()
}

tasks.jar {
    archiveBaseName.set("PlexonSpawners")
    archiveVersion.set(pluginVersion)
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
    manifest {
        attributes(
            "Implementation-Title" to "PlexonSpawners",
            "Implementation-Version" to pluginVersion,
            "Implementation-Vendor" to "ZpkDxGames"
        )
    }
}

val verifyDistribution = tasks.register("verifyDistribution") {
    group = "verification"
    description = "Checks the PlexonSpawners distribution contract and required PlexonCore binding."
    dependsOn(tasks.jar)
    doLast {
        val archive = tasks.jar.get().archiveFile.get().asFile
        require(archive.isFile && archive.length() > 10_000L) {
            "Runtime JAR is missing or unexpectedly small: $archive"
        }
        ZipFile(archive).use { zip ->
            listOf(
                "plugin.yml",
                "com/plexon/spawners/PlexonSpawners.class",
                "com/plexon/spawners/api/PlexonSpawnersApi.class",
                "com/plexon/spawners/event/PlexonSpawnerRecoveredEvent.class",
                "com/plexon/spawners/event/PlexonSpawnerPlacedEvent.class",
                "com/plexon/spawners/event/PlexonSpawnerEssenceAwardedEvent.class",
                "com/plexon/spawners/integration/core/CoreBridge.class",
                "com/plexon/spawners/integration/core/CoreBridgeFactory.class",
                "com/plexon/spawners/integration/core/PlexonCoreBridge.class"
            ).forEach { entry -> require(zip.getEntry(entry) != null) { "Missing JAR entry: $entry" } }

            val pluginYml = zip.getInputStream(zip.getEntry("plugin.yml")).bufferedReader().use { it.readText() }
            require(pluginYml.contains("depend:\n  - PlexonCore")) {
                "plugin.yml must declare PlexonCore as a required dependency"
            }
            require(!pluginYml.contains("softdepend:\n  - PlexonCore")) {
                "plugin.yml must not silently downgrade PlexonCore to a soft dependency"
            }
            require(zip.entries().asSequence().none { it.name.startsWith("com/zpkdxgames/plexoncore/") }) {
                "PlexonCore runtime classes must not be shaded into PlexonSpawners"
            }
        }
    }
}

tasks.check {
    dependsOn(verifyDistribution)
}
