import java.net.URI
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.zip.ZipFile

plugins {
    java
}

group = "com.plexon"
version = "4.0.0"

val pluginVersion = version.toString()
val coreVersion = "2.1.0"
val coreSha256 = "7ee823ded87d5be9c62426b04571c0d0d6b11c138575ca2c91838586c9f7576c"
val coreUrl = "https://github.com/ZpkDxGames/PlexonCore/releases/download/v$coreVersion/PlexonCore-$coreVersion.jar"
val coreJar = layout.buildDirectory.file("deps/PlexonCore-$coreVersion.jar")

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

val downloadPlexonCore = tasks.register("downloadPlexonCore") {
    group = "build setup"
    description = "Downloads and verifies the exact PlexonCore 2.1.0 stable compile/test artifact."
    inputs.property("coreVersion", coreVersion)
    inputs.property("coreSha256", coreSha256)
    outputs.file(coreJar)
    doLast {
        val output = coreJar.get().asFile.toPath()
        Files.createDirectories(output.parent)
        val temporary = Files.createTempFile(output.parent, "PlexonCore-", ".jar.tmp")
        try {
            URI(coreUrl).toURL().openStream().use { input ->
                Files.copy(input, temporary, StandardCopyOption.REPLACE_EXISTING)
            }
            val digest = MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(temporary))
                .joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
            require(digest == coreSha256) {
                "PlexonCore $coreVersion checksum mismatch: expected $coreSha256, got $digest"
            }
            Files.move(temporary, output, StandardCopyOption.REPLACE_EXISTING)
        } finally {
            Files.deleteIfExists(temporary)
        }
    }
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:26.2.build.121-stable")
    compileOnly("com.bgsoftware:WildStackerAPI:2026.2")
    compileOnly(files(coreJar))

    testImplementation(platform("org.junit:junit-bom:6.1.3"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("io.papermc.paper:paper-api:26.2.build.121-stable")
    testImplementation("com.bgsoftware:WildStackerAPI:2026.2")
    testImplementation(files(coreJar))
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<JavaCompile>().configureEach {
    dependsOn(downloadPlexonCore)
    options.encoding = "UTF-8"
    options.release.set(25)
    options.compilerArgs.addAll(listOf("-Xlint:all", "-Xlint:deprecation", "-Xlint:-processing"))
}

tasks.processResources {
    val resourceProperties = mapOf("version" to pluginVersion)
    inputs.properties(resourceProperties)
    filteringCharset = "UTF-8"
    filesMatching("plugin.yml") { expand(resourceProperties) }
}

tasks.test {
    dependsOn(downloadPlexonCore)
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
    description = "Verifies the 4.0 WildStacker-authoritative distribution and Core isolation contract."
    dependsOn(tasks.jar)
    doLast {
        val archive = tasks.jar.get().archiveFile.get().asFile
        require(archive.isFile && archive.length() > 10_000L) { "Runtime JAR missing or unexpectedly small: $archive" }
        ZipFile(archive).use { zip ->
            listOf(
                "plugin.yml",
                "config.yml",
                "messages.yml",
                "com/plexon/spawners/PlexonSpawners.class",
                "com/plexon/spawners/api/PlexonSpawnersApi.class",
                "com/plexon/spawners/api/DefaultPlexonSpawnersApi.class",
                "com/plexon/spawners/event/PlexonSpawnerBreakFinalizedEvent.class",
                "com/plexon/spawners/event/PlexonSpawnerRecoveredEvent.class",
                "com/plexon/spawners/event/PlexonSpawnerRewardFinalizedEvent.class",
                "com/plexon/spawners/runtime/SpawnerRuntimeSnapshot.class",
                "com/plexon/spawners/config/ConfigV12Migration.class",
                "com/plexon/spawners/integration/WildStackerBridge.class",
                "com/plexon/spawners/integration/core/CoreBridge.class",
                "com/plexon/spawners/listener/SpawnerBreakListener.class"
            ).forEach { entry -> require(zip.getEntry(entry) != null) { "Missing JAR entry: $entry" } }

            val names = zip.entries().asSequence().map { it.name }.toSet()
            listOf(
                "com/plexon/spawners/managed/",
                "com/plexon/spawners/compat/"
            ).forEach { prefix -> require(names.none { it.startsWith(prefix) }) { "Legacy architecture leaked into JAR: $prefix" } }
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
                "SpawnerChunkListener.class",
                "SpawnerWithdrawGui.class",
                "SpawnerWithdrawGuiHolder.class",
                "WithdrawalPolicy.class"
            ).forEach { banned -> require(names.none { it.endsWith(banned) }) { "Banned legacy/player-interaction class present: $banned" } }
            require(names.none { it.startsWith("com/bgsoftware/wildstacker/") }) { "WildStacker runtime classes must not be shaded" }
            require(names.none { it.startsWith("com/zpkdxgames/plexoncore/") }) { "PlexonCore runtime classes must not be shaded" }
            require(names.none { it.startsWith("io/papermc/") || it.startsWith("org/bukkit/") }) { "Server API classes must not be shaded" }
        }
    }
}

tasks.check { dependsOn(verifyDistribution) }
