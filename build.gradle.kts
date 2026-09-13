import java.util.zip.ZipFile

plugins {
    java
}

group = "com.plexon"
version = "4.0.0"

val pluginVersion = version.toString()

repositories {
    mavenCentral()
    maven {
        name = "papermc"
        url = uri("https://repo.papermc.io/repository/maven-public/")
    }
    maven {
        name = "bgsoftware-api"
        url = uri("https://repo.bg-software.com/repository/api/")
    }
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(25))
    withSourcesJar()
    withJavadocJar()
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:26.2.build.121-stable")
    compileOnly("com.bgsoftware:WildStackerAPI:2026.2")

    testImplementation(platform("org.junit:junit-bom:6.1.3"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("io.papermc.paper:paper-api:26.2.build.121-stable")
    testImplementation("com.bgsoftware:WildStackerAPI:2026.2")
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
    description = "Verifies the PlexonSpawners 4.0 WildStacker-authoritative distribution contract."
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
                "messages.yml",
                "com/plexon/spawners/PlexonSpawners.class",
                "com/plexon/spawners/breaking/SpawnerBreakPolicy.class",
                "com/plexon/spawners/config/ConfigBootstrap.class",
                "com/plexon/spawners/config/PluginSettings.class",
                "com/plexon/spawners/essence/EssenceRewardPolicy.class",
                "com/plexon/spawners/essence/EssenceService.class",
                "com/plexon/spawners/gui/SpawnerWithdrawGui.class",
                "com/plexon/spawners/gui/WithdrawalPolicy.class",
                "com/plexon/spawners/integration/WildStackerBridge.class",
                "com/plexon/spawners/listener/SpawnerBreakListener.class"
            ).forEach { entry -> require(zip.getEntry(entry) != null) { "Missing JAR entry: $entry" } }

            val names = zip.entries().asSequence().map { it.name }.toSet()
            listOf(
                "com/plexon/spawners/managed/",
                "com/plexon/spawners/compat/",
                "com/plexon/spawners/api/",
                "com/plexon/spawners/integration/core/"
            ).forEach { prefix ->
                require(names.none { it.startsWith(prefix) }) { "Legacy architecture leaked into JAR: $prefix" }
            }
            listOf(
                "ManagedSpawnerRegistry.class",
                "ManagedSpawnAggregationService.class",
                "NativeStackPolicy.class",
                "NearbyStackCapPolicy.class",
                "PhysicalFallbackBackend.class",
                "RedstoneSpawnerLockService.class",
                "SpawnerMigrationService.class",
                "SpawnerStackDisplayService.class",
                "SpawnerStateService.class",
                "SpawnerTier.class",
                "SpawnerTuning.class",
                "SpawnerPlaceListener.class",
                "SpawnerChunkListener.class"
            ).forEach { banned ->
                require(names.none { it.endsWith(banned) }) { "Banned legacy class present: $banned" }
            }
            require(names.none { it.startsWith("com/bgsoftware/wildstacker/") }) {
                "WildStacker runtime classes must not be shaded into PlexonSpawners"
            }
        }
    }
}

tasks.check {
    dependsOn(verifyDistribution)
}
