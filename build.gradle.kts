import java.util.zip.ZipFile

plugins {
    java
}

group = "com.plexon"
version = "3.3.0"

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
    compileOnly("com.zpkdxgames:PlexonCore:2.0.4")

    testImplementation(platform("org.junit:junit-bom:6.1.3"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("io.papermc.paper:paper-api:26.2.build.121-stable")
    testImplementation("com.zpkdxgames:PlexonCore:2.0.4")
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
    description = "Checks the PlexonSpawners 3.3 distribution contract and dependency isolation."
    dependsOn(tasks.jar)
    doLast {
        val archive = tasks.jar.get().archiveFile.get().asFile
        require(archive.isFile && archive.length() > 10_000L) {
            "Runtime JAR is missing or unexpectedly small: $archive"
        }
        ZipFile(archive).use { zip ->
            listOf(
                "plugin.yml",
                "config.yml",
                "com/plexon/spawners/PlexonSpawners.class",
                "com/plexon/spawners/api/PlexonSpawnersApi.class",
                "com/plexon/spawners/compat/WildStackerCompat.class",
                "com/plexon/spawners/config/NativeStackSettings.class",
                "com/plexon/spawners/managed/ManagedSpawnerRegistry.class",
                "com/plexon/spawners/managed/NativeStackPolicy.class",
                "com/plexon/spawners/managed/SpawnerMigrationState.class",
                "com/plexon/spawners/managed/SpawnerMigrationService.class",
                "com/plexon/spawners/managed/SpawnerStackDisplayService.class",
                "com/plexon/spawners/managed/SpawnerStateService.class",
                "com/plexon/spawners/managed/RedstoneSpawnerLockService.class",
                "com/plexon/spawners/listener/NearbyStackCapListener.class",
                "com/plexon/spawners/listener/SpawnerChunkListener.class",
                "com/plexon/spawners/listener/SpawnerProtectionListener.class",
                "com/plexon/spawners/gui/SpawnerControlGui.class",
                "com/plexon/spawners/gui/SpawnerWithdrawGuiHolder.class"
            ).forEach { entry -> require(zip.getEntry(entry) != null) { "Missing JAR entry: $entry" } }
            require(zip.entries().asSequence().none { it.name.startsWith("com/zpkdxgames/plexoncore/") }) {
                "PlexonCore runtime classes must not be shaded into PlexonSpawners"
            }
            require(zip.entries().asSequence().none { it.name.startsWith("com/bgsoftware/wildstacker/") }) {
                "WildStacker runtime classes must not be shaded into PlexonSpawners"
            }
        }
    }
}

tasks.check {
    dependsOn(verifyDistribution)
}
